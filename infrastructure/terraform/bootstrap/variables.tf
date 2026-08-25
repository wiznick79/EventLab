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

variable "github_owner_id" {
  description = "Immutable numeric GitHub organization or user ID used in the repository OIDC subject."
  type        = string
  default     = "49661706"
}

variable "github_repository_id" {
  description = "Immutable numeric GitHub repository ID used in the repository OIDC subject."
  type        = string
  default     = "1330006750"
}

variable "github_environment" {
  description = "Protected GitHub environment trusted by the Azure federated credential."
  type        = string
  default     = "azure"
}

variable "github_cleanup_environment" {
  description = "Unattended GitHub environment trusted only by scheduled expiry cleanup."
  type        = string
  default     = "azure-cleanup"
}

variable "state_allowed_ips" {
  description = "Administrative public IPs allowed to reach Terraform state. CI adds its short-lived runner IP for each run."
  type        = list(string)
  default     = []
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
