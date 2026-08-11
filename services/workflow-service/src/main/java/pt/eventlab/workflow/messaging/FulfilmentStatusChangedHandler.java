package pt.eventlab.workflow.messaging;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.FulfilmentStatusChanged;
import pt.eventlab.messaging.BusinessDecisionTrace;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@Service
class FulfilmentStatusChangedHandler {
    private static final String HANDLER = "workflow.fulfilment-status-changed";
    private final BusinessDecisionTrace decisions;
    private final InboxStore inbox;
    private final WorkflowApplicationService workflows;

    FulfilmentStatusChangedHandler(
            InboxStore inbox,
            BusinessDecisionTrace decisions,
            WorkflowApplicationService workflows) {
        this.inbox = inbox;
        this.decisions = decisions;
        this.workflows = workflows;
    }

    @Transactional
    public boolean handle(EventEnvelope<FulfilmentStatusChanged> event) {
        return decisions.record(
                "eventlab.workflow.version.decision", event.eventId(), event.workflowId(), () -> {
            if (!inbox.claim(event.eventId(), HANDLER)) {
                return new BusinessDecisionTrace.Outcome<>("DUPLICATE_IGNORED", false, false);
            }
            long currentVersion = workflows.observeFulfilmentStatus(event);
            return new BusinessDecisionTrace.Outcome<>("STALE_IGNORED", false, true, Map.of(
                    "eventlab.version.received", Long.toString(event.payload().aggregateVersion()),
                    "eventlab.version.current", Long.toString(currentVersion)));
        });
    }
}
