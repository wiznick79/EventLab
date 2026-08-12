package pt.eventlab.fulfilment.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.ExperimentPlan;
import pt.eventlab.contracts.FulfilmentBehavior;
import pt.eventlab.contracts.MessageTypes;
import pt.eventlab.contracts.messages.FulfilmentAttemptFailed;
import pt.eventlab.contracts.messages.FulfilmentCompleted;
import pt.eventlab.contracts.messages.FulfilmentDeadLettered;
import pt.eventlab.contracts.messages.FulfilmentMessageRejected;
import pt.eventlab.contracts.messages.FulfilmentRecoveryRequested;
import pt.eventlab.contracts.messages.FulfilmentRejected;
import pt.eventlab.contracts.messages.FulfilmentStatusChanged;
import pt.eventlab.contracts.messages.RequestFulfilment;
import pt.eventlab.fulfilment.messaging.FulfilmentMessagePublisher;
import pt.eventlab.messaging.BusinessDecisionTrace;
import pt.eventlab.messaging.InboxStore;

@Service
public class FulfilmentApplicationService {
    private static final String HANDLER = "fulfilment.request";
    private final BusinessDecisionTrace decisions;
    private final Clock clock = Clock.systemUTC();
    private final FulfilmentRepository fulfilments;
    private final FulfilmentMessagePublisher messages;
    private final InboxStore inbox;

    FulfilmentApplicationService(FulfilmentRepository fulfilments,
            FulfilmentMessagePublisher messages,
            InboxStore inbox,
            BusinessDecisionTrace decisions) {
        this.fulfilments = fulfilments;
        this.messages = messages;
        this.inbox = inbox;
        this.decisions = decisions;
    }

    @Transactional
    public FulfilmentAttemptResult attempt(EventEnvelope<RequestFulfilment> command) {
        return decisions.record(
                "eventlab.fulfilment.attempt.decision",
                command.eventId(), command.workflowId(), () -> attemptWithDecision(command));
    }

    @Transactional
    public FulfilmentAttemptResult rejectUnsupportedVersion(
            EventEnvelope<RequestFulfilment> command, int supportedVersion, int maxAttempts) {
        return decisions.record(
                "eventlab.fulfilment.contract.decision",
                command.eventId(), command.workflowId(), () -> {
            Instant now = clock.instant();
            Fulfilment job = fulfilments.findByWorkflowId(command.workflowId())
                    .orElseGet(() -> fulfilments.save(Fulfilment.start(
                            command.workflowId(), command.payload().scenarioId(), now)));
            int attempt = job.attempt();
            boolean exhausted = attempt >= maxAttempts;
            messages.publish(event(command, MessageTypes.FULFILMENT_MESSAGE_REJECTED,
                    new FulfilmentMessageRejected(command.workflowId(), command.schemaVersion(),
                            supportedVersion, attempt, maxAttempts)));
            if (exhausted) {
                messages.publish(event(command, MessageTypes.FULFILMENT_DEAD_LETTERED,
                        new FulfilmentDeadLettered(command.workflowId(), attempt,
                                "Unsupported contract version " + command.schemaVersion())));
            }
            FulfilmentAttemptResult result = new FulfilmentAttemptResult(false, exhausted, attempt, 0);
            return outcome(exhausted ? "UNSUPPORTED_CONTRACT_DEAD_LETTERED"
                    : "UNSUPPORTED_CONTRACT_REJECTED", false, result);
        });
    }

