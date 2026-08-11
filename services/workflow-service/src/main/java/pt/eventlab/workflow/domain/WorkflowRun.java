package pt.eventlab.workflow.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pt.eventlab.contracts.WorkflowState;

@Entity
@Table(name = "workflow_runs")
public class WorkflowRun {

    @Id
    private UUID id;
    private String scenarioId;
    private BigDecimal amount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private WorkflowState state;
    @Version
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant stepDeadline;

    protected WorkflowRun() {
    }

    private WorkflowRun(UUID id, String scenarioId, BigDecimal amount, String currency, Instant now) {
        this.id = id;
        this.scenarioId = scenarioId;
        this.amount = amount;
        this.currency = currency;
        this.state = WorkflowState.PAYMENT_PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static WorkflowRun start(String scenarioId, BigDecimal amount, String currency, Instant now) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }
        return new WorkflowRun(UUID.randomUUID(), scenarioId, amount, currency.toUpperCase(), now);
    }

    public void recordPaymentAuthorized(Instant now, Instant deadline) {
        if (state != WorkflowState.PAYMENT_PENDING) {
            throw new IllegalStateException("Only a payment-pending workflow can accept a payment result");
        }
        state = WorkflowState.FULFILMENT_PENDING;
        updatedAt = now;
        stepDeadline = deadline;
    }

    public void beginCompensation(Instant now, Instant deadline) {
        if (state != WorkflowState.FULFILMENT_PENDING) {
            throw new IllegalStateException("Only a fulfilment-pending workflow can compensate");
        }
        state = WorkflowState.COMPENSATION_PENDING;
        updatedAt = now;
        stepDeadline = deadline;
    }

    public void compensated(Instant now) {
        if (state != WorkflowState.COMPENSATION_PENDING) {
            throw new IllegalStateException("Only a compensation-pending workflow can be compensated");
        }
        state = WorkflowState.COMPENSATED;
        updatedAt = now;
        stepDeadline = null;
    }

    public WorkflowState requireIntervention(Instant now) {
        if (state != WorkflowState.FULFILMENT_PENDING && state != WorkflowState.COMPENSATION_PENDING) {
            throw new IllegalStateException("Only an active saga step can time out");
        }
        WorkflowState timedOutState = state;
        state = WorkflowState.FAILED_REQUIRES_INTERVENTION;
        updatedAt = now;
        stepDeadline = null;
        return timedOutState;
    }

    public void complete(Instant now) {
        if (state != WorkflowState.FULFILMENT_PENDING) {
            throw new IllegalStateException("Only a fulfilment-pending workflow can complete");
        }
        state = WorkflowState.COMPLETED;
        updatedAt = now;
        stepDeadline = null;
    }

    public UUID id() { return id; }
    public String scenarioId() { return scenarioId; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    public WorkflowState state() { return state; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant stepDeadline() { return stepDeadline; }
}
