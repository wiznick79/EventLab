# EventLab

EventLab is an interactive distributed-systems failure laboratory. It makes message delivery, retries, dead letters, ordering, saga compensation, replay, and recovery visible through a public web interface and distributed traces.

The project is a portfolio and learning system, not a parcel-management product. Its deliberately small order-fulfilment workflow exists only to provide realistic distributed-system experiments.

## Status

Milestone 1 is complete. The successful path is implemented and verified across Workflow, Payment, the Lab Console timeline projection, Azure Service Bus messaging, PostgreSQL persistence, OpenTelemetry tracing, Tempo/Grafana, and the React console.

## Target technology

- Java 21, Spring Boot 3, and Maven multi-module builds.
- React, TypeScript, and Vite.
- PostgreSQL and Flyway.
- Azure Service Bus Standard in Azure and the official Service Bus emulator locally.
- Docker and Docker Compose.
- Azure Container Apps.
- Terraform.
- OpenTelemetry, with Grafana/Tempo/Prometheus locally and Azure Monitor/Application Insights in Azure.
- JUnit 5 and Testcontainers.
- GitHub Actions, GitHub Container Registry, and GitHub OIDC federation with Microsoft Entra ID.

## Documentation

- [Proposed architecture](docs/architecture.md)
- [Incremental roadmap](docs/roadmap.md)
- [Architecture decisions](docs/decisions/README.md)

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

## Run the Milestone 1 stack

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

Start the three backend processes in separate PowerShell terminals from the repository root:

```powershell
$env:EVENTLAB_MESSAGING_ENABLED='true'
mvn -pl services/workflow-service -am spring-boot:run
```

```powershell
$env:EVENTLAB_MESSAGING_ENABLED='true'
mvn -pl services/payment-service -am spring-boot:run
```

```powershell
$env:EVENTLAB_MESSAGING_ENABLED='true'
mvn -pl services/lab-console -am spring-boot:run
```

Then start the UI:

```powershell
Set-Location frontend
npm.cmd run dev
```

Open `http://localhost:5173`, run **Successful payment workflow**, and follow any trace link into Grafana at `http://localhost:3000`.

The emulator connection string is deliberately static and local-only. The emulator does not persist broker state across restarts; its queues, topic, and subscriptions are recreated from `infrastructure/servicebus/Config.json`.

### Current reliability boundary

Milestone 1 intentionally performs database state changes and Service Bus sends as separate operations. That establishes the observable walking skeleton, but a crash between those operations can lose or duplicate a logical message. Milestone 2 replaces this dual-write path with transactional outbox dispatch and idempotent inbox consumption.
