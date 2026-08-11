package pt.eventlab.messaging;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessDecisionTraceTest {

    @Test
    void recordsDecisionEvidenceAndReturnsTheOperationResult() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name("eventlab.test.decision")).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(scope);
        BusinessDecisionTrace decisions = new BusinessDecisionTrace(tracer);
        UUID eventId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();

        boolean result = decisions.record(
                "eventlab.test.decision", eventId, workflowId,
                () -> new BusinessDecisionTrace.Outcome<>(
                        "STALE_IGNORED", false, true,
                        Map.of("eventlab.version.received", "1")));

        assertTrue(result);
        verify(span).tag("eventlab.message.event_id", eventId.toString());
        verify(span).tag("eventlab.workflow.id", workflowId.toString());
        verify(span).tag("eventlab.decision", "STALE_IGNORED");
        verify(span).tag("eventlab.state_change_applied", "false");
        verify(span).tag("eventlab.version.received", "1");
        verify(scope).close();
        verify(span).end();
    }
}
