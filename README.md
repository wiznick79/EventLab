# EventLab

EventLab is an interactive distributed-systems failure laboratory. It makes message delivery, retries, dead letters, ordering, saga compensation, replay, and recovery visible through a public web interface and distributed traces.

The project is a portfolio and learning system, not a parcel-management product. Its deliberately small order-fulfilment workflow exists only to provide realistic distributed-system experiments.

## Status

Milestone 0 is implemented: the repository contains the backend module boundaries, transport-neutral contracts, a tested React experiment-console shell, PostgreSQL Compose foundation, Maven Wrapper, container build foundations, and baseline GitHub Actions CI.

The distributed workflow and Azure Service Bus integration begin in Milestone 1.

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

Start the current UI shell with:

```powershell
Set-Location frontend
npm.cmd run dev
```
