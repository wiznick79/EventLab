package pt.eventlab.console.api;

import java.util.List;

public record EvidenceCheckResponse(
        String id,
        String label,
        String status,
        String observation,
        List<String> traceIds) {
}
