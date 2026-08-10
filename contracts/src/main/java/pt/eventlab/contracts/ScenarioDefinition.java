package pt.eventlab.contracts;

public record ScenarioDefinition(
        String id,
        String displayName,
        FailureMode failureMode,
        String expectedInvariant) {

    public ScenarioDefinition {
        id = requireText(id, "id");
        displayName = requireText(displayName, "displayName");
        if (failureMode == null) {
            throw new IllegalArgumentException("failureMode is required");
        }
        expectedInvariant = requireText(expectedInvariant, "expectedInvariant");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
