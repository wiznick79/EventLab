# EventLab architecture map

This document is the visual companion to the detailed [architecture](architecture.md) and
[decision records](decisions/README.md). It describes the implemented system rather than an
aspirational topology.

[Open the self-contained browser version](architecture-map.html).

## 1. Layered system map

```mermaid
flowchart TB
    Visitor["Visitor / interviewer"]

    subgraph Edge["Public experience and edge"]
        direction LR
        Frontend["React + TypeScript UI"]
        Proxy["Nginx edge proxy<br/>TLS, CSP, rate limits"]
    end

    subgraph Control["Experiment control and evidence"]
        direction LR
        Console["Lab Console<br/>plans, admission, load coordination"]
        Projection["Timeline + evidence projection<br/>SSE, reports, consistency checks"]
        Recovery["DLQ inspector + guarded replay"]
    end

    subgraph Business["Business workflow services"]
        direction LR
        Workflow["Workflow Service<br/>orchestration saga + state machine"]
        Payment["Payment Service<br/>authorize + compensate"]
        Fulfilment["Fulfilment Service<br/>reserve, reject, retry"]
    end

    subgraph Messaging["Asynchronous messaging"]
        direction LR
        PaymentQ[["payment-commands queue"]]
        FulfilmentQ[["fulfilment-commands queue"]]
        Topic(("business-events topic"))
        WorkflowSub[["workflow-events subscription"]]
        ConsoleSub[["lab-console-events subscription"]]
        DLQ[["native dead-letter subqueue"]]
    end

    subgraph Data["One PostgreSQL server; four ownership boundaries"]
        direction LR
        WorkflowDb[("workflow DB<br/>saga, inbox, outbox")]
        PaymentDb[("payment DB<br/>state, inbox, outbox")]
        FulfilmentDb[("fulfilment DB<br/>state, inbox, outbox")]
        ConsoleDb[("lab-console DB<br/>plans, projection, replay audit")]
    end

    subgraph Observability["Portable observability"]
        direction LR
        OTel["OpenTelemetry Collector"]
        Tempo[("Tempo traces")]
        Grafana["Grafana Explore + operations dashboard"]
        AzureRuntime["Container Apps, Service Bus, PostgreSQL"]
        AzureMonitor["Azure Monitor<br/>platform logs + metrics"]
    end

    Visitor -->|"loads site"| Proxy
    Proxy --> Frontend
    Frontend -->|"REST commands and queries"| Proxy
    Proxy -->|"/api + SSE"| Console
    Console --- Projection
    Console --- Recovery

    Console -->|"start workflow; read source state"| Workflow
    Recovery -.->|"restore simulated dependency"| Fulfilment

    Workflow -->|"outbox: authorize / compensate"| PaymentQ
    PaymentQ -->|"peek-lock + inbox"| Payment
    Workflow -->|"outbox: request fulfilment"| FulfilmentQ
    FulfilmentQ -->|"peek-lock + inbox"| Fulfilment
    FulfilmentQ -->|"exhausted / poison"| DLQ
    Recovery -.->|"peek, audit, replay"| DLQ

    Payment -->|"outbox events"| Topic
    Fulfilment -->|"outbox events"| Topic
    Workflow -->|"outbox lifecycle events"| Topic
    Topic --> WorkflowSub -->|"inbox + version guards"| Workflow
    Topic --> ConsoleSub -->|"durable observation"| Projection

    Workflow --> WorkflowDb
    Payment --> PaymentDb
    Fulfilment --> FulfilmentDb
    Console --> ConsoleDb
    Projection --> ConsoleDb
    Recovery --> ConsoleDb

    Workflow & Payment & Fulfilment & Console -->|"OTLP + W3C trace context"| OTel
    OTel --> Tempo --> Grafana
    Proxy -->|"/grafana"| Grafana
    Visitor -.->|"opens trace"| Proxy
    AzureRuntime -->|"platform telemetry"| AzureMonitor
```

The shared PostgreSQL and Service Bus infrastructure does **not** mean shared ownership.
Every service has its own database, credentials, inbox/outbox records, and transaction boundary.

## 2. Main workflow and compensation path

