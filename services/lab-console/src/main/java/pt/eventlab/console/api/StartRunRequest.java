package pt.eventlab.console.api;

import java.math.BigDecimal;
import pt.eventlab.contracts.ExperimentPlan;

public record StartRunRequest(String scenarioId, ExperimentPlan experimentPlan, BigDecimal amount, String currency) {

    public ExperimentPlan resolvedExperimentPlan() {
        return experimentPlan != null ? experimentPlan : ExperimentPlan.preset(scenarioId);
    }
}
