package pt.eventlab.console.messaging;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.JsonNode;
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
import pt.eventlab.messaging.ServiceBusEnvelopeCodec;
import pt.eventlab.messaging.ServiceBusClients;
import pt.eventlab.messaging.ServiceBusTraceContext;

@Component
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class BusinessEventProjectionProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessEventProjectionProcessor.class);

    private final ServiceBusEnvelopeCodec codec;
    private final ObservationRegistry observations;
    private final ServiceBusProcessorClient processor;
    private final BusinessEventProjectionHandler handler;
    private final ServiceBusTraceContext traceContext;

    BusinessEventProjectionProcessor(
            LabConsoleMessagingProperties properties,
            ServiceBusEnvelopeCodec codec,
            ServiceBusTraceContext traceContext,
            ObservationRegistry observations,
            BusinessEventProjectionHandler handler) {
        this.codec = codec;
        this.traceContext = traceContext;
        this.observations = observations;
        this.handler = handler;
        this.processor = ServiceBusClients
                .create(properties.connectionString(), properties.fullyQualifiedNamespace())
                .processor()
                .topicName(properties.businessEventsTopic())
                .subscriptionName(properties.labConsoleEventsSubscription())
                .disableAutoComplete()
                .maxConcurrentCalls(1)
                .processMessage(this::process)
                .processError(context -> LOGGER.error(
                        "Service Bus projection error in {}", context.getEntityPath(), context.getException()))
                .buildProcessorClient();
    }

    @PostConstruct
    void start() {
        processor.start();
    }

    private void process(ServiceBusReceivedMessageContext context) {
        try (Scope ignored = traceContext.makeCurrent(context.getMessage())) {
            Observation.createNotStarted("eventlab.servicebus.project", observations)
                    .lowCardinalityKeyValue("messaging.destination", "lab-console-events")
                    .observe(() -> handle(context));
        }
    }

    private void handle(ServiceBusReceivedMessageContext context) {
        EventEnvelope<JsonNode> event = codec.decode(context.getMessage().getBody(), JsonNode.class);
        handler.handle(event, traceContext.traceId(context.getMessage()));
        context.complete();
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
