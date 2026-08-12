# Incremental roadmap

Every milestone must finish with a behavior that can be demonstrated through the UI or a short recording.

## Milestone 0: repository and contracts — completed 2026-08-10

- Establish the Maven modules, frontend, Compose skeleton, coding conventions, and decision records.
- Define workflow states, event envelope, identifiers, versioning, and scenario contracts.
- Add baseline CI verification.

**Demonstration:** architecture page and executable skeleton with health endpoints.

Implemented with four independently runnable Spring Boot service skeletons, a shared contracts module, a responsive React scenario shell, PostgreSQL Compose configuration, container build foundations, and backend/frontend/Compose CI verification.

## Milestone 1: observable walking skeleton — completed 2026-08-10

- Implement Workflow, one participant, Lab Console, and the UI timeline.
- Run PostgreSQL and the Service Bus emulator locally.
- Propagate OpenTelemetry context end to end.
- Display a successful workflow and link it to a trace.

**Demonstration:** start a workflow and watch its messages and spans reach completion.

Implemented with PostgreSQL-owned service state, the official local Azure Service Bus emulator, a payment command queue, a business-event topic with independent Workflow and Lab Console subscriptions, W3C trace-context propagation, a Tempo/Grafana trace backend, and a React console that streams the projected timeline over SSE. The verified baseline produces `workflow.started`, `payment.authorized`, and `workflow.completed` under one trace.

## Milestone 2: reliable delivery — completed 2026-08-10

- Add transactional outbox dispatch.
- Add inbox/idempotent consumption.
- Implement the deterministic duplicate-payment-result scenario.
- Add multi-service integration tests.

**Demonstration:** deliver one logical result multiple times while producing one state change.

Implemented with transactional Workflow and Payment outboxes, scheduled Service Bus dispatch, transactional consumer inboxes, separate delivery-row and logical-event identity in the Lab Console, persistence-level integration tests, and a repeatable multi-service emulator check. The executable duplicate-payment scenario records two deliveries while proving one payment row and one workflow completion.

## Milestone 3: retry, DLQ, and recovery — completed 2026-08-11

- Add deterministic temporary failure.
- Expose delivery attempts and exponential backoff.
- Add retry exhaustion, DLQ inspection, replay audit, and guarded resubmission.

**Demonstration:** take Fulfilment offline logically, reach the DLQ, recover it, and complete the workflow.

Implemented with a persisted Fulfilment participant, deterministic per-workflow availability, four visible delivery attempts with 250/500/1000 ms backoff, explicit dead-letter settlement, workflow-scoped DLQ lookup, dependency recovery through the Fulfilment API, and replay through the original command queue. The consumer inbox is claimed only after successful handling, so retries remain possible while a replayed logical command still completes at most once.

## Milestone 4: saga compensation — completed 2026-08-11

- Complete Payment and Fulfilment boundaries.
- Persist orchestration state and timeouts.
- Add fulfilment rejection and payment compensation.
- Display forward and compensating paths and final invariants.

**Demonstration:** payment authorization followed by fulfilment rejection and successful compensation.

This is the MVP boundary.

Implemented with a deterministic `fulfilment-rejected` outcome, persisted saga step deadlines, an orchestration timeout monitor, an idempotent payment-compensation command handler, and distinct compensated/intervention terminal events. The Lab Console displays the forward and reversing paths, while the repeatable emulator scenario proves that an authorized payment is compensated and the workflow ends in `COMPENSATED`, never `COMPLETED`.

## Milestone 5: ordering and concurrency — completed 2026-08-11

- Add optimistic concurrency and workflow versions.
- Demonstrate stale or out-of-order delivery.
- Compare unordered processing with Service Bus sessions where useful.

**Demonstration:** a delayed stale event is visible but cannot regress workflow state.

Implemented with explicit Fulfilment aggregate versions, a persisted highest-applied version in Workflow, the existing JPA optimistic lock for concurrent database writers, and a deterministic scheduler that publishes a version-1 rejection after version 2 has completed. The Lab Console shows both the late delivery and `STALE_IGNORED`; the emulator check proves the persisted workflow remains `COMPLETED`. Service Bus sessions are documented as a selective serialization option rather than enabled globally, because they reduce reordering but do not replace version checks or idempotency.

## Milestone 6: ephemeral Azure deployment — completed 2026-08-12

- Create persistent bootstrap and disposable application Terraform roots.
- Configure Entra/GitHub OIDC and managed identities.
- Build immutable GHCR images in CI.
- Add `plan`, `deploy`, `destroy`, Flyway, seed, smoke-test, and TTL cleanup workflows.
- Export portable application traces to the environment's Tempo instance while retaining Azure Monitor for platform telemetry.

**Demonstration:** create an environment with a chosen lifetime, run a scenario in Azure, and destroy it completely.

Implemented with separate persistent-bootstrap and disposable-application Terraform roots, GitHub-to-Azure OIDC, managed identities for Service Bus and PostgreSQL access, immutable public GHCR images, reviewed plan/deploy/destroy workflows, scheduled TTL cleanup, and startup Flyway migrations. The time-limited environment exposes EventLab plus an anonymous Grafana/Tempo trace viewer. Deployment smoke tests prove both a completed workflow and that the exact trace attached to an ignored duplicate can be retrieved through public Grafana without an Azure account.

## Milestone 7: portfolio polish — completed 2026-08-12

- Add explanatory copy, diagrams, and trace links.
- Make every frontend invariant claim independently verifiable through explicit business-decision spans and attributes.
- Create Azure and local dashboards, runbooks, and cost controls.
- Record the primary scenarios.
- Publish a permanent static portfolio page for times when Azure is offline.

**Demonstration:** self-contained public project tour and recorded failure/recovery session.

Completed with trace-verifiable timeline claims, shared decision instrumentation, a provisioned trace-first Grafana operations dashboard, and a [permanent backend-independent project tour](https://wiznick79.github.io/EventLab/). The tour explains topology, reliability mechanisms, outcomes, and the three-layer proof model, and embeds reproducible recordings of duplicate suppression, dead-letter recovery, and saga compensation.

## Optional later experiments

- Container termination or network-delay injection.
- Split Inventory and Shipment if a scenario requires it.
- Kafka comparison adapter if it produces a meaningful broker-semantics comparison.
- Local `kind` or `k3d` only for a concrete orchestration experiment.

## Milestone 8: resilience and performance validation — completed 2026-08-12

- Establish a repeatable k6 baseline for concurrent happy-path and duplicate-delivery workflows.
- Measure API latency and asynchronous completion time while asserting durable business invariants.
- Terminate and restart a service with workflows in flight, then prove outbox/inbox recovery.
- Publish a concise, reproducible result rather than claiming production capacity from a development machine.

**Demonstration:** run concurrent workflows, interrupt a participant, restore it, and show that every accepted workflow reaches one correct terminal outcome.

Completed with an invariant-aware k6 concurrency baseline, a deterministic Payment outage experiment, and the narrow outbox acknowledgement window. Five workflows accepted with no Payment consumer all resumed after restart. A separate one-shot post-send failure produced two broker deliveries for one logical payment event; the inbox exposed one duplicate decision and the workflow completed once. The permanent tour presents the measurements beside their invariants, method links, and explicit development-machine boundary.
