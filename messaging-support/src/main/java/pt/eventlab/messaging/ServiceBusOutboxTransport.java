package pt.eventlab.messaging;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceBusOutboxTransport implements OutboxTransport {

    private final String connectionString;
    private final ServiceBusEnvelopeCodec codec;
    private final ServiceBusMessageFactory messages;
    private final Map<String, ServiceBusSenderClient> senders = new ConcurrentHashMap<>();

    public ServiceBusOutboxTransport(
            String connectionString,
            ServiceBusEnvelopeCodec codec,
            ServiceBusMessageFactory messages) {
        this.connectionString = connectionString;
        this.codec = codec;
        this.messages = messages;
    }

    @Override
    public void send(OutboxMessage outboxMessage) {
        ServiceBusMessage message = messages.create(codec.decode(outboxMessage.payload()));
        outboxMessage.traceHeaders().forEach((key, value) ->
                message.getApplicationProperties().put(key, value));
        sender(outboxMessage).sendMessage(message);
    }

    private ServiceBusSenderClient sender(OutboxMessage message) {
        String key = message.destinationType() + ":" + message.destinationName();
        return senders.computeIfAbsent(key, ignored -> {
            ServiceBusClientBuilder.ServiceBusSenderClientBuilder builder = new ServiceBusClientBuilder()
                    .connectionString(connectionString)
                    .sender();
            if (message.destinationType() == OutboxDestination.QUEUE) {
                builder.queueName(message.destinationName());
            } else {
                builder.topicName(message.destinationName());
            }
            return builder.buildClient();
        });
    }

    @Override
    public void close() {
        senders.values().forEach(ServiceBusSenderClient::close);
    }
}
