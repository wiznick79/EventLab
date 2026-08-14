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
    workflow          = "${var.image_registry}/workflow-service@${var.image_digests.workflow}"
    payment           = "${var.image_registry}/payment-service@${var.image_digests.payment}"
    fulfilment        = "${var.image_registry}/fulfilment-service@${var.image_digests.fulfilment}"
    console           = "${var.image_registry}/lab-console@${var.image_digests.console}"
    frontend          = "${var.image_registry}/frontend@${var.image_digests.frontend}"
    tempo             = "${var.image_registry}/frontend@${var.image_digests.tempo}"
    telemetry_gateway = "${var.image_registry}/frontend@${var.image_digests.telemetry_gateway}"
    grafana           = "${var.image_registry}/frontend@${var.image_digests.grafana}"
  }

  database_users = {
    workflow   = "workflow_app"
    payment    = "payment_app"
    fulfilment = "fulfilment_app"
    console    = "console_app"
  }
}
