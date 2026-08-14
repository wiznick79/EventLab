package pt.eventlab.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eventlab.messaging.enabled=false",
        "management.tracing.enabled=false"
})
class WorkflowServiceApplicationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkflowApplicationService workflows;

    @Test
    void contextLoads() {
    }

    @Test
    void storesStateAndOutgoingMessagesInOneTransaction() {
        int workflowsBefore = count("workflow_runs");
        int messagesBefore = count("outbox_messages where published_at is null");
        workflows.start("happy-path", new BigDecimal("129.90"), "EUR");

        assertEquals(workflowsBefore + 1, count("workflow_runs"));
        assertEquals(messagesBefore + 2, count("outbox_messages where published_at is null"));
    }

    @Test
    void returnsTheOriginalWorkflowForAnIdenticalIdempotentRequest() {
        UUID key = UUID.randomUUID();
        int workflowsBefore = count("workflow_runs");
        int messagesBefore = count("outbox_messages where published_at is null");

        var first = workflows.start(key, "happy-path", new BigDecimal("129.90"), "EUR");
        var duplicate = workflows.start(key, "happy-path", new BigDecimal("129.90"), "EUR");

        assertEquals(first.id(), duplicate.id());
        assertEquals(workflowsBefore + 1, count("workflow_runs"));
        assertEquals(messagesBefore + 2, count("outbox_messages where published_at is null"));
    }

    @Test
    void rejectsReuseOfAnIdempotencyKeyForDifferentInput() {
        UUID key = UUID.randomUUID();
        workflows.start(key, "happy-path", new BigDecimal("129.90"), "EUR");

        assertThrows(IllegalArgumentException.class,
                () -> workflows.start(key, "happy-path", new BigDecimal("130.00"), "EUR"));
    }

    private int count(String tableAndCondition) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableAndCondition, Integer.class);
    }
}
