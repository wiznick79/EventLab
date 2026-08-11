package pt.eventlab.payment.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.CompensatePayment;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.payment.domain.PaymentApplicationService;

@Service
class CompensatePaymentHandler {
    private static final String HANDLER = "payment.compensate";
    private final InboxStore inbox;
    private final PaymentApplicationService payments;

    CompensatePaymentHandler(InboxStore inbox, PaymentApplicationService payments) {
        this.inbox = inbox;
        this.payments = payments;
    }

    @Transactional
    public boolean handle(EventEnvelope<CompensatePayment> command) {
        if (!inbox.claim(command.eventId(), HANDLER)) return false;
        payments.compensate(command);
        return true;
    }
}
