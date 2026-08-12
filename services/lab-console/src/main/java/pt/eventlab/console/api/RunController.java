package pt.eventlab.console.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pt.eventlab.console.domain.TimelineProjectionService;
import pt.eventlab.console.domain.ExperimentRunRegistry;

@RestController
@RequestMapping("/api/v1/runs")
class RunController {

    private final TimelineProjectionService timeline;
    private final TimelineStream stream;
    private final WorkflowClient workflowClient;
    private final DeadLetterRecoveryService recovery;
    private final ExperimentRunRegistry runs;

    RunController(TimelineProjectionService timeline, TimelineStream stream,
            WorkflowClient workflowClient, DeadLetterRecoveryService recovery,
            ExperimentRunRegistry runs) {
        this.timeline = timeline;
        this.stream = stream;
        this.workflowClient = workflowClient;
        this.recovery = recovery;
        this.runs = runs;
    }

    @PostMapping
    RunResponse start(@RequestBody StartRunRequest request) {
        RunResponse response = workflowClient.start(request);
        runs.register(request, response);
        return response;
    }

    @GetMapping
    List<RunSummaryResponse> recent() {
        return runs.recent(12);
    }

    @GetMapping("/{workflowId}")
    RunDetailsResponse details(@PathVariable UUID workflowId) {
        return runs.details(workflowId);
    }

    @GetMapping("/{workflowId}/timeline")
    List<TimelineEventResponse> timeline(@PathVariable UUID workflowId) {
        return timeline.timeline(workflowId);
    }

    @GetMapping(path = "/{workflowId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable UUID workflowId) {
        return stream.subscribe(workflowId, timeline.timeline(workflowId));
    }

    @PostMapping("/{workflowId}/recover")
    void recover(@PathVariable UUID workflowId) {
        recovery.recover(workflowId);
    }
}
