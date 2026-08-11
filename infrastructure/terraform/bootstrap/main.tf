locals {
  github_subject = "repo:${var.github_owner}/${var.github_repository}:environment:${var.github_environment}"
}

resource "random_string" "state_suffix" {
  length  = 8
  lower   = true
  numeric = true
  special = false
  upper   = false
}

resource "azurerm_resource_group" "bootstrap" {
  name     = "rg-eventlab-bootstrap"
  location = var.location
  tags     = var.tags
}

resource "azurerm_storage_account" "state" {
  name                            = "steventlab${random_string.state_suffix.result}"
  resource_group_name             = azurerm_resource_group.bootstrap.name
  location                        = azurerm_resource_group.bootstrap.location
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  min_tls_version                 = "TLS1_2"
  allow_nested_items_to_be_public = false
  shared_access_key_enabled       = true
  tags                            = var.tags

  blob_properties {
    versioning_enabled = true

    delete_retention_policy {
      days = 7
    }

    container_delete_retention_policy {
      days = 7
    }
  }
}

resource "azurerm_storage_container" "state" {
  name                  = "tfstate"
  storage_account_id    = azurerm_storage_account.state.id
  container_access_type = "private"
}

resource "azuread_application" "github" {
  display_name     = "eventlab-github-actions"
  sign_in_audience = "AzureADMyOrg"
}

resource "azuread_service_principal" "github" {
  client_id = azuread_application.github.client_id
}

resource "azuread_application_federated_identity_credential" "github_environment" {
  application_id = azuread_application.github.id
  display_name   = "github-${var.github_owner}-${var.github_repository}-${var.github_environment}"
  description    = "GitHub Actions OIDC for the protected EventLab Azure environment"
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = local.github_subject
}

resource "azurerm_role_assignment" "github_contributor" {
  scope                = "/subscriptions/${var.subscription_id}"
  role_definition_name = "Contributor"
  principal_id         = azuread_service_principal.github.object_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "github_rbac_administrator" {
  scope                = "/subscriptions/${var.subscription_id}"
  role_definition_name = "Role Based Access Control Administrator"
  principal_id         = azuread_service_principal.github.object_id
  principal_type       = "ServicePrincipal"
}
