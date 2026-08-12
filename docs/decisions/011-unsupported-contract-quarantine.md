# ADR-011: Reject unsupported contracts explicitly and quarantine them after a bounded budget

**Status:** Accepted  
**Date:** 2026-08-12

## Context

EventLab already demonstrates transient dependency failures, where retrying the same valid command may eventually succeed. An unsupported schema is different: repeating it cannot make the consumer understand it, but the system still needs visible, bounded handling and operator evidence.

## Decision

Fulfilment validates the envelope schema version before applying the command. For the teaching experiment, version 99 is rejected three times. The first two deliveries are abandoned and the third is explicitly dead-lettered with stable broker reason `UnsupportedContractVersion`. Every rejection publishes evidence, but none completes Fulfilment. The poison DLQ event immediately moves the persisted saga to `FAILED_REQUIRES_INTERVENTION`.

The timeline uses `POISON_DEAD_LETTERED` rather than the transient-failure `DEAD_LETTERED` state so trace links and evidence checks cannot confuse incompatible data with a recoverable provider outage.

## Consequences

- The demo distinguishes attempted-and-rejected messages from messages retained while a consumer is offline.
- Automatic or manual dependency recovery is not offered for an incompatible contract.
- The unsupported payload remains available in the native DLQ for inspection or a future version-aware replay tool.
- Schema negotiation and upcasting remain outside this milestone; production consumers would normally reject known-incompatible versions without retries unless deployment ordering could make a retry useful.
