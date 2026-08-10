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

@RestController
@RequestMapping("/api/v1/runs")
class RunController {

    private final TimelineProjectionService timeline;
    private final TimelineStream stream;
    private final WorkflowClient workflowClient;
    private final DeadLetterRecoveryService recovery;

    RunController(TimelineProjectionService timeline, TimelineStream stream,
            WorkflowClient workflowClient, DeadLetterRecoveryService recovery) {
        this.timeline = timeline;
        this.stream = stream;
        this.workflowClient = workflowClient;
        this.recovery = recovery;
    }

    @PostMapping
    RunResponse start(@RequestBody StartRunRequest request) {
        return workflowClient.start(request);
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
