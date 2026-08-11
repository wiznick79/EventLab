# ADR-005: Portable instrumentation with a self-contained demo viewer

**Status:** Accepted

## Context

Grafana and Tempo provide portable skills and already form the project's local trace experience. Azure Monitor and Application Insights add useful Azure operational knowledge, but a public demonstration must not require visitors to own an Azure account or understand the Azure portal.

## Decision

Instrument services with OpenTelemetry and W3C trace context. Export traces to Tempo and expose an anonymous, read-oriented Grafana instance in both local and ephemeral Azure demonstrations. Continue exporting Azure deployments to Azure Monitor/Application Insights for platform learning and operator diagnostics.

Do not run a permanent full Grafana/Tempo/Loki stack in Azure. The small Grafana/Tempo pair exists only inside explicitly time-limited demo environments.

## Consequences

- Instrumentation remains backend-independent.
- Local development demonstrates the portable stack.
- Azure deployments still demonstrate Application Insights, platform metrics, sampling, retention, and cost controls.
- The public UI links directly to its environment's Grafana trace viewer without authentication.
- Ephemeral Tempo storage is acceptable because the entire demo environment is disposable.
