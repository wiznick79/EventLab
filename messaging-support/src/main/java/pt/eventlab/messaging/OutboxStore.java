package pt.eventlab.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.eventlab.contracts.EventEnvelope;

public final class OutboxStore {

    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() { };
    private static final int MAX_ATTEMPTS = 10;

    private final ServiceBusEnvelopeCodec codec;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    OutboxStore(
            JdbcTemplate jdbcTemplate,
            ServiceBusEnvelopeCodec codec,
            ObjectMapper objectMapper,
            OpenTelemetry openTelemetry,
            Tracer tracer) {
        this.jdbcTemplate = jdbcTemplate;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
    }

    public void enqueue(OutboxDestination type, String destination, EventEnvelope<?> envelope) {
        enqueue(type, destination, envelope, 1);
    }

    public void enqueue(
            OutboxDestination type,
            String destination,
            EventEnvelope<?> envelope,
            int copies) {
        if (copies < 1) {
            throw new IllegalArgumentException("copies must be positive");
        }
        String payload = codec.encodeToString(envelope);
        String headers = headersJson();
        for (int copy = 0; copy < copies; copy++) {
            jdbcTemplate.update("""
                    insert into outbox_messages (
                        outbox_id, event_id, destination_type, destination_name,
                        payload_json, trace_headers_json, created_at, attempts)
                    values (?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    UUID.randomUUID(), envelope.eventId(), type.name(), destination,
                    payload, headers, Timestamp.from(Instant.now()));
        }
    }

    public List<OutboxMessage> lockNextBatch(int batchSize) {
        return jdbcTemplate.query("""
                select outbox_id, event_id, destination_type, destination_name,
                       payload_json, trace_headers_json, attempts
                from outbox_messages
                where published_at is null
                  and quarantined_at is null
                  and (next_attempt_at is null or next_attempt_at <= ?)
                order by created_at
                limit ?
                for update skip locked
                """, this::map, Timestamp.from(Instant.now()), batchSize);
    }

    public void markPublished(UUID outboxId) {
        jdbcTemplate.update("""
                update outbox_messages
                set published_at = ?, attempts = attempts + 1, last_error = null
                where outbox_id = ?
                """, Timestamp.from(Instant.now()), outboxId);
    }

    public boolean markFailed(OutboxMessage message, String error) {
        int attempts = message.attempts() + 1;
        boolean quarantined = attempts >= MAX_ATTEMPTS;
        long delaySeconds = Math.min(60, 1L << Math.min(attempts - 1, 6));
        Instant now = Instant.now();
        jdbcTemplate.update("""
                update outbox_messages
                set attempts = ?, last_error = ?, next_attempt_at = ?, quarantined_at = ?
                where outbox_id = ?
                """, attempts, error.substring(0, Math.min(error.length(), 1000)),
                Timestamp.from(now.plus(Duration.ofSeconds(delaySeconds))),
                quarantined ? Timestamp.from(now) : null, message.outboxId());
        return quarantined;
    }

    private OutboxMessage map(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            return new OutboxMessage(
                    resultSet.getObject("outbox_id", UUID.class),
                    resultSet.getObject("event_id", UUID.class),
                    OutboxDestination.valueOf(resultSet.getString("destination_type")),
                    resultSet.getString("destination_name"),
                    resultSet.getString("payload_json"),
                    objectMapper.readValue(resultSet.getString("trace_headers_json"), HEADERS_TYPE),
                    resultSet.getInt("attempts"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid trace headers in outbox", exception);
        }
    }

    private String headersJson() {
        Map<String, String> headers = new HashMap<>();
        openTelemetry.getPropagators().getTextMapPropagator().inject(
                Context.current(), headers, Map::put);
        Span span = tracer.currentSpan();
        if (span != null) {
            headers.put("traceId", span.context().traceId());
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize outbox trace headers", exception);
        }
    }
}
