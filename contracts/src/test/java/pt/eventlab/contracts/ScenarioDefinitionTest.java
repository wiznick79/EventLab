package pt.eventlab.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScenarioDefinitionTest {

    @Test
    void capturesTheExpectedRecoveryInvariant() {
        ScenarioDefinition scenario = new ScenarioDefinition(
                "fulfilment-unavailable",
                "Fulfilment unavailable",
                FailureMode.TEMPORARY_UNAVAILABLE,
                "The workflow completes once the dead-lettered command is safely replayed");

        assertEquals(FailureMode.TEMPORARY_UNAVAILABLE, scenario.failureMode());
    }

    @Test
    void requiresAnExpectedInvariant() {
        assertThrows(IllegalArgumentException.class, () -> new ScenarioDefinition(
                "duplicate-payment",
                "Duplicate payment result",
                FailureMode.DUPLICATE_DELIVERY,
                " "));
    }
}
