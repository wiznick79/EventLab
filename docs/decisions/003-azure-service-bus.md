# ADR-003: Azure Service Bus

**Status:** Accepted

## Context

Hotel Booking already demonstrates Kafka, transactional outbox, retry topics, DLQs, and idempotency. EventLab needs queue-oriented delivery, settlement, retries, ordering, and operator recovery, while also adding useful Azure experience.

## Decision

Use Azure Service Bus Standard in Azure and the official Service Bus emulator locally. Use command queues, a business-events topic with subscriptions, peek-lock settlement, and native DLQs.

Keep Azure SDK types inside a transport adapter. Do not build additional production broker adapters initially.

## Consequences

- The project teaches queues/topics, settlement, delivery counts, sessions, scheduled delivery, duplicate detection, DLQs, managed identity, and Azure RBAC.
- Consumers still implement inbox-based idempotency; broker duplicate detection is not treated as exactly-once processing.
- Emulator limitations require Azure smoke tests.
- Kafka remains a possible later comparison, not an MVP dependency.
