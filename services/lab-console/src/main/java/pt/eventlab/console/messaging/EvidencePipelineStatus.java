package pt.eventlab.console.messaging;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EvidencePipelineStatus {

    private final boolean enabled;
    private final AtomicReference<String> state;
    private volatile Instant lastEventAt;
    private volatile String lastError;

    public EvidencePipelineStatus(@Value("${eventlab.messaging.enabled:false}") boolean enabled) {
        this.enabled = enabled;
        this.state = new AtomicReference<>(enabled ? "STARTING" : "DISABLED");
    }

    public void running() {
        state.set("RUNNING");
        lastError = null;
    }

    public void received() {
        lastEventAt = Instant.now();
        running();
    }

    public void failed(Throwable error) {
        state.set("ERROR");
        lastError = error.getClass().getSimpleName();
    }

    public boolean acceptingExperiments() {
        return enabled && "RUNNING".equals(state.get());
    }

    public Snapshot snapshot() {
        return new Snapshot(enabled, state.get(), lastEventAt, lastError);
    }

    public record Snapshot(boolean enabled, String state, Instant lastEventAt, String lastError) {
    }
}
