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
4. The deployment applies the saved plan and runs the happy-path scenario through the public frontend URL. It then runs the duplicate-delivery scenario and requires the ignored duplicate's exact trace ID to be retrievable anonymously through the deployed Grafana/Tempo datasource.

The final smoke test also requires the Control Center to report `ONLINE`, the scheduled expiry, and all three participant services as `UP`. It submits a custom Scenario Builder plan, loads its direct Run Inspector route, and waits for the backend evidence report to prove the expected invariants.

The same status response must report the evidence pipeline as enabled and `RUNNING`. HTTP `UP` alone is insufficient: if the Lab Console subscriber is disabled or reports a processor error, new runs are rejected with HTTP 503 while already-persisted evidence remains readable. Messages that received no processing attempt remain on the Service Bus subscription and are projected after the subscriber recovers; they belong in the DLQ only after repeated processing failure.

For the custom smoke-test run, the workflow also queries `/api/v1/runs/{workflowId}/consistency` and requires authoritative Workflow state and projected Lab Console state to both be `COMPLETED`. This catches a live but lagging evidence subscriber that endpoint health and processor lifecycle alone cannot detect.

The workflow summary publishes separate EventLab and Grafana URLs. Both are public for the lifetime of the disposable environment; visitors do not need an Azure or Grafana account.

After both smoke tests pass, the deployment workflow publishes the EventLab URL and expiry to `frontend/public/live-lab.json` and rebuilds the permanent portfolio. The tour then links visitors directly to the running lab. When no valid environment is advertised, it links to these launch instructions instead; the Actions workflow remains available as secondary implementation evidence.

Flyway runs during each Java service startup against its owned database. Service Bus access uses each Container App's system-assigned managed identity; namespace SAS authentication is disabled.

## Destroy and verify

Run **Destroy Azure environment** as soon as the demonstration ends. It destroys resources through the normal remote state and fails if an ephemeral EventLab resource group remains.

After deletion is verified, manual destroy and scheduled expiry cleanup mark the permanent tour's live-lab status offline and republish it. The frontend also treats an expired timestamp as offline, so a delayed status update cannot send visitors to an environment whose lifetime has elapsed.

The scheduled cleanup checks `destroy_after` twice per hour. Missing expiry tags fail loudly; expired environments are destroyed through the same Terraform state rather than by deleting resource groups out of band.

Ten minutes before `destroy_after`, the Lab Console changes to `READ_ONLY`. New experiment requests return HTTP 503, including requests made outside the frontend; existing run timelines, comparisons, trace links, and evidence downloads remain readable. To extend the demonstration, an authorized collaborator reruns **Deploy Azure environment** with the same environment name and a new supported lifetime. To end it early, run **Destroy Azure environment**. Visitors can see these handoff links, but only collaborators with repository workflow permission can execute them.

The application provider permits deletion of the disposable resource group when Azure has added platform-managed children that are not in Terraform state, such as the Application Insights Smart Detection action group. This exception is scoped to the disposable application root; cleanup still fails unless the subsequent Azure CLI check confirms that the complete resource group is gone.

## Failure response

- If deploy fails after resources were created, run the destroy workflow immediately.
- If Terraform cannot acquire the state lock, do not force-unlock until the competing workflow is confirmed stopped.
- If destroy fails, inspect the job and Azure activity log, rerun destroy, and verify the resource group is gone in Azure Portal or with `az group show`.
- Never delete the bootstrap resource group during ordinary environment cleanup. It owns the remote state and OIDC identity used for recovery.
