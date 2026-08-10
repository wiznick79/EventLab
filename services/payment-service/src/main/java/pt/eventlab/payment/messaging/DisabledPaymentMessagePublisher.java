package pt.eventlab.payment.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.eventlab.contracts.EventEnvelope;

@Component
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "false", matchIfMissing = true)
class DisabledPaymentMessagePublisher implements PaymentMessagePublisher {
    @Override
    public void publish(EventEnvelope<?> event) {
        // Messaging is intentionally disabled for isolated service tests.
    }
}
