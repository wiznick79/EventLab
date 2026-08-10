package pt.eventlab.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
