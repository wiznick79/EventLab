package pt.eventlab.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExperimentPlanTest {

    @Test
    void roundTripsACombinedCustomPlan() {
        ExperimentPlan plan = new ExperimentPlan(2, FulfilmentBehavior.BUSINESS_REJECTION);

        assertEquals(plan, ExperimentPlan.preset(plan.scenarioId()));
        assertEquals(
                "two payment-result deliveries produce one payment state change; payment is compensated and workflow ends COMPENSATED",
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
    void rejectsUnboundedDuplicateCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentPlan(3, FulfilmentBehavior.SUCCESS));
    }
}
