package pt.eventlab.console.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoadExperimentTest {

    @Test
    void recordsAcceptedMembersBeforeLaunchingIsComplete() {
        LoadExperiment experiment = new LoadExperiment(UUID.randomUUID(), "BURST", 25, 20, 0,
                Instant.parse("2026-08-12T20:00:00Z"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        experiment.accepted(first);
        experiment.accepted(second);
        experiment.launchFailed();

        assertEquals("LAUNCHING", experiment.status());
        assertEquals(java.util.List.of(first, second), experiment.workflowIds());
        assertEquals(1, experiment.launchFailures());

        experiment.launchCompleted(Instant.parse("2026-08-12T20:00:01Z"));

        assertEquals("RUNNING", experiment.status());
    }
}
