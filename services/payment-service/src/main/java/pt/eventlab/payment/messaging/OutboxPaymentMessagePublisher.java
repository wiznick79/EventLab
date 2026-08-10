package pt.eventlab.payment.messaging;

import org.springframework.stereotype.Component;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.messaging.OutboxDestination;
import pt.eventlab.messaging.OutboxStore;

@Component
class OutboxPaymentMessagePublisher implements PaymentMessagePublisher {

    private final PaymentMessagingProperties properties;
    private final OutboxStore outbox;

    OutboxPaymentMessagePublisher(PaymentMessagingProperties properties, OutboxStore outbox) {
        this.properties = properties;
        this.outbox = outbox;
    }

    @Override
    public void publish(EventEnvelope<?> event, int copies) {
        outbox.enqueue(OutboxDestination.TOPIC, properties.businessEventsTopic(), event, copies);
    }
}
