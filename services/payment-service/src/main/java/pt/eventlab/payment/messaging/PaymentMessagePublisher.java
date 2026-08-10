package pt.eventlab.payment.messaging;

import pt.eventlab.contracts.EventEnvelope;

public interface PaymentMessagePublisher {
    void publish(EventEnvelope<?> event, int copies);
}
