package pt.eventlab.console.api;

import java.util.List;
import java.util.UUID;
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

    LoadExperimentController(LoadExperimentService loadExperiments) {
        this.loadExperiments = loadExperiments;
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
}
