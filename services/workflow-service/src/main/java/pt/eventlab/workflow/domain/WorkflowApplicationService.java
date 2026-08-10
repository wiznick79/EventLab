package pt.eventlab.workflow.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.MessageTypes;
import pt.eventlab.contracts.WorkflowState;
import pt.eventlab.contracts.messages.AuthorizePayment;
import pt.eventlab.contracts.messages.PaymentAuthorized;
import pt.eventlab.contracts.messages.WorkflowCompleted;
import pt.eventlab.contracts.messages.WorkflowStarted;
import pt.eventlab.workflow.messaging.WorkflowMessagePublisher;

@Service
public class WorkflowApplicationService {

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
        workflow.complete(clock.instant());
        workflows.flush();

        messages.publishBusinessEvent(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.WORKFLOW_COMPLETED, 1, workflow.id(), event.eventId(),
                event.correlationId(), clock.instant(),
                new WorkflowCompleted(workflow.id(), WorkflowState.COMPLETED)));
    }
}
