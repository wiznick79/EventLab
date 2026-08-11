# Disposable Azure environment

This root owns one complete, expiring EventLab deployment. Destroying its state removes the resource group and all metered application resources while leaving the bootstrap state account and GitHub identity intact.

It provisions Service Bus Standard with local authentication disabled, PostgreSQL Flexible Server, four managed-identity Java Container Apps, a public frontend Container App, Log Analytics, and workspace-based Application Insights. Every resource is tagged with the required RFC3339 `destroy_after` value.

Images are immutable GHCR artifacts identified by one full Git commit SHA. Packages must be public so Container Apps can pull them without a registry password.

```powershell
Set-Location infrastructure/terraform/application
Copy-Item backend.hcl.example backend.hcl
Copy-Item terraform.tfvars.example terraform.tfvars
terraform init -backend-config=backend.hcl
terraform plan -out application.tfplan
```

Do not apply this root casually: Service Bus Standard and PostgreSQL are billable while the environment exists. Normal deployment and destruction will be performed through protected GitHub workflows with 2-, 8-, or 24-hour expiry choices.
