variable "subscription_id" {
  description = "Azure subscription that owns the persistent bootstrap resources."
  type        = string
}

variable "tenant_id" {
  description = "Microsoft Entra tenant used for the GitHub Actions workload identity."
  type        = string
}

variable "location" {
  description = "Azure region for the persistent Terraform state storage."
  type        = string
  default     = "francecentral"
}

variable "github_owner" {
  description = "GitHub organization or user that owns the repository."
  type        = string
  default     = "wiznick79"
}

variable "github_repository" {
  description = "GitHub repository name without its owner."
  type        = string
  default     = "EventLab"
}

variable "github_environment" {
  description = "Protected GitHub environment trusted by the Azure federated credential."
  type        = string
  default     = "azure"
}

variable "tags" {
  description = "Tags applied to persistent bootstrap resources."
  type        = map(string)
  default = {
    project   = "eventlab"
    lifecycle = "persistent"
    managedBy = "terraform"
  }
}
