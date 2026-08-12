# ADR-013: Compare bounded runtime consumer concurrency profiles

## Status

Accepted

## Context

The interactive load lab originally changed arrival rate and duplicate mix while every Service Bus processor retained one concurrent call. That proves correctness under overlapping workflows, but it does not let a visitor investigate whether adding consumer parallelism improves this pipeline.

## Decision

Each load experiment may select a consumer concurrency of 1, 4, or 8. Before launching members, the Lab Console asks Workflow, Payment, Fulfilment, and its own evidence projection to rebuild their real Service Bus processors with the selected `maxConcurrentCalls`. These bounded values are teaching profiles rather than arbitrary production tuning.

The chosen profile is persisted with the experiment and displayed beside throughput, median latency, p95 latency, and correctness evidence. Recent experiments are shown in one comparison table. At terminal assessment the processors return to concurrency 1, which remains the predictable normal-demo baseline.

The service endpoints are an internal control plane. A partial configuration is treated as unavailable and already configured remote processors are rolled back to 1; no load members are launched under a knowingly mixed profile.

For a less order-sensitive comparison, the browser runner first executes one unmeasured warm-up for each profile, waits two seconds after every experiment, then executes a three-by-three Latin-square sequence: `1→4→8`, `4→8→1`, and `8→1→4`. It runs identically in local and Azure environments. It summarizes minimum, median, maximum, and percentage spread for the three throughput observations per profile and stops if any constituent experiment fails its correctness proof. A spread above 25% is explicitly labelled `INCONCLUSIVE`, rather than declaring a performance winner. Every constituent result is durable, although the browser must remain open to coordinate the sequence.

## Consequences

- Visitors can compare identical workloads instead of interpreting one isolated number.
- The measurement includes the real broker consumers and their interactions with databases and the evidence subscriber.
- Higher concurrency is not presented as inherently better. Prefetch, local CPU, database contention, warm-up, and a small sample can dominate results.
- Rebuilding processors briefly pauses receiving, so this mechanism is appropriate for a bounded lab, not a general production autoscaler.
