package pt.eventlab.console.api;

import java.math.BigDecimal;
import java.util.UUID;
import pt.eventlab.contracts.ExperimentPlan;

public record StartRunRequest(
        String scenarioId,
        ExperimentPlan experimentPlan,
        BigDecimal amount,
        String currency,
        UUID idempotencyKey) {

    public StartRunRequest(
            String scenarioId, ExperimentPlan experimentPlan, BigDecimal amount, String currency) {
        this(scenarioId, experimentPlan, amount, currency, null);
    }

    public ExperimentPlan resolvedExperimentPlan() {
        return experimentPlan != null ? experimentPlan : ExperimentPlan.preset(scenarioId);
    }

    StartRunRequest withIdempotencyKey(UUID value) {
        return new StartRunRequest(scenarioId, experimentPlan, amount, currency, value);
    }
}
