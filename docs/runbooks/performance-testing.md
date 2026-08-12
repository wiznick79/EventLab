# Performance and resilience testing

The Milestone 8 baseline uses k6 to apply concurrent work through the public Lab Console API. It treats latency as useful only when the resulting distributed workflow remains correct.

## Baseline acceptance criteria

The default local run starts ten happy-path workflows with up to five virtual users and three duplicate-delivery workflows. It must satisfy all of these conditions:

- fewer than 1% of HTTP requests fail;
- the 95th-percentile run-creation response is below one second;
- the 95th-percentile happy-path completion time is below 15 seconds;
- every happy-path workflow reaches `COMPLETED` exactly once;
- every duplicate scenario exposes two `payment.authorized` deliveries, exactly one duplicate marker and `DUPLICATE_IGNORED` decision, and exactly one completion.

These are development-laptop regression limits, not production capacity claims. Polling requests are included in the HTTP metrics, while workflow completion time measures the asynchronous path from accepted run to projected terminal event.

## Run locally

Start the Compose infrastructure and all four backend services with messaging enabled as described in the root README. Confirm `http://localhost:8080/actuator/health` reports `UP`, then run:

```powershell
docker compose --profile performance run --rm k6
```

The console prints the threshold result and `performance/results/baseline-summary.json` receives the full machine-readable k6 summary. Result files are intentionally ignored because hardware, background load, and warm-up state affect the numbers.

The workload can be adjusted without editing the script:

```powershell
$env:EVENTLAB_K6_HAPPY_ITERATIONS='30'
$env:EVENTLAB_K6_DUPLICATE_ITERATIONS='5'
docker compose --profile performance run --rm k6
```

Increasing these values is exploratory. The committed defaults remain small enough to repeat during development and strict enough to catch broken asynchronous completion or idempotency.

The first illustrative result is recorded in [the 2026-08-12 local baseline](../results/local-baseline-2026-08-12.md).

## Next resilience experiment

The next Milestone 8 increment will terminate a service after runs have been accepted, restart it, and require all in-flight workflows to recover through persisted outbox and inbox state without duplicate terminal transitions.
