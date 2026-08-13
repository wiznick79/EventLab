package pt.eventlab.contracts.messages;

import java.util.UUID;

public record FulfilmentCommandQueued(UUID workflowId, UUID commandEventId) {
}
