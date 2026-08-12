package pt.eventlab.console.api;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import java.time.Instant;
import java.util.UUID;

public record DeadLetterInspectionResponse(
        UUID workflowId,
        String status,
        String queue,
        String messageId,
        String subject,
        String deadLetterReason,
        String errorDescription,
        Long deliveryCount,
        Long sequenceNumber,
        Instant enqueuedAt,
        Integer schemaVersion,
        boolean replayAllowed,
        String operatorGuidance) {

    static DeadLetterInspectionResponse found(
            UUID workflowId, String queue, ServiceBusReceivedMessage message) {
        String reason = message.getDeadLetterReason();
        boolean replayAllowed = "FulfilmentRetriesExhausted".equals(reason);
        return new DeadLetterInspectionResponse(
                workflowId,
                "FOUND",
                queue + "/$deadletterqueue",
                message.getMessageId(),
                message.getSubject(),
                reason,
                message.getDeadLetterErrorDescription(),
                message.getDeliveryCount(),
                message.getSequenceNumber(),
                message.getEnqueuedTime() == null ? null : message.getEnqueuedTime().toInstant(),
                integerProperty(message, "schemaVersion"),
                replayAllowed,
                replayAllowed
                        ? "Restore the transient dependency before using the guarded replay action."
                        : "Replay is disabled: deploy a compatible consumer or transform the contract first.");
    }

    static DeadLetterInspectionResponse notFound(UUID workflowId, String queue) {
        return new DeadLetterInspectionResponse(
                workflowId, "NOT_FOUND", queue + "/$deadletterqueue",
                null, null, null, null, null, null, null, null, false,
                "No matching message is currently visible in the dead-letter queue.");
    }

    static DeadLetterInspectionResponse unavailable(UUID workflowId, String queue) {
        return new DeadLetterInspectionResponse(
                workflowId, "UNAVAILABLE", queue + "/$deadletterqueue",
                null, null, null, null, null, null, null, null, false,
                "Native broker inspection is unavailable because messaging is disabled.");
    }

    private static Integer integerProperty(ServiceBusReceivedMessage message, String name) {
        Object value = message.getApplicationProperties().get(name);
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
