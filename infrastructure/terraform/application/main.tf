resource "random_string" "suffix" {
  length  = 6
  lower   = true
  numeric = true
  special = false
  upper   = false
}

resource "random_password" "postgres" {
  length  = 32
  special = true
}

resource "azurerm_resource_group" "environment" {
  name     = "rg-${local.prefix}"
  location = var.location
  tags     = local.tags
}

resource "azurerm_log_analytics_workspace" "environment" {
  name                = "log-${local.prefix}"
  location            = azurerm_resource_group.environment.location
  resource_group_name = azurerm_resource_group.environment.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.tags
}

resource "azurerm_application_insights" "environment" {
  name                = "appi-${local.prefix}"
  location            = azurerm_resource_group.environment.location
  resource_group_name = azurerm_resource_group.environment.name
  workspace_id        = azurerm_log_analytics_workspace.environment.id
  application_type    = "web"
  retention_in_days   = 30
  tags                = local.tags
}

resource "azurerm_container_app_environment" "environment" {
  name                       = "cae-${local.prefix}"
  location                   = azurerm_resource_group.environment.location
  resource_group_name        = azurerm_resource_group.environment.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.environment.id
  tags                       = local.tags
}

resource "azurerm_servicebus_namespace" "environment" {
  name                = "sb-${local.prefix}-${random_string.suffix.result}"
  location            = azurerm_resource_group.environment.location
  resource_group_name = azurerm_resource_group.environment.name
  sku                 = "Standard"
  local_auth_enabled  = false
  minimum_tls_version = "1.2"
  tags                = local.tags
}

resource "azurerm_servicebus_queue" "payment_commands" {
  name               = "payment-commands"
  namespace_id       = azurerm_servicebus_namespace.environment.id
  max_delivery_count = 10
}

resource "azurerm_servicebus_queue" "fulfilment_commands" {
  name               = "fulfilment-commands"
  namespace_id       = azurerm_servicebus_namespace.environment.id
  max_delivery_count = 10
}

resource "azurerm_servicebus_topic" "business_events" {
  name         = "business-events"
  namespace_id = azurerm_servicebus_namespace.environment.id
}

resource "azurerm_servicebus_subscription" "workflow_events" {
  name               = "workflow-events"
  topic_id           = azurerm_servicebus_topic.business_events.id
  max_delivery_count = 10
}

resource "azurerm_servicebus_subscription" "console_events" {
  name               = "lab-console-events"
  topic_id           = azurerm_servicebus_topic.business_events.id
  max_delivery_count = 10
}

resource "azurerm_postgresql_flexible_server" "environment" {
  name                          = "psql-${local.prefix}-${random_string.suffix.result}"
  resource_group_name           = azurerm_resource_group.environment.name
  location                      = azurerm_resource_group.environment.location
  version                       = "16"
  administrator_login           = "eventlab_admin"
  administrator_password        = random_password.postgres.result
  public_network_access_enabled = true
  sku_name                      = "B_Standard_B1ms"
  storage_mb                    = 32768
  backup_retention_days         = 7
  geo_redundant_backup_enabled  = false
  zone                          = "1"
  tags                          = local.tags

  authentication {
    active_directory_auth_enabled = false
    password_auth_enabled         = true
  }
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = "AllowAzureServices"
  server_id        = azurerm_postgresql_flexible_server.environment.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

resource "azurerm_postgresql_flexible_server_database" "service" {
  for_each  = local.database_names
  name      = each.value
  server_id = azurerm_postgresql_flexible_server.environment.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}
