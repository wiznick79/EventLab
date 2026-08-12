# Architecture decision records

These records capture accepted project-level decisions. A decision can be superseded by a later ADR, but its original reasoning remains in the repository.

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](001-project-focus.md) | Build a distributed-systems failure laboratory, not a parcel CRUD system | Accepted |
| [ADR-002](002-service-boundaries.md) | Start with Workflow, Payment, Fulfilment, and Lab Console | Accepted |
| [ADR-003](003-azure-service-bus.md) | Use Azure Service Bus Standard and its local emulator | Accepted |
| [ADR-004](004-no-consolidated-runtime.md) | Do not build a consolidated Spring Modulith runtime | Accepted |
| [ADR-005](005-observability.md) | Use OpenTelemetry with portable local and Azure-native backends | Accepted |
| [ADR-006](006-failure-injection.md) | Begin with curated deterministic application-level scenarios | Accepted |
| [ADR-007](007-data-ownership.md) | Share PostgreSQL infrastructure while preserving service ownership | Accepted |
| [ADR-008](008-ephemeral-azure.md) | Separate persistent bootstrap from disposable Azure infrastructure | Accepted |
| [ADR-009](009-technology-baseline.md) | Pin the initial Java and web framework baseline | Accepted |
| [ADR-010](010-scenario-builder.md) | Compose bounded immutable experiment plans | Accepted |
| [ADR-011](011-unsupported-contract-quarantine.md) | Reject unsupported contracts explicitly and quarantine them after a bounded budget | Accepted |
