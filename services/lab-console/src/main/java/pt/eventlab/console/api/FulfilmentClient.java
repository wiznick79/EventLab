package pt.eventlab.console.api;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pt.eventlab.contracts.messages.FulfilmentRecoveryRequested;

@Component
class FulfilmentClient {
    private final RestClient client;

    FulfilmentClient(RestClient.Builder builder, @Value("${eventlab.fulfilment-base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    void recover(FulfilmentRecoveryRequested request) {
        client.post().uri("/api/v1/fulfilments/{workflowId}/recover", request.workflowId())
                .body(request).retrieve().toBodilessEntity();
    }
}
