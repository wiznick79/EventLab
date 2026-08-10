package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record FulfilmentRecoveryRequested(
        UUID workflowId,
        String originalMessageId,
        String replayMessageId,
        String initiatedBy,
        String reason) {

    public FulfilmentRecoveryRequested {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(originalMessageId, "originalMessageId is required");
        Objects.requireNonNull(replayMessageId, "replayMessageId is required");
        Objects.requireNonNull(initiatedBy, "initiatedBy is required");
        Objects.requireNonNull(reason, "reason is required");
    }
}
