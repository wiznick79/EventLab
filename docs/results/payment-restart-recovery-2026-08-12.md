# Payment restart recovery — 2026-08-12

This experiment verifies that accepted workflows survive a complete Payment consumer outage and resume after the participant restarts.

## Procedure

1. Verify that port 8082 belongs to the EventLab payment-service JAR.
2. Force-stop that JVM to simulate an abrupt process failure.
3. Submit five happy-path workflows through the Lab Console while Payment is offline.
4. Confirm none has advanced through payment during the outage.
5. Start Payment again with messaging enabled.
6. Poll every projected timeline and assert exact event counts.

## Result

| Measure | Observed |
| --- | ---: |
| Workflows accepted while Payment was offline | 5 |
| Workflows recovered after restart | 5 |
| `payment.authorized` events | 5 |
| `COMPLETED` terminal events | 5 |

Every accepted workflow produced exactly one payment authorization and exactly one completion after the consumer returned. No workflow required manual replay.

## What this proves

Workflow creation and its outgoing authorization command are committed before asynchronous delivery. Azure Service Bus buffers commands while Payment has no consumer, and the restarted consumer resumes the normal idempotent processing path. The result demonstrates outage recovery for queued work; it does not yet target a crash at the narrower point between broker acceptance and an outbox row being marked published.

Run the experiment with `scripts/verify-payment-restart-recovery.ps1`. The script restores Payment in a `finally` block and refuses to terminate an unrelated process on port 8082.
