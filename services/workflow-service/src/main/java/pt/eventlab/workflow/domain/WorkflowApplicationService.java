package pt.eventlab.workflow.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.ExperimentPlan;
import pt.eventlab.contracts.FulfilmentBehavior;
import pt.eventlab.contracts.MessageTypes;
import pt.eventlab.contracts.WorkflowState;
import pt.eventlab.contracts.messages.AuthorizePayment;
import pt.eventlab.contracts.messages.PaymentAuthorized;
import pt.eventlab.contracts.messages.FulfilmentCommandQueued;
import pt.eventlab.contracts.messages.FulfilmentCompleted;
import pt.eventlab.contracts.messages.FulfilmentDeadLettered;
import pt.eventlab.contracts.messages.FulfilmentRejected;
import pt.eventlab.contracts.messages.CompensatePayment;
import pt.eventlab.contracts.messages.PaymentCompensated;
import pt.eventlab.contracts.messages.RequestFulfilment;
import pt.eventlab.contracts.messages.WorkflowCompleted;
import pt.eventlab.contracts.messages.WorkflowStarted;
import pt.eventlab.contracts.messages.WorkflowCompensated;
import pt.eventlab.contracts.messages.WorkflowInterventionRequired;
import pt.eventlab.contracts.messages.FulfilmentStatusChanged;
import pt.eventlab.contracts.messages.StaleEventIgnored;
import pt.eventlab.workflow.messaging.WorkflowMessagePublisher;

@Service
public class WorkflowApplicationService {

    private static final Duration STEP_TIMEOUT = Duration.ofMinutes(2);

    private final Clock clock;
    private final WorkflowMessagePublisher messages;
    private final WorkflowRepository workflows;

    @Autowired
    WorkflowApplicationService(WorkflowRepository workflows, WorkflowMessagePublisher messages) {
        this(workflows, messages, Clock.systemUTC());
    }

