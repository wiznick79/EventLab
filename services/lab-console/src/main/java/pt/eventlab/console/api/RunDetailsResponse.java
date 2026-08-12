package pt.eventlab.console.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.eventlab.console.domain.ExperimentRun;
import pt.eventlab.contracts.ExperimentPlan;

public record RunDetailsResponse(
        UUID workflowId,
        UUID experimentPlanId,
        String scenarioId,
        ExperimentPlan experimentPlan,
        String expectedInvariant,
        String state,
        Instant createdAt,
        List<TimelineEventResponse> timeline) {

    public static RunDetailsResponse from(
            ExperimentRun run, String state, List<TimelineEventResponse> timeline) {
        return new RunDetailsResponse(run.workflowId(), run.experimentPlanId(), run.scenarioId(),
                run.plan(), run.expectedInvariant(), state, run.createdAt(), timeline);
    }
}
