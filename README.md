# EventLab

EventLab is an interactive distributed-systems failure laboratory. It makes message delivery, retries, dead letters, ordering, saga compensation, replay, and recovery visible through a public web interface and distributed traces.

**[Open the permanent EventLab project tour](https://wiznick79.github.io/EventLab/)**

The project is a portfolio and learning system, not a parcel-management product. Its deliberately small order-fulfilment workflow exists only to provide realistic distributed-system experiments.

## Status

Milestones 0–12 are complete. EventLab includes a disposable Azure environment, a permanent portfolio tour, repeatable resilience measurements, an interactive Scenario Builder, durable Run Inspector evidence, configurable recovery policies, and backend-generated downloadable evidence reports.

## Target technology

- Java 21, Spring Boot 3, and Maven multi-module builds.
- React, TypeScript, and Vite.
- PostgreSQL and Flyway.
- Azure Service Bus Standard in Azure and the official Service Bus emulator locally.
- Docker and Docker Compose.
- Azure Container Apps.
- Terraform.
- OpenTelemetry, with Grafana/Tempo for self-contained trace demos and Azure Monitor/Application Insights for Azure operations.
- JUnit 5 and Testcontainers.
- GitHub Actions, GitHub Container Registry, and GitHub OIDC federation with Microsoft Entra ID.

## Documentation

- [Proposed architecture](docs/architecture.md)
- [Incremental roadmap](docs/roadmap.md)
- [Ephemeral Azure runbook](docs/runbooks/azure-environment.md)
- [How to verify frontend claims in traces](docs/runbooks/reading-traces.md)
- [How to regenerate recorded demonstrations](docs/runbooks/recording-demos.md)
- [Performance and resilience testing](docs/runbooks/performance-testing.md)
- [Architecture decisions](docs/decisions/README.md)

The permanent static tour is published through GitHub Pages from the same frontend with `VITE_STATIC_TOUR=true`. It contains no API calls and remains useful while the disposable Azure lab is offline. Locally, open `http://localhost:5173/?tour` after starting Vite.

## Guiding rule

A feature belongs in the MVP only if it enables, exposes, verifies, or explains a distributed-systems behavior.

## Repository structure

- `contracts/` — transport-neutral event, workflow, and scenario contracts.
- `services/workflow-service/` — future workflow and saga orchestrator.
- `services/payment-service/` — simulated payment participant.
- `services/fulfilment-service/` — simulated fulfilment participant.
- `services/lab-console/` — experiment control plane and timeline projection.
- `frontend/` — React experiment console.
- `infrastructure/` — container and, later, Azure/Terraform assets.
- `docs/` — architecture, roadmap, and decision records.

## Verify locally

```powershell
.\mvnw.cmd verify
Set-Location frontend
npm.cmd install
npm.cmd test
npm.cmd run build
Set-Location ..
docker compose config --quiet
```

## Run the local stack

The local broker is Microsoft's official Azure Service Bus emulator. It depends on an Azure SQL Edge container and requires you to accept Microsoft's container EULA explicitly. Review the applicable terms, then copy the environment template and change `ACCEPT_EULA=N` to `ACCEPT_EULA=Y`. EventLab never accepts it automatically.

```powershell
Copy-Item .env.example .env
# Review .env and explicitly set ACCEPT_EULA=Y if you accept the terms.
docker compose up -d postgres servicebus-sql servicebus tempo otel-collector grafana
```

The PostgreSQL initialization script creates one database and role per service. If the Postgres volume predates that script, remove only that development volume and recreate it:

```powershell
docker compose down
docker volume rm eventlab_eventlab-postgres-data
docker compose up -d postgres
```

Package the multi-module backend once from the repository root:

```powershell
mvn package
```

Then start the four backend processes in separate PowerShell terminals from the repository root. Each terminal must enable messaging before launching its service JAR:

```powershell
$env:EVENTLAB_MESSAGING_ENABLED='true'
java -jar services/workflow-service/target/workflow-service-0.1.0-SNAPSHOT.jar
```

```powershell
$env:EVENTLAB_MESSAGING_ENABLED='true'
java -jar services/payment-service/target/payment-service-0.1.0-SNAPSHOT.jar
```

```powershell
$env:EVENTLAB_MESSAGING_ENABLED='true'
java -jar services/fulfilment-service/target/fulfilment-service-0.1.0-SNAPSHOT.jar
```

```powershell
$env:EVENTLAB_MESSAGING_ENABLED='true'
java -jar services/lab-console/target/lab-console-0.1.0-SNAPSHOT.jar
```

Then start the UI:

```powershell
Set-Location frontend
npm.cmd run dev
```

Open `http://localhost:5173`, run **Successful payment workflow**, and follow any trace link into Grafana at `http://localhost:3000`.

The emulator connection string is deliberately static and local-only. The emulator does not persist broker state across restarts; its queues, topic, and subscriptions are recreated from `infrastructure/servicebus/Config.json`.

After packaging the backend and starting the Compose infrastructure, the cross-service duplicate-delivery check can be repeated without the UI:

```powershell
mvn package
.\scripts\verify-duplicate-scenario.ps1
.\scripts\verify-dlq-recovery.ps1
.\scripts\verify-compensation-scenario.ps1
.\scripts\verify-ordering-scenario.ps1
.\scripts\verify-custom-experiment.ps1
.\scripts\verify-automatic-recovery.ps1
```

Run the invariant-aware concurrent baseline with the official k6 container:

```powershell
docker compose --profile performance run --rm k6
.\scripts\verify-payment-restart-recovery.ps1
.\scripts\verify-outbox-acknowledgement-window.ps1
```

### Reliability model

Workflow, Payment, and Fulfilment persist business state and outgoing messages in the same database transaction. Scheduled dispatchers send pending outbox rows and mark successful delivery. A crash after the broker accepts a message but before the row is marked can still produce a duplicate—as expected under at-least-once delivery—so consumers claim the logical message ID in an inbox within the same transaction as their state change. Failed Fulfilment attempts deliberately do not claim the inbox entry; the claim occurs only when the command succeeds.
