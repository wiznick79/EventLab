package pt.eventlab.payment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {
    @Test
    void compensationIsIdempotent() {
        Instant now = Instant.parse("2026-08-11T00:00:00Z");
        Payment payment = Payment.authorize(UUID.randomUUID(), new BigDecimal("129.90"), "EUR", now);

        payment.compensate(now.plusSeconds(1));
        payment.compensate(now.plusSeconds(2));

        assertEquals("COMPENSATED", payment.status());
    }
}
