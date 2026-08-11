package pt.eventlab.payment.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.CompensatePayment;
import pt.eventlab.messaging.BusinessDecisionTrace;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.payment.domain.PaymentApplicationService;

@Service
class CompensatePaymentHandler {
    private static final String HANDLER = "payment.compensate";
    private final BusinessDecisionTrace decisions;
    private final InboxStore inbox;
    private final PaymentApplicationService payments;

    CompensatePaymentHandler(
            InboxStore inbox,
            BusinessDecisionTrace decisions,
            PaymentApplicationService payments) {
        this.inbox = inbox;
        this.decisions = decisions;
        this.payments = payments;
    }

    @Transactional
    public boolean handle(EventEnvelope<CompensatePayment> command) {
        return decisions.record(
                "eventlab.payment.compensation.decision",
                command.eventId(), command.workflowId(), () -> {
            if (!inbox.claim(command.eventId(), HANDLER)) {
                return new BusinessDecisionTrace.Outcome<>("DUPLICATE_IGNORED", false, false);
            }
            payments.compensate(command);
            return new BusinessDecisionTrace.Outcome<>("PAYMENT_COMPENSATED", true, true);
        });
    }
}
