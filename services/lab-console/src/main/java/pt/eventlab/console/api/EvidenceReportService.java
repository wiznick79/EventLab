package pt.eventlab.console.api;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import pt.eventlab.console.domain.ExperimentRunRegistry;
import pt.eventlab.contracts.FulfilmentBehavior;

@Service
class EvidenceReportService {

    private final Clock clock = Clock.systemUTC();
    private final ExperimentRunRegistry runs;

    EvidenceReportService(ExperimentRunRegistry runs) {
        this.runs = runs;
    }

    EvidenceReportResponse report(java.util.UUID workflowId) {
        RunDetailsResponse run = runs.details(workflowId);
        var plan = run.experimentPlan();
        List<EvidenceCheckResponse> checks = new ArrayList<>();
        boolean terminal = terminal(run.state());

        checks.add(countCheck("payment-deliveries", "Payment-result deliveries",
                run.timeline(), event -> "payment.authorized".equals(event.eventType()),
                plan.paymentResultDeliveries(), terminal));
        if (plan.paymentResultDeliveries() == 2) {
            checks.add(countCheck("duplicate-suppression", "Duplicate state change suppressed",
                    run.timeline(), event -> "DUPLICATE_IGNORED".equals(event.state()), 1, terminal));
        }
        if (plan.fulfilmentBehavior() == FulfilmentBehavior.TEMPORARY_UNAVAILABLE) {
            checks.add(countCheck("retry-budget", "Configured retry budget exhausted",
                    run.timeline(), event -> "fulfilment.attempt-failed".equals(event.eventType()),
                    plan.fulfilmentMaxAttempts(), terminal));
            checks.add(countCheck("dead-letter", "Command quarantined once",
                    run.timeline(), event -> "DEAD_LETTERED".equals(event.state()), 1, terminal));
            checks.add(countCheck("recovery", "Recovery audited once",
                    run.timeline(), event -> "RECOVERY_REQUESTED".equals(event.state()), 1, terminal));
        }
        checks.add(outcomeCheck(run));

        String assessment = checks.stream().anyMatch(check -> "FAILED".equals(check.status()))
                ? "FAILED"
                : checks.stream().allMatch(check -> "PROVED".equals(check.status())) ? "PROVED" : "IN_PROGRESS";
        return new EvidenceReportResponse(run.workflowId(), run.experimentPlanId(), plan,
                run.expectedInvariant(), assessment, clock.instant(), checks, run.timeline());
    }

    private EvidenceCheckResponse outcomeCheck(RunDetailsResponse run) {
        String expected = switch (run.experimentPlan().fulfilmentBehavior()) {
            case SUCCESS, TEMPORARY_UNAVAILABLE, STALE_AFTER_SUCCESS -> "COMPLETED";
            case BUSINESS_REJECTION -> "COMPENSATED";
        };
        boolean extraEvidence = run.experimentPlan().fulfilmentBehavior() != FulfilmentBehavior.STALE_AFTER_SUCCESS
                || count(run.timeline(), event -> "STALE_IGNORED".equals(event.state())) == 1;
        String status = expected.equals(run.state()) && extraEvidence
                ? "PROVED" : terminal(run.state()) ? "FAILED" : "IN_PROGRESS";
        String observation = "expected " + expected + "; observed " + run.state();
        if (run.experimentPlan().fulfilmentBehavior() == FulfilmentBehavior.STALE_AFTER_SUCCESS) {
            observation += "; stale decisions="
                    + count(run.timeline(), event -> "STALE_IGNORED".equals(event.state()));
        }
        return new EvidenceCheckResponse("terminal-outcome", "Expected terminal outcome", status,
                observation, traceIds(run.timeline(), event -> expected.equals(event.state())
                        || "STALE_IGNORED".equals(event.state())));
    }

    private EvidenceCheckResponse countCheck(String id, String label, List<TimelineEventResponse> timeline,
            Predicate<TimelineEventResponse> predicate, int expected, boolean terminal) {
        int observed = count(timeline, predicate);
        String status = observed == expected ? "PROVED" : observed > expected || terminal ? "FAILED" : "IN_PROGRESS";
        return new EvidenceCheckResponse(id, label, status,
                "expected " + expected + "; observed " + observed, traceIds(timeline, predicate));
    }

    private int count(List<TimelineEventResponse> timeline, Predicate<TimelineEventResponse> predicate) {
        return (int) timeline.stream().filter(predicate).count();
    }

    private List<String> traceIds(
            List<TimelineEventResponse> timeline, Predicate<TimelineEventResponse> predicate) {
        return timeline.stream().filter(predicate).map(TimelineEventResponse::traceId)
                .filter(Objects::nonNull).distinct().toList();
    }

    private boolean terminal(String state) {
        return List.of("COMPLETED", "COMPENSATED", "FAILED_REQUIRES_INTERVENTION").contains(state);
    }
}
