package pt.eventlab.console.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class ExperimentAdmissionService {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final ArrayDeque<Instant> admitted = new ArrayDeque<>();
    private final Clock clock = Clock.systemUTC();
    private final int maximumPerMinute;

    ExperimentAdmissionService(
            @Value("${eventlab.deployment.max-runs-per-minute:120}") int maximumPerMinute) {
        this.maximumPerMinute = maximumPerMinute;
    }

    synchronized void acquire() {
        Instant now = clock.instant();
        Instant oldestAllowed = now.minus(WINDOW);
        while (!admitted.isEmpty() && admitted.peekFirst().isBefore(oldestAllowed)) {
            admitted.removeFirst();
        }
        if (admitted.size() >= maximumPerMinute) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "The public experiment admission budget is exhausted; retry in one minute");
        }
        admitted.addLast(now);
    }
}
