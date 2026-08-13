package pt.eventlab.console.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eventlab.messaging")
public record LabConsoleMessagingProperties(
        boolean enabled,
        String connectionString,
        String fullyQualifiedNamespace,
        String paymentCommandsQueue,
        String fulfilmentCommandsQueue,
        String businessEventsTopic,
        String workflowEventsSubscription,
        String labConsoleEventsSubscription) {
}
