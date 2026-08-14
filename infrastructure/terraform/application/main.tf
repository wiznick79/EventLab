resource "random_string" "suffix" {
  length  = 6
  lower   = true
  numeric = true
  special = false
  upper   = false
}

resource "random_password" "postgres_admin" {
  length  = 32
  special = false
}

resource "random_password" "database_user" {
  for_each = local.database_names
  length   = 32
  special  = false
}

data "azurerm_resource_group" "environment" {
  name = var.environment_resource_group_name
}

resource "azurerm_log_analytics_workspace" "environment" {
  name                = "log-${local.prefix}"
  location            = data.azurerm_resource_group.environment.location
  resource_group_name = data.azurerm_resource_group.environment.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.tags
}

resource "azurerm_application_insights" "environment" {
  name                = "appi-${local.prefix}"
  location            = data.azurerm_resource_group.environment.location
  resource_group_name = data.azurerm_resource_group.environment.name
  workspace_id        = azurerm_log_analytics_workspace.environment.id
  application_type    = "web"
  retention_in_days   = 30
  tags                = local.tags
}

resource "azurerm_container_app_environment" "environment" {
  name                       = "cae-${local.prefix}"
  location                   = data.azurerm_resource_group.environment.location
  resource_group_name        = data.azurerm_resource_group.environment.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.environment.id
  infrastructure_subnet_id   = azurerm_subnet.container_apps.id
  tags                       = local.tags
}

resource "azurerm_virtual_network" "environment" {
  name                = "vnet-${local.prefix}"
  location            = data.azurerm_resource_group.environment.location
  resource_group_name = data.azurerm_resource_group.environment.name
  address_space       = ["10.42.0.0/16"]
  tags                = local.tags
}

resource "azurerm_subnet" "container_apps" {
  name                 = "snet-container-apps"
  resource_group_name  = data.azurerm_resource_group.environment.name
  virtual_network_name = azurerm_virtual_network.environment.name
  address_prefixes     = ["10.42.0.0/23"]

  delegation {
    name = "container-apps"
    service_delegation {
      name = "Microsoft.App/environments"
    }
  }
}

resource "azurerm_subnet" "postgres" {
  name                 = "snet-postgres"
  resource_group_name  = data.azurerm_resource_group.environment.name
  virtual_network_name = azurerm_virtual_network.environment.name
  address_prefixes     = ["10.42.2.0/24"]
  delegation {
    name = "postgres"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_private_dns_zone" "postgres" {
  name                = "${local.prefix}.postgres.database.azure.com"
  resource_group_name = data.azurerm_resource_group.environment.name
  tags                = local.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  name                  = "${local.prefix}-postgres"
  private_dns_zone_name = azurerm_private_dns_zone.postgres.name
  virtual_network_id    = azurerm_virtual_network.environment.id
  resource_group_name   = data.azurerm_resource_group.environment.name
  tags                  = local.tags
}

resource "azurerm_servicebus_namespace" "environment" {
  name                = "sb-${local.prefix}-${random_string.suffix.result}"
  location            = data.azurerm_resource_group.environment.location
  resource_group_name = data.azurerm_resource_group.environment.name
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
  resource_group_name           = data.azurerm_resource_group.environment.name
  location                      = data.azurerm_resource_group.environment.location
  version                       = "16"
  administrator_login           = "eventlab_admin"
  administrator_password        = random_password.postgres_admin.result
  delegated_subnet_id           = azurerm_subnet.postgres.id
  private_dns_zone_id           = azurerm_private_dns_zone.postgres.id
  public_network_access_enabled = false
  sku_name                      = "B_Standard_B1ms"
  storage_mb                    = 32768
  backup_retention_days         = 7
  geo_redundant_backup_enabled  = false
  zone                          = "1"
  tags                          = local.tags

  depends_on = [azurerm_private_dns_zone_virtual_network_link.postgres]

  authentication {
    active_directory_auth_enabled = false
    password_auth_enabled         = true
  }
}

resource "azurerm_postgresql_flexible_server_database" "service" {
  for_each  = local.database_names
  name      = each.value
  server_id = azurerm_postgresql_flexible_server.environment.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

resource "azurerm_container_app_job" "database_roles" {
  name                         = "database-roles-${var.environment_name}"
  location                     = data.azurerm_resource_group.environment.location
  resource_group_name          = data.azurerm_resource_group.environment.name
  container_app_environment_id = azurerm_container_app_environment.environment.id
  replica_timeout_in_seconds   = 300
  replica_retry_limit          = 2
  tags                         = local.tags

  manual_trigger_config {
    parallelism              = 1
    replica_completion_count = 1
  }

  secret {
    name  = "admin-password"
    value = random_password.postgres_admin.result
  }

  dynamic "secret" {
    for_each = random_password.database_user
    content {
      name  = "${secret.key}-password"
      value = secret.value.result
    }
  }

  template {
    container {
      name    = "database-roles"
      image   = "postgres:17.6-alpine@sha256:ef257d1a78fbc245feaeaf6b68a08a7f69aceacf250c4f8c67c8aef4dd4ccbb0"
      cpu     = 0.25
      memory  = "0.5Gi"
      command = ["sh", "-ceu"]
      args = [<<-EOT
        export PGPASSWORD="$ADMIN_PASSWORD"
        for SERVICE in workflow payment fulfilment console; do
          case "$SERVICE" in
            workflow) USER_NAME="workflow_app"; DATABASE="workflow_service"; USER_PASSWORD="$WORKFLOW_PASSWORD" ;;
            payment) USER_NAME="payment_app"; DATABASE="payment_service"; USER_PASSWORD="$PAYMENT_PASSWORD" ;;
            fulfilment) USER_NAME="fulfilment_app"; DATABASE="fulfilment_service"; USER_PASSWORD="$FULFILMENT_PASSWORD" ;;
            console) USER_NAME="console_app"; DATABASE="lab_console"; USER_PASSWORD="$CONSOLE_PASSWORD" ;;
          esac
          psql "host=$DATABASE_HOST dbname=postgres user=$DATABASE_ADMIN sslmode=require" \
            --set=ON_ERROR_STOP=1 \
            --command="DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$USER_NAME') THEN CREATE ROLE $USER_NAME LOGIN PASSWORD '$USER_PASSWORD'; ELSE ALTER ROLE $USER_NAME PASSWORD '$USER_PASSWORD'; END IF; END \$\$;"
          psql "host=$DATABASE_HOST dbname=postgres user=$DATABASE_ADMIN sslmode=require" \
            --set=ON_ERROR_STOP=1 --command="ALTER DATABASE $DATABASE OWNER TO $USER_NAME"
        done
      EOT
      ]
      env {
        name  = "DATABASE_HOST"
        value = azurerm_postgresql_flexible_server.environment.fqdn
      }
      env {
        name  = "DATABASE_ADMIN"
        value = azurerm_postgresql_flexible_server.environment.administrator_login
      }
      env {
        name        = "ADMIN_PASSWORD"
        secret_name = "admin-password"
      }
      dynamic "env" {
        for_each = random_password.database_user
        content {
          name        = "${upper(env.key)}_PASSWORD"
          secret_name = "${env.key}-password"
        }
      }
    }
  }

  depends_on = [azurerm_postgresql_flexible_server_database.service]
}
