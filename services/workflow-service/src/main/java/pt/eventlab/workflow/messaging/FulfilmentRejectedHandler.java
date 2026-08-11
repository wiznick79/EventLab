package pt.eventlab.workflow.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.FulfilmentRejected;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@Service
class FulfilmentRejectedHandler {
    private static final String HANDLER = "workflow.fulfilment-rejected";
    private final InboxStore inbox;
    private final WorkflowApplicationService workflows;

    FulfilmentRejectedHandler(InboxStore inbox, WorkflowApplicationService workflows) {
        this.inbox = inbox;
        this.workflows = workflows;
    }

    @Transactional
    public boolean handle(EventEnvelope<FulfilmentRejected> event) {
        if (!inbox.claim(event.eventId(), HANDLER)) return false;
        workflows.recordFulfilmentRejected(event);
        return true;
    }
}
