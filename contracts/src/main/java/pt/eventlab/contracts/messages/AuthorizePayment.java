package pt.eventlab.contracts.messages;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record AuthorizePayment(UUID workflowId, BigDecimal amount, String currency) {

    public AuthorizePayment {
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
    }
}
