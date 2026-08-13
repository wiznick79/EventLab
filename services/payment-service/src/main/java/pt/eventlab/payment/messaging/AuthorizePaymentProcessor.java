package pt.eventlab.payment.messaging;

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
import pt.eventlab.contracts.MessageTypes;
import pt.eventlab.contracts.messages.CompensatePayment;
import pt.eventlab.messaging.ServiceBusEnvelopeCodec;
import pt.eventlab.messaging.ServiceBusClients;
import pt.eventlab.messaging.ServiceBusTraceContext;

@Component
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class AuthorizePaymentProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizePaymentProcessor.class);

    private final ServiceBusEnvelopeCodec codec;
    private final ObservationRegistry observations;
    private final PaymentMessagingProperties properties;
    private volatile ServiceBusProcessorClient processor;
    private volatile int concurrency = 1;
    private volatile int processingDelayMillis;
    private final ServiceBusTraceContext traceContext;
    private final AuthorizePaymentHandler handler;
    private final CompensatePaymentHandler compensationHandler;

    AuthorizePaymentProcessor(
            PaymentMessagingProperties properties,
            ServiceBusEnvelopeCodec codec,
            ServiceBusTraceContext traceContext,
            ObservationRegistry observations,
            AuthorizePaymentHandler handler,
            CompensatePaymentHandler compensationHandler) {
        this.codec = codec;
        this.properties = properties;
        this.traceContext = traceContext;
        this.observations = observations;
        this.handler = handler;
        this.compensationHandler = compensationHandler;
        this.processor = buildProcessor(1);
    }

    private ServiceBusProcessorClient buildProcessor(int calls) {
        return ServiceBusClients
                .create(properties.connectionString(), properties.fullyQualifiedNamespace())
                .processor()
                .queueName(properties.paymentCommandsQueue())
                .disableAutoComplete()
                .maxConcurrentCalls(calls)
                .processMessage(this::process)
                .processError(context -> LOGGER.error(
                        "Service Bus payment processor error in {}", context.getEntityPath(), context.getException()))
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
        try (Scope ignored = traceContext.makeCurrent(context.getMessage())) {
            Observation.createNotStarted("eventlab.servicebus.process", observations)
                    .lowCardinalityKeyValue("messaging.destination", "payment-commands")
                    .observe(() -> handle(context));
        }
    }

    private void handle(ServiceBusReceivedMessageContext context) {
        applyDelay();
        if (MessageTypes.COMPENSATE_PAYMENT.equals(context.getMessage().getSubject())) {
            EventEnvelope<CompensatePayment> command = codec.decode(
                    context.getMessage().getBody(), CompensatePayment.class);
            compensationHandler.handle(command);
        } else {
            EventEnvelope<AuthorizePayment> command = codec.decode(
                    context.getMessage().getBody(), AuthorizePayment.class);
            handler.handle(command);
        }
        context.complete();
    }

    private void applyDelay() {
        if (processingDelayMillis == 0) return;
        try {
            Thread.sleep(processingDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment constraint interrupted", exception);
        }
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
