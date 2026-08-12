package pt.eventlab.console.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunConsistencyServiceTest {

    @Test
    void reportsAProjectionThatMissedAnAuthoritativeTerminalState() {
        Instant terminalAt = Instant.parse("2026-08-12T12:00:00Z");

        RunConsistencyResponse response = RunConsistencyService.compare(UUID.randomUUID(),
                new WorkflowClient.WorkflowSnapshot("COMPLETED", terminalAt),
                "PAYMENT_PENDING", terminalAt.plusSeconds(12));

        assertEquals("PROJECTION_BEHIND", response.status());
        assertEquals(12, response.lagSeconds());
    }

    @Test
    void reportsAgreementAtTheTerminalBoundary() {
        Instant terminalAt = Instant.parse("2026-08-12T12:00:00Z");

        RunConsistencyResponse response = RunConsistencyService.compare(UUID.randomUUID(),
                new WorkflowClient.WorkflowSnapshot("COMPENSATED", terminalAt),
                "COMPENSATED", terminalAt.plusSeconds(30));

        assertEquals("CONSISTENT", response.status());
        assertEquals(0, response.lagSeconds());
    }
}
