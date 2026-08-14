locals {
  common_environment = {
    EVENTLAB_MESSAGING_ENABLED                    = "true"
    EVENTLAB_SERVICEBUS_CONNECTION_STRING         = ""
    EVENTLAB_SERVICEBUS_FULLY_QUALIFIED_NAMESPACE = "${azurerm_servicebus_namespace.environment.name}.servicebus.windows.net"
    APPLICATIONINSIGHTS_CONNECTION_STRING         = azurerm_application_insights.environment.connection_string
    OTEL_TRACES_ENDPOINT                          = "https://${azurerm_container_app.tempo.ingress[0].fqdn}/v1/traces"
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE    = "3"
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
      startup_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/readiness"
        initial_delay           = 5
        interval_seconds        = 10
        timeout                 = 2
        failure_count_threshold = 30
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
      startup_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/readiness"
        initial_delay           = 5
        interval_seconds        = 10
        timeout                 = 2
        failure_count_threshold = 30
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
      startup_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/readiness"
        initial_delay           = 5
        interval_seconds        = 10
        timeout                 = 2
        failure_count_threshold = 30
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
      env {
        name  = "PAYMENT_SERVICE_BASE_URL"
        value = "https://${azurerm_container_app.payment.ingress[0].fqdn}"
      }
      env {
        name  = "DEPLOYMENT_ENVIRONMENT"
        value = var.environment_name
      }
      env {
        name  = "DEPLOYMENT_VERSION"
        value = var.image_tag
      }
      env {
        name  = "DEPLOYMENT_EXPIRES_AT"
        value = var.destroy_after
      }
      dynamic "env" {
        for_each = local.common_environment
        content {
          name  = env.key
          value = env.value
        }
      }
      startup_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/readiness"
        initial_delay           = 5
        interval_seconds        = 10
        timeout                 = 2
        failure_count_threshold = 30
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

resource "azurerm_container_app" "tempo" {
  name                         = "tempo"
  container_app_environment_id = azurerm_container_app_environment.environment.id
  resource_group_name          = azurerm_resource_group.environment.name
  revision_mode                = "Single"
  tags                         = local.tags

  template {
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "tempo"
      image  = local.service_images.tempo
      cpu    = 0.25
      memory = "0.5Gi"
    }

    container {
      name   = "telemetry-gateway"
      image  = local.service_images.telemetry_gateway
      cpu    = 0.25
      memory = "0.5Gi"

      liveness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/ready"
        initial_delay           = 30
        failure_count_threshold = 10
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/ready"
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

resource "azurerm_container_app" "grafana" {
  name                         = "grafana"
  container_app_environment_id = azurerm_container_app_environment.environment.id
  resource_group_name          = azurerm_resource_group.environment.name
  revision_mode                = "Single"
  tags                         = local.tags

  template {
    min_replicas = 1
    max_replicas = 1
    container {
      name   = "grafana"
      image  = local.service_images.grafana
      cpu    = 0.25
      memory = "0.5Gi"
      env {
        name  = "TEMPO_URL"
        value = "https://${azurerm_container_app.tempo.ingress[0].fqdn}"
      }
      env {
        name  = "GF_AUTH_ANONYMOUS_ENABLED"
        value = "true"
      }
      env {
        name  = "GF_AUTH_ANONYMOUS_ORG_ROLE"
        value = "Viewer"
      }
      env {
        name  = "GF_USERS_VIEWERS_CAN_EDIT"
        value = "true"
      }
      env {
        name  = "GF_AUTH_DISABLE_LOGIN_FORM"
        value = "true"
      }
      liveness_probe {
        transport     = "HTTP"
        port          = 3000
        path          = "/api/health"
        initial_delay = 30
      }
      readiness_probe {
        transport = "HTTP"
        port      = 3000
        path      = "/api/health"
      }
    }
  }

  ingress {
    external_enabled           = true
    target_port                = 3000
    transport                  = "http"
    allow_insecure_connections = false
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
      env {
        name  = "GRAFANA_BASE_URL"
        value = "https://${azurerm_container_app.grafana.ingress[0].fqdn}"
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
