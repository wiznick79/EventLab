package pt.eventlab.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import pt.eventlab.console.api.RunResponse;
import pt.eventlab.console.api.StartRunRequest;
import pt.eventlab.console.domain.ExperimentRunRegistry;
import pt.eventlab.console.domain.LoadExperiment;
import pt.eventlab.console.domain.LoadExperimentRepository;
import pt.eventlab.console.domain.TimelineProjectionService;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.ExperimentPlan;
import pt.eventlab.contracts.FulfilmentBehavior;
import pt.eventlab.contracts.MessageTypes;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:console;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eventlab.messaging.enabled=false",
        "management.tracing.enabled=false"
})
@AutoConfigureMockMvc
class LabConsoleApplicationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TimelineProjectionService timeline;

    @Autowired
    private ExperimentRunRegistry runs;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LoadExperimentRepository loadExperiments;

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

    @Test
    void persistsAPlanForShareableInspection() {
        UUID workflowId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        ExperimentPlan plan = new ExperimentPlan(2, FulfilmentBehavior.BUSINESS_REJECTION);

        runs.register(new StartRunRequest("custom-plan", plan, java.math.BigDecimal.TEN, "EUR"),
                new RunResponse(workflowId, planId, "PAYMENT_PENDING"));

        var details = runs.details(workflowId);
        assertEquals(planId, details.experimentPlanId());
        assertEquals(plan, details.experimentPlan());
        assertEquals(plan.expectedInvariant(), details.expectedInvariant());
        assertEquals("PAYMENT_PENDING", details.state());
        assertTrue(runs.recent(12).stream().anyMatch(run -> run.workflowId().equals(workflowId)));
    }

    @Test
    void producesABackendEvidenceAssessment() throws Exception {
        UUID workflowId = UUID.randomUUID();
        runs.register(new StartRunRequest("happy-path", null, java.math.BigDecimal.TEN, "EUR"),
                new RunResponse(workflowId, UUID.randomUUID(), "PAYMENT_PENDING"));

        mvc.perform(get("/api/v1/runs/{workflowId}/evidence", workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value(workflowId.toString()))
                .andExpect(jsonPath("$.assessment").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.checks[0].id").value("payment-deliveries"));
    }

    @Test
    void downloadsPrettyPrintedEvidenceJson() throws Exception {
        UUID workflowId = UUID.randomUUID();
        runs.register(new StartRunRequest("happy-path", null, java.math.BigDecimal.TEN, "EUR"),
                new RunResponse(workflowId, UUID.randomUUID(), "PAYMENT_PENDING"));

        mvc.perform(get("/api/v1/runs/{workflowId}/evidence/download", workflowId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"eventlab-" + workflowId + "-evidence.json\""))
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        System.lineSeparator() + "  \"workflowId\"")));
    }

    @Test
    void downloadsLoadEvidenceAsFormattedJsonAndMarkdown() throws Exception {
        UUID experimentId = UUID.randomUUID();
        LoadExperiment experiment = new LoadExperiment(experimentId, "BURST", 10, 20, 0, 1,
                "NONE", 0, Instant.now());
        experiment.launchCompleted(Instant.now());
        loadExperiments.save(experiment);

        mvc.perform(get("/api/v1/load-experiments/{id}/download.json", experimentId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"eventlab-load-" + experimentId + ".json\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        System.lineSeparator() + "  \"id\"")));
        mvc.perform(get("/api/v1/load-experiments/{id}/download.md", experimentId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"eventlab-load-" + experimentId + ".md\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "# EventLab load experiment")));
    }

    @Test
    void reportsDeadLetterInspectionUnavailableWhenMessagingIsDisabled() throws Exception {
        UUID workflowId = UUID.randomUUID();
        runs.register(new StartRunRequest("happy-path", null, java.math.BigDecimal.TEN, "EUR"),
                new RunResponse(workflowId, UUID.randomUUID(), "PAYMENT_PENDING"));

        mvc.perform(get("/api/v1/runs/{workflowId}/dead-letter", workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value(workflowId.toString()))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.replayAllowed").value(false));
    }
}
