# Disposable Azure environment

This root owns one complete, expiring EventLab deployment inside the persistent `rg-eventlab-environments` authorization boundary. Destroying its state removes every tagged metered application resource while leaving that empty boundary, the bootstrap state account, and the GitHub identity intact.

It provisions Service Bus Standard with local authentication disabled and entity-scoped managed-identity roles, private-networked PostgreSQL with a separate login per service, four Java Container Apps, an internal Grafana/Tempo stack, a public rate-limited frontend, Log Analytics, and workspace-based Application Insights. Every resource is tagged with the required RFC3339 `destroy_after` value.

Images are selected by a full Git commit SHA for provenance, resolved to OCI digests before planning, and deployed by digest. Packages must be public so Container Apps can pull them without a registry password.

```powershell
Set-Location infrastructure/terraform/application
Copy-Item backend.hcl.example backend.hcl
Copy-Item terraform.tfvars.example terraform.tfvars
terraform init -backend-config=backend.hcl
terraform plan -out application.tfplan
```

Do not apply this root casually: Service Bus Standard and PostgreSQL are billable while the environment exists. Normal deployment and destruction will be performed through protected GitHub workflows with 2-, 8-, or 24-hour expiry choices.
