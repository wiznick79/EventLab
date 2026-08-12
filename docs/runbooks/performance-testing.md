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

## Interactive Load & Concurrency Lab

The live UI now provides a browser-driven pressure experiment in addition to k6. Choose 10, 25, 50, or 100 workflows locally, select burst or steady arrival, and choose the percentage that should receive a duplicate payment result. Public Azure environments expose only the 10- and 25-workflow presets.

The Lab Console returns immediately with a durable load-experiment ID, launches the members asynchronously, and refreshes the aggregate report once per second. Read the metrics together:

- **accepted / requested** identifies launch failures;
- **terminal / accepted** and **current backlog** show whether accepted work drains;
- **max in flight** proves actual overlap rather than a fast sequential loop;
- **throughput, median, and p95** describe this environment's observed performance;
- **duplicates observed** proves the selected pressure mix reached the consumer;
- **invariant violations** is the correctness gate, derived from each member's backend evidence report.

The first progress bar measures workflow-creation responses and the second measures accepted workflows reaching terminal state. During a burst, accepted work is persisted member by member: the launch count can still be increasing while the processing backlog already contains work. This distinction prevents request-acceptance time from being mistaken for broker-processing latency.

`PROVED` means every accepted member became terminal, every member evidence report passed, and every requested launch was accepted. These bounded results are portfolio evidence and regression feedback, not a production capacity claim. Continue to use k6 for repeatable scripted baselines.

The first illustrative result is recorded in [the 2026-08-12 local baseline](../results/local-baseline-2026-08-12.md).

## Next resilience experiment

The restart experiment deliberately terminates the locally running Payment JVM, accepts workflows while no payment consumer is available, restarts Payment, and checks every timeline for one payment event and one terminal completion:

```powershell
.\scripts\verify-payment-restart-recovery.ps1
```

The script refuses to stop port 8082 unless its owner is a Java command running the EventLab payment-service JAR. Its `finally` block restores Payment if an assertion or API call fails. The interruption happens before workflow creation so the experiment deterministically proves broker buffering plus post-restart consumption. A later experiment can target the narrower crash window between broker acceptance and outbox publication.

The first successful execution is recorded in [the 2026-08-12 Payment restart result](../results/payment-restart-recovery-2026-08-12.md).

## Outbox acknowledgement-window experiment

The narrow-window experiment enables a one-shot Payment fault after `transport.send` returns but before `markPublished` records the acknowledgement:

```powershell
.\mvnw.cmd -B -ntp package -DskipTests
.\scripts\verify-outbox-acknowledgement-window.ps1
```

The first dispatch therefore reaches Service Bus and is recorded as failed locally. The next scheduled dispatch sends the same logical event again. The script requires two broker deliveries with one event ID, one duplicate marker and `DUPLICATE_IGNORED` decision, and one workflow completion. The fault property defaults to false and is enabled only on the temporary Payment process started by this experiment; the script restores the ordinary process afterward.

The first successful execution is recorded in [the 2026-08-12 acknowledgement-window result](../results/outbox-acknowledgement-window-2026-08-12.md).
