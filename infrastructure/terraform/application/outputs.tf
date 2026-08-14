output "frontend_url" {
  value = "https://${azurerm_container_app.frontend.ingress[0].fqdn}"
}

output "grafana_url" {
  value = "https://${azurerm_container_app.frontend.ingress[0].fqdn}/grafana"
}

output "resource_group_name" {
  value = data.azurerm_resource_group.environment.name
}

output "database_role_job_name" {
  value = azurerm_container_app_job.database_roles.name
}

output "destroy_after" {
  value = var.destroy_after
}

output "application_insights_connection_string" {
  value     = azurerm_application_insights.environment.connection_string
  sensitive = true
}
