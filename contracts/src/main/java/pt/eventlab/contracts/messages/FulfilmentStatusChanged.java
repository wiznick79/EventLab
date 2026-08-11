package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record FulfilmentStatusChanged(UUID workflowId, long aggregateVersion, String outcome) {
    public FulfilmentStatusChanged {
        Objects.requireNonNull(workflowId, "workflowId is required");
        if (aggregateVersion < 1) throw new IllegalArgumentException("aggregateVersion must be positive");
        Objects.requireNonNull(outcome, "outcome is required");
    }
}
