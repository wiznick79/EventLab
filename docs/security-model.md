# Security and threat model

## Scope and assets

EventLab protects the GitHub repository and workflows, Terraform state, the Azure subscription boundary, synthetic workflow evidence, and the availability of the public demonstration. It does not process real customer or payment data. Scenario amounts are illustrative values only.

The primary trust boundaries are the public browser-to-frontend edge, frontend-to-Lab Console proxy, service-to-Service Bus identities, service-to-database credentials, GitHub-to-Azure OIDC federation, and Terraform state storage.

## Principal threats and controls

| Threat | Control |
| --- | --- |
| Public request or SSE exhaustion | Nginx body, request, connection, and stream limits; backend global run admission; one active load campaign; bounded public workload size; five-minute SSE lifetime. |
| Duplicate POST after network retry | Client-generated idempotency key, header/body agreement, durable unique experiment-plan identity, and same-response replay. |
| Poison or permanently failing outbox entry | Exponential retry schedule, ten-attempt quarantine, bounded error storage, and operator-visible database evidence. |
| Unsafe DLQ replay | Workflow existence check, bounded broker scan, reason allowlist, incompatible-contract quarantine, and broker lock abandonment. |
| Database exposure or cross-service access | PostgreSQL has no public endpoint, uses delegated VNet/private DNS, and gives every service its own login and database ownership. |
| Excessive broker privilege | SAS/local authentication is disabled; each system-assigned identity receives only entity-scoped Sender or Receiver roles it needs. |
| Compromised CI dependency or image | Actions are pinned to full commit SHAs; runtime images are digest pinned; CodeQL, dependency review, Gitleaks, Trivy, Dependabot, SBOM, and provenance checks run in CI. |
| Long-lived cloud secret | GitHub uses an environment-scoped federated credential and short-lived OIDC token; no Azure client secret is stored. |
| CI identity escapes project | Contributor and RBAC Administrator are scoped to the persistent EventLab environment resource group; state permissions are scoped to the state account. |
| Terraform state disclosure | Shared-key authentication is disabled, blob access uses Entra RBAC, versioning/deletion retention is enabled, and the firewall admits only explicit administrative IPs plus the current CI runner temporarily. |
| Public observability abuse | Grafana remains anonymous read-only-by-role for the demo, but has no direct public ingress; `/grafana` is proxied through the same rate-limited TLS frontend. Tempo retention is 24 hours. |
| Expired environment remains billable | Every resource is tagged with an RFC3339 expiry; scheduled cleanup validates metadata and destroys the corresponding remote Terraform state. |
| Workflow supply-chain mutation | Workflows have minimum permissions, checkout credentials are not persisted, deployment images resolve to registry digests, and workflows no longer push generated status commits to `main`. |

## Accepted residual risks

- The lab is intentionally anonymous. A determined distributed client can consume the small public environment's resources despite per-address and global admission bounds. Azure cost budgets and short expiry are the final containment layer.
- Grafana Explore is enabled for anonymous Viewer users because trace exploration is an explicit learning feature. It is proxied and rate limited, contains synthetic 24-hour traces, and cannot modify Azure or business data.
- Current Grafana and Tempo releases contain newly disclosed High-severity vulnerabilities in bundled Go dependencies for which no patched upstream image exists yet. Their images remain blocked on Critical findings; every application and edge image is blocked on both fixable High and Critical findings. Both observability services have internal-only ingress, bounded synthetic input, short retention, pinned digests, and automated update tracking.
- Generated database passwords are present in encrypted Terraform state as sensitive values, as required for Container Apps secret provisioning. Access to state is therefore a privileged operation.
- This project runs one replica per service to keep the educational environment affordable. The synchronized idempotency lookup is supplemented by a database uniqueness constraint, but a horizontally scaled production service should use an atomic insert/claim design and a first-class idempotency response store.

## Verification

`mvn verify`, frontend tests/build, Terraform validation, Compose validation, the real broker/database integration job, CodeQL, dependency review, Gitleaks, Trivy configuration scanning, and post-build image scanning are required checks. Azure deploy smoke tests prove the happy path, idempotent submission, duplicate handling, trace retrieval, consistency, and poison-message quarantine before advertising a live URL.
