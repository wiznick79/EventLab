package pt.eventlab.contracts.messages;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record WorkflowStarted(UUID workflowId, String scenarioId, BigDecimal amount, String currency) {

    public WorkflowStarted {
        Objects.requireNonNull(workflowId, "workflowId is required");
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId is required");
        }
        Objects.requireNonNull(amount, "amount is required");
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
    }
}
