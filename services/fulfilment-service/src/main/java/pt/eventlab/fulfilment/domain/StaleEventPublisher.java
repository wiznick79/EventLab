package pt.eventlab.fulfilment.domain;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class StaleEventPublisher {
    private final FulfilmentApplicationService fulfilments;

    StaleEventPublisher(FulfilmentApplicationService fulfilments) {
        this.fulfilments = fulfilments;
    }

    @Scheduled(fixedDelayString = "${eventlab.fulfilment.stale-event-scan-delay:250}")
    void publishDueEvents() {
        fulfilments.publishDueStaleEvents();
    }
}
