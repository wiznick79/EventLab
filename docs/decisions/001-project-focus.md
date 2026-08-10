# ADR-001: Project focus

**Status:** Accepted

## Context

The project needs to be distinctive, demonstrable, and complementary to Hotel Booking. A feature-rich parcel application would consume effort without exposing the intended distributed-system lessons.

## Decision

Build EventLab as an interactive distributed-workflow failure laboratory. Use a minimal order-fulfilment domain only as experimental context. Include a feature in the MVP only when it enables, exposes, verifies, or explains a distributed-systems behavior.

## Consequences

- The UI centers on scenarios, messages, traces, invariants, and recovery.
- Accounts, catalogues, shipping calculations, and general CRUD are excluded.
- A permanent static page and recordings can demonstrate the project while Azure is offline.
