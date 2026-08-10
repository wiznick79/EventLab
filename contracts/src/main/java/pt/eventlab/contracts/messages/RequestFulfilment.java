package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record RequestFulfilment(UUID workflowId, String scenarioId) {

    public RequestFulfilment {
        Objects.requireNonNull(workflowId, "workflowId is required");
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId is required");
        }
    }
}
