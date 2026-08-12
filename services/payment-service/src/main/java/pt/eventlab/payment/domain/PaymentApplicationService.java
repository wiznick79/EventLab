package pt.eventlab.payment.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.ExperimentPlan;
import pt.eventlab.contracts.MessageTypes;
import pt.eventlab.contracts.messages.AuthorizePayment;
import pt.eventlab.contracts.messages.PaymentAuthorized;
import pt.eventlab.contracts.messages.CompensatePayment;
import pt.eventlab.contracts.messages.PaymentCompensated;
import pt.eventlab.payment.messaging.PaymentMessagePublisher;

@Service
public class PaymentApplicationService {

    private final Clock clock;
    private final PaymentMessagePublisher messages;
    private final PaymentRepository payments;

    @Autowired
    PaymentApplicationService(PaymentRepository payments, PaymentMessagePublisher messages) {
        this(payments, messages, Clock.systemUTC());
    }

    PaymentApplicationService(PaymentRepository payments, PaymentMessagePublisher messages, Clock clock) {
        this.payments = payments;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public void authorize(EventEnvelope<AuthorizePayment> command) {
        Instant now = clock.instant();
        AuthorizePayment payload = command.payload();
        Payment payment = payments.saveAndFlush(Payment.authorize(
                payload.workflowId(), payload.amount(), payload.currency(), now));

        EventEnvelope<PaymentAuthorized> event = new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.PAYMENT_AUTHORIZED, 1,
                command.workflowId(), command.eventId(), command.correlationId(), now,
                new PaymentAuthorized(payment.id(), payment.workflowId(), payment.amount(), payment.currency()));
        int copies = ExperimentPlan.preset(payload.scenarioId()).paymentResultDeliveries();
        messages.publish(event, copies);
    }

    @Transactional
    public void compensate(EventEnvelope<CompensatePayment> command) {
        Instant now = clock.instant();
        Payment payment = payments.findByWorkflowId(command.workflowId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment for " + command.workflowId()));
        payment.compensate(now);
        messages.publish(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.PAYMENT_COMPENSATED, 1,
                command.workflowId(), command.eventId(), command.correlationId(), now,
                new PaymentCompensated(payment.id(), payment.workflowId())), 1);
    }
}
