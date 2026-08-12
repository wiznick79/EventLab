# ADR-012: Run bounded load experiments through ordinary workflow paths

## Status

Accepted

## Context

EventLab described distributed systems "under pressure," but its interactive console previously launched one workflow at a time. The k6 baseline proved concurrent behavior reproducibly, yet a visitor could not create or inspect that pressure from the live lab. A useful demonstration must distinguish throughput from correctness: accepting requests quickly is not success if messages are lost, duplicated into extra state changes, or never drain from the backlog.

## Decision

The Lab Console will coordinate bounded groups of otherwise ordinary experiment runs. It will launch them concurrently for burst traffic or at a configured interval for steady traffic. A configurable subset receives duplicate payment-result delivery, while all members retain their own immutable plan, durable run record, event timeline, evidence assessment, and trace links.

The aggregate report derives its claims from those individual backend evidence reports. It exposes accepted and terminal work, current backlog, maximum in-flight workflows, throughput, median and p95 terminal latency, duplicate deliveries, launch failures, and invariant violations. An experiment is `PROVED` only when every accepted workflow is terminal, every individual report is proved, and no launch failed.

Limits are part of the product contract: local development permits at most 100 workflows; a public deployment permits at most 25; and only one load experiment may run at a time. The coordinator persists the group and member workflow identifiers so results remain inspectable after browser refreshes or a Lab Console restart.

## Consequences

- The phrase "under pressure" now has an interactive, measurable meaning.
- Load follows the same API, broker, consumers, databases, and evidence pipeline as a single scenario; no frontend simulation is involved.
- Results demonstrate correctness on the current environment, not a production capacity guarantee.
- Safe public limits reduce cost and denial-of-service risk, but this is not a general-purpose benchmarking platform.
- k6 remains the repeatable regression tool; the interactive lab is the explanatory portfolio experience.
