package pt.eventlab.fulfilment.messaging;

import pt.eventlab.contracts.EventEnvelope;

public interface FulfilmentMessagePublisher {
    void publish(EventEnvelope<?> event);
}
