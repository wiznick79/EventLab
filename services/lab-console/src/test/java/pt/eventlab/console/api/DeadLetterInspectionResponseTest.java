package pt.eventlab.console.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeadLetterInspectionResponseTest {

    @Test
    void exposesBrokerMetadataWithoutOfferingPoisonReplay() {
        UUID workflowId = UUID.randomUUID();
        ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);
        var properties = new HashMap<String, Object>();
        properties.put("schemaVersion", 99);
        when(message.getApplicationProperties()).thenReturn(properties);
        when(message.getMessageId()).thenReturn("message-17");
        when(message.getSubject()).thenReturn("fulfilment.requested");
        when(message.getDeadLetterReason()).thenReturn("UnsupportedContractVersion");
        when(message.getDeadLetterErrorDescription()).thenReturn(
                "Received schema version 99; supported version is 1");
        when(message.getDeliveryCount()).thenReturn(3L);
        when(message.getSequenceNumber()).thenReturn(42L);
        when(message.getEnqueuedTime()).thenReturn(
                OffsetDateTime.of(2026, 8, 12, 12, 0, 0, 0, ZoneOffset.UTC));

        DeadLetterInspectionResponse response = DeadLetterInspectionResponse.found(
                workflowId, "fulfilment-commands", message);

        assertEquals("FOUND", response.status());
        assertEquals("fulfilment-commands/$deadletterqueue", response.queue());
        assertEquals("UnsupportedContractVersion", response.deadLetterReason());
        assertEquals(99, response.schemaVersion());
        assertEquals(3, response.deliveryCount());
        assertFalse(response.replayAllowed());
    }
}
