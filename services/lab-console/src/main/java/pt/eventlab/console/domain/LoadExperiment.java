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
    private int consumerConcurrency;
    private String workflowIds;
    private int launchFailures;
    private Instant createdAt;
    private Instant launchedAt;
    private Instant completedAt;

    protected LoadExperiment() {
    }

    public LoadExperiment(UUID id, String trafficPattern, int requestedWorkflows,
            int duplicatePercentage, int intervalMillis, int consumerConcurrency, Instant createdAt) {
        this.id = id;
        this.status = "LAUNCHING";
        this.trafficPattern = trafficPattern;
        this.requestedWorkflows = requestedWorkflows;
        this.duplicatePercentage = duplicatePercentage;
        this.intervalMillis = intervalMillis;
        this.consumerConcurrency = consumerConcurrency;
        this.workflowIds = "";
        this.createdAt = createdAt;
    }

    public void accepted(UUID workflowId) {
        workflowIds = workflowIds.isBlank() ? workflowId.toString() : workflowIds + "," + workflowId;
    }

    public void launchFailed() {
        launchFailures++;
    }

    public void launchCompleted(Instant at) {
        launchedAt = at;
        status = workflowIds.isBlank() ? "FAILED" : "RUNNING";
        if (workflowIds.isBlank()) completedAt = at;
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
    public int consumerConcurrency() { return consumerConcurrency; }
    public int launchFailures() { return launchFailures; }
    public Instant createdAt() { return createdAt; }
    public Instant launchedAt() { return launchedAt; }
    public Instant completedAt() { return completedAt; }
    public List<UUID> workflowIds() {
        return workflowIds == null || workflowIds.isBlank() ? List.of()
                : Arrays.stream(workflowIds.split(",")).map(UUID::fromString).toList();
    }
}
