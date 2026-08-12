package pt.eventlab.workflow.api;

import java.math.BigDecimal;
import pt.eventlab.contracts.ExperimentPlan;

public record CreateWorkflowRequest(String scenarioId, ExperimentPlan experimentPlan, BigDecimal amount, String currency) {

    String resolvedScenarioId() {
        if (experimentPlan != null) return experimentPlan.scenarioId();
        return scenarioId;
    }
}
