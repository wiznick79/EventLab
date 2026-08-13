package pt.eventlab.console.api;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import pt.eventlab.console.messaging.LabConsoleMessagingProperties;

@Service
class BrokerPressureService {

    private final LabConsoleMessagingProperties properties;
    private final ServiceBusAdministrationClient client;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Snapshot> logicalSnapshots = new ConcurrentHashMap<>();
    private volatile boolean nativeCountersSupported;

    BrokerPressureService(LabConsoleMessagingProperties properties) {
        this.properties = properties;
        this.client = properties.enabled() ? createClient(properties) : null;
        this.nativeCountersSupported = client != null;
    }

    void begin(UUID experimentId) {
        nativeCountersSupported = client != null;
        snapshots.put(experimentId, Snapshot.empty());
        logicalSnapshots.put(experimentId, Snapshot.empty());
    }

    void recordLogical(UUID experimentId, int payment, int workflow, int fulfilment, int evidence) {
        logicalSnapshots.compute(experimentId, (id, previous) ->
                (previous == null ? Snapshot.empty() : previous)
                        .observed(payment, workflow, fulfilment, evidence));
    }

    void sample(UUID experimentId) {
        if (!nativeCountersSupported) return;
        try {
            int payment = client.getQueueRuntimeProperties(properties.paymentCommandsQueue())
                    .getActiveMessageCount();
            int workflow = client.getSubscriptionRuntimeProperties(properties.businessEventsTopic(),
                    properties.workflowEventsSubscription()).getActiveMessageCount();
            int fulfilment = client.getQueueRuntimeProperties(properties.fulfilmentCommandsQueue())
                    .getActiveMessageCount();
            int evidence = client.getSubscriptionRuntimeProperties(properties.businessEventsTopic(),
                    properties.labConsoleEventsSubscription()).getActiveMessageCount();
            snapshots.compute(experimentId, (id, previous) ->
                    (previous == null ? Snapshot.empty() : previous)
                            .observed(payment, workflow, fulfilment, evidence));
        } catch (RuntimeException exception) {
            nativeCountersSupported = false;
            snapshots.compute(experimentId, (id, previous) ->
                    (previous == null ? Snapshot.empty() : previous).markedUnavailable());
        }
    }

    BrokerPressureResponse inspect(UUID experimentId) {
        if (client == null) {
            return BrokerPressureResponse.unavailable("Messaging management is disabled");
        }
        Snapshot snapshot = snapshots.get(experimentId);
        if (snapshot != null && snapshot.available()) {
            return snapshot.response("NATIVE_SERVICE_BUS_COUNTS");
        }
        Snapshot logical = logicalSnapshots.get(experimentId);
        if (logical != null && logical.available()) {
            return logical.response("LOGICAL_EXPERIMENT_BACKLOG");
        }
        return BrokerPressureResponse.unavailable("Pressure evidence is temporarily unavailable");
    }

    private ServiceBusAdministrationClient createClient(LabConsoleMessagingProperties source) {
        ServiceBusAdministrationClientBuilder builder = new ServiceBusAdministrationClientBuilder();
        if (source.fullyQualifiedNamespace() != null && !source.fullyQualifiedNamespace().isBlank()) {
            return builder.credential(source.fullyQualifiedNamespace(),
                    new DefaultAzureCredentialBuilder().build()).buildClient();
        }
        return builder.connectionString(source.connectionString()).buildClient();
    }

    private record Snapshot(boolean available,
                            int paymentCurrent, int paymentPeak,
                            int workflowCurrent, int workflowPeak,
                            int fulfilmentCurrent, int fulfilmentPeak,
                            int evidenceCurrent, int evidencePeak) {

        static Snapshot empty() {
            return new Snapshot(false, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        Snapshot observed(int payment, int workflow, int fulfilment, int evidence) {
            return new Snapshot(true,
                    payment, Math.max(paymentPeak, payment),
                    workflow, Math.max(workflowPeak, workflow),
                    fulfilment, Math.max(fulfilmentPeak, fulfilment),
                    evidence, Math.max(evidencePeak, evidence));
        }

        Snapshot markedUnavailable() {
            return new Snapshot(false, paymentCurrent, paymentPeak, workflowCurrent, workflowPeak,
                    fulfilmentCurrent, fulfilmentPeak, evidenceCurrent, evidencePeak);
        }

        BrokerPressureResponse response(String status) {
            return new BrokerPressureResponse(true, status,
                    new BrokerPressureResponse.StagePressure(paymentCurrent, paymentPeak),
                    new BrokerPressureResponse.StagePressure(workflowCurrent, workflowPeak),
                    new BrokerPressureResponse.StagePressure(fulfilmentCurrent, fulfilmentPeak),
                    new BrokerPressureResponse.StagePressure(evidenceCurrent, evidencePeak));
        }
    }
}
