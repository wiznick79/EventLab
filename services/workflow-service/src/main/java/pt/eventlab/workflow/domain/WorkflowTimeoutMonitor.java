package pt.eventlab.workflow.domain;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class WorkflowTimeoutMonitor {
    private final WorkflowApplicationService workflows;

    WorkflowTimeoutMonitor(WorkflowApplicationService workflows) {
        this.workflows = workflows;
    }

    @Scheduled(fixedDelayString = "${eventlab.workflow.timeout-scan-delay:5000}")
    void expireTimedOutSteps() {
        workflows.expireTimedOutSteps();
    }
}
