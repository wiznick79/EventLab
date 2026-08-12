package pt.eventlab.contracts;

public enum FulfilmentBehavior {
    SUCCESS,
    TEMPORARY_UNAVAILABLE,
    BUSINESS_REJECTION,
    STALE_AFTER_SUCCESS,
    UNSUPPORTED_CONTRACT
}
