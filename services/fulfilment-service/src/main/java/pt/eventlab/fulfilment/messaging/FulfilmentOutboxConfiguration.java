package pt.eventlab.fulfilment.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.eventlab.messaging.OutboxDispatcher;
import pt.eventlab.messaging.OutboxStore;
import pt.eventlab.messaging.OutboxTransport;
import pt.eventlab.messaging.ServiceBusEnvelopeCodec;
import pt.eventlab.messaging.ServiceBusMessageFactory;
import pt.eventlab.messaging.ServiceBusOutboxTransport;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "eventlab.messaging.enabled", havingValue = "true")
class FulfilmentOutboxConfiguration {
    @Bean(destroyMethod = "close")
    OutboxTransport fulfilmentOutboxTransport(FulfilmentMessagingProperties properties,
            ServiceBusEnvelopeCodec codec, ServiceBusMessageFactory messages) {
        return new ServiceBusOutboxTransport(
                properties.connectionString(), properties.fullyQualifiedNamespace(), codec, messages);
    }

    @Bean
    OutboxDispatcher fulfilmentOutboxDispatcher(OutboxStore outbox, OutboxTransport transport) {
        return new OutboxDispatcher(outbox, transport);
    }
}
