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

## Milestone 9: interactive Scenario Builder — completed 2026-08-12

- Compose duplicate delivery with success, unavailability, business rejection, or stale update behavior.
- Validate and identify each immutable plan at the backend boundary.
- Execute plans through the real services and Service Bus rather than simulating outcomes in the frontend.
- Declare the expected invariant before execution and prove it against the observed timeline.
- Preserve guarded DLQ recovery for composed unavailable plans.

**Demonstration:** select duplicate payment-result delivery plus Fulfilment rejection, then observe two deliveries, one ignored duplicate, one payment compensation, terminal `COMPENSATED`, and no `COMPLETED` state.

Implemented with a transport-neutral `ExperimentPlan`, per-participant plan interpretation, a persisted plan UUID, responsive React controls, expected-versus-observed evidence, and a reusable cross-service verification script.

## Milestone 10: durable Run Inspector — completed 2026-08-12

- Persist the immutable submitted plan in the Lab Console evidence projection.
- List recent experiments with their latest observed state.
- Restore plan, invariant, timeline, and trace links through `/runs/{workflowId}` after refresh.
- Copy a stable evidence URL and compare two runs side by side.
- Keep Workflow, Payment, and Fulfilment business state in their owning services.

**Demonstration:** execute two different plans, compare their delivery behavior and outcomes, refresh either run's URL, and recover the same persisted evidence without rerunning it.

Implemented with a Flyway-managed experiment registry, recent/detail APIs, SPA history routing, an accessible Run Inspector, evidence-link copying, and a two-run comparison view.

## Milestone 11: configurable retry and recovery policies — completed 2026-08-12

- Select a bounded Fulfilment retry budget from two through six attempts.
- Choose guarded manual replay or backend-owned automatic recovery.
- Preserve default four-attempt manual behavior for existing presets and older plan JSON.
- Persist policy choices with the Run Inspector evidence record.
- Prove automatic recovery uses the same DLQ and audited replay path without browser intervention.

**Demonstration:** configure three attempts and automatic recovery, then observe three failed-attempt events, one dead-letter decision, one recovery initiated by `automatic-policy`, and terminal `COMPLETED`.

Implemented with version-tolerant shared contracts, per-run exhaustion in Fulfilment, an atomic recovery claim in the Lab Console, conditional builder controls, trace-visible initiator evidence, and a reusable integration script.

## Milestone 12: backend-generated evidence reports — completed 2026-08-12

- Evaluate plan-specific invariants from persisted timeline observations in the Lab Console.
- Expose individual `IN_PROGRESS`, `PROVED`, or `FAILED` checks rather than trusting a frontend-only verdict.
- Attach the exact supporting trace IDs to every applicable check.
- Render the assessment alongside the live timeline.
- Export the plan, checks, trace IDs, and complete event timeline as JSON.

**Demonstration:** run an automatic-recovery experiment and download a report proving the selected delivery count, retry budget, single DLQ transition, single audited recovery, and expected terminal outcome.

Implemented with a plan-aware evidence evaluator, a stable `/api/v1/runs/{workflowId}/evidence` endpoint, race-safe live refreshes, a responsive evidence checklist, and downloadable JSON bundles.

## Milestone 13: Live Lab Control Center — completed 2026-08-12

- Identify the deployed environment and immutable application version.
- Show the scheduled expiry as a live countdown and expose Workflow, Payment, and Fulfilment health.
- Stop accepting new experiments during the ten-minute teardown safety window while preserving existing Run Inspector evidence.
- Keep extension and destruction explicitly owner-operated through permission-protected GitHub workflows.
- Smoke-test the public Scenario Builder, direct run routes, backend evidence, dependency health, and anonymous Grafana access after every Azure deployment.

**Demonstration:** watch the Control Center identify the deployed commit, verify all three participants, and count down the environment lifetime; shortly before teardown, observe that new runs are rejected by both the interface and backend while existing evidence remains readable.

Implemented with a deployment-status API, server-enforced lifecycle modes, health probes with bounded timeouts, a responsive Control Center, owner handoff links, Terraform-injected deployment metadata, and expanded post-deployment smoke tests.

## Milestone 14: evidence-pipeline readiness — completed 2026-08-12

- Distinguish HTTP availability from the ability to consume and project business events.
- Expose whether the Lab Console subscriber is starting, running, disabled, or in error.
- Record the last successfully projected event time and surface it in the Control Center.
- Reject new experiments when their live evidence cannot be collected while preserving existing evidence.
- Require a running evidence pipeline in the Azure post-deployment smoke test.

