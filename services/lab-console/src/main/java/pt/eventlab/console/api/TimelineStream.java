package pt.eventlab.console.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TimelineStream {

    private static final int MAX_GLOBAL_SUBSCRIBERS = 200;
    private static final int MAX_WORKFLOW_SUBSCRIBERS = 4;
    private static final long STREAM_TIMEOUT_MILLIS = 5 * 60 * 1000L;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final AtomicInteger subscriberCount = new AtomicInteger();

    public SseEmitter subscribe(UUID workflowId, List<TimelineEventResponse> existing) {
        if (subscriberCount.incrementAndGet() > MAX_GLOBAL_SUBSCRIBERS) {
            subscriberCount.decrementAndGet();
            throw tooManyStreams();
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        CopyOnWriteArrayList<SseEmitter> workflowSubscribers =
                subscribers.computeIfAbsent(workflowId, ignored -> new CopyOnWriteArrayList<>());
        workflowSubscribers.add(emitter);
        if (workflowSubscribers.size() > MAX_WORKFLOW_SUBSCRIBERS) {
            remove(workflowId, emitter);
            throw tooManyStreams();
        }
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

    @Scheduled(fixedDelay = 15_000)
    void keepAlive() {
        subscribers.forEach((workflowId, emitters) -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException exception) {
                emitter.completeWithError(exception);
                remove(workflowId, emitter);
            }
        }));
    }

    private void remove(UUID workflowId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(workflowId);
        if (emitters != null) {
            if (emitters.remove(emitter)) {
                subscriberCount.decrementAndGet();
            }
            if (emitters.isEmpty()) {
                subscribers.remove(workflowId);
            }
        }
    }

    private ResponseStatusException tooManyStreams() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many live timeline streams; retry after another viewer disconnects");
    }
}
