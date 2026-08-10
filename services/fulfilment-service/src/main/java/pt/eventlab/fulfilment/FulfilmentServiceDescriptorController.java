package pt.eventlab.fulfilment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.eventlab.contracts.ServiceDescriptor;

@RestController
@RequestMapping("/api/v1")
class FulfilmentServiceDescriptorController {

    @GetMapping("/about")
    ServiceDescriptor about() {
        return new ServiceDescriptor("fulfilment-service", "Reserves and releases simulated fulfilment", "skeleton");
    }
}
