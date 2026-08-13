package pt.eventlab.console.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import pt.eventlab.console.domain.TimelineEvent;

public record TimelineEventResponse(
        long sequence,
        UUID eventId,
        UUID workflowId,
        String eventType,
        String service,
        String state,
        String description,
        Instant occurredAt,
        Instant observedAt,
        String traceId,
        boolean duplicateDelivery,
        JsonNode payload) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static TimelineEventResponse from(TimelineEvent event) {
        try {
            return new TimelineEventResponse(
                    event.sequenceNumber(), event.logicalEventId(), event.workflowId(), event.eventType(),
                    event.serviceName(), event.state(), event.description(), event.occurredAt(),
                    event.observedAt(), event.traceId(), event.duplicateDelivery(),
                    OBJECT_MAPPER.readTree(event.payloadJson()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored timeline payload is invalid", exception);
        }
    }
}
