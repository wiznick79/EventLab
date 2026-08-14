# ADR 014: Bound the anonymous lab with layered, disposable controls

- Status: Accepted
- Date: 2026-08-14

## Context

EventLab is intentionally interactive and anonymous when deployed. Requiring an Azure or Grafana login would weaken the portfolio demonstration, but exposing unbounded experiment creation, direct observability ingress, public PostgreSQL, broad deployment roles, or mutable supply-chain references is not acceptable.

## Decision

Keep the synthetic lab anonymous and apply multiple independent bounds:

- enforce request/body/connection limits at Nginx and run/SSE/workload limits in the Lab Console;
- require durable idempotency for run creation;
- proxy internal-only Grafana through the EventLab edge;
- place PostgreSQL on delegated private networking and use one database login per service;
- use entity-scoped Service Bus managed-identity roles with local authentication disabled;
- place all ephemeral resources in a persistent resource-group authorization boundary;
- scope GitHub's Azure roles to that group and state account;
- authenticate GitHub with environment-bound OIDC and state with Entra RBAC;
- deploy registry digests, pin GitHub Actions to commits, and scan code, dependencies, secrets, infrastructure, and runtime images;
- preserve automatic expiry and Terraform-owned destruction.

## Consequences

The public demo remains frictionless and trace exploration remains useful. Abuse can consume only bounded capacity in a small short-lived environment. Deployment is more complex: workflows must temporarily admit their runner IP to state, initialize database roles through a private Container Apps job, and resolve image tags to digests. Terraform state remains sensitive because it contains generated database credentials.

The design is appropriate for a portfolio lab, not a complete production perimeter. A production system would add authenticated tenants, a managed edge/WAF, durable distributed rate limiting, centralized secret rotation, and independent security monitoring.
