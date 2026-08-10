package pt.eventlab.workflow.messaging;

import pt.eventlab.contracts.EventEnvelope;

public interface WorkflowMessagePublisher {

    void sendPaymentCommand(EventEnvelope<?> command);

    void sendFulfilmentCommand(EventEnvelope<?> command);

    void publishBusinessEvent(EventEnvelope<?> event);
}
