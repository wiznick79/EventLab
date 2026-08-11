package pt.eventlab.messaging;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;

public final class ServiceBusClients {

    private ServiceBusClients() {
    }

    public static ServiceBusClientBuilder create(String connectionString, String fullyQualifiedNamespace) {
        if (fullyQualifiedNamespace != null && !fullyQualifiedNamespace.isBlank()) {
            return new ServiceBusClientBuilder().credential(
                    fullyQualifiedNamespace,
                    new DefaultAzureCredentialBuilder().build());
        }
        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalArgumentException(
                    "Either a Service Bus connection string or fully qualified namespace is required");
        }
        return new ServiceBusClientBuilder().connectionString(connectionString);
    }
}
