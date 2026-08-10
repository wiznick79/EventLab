package pt.eventlab.payment.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eventlab.messaging")
public record PaymentMessagingProperties(
        boolean enabled,
        String connectionString,
        String paymentCommandsQueue,
        String businessEventsTopic) {
}
