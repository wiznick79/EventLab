# ADR-008: Ephemeral Azure infrastructure

**Status:** Accepted

## Context

The project has limited Azure for Students credit and does not require a continuously available interactive environment.

## Decision

Separate Terraform into persistent bootstrap and disposable application roots/states. Bootstrap owns remote state and GitHub/Entra OIDC identity. Each disposable environment owns its complete runtime resource group and has a 2-, 8-, or 24-hour lifetime recorded as `destroy_after`.

Provide manual `plan`, `deploy`, and `destroy` GitHub Actions operations plus scheduled expiry cleanup. Build immutable images once and store them in GHCR. Use OIDC and managed identities instead of long-lived Azure client secrets.

## Consequences

- PostgreSQL, Service Bus, Container Apps, and monitoring charges stop when the environment is destroyed.
- Cleanup must use the normal Terraform state, verify deletion, report failures, and detect missing expiry tags.
- A small storage account and essential identity/role resources remain persistent.
- The static portfolio page remains available when the interactive environment is offline.
