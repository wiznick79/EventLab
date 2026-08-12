package pt.eventlab.console.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pt.eventlab.console.messaging.EvidencePipelineStatus;

class DeploymentControlServiceTest {

    @Test
    void localDevelopmentAcceptsExperimentsWithoutAnExpiry() {
        DeploymentControlService service = service(new DeploymentProperties("local", "development", null, 600));

        service.requireAcceptingExperiments();
        assertEquals("ONLINE", service.status().mode());
    }

    @Test
    void anExpiredEnvironmentRejectsNewExperiments() {
        DeploymentControlService service = service(new DeploymentProperties(
                "demo", "abc123", Instant.parse("2020-01-01T00:00:00Z"), 600));

        assertThrows(ResponseStatusException.class, service::requireAcceptingExperiments);
        assertEquals("EXPIRED", service.status().mode());
    }

    @Test
    void anEnvironmentApproachingExpiryBecomesReadOnly() {
        DeploymentControlService service = service(new DeploymentProperties(
                "demo", "abc123", Instant.now().plusSeconds(300), 600));

        assertThrows(ResponseStatusException.class, service::requireAcceptingExperiments);
        assertEquals("READ_ONLY", service.status().mode());
    }

    @Test
    void aDisabledEvidencePipelineRejectsNewExperiments() {
        EvidencePipelineStatus pipeline = new EvidencePipelineStatus(false);
        DeploymentControlService service = new DeploymentControlService(RestClient.builder(),
                new DeploymentProperties("local", "development", null, 600), pipeline,
                "http://localhost:18081", "http://localhost:18082", "http://localhost:18083");

        assertThrows(ResponseStatusException.class, service::requireAcceptingExperiments);
        assertEquals("DISABLED", service.status().evidencePipeline().status());
    }

    private DeploymentControlService service(DeploymentProperties properties) {
        EvidencePipelineStatus pipeline = new EvidencePipelineStatus(true);
        pipeline.running();
        return new DeploymentControlService(RestClient.builder(), properties, pipeline,
                "http://localhost:18081", "http://localhost:18082", "http://localhost:18083");
    }
}
