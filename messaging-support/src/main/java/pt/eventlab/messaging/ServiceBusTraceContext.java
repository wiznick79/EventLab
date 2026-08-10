package pt.eventlab.messaging;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Set;

public final class ServiceBusTraceContext {

    private static final TextMapGetter<ServiceBusReceivedMessage> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(ServiceBusReceivedMessage carrier) {
            return Set.copyOf(carrier.getApplicationProperties().keySet());
        }

        @Override
        public String get(ServiceBusReceivedMessage carrier, String key) {
            Object value = carrier.getApplicationProperties().get(key);
            return value == null ? null : value.toString();
        }
    };

    private final OpenTelemetry openTelemetry;

    ServiceBusTraceContext(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    public Scope makeCurrent(ServiceBusReceivedMessage message) {
        Context extracted = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.root(), message, GETTER);
        return extracted.makeCurrent();
    }

    public String traceId(ServiceBusReceivedMessage message) {
        Object value = message.getApplicationProperties().get("traceId");
        return value == null ? null : value.toString();
    }
}
