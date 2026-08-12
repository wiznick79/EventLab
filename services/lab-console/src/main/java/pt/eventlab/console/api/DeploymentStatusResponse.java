package pt.eventlab.console.api;

import java.time.Instant;
import java.util.List;

public record DeploymentStatusResponse(
        String environment,
        String version,
        Instant expiresAt,
        String mode,
        boolean acceptingExperiments,
        Instant checkedAt,
        List<DependencyStatusResponse> dependencies) {
}
