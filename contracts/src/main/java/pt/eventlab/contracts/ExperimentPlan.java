package pt.eventlab.contracts;

import java.util.Locale;

public record ExperimentPlan(
        int paymentResultDeliveries,
        FulfilmentBehavior fulfilmentBehavior,
        int fulfilmentMaxAttempts,
        RecoveryMode recoveryMode) {

    public ExperimentPlan(int paymentResultDeliveries, FulfilmentBehavior fulfilmentBehavior) {
        this(paymentResultDeliveries, fulfilmentBehavior, 4, RecoveryMode.MANUAL);
    }

    public ExperimentPlan {
        if (fulfilmentMaxAttempts == 0) fulfilmentMaxAttempts = 4;
        if (recoveryMode == null) recoveryMode = RecoveryMode.MANUAL;
        if (paymentResultDeliveries < 1 || paymentResultDeliveries > 2) {
            throw new IllegalArgumentException("paymentResultDeliveries must be between 1 and 2");
        }
        if (fulfilmentBehavior == null) {
            throw new IllegalArgumentException("fulfilmentBehavior is required");
        }
        if (fulfilmentMaxAttempts < 2 || fulfilmentMaxAttempts > 6) {
            throw new IllegalArgumentException("fulfilmentMaxAttempts must be between 2 and 6");
        }
        if (fulfilmentBehavior != FulfilmentBehavior.TEMPORARY_UNAVAILABLE
                && recoveryMode == RecoveryMode.AUTOMATIC) {
            throw new IllegalArgumentException("automatic recovery requires temporary unavailability");
        }
    }

    public static ExperimentPlan preset(String scenarioId) {
        return switch (scenarioId) {
            case "happy-path" -> new ExperimentPlan(1, FulfilmentBehavior.SUCCESS);
            case "duplicate-payment-result" -> new ExperimentPlan(2, FulfilmentBehavior.SUCCESS);
            case "fulfilment-unavailable" -> new ExperimentPlan(1, FulfilmentBehavior.TEMPORARY_UNAVAILABLE);
            case "fulfilment-rejected" -> new ExperimentPlan(1, FulfilmentBehavior.BUSINESS_REJECTION);
            case "out-of-order-event" -> new ExperimentPlan(1, FulfilmentBehavior.STALE_AFTER_SUCCESS);
            default -> parseCustom(scenarioId);
        };
    }

    public String scenarioId() {
        return "custom:payment-deliveries=" + paymentResultDeliveries
                + ";fulfilment=" + fulfilmentBehavior.name().toLowerCase(Locale.ROOT)
                + ";attempts=" + fulfilmentMaxAttempts
                + ";recovery=" + recoveryMode.name().toLowerCase(Locale.ROOT);
    }

    public String expectedInvariant() {
        String delivery = paymentResultDeliveries == 2
                ? "two payment-result deliveries produce one payment state change; " : "";
        return delivery + switch (fulfilmentBehavior) {
            case SUCCESS -> "workflow completes exactly once";
            case TEMPORARY_UNAVAILABLE -> "command reaches the DLQ after " + fulfilmentMaxAttempts
                    + " attempts and completes exactly once after "
                    + (recoveryMode == RecoveryMode.AUTOMATIC ? "automatic" : "guarded manual") + " recovery";
            case BUSINESS_REJECTION -> "payment is compensated and workflow ends COMPENSATED";
            case STALE_AFTER_SUCCESS -> "workflow remains COMPLETED after the delayed stale update";
        };
    }

    private static ExperimentPlan parseCustom(String scenarioId) {
        if (scenarioId == null || !scenarioId.startsWith("custom:")) {
            throw new IllegalArgumentException("Unknown scenario " + scenarioId);
        }
        int deliveries = 0;
        int attempts = 4;
        FulfilmentBehavior behavior = null;
        RecoveryMode recoveryMode = RecoveryMode.MANUAL;
        for (String part : scenarioId.substring("custom:".length()).split(";")) {
            String[] entry = part.split("=", 2);
            if (entry.length != 2) continue;
            if ("payment-deliveries".equals(entry[0])) deliveries = Integer.parseInt(entry[1]);
            if ("fulfilment".equals(entry[0])) {
                behavior = FulfilmentBehavior.valueOf(entry[1].toUpperCase(Locale.ROOT));
            }
            if ("attempts".equals(entry[0])) attempts = Integer.parseInt(entry[1]);
            if ("recovery".equals(entry[0])) {
                recoveryMode = RecoveryMode.valueOf(entry[1].toUpperCase(Locale.ROOT));
            }
        }
        return new ExperimentPlan(deliveries, behavior, attempts, recoveryMode);
    }
}
