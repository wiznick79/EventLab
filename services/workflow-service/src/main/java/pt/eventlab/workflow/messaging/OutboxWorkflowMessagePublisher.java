package pt.eventlab.workflow.messaging;

import org.springframework.stereotype.Component;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.messaging.OutboxDestination;
import pt.eventlab.messaging.OutboxStore;

@Component
class OutboxWorkflowMessagePublisher implements WorkflowMessagePublisher {

    private final WorkflowMessagingProperties properties;
    private final OutboxStore outbox;

    OutboxWorkflowMessagePublisher(WorkflowMessagingProperties properties, OutboxStore outbox) {
        this.properties = properties;
        this.outbox = outbox;
    }

    @Override
    public void sendPaymentCommand(EventEnvelope<?> command) {
        outbox.enqueue(OutboxDestination.QUEUE, properties.paymentCommandsQueue(), command);
    }

    @Override
    public void publishBusinessEvent(EventEnvelope<?> event) {
        outbox.enqueue(OutboxDestination.TOPIC, properties.businessEventsTopic(), event);
    }
}
