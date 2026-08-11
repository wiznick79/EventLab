package pt.eventlab.workflow.messaging;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.MessageTypes;
import pt.eventlab.contracts.messages.PaymentAuthorized;
import pt.eventlab.contracts.messages.FulfilmentCompleted;
import pt.eventlab.contracts.messages.FulfilmentRejected;
import pt.eventlab.contracts.messages.PaymentCompensated;
import pt.eventlab.contracts.messages.FulfilmentStatusChanged;
import pt.eventlab.messaging.ServiceBusEnvelopeCodec;
import pt.eventlab.messaging.ServiceBusClients;
import pt.eventlab.messaging.ServiceBusTraceContext;

@Component
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class PaymentAuthorizedProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentAuthorizedProcessor.class);

    private final ServiceBusEnvelopeCodec codec;
    private final ObservationRegistry observations;
    private final ServiceBusProcessorClient processor;
    private final ServiceBusTraceContext traceContext;
    private final PaymentAuthorizedHandler handler;
    private final FulfilmentCompletedHandler fulfilmentHandler;
    private final FulfilmentRejectedHandler rejectionHandler;
    private final PaymentCompensatedHandler compensationHandler;
    private final FulfilmentStatusChangedHandler statusChangedHandler;

    PaymentAuthorizedProcessor(
            WorkflowMessagingProperties properties,
            ServiceBusEnvelopeCodec codec,
            ServiceBusTraceContext traceContext,
            ObservationRegistry observations,
            PaymentAuthorizedHandler handler,
            FulfilmentCompletedHandler fulfilmentHandler,
            FulfilmentRejectedHandler rejectionHandler,
            PaymentCompensatedHandler compensationHandler,
            FulfilmentStatusChangedHandler statusChangedHandler) {
        this.codec = codec;
        this.traceContext = traceContext;
        this.observations = observations;
        this.handler = handler;
        this.fulfilmentHandler = fulfilmentHandler;
        this.rejectionHandler = rejectionHandler;
        this.compensationHandler = compensationHandler;
        this.statusChangedHandler = statusChangedHandler;
        this.processor = ServiceBusClients
                .create(properties.connectionString(), properties.fullyQualifiedNamespace())
                .processor()
                .topicName(properties.businessEventsTopic())
                .subscriptionName(properties.workflowEventsSubscription())
                .disableAutoComplete()
                .maxConcurrentCalls(1)
                .processMessage(this::process)
                .processError(context -> LOGGER.error(
                        "Service Bus workflow processor error in {}", context.getEntityPath(), context.getException()))
                .buildProcessorClient();
    }

    @PostConstruct
    void start() {
        processor.start();
    }

    private void process(ServiceBusReceivedMessageContext context) {
        if (!MessageTypes.PAYMENT_AUTHORIZED.equals(context.getMessage().getSubject())
                && !MessageTypes.FULFILMENT_COMPLETED.equals(context.getMessage().getSubject())) {
            if (!MessageTypes.FULFILMENT_REJECTED.equals(context.getMessage().getSubject())
                    && !MessageTypes.PAYMENT_COMPENSATED.equals(context.getMessage().getSubject())) {
                if (!MessageTypes.FULFILMENT_STATUS_CHANGED.equals(context.getMessage().getSubject())) {
                    context.complete();
                    return;
                }
            }
        }

        try (Scope ignored = traceContext.makeCurrent(context.getMessage())) {
            Observation.createNotStarted("eventlab.servicebus.process", observations)
                    .lowCardinalityKeyValue("messaging.destination", "workflow-events")
                    .observe(() -> handle(context));
        }
    }

    private void handle(ServiceBusReceivedMessageContext context) {
        if (MessageTypes.PAYMENT_AUTHORIZED.equals(context.getMessage().getSubject())) {
            EventEnvelope<PaymentAuthorized> event = codec.decode(
                    context.getMessage().getBody(), PaymentAuthorized.class);
            handler.handle(event);
        } else if (MessageTypes.FULFILMENT_COMPLETED.equals(context.getMessage().getSubject())) {
            EventEnvelope<FulfilmentCompleted> event = codec.decode(
                    context.getMessage().getBody(), FulfilmentCompleted.class);
            fulfilmentHandler.handle(event);
        } else if (MessageTypes.FULFILMENT_REJECTED.equals(context.getMessage().getSubject())) {
            EventEnvelope<FulfilmentRejected> event = codec.decode(
                    context.getMessage().getBody(), FulfilmentRejected.class);
            rejectionHandler.handle(event);
        } else if (MessageTypes.PAYMENT_COMPENSATED.equals(context.getMessage().getSubject())) {
            EventEnvelope<PaymentCompensated> event = codec.decode(
                    context.getMessage().getBody(), PaymentCompensated.class);
            compensationHandler.handle(event);
        } else {
            EventEnvelope<FulfilmentStatusChanged> event = codec.decode(
                    context.getMessage().getBody(), FulfilmentStatusChanged.class);
            statusChangedHandler.handle(event);
        }
        context.complete();
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