    private BusinessDecisionTrace.Outcome<FulfilmentAttemptResult> attemptWithDecision(
            EventEnvelope<RequestFulfilment> command) {
        if (inbox.contains(command.eventId(), HANDLER)) {
            return outcome("DUPLICATE_IGNORED", false,
                    new FulfilmentAttemptResult(true, false, 0, 0));
        }
        Instant now = clock.instant();
        Fulfilment job = fulfilments.findByWorkflowId(command.workflowId())
                .orElseGet(() -> fulfilments.save(Fulfilment.start(
                        command.workflowId(), command.payload().scenarioId(), now)));
        int attempt = job.attempt();
        ExperimentPlan plan = ExperimentPlan.preset(command.payload().scenarioId());
        if (!job.available()) {
            int maxAttempts = plan.fulfilmentMaxAttempts();
            boolean exhausted = attempt >= maxAttempts;
            long delay = exhausted ? 0 : 250L << (attempt - 1);
            messages.publish(event(command, MessageTypes.FULFILMENT_ATTEMPT_FAILED,
                    new FulfilmentAttemptFailed(command.workflowId(), attempt, maxAttempts, delay)));
            if (exhausted) {
                messages.publish(event(command, MessageTypes.FULFILMENT_DEAD_LETTERED,
                        new FulfilmentDeadLettered(command.workflowId(), attempt, "Simulated provider unavailable")));
            }
            return outcome(exhausted ? "DEAD_LETTERED" : "RETRY_SCHEDULED", true,
                    new FulfilmentAttemptResult(false, exhausted, attempt, delay));
        }
        if (!inbox.claim(command.eventId(), HANDLER)) {
            return outcome("DUPLICATE_IGNORED", false,
                    new FulfilmentAttemptResult(true, false, attempt, 0));
        }
        FulfilmentBehavior behavior = plan.fulfilmentBehavior();
        if (behavior == FulfilmentBehavior.BUSINESS_REJECTION) {
            job.reject(now);
            messages.publish(event(command, MessageTypes.FULFILMENT_REJECTED,
                    new FulfilmentRejected(command.workflowId(), "Simulated capacity rejection", 2)));
            return outcome("FULFILMENT_REJECTED", true,
                    new FulfilmentAttemptResult(true, false, attempt, 0));
        }
        job.complete(now);
        if (behavior == FulfilmentBehavior.STALE_AFTER_SUCCESS) {
            job.scheduleStaleEvent(now.plusSeconds(1));
        }
        messages.publish(event(command, MessageTypes.FULFILMENT_COMPLETED,
                new FulfilmentCompleted(command.workflowId(), job.id(), 2)));
        return outcome("FULFILMENT_COMPLETED", true,
                new FulfilmentAttemptResult(true, false, attempt, 0));
    }

    @Transactional
    public void publishDueStaleEvents() {
        Instant now = clock.instant();
        for (Fulfilment job : fulfilments.findByStaleEventSentFalseAndStaleEventDueAtBefore(now)) {
            messages.publish(new EventEnvelope<>(
                    UUID.randomUUID(), MessageTypes.FULFILMENT_STATUS_CHANGED, 1,
                    job.workflowId(), null, job.workflowId(), now,
                    new FulfilmentStatusChanged(job.workflowId(), 1, "REJECTED")));
            job.markStaleEventSent();
        }
    }

    @Transactional
    public void recover(FulfilmentRecoveryRequested recovery) {
        decisions.record(
                "eventlab.fulfilment.recovery.decision",
                UUID.fromString(recovery.replayMessageId()), recovery.workflowId(), () -> {
            recoverDependency(recovery);
            return new BusinessDecisionTrace.Outcome<>("RECOVERY_ACCEPTED", true, null, Map.of(
                    "eventlab.recovery.original_message_id", recovery.originalMessageId(),
                    "eventlab.recovery.replay_message_id", recovery.replayMessageId(),
                    "eventlab.recovery.initiated_by", recovery.initiatedBy()));
        });
    }

    private void recoverDependency(FulfilmentRecoveryRequested recovery) {
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

    private BusinessDecisionTrace.Outcome<FulfilmentAttemptResult> outcome(
            String decision, boolean stateChangeApplied, FulfilmentAttemptResult result) {
        return new BusinessDecisionTrace.Outcome<>(decision, stateChangeApplied, result, Map.of(
                "eventlab.delivery.attempt", Integer.toString(result.attempt()),
                "eventlab.delivery.retry_delay_ms", Long.toString(result.retryDelayMs()),
                "eventlab.delivery.dead_lettered", Boolean.toString(result.deadLetter())));
    }
}
