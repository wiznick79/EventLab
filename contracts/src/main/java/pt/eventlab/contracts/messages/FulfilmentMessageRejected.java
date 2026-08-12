package pt.eventlab.contracts.messages;

import java.util.UUID;

public record FulfilmentMessageRejected(
        UUID workflowId,
        int receivedSchemaVersion,
        int supportedSchemaVersion,
        int attempt,
        int maxAttempts) {
}
