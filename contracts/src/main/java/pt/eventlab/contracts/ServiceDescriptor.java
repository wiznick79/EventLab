package pt.eventlab.contracts;

import java.util.Objects;

public record ServiceDescriptor(String service, String role, String status) {

    public ServiceDescriptor {
        service = requireText(service, "service");
        role = requireText(role, "role");
        status = Objects.requireNonNullElse(status, "unknown");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
