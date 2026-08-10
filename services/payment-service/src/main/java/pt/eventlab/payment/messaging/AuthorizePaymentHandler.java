package pt.eventlab.payment.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.AuthorizePayment;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.payment.domain.PaymentApplicationService;

@Service
class AuthorizePaymentHandler {

    private static final String HANDLER = "payment.authorize";

    private final InboxStore inbox;
    private final PaymentApplicationService payments;

    AuthorizePaymentHandler(InboxStore inbox, PaymentApplicationService payments) {
        this.inbox = inbox;
        this.payments = payments;
    }

    @Transactional
    public boolean handle(EventEnvelope<AuthorizePayment> command) {
        if (!inbox.claim(command.eventId(), HANDLER)) {
            return false;
        }
        payments.authorize(command);
        return true;
    }
}