    WorkflowApplicationService(WorkflowRepository workflows, WorkflowMessagePublisher messages, Clock clock) {
        this.workflows = workflows;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public WorkflowRun start(String scenarioId, BigDecimal amount, String currency) {
        Instant now = clock.instant();
        WorkflowRun workflow = workflows.saveAndFlush(WorkflowRun.start(scenarioId, amount, currency, now));
        UUID correlationId = workflow.id();

        messages.publishBusinessEvent(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.WORKFLOW_STARTED, 1, workflow.id(), null,
                correlationId, now,
                new WorkflowStarted(workflow.id(), workflow.scenarioId(), workflow.amount(), workflow.currency())));

        messages.sendPaymentCommand(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.AUTHORIZE_PAYMENT, 1, workflow.id(), null,
                correlationId, now,
                new AuthorizePayment(
                        workflow.id(), workflow.scenarioId(), workflow.amount(), workflow.currency())));
        return workflow;
    }

    @Transactional(readOnly = true)
    public WorkflowRun get(UUID workflowId) {
        return workflows.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown workflow " + workflowId));
    }

    @Transactional
    public void recordPaymentAuthorized(EventEnvelope<PaymentAuthorized> event) {
        WorkflowRun workflow = workflows.findById(event.workflowId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown workflow " + event.workflowId()));
        Instant now = clock.instant();
        workflow.recordPaymentAuthorized(now, now.plus(STEP_TIMEOUT));
        workflows.flush();

        ExperimentPlan plan = ExperimentPlan.preset(workflow.scenarioId());
        int schemaVersion = plan.fulfilmentBehavior() == FulfilmentBehavior.UNSUPPORTED_CONTRACT ? 99 : 1;
        UUID commandEventId = UUID.randomUUID();
        messages.sendFulfilmentCommand(new EventEnvelope<>(
                commandEventId, MessageTypes.REQUEST_FULFILMENT, schemaVersion,
                workflow.id(), event.eventId(), event.correlationId(), clock.instant(),
                new RequestFulfilment(workflow.id(), workflow.scenarioId())));
        messages.publishBusinessEvent(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.FULFILMENT_COMMAND_QUEUED, 1,
                workflow.id(), event.eventId(), event.correlationId(), clock.instant(),
                new FulfilmentCommandQueued(workflow.id(), commandEventId)));
    }

    @Transactional
    public void recordFulfilmentRejected(EventEnvelope<FulfilmentRejected> event) {
        WorkflowRun workflow = workflow(event.workflowId());
        if (!workflow.observeFulfilmentVersion(event.payload().aggregateVersion())) {
            publishStaleIgnored(workflow, event.eventType(), event.payload().aggregateVersion(), event.eventId());
            return;
        }
        Instant now = clock.instant();
        workflow.beginCompensation(now, now.plus(STEP_TIMEOUT));
        messages.sendPaymentCommand(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.COMPENSATE_PAYMENT, 1, workflow.id(), event.eventId(),
                event.correlationId(), now, new CompensatePayment(workflow.id(), event.payload().reason())));
    }

    @Transactional
    public void recordPaymentCompensated(EventEnvelope<PaymentCompensated> event) {
        WorkflowRun workflow = workflow(event.workflowId());
        workflow.compensated(clock.instant());
        messages.publishBusinessEvent(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.WORKFLOW_COMPENSATED, 1, workflow.id(), event.eventId(),
                event.correlationId(), clock.instant(),
                new WorkflowCompensated(workflow.id(), WorkflowState.COMPENSATED)));
    }

    @Transactional
    public void expireTimedOutSteps() {
        Instant now = clock.instant();
        List<WorkflowRun> expired = workflows.findByStateInAndStepDeadlineBefore(
                List.of(WorkflowState.FULFILMENT_PENDING, WorkflowState.COMPENSATION_PENDING), now);
        for (WorkflowRun workflow : expired) {
            WorkflowState timedOut = workflow.requireIntervention(now);
            messages.publishBusinessEvent(new EventEnvelope<>(
                    UUID.randomUUID(), MessageTypes.WORKFLOW_INTERVENTION_REQUIRED, 1,
                    workflow.id(), null, workflow.id(), now,
                    new WorkflowInterventionRequired(workflow.id(), timedOut.name())));
        }
    }

    @Transactional
    public void recordFulfilmentDeadLettered(EventEnvelope<FulfilmentDeadLettered> event) {
        if (!event.payload().reason().startsWith("Unsupported contract")) return;
        WorkflowRun workflow = workflow(event.workflowId());
        WorkflowState failedStep = workflow.requireIntervention(clock.instant());
        messages.publishBusinessEvent(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.WORKFLOW_INTERVENTION_REQUIRED, 1,
                workflow.id(), event.eventId(), event.correlationId(), clock.instant(),
                new WorkflowInterventionRequired(
                        workflow.id(), failedStep.name(), "POISON_MESSAGE_QUARANTINED")));
    }

    @Transactional
    public void recordFulfilmentCompleted(EventEnvelope<FulfilmentCompleted> event) {
        WorkflowRun workflow = workflow(event.workflowId());
        if (!workflow.observeFulfilmentVersion(event.payload().aggregateVersion())) {
            publishStaleIgnored(workflow, event.eventType(), event.payload().aggregateVersion(), event.eventId());
            return;
        }
        workflow.complete(clock.instant());
        workflows.flush();

        messages.publishBusinessEvent(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.WORKFLOW_COMPLETED, 1, workflow.id(), event.eventId(),
                event.correlationId(), clock.instant(),
                new WorkflowCompleted(workflow.id(), WorkflowState.COMPLETED)));
    }

    @Transactional
    public long observeFulfilmentStatus(EventEnvelope<FulfilmentStatusChanged> event) {
        WorkflowRun workflow = workflow(event.workflowId());
        long received = event.payload().aggregateVersion();
        if (received <= workflow.lastFulfilmentVersion()) {
            publishStaleIgnored(workflow, event.eventType(), received, event.eventId());
            return workflow.lastFulfilmentVersion();
        }
        throw new IllegalStateException("Unexpected future fulfilment version " + received);
    }

    private void publishStaleIgnored(
            WorkflowRun workflow, String eventType, long receivedVersion, UUID causationId) {
        messages.publishBusinessEvent(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.STALE_EVENT_IGNORED, 1,
                workflow.id(), causationId, workflow.id(), clock.instant(),
                new StaleEventIgnored(
                        workflow.id(), eventType, receivedVersion, workflow.lastFulfilmentVersion())));
    }

    private WorkflowRun workflow(UUID workflowId) {
        return workflows.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown workflow " + workflowId));
    }
}
