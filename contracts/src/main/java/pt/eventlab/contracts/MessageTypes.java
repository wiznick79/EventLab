package pt.eventlab.contracts;

public final class MessageTypes {

    public static final String WORKFLOW_STARTED = "workflow.started";
    public static final String AUTHORIZE_PAYMENT = "payment.authorize";
    public static final String PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String WORKFLOW_COMPLETED = "workflow.completed";

    private MessageTypes() {
    }
}
