# EventLab

EventLab is an interactive distributed-systems failure laboratory. It makes message delivery, retries, dead letters, ordering, saga compensation, replay, and recovery visible through a public web interface and distributed traces.

The project is a portfolio and learning system, not a parcel-management product. Its deliberately small order-fulfilment workflow exists only to provide realistic distributed-system experiments.

## Status

Architecture and scope definition. Application implementation has not started.

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
