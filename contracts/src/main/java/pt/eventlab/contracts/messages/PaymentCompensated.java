package pt.eventlab.contracts.messages;

import java.util.Objects;
import java.util.UUID;

public record PaymentCompensated(UUID paymentId, UUID workflowId) {
    public PaymentCompensated {
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(workflowId, "workflowId is required");
    }
}
