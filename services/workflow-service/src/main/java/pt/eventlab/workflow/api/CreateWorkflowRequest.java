package pt.eventlab.workflow.api;

import java.math.BigDecimal;
import java.util.UUID;
import pt.eventlab.contracts.ExperimentPlan;

public record CreateWorkflowRequest(
        String scenarioId,
        ExperimentPlan experimentPlan,
        BigDecimal amount,
        String currency,
        UUID idempotencyKey) {

    String resolvedScenarioId() {
        if (experimentPlan != null) return experimentPlan.scenarioId();
        return scenarioId;
    }
}
