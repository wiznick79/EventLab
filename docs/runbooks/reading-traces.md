# Reading EventLab traces

The timeline states what the laboratory observed. A decision span provides independent execution evidence for claims that a consumer rejected, retried, reversed, or replayed a message. Open the trace from the relevant timeline row, select the named span, and inspect its attributes.

Every decision span includes:

- `eventlab.workflow.id` and `eventlab.message.event_id` to connect the span to the run and message;
- `eventlab.decision` for the named business outcome;
- `eventlab.state_change_applied` to say whether that delivery changed durable business state.

## Evidence by scenario

| Frontend claim | Decision span | Required evidence |
| --- | --- | --- |
| Duplicate ignored | `eventlab.workflow.inbox.decision` | `eventlab.decision=DUPLICATE_IGNORED` and `eventlab.state_change_applied=false` |
| Stale update ignored | `eventlab.workflow.version.decision` | `eventlab.decision=STALE_IGNORED`, no state change, and `eventlab.version.received` lower than `eventlab.version.current` |
| Delivery will retry | `eventlab.fulfilment.attempt.decision` | `eventlab.decision=RETRY_SCHEDULED`, with the attempt number and non-zero `eventlab.delivery.retry_delay_ms` |
| Retries exhausted | `eventlab.fulfilment.attempt.decision` | `eventlab.decision=DEAD_LETTERED` and `eventlab.delivery.dead_lettered=true` |
| Dead letter recovered | `eventlab.fulfilment.recovery.decision` | `eventlab.decision=RECOVERY_ACCEPTED`, plus different original and replay message IDs |
| Payment reversed | `eventlab.payment.compensation.decision` | `eventlab.decision=PAYMENT_COMPENSATED` and `eventlab.state_change_applied=true` |

Successful fulfilment and deterministic rejection use the same fulfilment decision span with `FULFILMENT_COMPLETED` and `FULFILMENT_REJECTED` outcomes. Duplicate compensation and fulfilment commands are also recorded as `DUPLICATE_IGNORED` with no applied state change.

Trace evidence complements the database invariants and scenario checks; it does not replace them. The trace explains which branch executed, while the persisted workflow, inbox, outbox, and participant records prove the final durable result.
