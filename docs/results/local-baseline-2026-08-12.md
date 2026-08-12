# Local concurrency baseline — 2026-08-12

This result records the first repeatable Milestone 8 baseline. It is evidence that the test and invariants execute successfully on a development environment, not a production sizing claim.

## Workload

- 10 happy-path workflows, shared across 5 virtual users.
- 3 duplicate-delivery workflows, executed sequentially alongside the happy-path workload.
- Local Spring Boot processes, PostgreSQL and the Azure Service Bus emulator through Docker Desktop.
- k6 2.2.0 executed from the pinned container in `docker-compose.yml`.

## Result

| Measure | Observed | Threshold |
| --- | ---: | ---: |
| HTTP failure rate | 0% (89 requests) | < 1% |
| Start-run latency p95 | 15.59 ms | < 1,000 ms |
| Happy-path completion p95 | 1,551 ms | < 15,000 ms |
| Happy-path invariant pass rate | 100% (10/10) | 100% |
| Duplicate invariant pass rate | 100% (3/3) | 100% |

Every happy-path workflow reached exactly one `COMPLETED` event. Every duplicate workload exposed two `payment.authorized` deliveries, one duplicate marker, one `DUPLICATE_IGNORED` decision, and exactly one completion.

## Reproduce

Follow [the performance test runbook](../runbooks/performance-testing.md). The ignored machine-readable summary is regenerated at `performance/results/baseline-summary.json`.
