# ADR-006: Deterministic failure injection

**Status:** Accepted

## Context

A general chaos DSL and infrastructure-level fault platform would delay the first useful demonstration and make tests harder to reproduce.

## Decision

Start with curated application-level scenarios. Each failure plan is deterministic, scoped to one workflow run, records when it triggers, and declares its expected invariant and recovery path.

The MVP scenarios are duplicate delivery, fulfilment unavailability through DLQ and replay, and fulfilment rejection followed by saga compensation. Stale/out-of-order delivery follows immediately after the MVP.

## Consequences

- Demos and integration tests are repeatable.
- Concurrent visitors are isolated.
- Container termination and network faults remain optional later experiments.
- Fault-control APIs remain separate from normal business APIs.
