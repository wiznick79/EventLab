# Azure environment runbook

EventLab Azure environments are deliberately disposable. Service Bus Standard and PostgreSQL Flexible Server are billable, so every deployment must have a 2-, 8-, or 24-hour expiry and must be destroyed through Terraform state.

## One-time bootstrap

1. Sign in with `az login` and select the Azure for Students subscription.
2. Copy `infrastructure/terraform/bootstrap/terraform.tfvars.example` to the ignored `terraform.tfvars` file and set the subscription and tenant IDs. France Central is the project default because the current subscription policy permits it while rejecting West Europe. Recheck the `Allowed resource deployment regions` policy before changing regions.
3. Run `terraform init`, `terraform plan -out bootstrap.tfplan`, inspect the plan, and apply that saved plan.
4. Register the Container Apps resource provider with `az provider register --namespace Microsoft.App --wait`. The deployment workflow repeats this idempotently before each apply.
5. Create a GitHub environment named `azure`. Add required reviewers for manual deployments while ensuring scheduled cleanup can still run unattended.
6. Add these bootstrap outputs as GitHub environment variables:

| GitHub variable | Terraform output |
| --- | --- |
| `AZURE_CLIENT_ID` | `azure_client_id` |
| `AZURE_TENANT_ID` | `azure_tenant_id` |
| `AZURE_SUBSCRIPTION_ID` | `azure_subscription_id` |
| `TF_STATE_RESOURCE_GROUP` | `backend_resource_group_name` |
| `TF_STATE_STORAGE_ACCOUNT` | `backend_storage_account_name` |
| `TF_STATE_CONTAINER` | `backend_container_name` |
| `TF_STATE_KEY_PREFIX` | `backend_key_prefix` |

No client secret is required. GitHub receives a short-lived Azure token only when a workflow uses the repository's `azure` environment.

The bootstrap resource group may retain an older metadata location after an allowed-region policy change. Terraform intentionally preserves it because resource-group location does not constrain contained resources; the state storage and disposable application resources use the configured deployment location.

## Image publication

`images.yml` builds the five application images plus Tempo, the telemetry gateway, and Grafana for every main-branch commit. The observability images use suffixed immutable tags in the existing public `frontend` package, so the deployment needs only the five public GHCR packages and stores no registry password in Azure.

## Plan and deploy

1. Run **Build immutable images** for the chosen commit and confirm all five matrix jobs pass.
2. Run **Plan Azure environment** with the same full commit SHA and inspect the Terraform summary.
3. Run **Deploy Azure environment** with a 2-hour lifetime for the first test.
4. The deployment applies the saved plan and runs the happy-path scenario through the public frontend URL. A successful smoke test requires the projected workflow timeline to reach `COMPLETED`.

Flyway runs during each Java service startup against its owned database. Service Bus access uses each Container App's system-assigned managed identity; namespace SAS authentication is disabled.

## Destroy and verify

Run **Destroy Azure environment** as soon as the demonstration ends. It destroys resources through the normal remote state and fails if an ephemeral EventLab resource group remains.

The scheduled cleanup checks `destroy_after` twice per hour. Missing expiry tags fail loudly; expired environments are destroyed through the same Terraform state rather than by deleting resource groups out of band.

## Failure response

- If deploy fails after resources were created, run the destroy workflow immediately.
- If Terraform cannot acquire the state lock, do not force-unlock until the competing workflow is confirmed stopped.
- If destroy fails, inspect the job and Azure activity log, rerun destroy, and verify the resource group is gone in Azure Portal or with `az group show`.
- Never delete the bootstrap resource group during ordinary environment cleanup. It owns the remote state and OIDC identity used for recovery.
