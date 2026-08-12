package pt.eventlab.workflow.messaging;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/consumer-concurrency")
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class ConsumerConcurrencyController {
    private final PaymentAuthorizedProcessor processor;

    ConsumerConcurrencyController(PaymentAuthorizedProcessor processor) { this.processor = processor; }

    @PutMapping
    Response configure(@RequestBody Request request) {
        validate(request.calls());
        processor.reconfigure(request.calls());
        return new Response("Workflow", processor.concurrency());
    }

    static void validate(int calls) {
        if (!List.of(1, 4, 8).contains(calls)) {
            throw new IllegalArgumentException("consumer concurrency must be 1, 4, or 8");
        }
    }

    record Request(int calls) { }
    record Response(String service, int calls) { }
}
