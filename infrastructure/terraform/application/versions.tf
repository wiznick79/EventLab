terraform {
  required_version = ">= 1.10.0"

  backend "azurerm" {}

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "azurerm" {
  subscription_id = var.subscription_id
  features {
    resource_group {
      # This root owns an explicitly disposable resource group. Azure may add
      # platform-managed children such as the Application Insights Smart
      # Detection action group, which must not prevent expiry cleanup.
      prevent_deletion_if_contains_resources = false
    }
  }
}
