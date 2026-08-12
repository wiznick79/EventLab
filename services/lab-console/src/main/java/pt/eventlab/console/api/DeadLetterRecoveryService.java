package pt.eventlab.console.api;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
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
            for (ServiceBusReceivedMessage deadLetter : receiver.receiveMessages(20, Duration.ofSeconds(5))) {
                Object value = deadLetter.getApplicationProperties().get("workflowId");
                if (workflowId.toString().equals(String.valueOf(value))) {
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
                    return;
                }
                receiver.abandon(deadLetter);
            }
        }
        throw new IllegalStateException("No dead-lettered fulfilment command found for " + workflowId);
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
