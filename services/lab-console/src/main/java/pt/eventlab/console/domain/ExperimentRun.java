package pt.eventlab.console.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pt.eventlab.contracts.ExperimentPlan;
import pt.eventlab.contracts.FulfilmentBehavior;

@Entity
@Table(name = "experiment_runs")
public class ExperimentRun {

    @Id
    private UUID workflowId;
    private UUID experimentPlanId;
    private String scenarioId;
    private int paymentResultDeliveries;
    @Enumerated(EnumType.STRING)
    private FulfilmentBehavior fulfilmentBehavior;
    private String expectedInvariant;
    private Instant createdAt;

    protected ExperimentRun() {
    }

    public ExperimentRun(UUID workflowId, UUID experimentPlanId, String scenarioId,
            ExperimentPlan plan, Instant createdAt) {
        this.workflowId = workflowId;
        this.experimentPlanId = experimentPlanId;
        this.scenarioId = scenarioId;
        this.paymentResultDeliveries = plan.paymentResultDeliveries();
        this.fulfilmentBehavior = plan.fulfilmentBehavior();
        this.expectedInvariant = plan.expectedInvariant();
        this.createdAt = createdAt;
    }

    public UUID workflowId() { return workflowId; }
    public UUID experimentPlanId() { return experimentPlanId; }
    public String scenarioId() { return scenarioId; }
    public ExperimentPlan plan() { return new ExperimentPlan(paymentResultDeliveries, fulfilmentBehavior); }
    public String expectedInvariant() { return expectedInvariant; }
    public Instant createdAt() { return createdAt; }
}
