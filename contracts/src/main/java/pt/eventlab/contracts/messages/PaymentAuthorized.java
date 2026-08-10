package pt.eventlab.contracts.messages;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PaymentAuthorized(UUID paymentId, UUID workflowId, BigDecimal amount, String currency) {

    public PaymentAuthorized {
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(amount, "amount is required");
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
    }
}
