# Persistent Azure bootstrap

This one-time Terraform root deliberately uses local state. It creates the small set of resources that must outlive every disposable EventLab environment:

- a resource group, storage account, and private versioned container for remote Terraform state;
- a Microsoft Entra application and service principal for GitHub Actions;
- one federated credential restricted to the repository's protected `azure` environment;
- subscription-scoped `Contributor` and `Role Based Access Control Administrator` assignments.

The second role permits the deployment workflow to grant managed identities access to Service Bus, PostgreSQL, and monitoring resources. No client secret is created.

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

Create a GitHub environment named `azure`, then copy the three Azure identity outputs and backend outputs into its variables. Protect that environment with required reviewers before enabling deployment workflows.
