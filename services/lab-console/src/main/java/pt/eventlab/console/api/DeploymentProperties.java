package pt.eventlab.console.api;

import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eventlab.deployment")
record DeploymentProperties(
        String environment,
        String version,
        Instant expiresAt,
        long readOnlyLeadSeconds) {

    DeploymentProperties {
        if (environment == null || environment.isBlank()) environment = "local";
        if (version == null || version.isBlank()) version = "development";
        if (readOnlyLeadSeconds == 0) readOnlyLeadSeconds = 600;
    }
}
