locals {
  common_environment = {
    EVENTLAB_MESSAGING_ENABLED                    = "true"
    EVENTLAB_SERVICEBUS_CONNECTION_STRING         = ""
    EVENTLAB_SERVICEBUS_FULLY_QUALIFIED_NAMESPACE = "${azurerm_servicebus_namespace.environment.name}.servicebus.windows.net"
    APPLICATIONINSIGHTS_CONNECTION_STRING         = azurerm_application_insights.environment.connection_string
  }
}

resource "azurerm_container_app" "workflow" {
  name                         = "workflow-service"
  container_app_environment_id = azurerm_container_app_environment.environment.id
  resource_group_name          = azurerm_resource_group.environment.name
  revision_mode                = "Single"
  tags                         = local.tags

  identity { type = "SystemAssigned" }
  secret {
    name  = "database-password"
    value = random_password.postgres.result
  }

  template {
    min_replicas = 1
    max_replicas = 1
    container {
      name   = "workflow-service"
      image  = local.service_images.workflow
      cpu    = 0.5
      memory = "1Gi"
      env {
        name  = "SERVER_PORT"
        value = "8080"
      }
      env {
        name  = "WORKFLOW_DATABASE_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.environment.fqdn}:5432/${local.database_names.workflow}?sslmode=require"
      }
      env {
        name  = "WORKFLOW_DATABASE_USER"
        value = azurerm_postgresql_flexible_server.environment.administrator_login
      }
      env {
        name        = "WORKFLOW_DATABASE_PASSWORD"
        secret_name = "database-password"
      }
      dynamic "env" {
        for_each = local.common_environment
        content {
          name  = env.key
          value = env.value
        }
      }
      liveness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/liveness"
        initial_delay           = 60
        interval_seconds        = 30
        failure_count_threshold = 10
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/readiness"
      }
    }
  }

  ingress {
    external_enabled = false
    target_port      = 8080
    transport        = "http"
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}

resource "azurerm_container_app" "payment" {
  name                         = "payment-service"
  container_app_environment_id = azurerm_container_app_environment.environment.id
  resource_group_name          = azurerm_resource_group.environment.name
  revision_mode                = "Single"
  tags                         = local.tags
  identity { type = "SystemAssigned" }
  secret {
    name  = "database-password"
    value = random_password.postgres.result
  }
  template {
    min_replicas = 1
    max_replicas = 1
    container {
      name   = "payment-service"
      image  = local.service_images.payment
      cpu    = 0.5
      memory = "1Gi"
      env {
        name  = "SERVER_PORT"
        value = "8080"
      }
      env {
        name  = "PAYMENT_DATABASE_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.environment.fqdn}:5432/${local.database_names.payment}?sslmode=require"
      }
      env {
        name  = "PAYMENT_DATABASE_USER"
        value = azurerm_postgresql_flexible_server.environment.administrator_login
      }
      env {
        name        = "PAYMENT_DATABASE_PASSWORD"
        secret_name = "database-password"
      }
      dynamic "env" {
        for_each = local.common_environment
        content {
          name  = env.key
          value = env.value
        }
      }
      liveness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/liveness"
        initial_delay           = 60
        interval_seconds        = 30
        failure_count_threshold = 10
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/readiness"
      }
    }
  }
}

