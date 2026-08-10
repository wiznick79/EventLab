package pt.eventlab.contracts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transport-neutral metadata shared by EventLab messages.
 *
 * @param eventId stable identifier for the logical event
 * @param eventType version-independent event type name
 * @param schemaVersion positive payload schema version
 * @param workflowId workflow run that owns the event
 * @param causationId event or command that caused this event, when applicable
 * @param correlationId identifier propagated across the complete workflow
 * @param occurredAt business event creation time
 * @param payload event-specific immutable data
 * @param <T> payload type
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID workflowId,
        UUID causationId,
        UUID correlationId,
        Instant occurredAt,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId is required");
        eventType = requireText(eventType, "eventType");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(workflowId, "workflowId is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(payload, "payload is required");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
