output "azure_client_id" {
  description = "Set as the AZURE_CLIENT_ID GitHub environment variable."
  value       = azuread_application.github.client_id
}

output "azure_subscription_id" {
  description = "Set as the AZURE_SUBSCRIPTION_ID GitHub environment variable."
  value       = var.subscription_id
}

output "azure_tenant_id" {
  description = "Set as the AZURE_TENANT_ID GitHub environment variable."
  value       = var.tenant_id
}

output "backend_resource_group_name" {
  value = azurerm_resource_group.bootstrap.name
}

output "backend_storage_account_name" {
  value = azurerm_storage_account.state.name
}

output "backend_container_name" {
  value = azurerm_storage_container.state.name
}

output "backend_key_prefix" {
  value = "eventlab"
}

output "environment_resource_group_name" {
  value = azurerm_resource_group.environments.name
}

output "github_oidc_subject" {
  value = local.github_subject
}

output "github_cleanup_oidc_subject" {
  value = local.github_cleanup_subject
}
