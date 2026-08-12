package pt.eventlab.contracts;

public final class MessageTypes {

    public static final String WORKFLOW_STARTED = "workflow.started";
    public static final String AUTHORIZE_PAYMENT = "payment.authorize";
    public static final String PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String REQUEST_FULFILMENT = "fulfilment.request";
    public static final String FULFILMENT_ATTEMPT_FAILED = "fulfilment.attempt-failed";
    public static final String FULFILMENT_MESSAGE_REJECTED = "fulfilment.message-rejected";
    public static final String FULFILMENT_DEAD_LETTERED = "fulfilment.dead-lettered";
    public static final String FULFILMENT_RECOVERY_REQUESTED = "fulfilment.recovery-requested";
    public static final String FULFILMENT_COMPLETED = "fulfilment.completed";
    public static final String FULFILMENT_REJECTED = "fulfilment.rejected";
    public static final String COMPENSATE_PAYMENT = "payment.compensate";
    public static final String PAYMENT_COMPENSATED = "payment.compensated";
    public static final String WORKFLOW_COMPLETED = "workflow.completed";
    public static final String WORKFLOW_COMPENSATED = "workflow.compensated";
    public static final String WORKFLOW_INTERVENTION_REQUIRED = "workflow.intervention-required";
    public static final String FULFILMENT_STATUS_CHANGED = "fulfilment.status-changed";
    public static final String STALE_EVENT_IGNORED = "workflow.stale-event-ignored";

    private MessageTypes() {
    }
}
