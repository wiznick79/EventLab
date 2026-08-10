package pt.eventlab.workflow.messaging;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.messaging.ServiceBusMessageFactory;

@Component
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class ServiceBusWorkflowMessagePublisher implements WorkflowMessagePublisher {

    private final ServiceBusMessageFactory messages;
    private final ServiceBusSenderClient paymentCommands;
    private final ServiceBusSenderClient businessEvents;

    ServiceBusWorkflowMessagePublisher(
            WorkflowMessagingProperties properties,
            ServiceBusMessageFactory messages) {
        this.messages = messages;
        this.paymentCommands = new ServiceBusClientBuilder()
                .connectionString(properties.connectionString())
                .sender()
                .queueName(properties.paymentCommandsQueue())
                .buildClient();
        this.businessEvents = new ServiceBusClientBuilder()
                .connectionString(properties.connectionString())
                .sender()
                .topicName(properties.businessEventsTopic())
                .buildClient();
    }

    @Override
    public void sendPaymentCommand(EventEnvelope<?> command) {
        paymentCommands.sendMessage(messages.create(command));
    }

    @Override
    public void publishBusinessEvent(EventEnvelope<?> event) {
        businessEvents.sendMessage(messages.create(event));
    }

    @PreDestroy
    void close() {
        paymentCommands.close();
        businessEvents.close();
    }
}
