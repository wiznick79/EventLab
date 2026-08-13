package pt.eventlab.console.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/load-experiments")
class LoadExperimentController {

    private final LoadExperimentService loadExperiments;
    private final ObjectMapper objectMapper;

    LoadExperimentController(LoadExperimentService loadExperiments, ObjectMapper objectMapper) {
        this.loadExperiments = loadExperiments;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    LoadExperimentResponse start(@RequestBody StartLoadExperimentRequest request) {
        return loadExperiments.start(request);
    }

    @GetMapping
    List<LoadExperimentResponse> recent() {
        return loadExperiments.recent();
    }

    @GetMapping("/{id}")
    LoadExperimentResponse inspect(@PathVariable UUID id) {
        return loadExperiments.inspect(id);
    }

    @GetMapping(path = "/{id}/download.json", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> downloadJson(@PathVariable UUID id) {
        try {
            String body = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(loadExperiments.inspect(id)) + System.lineSeparator();
            return download(body, "application/json", "eventlab-load-" + id + ".json");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize load experiment", exception);
        }
    }

    @GetMapping(path = "/{id}/download.md", produces = "text/markdown")
    ResponseEntity<String> downloadMarkdown(@PathVariable UUID id) {
        LoadExperimentResponse report = loadExperiments.inspect(id);
        String body = """
                # EventLab load experiment

                - **Experiment:** `%s`
                - **Result:** %s
                - **Workload:** %d %s workflows, %d%% duplicate deliveries
                - **Consumer concurrency:** %d
                - **Constraint:** %s%s
                - **Correctness:** %d/%d proved, %d invariant violations
                - **Throughput:** %.2f workflows/second
                - **Latency:** median %.2f s, p95 %.2f s
                - **Full drain:** %.2f s
                - **Dominant delay:** %s, %.2f s (%d%% of drain)
                - **Constraint assessment:** %s

                ## Conclusion

                %s
                """.formatted(report.id(), report.status(), report.requestedWorkflows(),
                report.trafficPattern().toLowerCase(), report.duplicatePercentage(),
                report.consumerConcurrency(), report.constrainedStage(),
                "NONE".equals(report.constrainedStage()) ? "" : " at " + report.processingDelayMillis() + " ms/message",
                report.provedWorkflows(), report.acceptedWorkflows(), report.invariantViolations(),
                report.throughputPerSecond(), report.medianLatencyMillis() / 1000.0,
                report.p95LatencyMillis() / 1000.0, report.drainDurationMillis() / 1000.0,
                report.dominantStall().label(), report.dominantStall().durationMillis() / 1000.0,
                report.dominantStall().sharePercent(), report.constraintAssessment(),
                conclusion(report));
        return download(body, "text/markdown", "eventlab-load-" + id + ".md");
    }

    private ResponseEntity<String> download(String body, String contentType, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType(contentType))
                .body(body);
    }

    private String conclusion(LoadExperimentResponse report) {
        if (!"PROVED".equals(report.status())) {
            return "The experiment did not prove every requested invariant; inspect its member evidence before drawing a performance conclusion.";
        }
        if ("MATCHED".equals(report.constraintAssessment())) {
            return "Every accepted workflow preserved its invariant, and the observed dominant delay matched the controlled bottleneck hypothesis.";
        }
        if ("NOT_MATCHED".equals(report.constraintAssessment())) {
            return "Every accepted workflow preserved its invariant, but another pipeline stage dominated the controlled bottleneck hypothesis.";
        }
        return "Every accepted workflow preserved its invariant under the measured load.";
    }
}
