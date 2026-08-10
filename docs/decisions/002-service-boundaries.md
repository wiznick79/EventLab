# ADR-002: Initial service boundaries

**Status:** Accepted

## Context

Too few participants make compensation artificial; too many create deployment and cognitive overhead before the visualization works.

## Decision

Use four backend deployables: Workflow Service, Payment Service, Fulfilment Service, and Lab Console. Workflow owns an orchestration saga. Fulfilment initially combines inventory reservation and shipment scheduling.

## Consequences

- Three business services are enough for credible forward and compensating paths.
- Lab Console separates experiment control and presentation from business ownership.
- Inventory and Shipment split only for a later named scenario that benefits from it.
