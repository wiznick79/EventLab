# Incremental roadmap

Every milestone must finish with a behavior that can be demonstrated through the UI or a short recording.

## Milestone 0: repository and contracts

- Establish the Maven modules, frontend, Compose skeleton, coding conventions, and decision records.
- Define workflow states, event envelope, identifiers, versioning, and scenario contracts.
- Add baseline CI verification.

**Demonstration:** architecture page and executable skeleton with health endpoints.

## Milestone 1: observable walking skeleton

- Implement Workflow, one participant, Lab Console, and the UI timeline.
- Run PostgreSQL and the Service Bus emulator locally.
- Propagate OpenTelemetry context end to end.
- Display a successful workflow and link it to a trace.

**Demonstration:** start a workflow and watch its messages and spans reach completion.

## Milestone 2: reliable delivery

- Add transactional outbox dispatch.
- Add inbox/idempotent consumption.
- Implement the deterministic duplicate-payment-result scenario.
- Add multi-service integration tests.

**Demonstration:** deliver one logical result multiple times while producing one state change.

## Milestone 3: retry, DLQ, and recovery

- Add deterministic temporary failure.
- Expose delivery attempts and exponential backoff.
- Add retry exhaustion, DLQ inspection, replay audit, and guarded resubmission.

**Demonstration:** take Fulfilment offline logically, reach the DLQ, recover it, and complete the workflow.

## Milestone 4: saga compensation

- Complete Payment and Fulfilment boundaries.
- Persist orchestration state and timeouts.
- Add fulfilment rejection and payment compensation.
- Display forward and compensating paths and final invariants.

**Demonstration:** payment authorization followed by fulfilment rejection and successful compensation.

This is the MVP boundary.

## Milestone 5: ordering and concurrency

- Add optimistic concurrency and workflow versions.
- Demonstrate stale or out-of-order delivery.
- Compare unordered processing with Service Bus sessions where useful.

**Demonstration:** a delayed stale event is visible but cannot regress workflow state.

## Milestone 6: ephemeral Azure deployment

- Create persistent bootstrap and disposable application Terraform roots.
- Configure Entra/GitHub OIDC and managed identities.
- Build immutable GHCR images in CI.
- Add `plan`, `deploy`, `destroy`, Flyway, seed, smoke-test, and TTL cleanup workflows.
- Export telemetry to Azure Monitor/Application Insights.

**Demonstration:** create an environment with a chosen lifetime, run a scenario in Azure, and destroy it completely.

## Milestone 7: portfolio polish

- Add explanatory copy, diagrams, and trace links.
- Create Azure and local dashboards, runbooks, and cost controls.
- Record the primary scenarios.
- Publish a permanent static portfolio page for times when Azure is offline.

**Demonstration:** self-contained public project tour and recorded failure/recovery session.

## Optional later experiments

- Container termination or network-delay injection.
- Selected k6 resilience tests.
- Split Inventory and Shipment if a scenario requires it.
- Kafka comparison adapter if it produces a meaningful broker-semantics comparison.
- Local `kind` or `k3d` only for a concrete orchestration experiment.
