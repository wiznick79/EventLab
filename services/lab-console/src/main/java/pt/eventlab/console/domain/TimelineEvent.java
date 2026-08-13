package pt.eventlab.console.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "timeline_events")
public class TimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long sequenceNumber;
    private UUID id;
    private UUID logicalEventId;
    private UUID workflowId;
    private String eventType;
    private String serviceName;
    private String state;
    private String description;
    private Instant occurredAt;
    private Instant observedAt;
    private String traceId;
    private String payloadJson;
    private boolean duplicateDelivery;

    protected TimelineEvent() {
    }

    public TimelineEvent(
            UUID logicalEventId,
            UUID workflowId,
            String eventType,
            String serviceName,
            String state,
            String description,
            Instant occurredAt,
            Instant observedAt,
            String traceId,
            String payloadJson,
            boolean duplicateDelivery) {
        this.id = UUID.randomUUID();
        this.logicalEventId = logicalEventId;
        this.workflowId = workflowId;
        this.eventType = eventType;
        this.serviceName = serviceName;
        this.state = state;
        this.description = description;
        this.occurredAt = occurredAt;
        this.observedAt = observedAt;
        this.traceId = traceId;
        this.payloadJson = payloadJson;
        this.duplicateDelivery = duplicateDelivery;
    }

    public long sequenceNumber() { return sequenceNumber; }
    public UUID logicalEventId() { return logicalEventId; }
    public UUID workflowId() { return workflowId; }
    public String eventType() { return eventType; }
    public String serviceName() { return serviceName; }
    public String state() { return state; }
    public String description() { return description; }
    public Instant occurredAt() { return occurredAt; }
    public Instant observedAt() { return observedAt; }
    public String traceId() { return traceId; }
    public String payloadJson() { return payloadJson; }
    public boolean duplicateDelivery() { return duplicateDelivery; }
}
