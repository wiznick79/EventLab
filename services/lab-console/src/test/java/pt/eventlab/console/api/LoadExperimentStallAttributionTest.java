package pt.eventlab.console.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoadExperimentStallAttributionTest {

    @Test
    void attributesTheLongestEvidenceBackedSegment() {
        StallAttributionResponse handoff = LoadExperimentService.dominantStall(
                "PROVED", 2_000, 13_700, 14_000, 14_400);
        StallAttributionResponse terminal = LoadExperimentService.dominantStall(
                "PROVED", 2_000, 2_300, 4_000, 14_100);

        assertEquals("WORKFLOW_HANDOFF", handoff.stage());
        assertEquals(11_700, handoff.durationMillis());
        assertEquals(81, handoff.sharePercent());
        assertEquals("TERMINAL_EVIDENCE", terminal.stage());
        assertEquals(10_100, terminal.durationMillis());
        assertEquals(72, terminal.sharePercent());
    }

    @Test
    void waitsForTheWorkflowWaveToDrainBeforeAttributing() {
        StallAttributionResponse result = LoadExperimentService.dominantStall(
                "RUNNING", 2_000, 2_300, 4_000, 0);

        assertEquals("COLLECTING", result.stage());
    }
}
