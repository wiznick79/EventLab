package pt.eventlab.console.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoadExperimentResponse(
        UUID id,
        String status,
        String trafficPattern,
        int requestedWorkflows,
        int acceptedWorkflows,
        int launchFailures,
        int duplicatePercentage,
        int terminalWorkflows,
        int provedWorkflows,
        int invariantViolations,
        int duplicateDeliveries,
        int backlog,
        int maxInFlight,
        double throughputPerSecond,
        long medianLatencyMillis,
        long p95LatencyMillis,
        Instant createdAt,
        Instant completedAt,
        List<UUID> workflowIds) {
}
