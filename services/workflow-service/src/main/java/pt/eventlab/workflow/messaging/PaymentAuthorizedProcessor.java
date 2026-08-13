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
import pt.eventlab.contracts.messages.FulfilmentDeadLettered;
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
    private final WorkflowMessagingProperties properties;
    private volatile ServiceBusProcessorClient processor;
    private volatile int concurrency = 1;
    private volatile int processingDelayMillis;
    private final ServiceBusTraceContext traceContext;
    private final PaymentAuthorizedHandler handler;
    private final FulfilmentCompletedHandler fulfilmentHandler;
    private final FulfilmentRejectedHandler rejectionHandler;
    private final PaymentCompensatedHandler compensationHandler;
    private final FulfilmentStatusChangedHandler statusChangedHandler;
    private final FulfilmentDeadLetteredHandler deadLetteredHandler;

    PaymentAuthorizedProcessor(
            WorkflowMessagingProperties properties,
            ServiceBusEnvelopeCodec codec,
            ServiceBusTraceContext traceContext,
            ObservationRegistry observations,
            PaymentAuthorizedHandler handler,
            FulfilmentCompletedHandler fulfilmentHandler,
            FulfilmentRejectedHandler rejectionHandler,
            PaymentCompensatedHandler compensationHandler,
            FulfilmentStatusChangedHandler statusChangedHandler,
            FulfilmentDeadLetteredHandler deadLetteredHandler) {
        this.codec = codec;
        this.properties = properties;
        this.traceContext = traceContext;
        this.observations = observations;
        this.handler = handler;
        this.fulfilmentHandler = fulfilmentHandler;
        this.rejectionHandler = rejectionHandler;
        this.compensationHandler = compensationHandler;
        this.statusChangedHandler = statusChangedHandler;
        this.deadLetteredHandler = deadLetteredHandler;
        this.processor = buildProcessor(1);
    }

    private ServiceBusProcessorClient buildProcessor(int calls) {
        return ServiceBusClients
                .create(properties.connectionString(), properties.fullyQualifiedNamespace())
                .processor()
                .topicName(properties.businessEventsTopic())
                .subscriptionName(properties.workflowEventsSubscription())
                .disableAutoComplete()
                .maxConcurrentCalls(calls)
                .processMessage(this::process)
                .processError(context -> LOGGER.error(
                        "Service Bus workflow processor error in {}", context.getEntityPath(), context.getException()))
                .buildProcessorClient();
    }

    synchronized void reconfigure(int calls) {
        ConsumerConcurrencyController.validate(calls);
        if (calls == concurrency) return;
        ServiceBusProcessorClient replacement = buildProcessor(calls);
        processor.close();
        processor = replacement;
        processor.start();
        concurrency = calls;
    }

    int concurrency() { return concurrency; }

    void configureDelay(int delayMillis) { processingDelayMillis = delayMillis; }

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
                    if (!MessageTypes.FULFILMENT_DEAD_LETTERED.equals(context.getMessage().getSubject())) {
                        context.complete();
                        return;
                    }
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
            applyDelay();
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
        } else if (MessageTypes.FULFILMENT_STATUS_CHANGED.equals(context.getMessage().getSubject())) {
            EventEnvelope<FulfilmentStatusChanged> event = codec.decode(
                    context.getMessage().getBody(), FulfilmentStatusChanged.class);
            statusChangedHandler.handle(event);
        } else {
            EventEnvelope<FulfilmentDeadLettered> event = codec.decode(
                    context.getMessage().getBody(), FulfilmentDeadLettered.class);
            deadLetteredHandler.handle(event);
        }
        context.complete();
    }

    private void applyDelay() {
        if (processingDelayMillis == 0) return;
        try {
            Thread.sleep(processingDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Workflow constraint interrupted", exception);
        }
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
