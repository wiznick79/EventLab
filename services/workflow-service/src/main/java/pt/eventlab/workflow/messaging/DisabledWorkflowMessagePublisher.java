package pt.eventlab.workflow.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.eventlab.contracts.EventEnvelope;

@Component
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "false", matchIfMissing = true)
class DisabledWorkflowMessagePublisher implements WorkflowMessagePublisher {

    @Override
    public void sendPaymentCommand(EventEnvelope<?> command) {
        // Messaging is intentionally disabled for isolated service tests.
    }

    @Override
    public void publishBusinessEvent(EventEnvelope<?> event) {
        // Messaging is intentionally disabled for isolated service tests.
    }
}
