package pt.eventlab.console.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
class DeploymentControlService {

    private final Clock clock = Clock.systemUTC();
    private final DeploymentProperties properties;
    private final List<DependencyProbe> probes;

    DeploymentControlService(RestClient.Builder builder, DeploymentProperties properties,
            @Value("${eventlab.workflow-base-url}") String workflowUrl,
            @Value("${eventlab.payment-base-url}") String paymentUrl,
            @Value("${eventlab.fulfilment-base-url}") String fulfilmentUrl) {
        this.properties = properties;
        this.probes = List.of(
                new DependencyProbe("Workflow", client(builder, workflowUrl)),
                new DependencyProbe("Payment", client(builder, paymentUrl)),
                new DependencyProbe("Fulfilment", client(builder, fulfilmentUrl)));
    }

    DeploymentStatusResponse status() {
        Instant now = clock.instant();
        String mode = mode(now);
        List<DependencyStatusResponse> dependencies = probes.stream().map(this::probe).toList();
        return new DeploymentStatusResponse(properties.environment(), properties.version(),
                properties.expiresAt(), mode, "ONLINE".equals(mode), now, dependencies);
    }

    void requireAcceptingExperiments() {
        String mode = mode(clock.instant());
        if (!"ONLINE".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The live lab is " + mode.toLowerCase().replace('_', ' ')
                            + " and is not accepting new experiments");
        }
    }

    private String mode(Instant now) {
        if (properties.expiresAt() == null) {
            return "ONLINE";
        }
        if (!now.isBefore(properties.expiresAt())) {
            return "EXPIRED";
        }
        return now.plusSeconds(properties.readOnlyLeadSeconds()).isBefore(properties.expiresAt())
                ? "ONLINE" : "READ_ONLY";
    }

    private DependencyStatusResponse probe(DependencyProbe probe) {
        try {
            HealthResponse health = probe.client().get().uri("/actuator/health")
                    .retrieve().body(HealthResponse.class);
            return new DependencyStatusResponse(probe.name(),
                    health != null && "UP".equals(health.status()) ? "UP" : "DOWN");
        } catch (RuntimeException exception) {
            return new DependencyStatusResponse(probe.name(), "DOWN");
        }
    }

    private RestClient client(RestClient.Builder builder, String baseUrl) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(1000);
        requests.setReadTimeout(1000);
        return builder.clone().baseUrl(baseUrl).requestFactory(requests).build();
    }

    private record DependencyProbe(String name, RestClient client) {
    }

    private record HealthResponse(String status) {
    }
}
