package pt.eventlab.console.api;

public record BrokerPressureResponse(
        boolean available,
        String status,
        StagePressure paymentCommands,
        StagePressure workflowEvents,
        StagePressure fulfilmentCommands,
        StagePressure evidenceEvents) {

    public record StagePressure(int current, int peak) {
    }

    static BrokerPressureResponse unavailable(String status) {
        StagePressure empty = new StagePressure(0, 0);
        return new BrokerPressureResponse(false, status, empty, empty, empty, empty);
    }
}
