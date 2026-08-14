package pt.eventlab.console.domain;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.eventlab.console.api.RunDetailsResponse;
import pt.eventlab.console.api.RunResponse;
import pt.eventlab.console.api.RunSummaryResponse;
import pt.eventlab.console.api.StartRunRequest;
import pt.eventlab.console.api.TimelineEventResponse;
import pt.eventlab.contracts.RecoveryMode;

@Service
public class ExperimentRunRegistry {

    private final Clock clock = Clock.systemUTC();
    private final ExperimentRunRepository runs;
    private final TimelineEventRepository events;

    ExperimentRunRegistry(ExperimentRunRepository runs, TimelineEventRepository events) {
        this.runs = runs;
        this.events = events;
    }

    @Transactional
    public void register(StartRunRequest request, RunResponse response) {
        if (runs.existsById(response.workflowId())) return;
        var plan = request.resolvedExperimentPlan();
        String scenarioId = request.scenarioId() == null ? plan.scenarioId() : request.scenarioId();
        runs.save(new ExperimentRun(response.workflowId(), response.experimentPlanId(),
                scenarioId, plan, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<RunSummaryResponse> recent(int limit) {
        return runs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public RunDetailsResponse details(UUID workflowId) {
        ExperimentRun run = runs.findById(workflowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Experiment run not found"));
        List<TimelineEventResponse> timeline = events.findByWorkflowIdOrderBySequenceNumber(workflowId).stream()
                .map(TimelineEventResponse::from)
                .toList();
        return RunDetailsResponse.from(run, observedState(timeline), timeline);
    }

    @Transactional(readOnly = true)
    public List<UUID> automaticRecoveryCandidates() {
        return runs.findByRecoveryModeAndRecoveryClaimedFalse(RecoveryMode.AUTOMATIC).stream()
                .filter(run -> {
                    TimelineEvent latest = events.findFirstByWorkflowIdOrderBySequenceNumberDesc(run.workflowId());
                    return latest != null && "DEAD_LETTERED".equals(latest.state());
                })
                .map(ExperimentRun::workflowId)
                .toList();
    }

    @Transactional
    public boolean claimAutomaticRecovery(UUID workflowId) {
        return runs.claimRecovery(workflowId) == 1;
    }

    @Transactional
    public void releaseAutomaticRecovery(UUID workflowId) {
        runs.releaseRecovery(workflowId);
    }

    private RunSummaryResponse summary(ExperimentRun run) {
        TimelineEvent latest = events.findFirstByWorkflowIdOrderBySequenceNumberDesc(run.workflowId());
        return RunSummaryResponse.from(run, latest == null ? "PAYMENT_PENDING" : latest.state());
    }

    private String observedState(List<TimelineEventResponse> timeline) {
        return timeline.isEmpty() ? "PAYMENT_PENDING" : timeline.get(timeline.size() - 1).state();
    }
}
