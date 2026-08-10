package pt.eventlab.messaging;

import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.eventlab.contracts.EventEnvelope;

public final class ServiceBusEnvelopeCodec {

    private final ObjectMapper objectMapper;

    ServiceBusEnvelopeCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    BinaryData encode(EventEnvelope<?> envelope) {
        try {
            return BinaryData.fromString(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize event " + envelope.eventType(), exception);
        }
    }

    public <T> EventEnvelope<T> decode(BinaryData body, Class<T> payloadType) {
        JavaType envelopeType = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, payloadType);
        try {
            return objectMapper.readValue(body.toString(), envelopeType);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot deserialize Service Bus message", exception);
        }
    }
}
