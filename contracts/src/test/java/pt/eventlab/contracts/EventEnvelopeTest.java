package pt.eventlab.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void retainsTransportNeutralMetadata() {
        UUID eventId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-10T12:00:00Z");

        EventEnvelope<String> envelope = new EventEnvelope<>(
                eventId,
                "workflow.started",
                1,
                workflowId,
                null,
                correlationId,
                occurredAt,
                "payload");

        assertEquals(eventId, envelope.eventId());
        assertEquals(workflowId, envelope.workflowId());
        assertEquals(correlationId, envelope.correlationId());
    }

    @Test
    void rejectsInvalidSchemaVersions() {
        assertThrows(IllegalArgumentException.class, () -> new EventEnvelope<>(
                UUID.randomUUID(),
                "workflow.started",
                0,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                Instant.now(),
                "payload"));
    }
}
