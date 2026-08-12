package pt.eventlab.console.api;

import java.time.Instant;

record EvidencePipelineResponse(
        boolean enabled,
        String status,
        Instant lastEventAt,
        String lastError) {
}
