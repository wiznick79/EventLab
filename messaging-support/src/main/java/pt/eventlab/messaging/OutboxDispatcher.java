package pt.eventlab.messaging;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

public class OutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxStore outbox;
    private final OutboxTransport transport;
    private final AtomicBoolean failAfterSend;

    public OutboxDispatcher(OutboxStore outbox, OutboxTransport transport) {
        this(outbox, transport, false);
    }

    public OutboxDispatcher(OutboxStore outbox, OutboxTransport transport, boolean failOnceAfterSend) {
        this.outbox = outbox;
        this.transport = transport;
        this.failAfterSend = new AtomicBoolean(failOnceAfterSend);
    }

    @Scheduled(fixedDelayString = "${eventlab.messaging.outbox-delay:250}")
    @Transactional
    public void dispatch() {
        for (OutboxMessage message : outbox.lockNextBatch(50)) {
            try {
                transport.send(message);
                if (failAfterSend.compareAndSet(true, false)) {
                    throw new IllegalStateException("Injected failure after broker send and before outbox acknowledgement");
                }
                outbox.markPublished(message.outboxId());
            } catch (RuntimeException exception) {
                boolean quarantined = outbox.markFailed(message, exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage());
                if (quarantined) {
                    LOGGER.error("Outbox message {} quarantined after the retry budget", message.outboxId(), exception);
                } else {
                    LOGGER.warn("Outbox delivery failed for {}", message.outboxId(), exception);
                }
            }
        }
    }
}
