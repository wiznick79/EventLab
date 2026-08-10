package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;
import pt.eventlab.contracts.WorkflowState;

public record WorkflowCompleted(UUID workflowId, WorkflowState finalState) {

    public WorkflowCompleted {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(finalState, "finalState is required");
    }
}
