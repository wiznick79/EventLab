# Persistent Azure bootstrap

This one-time Terraform root deliberately uses local state. It creates the small set of resources that must outlive every disposable EventLab environment:

- a bootstrap resource group, shared-key-disabled storage account, and private versioned/firewalled container for remote Terraform state;
- a persistent environment resource group that bounds all GitHub deployment and RBAC authority;
- a Microsoft Entra application and service principal for GitHub Actions;
- separate federated credentials for the reviewer-protected `azure` environment and unattended `azure-cleanup`, using GitHub's immutable ID-based, environment-scoped OIDC subject format;
- environment-resource-group-scoped `Contributor` and `Role Based Access Control Administrator` assignments, plus state-account-scoped RBAC.

The second role permits the deployment workflow to grant managed identities access to Service Bus, PostgreSQL, and monitoring resources. No client secret is created.

Before the first deployment, register the subscription for the `Microsoft.App` resource provider as described in the Azure environment runbook. Provider registration is a persistent, subscription-level bootstrap prerequisite; the resource-group-scoped GitHub identity deliberately cannot perform it.

AzureRM 5 no longer registers resource providers implicitly in this project. Both Terraform roots set `resource_provider_registrations = "none"`; every provider required by the application must therefore be registered deliberately during bootstrap. The GitHub owner and repository IDs in `terraform.tfvars.example` are immutable identity components, not secrets. Confirm them with GitHub's API if the repository is transferred or recreated.

## Bootstrap manually

```powershell
Set-Location infrastructure/terraform/bootstrap
Copy-Item terraform.tfvars.example terraform.tfvars
# Replace the placeholder IDs with: az account show --query "{subscription:id,tenant:tenantId}"
terraform init
terraform plan -out bootstrap.tfplan
terraform apply bootstrap.tfplan
terraform output
```

Keep `terraform.tfstate` secure and backed up after the initial apply. The application root migrates its own state into the storage account created here; the bootstrap state remains local so destroying an application environment cannot remove its backend or identity.

Shared-key authentication is disabled. The AzureRM provider uses Microsoft
Entra ID for storage data-plane reads. When revisiting this bootstrap root from
a workstation, temporarily include that workstation's public CIDR in
`state_allowed_ips`, then remove it again after the apply.

Create GitHub environments named `azure` and `azure-cleanup`, then copy the Azure identity/backend outputs into both. The `backend_key_prefix` output is combined with each requested environment name to isolate its state. Protect `azure` with a required reviewer; keep `azure-cleanup` unreviewed so expiry cleanup cannot be stranded.
