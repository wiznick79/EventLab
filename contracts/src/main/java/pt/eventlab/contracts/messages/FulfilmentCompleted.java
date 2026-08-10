package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record FulfilmentCompleted(UUID workflowId, UUID fulfilmentId) {

    public FulfilmentCompleted {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(fulfilmentId, "fulfilmentId is required");
    }
}
