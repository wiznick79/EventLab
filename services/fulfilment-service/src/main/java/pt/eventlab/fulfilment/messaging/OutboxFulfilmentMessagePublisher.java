package pt.eventlab.fulfilment.messaging;

import org.springframework.stereotype.Component;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.messaging.OutboxDestination;
import pt.eventlab.messaging.OutboxStore;

@Component
class OutboxFulfilmentMessagePublisher implements FulfilmentMessagePublisher {
    private final FulfilmentMessagingProperties properties;
    private final OutboxStore outbox;

    OutboxFulfilmentMessagePublisher(FulfilmentMessagingProperties properties, OutboxStore outbox) {
        this.properties = properties;
        this.outbox = outbox;
    }

    @Override
    public void publish(EventEnvelope<?> event) {
        outbox.enqueue(OutboxDestination.TOPIC, properties.businessEventsTopic(), event, 1);
    }
}
