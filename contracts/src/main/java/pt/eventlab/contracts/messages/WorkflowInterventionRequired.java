package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record WorkflowInterventionRequired(UUID workflowId, String timedOutState) {
    public WorkflowInterventionRequired {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(timedOutState, "timedOutState is required");
    }
}
