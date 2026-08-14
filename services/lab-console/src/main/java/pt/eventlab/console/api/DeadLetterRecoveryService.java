package pt.eventlab.console.api;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pt.eventlab.console.messaging.LabConsoleMessagingProperties;
import pt.eventlab.contracts.messages.FulfilmentRecoveryRequested;
import pt.eventlab.messaging.ServiceBusClients;

@Service
class DeadLetterRecoveryService {
    private static final int PEEK_BATCH_SIZE = 100;
    private static final int MAX_INSPECTED_MESSAGES = 1_000;
    private final FulfilmentClient fulfilment;
    private final LabConsoleMessagingProperties properties;

    DeadLetterRecoveryService(FulfilmentClient fulfilment, LabConsoleMessagingProperties properties) {
        this.fulfilment = fulfilment;
        this.properties = properties;
    }

    void recover(UUID workflowId) {
        recover(workflowId, "lab-console");
    }

    void recover(UUID workflowId, String initiatedBy) {
        if (!properties.enabled()) throw new IllegalStateException("Messaging is disabled");
        var clients = ServiceBusClients.create(
                properties.connectionString(), properties.fullyQualifiedNamespace());
        try (ServiceBusReceiverClient receiver = clients.receiver()
                     .queueName(properties.fulfilmentCommandsQueue()).subQueue(SubQueue.DEAD_LETTER_QUEUE)
                     .receiveMode(ServiceBusReceiveMode.PEEK_LOCK).buildClient();
             ServiceBusSenderClient sender = clients.sender()
                     .queueName(properties.fulfilmentCommandsQueue()).buildClient()) {
            List<ServiceBusReceivedMessage> unrelated = new ArrayList<>();
            try {
                int inspected = 0;
                while (inspected < MAX_INSPECTED_MESSAGES) {
                    int batchSize = Math.min(PEEK_BATCH_SIZE, MAX_INSPECTED_MESSAGES - inspected);
                    List<ServiceBusReceivedMessage> batch = receiver
                            .receiveMessages(batchSize, Duration.ofSeconds(2)).stream().toList();
                    if (batch.isEmpty()) break;
                    for (ServiceBusReceivedMessage deadLetter : batch) {
                        inspected++;
                        Object value = deadLetter.getApplicationProperties().get("workflowId");
                        if (!workflowId.toString().equals(String.valueOf(value))) {
                            unrelated.add(deadLetter);
                            continue;
                        }
                        if (!isReplayable(deadLetter)) {
                            abandonBestEffort(receiver, deadLetter);
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "This dead letter is quarantined evidence and cannot be replayed");
                        }
                        replay(workflowId, initiatedBy, receiver, sender, deadLetter);
                        return;
                    }
                }
            } finally {
                unrelated.forEach(message -> abandonBestEffort(receiver, message));
            }
        }
        throw new IllegalStateException("No dead-lettered fulfilment command found for " + workflowId);
    }

    private void replay(UUID workflowId, String initiatedBy, ServiceBusReceiverClient receiver,
            ServiceBusSenderClient sender, ServiceBusReceivedMessage deadLetter) {
        String replayMessageId = UUID.randomUUID().toString();
        FulfilmentRecoveryRequested audit = new FulfilmentRecoveryRequested(
                workflowId, deadLetter.getMessageId(), replayMessageId,
                initiatedBy, "Simulated dependency restored after retry exhaustion");
        fulfilment.recover(audit);
        ServiceBusMessage replay = new ServiceBusMessage(deadLetter.getBody())
                .setSubject(deadLetter.getSubject()).setContentType(deadLetter.getContentType())
                .setCorrelationId(deadLetter.getCorrelationId()).setMessageId(replayMessageId);
        deadLetter.getApplicationProperties().forEach(replay.getApplicationProperties()::put);
        replay.getApplicationProperties().put("replayedFromDeadLetter", true);
        sender.sendMessage(replay);
        receiver.complete(deadLetter);
    }

    private boolean isReplayable(ServiceBusReceivedMessage message) {
        return "FulfilmentRetriesExhausted".equals(message.getDeadLetterReason());
    }

    private void abandonBestEffort(
            ServiceBusReceiverClient receiver, ServiceBusReceivedMessage message) {
        try {
            receiver.abandon(message);
        } catch (RuntimeException ignored) {
            // A lock may expire during a bounded scan; the broker will make the message available again.
        }
    }

    DeadLetterInspectionResponse inspect(UUID workflowId) {
        String queue = properties.fulfilmentCommandsQueue();
        if (!properties.enabled()) return DeadLetterInspectionResponse.unavailable(workflowId, queue);
        var clients = ServiceBusClients.create(
                properties.connectionString(), properties.fullyQualifiedNamespace());
        try (ServiceBusReceiverClient receiver = clients.receiver()
                     .queueName(queue).subQueue(SubQueue.DEAD_LETTER_QUEUE)
                     .receiveMode(ServiceBusReceiveMode.PEEK_LOCK).buildClient()) {
            long sequenceNumber = 0;
            int inspected = 0;
            while (inspected < MAX_INSPECTED_MESSAGES) {
                var batch = receiver.peekMessages(PEEK_BATCH_SIZE, sequenceNumber).stream().toList();
                if (batch.isEmpty()) break;
                for (ServiceBusReceivedMessage deadLetter : batch) {
                    inspected++;
                    Object value = deadLetter.getApplicationProperties().get("workflowId");
                    if (workflowId.toString().equals(String.valueOf(value))) {
                        return DeadLetterInspectionResponse.found(workflowId, queue, deadLetter);
                    }
                }
                if (batch.size() < PEEK_BATCH_SIZE) break;
                sequenceNumber = batch.get(batch.size() - 1).getSequenceNumber() + 1;
            }
        }
        return DeadLetterInspectionResponse.notFound(workflowId, queue);
    }
}
