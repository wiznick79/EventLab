package pt.eventlab.console.api;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import pt.eventlab.console.messaging.BusinessEventProjectionProcessor;

@Component
class ConsumerConcurrencyClient {
    private final List<Target> targets;
    private final ObjectProvider<BusinessEventProjectionProcessor> projection;

    ConsumerConcurrencyClient(RestClient.Builder builder,
            ObjectProvider<BusinessEventProjectionProcessor> projection,
            @Value("${eventlab.workflow-base-url}") String workflowUrl,
            @Value("${eventlab.payment-base-url}") String paymentUrl,
            @Value("${eventlab.fulfilment-base-url}") String fulfilmentUrl) {
        this.projection = projection;
        this.targets = List.of(
                new Target("Workflow", builder.clone().baseUrl(workflowUrl).build()),
                new Target("Payment", builder.clone().baseUrl(paymentUrl).build()),
                new Target("Fulfilment", builder.clone().baseUrl(fulfilmentUrl).build()));
    }

    void configure(int calls) {
        List<Target> configured = new ArrayList<>();
        try {
            for (Target target : targets) {
                target.client().put().uri("/internal/consumer-concurrency")
                        .body(new Request(calls)).retrieve().toBodilessEntity();
                configured.add(target);
            }
            BusinessEventProjectionProcessor local = projection.getIfAvailable();
            if (local != null) local.reconfigure(calls);
        } catch (RuntimeException exception) {
            rollback(configured);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not apply consumer concurrency " + calls + " consistently", exception);
        }
    }

    private void rollback(List<Target> configured) {
        for (Target target : configured.reversed()) {
            try {
                target.client().put().uri("/internal/consumer-concurrency")
                        .body(new Request(1)).retrieve().toBodilessEntity();
            } catch (RuntimeException ignored) {
                // The admission request will fail; dependency health exposes a service that cannot reset.
            }
        }
        BusinessEventProjectionProcessor local = projection.getIfAvailable();
        if (local != null) local.reconfigure(1);
    }

    private record Request(int calls) { }
    private record Target(String name, RestClient client) { }
}
