package pt.eventlab.workflow.api;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@RestController
@RequestMapping("/api/v1/workflows")
class WorkflowController {

    private final WorkflowApplicationService workflows;

    WorkflowController(WorkflowApplicationService workflows) {
        this.workflows = workflows;
    }

    @PostMapping
    ResponseEntity<WorkflowResponse> create(@RequestBody CreateWorkflowRequest request) {
        WorkflowResponse response = WorkflowResponse.from(workflows.start(
                request.resolvedScenarioId(), request.amount(), request.currency()));
        return ResponseEntity.created(URI.create("/api/v1/workflows/" + response.workflowId())).body(response);
    }

    @GetMapping("/{workflowId}")
    WorkflowResponse get(@PathVariable UUID workflowId) {
        return WorkflowResponse.from(workflows.get(workflowId));
    }
}
