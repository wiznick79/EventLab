package pt.eventlab.fulfilment.domain;

public record FulfilmentAttemptResult(boolean completed, boolean deadLetter, int attempt, long retryDelayMs) {
}
