package pt.eventlab.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.eventlab.contracts.ServiceDescriptor;

@RestController
@RequestMapping("/api/v1")
class PaymentServiceDescriptorController {

    @GetMapping("/about")
    ServiceDescriptor about() {
        return new ServiceDescriptor("payment-service", "Authorizes and compensates simulated payments", "skeleton");
    }
}
