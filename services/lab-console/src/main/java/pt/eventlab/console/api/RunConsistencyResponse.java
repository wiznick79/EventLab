package pt.eventlab.console.api;

import java.time.Instant;
import java.util.UUID;

record RunConsistencyResponse(
        UUID workflowId,
        String status,
        String authoritativeState,
        String projectedState,
        Instant authoritativeUpdatedAt,
        long lagSeconds,
        String explanation) {
}
