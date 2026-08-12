package pt.eventlab.workflow.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.eventlab.contracts.EventEnvelope;
import pt.eventlab.contracts.messages.FulfilmentDeadLettered;
import pt.eventlab.messaging.InboxStore;
import pt.eventlab.workflow.domain.WorkflowApplicationService;

@Service
class FulfilmentDeadLetteredHandler {
    private static final String HANDLER = "workflow.fulfilment-dead-lettered";
    private final InboxStore inbox;
    private final WorkflowApplicationService workflows;

    FulfilmentDeadLetteredHandler(InboxStore inbox, WorkflowApplicationService workflows) {
        this.inbox = inbox;
        this.workflows = workflows;
    }

    @Transactional
    public boolean handle(EventEnvelope<FulfilmentDeadLettered> event) {
        if (!inbox.claim(event.eventId(), HANDLER)) return false;
        workflows.recordFulfilmentDeadLettered(event);
        return true;
    }
}
