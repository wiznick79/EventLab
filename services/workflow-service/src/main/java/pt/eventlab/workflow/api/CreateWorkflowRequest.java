package pt.eventlab.workflow.api;

import java.math.BigDecimal;

public record CreateWorkflowRequest(String scenarioId, BigDecimal amount, String currency) {
}
