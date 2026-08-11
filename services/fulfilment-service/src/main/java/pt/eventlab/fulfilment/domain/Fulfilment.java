package pt.eventlab.fulfilment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fulfilments")
public class Fulfilment {
    @Id private UUID id;
    private UUID workflowId;
    private String scenarioId;
    private boolean available;
    private int attempts;
    private String status;
    private Instant createdAt;
    private Instant completedAt;

    protected Fulfilment() { }

    private Fulfilment(UUID workflowId, String scenarioId, Instant now) {
        this.id = UUID.randomUUID();
        this.workflowId = workflowId;
        this.scenarioId = scenarioId;
        this.available = !"fulfilment-unavailable".equals(scenarioId);
        this.status = "PENDING";
        this.createdAt = now;
    }

    static Fulfilment start(UUID workflowId, String scenarioId, Instant now) {
        return new Fulfilment(workflowId, scenarioId, now);
    }

    int attempt() { return ++attempts; }
    boolean available() { return available; }
    void recover() { available = true; }
    void complete(Instant now) { status = "COMPLETED"; completedAt = now; }
    void reject(Instant now) { status = "REJECTED"; completedAt = now; }
    public UUID id() { return id; }
    public UUID workflowId() { return workflowId; }
}