resource "azurerm_container_app" "fulfilment" {
  name                         = "fulfilment-service"
  container_app_environment_id = azurerm_container_app_environment.environment.id
  resource_group_name          = azurerm_resource_group.environment.name
  revision_mode                = "Single"
  tags                         = local.tags
  identity { type = "SystemAssigned" }
  secret {
    name  = "database-password"
    value = random_password.postgres.result
  }
  template {
    min_replicas = 1
    max_replicas = 1
    container {
      name   = "fulfilment-service"
      image  = local.service_images.fulfilment
      cpu    = 0.5
      memory = "1Gi"
      env {
        name  = "SERVER_PORT"
        value = "8080"
      }
      env {
        name  = "FULFILMENT_DATABASE_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.environment.fqdn}:5432/${local.database_names.fulfilment}?sslmode=require"
      }
      env {
        name  = "FULFILMENT_DATABASE_USER"
        value = azurerm_postgresql_flexible_server.environment.administrator_login
      }
      env {
        name        = "FULFILMENT_DATABASE_PASSWORD"
        secret_name = "database-password"
      }
      dynamic "env" {
        for_each = local.common_environment
        content {
          name  = env.key
          value = env.value
        }
      }
      liveness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/liveness"
        initial_delay           = 60
        interval_seconds        = 30
        failure_count_threshold = 10
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/readiness"
      }
    }
  }
  ingress {
    external_enabled = false
    target_port      = 8080
    transport        = "http"
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}

resource "azurerm_container_app" "console" {
  name                         = "lab-console"
  container_app_environment_id = azurerm_container_app_environment.environment.id
  resource_group_name          = azurerm_resource_group.environment.name
  revision_mode                = "Single"
  tags                         = local.tags
  identity { type = "SystemAssigned" }
  secret {
    name  = "database-password"
    value = random_password.postgres.result
  }
  template {
    min_replicas = 1
    max_replicas = 1
    container {
      name   = "lab-console"
      image  = local.service_images.console
      cpu    = 0.5
      memory = "1Gi"
      env {
        name  = "SERVER_PORT"
        value = "8080"
      }
      env {
        name  = "LAB_CONSOLE_DATABASE_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.environment.fqdn}:5432/${local.database_names.console}?sslmode=require"
      }
      env {
        name  = "LAB_CONSOLE_DATABASE_USER"
        value = azurerm_postgresql_flexible_server.environment.administrator_login
      }
      env {
        name        = "LAB_CONSOLE_DATABASE_PASSWORD"
        secret_name = "database-password"
      }
      env {
        name  = "WORKFLOW_SERVICE_BASE_URL"
        value = "https://${azurerm_container_app.workflow.ingress[0].fqdn}"
      }
      env {
        name  = "FULFILMENT_SERVICE_BASE_URL"
        value = "https://${azurerm_container_app.fulfilment.ingress[0].fqdn}"
      }
      dynamic "env" {
        for_each = local.common_environment
        content {
          name  = env.key
          value = env.value
        }
      }
      liveness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/liveness"
        initial_delay           = 60
        interval_seconds        = 30
        failure_count_threshold = 10
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/readiness"
      }
    }
  }
  ingress {
    external_enabled = false
    target_port      = 8080
    transport        = "http"
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}

resource "azurerm_container_app" "frontend" {
  name                         = "eventlab"
  container_app_environment_id = azurerm_container_app_environment.environment.id
  resource_group_name          = azurerm_resource_group.environment.name
  revision_mode                = "Single"
  tags                         = local.tags
  template {
    min_replicas = 1
    max_replicas = 1
    container {
      name   = "frontend"
      image  = local.service_images.frontend
      cpu    = 0.25
      memory = "0.5Gi"
      env {
        name  = "LAB_CONSOLE_HOST"
        value = azurerm_container_app.console.ingress[0].fqdn
      }
      liveness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/"
      }
    }
  }
  ingress {
    external_enabled           = true
    target_port                = 8080
    transport                  = "http"
    allow_insecure_connections = false
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}

resource "azurerm_role_assignment" "service_bus_data_owner" {
  for_each = {
    workflow   = azurerm_container_app.workflow.identity[0].principal_id
    payment    = azurerm_container_app.payment.identity[0].principal_id
    fulfilment = azurerm_container_app.fulfilment.identity[0].principal_id
    console    = azurerm_container_app.console.identity[0].principal_id
  }
  scope                = azurerm_servicebus_namespace.environment.id
  role_definition_name = "Azure Service Bus Data Owner"
  principal_id         = each.value
  principal_type       = "ServicePrincipal"
}
