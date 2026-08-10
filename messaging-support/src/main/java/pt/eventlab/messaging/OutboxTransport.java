package pt.eventlab.messaging;

public interface OutboxTransport extends AutoCloseable {

    void send(OutboxMessage message);

    @Override
    void close();
}
