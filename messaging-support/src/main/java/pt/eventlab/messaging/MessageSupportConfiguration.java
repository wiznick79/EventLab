package pt.eventlab.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class MessageSupportConfiguration {

    @Bean
    ServiceBusEnvelopeCodec serviceBusEnvelopeCodec(ObjectMapper objectMapper) {
        return new ServiceBusEnvelopeCodec(objectMapper);
    }

    @Bean
    ServiceBusMessageFactory serviceBusMessageFactory(
            ServiceBusEnvelopeCodec codec,
            OpenTelemetry openTelemetry,
            Tracer tracer) {
        return new ServiceBusMessageFactory(codec, openTelemetry, tracer);
    }

    @Bean
    ServiceBusTraceContext serviceBusTraceContext(OpenTelemetry openTelemetry) {
        return new ServiceBusTraceContext(openTelemetry);
    }

    @Bean
    OutboxStore outboxStore(
            JdbcTemplate jdbcTemplate,
            ServiceBusEnvelopeCodec codec,
            ObjectMapper objectMapper,
            OpenTelemetry openTelemetry,
            Tracer tracer) {
        return new OutboxStore(jdbcTemplate, codec, objectMapper, openTelemetry, tracer);
    }

    @Bean
    InboxStore inboxStore(JdbcTemplate jdbcTemplate) {
        return new InboxStore(jdbcTemplate);
    }
}
