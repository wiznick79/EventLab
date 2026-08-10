package pt.eventlab.workflow.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pt.eventlab.contracts.WorkflowState;
import pt.eventlab.workflow.domain.WorkflowRun;

public record WorkflowResponse(
        UUID workflowId,
        String scenarioId,
        BigDecimal amount,
        String currency,
        WorkflowState state,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static WorkflowResponse from(WorkflowRun workflow) {
        return new WorkflowResponse(
                workflow.id(),
                workflow.scenarioId(),
                workflow.amount(),
                workflow.currency(),
                workflow.state(),
                workflow.version(),
                workflow.createdAt(),
                workflow.updatedAt());
    }
}
