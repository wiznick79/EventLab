package pt.eventlab.console.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.console.api.TimelineEventResponse;
import pt.eventlab.console.api.TimelineStream;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.MessageTypes;

@Service
public class TimelineProjectionService {

    private final Clock clock = Clock.systemUTC();
    private final ObjectMapper objectMapper;
    private final TimelineEventRepository events;
    private final TimelineStream stream;

    TimelineProjectionService(
            TimelineEventRepository events,
            ObjectMapper objectMapper,
            TimelineStream stream) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.stream = stream;
    }

    @Transactional
    public TimelineEventResponse project(EventEnvelope<JsonNode> envelope, String traceId) {
        EventPresentation presentation = presentation(envelope.eventType());
        TimelineEvent saved = events.save(new TimelineEvent(
                envelope.eventId(), envelope.workflowId(), envelope.eventType(),
                presentation.service(), presentation.state(), presentation.description(),
                envelope.occurredAt(), clock.instant(), traceId, json(envelope.payload())));
        TimelineEventResponse response = TimelineEventResponse.from(saved);
        stream.publish(response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<TimelineEventResponse> timeline(UUID workflowId) {
        return events.findByWorkflowIdOrderBySequenceNumber(workflowId).stream()
                .map(TimelineEventResponse::from)
                .toList();
    }

    private String json(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot store timeline payload", exception);
        }
    }

    private EventPresentation presentation(String eventType) {
        return switch (eventType) {
            case MessageTypes.WORKFLOW_STARTED -> new EventPresentation(
                    "Workflow", "PAYMENT_PENDING", "Workflow accepted; payment authorization requested");
            case MessageTypes.PAYMENT_AUTHORIZED -> new EventPresentation(
                    "Payment", "PAYMENT_AUTHORIZED", "Payment authorized by the Payment service");
            case MessageTypes.WORKFLOW_COMPLETED -> new EventPresentation(
                    "Workflow", "COMPLETED", "Workflow reached its successful terminal state");
            default -> new EventPresentation("Unknown", "OBSERVED", "Observed " + eventType);
        };
    }

    private record EventPresentation(String service, String state, String description) {
    }
}
