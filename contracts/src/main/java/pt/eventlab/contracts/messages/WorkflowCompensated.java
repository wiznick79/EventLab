package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;
import pt.eventlab.contracts.WorkflowState;

public record WorkflowCompensated(UUID workflowId, WorkflowState state) {
    public WorkflowCompensated {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(state, "state is required");
    }
}
