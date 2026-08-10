package pt.eventlab.workflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.eventlab.contracts.ServiceDescriptor;

@RestController
@RequestMapping("/api/v1")
class WorkflowServiceDescriptorController {

    @GetMapping("/about")
    ServiceDescriptor about() {
        return new ServiceDescriptor("workflow-service", "Orchestrates workflow state and saga decisions", "skeleton");
    }
}
