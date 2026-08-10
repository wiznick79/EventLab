package pt.eventlab.workflow.messaging;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
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
import pt.eventlab.messaging.ServiceBusEnvelopeCodec;
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

    PaymentAuthorizedProcessor(
            WorkflowMessagingProperties properties,
            ServiceBusEnvelopeCodec codec,
            ServiceBusTraceContext traceContext,
            ObservationRegistry observations,
            PaymentAuthorizedHandler handler) {
        this.codec = codec;
        this.traceContext = traceContext;
        this.observations = observations;
        this.handler = handler;
        this.processor = new ServiceBusClientBuilder()
                .connectionString(properties.connectionString())
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
        if (!MessageTypes.PAYMENT_AUTHORIZED.equals(context.getMessage().getSubject())) {
            context.complete();
            return;
        }

        try (Scope ignored = traceContext.makeCurrent(context.getMessage())) {
            Observation.createNotStarted("eventlab.servicebus.process", observations)
                    .lowCardinalityKeyValue("messaging.destination", "workflow-events")
                    .observe(() -> handle(context));
        }
    }

    private void handle(ServiceBusReceivedMessageContext context) {
        EventEnvelope<PaymentAuthorized> event = codec.decode(
                context.getMessage().getBody(), PaymentAuthorized.class);
        handler.handle(event);
        context.complete();
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
