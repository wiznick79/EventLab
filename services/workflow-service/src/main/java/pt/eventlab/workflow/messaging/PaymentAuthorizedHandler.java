package pt.eventlab.workflow.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.PaymentAuthorized;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@Service
class PaymentAuthorizedHandler {

    private static final String HANDLER = "workflow.payment-authorized";

    private final InboxStore inbox;
    private final Tracer tracer;
    private final WorkflowApplicationService workflows;

    PaymentAuthorizedHandler(InboxStore inbox, Tracer tracer, WorkflowApplicationService workflows) {
        this.inbox = inbox;
        this.tracer = tracer;
        this.workflows = workflows;
    }

    @Transactional
    public boolean handle(EventEnvelope<PaymentAuthorized> event) {
        Span decision = tracer.nextSpan().name("eventlab.workflow.inbox.decision").start();
        decision.tag("eventlab.inbox.handler", HANDLER);
        decision.tag("eventlab.message.event_id", event.eventId().toString());
        decision.tag("eventlab.workflow.id", event.payload().workflowId().toString());
        try (Tracer.SpanInScope ignored = tracer.withSpan(decision)) {
            if (!inbox.claim(event.eventId(), HANDLER)) {
                decision.tag("eventlab.decision", "DUPLICATE_IGNORED");
                decision.tag("eventlab.state_change_applied", "false");
                return false;
            }
            workflows.recordPaymentAuthorized(event);
            decision.tag("eventlab.decision", "PAYMENT_ACCEPTED");
            decision.tag("eventlab.state_change_applied", "true");
            return true;
        } catch (RuntimeException exception) {
            decision.error(exception);
            throw exception;
        } finally {
            decision.end();
        }
    }
}
