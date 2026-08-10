package pt.eventlab.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pt.eventlab.console.domain.TimelineProjectionService;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.MessageTypes;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:console;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eventlab.messaging.enabled=false",
        "management.tracing.enabled=false"
})
class LabConsoleApplicationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TimelineProjectionService timeline;

    @Test
    void contextLoads() {
    }

    @Test
    void retainsDuplicateDeliveryAsAnObservation() {
        UUID workflowId = UUID.randomUUID();
        EventEnvelope<com.fasterxml.jackson.databind.JsonNode> event = new EventEnvelope<>(
                UUID.randomUUID(), MessageTypes.PAYMENT_AUTHORIZED, 1,
                workflowId, null, workflowId, Instant.now(), objectMapper.createObjectNode());

        timeline.project(event, "0123456789abcdef0123456789abcdef", false);
        timeline.project(event, "0123456789abcdef0123456789abcdef", true);

        var events = timeline.timeline(workflowId);
        assertEquals(2, events.size());
        assertTrue(events.get(1).duplicateDelivery());
        assertEquals("DUPLICATE_IGNORED", events.get(1).state());
    }
}
