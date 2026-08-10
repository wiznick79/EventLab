package pt.eventlab.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.MessageTypes;
import pt.eventlab.contracts.messages.AuthorizePayment;
import pt.eventlab.payment.domain.PaymentApplicationService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eventlab.messaging.enabled=false",
        "management.tracing.enabled=false"
})
class PaymentServiceApplicationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentApplicationService payments;

    @Test
    void contextLoads() {
    }

    @Test
    void duplicateScenarioCreatesTwoDeliveriesOfOneLogicalEvent() {
        UUID workflowId = UUID.randomUUID();
        payments.authorize(new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.AUTHORIZE_PAYMENT, 1,
                workflowId, null, workflowId, Instant.now(),
                new AuthorizePayment(
                        workflowId, "duplicate-payment-result", new BigDecimal("129.90"), "EUR")));

        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from payments", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from outbox_messages", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(distinct event_id) from outbox_messages", Integer.class));
    }
}