**Demonstration:** start the Lab Console without messaging and observe that HTTP remains reachable but the Control Center identifies the disabled evidence pipeline and the backend rejects a new experiment with HTTP 503; restart with messaging enabled and observe retained events catch up before new runs are accepted.

Implemented with an always-present pipeline status component, processor lifecycle/error callbacks, a fail-closed run-admission gate, a Control Center indicator, and regression coverage for the disabled-subscriber condition.

## Milestone 15: evidence consistency watchdog — completed 2026-08-12

- Compare Run Inspector projection state with Workflow's authoritative aggregate state through its public service API.
- Treat intermediate differences as normal in-flight propagation rather than false failures.
- Give terminal events a bounded grace period, then identify a projection that remains behind.
- Keep invariant assessment distinct from operational consistency proof.
- Require authoritative and projected terminal agreement in the Azure deployment smoke test.

**Demonstration:** inspect a completed run and show `COMPLETED ↔ COMPLETED · CONSISTENT`; if the evidence subscriber misses the terminal event, show the authoritative terminal state beside the stale projection and classify it as `PROJECTION_BEHIND` after five seconds.

Implemented with a cross-service consistency endpoint, terminal-aware comparison rules, source-unavailable handling, a responsive two-state Run Inspector proof, focused classifier tests, and an Azure consistency assertion.

## Milestone 16: poison-message quarantine — completed 2026-08-12

- Send a syntactically valid Fulfilment command with an unsupported schema version.
- Distinguish a consumer that rejects a message from an offline consumer that never attempts it.
- Bound poison delivery to three attempts and explicitly settle the final delivery into the native Service Bus DLQ.
- Keep the unsupported payload from completing Fulfilment and move the saga to `FAILED_REQUIRES_INTERVENTION`.
- Prove each rejection, the single quarantine decision, and the terminal outcome through backend-generated evidence and trace links.

**Demonstration:** choose **Unsupported contract → poison DLQ** in the Scenario Builder, then observe schema version 99 rejected on three deliveries, one `POISON_DEAD_LETTERED` transition, zero Fulfilment completion, and terminal `FAILED_REQUIRES_INTERVENTION`.

Implemented with explicit schema validation at the consumer boundary, abandon/dead-letter settlement, a contract-rejection business event, poison-specific timeline and trace decisions, immediate saga escalation, and plan-aware evidence checks.

## Milestone 17: native DLQ Inspector — completed 2026-08-12

- Inspect the actual Service Bus dead-letter subqueue without receiving, locking, or settling its messages.
- Locate a DLQ entry by the experiment workflow ID rather than displaying unrelated messages.
- Expose bounded broker metadata: queue, message ID, reason, error description, schema version, delivery count, sequence number, and enqueue time.
- Keep incompatible-contract replay blocked while preserving the existing guarded recovery path for transient failures.
- Present broker proof beside trace decisions, projected events, and authoritative Workflow state.

**Demonstration:** run **Unsupported contract → poison DLQ** and observe `UnsupportedContractVersion` plus schema version 99 directly from `fulfilment-commands/$deadletterqueue`, while the UI explains why replay is unavailable.

Implemented with paged Service Bus peek operations, a workflow-scoped inspection endpoint, a responsive native-broker proof panel, mapping and controller tests, and an Azure smoke assertion for the poison DLQ metadata.

## Milestone 18: interactive load and concurrency evidence — completed 2026-08-12

- Launch real workflows with burst or steady arrival patterns.
- Mix normal and duplicate payment-result deliveries under concurrent load.
- Measure accepted and terminal work, backlog drain, maximum overlap, throughput, median latency, and p95 latency.
- Make correctness the gate through per-workflow backend evidence and explicit invariant-violation counts.
- Bound local and public workloads and allow only one active pressure experiment per environment.

**Demonstration:** launch a 25-workflow burst with 20% duplicate delivery, observe more than one workflow in flight, watch the backlog drain to zero, and require 25 proved evidence reports with zero invariant violations before the aggregate becomes `PROVED`.

Implemented with persistent load groups, Java virtual-thread launchers, immediate per-member acceptance persistence, separate launch and terminal progress, scheduled evidence aggregation, durable member links, a responsive Load & Concurrency Lab, and environment-specific safety ceilings. The existing k6 suite remains the repeatable regression baseline; the interactive report makes the same correctness-under-load idea explainable in the public demo.

## Milestone 19: consumer concurrency comparison — completed 2026-08-13

