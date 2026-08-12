package pt.eventlab.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExperimentPlanTest {

    @Test
    void roundTripsACombinedCustomPlan() {
        ExperimentPlan plan = new ExperimentPlan(2, FulfilmentBehavior.TEMPORARY_UNAVAILABLE,
                3, RecoveryMode.AUTOMATIC);

        assertEquals(plan, ExperimentPlan.preset(plan.scenarioId()));
        assertEquals(
                "two payment-result deliveries produce one payment state change; command reaches the DLQ after 3 attempts and completes exactly once after automatic recovery",
                plan.expectedInvariant());
    }

    @Test
    void translatesExistingPresets() {
        assertEquals(new ExperimentPlan(2, FulfilmentBehavior.SUCCESS),
                ExperimentPlan.preset("duplicate-payment-result"));
        assertEquals(new ExperimentPlan(1, FulfilmentBehavior.STALE_AFTER_SUCCESS),
                ExperimentPlan.preset("out-of-order-event"));
    }

    @Test
    void describesUnsupportedContractsAsPoisonMessages() {
        ExperimentPlan plan = new ExperimentPlan(1, FulfilmentBehavior.UNSUPPORTED_CONTRACT);

        assertEquals(
                "unsupported contract is rejected 3 times, dead-lettered, and never completes fulfilment",
                plan.expectedInvariant());
        assertEquals(plan, ExperimentPlan.preset(plan.scenarioId()));
    }

    @Test
    void rejectsUnboundedDuplicateCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentPlan(3, FulfilmentBehavior.SUCCESS));
    }

    @Test
    void defaultsMissingPolicyFieldsForOlderJsonContracts() {
        ExperimentPlan plan = new ExperimentPlan(1, FulfilmentBehavior.TEMPORARY_UNAVAILABLE, 0, null);

        assertEquals(4, plan.fulfilmentMaxAttempts());
        assertEquals(RecoveryMode.MANUAL, plan.recoveryMode());
    }
}
