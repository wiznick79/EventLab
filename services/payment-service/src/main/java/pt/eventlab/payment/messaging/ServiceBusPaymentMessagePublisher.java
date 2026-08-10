package pt.eventlab.payment.messaging;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.messaging.ServiceBusMessageFactory;

@Component
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class ServiceBusPaymentMessagePublisher implements PaymentMessagePublisher {

    private final ServiceBusMessageFactory messages;
    private final ServiceBusSenderClient sender;

    ServiceBusPaymentMessagePublisher(PaymentMessagingProperties properties, ServiceBusMessageFactory messages) {
        this.messages = messages;
        this.sender = new ServiceBusClientBuilder()
                .connectionString(properties.connectionString())
                .sender()
                .topicName(properties.businessEventsTopic())
                .buildClient();
    }

    @Override
    public void publish(EventEnvelope<?> event) {
        sender.sendMessage(messages.create(event));
    }

    @PreDestroy
    void close() {
        sender.close();
    }
}
