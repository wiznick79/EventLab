# Outbox acknowledgement-window recovery — 2026-08-12

This experiment targets the ambiguity that makes transactional outboxes necessary: the broker can accept a message even when the producer fails before recording that fact locally.

## Procedure

1. Start a temporary Payment process with the one-shot `eventlab.messaging.fail-once-after-send` fault enabled.
2. Start one ordinary happy-path workflow.
3. Allow Payment to commit its authorization and outbox event.
4. Send that event to Service Bus, then inject a failure before `markPublished` updates the outbox row.
5. Let the scheduled dispatcher retry the still-pending logical event.
6. Inspect the projected timeline and its logical event IDs.
7. Replace the temporary process with an ordinary Payment process.

## Result

| Measure | Observed |
| --- | ---: |
| Logical payment event IDs | 1 |
| Broker deliveries | 2 |
| Duplicate delivery markers | 1 |
| `DUPLICATE_IGNORED` decisions | 1 |
| Workflow completions | 1 |

The two broker deliveries used logical event ID `4e9e9918-2724-4d3c-8579-7ee8d8891adc`. The Workflow inbox accepted the first, rejected the retry, and allowed one terminal transition.

## What this proves

The system does not assume exactly-once transport. An acknowledgement ambiguity leaves the outbox row eligible for retry, which can produce an actual duplicate. Stable logical identity plus the transactional consumer inbox converts that at-least-once delivery into one state change. The Lab Console retains both deliveries, making the safety mechanism visible rather than silently hiding the retry.

The injected fault is disabled by default and consumed at most once in the temporary Payment process. `scripts/verify-outbox-acknowledgement-window.ps1` restores the normal Payment process in its cleanup path.
