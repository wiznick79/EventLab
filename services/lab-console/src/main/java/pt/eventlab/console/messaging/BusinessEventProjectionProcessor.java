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
public class BusinessEventProjectionProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessEventProjectionProcessor.class);

    private final ServiceBusEnvelopeCodec codec;
    private final ObservationRegistry observations;
    private final LabConsoleMessagingProperties properties;
    private volatile ServiceBusProcessorClient processor;
    private volatile int concurrency = 1;
    private volatile int processingDelayMillis;
    private final BusinessEventProjectionHandler handler;
    private final ServiceBusTraceContext traceContext;
    private final EvidencePipelineStatus pipelineStatus;

    BusinessEventProjectionProcessor(
            LabConsoleMessagingProperties properties,
            ServiceBusEnvelopeCodec codec,
            ServiceBusTraceContext traceContext,
            ObservationRegistry observations,
            BusinessEventProjectionHandler handler,
            EvidencePipelineStatus pipelineStatus) {
        this.codec = codec;
        this.properties = properties;
        this.traceContext = traceContext;
        this.observations = observations;
        this.handler = handler;
        this.pipelineStatus = pipelineStatus;
        this.processor = buildProcessor(1);
    }

    private ServiceBusProcessorClient buildProcessor(int calls) {
        return ServiceBusClients
                .create(properties.connectionString(), properties.fullyQualifiedNamespace())
                .processor()
                .topicName(properties.businessEventsTopic())
                .subscriptionName(properties.labConsoleEventsSubscription())
                .disableAutoComplete()
                .maxConcurrentCalls(calls)
                .processMessage(this::process)
                .processError(context -> {
                    pipelineStatus.failed(context.getException());
                    LOGGER.error("Service Bus projection error in {}",
                            context.getEntityPath(), context.getException());
                })
                .buildProcessorClient();
    }

    public synchronized void reconfigure(int calls) {
        if (!java.util.List.of(1, 4, 8).contains(calls)) {
            throw new IllegalArgumentException("consumer concurrency must be 1, 4, or 8");
        }
        if (calls == concurrency) return;
        ServiceBusProcessorClient replacement = buildProcessor(calls);
        processor.close();
        processor = replacement;
        processor.start();
        concurrency = calls;
    }

    public int concurrency() { return concurrency; }

    public void configureDelay(int delayMillis) {
        if (delayMillis < 0 || delayMillis > 500) {
            throw new IllegalArgumentException("processing delay must be between 0 and 500 ms");
        }
        processingDelayMillis = delayMillis;
    }

    @PostConstruct
    void start() {
        processor.start();
        pipelineStatus.running();
    }

    private void process(ServiceBusReceivedMessageContext context) {
        try (Scope ignored = traceContext.makeCurrent(context.getMessage())) {
            Observation.createNotStarted("eventlab.servicebus.project", observations)
                    .lowCardinalityKeyValue("messaging.destination", "lab-console-events")
                    .observe(() -> handle(context));
        }
    }

    private void handle(ServiceBusReceivedMessageContext context) {
        applyDelay();
        EventEnvelope<JsonNode> event = codec.decode(context.getMessage().getBody(), JsonNode.class);
        handler.handle(event, traceContext.traceId(context.getMessage()));
        context.complete();
        pipelineStatus.received();
    }

    private void applyDelay() {
        if (processingDelayMillis == 0) return;
        try {
            Thread.sleep(processingDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Evidence constraint interrupted", exception);
        }
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
