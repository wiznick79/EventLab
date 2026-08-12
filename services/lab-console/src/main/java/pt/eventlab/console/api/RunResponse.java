package pt.eventlab.console.api;

import java.util.UUID;

public record RunResponse(UUID workflowId, UUID experimentPlanId, String state) {
}
