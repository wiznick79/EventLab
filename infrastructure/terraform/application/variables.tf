variable "subscription_id" {
  description = "Azure subscription for the disposable environment."
  type        = string
}

variable "location" {
  type    = string
  default = "westeurope"
}

variable "environment_name" {
  description = "Short lowercase environment identifier, such as demo or pr-42."
  type        = string
  default     = "demo"

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{1,14}$", var.environment_name))
    error_message = "environment_name must be 2-15 lowercase letters, numbers, or hyphens."
  }
}

variable "destroy_after" {
  description = "UTC RFC3339 expiry used by the scheduled cleanup workflow."
  type        = string

  validation {
    condition     = can(formatdate("YYYY-MM-DD'T'hh:mm:ssZ", var.destroy_after))
    error_message = "destroy_after must be an RFC3339 timestamp."
  }
}

variable "image_tag" {
  description = "Immutable Git commit SHA used for every EventLab image."
  type        = string

  validation {
    condition     = can(regex("^[0-9a-f]{40}$", var.image_tag))
    error_message = "image_tag must be a full 40-character Git commit SHA."
  }
}

variable "image_registry" {
  type    = string
  default = "ghcr.io/wiznick79/eventlab"
}

variable "tags" {
  type    = map(string)
  default = {}
}
