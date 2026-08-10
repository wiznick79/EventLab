# ADR-004: No consolidated runtime

**Status:** Accepted

## Context

A consolidated Spring application with in-process events could be cheaper to host continuously, but it would add a second packaging topology and messaging model whose behavior differs from durable distributed messaging.

## Decision

Do not create a Spring Modulith or modular-monolith runtime. Demonstrate the actual distributed system on demand and maintain a static portfolio page with screenshots, diagrams, and recorded scenarios.

## Consequences

- No dual-runtime configuration or test matrix.
- Documentation does not risk presenting a modular monolith as microservices.
- Azure application infrastructure can remain ephemeral.
- Messaging ports exist for clean boundaries and testing, not to promise interchangeable transports.
