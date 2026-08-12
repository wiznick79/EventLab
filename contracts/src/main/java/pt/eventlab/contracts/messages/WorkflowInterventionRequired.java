package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record WorkflowInterventionRequired(UUID workflowId, String timedOutState, String reason) {
    public WorkflowInterventionRequired(UUID workflowId, String timedOutState) {
        this(workflowId, timedOutState, "SAGA_DEADLINE_EXPIRED");
    }

    public WorkflowInterventionRequired {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(timedOutState, "timedOutState is required");
        reason = reason == null ? "SAGA_DEADLINE_EXPIRED" : reason;
    }
}
