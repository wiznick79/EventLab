package pt.eventlab.console;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.eventlab.contracts.ServiceDescriptor;

@RestController
@RequestMapping("/api/v1")
class LabConsoleDescriptorController {

    @GetMapping("/about")
    ServiceDescriptor about() {
        return new ServiceDescriptor("lab-console", "Controls experiments and projects their timelines", "skeleton");
    }
}
