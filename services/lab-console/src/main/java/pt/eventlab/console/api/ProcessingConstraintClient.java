package pt.eventlab.console.api;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pt.eventlab.console.messaging.BusinessEventProjectionProcessor;

@Component
class ProcessingConstraintClient {
    private final List<Target> targets;
    private final ObjectProvider<BusinessEventProjectionProcessor> projection;

    ProcessingConstraintClient(RestClient.Builder builder,
            ObjectProvider<BusinessEventProjectionProcessor> projection,
            @Value("${eventlab.workflow-base-url}") String workflowUrl,
            @Value("${eventlab.payment-base-url}") String paymentUrl,
            @Value("${eventlab.fulfilment-base-url}") String fulfilmentUrl) {
        this.projection = projection;
        this.targets = List.of(
                new Target("WORKFLOW", builder.clone().baseUrl(workflowUrl).build()),
                new Target("PAYMENT", builder.clone().baseUrl(paymentUrl).build()),
                new Target("FULFILMENT", builder.clone().baseUrl(fulfilmentUrl).build()));
    }

    void configure(String constrainedStage, int delayMillis) {
        try {
            for (Target target : targets) {
                int targetDelay = target.stage().equals(constrainedStage) ? delayMillis : 0;
                target.client().put().uri("/internal/processing-delay")
                        .body(new Request(targetDelay)).retrieve().toBodilessEntity();
            }
            BusinessEventProjectionProcessor local = projection.getIfAvailable();
            if (local != null) local.configureDelay("EVIDENCE".equals(constrainedStage) ? delayMillis : 0);
        } catch (RuntimeException exception) {
            resetBestEffort();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not apply the processing constraint consistently", exception);
        }
    }

    void resetBestEffort() {
        for (Target target : targets) {
            try {
                target.client().put().uri("/internal/processing-delay")
                        .body(new Request(0)).retrieve().toBodilessEntity();
            } catch (RuntimeException ignored) {
                // Dependency health exposes a service that could not reset.
            }
        }
        BusinessEventProjectionProcessor local = projection.getIfAvailable();
        if (local != null) local.configureDelay(0);
    }

    private record Request(int delayMillis) { }
    private record Target(String stage, RestClient client) { }
}
