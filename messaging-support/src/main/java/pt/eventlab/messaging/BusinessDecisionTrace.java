package pt.eventlab.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class BusinessDecisionTrace {

    private final Tracer tracer;

    BusinessDecisionTrace(Tracer tracer) {
        this.tracer = tracer;
    }

    public <T> T record(
            String spanName,
            UUID eventId,
            UUID workflowId,
            Supplier<Outcome<T>> operation) {
        Span span = tracer.nextSpan().name(spanName).start();
        span.tag("eventlab.message.event_id", eventId.toString());
        span.tag("eventlab.workflow.id", workflowId.toString());
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            Outcome<T> outcome = operation.get();
            span.tag("eventlab.decision", outcome.decision());
            span.tag("eventlab.state_change_applied", Boolean.toString(outcome.stateChangeApplied()));
            outcome.attributes().forEach(span::tag);
            return outcome.result();
        } catch (RuntimeException exception) {
            span.error(exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    public record Outcome<T>(
            String decision,
            boolean stateChangeApplied,
            T result,
            Map<String, String> attributes) {

        public Outcome(String decision, boolean stateChangeApplied, T result) {
            this(decision, stateChangeApplied, result, Map.of());
        }
    }
}
