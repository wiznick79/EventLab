variable "subscription_id" {
  description = "Azure subscription for the disposable environment."
  type        = string
}

variable "location" {
  type    = string
  default = "francecentral"
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

variable "image_digests" {
  description = "Registry digests for every deployed image. Tags identify provenance; digests make the deployment immutable."
  type        = map(string)
  default = {
    workflow          = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    payment           = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    fulfilment        = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    console           = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    frontend          = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    tempo             = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    telemetry_gateway = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    grafana           = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
  }

  validation {
    condition = alltrue([
      for name in ["workflow", "payment", "fulfilment", "console", "frontend", "tempo", "telemetry_gateway", "grafana"] :
      can(regex("^sha256:[0-9a-f]{64}$", lookup(var.image_digests, name, "")))
    ])
    error_message = "image_digests must contain a sha256 digest for all eight deployable images."
  }
}

variable "environment_resource_group_name" {
  description = "Persistent least-privilege boundary created by the bootstrap stack."
  type        = string
  default     = "rg-eventlab-environments"
}

variable "tags" {
  type    = map(string)
  default = {}
}
