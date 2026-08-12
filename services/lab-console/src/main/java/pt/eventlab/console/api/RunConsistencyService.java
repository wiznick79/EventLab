package pt.eventlab.console.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pt.eventlab.console.domain.ExperimentRunRegistry;

@Service
class RunConsistencyService {

    private static final long GRACE_SECONDS = 5;
    private static final Set<String> TERMINAL_STATES = Set.of(
            "COMPLETED", "COMPENSATED", "FAILED_REQUIRES_INTERVENTION");

    private final Clock clock = Clock.systemUTC();
    private final ExperimentRunRegistry runs;
    private final WorkflowClient workflow;

    RunConsistencyService(ExperimentRunRegistry runs, WorkflowClient workflow) {
        this.runs = runs;
        this.workflow = workflow;
    }

    RunConsistencyResponse inspect(UUID workflowId) {
        RunDetailsResponse projected = runs.details(workflowId);
        try {
            WorkflowClient.WorkflowSnapshot authoritative = workflow.inspect(workflowId);
            return compare(workflowId, authoritative, projected.state(), clock.instant());
        } catch (RuntimeException exception) {
            return new RunConsistencyResponse(workflowId, "SOURCE_UNAVAILABLE", null,
                    projected.state(), null, 0,
                    "Workflow's authoritative state is temporarily unavailable");
        }
    }

    static RunConsistencyResponse compare(UUID workflowId,
            WorkflowClient.WorkflowSnapshot authoritative, String projectedState, Instant now) {
        if (!TERMINAL_STATES.contains(authoritative.state())) {
            return new RunConsistencyResponse(workflowId, "IN_FLIGHT", authoritative.state(),
                    projectedState, authoritative.updatedAt(), 0,
                    "The workflow is still executing; intermediate views may differ");
        }
        if (authoritative.state().equals(projectedState)) {
            return new RunConsistencyResponse(workflowId, "CONSISTENT", authoritative.state(),
                    projectedState, authoritative.updatedAt(), 0,
                    "Authoritative and projected terminal states agree");
        }
        long lag = Math.max(0, Duration.between(authoritative.updatedAt(), now).toSeconds());
        String status = lag <= GRACE_SECONDS ? "CATCHING_UP" : "PROJECTION_BEHIND";
        String explanation = status.equals("CATCHING_UP")
                ? "The terminal event is within the projection grace period"
                : "Workflow is terminal but the evidence projection has not caught up";
        return new RunConsistencyResponse(workflowId, status, authoritative.state(),
                projectedState, authoritative.updatedAt(), lag, explanation);
    }
}
