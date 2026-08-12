package pt.eventlab.console.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.eventlab.contracts.ExperimentPlan;

public record EvidenceReportResponse(
        UUID workflowId,
        UUID experimentPlanId,
        ExperimentPlan experimentPlan,
        String expectedInvariant,
        String assessment,
        Instant generatedAt,
        List<EvidenceCheckResponse> checks,
        List<TimelineEventResponse> timeline) {
}
