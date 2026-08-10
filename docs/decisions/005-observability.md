# ADR-005: Portable instrumentation and Azure-native operations

**Status:** Accepted

## Context

Grafana, Tempo, and Prometheus provide portable skills. Azure Monitor and Application Insights add useful Azure operational knowledge. Operating both complete stacks in Azure would increase cost and noise.

## Decision

Instrument services with OpenTelemetry and W3C trace context. Locally export to an OpenTelemetry Collector, Tempo, Prometheus, and Grafana; add Loki after tracing works. In Azure, export application telemetry to Azure Monitor/Application Insights and use native platform metrics.

Do not run a permanent full Grafana/Tempo/Loki stack in Azure.

## Consequences

- Instrumentation remains backend-independent.
- Local development demonstrates the portable stack.
- Azure deployments demonstrate Application Insights, platform metrics, sampling, retention, and cost controls.
- The UI can link to Grafana locally and Azure trace views in deployed environments.
