package pt.eventlab.workflow.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eventlab.messaging")
public record WorkflowMessagingProperties(
        boolean enabled,
        String connectionString,
        String paymentCommandsQueue,
        String fulfilmentCommandsQueue,
        String businessEventsTopic,
        String workflowEventsSubscription) {
}
