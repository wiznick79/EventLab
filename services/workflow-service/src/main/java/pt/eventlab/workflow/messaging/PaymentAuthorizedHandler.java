package pt.eventlab.workflow.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.PaymentAuthorized;
import pt.eventlab.messaging.BusinessDecisionTrace;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@Service
class PaymentAuthorizedHandler {

    private static final String HANDLER = "workflow.payment-authorized";

    private final BusinessDecisionTrace decisions;
    private final InboxStore inbox;
    private final WorkflowApplicationService workflows;

    PaymentAuthorizedHandler(
            InboxStore inbox,
            BusinessDecisionTrace decisions,
            WorkflowApplicationService workflows) {
        this.inbox = inbox;
        this.decisions = decisions;
        this.workflows = workflows;
    }

    @Transactional
    public boolean handle(EventEnvelope<PaymentAuthorized> event) {
        return decisions.record(
                "eventlab.workflow.inbox.decision", event.eventId(), event.workflowId(), () -> {
            if (!inbox.claim(event.eventId(), HANDLER)) {
                return new BusinessDecisionTrace.Outcome<>("DUPLICATE_IGNORED", false, false);
            }
            workflows.recordPaymentAuthorized(event);
            return new BusinessDecisionTrace.Outcome<>("PAYMENT_ACCEPTED", true, true);
        });
    }
}
