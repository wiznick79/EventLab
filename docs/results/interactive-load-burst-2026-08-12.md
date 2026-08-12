# Interactive load burst — 2026-08-12

## Result

`PROVED`

## Configuration

- Environment: local development machine
- Traffic: burst
- Requested workflows: 10
- Duplicate payment-result mix: 20%
- Execution path: Lab Console → Workflow → Azure Service Bus emulator → Payment / Fulfilment → evidence projection

## Observed aggregate evidence

| Measurement | Result |
|---|---:|
| Accepted workflows | 10 / 10 |
| Terminal workflows | 10 / 10 |
| Backend evidence proved | 10 / 10 |
| Invariant violations | 0 |
| Launch failures | 0 |
| Backlog after completion | 0 |
| Maximum workflows in flight | 10 |
| Duplicate deliveries observed | 2 |
| Throughput | 6.05 workflows / second |
| Median terminal latency | 1.165 seconds |
| p95 terminal latency | 1.651 seconds |

Load experiment ID: `721f8196-b49e-472b-ba36-7072c6076ffe`.

## Interpretation

The maximum in-flight count proves that the burst overlapped all ten workflows rather than running a fast sequential loop. The duplicate count matches the selected 20% mix. More importantly, every accepted workflow reached its expected terminal outcome, all ten plan-aware backend evidence reports passed, and the accepted-work backlog drained to zero.

This is a bounded development-machine demonstration, not a production capacity claim. Repeat it through the interactive Load & Concurrency Lab for explanatory demos and use the committed k6 baseline for scripted regression comparisons.
