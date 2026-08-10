package pt.eventlab.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

public class OutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxStore outbox;
    private final OutboxTransport transport;

    public OutboxDispatcher(OutboxStore outbox, OutboxTransport transport) {
        this.outbox = outbox;
        this.transport = transport;
    }

    @Scheduled(fixedDelayString = "${eventlab.messaging.outbox-delay:250}")
    @Transactional
    public void dispatch() {
        for (OutboxMessage message : outbox.lockNextBatch(50)) {
            try {
                transport.send(message);
                outbox.markPublished(message.outboxId());
            } catch (RuntimeException exception) {
                outbox.markFailed(message.outboxId(), exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage());
                LOGGER.warn("Outbox delivery failed for {}", message.outboxId(), exception);
            }
        }
    }
}
