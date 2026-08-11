# ADR-005: Portable instrumentation with a self-contained demo viewer

**Status:** Accepted

## Context

Grafana and Tempo provide portable skills and already form the project's local trace experience. Azure Monitor and Application Insights add useful Azure operational knowledge, but a public demonstration must not require visitors to own an Azure account or understand the Azure portal.

## Decision

Instrument services with Micrometer/OpenTelemetry and W3C trace context. Export application traces to Tempo and expose an anonymous, read-oriented Grafana instance in both local and ephemeral Azure demonstrations. Use Azure Monitor for Container Apps platform logs and metrics; do not attach the Application Insights Java agent to demo revisions because it competes with the portable trace pipeline.

Do not run a permanent full Grafana/Tempo/Loki stack in Azure. The small Grafana/Tempo pair exists only inside explicitly time-limited demo environments.

## Consequences

- Instrumentation remains backend-independent.
- Local development demonstrates the portable stack.
- Azure deployments still demonstrate Azure Monitor platform logs, metrics, retention, and cost controls. The Application Insights resource remains available for a later dedicated native-instrumentation exercise.
- The public UI links directly to its environment's Grafana trace viewer without authentication.
- Ephemeral Tempo storage is acceptable because the entire demo environment is disposable.
