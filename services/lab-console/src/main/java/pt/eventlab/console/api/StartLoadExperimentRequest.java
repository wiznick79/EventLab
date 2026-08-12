package pt.eventlab.console.api;

public record StartLoadExperimentRequest(
        int workflowCount,
        String trafficPattern,
        int duplicatePercentage,
        int intervalMillis,
        int consumerConcurrency) {

    public StartLoadExperimentRequest {
        if (consumerConcurrency == 0) {
            consumerConcurrency = 1;
        }
    }
}
