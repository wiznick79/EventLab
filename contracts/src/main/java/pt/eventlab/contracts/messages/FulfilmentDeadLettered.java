package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record FulfilmentDeadLettered(UUID workflowId, int attempts, String reason) {

    public FulfilmentDeadLettered {
        Objects.requireNonNull(workflowId, "workflowId is required");
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }
}
