package pt.eventlab.console.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "load_experiments")
public class LoadExperiment {

    @Id
    private UUID id;
    private String status;
    private String trafficPattern;
    private int requestedWorkflows;
    private int duplicatePercentage;
    private int intervalMillis;
    private String workflowIds;
    private int launchFailures;
    private Instant createdAt;
    private Instant launchedAt;
    private Instant completedAt;

    protected LoadExperiment() {
    }

    public LoadExperiment(UUID id, String trafficPattern, int requestedWorkflows,
            int duplicatePercentage, int intervalMillis, Instant createdAt) {
        this.id = id;
        this.status = "LAUNCHING";
        this.trafficPattern = trafficPattern;
        this.requestedWorkflows = requestedWorkflows;
        this.duplicatePercentage = duplicatePercentage;
        this.intervalMillis = intervalMillis;
        this.workflowIds = "";
        this.createdAt = createdAt;
    }

    public void launched(List<UUID> ids, int failures, Instant at) {
        workflowIds = ids.stream().map(UUID::toString).reduce((left, right) -> left + "," + right).orElse("");
        launchFailures = failures;
        launchedAt = at;
        status = ids.isEmpty() ? "FAILED" : "RUNNING";
        if (ids.isEmpty()) completedAt = at;
    }

    public void completed(boolean proved, Instant at) {
        status = proved && launchFailures == 0 ? "PROVED" : "FAILED";
        completedAt = at;
    }

    public UUID id() { return id; }
    public String status() { return status; }
    public String trafficPattern() { return trafficPattern; }
    public int requestedWorkflows() { return requestedWorkflows; }
    public int duplicatePercentage() { return duplicatePercentage; }
    public int intervalMillis() { return intervalMillis; }
    public int launchFailures() { return launchFailures; }
    public Instant createdAt() { return createdAt; }
    public Instant launchedAt() { return launchedAt; }
    public Instant completedAt() { return completedAt; }
    public List<UUID> workflowIds() {
        return workflowIds == null || workflowIds.isBlank() ? List.of()
                : Arrays.stream(workflowIds.split(",")).map(UUID::fromString).toList();
    }
}
