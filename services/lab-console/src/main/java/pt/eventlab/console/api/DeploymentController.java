package pt.eventlab.console.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deployment")
class DeploymentController {

    private final DeploymentControlService deployment;

    DeploymentController(DeploymentControlService deployment) {
        this.deployment = deployment;
    }

    @GetMapping("/status")
    DeploymentStatusResponse status() {
        return deployment.status();
    }
}