- Select bounded Service Bus consumer concurrency profiles of 1, 4, or 8.
- Apply the profile to Workflow, Payment, Fulfilment, and evidence projection processors before load begins.
- Persist the selected profile with each result and restore the sequential baseline afterward.
- Compare recent throughput, median latency, p95 latency, and correctness outcomes side by side.
- Record a reproducible local comparison without presenting it as production sizing evidence.

**Demonstration:** run the same 50-workflow burst at concurrency 1, 4, and 8, then use the comparison table to explain both the invariant result and why more parallelism did not improve this local run.

Implemented with runtime processor reconfiguration, an internal bounded control plane with rollback, persistent profile metadata, a recent-results comparison table, backward-compatible API defaults, and a documented three-profile measurement.

## Milestone 20: order-balanced concurrency trials — completed 2026-08-13

- Replace conclusions from single measurements with repeated profile trials.
- Rotate concurrency 1, 4, and 8 through every run position to reduce warm-up and ordering bias.
- Stop the sequence when any constituent run fails its distributed invariant proof.
- Summarize median throughput per profile and retain every underlying experiment for inspection.
- Use a lighter single round in the cost-bounded public environment.

**Demonstration:** launch one balanced comparison locally, watch nine real experiments run in the orders `1→4→8`, `4→8→1`, and `8→1→4`, then compare three median throughput values with the combined invariant-violation count.

Implemented as a visible browser-coordinated sequence over the ordinary persistent load API. The page explicitly says it must remain open; a future production-grade benchmark scheduler would persist campaign coordination on the backend.

## Milestone 21: comparison stability and live campaign evidence — completed 2026-08-13

- Run the same nine measured trials in both local and Azure environments.
- Warm each consumer profile once before collecting measurements.
- Allow broker connections and processors a short settling interval between experiments.
- Refresh the latest-results table after every constituent run.
- Report minimum, median, maximum, spread, and an honest stability classification.

**Demonstration:** watch the latest-results table change during the campaign, then explain that a profile with more than 25% throughput spread is `INCONCLUSIVE` even when every workflow invariant is proved.

Implemented with three excluded warm-up experiments, two-second settling intervals, live durable-result insertion, campaign-only measured summaries, and a deliberately conservative variability verdict.

## Milestone 22: self-contained interactive laboratories — completed 2026-08-13

- Keep each experiment control surface adjacent to the evidence it produces.
- Place curated-scenario results beneath the scenario library.
- Place custom-plan results beneath the Scenario Builder.
- Keep live load results, campaign summaries, and load history with the Load Lab.
- Finish with the durable Evidence Archive rather than sending new results below unrelated sections.
- Add sticky section navigation while preserving smooth focus-aware scrolling.

**Demonstration:** launch either a curated or custom experiment and land on its result directly beneath the initiating controls; move between all four interactive areas from the compact lab navigation.

Implemented with state-aware visual ordering of the shared result panel, explicit section anchors, responsive sticky navigation, and unchanged backend/run identity semantics.

## Milestone 23: load-pipeline phase diagnostics — completed 2026-08-13

- Split aggregate load timing into launch, first payment, first fulfilment, first terminal, and full-drain milestones.
- Derive timings from persisted experiment and business-event timestamps rather than browser clocks.
- Update phase evidence live alongside the existing stage counts.
- Preserve throughput and latency as outcome metrics while exposing where a slow-mode delay begins.
- Keep unavailable future phases at zero until the corresponding backend evidence exists.

**Demonstration:** compare a fast and slow run and identify the first phase whose elapsed time diverges, narrowing the investigation to admission, consumer readiness, or sustained pipeline drain.

Implemented with backend-derived phase delays in the load response and a responsive timing strip directly beneath the distributed stage progression.

## Milestone 24: Workflow-to-Fulfilment handoff evidence — completed 2026-08-13

- Split the previously opaque Payment-to-Fulfilment interval at the Workflow transaction boundary.
- Publish `fulfilment.command-queued` when Workflow consumes authorization and atomically commits the Fulfilment command to its outbox.
- Preserve the command event ID in the diagnostic event for trace and causation correlation.
- Project the handoff into ordinary durable run evidence.
- Add the first queued-command timestamp to aggregate phase diagnostics.

**Demonstration:** compare Payment, command-queued, and Fulfilment timestamps to distinguish delayed Workflow consumption from delayed command dispatch or Fulfilment processing.

Implemented as an explicit business-process observation emitted in the same transaction as the command outbox row, retaining the system's reliability semantics while removing a large diagnostic blind spot.
