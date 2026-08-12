package pt.eventlab.console.api;

public record StartLoadExperimentRequest(
        int workflowCount,
        String trafficPattern,
        int duplicatePercentage,
        int intervalMillis) {
}
