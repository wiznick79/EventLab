package pt.eventlab.fulfilment.messaging;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.RequestFulfilment;
import pt.eventlab.fulfilment.domain.FulfilmentApplicationService;
import pt.eventlab.fulfilment.domain.FulfilmentAttemptResult;
import pt.eventlab.messaging.ServiceBusEnvelopeCodec;
import pt.eventlab.messaging.ServiceBusClients;

@Component
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class RequestFulfilmentProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestFulfilmentProcessor.class);
    private final ServiceBusEnvelopeCodec codec;
    private final FulfilmentApplicationService fulfilments;
    private final ServiceBusProcessorClient processor;

    RequestFulfilmentProcessor(FulfilmentMessagingProperties properties,
            ServiceBusEnvelopeCodec codec, FulfilmentApplicationService fulfilments) {
        this.codec = codec;
        this.fulfilments = fulfilments;
        this.processor = ServiceBusClients.create(
                        properties.connectionString(), properties.fullyQualifiedNamespace())
                .processor().queueName(properties.fulfilmentCommandsQueue()).disableAutoComplete()
                .maxConcurrentCalls(1).processMessage(this::process)
                .processError(error -> LOGGER.error("Fulfilment processor error", error.getException()))
                .buildProcessorClient();
    }

    @PostConstruct void start() { processor.start(); }

    private void process(ServiceBusReceivedMessageContext context) {
        EventEnvelope<RequestFulfilment> command = codec.decode(
                context.getMessage().getBody(), RequestFulfilment.class);
        if (command.schemaVersion() != 1) {
            FulfilmentAttemptResult poison = fulfilments.rejectUnsupportedVersion(command, 1, 3);
            if (poison.deadLetter()) {
                context.deadLetter(new DeadLetterOptions()
                        .setDeadLetterReason("UnsupportedContractVersion")
                        .setDeadLetterErrorDescription("Received schema version "
                                + command.schemaVersion() + "; supported version is 1"));
            } else {
                context.abandon();
            }
            return;
        }
        FulfilmentAttemptResult result = fulfilments.attempt(command);
        if (result.completed()) {
            context.complete();
        } else if (result.deadLetter()) {
            context.deadLetter(new DeadLetterOptions().setDeadLetterReason("FulfilmentRetriesExhausted")
                    .setDeadLetterErrorDescription("Simulated provider unavailable after " + result.attempt() + " attempts"));
        } else {
            try {
                Thread.sleep(result.retryDelayMs());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            context.abandon();
        }
    }

    @PreDestroy void close() { processor.close(); }
}
