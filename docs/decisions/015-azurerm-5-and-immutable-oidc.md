# ADR-015: Adopt AzureRM 5 while preserving immutable GitHub OIDC identity

## Status

Accepted.

## Context

EventLab's persistent bootstrap and disposable application roots previously selected AzureRM 4.x. AzureRM 5 makes resource-provider registration explicit and requires `logs_destination = "log-analytics"` when a Container Apps environment is connected to a Log Analytics workspace.

GitHub also changed the default OIDC subject for newer repositories to include immutable numeric owner and repository IDs. EventLab's live Microsoft Entra federated credentials already use that format. Reverting them to name-only subjects would break workflow authentication and would weaken protection against repository rename or name-reuse scenarios.

## Decision

- Select AzureRM `~> 5.1` in both Terraform roots and commit the generated provider locks.
- Set `resource_provider_registrations = "none"` explicitly. Required Azure resource providers are registered once by the subscription owner rather than implicitly by the resource-group-scoped CI identity.
- Set the Container Apps environment log destination explicitly to `log-analytics`.
- Use resource IDs for the PostgreSQL private DNS virtual-network link, as required by the AzureRM 5 schema.
- Model GitHub owner and repository IDs as bootstrap inputs and construct the exact immutable, environment-scoped OIDC subjects emitted for this repository.
- Use Terraform 1.15.9 in Azure workflows while retaining the existing broadly compatible module constraint.

## Consequences

Provider upgrades cannot silently register subscription-wide services. A new Azure subscription must complete the documented registration list before deployment. Repository transfer or recreation requires verifying both GitHub names and numeric IDs and deliberately applying the bootstrap change. The application root can upgrade existing AzureRM 4 state in place, but every provider-major upgrade must still be validated through a disposable live environment before merge.
