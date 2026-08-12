package pt.eventlab.messaging;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OutboxDispatcherTest {

    @Test
    void retriesWhenTheBrokerAcceptedAMessageBeforeAcknowledgementFailed() {
        OutboxStore outbox = mock(OutboxStore.class);
        OutboxTransport transport = mock(OutboxTransport.class);
        OutboxMessage message = new OutboxMessage(
                UUID.randomUUID(), UUID.randomUUID(), OutboxDestination.TOPIC,
                "business-events", "{}", Map.of());
        when(outbox.lockNextBatch(50)).thenReturn(List.of(message));
        OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, transport, true);

        dispatcher.dispatch();
        dispatcher.dispatch();

        verify(transport, times(2)).send(message);
        InOrder updates = inOrder(outbox);
        updates.verify(outbox).markFailed(
                message.outboxId(), "Injected failure after broker send and before outbox acknowledgement");
        updates.verify(outbox).markPublished(message.outboxId());
    }
}
