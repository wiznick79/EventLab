# ADR-010: compose bounded immutable experiment plans

**Status:** Accepted

## Decision

EventLab exposes a Scenario Builder that composes two deliberately bounded controls: one or two deliveries of the same logical payment result, and one of four deterministic Fulfilment behaviors. Each submitted plan receives an immutable experiment-plan ID and is scoped to one workflow run.

The canonical plan travels through the ordinary workflow messages. Payment and Fulfilment interpret only their own part of that plan; the Lab Console does not simulate business outcomes. The same Service Bus queues, service databases, inboxes, outboxes, saga handlers, and telemetry used by the curated presets execute custom experiments.

## Why bounded composition

The builder proves the deployed system is interactive and permits combinations that no preset previously demonstrated, while retaining deterministic results that can be explained and tested. A general-purpose chaos scripting language would require unsafe arbitrary controls, a much larger validation surface, and claims that are difficult to reproduce.

## Evidence

The UI declares the expected invariant before execution and compares it with the live projected timeline. The run and plan IDs connect that claim to durable state and traces. `scripts/verify-custom-experiment.ps1` executes the cross-product case of duplicate payment delivery plus business rejection and requires one ignored duplicate, one compensated terminal state, and no completion.
