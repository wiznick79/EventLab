locals {
  # Repositories created after GitHub's immutable-subject rollout use numeric
  # owner and repository IDs in default OIDC subjects. Keep the human-readable
  # names as well because they are also part of GitHub's emitted claim.
  github_subject_prefix  = "repo:${var.github_owner}@${var.github_owner_id}/${var.github_repository}@${var.github_repository_id}"
  github_subject         = "${local.github_subject_prefix}:environment:${var.github_environment}"
  github_cleanup_subject = "${local.github_subject_prefix}:environment:${var.github_cleanup_environment}"
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

  lifecycle {
    # Resource-group location is metadata; preserving an existing group avoids
    # destructive replacement when subscription policy changes allowed regions.
    ignore_changes = [location]
  }
}

resource "azurerm_resource_group" "environments" {
  name     = "rg-eventlab-environments"
  location = var.location
  tags = merge(var.tags, {
    purpose = "ephemeral-environment-boundary"
  })
}

resource "azurerm_storage_account" "state" {
  name                            = "steventlab${random_string.state_suffix.result}"
  resource_group_name             = azurerm_resource_group.bootstrap.name
  location                        = var.location
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  min_tls_version                 = "TLS1_2"
  allow_nested_items_to_be_public = false
  shared_access_key_enabled       = false
  public_network_access_enabled   = true
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


  network_rules {
    default_action = "Deny"
    bypass         = ["AzureServices"]
    ip_rules       = var.state_allowed_ips
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
  description    = "GitHub Actions OIDC for the protected EventLab Azure environment using immutable repository identity"
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = local.github_subject
}

resource "azuread_application_federated_identity_credential" "github_cleanup_environment" {
  application_id = azuread_application.github.id
  display_name   = "github-${var.github_owner}-${var.github_repository}-${var.github_cleanup_environment}"
  description    = "GitHub Actions OIDC for unattended EventLab expiry cleanup using immutable repository identity"
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = local.github_cleanup_subject
}

resource "azurerm_role_assignment" "github_contributor" {
  scope                = azurerm_resource_group.environments.id
  role_definition_name = "Contributor"
  principal_id         = azuread_service_principal.github.object_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "github_rbac_administrator" {
  scope                = azurerm_resource_group.environments.id
  role_definition_name = "Role Based Access Control Administrator"
  principal_id         = azuread_service_principal.github.object_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "github_state_blob_data" {
  scope                = azurerm_storage_account.state.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azuread_service_principal.github.object_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "github_state_account" {
  scope                = azurerm_storage_account.state.id
  role_definition_name = "Storage Account Contributor"
  principal_id         = azuread_service_principal.github.object_id
  principal_type       = "ServicePrincipal"
}
