package pt.eventlab.workflow.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.PaymentCompensated;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@Service
class PaymentCompensatedHandler {
    private static final String HANDLER = "workflow.payment-compensated";
    private final InboxStore inbox;
    private final WorkflowApplicationService workflows;

    PaymentCompensatedHandler(InboxStore inbox, WorkflowApplicationService workflows) {
        this.inbox = inbox;
        this.workflows = workflows;
    }

    @Transactional
    public boolean handle(EventEnvelope<PaymentCompensated> event) {
        if (!inbox.claim(event.eventId(), HANDLER)) return false;
        workflows.recordPaymentCompensated(event);
        return true;
    }
}
