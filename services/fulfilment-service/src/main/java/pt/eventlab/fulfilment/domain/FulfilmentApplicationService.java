package pt.eventlab.fulfilment.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.MessageTypes;
import pt.eventlab.contracts.messages.FulfilmentAttemptFailed;
import pt.eventlab.contracts.messages.FulfilmentCompleted;
import pt.eventlab.contracts.messages.FulfilmentDeadLettered;
import pt.eventlab.contracts.messages.FulfilmentRecoveryRequested;
import pt.eventlab.contracts.messages.RequestFulfilment;
import pt.eventlab.fulfilment.messaging.FulfilmentMessagePublisher;
import pt.eventlab.messaging.InboxStore;

@Service
public class FulfilmentApplicationService {
    private static final int MAX_ATTEMPTS = 4;
    private static final String HANDLER = "fulfilment.request";
    private final Clock clock = Clock.systemUTC();
    private final FulfilmentRepository fulfilments;
    private final FulfilmentMessagePublisher messages;
    private final InboxStore inbox;

    FulfilmentApplicationService(FulfilmentRepository fulfilments,
            FulfilmentMessagePublisher messages, InboxStore inbox) {
        this.fulfilments = fulfilments;
        this.messages = messages;
        this.inbox = inbox;
    }

    @Transactional
    public FulfilmentAttemptResult attempt(EventEnvelope<RequestFulfilment> command) {
        if (inbox.contains(command.eventId(), HANDLER)) {
            return new FulfilmentAttemptResult(true, false, 0, 0);
        }
        Instant now = clock.instant();
        Fulfilment job = fulfilments.findByWorkflowId(command.workflowId())
                .orElseGet(() -> fulfilments.save(Fulfilment.start(
                        command.workflowId(), command.payload().scenarioId(), now)));
        int attempt = job.attempt();
        if (!job.available()) {
            boolean exhausted = attempt >= MAX_ATTEMPTS;
            long delay = exhausted ? 0 : 250L << (attempt - 1);
            messages.publish(event(command, MessageTypes.FULFILMENT_ATTEMPT_FAILED,
                    new FulfilmentAttemptFailed(command.workflowId(), attempt, MAX_ATTEMPTS, delay)));
            if (exhausted) {
                messages.publish(event(command, MessageTypes.FULFILMENT_DEAD_LETTERED,
                        new FulfilmentDeadLettered(command.workflowId(), attempt, "Simulated provider unavailable")));
            }
            return new FulfilmentAttemptResult(false, exhausted, attempt, delay);
        }
        if (!inbox.claim(command.eventId(), HANDLER)) {
            return new FulfilmentAttemptResult(true, false, attempt, 0);
        }
        job.complete(now);
        messages.publish(event(command, MessageTypes.FULFILMENT_COMPLETED,
                new FulfilmentCompleted(command.workflowId(), job.id())));
        return new FulfilmentAttemptResult(true, false, attempt, 0);
    }

    @Transactional
    public void recover(FulfilmentRecoveryRequested recovery) {
        UUID workflowId = recovery.workflowId();
        Fulfilment job = fulfilments.findByWorkflowId(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fulfilment " + workflowId));
        job.recover();
        Instant now = clock.instant();
        messages.publish(new EventEnvelope<>(UUID.randomUUID(), MessageTypes.FULFILMENT_RECOVERY_REQUESTED,
                1, workflowId, null, workflowId, now, recovery));
    }

    private <T> EventEnvelope<T> event(EventEnvelope<?> source, String type, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, 1, source.workflowId(), source.eventId(),
                source.correlationId(), clock.instant(), payload);
    }
}
