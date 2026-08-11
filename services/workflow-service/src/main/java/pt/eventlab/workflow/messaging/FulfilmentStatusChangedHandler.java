package pt.eventlab.workflow.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.FulfilmentStatusChanged;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@Service
class FulfilmentStatusChangedHandler {
    private static final String HANDLER = "workflow.fulfilment-status-changed";
    private final InboxStore inbox;
    private final WorkflowApplicationService workflows;

    FulfilmentStatusChangedHandler(InboxStore inbox, WorkflowApplicationService workflows) {
        this.inbox = inbox;
        this.workflows = workflows;
    }

    @Transactional
    public boolean handle(EventEnvelope<FulfilmentStatusChanged> event) {
        if (!inbox.claim(event.eventId(), HANDLER)) return false;
        workflows.observeFulfilmentStatus(event);
        return true;
    }
}
