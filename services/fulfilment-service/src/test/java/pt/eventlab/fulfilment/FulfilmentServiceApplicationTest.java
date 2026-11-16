package pt.eventlab.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.MessageTypes;
import pt.eventlab.contracts.messages.RequestFulfilment;
import pt.eventlab.fulfilment.domain.FulfilmentApplicationService;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:fulfilment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eventlab.messaging.enabled=false",
        "management.tracing.enabled=false",
    }
)
class FulfilmentServiceApplicationTest {

    @Autowired
    private FulfilmentApplicationService fulfilments;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        // Verify that the application context starts with the upgraded dependencies.
    }

    @Test
    void unsupportedContractIsQuarantinedAfterThreeAttemptsWithoutCompletingFulfilment() {
        UUID workflowId = UUID.randomUUID();
        EventEnvelope<RequestFulfilment> command = new EventEnvelope<>(
            UUID.randomUUID(),
            MessageTypes.REQUEST_FULFILMENT,
            2,
            workflowId,
            null,
            workflowId,
            Instant.now(),
            new RequestFulfilment(workflowId, "happy-path")
        );

        assertFalse(fulfilments.rejectUnsupportedVersion(command, 1, 3).deadLetter());
        assertFalse(fulfilments.rejectUnsupportedVersion(command, 1, 3).deadLetter());
        var exhausted = fulfilments.rejectUnsupportedVersion(command, 1, 3);
        assertTrue(exhausted.deadLetter());
        assertFalse(exhausted.completed());
        assertEquals(3, exhausted.attempt());

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "select count(*) from fulfilments where workflow_id = ?",
                Integer.class,
                workflowId
            )
        );
        assertEquals(
            "PENDING",
            jdbcTemplate.queryForObject(
                "select status from fulfilments where workflow_id = ?",
                String.class,
                workflowId
            )
        );
        assertEquals(
            4,
            jdbcTemplate.queryForObject(
                "select count(*) from outbox_messages where payload_json like ?",
                Integer.class,
                "%" + workflowId + "%"
            )
        );
    }
}
