package pt.eventlab.fulfilment.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eventlab.messaging")
public record FulfilmentMessagingProperties(
        boolean enabled,
        String connectionString,
        String fullyQualifiedNamespace,
        String fulfilmentCommandsQueue,
        String businessEventsTopic) {
}
