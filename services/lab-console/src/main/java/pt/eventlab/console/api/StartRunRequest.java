package pt.eventlab.console.api;

import java.math.BigDecimal;

public record StartRunRequest(String scenarioId, BigDecimal amount, String currency) {
}
