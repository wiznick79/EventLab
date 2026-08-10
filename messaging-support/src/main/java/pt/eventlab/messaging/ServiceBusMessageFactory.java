package pt.eventlab.messaging;

import com.azure.messaging.servicebus.ServiceBusMessage;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import java.util.Locale;
import pt.eventlab.contracts.EventEnvelope;

public final class ServiceBusMessageFactory {

    private final ServiceBusEnvelopeCodec codec;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    ServiceBusMessageFactory(ServiceBusEnvelopeCodec codec, OpenTelemetry openTelemetry, Tracer tracer) {
        this.codec = codec;
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
    }

    public ServiceBusMessage create(EventEnvelope<?> envelope) {
        ServiceBusMessage message = new ServiceBusMessage(codec.encode(envelope));
        message.setMessageId(envelope.eventId().toString());
        message.setCorrelationId(envelope.correlationId().toString());
        message.setContentType("application/json");
        message.setSubject(envelope.eventType());
        message.getApplicationProperties().put("eventType", envelope.eventType());
        message.getApplicationProperties().put("schemaVersion", envelope.schemaVersion());
        message.getApplicationProperties().put("workflowId", envelope.workflowId().toString());

        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            message.getApplicationProperties().put("traceId", currentSpan.context().traceId().toLowerCase(Locale.ROOT));
        }

        openTelemetry.getPropagators().getTextMapPropagator().inject(
                Context.current(),
                message,
                (carrier, key, value) -> carrier.getApplicationProperties().put(key, value));
        return message;
    }
}
