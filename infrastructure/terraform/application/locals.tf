locals {
  prefix = "eventlab-${var.environment_name}"
  tags = merge(var.tags, {
    project       = "eventlab"
    environment   = var.environment_name
    lifecycle     = "ephemeral"
    destroy_after = var.destroy_after
    managedBy     = "terraform"
    imageTag      = var.image_tag
  })

  database_names = {
    workflow   = "workflow_service"
    payment    = "payment_service"
    fulfilment = "fulfilment_service"
    console    = "lab_console"
  }

  service_images = {
    workflow   = "${var.image_registry}/workflow-service:${var.image_tag}"
    payment    = "${var.image_registry}/payment-service:${var.image_tag}"
    fulfilment = "${var.image_registry}/fulfilment-service:${var.image_tag}"
    console    = "${var.image_registry}/lab-console:${var.image_tag}"
    frontend   = "${var.image_registry}/frontend:${var.image_tag}"
  }
}
