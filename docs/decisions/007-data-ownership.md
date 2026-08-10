# ADR-007: PostgreSQL topology and ownership

**Status:** Accepted

## Context

Separate managed database servers would demonstrate physical isolation but consume a disproportionate part of the student Azure credit.

## Decision

Use one PostgreSQL server/container with separate service-owned databases or schemas and credentials. Services never access another service's tables. Each state-changing service owns inbox and outbox persistence. Flyway owns migrations.

## Consequences

- Logical ownership and transactional boundaries remain explicit.
- Azure cost and local resource use stay manageable.
- Shared server failure remains an acknowledged infrastructure coupling.
- Separate servers may be introduced later only for a concrete isolation experiment.
