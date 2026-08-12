package pt.eventlab.console.api;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class WorkflowClient {

    private final RestClient restClient;

    WorkflowClient(RestClient.Builder builder, @Value("${eventlab.workflow-base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    RunResponse start(StartRunRequest request) {
        WorkflowApiResponse response = restClient.post()
                .uri("/api/v1/workflows")
                .body(request)
                .retrieve()
                .body(WorkflowApiResponse.class);
        if (response == null) {
            throw new IllegalStateException("Workflow service returned no response");
        }
        return new RunResponse(response.workflowId(), response.experimentPlanId(), response.state());
    }

    WorkflowSnapshot inspect(UUID workflowId) {
        WorkflowApiResponse response = restClient.get()
                .uri("/api/v1/workflows/{workflowId}", workflowId)
                .retrieve()
                .body(WorkflowApiResponse.class);
        if (response == null) {
            throw new IllegalStateException("Workflow service returned no response");
        }
        return new WorkflowSnapshot(response.state(), response.updatedAt());
    }

    record WorkflowSnapshot(String state, Instant updatedAt) {
    }

    private record WorkflowApiResponse(
            UUID workflowId, UUID experimentPlanId, String state, Instant updatedAt) {
    }
}
