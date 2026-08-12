# Local consumer-concurrency comparison — 2026-08-13

This development-machine run validates the comparison mechanism; it is not a production sizing claim.

## Method

- 50 workflows per run
- burst arrival
- 20% duplicate payment-result deliveries
- identical application and local Docker infrastructure
- real Service Bus processor concurrency set to 1, 4, then 8
- aggregate result accepted only when every member evidence report passed

| Consumer concurrency | Result | Proved | Violations | Throughput | Median latency | p95 latency |
|---:|---|---:|---:|---:|---:|---:|
| 1 | PROVED | 50/50 | 0 | 7.26/s | 5.002s | 6.351s |
| 4 | PROVED | 50/50 | 0 | 3.34/s | 13.823s | 14.443s |
| 8 | PROVED | 50/50 | 0 | 3.32/s | 14.323s | 14.628s |

## Interpretation

All three profiles preserved the distributed invariants and ignored exactly ten duplicate deliveries. On this small local environment, extra consumer parallelism was slower. That is a useful result rather than a failed benchmark: concurrency increased contention and did not remove the dominant bottleneck. Repeated runs and broker/database telemetry would be required before attributing the difference to one component or choosing a production setting.