```mermaid
sequenceDiagram
    autonumber
    actor Visitor
    participant UI as React UI
    participant Lab as Lab Console
    participant WF as Workflow Service
    participant PQ as payment-commands
    participant Pay as Payment Service
    participant Events as business-events
    participant FQ as fulfilment-commands
    participant Ful as Fulfilment Service
    participant DLQ as native DLQ

    Visitor->>UI: Choose preset or compose plan
    UI->>Lab: POST /api/v1/runs
    Lab->>WF: Create workflow with immutable plan
    WF->>WF: Persist saga + authorize command in outbox
    WF-->>PQ: Dispatch payment.authorize
    PQ->>Pay: Peek-lock delivery
    Pay->>Pay: Claim inbox + authorize + write event to outbox
    Pay-->>Events: Publish payment.authorized
    Events->>WF: workflow-events subscription
    Events->>Lab: lab-console-events subscription
    WF->>WF: Advance saga + write fulfilment command to outbox
    WF-->>FQ: Dispatch fulfilment.request
    FQ->>Ful: Peek-lock delivery

    alt fulfilment succeeds
        Ful->>Ful: Claim inbox + persist completion + outbox event
        Ful-->>Events: Publish fulfilment.completed
        Events->>WF: Advance saga to COMPLETED
        WF-->>Events: Publish workflow.completed
    else business rejection
        Ful->>Ful: Persist rejection + outbox event
        Ful-->>Events: Publish fulfilment.rejected
        Events->>WF: Move saga to COMPENSATION_PENDING
        WF-->>PQ: Dispatch payment.compensate
        PQ->>Pay: Idempotent compensation
        Pay-->>Events: Publish payment.compensated
        Events->>WF: Advance saga to COMPENSATED
        WF-->>Events: Publish workflow.compensated
    else temporary or incompatible failure
        Ful-->>FQ: Abandon recoverable attempts
        Ful-->>Events: Publish attempt and quarantine evidence
        Ful-->>DLQ: Dead-letter exhausted or incompatible command
        Events->>Lab: Project retry / DLQ evidence
        Visitor->>Lab: Inspect and optionally request guarded replay
        Lab->>Ful: Restore simulated dependency
        Lab-->>DLQ: Peek, audit, and replay only recoverable messages
    end

    Lab-->>UI: SSE timeline + backend evidence assessment
    UI-->>Visitor: State, invariant result, and trace links
```

The Lab Console observes and explains the workflow; it does not mutate participant tables.
Its final claim is derived from persisted events and checked against Workflow's authoritative state.

## 3. Reliability boundary inside each state-changing consumer

```mermaid
flowchart LR
    Delivery["At-least-once broker delivery"] --> Begin["Begin service-owned DB transaction"]
    Begin --> Claim{"Insert event ID into inbox"}
    Claim -->|"already exists"| Duplicate["Record DUPLICATE_IGNORED<br/>no state change"]
    Claim -->|"new event"| Guard{"Business / version guard"}
    Guard -->|"valid"| State["Update owned business state"]
    Guard -->|"stale or rejected"| Decision["Persist explicit decision evidence"]
    State --> Outbox["Insert outgoing envelope into outbox"]
    Decision --> Outbox
    Outbox --> Commit["Atomic commit"]
    Duplicate --> Commit
    Commit --> Settle["Complete broker delivery"]
    Commit --> Dispatcher["Scheduled outbox dispatcher"]
    Dispatcher --> Broker["Send to Service Bus"]
    Broker --> Mark["Mark outbox row published"]
    Broker -.->|"crash before mark"| Redelivery["Possible duplicate send"]
    Redelivery --> Delivery
```

This is intentionally **at-least-once**, not exactly-once delivery. A crash after the broker accepts
an outbox message but before the row is marked can resend it; the receiving inbox makes that safe and
visible.

## 4. Runtime mapping

| Architectural role | Local development | Disposable Azure environment |
| --- | --- | --- |
| Public edge and UI | Vite dev server | Nginx frontend Container App with rate limiting |
| Java services | Four Spring Boot processes | Four Azure Container Apps |
| Broker | Official Azure Service Bus emulator | Azure Service Bus Standard with managed identity |
| Persistence | One PostgreSQL container, separate databases | PostgreSQL Flexible Server, separate databases and logins |
| Trace pipeline | OpenTelemetry Collector → Tempo → Grafana | OpenTelemetry → internal Tempo/Grafana; Azure Monitor for platform telemetry |
| Provisioning | Docker Compose | Terraform through protected GitHub Actions and Azure OIDC |
| Lifecycle | Developer-controlled | Time-limited 2, 8, or 24-hour environment with automated destruction |

## 5. Architectural invariants

- The browser reaches business execution only through the Lab Console control plane.
- Workflow owns orchestration; Payment and Fulfilment own their participant state.
- Services never read or write another service's database.
- Commands use dedicated queues; lifecycle events fan out through topic subscriptions.
- Inbox, outbox, optimistic locking, and aggregate-version guards make redelivery and reordering safe.
- The evidence projection explains execution, while Workflow remains the authoritative business state.
- Traces explain which code path ran; persisted state and evidence checks prove the durable outcome.
- Load experiments use the same APIs, broker, consumers, databases, and evidence path as single runs.
