package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record FulfilmentRejected(UUID workflowId, String reason, long aggregateVersion) {
    public FulfilmentRejected {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(reason, "reason is required");
        if (aggregateVersion < 1) throw new IllegalArgumentException("aggregateVersion must be positive");
    }
}
