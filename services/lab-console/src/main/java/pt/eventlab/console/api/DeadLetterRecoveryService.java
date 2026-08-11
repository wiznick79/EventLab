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
    private final FulfilmentClient fulfilment;
    private final LabConsoleMessagingProperties properties;

    DeadLetterRecoveryService(FulfilmentClient fulfilment, LabConsoleMessagingProperties properties) {
        this.fulfilment = fulfilment;
        this.properties = properties;
    }

    void recover(UUID workflowId) {
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
                            "lab-console", "Simulated dependency restored after retry exhaustion");
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
}
