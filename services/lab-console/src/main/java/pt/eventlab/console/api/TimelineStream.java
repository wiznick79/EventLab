package pt.eventlab.console.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TimelineStream {

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID workflowId, List<TimelineEventResponse> existing) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(workflowId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(workflowId, emitter));
        emitter.onTimeout(() -> remove(workflowId, emitter));
        emitter.onError(ignored -> remove(workflowId, emitter));
        try {
            for (TimelineEventResponse event : existing) {
                emitter.send(SseEmitter.event().name("timeline-event").id(Long.toString(event.sequence())).data(event));
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(TimelineEventResponse event) {
        for (SseEmitter emitter : subscribers.getOrDefault(event.workflowId(), new CopyOnWriteArrayList<>())) {
            try {
                emitter.send(SseEmitter.event().name("timeline-event").id(Long.toString(event.sequence())).data(event));
            } catch (IOException exception) {
                emitter.completeWithError(exception);
                remove(event.workflowId(), emitter);
            }
        }
    }

    private void remove(UUID workflowId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(workflowId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                subscribers.remove(workflowId);
            }
        }
    }
}
