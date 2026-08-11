package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record StaleEventIgnored(
        UUID workflowId, String eventType, long receivedVersion, long currentVersion) {
    public StaleEventIgnored {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(eventType, "eventType is required");
    }
}
