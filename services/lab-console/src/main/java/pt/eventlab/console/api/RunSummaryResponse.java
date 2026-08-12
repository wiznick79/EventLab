package pt.eventlab.console.api;

import java.time.Instant;
import java.util.UUID;
import pt.eventlab.console.domain.ExperimentRun;
import pt.eventlab.contracts.ExperimentPlan;

public record RunSummaryResponse(
        UUID workflowId,
        UUID experimentPlanId,
        String scenarioId,
        ExperimentPlan experimentPlan,
        String expectedInvariant,
        String state,
        Instant createdAt) {

    public static RunSummaryResponse from(ExperimentRun run, String state) {
        return new RunSummaryResponse(run.workflowId(), run.experimentPlanId(), run.scenarioId(),
                run.plan(), run.expectedInvariant(), state, run.createdAt());
    }
}
