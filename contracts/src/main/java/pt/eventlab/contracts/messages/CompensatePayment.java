package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record CompensatePayment(UUID workflowId, String reason) {
    public CompensatePayment {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(reason, "reason is required");
    }
}
