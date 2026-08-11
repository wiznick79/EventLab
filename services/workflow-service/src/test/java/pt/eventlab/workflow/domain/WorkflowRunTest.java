package pt.eventlab.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import pt.eventlab.contracts.WorkflowState;

class WorkflowRunTest {
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void recordsCompensationDeadlineAndSuccessfulTerminalState() {
        WorkflowRun workflow = WorkflowRun.start("fulfilment-rejected", new BigDecimal("129.90"), "EUR", NOW);
        workflow.recordPaymentAuthorized(NOW, NOW.plusSeconds(120));
        workflow.beginCompensation(NOW.plusSeconds(1), NOW.plusSeconds(121));
        workflow.compensated(NOW.plusSeconds(2));

        assertEquals(WorkflowState.COMPENSATED, workflow.state());
        assertNull(workflow.stepDeadline());
    }

    @Test
    void expiredActiveStepRequiresIntervention() {
        WorkflowRun workflow = WorkflowRun.start("happy-path", new BigDecimal("129.90"), "EUR", NOW);
        workflow.recordPaymentAuthorized(NOW, NOW.plusSeconds(120));

        assertEquals(WorkflowState.FULFILMENT_PENDING, workflow.requireIntervention(NOW.plusSeconds(121)));
        assertEquals(WorkflowState.FAILED_REQUIRES_INTERVENTION, workflow.state());
        assertNull(workflow.stepDeadline());
    }
}
