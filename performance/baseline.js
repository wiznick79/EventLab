import http from 'k6/http'
import { check, fail, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:38080'
const completionTimeoutSeconds = Number(__ENV.COMPLETION_TIMEOUT_SECONDS || 20)
const invariantPass = new Rate('business_invariant_pass')
const completionDuration = new Trend('workflow_completion_duration', true)

export const options = {
  scenarios: {
    happy_path: {
      executor: 'shared-iterations',
      exec: 'happyPath',
      vus: Math.min(Number(__ENV.HAPPY_ITERATIONS || 10), 5),
      iterations: Number(__ENV.HAPPY_ITERATIONS || 10),
      maxDuration: '2m',
      tags: { scenario: 'happy' },
    },
    duplicate_delivery: {
      executor: 'shared-iterations',
      exec: 'duplicateDelivery',
      vus: 1,
      iterations: Number(__ENV.DUPLICATE_ITERATIONS || 3),
      startTime: '2s',
      maxDuration: '2m',
      tags: { scenario: 'duplicate' },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:start-run}': ['p(95)<1000'],
    'workflow_completion_duration{test_case:happy}': ['p(95)<15000'],
    'business_invariant_pass{test_case:happy}': ['rate==1'],
    'business_invariant_pass{test_case:duplicate}': ['rate==1'],
  },
}

function startRun(scenarioId) {
  const response = http.post(`${baseUrl}/api/v1/runs`, JSON.stringify({
    scenarioId,
    amount: 129.90,
    currency: 'EUR',
  }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'start-run' },
  })

  if (!check(response, { 'run accepted': (result) => result.status === 200 })) {
    fail(`Could not start ${scenarioId}: HTTP ${response.status}`)
  }
  return response.json('workflowId')
}

function awaitTimeline(workflowId, terminalState, testCase) {
  const startedAt = Date.now()
  let timeline = []

  while ((Date.now() - startedAt) / 1000 < completionTimeoutSeconds) {
    const response = http.get(`${baseUrl}/api/v1/runs/${workflowId}/timeline`, {
      tags: { name: 'poll-timeline' },
    })
    if (response.status === 200) {
      timeline = response.json()
      if (timeline.some((event) => event.state === terminalState)) {
        completionDuration.add(Date.now() - startedAt, { test_case: testCase })
        return timeline
      }
    }
    sleep(0.25)
  }

  completionDuration.add(Date.now() - startedAt, { test_case: testCase })
  return timeline
}

function count(timeline, predicate) {
  return timeline.filter(predicate).length
}

export function happyPath() {
  const timeline = awaitTimeline(startRun('happy-path'), 'COMPLETED', 'happy')
  const valid = count(timeline, (event) => event.state === 'COMPLETED') === 1
  invariantPass.add(valid, { test_case: 'happy' })
  check(timeline, { 'happy path completes exactly once': () => valid })
}

export function duplicateDelivery() {
  const timeline = awaitTimeline(startRun('duplicate-payment-result'), 'COMPLETED', 'duplicate')
  const valid = count(timeline, (event) => event.state === 'COMPLETED') === 1
    && count(timeline, (event) => event.eventType === 'payment.authorized') === 2
    && count(timeline, (event) => event.duplicateDelivery) === 1
    && count(timeline, (event) => event.state === 'DUPLICATE_IGNORED') === 1
  invariantPass.add(valid, { test_case: 'duplicate' })
  check(timeline, { 'duplicate is visible and changes state once': () => valid })
}

export function handleSummary(data) {
  return {
    '/results/baseline-summary.json': JSON.stringify(data, null, 2),
  }
}
