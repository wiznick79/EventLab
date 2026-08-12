package pt.eventlab.console.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    public TimelineEventResponse project(
            EventEnvelope<JsonNode> envelope,
            String traceId,
            boolean duplicateDelivery) {
        EventPresentation presentation = duplicateDelivery
                ? new EventPresentation(
                        "Workflow inbox", "DUPLICATE_IGNORED",
                        "Duplicate logical event observed; the workflow inbox rejected a second state change")
                : presentation(envelope);
        TimelineEvent saved = events.save(new TimelineEvent(
                envelope.eventId(), envelope.workflowId(), envelope.eventType(),
                presentation.service(), presentation.state(), presentation.description(),
                envelope.occurredAt(), clock.instant(), traceId, json(envelope.payload()), duplicateDelivery));
        TimelineEventResponse response = TimelineEventResponse.from(saved);
        publishAfterCommit(response);
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

    private void publishAfterCommit(TimelineEventResponse event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stream.publish(event);
            }
        });
    }

    private EventPresentation presentation(EventEnvelope<JsonNode> envelope) {
        return switch (envelope.eventType()) {
            case MessageTypes.WORKFLOW_STARTED -> new EventPresentation(
                    "Workflow", "PAYMENT_PENDING", "Workflow accepted; payment authorization requested");
            case MessageTypes.PAYMENT_AUTHORIZED -> new EventPresentation(
                    "Payment", "FULFILMENT_PENDING", "Payment authorized; fulfilment requested");
            case MessageTypes.FULFILMENT_ATTEMPT_FAILED -> new EventPresentation(
                    "Fulfilment", "RETRY_SCHEDULED", "Attempt " + envelope.payload().path("attempt").asInt()
                            + " failed; retry delay " + envelope.payload().path("nextDelayMilliseconds").asLong() + " ms");
            case MessageTypes.FULFILMENT_MESSAGE_REJECTED -> new EventPresentation(
                    "Fulfilment", "MESSAGE_REJECTED", "Unsupported schema version "
                            + envelope.payload().path("receivedSchemaVersion").asInt()
                            + " rejected on delivery " + envelope.payload().path("attempt").asInt()
                            + " of " + envelope.payload().path("maxAttempts").asInt());
            case MessageTypes.FULFILMENT_DEAD_LETTERED -> new EventPresentation(
                    "Fulfilment",
                    envelope.payload().path("reason").asText().startsWith("Unsupported")
                            ? "POISON_DEAD_LETTERED" : "DEAD_LETTERED",
                    envelope.payload().path("reason").asText().startsWith("Unsupported")
                            ? "Unsupported contract was quarantined in the dead-letter queue"
                            : "Retry budget exhausted; command moved to the dead-letter queue");
            case MessageTypes.FULFILMENT_RECOVERY_REQUESTED -> new EventPresentation(
                    "Recovery", "RECOVERY_REQUESTED", "Dependency restored; replay "
                            + envelope.payload().path("replayMessageId").asText() + " requested by "
                            + envelope.payload().path("initiatedBy").asText());
            case MessageTypes.FULFILMENT_COMPLETED -> new EventPresentation(
                    "Fulfilment", "FULFILLED", "Fulfilment completed after a successful command delivery");
            case MessageTypes.FULFILMENT_REJECTED -> new EventPresentation(
                    "Fulfilment", "COMPENSATION_PENDING",
                    "Fulfilment rejected the request; payment compensation was requested");
            case MessageTypes.PAYMENT_COMPENSATED -> new EventPresentation(
                    "Payment", "PAYMENT_COMPENSATED", "The authorized payment was voided successfully");
            case MessageTypes.WORKFLOW_COMPENSATED -> new EventPresentation(
                    "Workflow", "COMPENSATED", "Saga reached its compensated terminal state");
            case MessageTypes.WORKFLOW_INTERVENTION_REQUIRED -> new EventPresentation(
                    "Workflow", "FAILED_REQUIRES_INTERVENTION",
                    "POISON_MESSAGE_QUARANTINED".equals(envelope.payload().path("reason").asText())
                            ? "The incompatible command is quarantined; operator intervention is required"
                            : "A persisted saga deadline expired; operator intervention is required");
            case MessageTypes.FULFILMENT_STATUS_CHANGED -> new EventPresentation(
                    "Fulfilment", "LATE_UPDATE_OBSERVED", "Delayed version "
                            + envelope.payload().path("aggregateVersion").asLong()
                            + " update arrived after the terminal outcome");
            case MessageTypes.STALE_EVENT_IGNORED -> new EventPresentation(
                    "Workflow", "STALE_IGNORED", "Version "
                            + envelope.payload().path("receivedVersion").asLong() + " was rejected; current version is "
                            + envelope.payload().path("currentVersion").asLong());
            case MessageTypes.WORKFLOW_COMPLETED -> new EventPresentation(
                    "Workflow", "COMPLETED", "Workflow reached its successful terminal state");
            default -> new EventPresentation("Unknown", "OBSERVED", "Observed " + envelope.eventType());
        };
    }

    private record EventPresentation(String service, String state, String description) {
    }
}
