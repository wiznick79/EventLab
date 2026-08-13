package pt.eventlab.console.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoadExperimentResponse(
        UUID id,
        String status,
        String statusReason,
        String trafficPattern,
        int consumerConcurrency,
        int requestedWorkflows,
        int processedLaunches,
        int pendingLaunches,
        int acceptedWorkflows,
        int launchFailures,
        int duplicatePercentage,
        int paymentObservedWorkflows,
        int fulfilmentObservedWorkflows,
        int terminalWorkflows,
        int provedWorkflows,
        int invariantViolations,
        int duplicateDeliveries,
        int backlog,
        int maxInFlight,
        double throughputPerSecond,
        long medianLatencyMillis,
        long p95LatencyMillis,
        long launchDurationMillis,
        long firstPaymentDelayMillis,
        long lastPaymentDelayMillis,
        long firstFulfilmentQueuedDelayMillis,
        long lastFulfilmentQueuedDelayMillis,
        long firstFulfilmentDelayMillis,
        long lastFulfilmentDelayMillis,
        long firstTerminalDelayMillis,
        long drainDurationMillis,
        Instant createdAt,
        Instant completedAt,
        List<UUID> workflowIds) {
}
