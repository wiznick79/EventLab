package pt.eventlab.console.api;

public record StallAttributionResponse(
        String stage,
        String label,
        long durationMillis,
        int sharePercent,
        String explanation) {
}
