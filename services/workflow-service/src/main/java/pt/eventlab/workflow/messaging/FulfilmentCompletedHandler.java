package pt.eventlab.workflow.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.FulfilmentCompleted;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@Service
class FulfilmentCompletedHandler {
    private static final String HANDLER = "workflow.fulfilment-completed";
    private final InboxStore inbox;
    private final WorkflowApplicationService workflows;

    FulfilmentCompletedHandler(InboxStore inbox, WorkflowApplicationService workflows) {
        this.inbox = inbox;
        this.workflows = workflows;
    }

    @Transactional
    public boolean handle(EventEnvelope<FulfilmentCompleted> event) {
        if (!inbox.claim(event.eventId(), HANDLER)) return false;
        workflows.recordFulfilmentCompleted(event);
        return true;
    }
}
