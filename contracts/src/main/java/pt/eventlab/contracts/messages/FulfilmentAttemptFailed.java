package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record FulfilmentAttemptFailed(
        UUID workflowId,
        int attempt,
        int maximumAttempts,
        long nextDelayMilliseconds) {

    public FulfilmentAttemptFailed {
        Objects.requireNonNull(workflowId, "workflowId is required");
        if (attempt < 1 || maximumAttempts < attempt || nextDelayMilliseconds < 0) {
            throw new IllegalArgumentException("invalid retry attempt metadata");
        }
    }
}
