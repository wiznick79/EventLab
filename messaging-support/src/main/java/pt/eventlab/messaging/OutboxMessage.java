package pt.eventlab.messaging;

import java.util.Map;
import java.util.UUID;

public record OutboxMessage(
        UUID outboxId,
        UUID eventId,
        OutboxDestination destinationType,
        String destinationName,
        String payload,
        Map<String, String> traceHeaders,
        int attempts) {
}
