package pt.eventlab.console.api;

public record StartLoadExperimentRequest(
        int workflowCount,
        String trafficPattern,
        int duplicatePercentage,
        int intervalMillis,
        int consumerConcurrency,
        String constrainedStage,
        int processingDelayMillis) {

    public StartLoadExperimentRequest {
        if (consumerConcurrency == 0) {
            consumerConcurrency = 1;
        }
        if (constrainedStage == null || constrainedStage.isBlank()) {
            constrainedStage = "NONE";
        }
        if ("NONE".equalsIgnoreCase(constrainedStage)) {
            processingDelayMillis = 0;
        }
    }
}
