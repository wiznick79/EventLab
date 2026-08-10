package pt.eventlab.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
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
        workflows.start("happy-path", new BigDecimal("129.90"), "EUR");

        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from workflow_runs", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from outbox_messages where published_at is null", Integer.class));
    }
}
