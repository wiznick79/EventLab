package pt.eventlab.payment.messaging;

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
import pt.eventlab.contracts.messages.AuthorizePayment;
import pt.eventlab.messaging.ServiceBusEnvelopeCodec;
import pt.eventlab.messaging.ServiceBusTraceContext;

@Component
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class AuthorizePaymentProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizePaymentProcessor.class);

    private final ServiceBusEnvelopeCodec codec;
    private final ObservationRegistry observations;
    private final ServiceBusProcessorClient processor;
    private final ServiceBusTraceContext traceContext;
    private final AuthorizePaymentHandler handler;

    AuthorizePaymentProcessor(
            PaymentMessagingProperties properties,
            ServiceBusEnvelopeCodec codec,
            ServiceBusTraceContext traceContext,
            ObservationRegistry observations,
            AuthorizePaymentHandler handler) {
        this.codec = codec;
        this.traceContext = traceContext;
        this.observations = observations;
        this.handler = handler;
        this.processor = new ServiceBusClientBuilder()
                .connectionString(properties.connectionString())
                .processor()
                .queueName(properties.paymentCommandsQueue())
                .disableAutoComplete()
                .maxConcurrentCalls(1)
                .processMessage(this::process)
                .processError(context -> LOGGER.error(
                        "Service Bus payment processor error in {}", context.getEntityPath(), context.getException()))
                .buildProcessorClient();
    }

    @PostConstruct
    void start() {
        processor.start();
    }

    private void process(ServiceBusReceivedMessageContext context) {
        try (Scope ignored = traceContext.makeCurrent(context.getMessage())) {
            Observation.createNotStarted("eventlab.servicebus.process", observations)
                    .lowCardinalityKeyValue("messaging.destination", "payment-commands")
                    .observe(() -> handle(context));
        }
    }

    private void handle(ServiceBusReceivedMessageContext context) {
        EventEnvelope<AuthorizePayment> command = codec.decode(
                context.getMessage().getBody(), AuthorizePayment.class);
        handler.handle(command);
        context.complete();
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
