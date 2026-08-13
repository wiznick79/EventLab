package pt.eventlab.payment.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/processing-delay")
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class ProcessingDelayController {
    private final AuthorizePaymentProcessor processor;

    ProcessingDelayController(AuthorizePaymentProcessor processor) { this.processor = processor; }

    @PutMapping
    Response configure(@RequestBody Request request) {
        validate(request.delayMillis());
        processor.configureDelay(request.delayMillis());
        return new Response("Payment", request.delayMillis());
    }

    static void validate(int delayMillis) {
        if (delayMillis < 0 || delayMillis > 500) {
            throw new IllegalArgumentException("processing delay must be between 0 and 500 ms");
        }
    }

    record Request(int delayMillis) { }
    record Response(String service, int delayMillis) { }
}
