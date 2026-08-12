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
| Unsupported contract rejected | `eventlab.fulfilment.contract.decision` | `eventlab.decision=UNSUPPORTED_CONTRACT_REJECTED`, `eventlab.state_change_applied=false`, and the delivery attempt attribute increments |
| Poison message quarantined | `eventlab.fulfilment.contract.decision` | `eventlab.decision=UNSUPPORTED_CONTRACT_DEAD_LETTERED`, `eventlab.delivery.dead_lettered=true`, and no `FULFILMENT_COMPLETED` event exists |
| Dead letter recovered | `eventlab.fulfilment.recovery.decision` | `eventlab.decision=RECOVERY_ACCEPTED`, plus different original and replay message IDs |
| Payment reversed | `eventlab.payment.compensation.decision` | `eventlab.decision=PAYMENT_COMPENSATED` and `eventlab.state_change_applied=true` |

Successful fulfilment and deterministic rejection use the same fulfilment decision span with `FULFILMENT_COMPLETED` and `FULFILMENT_REJECTED` outcomes. Duplicate compensation and fulfilment commands are also recorded as `DUPLICATE_IGNORED` with no applied state change.

Trace evidence complements the database invariants and scenario checks; it does not replace them. The trace explains which branch executed, while the persisted workflow, inbox, outbox, and participant records prove the final durable result.

An offline consumer produces no attempt span and leaves the broker message available for later delivery. The poison experiment is intentionally different: three contract-decision spans prove the consumer received and evaluated the payload, while the final span and projected `POISON_DEAD_LETTERED` event prove explicit quarantine.

## Native DLQ proof

For a currently quarantined Fulfilment command, `GET /api/v1/runs/{workflowId}/dead-letter` peeks the native Service Bus dead-letter subqueue. The Run Inspector renders the same response. For the poison experiment, verify:

- queue `fulfilment-commands/$deadletterqueue`;
- reason `UnsupportedContractVersion`;
- declared schema version `99`;
- replay policy `Replay blocked`.

This inspection is non-destructive and intentionally excludes the message body. A `NOT_FOUND` response can also mean a transient-failure entry was already recovered and completed; durable timeline and trace evidence remain available after replay.

## Operations dashboard

Grafana provisions **EventLab Operations** at `/d/eventlab-operations/eventlab-operations`. The live UI's **Operations** link opens it for the current hour. Its panels provide four starting points:

- all business-decision spans;
- duplicate and stale deliveries that protected an invariant;
- retry, dead-letter, recovery, and compensation decisions;
- recent Service Bus processing spans.

Select any result to open its trace. The dashboard is intentionally trace-first: EventLab's disposable Tempo setup does not run a metrics generator or a separate Prometheus stack merely to manufacture charts. Azure Monitor remains the source for Container Apps platform CPU, memory, replicas, restarts, and ingress metrics.
