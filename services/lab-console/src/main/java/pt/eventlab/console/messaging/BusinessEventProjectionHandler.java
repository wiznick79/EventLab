package pt.eventlab.console.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.console.domain.TimelineProjectionService;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.messaging.InboxStore;

@Service
class BusinessEventProjectionHandler {

    private static final String HANDLER = "lab-console.timeline";

    private final InboxStore inbox;
    private final TimelineProjectionService projection;

    BusinessEventProjectionHandler(InboxStore inbox, TimelineProjectionService projection) {
        this.inbox = inbox;
        this.projection = projection;
    }

    @Transactional
    public void handle(EventEnvelope<JsonNode> event, String traceId) {
        boolean firstDelivery = inbox.claim(event.eventId(), HANDLER);
        projection.project(event, traceId, !firstDelivery);
    }
}
