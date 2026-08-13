import { useEffect, useRef, useState } from 'react'
import { PortfolioTour } from './PortfolioTour'
import { DemoRecording } from './DemoRecording'

type TimelineEvent = {
  sequence: number
  eventId: string
  workflowId: string
  eventType: string
  service: string
  state: string
  description: string
  occurredAt: string
  traceId?: string
  duplicateDelivery: boolean
  payload?: Record<string, unknown>
}

type RunResponse = { workflowId: string; experimentPlanId: string; state: string }

type TraceEvidence = { span: string; decision: string }
type FulfilmentBehavior = 'SUCCESS' | 'TEMPORARY_UNAVAILABLE' | 'BUSINESS_REJECTION' | 'STALE_AFTER_SUCCESS' | 'UNSUPPORTED_CONTRACT'
type RecoveryMode = 'MANUAL' | 'AUTOMATIC'
type ExperimentPlan = { paymentResultDeliveries: number; fulfilmentBehavior: FulfilmentBehavior; fulfilmentMaxAttempts: number; recoveryMode: RecoveryMode }
type RunSummary = RunResponse & {
  scenarioId: string
  experimentPlan: ExperimentPlan
  expectedInvariant: string
  createdAt: string
}
type RunDetails = RunSummary & { timeline: TimelineEvent[] }
type EvidenceCheck = { id: string; label: string; status: 'PROVED' | 'IN_PROGRESS' | 'FAILED'; observation: string; traceIds: string[] }
type EvidenceReport = { assessment: EvidenceCheck['status']; generatedAt: string; checks: EvidenceCheck[] }
type RunConsistency = { status: 'IN_FLIGHT' | 'CONSISTENT' | 'CATCHING_UP' | 'PROJECTION_BEHIND' | 'SOURCE_UNAVAILABLE'; authoritativeState?: string; projectedState: string; lagSeconds: number; explanation: string }
type DeadLetterInspection = {
  workflowId: string
  status: 'FOUND' | 'NOT_FOUND' | 'UNAVAILABLE'
  queue: string
  messageId?: string
  subject?: string
  deadLetterReason?: string
  errorDescription?: string
  deliveryCount?: number
  sequenceNumber?: number
  enqueuedAt?: string
  schemaVersion?: number
  replayAllowed: boolean
  operatorGuidance: string
}
type DeploymentStatus = {
  environment: string
  version: string
  expiresAt?: string
  mode: 'ONLINE' | 'READ_ONLY' | 'EXPIRED'
  acceptingExperiments: boolean
  evidencePipeline?: { enabled: boolean; status: 'STARTING' | 'RUNNING' | 'DISABLED' | 'ERROR'; lastEventAt?: string; lastError?: string }
  dependencies: { name: string; status: 'UP' | 'DOWN' }[]
}
type LoadExperiment = {
  id: string
  status: 'LAUNCHING' | 'RUNNING' | 'PROVED' | 'FAILED'
  statusReason?: 'LAUNCH_INTERRUPTED' | 'EVIDENCE_FAILED'
  trafficPattern: 'BURST' | 'STEADY'
  consumerConcurrency: 1 | 4 | 8
  requestedWorkflows: number
  processedLaunches: number
  pendingLaunches: number
  acceptedWorkflows: number
  launchFailures: number
  duplicatePercentage: number
  paymentObservedWorkflows: number
  fulfilmentObservedWorkflows: number
  terminalWorkflows: number
  provedWorkflows: number
  invariantViolations: number
  duplicateDeliveries: number
  backlog: number
  maxInFlight: number
  throughputPerSecond: number
  medianLatencyMillis: number
  p95LatencyMillis: number
  launchDurationMillis: number
  firstPaymentDelayMillis: number
  lastPaymentDelayMillis: number
  firstFulfilmentQueuedDelayMillis: number
  lastFulfilmentQueuedDelayMillis: number
  firstFulfilmentDelayMillis: number
  lastFulfilmentDelayMillis: number
  firstTerminalDelayMillis: number
  drainDurationMillis: number
  brokerPressure: {
    available: boolean
    status: string
    paymentCommands: { current: number; peak: number }
    workflowEvents: { current: number; peak: number }
    fulfilmentCommands: { current: number; peak: number }
    evidenceEvents: { current: number; peak: number }
  }
  createdAt: string
  completedAt?: string
  workflowIds: string[]
}
type ConcurrencyProfile = 1 | 4 | 8

function median(values: number[]) {
  if (values.length === 0) return 0
  const sorted = [...values].sort((left, right) => left - right)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

function comparisonStatistics(runs: LoadExperiment[]) {
  const values = runs.map((item) => item.throughputPerSecond)
  if (values.length === 0) return { minimum: 0, median: 0, maximum: 0, spread: 0, stable: false }
  const middle = median(values)
  const minimum = Math.min(...values)
  const maximum = Math.max(...values)
  const spread = middle === 0 ? 0 : (maximum - minimum) / middle * 100
  return { minimum, median: middle, maximum, spread, stable: values.length >= 3 && spread <= 25 }
}

export function formatRemaining(expiresAt: string | undefined, now = Date.now()) {
  if (!expiresAt) return 'No automatic expiry'
  const remaining = Math.max(0, Date.parse(expiresAt) - now)
  const hours = Math.floor(remaining / 3_600_000)
  const minutes = Math.floor((remaining % 3_600_000) / 60_000)
  const seconds = Math.floor((remaining % 60_000) / 1000)
  return remaining === 0 ? 'Expired' : `${hours}h ${minutes}m ${seconds}s remaining`
}

export function presetPlan(scenarioId: string): ExperimentPlan {
  return {
    'duplicate-payment-result': { paymentResultDeliveries: 2, fulfilmentBehavior: 'SUCCESS' as FulfilmentBehavior, fulfilmentMaxAttempts: 4, recoveryMode: 'MANUAL' as RecoveryMode },
    'fulfilment-unavailable': { paymentResultDeliveries: 1, fulfilmentBehavior: 'TEMPORARY_UNAVAILABLE' as FulfilmentBehavior, fulfilmentMaxAttempts: 4, recoveryMode: 'MANUAL' as RecoveryMode },
    'fulfilment-rejected': { paymentResultDeliveries: 1, fulfilmentBehavior: 'BUSINESS_REJECTION' as FulfilmentBehavior, fulfilmentMaxAttempts: 4, recoveryMode: 'MANUAL' as RecoveryMode },
    'out-of-order-event': { paymentResultDeliveries: 1, fulfilmentBehavior: 'STALE_AFTER_SUCCESS' as FulfilmentBehavior, fulfilmentMaxAttempts: 4, recoveryMode: 'MANUAL' as RecoveryMode },
  }[scenarioId] ?? { paymentResultDeliveries: 1, fulfilmentBehavior: 'SUCCESS', fulfilmentMaxAttempts: 4, recoveryMode: 'MANUAL' }
}

export function expectedInvariant(plan: ExperimentPlan) {
  const duplicate = plan.paymentResultDeliveries === 2
    ? 'Two payment-result deliveries produce one payment state change; '
    : ''
  const outcome = {
    SUCCESS: 'the workflow completes exactly once.',
    TEMPORARY_UNAVAILABLE: `the command reaches the DLQ after ${plan.fulfilmentMaxAttempts} attempts and completes once after ${plan.recoveryMode === 'AUTOMATIC' ? 'automatic' : 'guarded manual'} recovery.`,
    BUSINESS_REJECTION: 'the payment is compensated and the workflow ends COMPENSATED.',
    STALE_AFTER_SUCCESS: 'the workflow remains COMPLETED after the stale update.',
    UNSUPPORTED_CONTRACT: 'the unsupported contract is rejected 3 times, dead-lettered, and never completes fulfilment.',
  }[plan.fulfilmentBehavior]
  return duplicate + outcome
}

const services = [
  ['Workflow', 'Orchestrator'],
  ['Payment', 'Participant'],
  ['Fulfilment', 'Participant'],
  ['Lab Console', 'Control plane'],
]

export function grafanaTraceUrl(traceId: string) {
  const left = {
    range: { from: 'now-1h', to: 'now' },
    datasource: 'tempo',
    queries: [{ query: traceId, queryType: 'traceql' }],
  }

  return `http://localhost:3000/explore?left=${encodeURIComponent(JSON.stringify(left))}`
}

export function traceUrl(
  traceId: string,
  grafanaBaseUrl = window.EVENTLAB_CONFIG?.grafanaBaseUrl,
) {
  if (!grafanaBaseUrl) return grafanaTraceUrl(traceId)
  const left = {
    range: { from: 'now-1h', to: 'now' },
    datasource: 'tempo',
    queries: [{ query: traceId, queryType: 'traceql' }],
  }

  return `${grafanaBaseUrl}/explore?left=${encodeURIComponent(JSON.stringify(left))}`
}

export function grafanaDashboardUrl(
  grafanaBaseUrl = window.EVENTLAB_CONFIG?.grafanaBaseUrl ?? 'http://localhost:3000',
) {
  return `${grafanaBaseUrl}/d/eventlab-operations/eventlab-operations?from=now-1h&to=now`
}

export function traceEvidence(state: string): TraceEvidence | undefined {
  switch (state) {
    case 'DUPLICATE_IGNORED':
      return { span: 'eventlab.workflow.inbox.decision', decision: 'DUPLICATE_IGNORED' }
    case 'STALE_IGNORED':
      return { span: 'eventlab.workflow.version.decision', decision: 'STALE_IGNORED' }
    case 'RETRY_SCHEDULED':
      return { span: 'eventlab.fulfilment.attempt.decision', decision: 'RETRY_SCHEDULED' }
    case 'DEAD_LETTERED':
      return { span: 'eventlab.fulfilment.attempt.decision', decision: 'DEAD_LETTERED' }
    case 'MESSAGE_REJECTED':
      return { span: 'eventlab.fulfilment.contract.decision', decision: 'UNSUPPORTED_CONTRACT_REJECTED' }
    case 'POISON_DEAD_LETTERED':
      return { span: 'eventlab.fulfilment.contract.decision', decision: 'UNSUPPORTED_CONTRACT_DEAD_LETTERED' }
    case 'RECOVERY_REQUESTED':
      return { span: 'eventlab.fulfilment.recovery.decision', decision: 'RECOVERY_ACCEPTED' }
    case 'PAYMENT_COMPENSATED':
      return { span: 'eventlab.payment.compensation.decision', decision: 'PAYMENT_COMPENSATED' }
    default:
      return undefined
  }
}

export function App() {
  const parameters = new URLSearchParams(window.location.search)
  const recording = parameters.get('recording')
  if (recording) return <DemoRecording scenario={recording} step={Number(parameters.get('step') ?? '1')} />

  const staticTour = import.meta.env.VITE_STATIC_TOUR === 'true'
    || parameters.has('tour')
  if (staticTour) return <PortfolioTour />

  const [run, setRun] = useState<RunResponse | null>(null)
  const [events, setEvents] = useState<TimelineEvent[]>([])
  const [starting, setStarting] = useState(false)
  const [recovering, setRecovering] = useState(false)
  const [error, setError] = useState('')
  const [activeScenario, setActiveScenario] = useState('')
  const [activePlan, setActivePlan] = useState<ExperimentPlan | null>(null)
  const [launchSequence, setLaunchSequence] = useState(0)
  const [recentRuns, setRecentRuns] = useState<RunSummary[]>([])
  const [comparison, setComparison] = useState<[string, string]>(['', ''])
  const [copied, setCopied] = useState(false)
  const [evidenceReport, setEvidenceReport] = useState<EvidenceReport | null>(null)
  const [runConsistency, setRunConsistency] = useState<RunConsistency | null>(null)
  const [deadLetterInspection, setDeadLetterInspection] = useState<DeadLetterInspection | null>(null)
  const [deployment, setDeployment] = useState<DeploymentStatus | null>(null)
  const [clockTick, setClockTick] = useState(Date.now())
  const [builderPlan, setBuilderPlan] = useState<ExperimentPlan>({
    paymentResultDeliveries: 1,
    fulfilmentBehavior: 'SUCCESS',
    fulfilmentMaxAttempts: 4,
    recoveryMode: 'MANUAL',
  })
  const [loadConfig, setLoadConfig] = useState({ workflowCount: 10, trafficPattern: 'BURST', duplicatePercentage: 20, intervalMillis: 200, consumerConcurrency: 1 })
  const [loadExperiment, setLoadExperiment] = useState<LoadExperiment | null>(null)
  const [recentLoadExperiments, setRecentLoadExperiments] = useState<LoadExperiment[]>([])
  const [loadStarting, setLoadStarting] = useState(false)
  const [comparisonRunning, setComparisonRunning] = useState(false)
  const [comparisonProgress, setComparisonProgress] = useState('')
  const [comparisonResults, setComparisonResults] = useState<LoadExperiment[]>([])
  const [loadError, setLoadError] = useState('')
  const streamRef = useRef<EventSource | null>(null)
  const loadTimerRef = useRef<number | null>(null)
  const loadPanelRef = useRef<HTMLElement | null>(null)
  const runPanelRef = useRef<HTMLElement | null>(null)
  const evidenceRequestRef = useRef(0)

  useEffect(() => {
    void loadRecentRuns()
    void loadRecentLoadExperiments()
    void loadDeploymentStatus()
    const statusTimer = window.setInterval(() => void loadDeploymentStatus(), 15_000)
    const clockTimer = window.setInterval(() => setClockTick(Date.now()), 1000)
    const route = window.location.pathname.match(/^\/runs\/([0-9a-f-]+)$/i)
    if (route) void inspectRun(route[1], false)
    const restoreRoute = () => {
      const restored = window.location.pathname.match(/^\/runs\/([0-9a-f-]+)$/i)
      if (restored) void inspectRun(restored[1], false)
    }
    window.addEventListener('popstate', restoreRoute)
    return () => {
      streamRef.current?.close()
      if (loadTimerRef.current) window.clearInterval(loadTimerRef.current)
      window.clearInterval(statusTimer)
      window.clearInterval(clockTimer)
      window.removeEventListener('popstate', restoreRoute)
    }
  }, [])
  useEffect(() => {
    if (launchSequence === 0) return
    const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
    runPanelRef.current?.scrollIntoView({
      behavior: reducedMotion ? 'auto' : 'smooth',
      block: 'start',
    })
    runPanelRef.current?.focus({ preventScroll: true })
  }, [launchSequence])

  async function startLoadExperiment() {
    setLoadStarting(true)
    setLoadError('')
    try {
      const response = await fetch('/api/v1/load-experiments', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(loadConfig),
      })
      if (!response.ok) throw new Error(response.status === 409
        ? 'Another load experiment is still running.' : `Load experiment returned HTTP ${response.status}`)
      const created: LoadExperiment = await response.json()
      setLoadExperiment(created)
      window.setTimeout(() => loadPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
      if (loadTimerRef.current) window.clearInterval(loadTimerRef.current)
      loadTimerRef.current = window.setTimeout(() => void pollLoadExperiment(created.id), 1000)
    } catch (reason) {
      setLoadError(reason instanceof Error ? reason.message : 'Could not start the load experiment')
    } finally {
      setLoadStarting(false)
    }
  }

  async function loadRecentLoadExperiments() {
    try {
      const response = await fetch('/api/v1/load-experiments')
      if (!response.ok) return
      setRecentLoadExperiments(await response.json())
    } catch {
      // The live experiment remains usable when historical comparison is unavailable.
    }
  }

  async function waitForLoadExperiment(id: string) {
    while (true) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      const response = await fetch(`/api/v1/load-experiments/${id}`)
      if (!response.ok) throw new Error(`Load metrics returned HTTP ${response.status}`)
      const report: LoadExperiment = await response.json()
      setLoadExperiment(report)
      if (report.status === 'PROVED' || report.status === 'FAILED') return report
    }
  }

  async function runConcurrencyComparison() {
    const warmups: ConcurrencyProfile[] = [1, 4, 8]
    const measuredProfiles: ConcurrencyProfile[] = [1, 4, 8, 4, 8, 1, 8, 1, 4]
    const executions = [...warmups, ...measuredProfiles]
    let executionIndex = 0
    const completed: LoadExperiment[] = []
    setComparisonRunning(true)
    setComparisonResults([])
    setLoadError('')
    try {
      for (const calls of executions) {
        const measured = executionIndex >= warmups.length
        const phaseIndex = measured ? executionIndex - warmups.length + 1 : executionIndex + 1
        setComparisonProgress(`${measured ? 'Measured run' : 'Warm-up'} ${phaseIndex} of ${measured ? 9 : 3} · consumer concurrency ${calls}`)
        executionIndex++
        setComparisonProgress(`${measured ? 'Measured run' : 'Warm-up'} ${phaseIndex} of ${measured ? 9 : 3} · consumer concurrency ${calls}`)
        const response = await fetch('/api/v1/load-experiments', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...loadConfig, consumerConcurrency: calls }),
        })
        if (!response.ok) throw new Error(response.status === 409
          ? 'Another load experiment is still running.' : `Load experiment returned HTTP ${response.status}`)
        const created: LoadExperiment = await response.json()
        setLoadExperiment(created)
        window.setTimeout(() => loadPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
        const result = await waitForLoadExperiment(created.id)
        setRecentLoadExperiments((current) => [result, ...current.filter((item) => item.id !== result.id)].slice(0, 12))
        if (measured) {
          completed.push(result)
          setComparisonResults([...completed])
        }
        if (result.status !== 'PROVED') throw new Error(`Comparison stopped: concurrency ${calls} did not prove its invariants.`)
        setComparisonProgress(`${measured ? 'Measured run' : 'Warm-up'} ${phaseIndex} complete · settling for 2 seconds`)
        await new Promise((resolve) => window.setTimeout(resolve, 2000))
      }
      await loadRecentLoadExperiments()
      setComparisonProgress('Complete · 3 warm-ups and 9 of 9 measured runs proved')
    } catch (reason) {
      setLoadError(reason instanceof Error ? reason.message : 'Could not complete the comparison')
    } finally {
      setComparisonRunning(false)
    }
  }

  async function pollLoadExperiment(id: string) {
    try {
      const response = await fetch(`/api/v1/load-experiments/${id}`)
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const report: LoadExperiment = await response.json()
      setLoadExperiment(report)
      if (report.status === 'PROVED' || report.status === 'FAILED') {
        if (loadTimerRef.current) window.clearInterval(loadTimerRef.current)
        loadTimerRef.current = null
        await loadRecentRuns()
        await loadRecentLoadExperiments()
      } else {
        loadTimerRef.current = window.setTimeout(() => void pollLoadExperiment(id), 1000)
      }
    } catch {
      setLoadError('Load metrics are temporarily unavailable; the accepted workflows continue running.')
      loadTimerRef.current = window.setTimeout(() => void pollLoadExperiment(id), 2000)
    }
  }

  async function startScenario(scenarioId: string, experimentPlan?: ExperimentPlan) {
    streamRef.current?.close()
    setStarting(true)
    setError('')
    setEvents([])
    setEvidenceReport(null)
    setRunConsistency(null)
    setDeadLetterInspection(null)
    setActiveScenario(scenarioId)
    const resolvedPlan = experimentPlan ?? presetPlan(scenarioId)
    setActivePlan(resolvedPlan)
    setLaunchSequence((current) => current + 1)
    try {
      const response = await fetch('/api/v1/runs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scenarioId, experimentPlan, amount: 129.90, currency: 'EUR' }),
      })
      if (!response.ok) throw new Error(`The Lab Console returned HTTP ${response.status}`)
      const created: RunResponse = await response.json()
      setRun(created)
      window.history.pushState({}, '', `/runs/${created.workflowId}`)
      subscribe(created.workflowId)
      void loadEvidence(created.workflowId)
      void loadConsistency(created.workflowId)
      await loadRecentRuns()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not start the workflow')
    } finally {
      setStarting(false)
    }
  }

  function subscribe(workflowId: string) {
    streamRef.current?.close()
    const source = new EventSource(`/api/v1/runs/${workflowId}/stream`)
    source.addEventListener('timeline-event', (message) => {
      const next: TimelineEvent = JSON.parse((message as MessageEvent).data)
      setEvents((current) => current.some((event) => event.sequence === next.sequence)
        ? current
        : [...current, next].sort((a, b) => a.sequence - b.sequence))
      void loadEvidence(workflowId)
      void loadConsistency(workflowId)
      if (next.state === 'DEAD_LETTERED' || next.state === 'POISON_DEAD_LETTERED') {
        void loadDeadLetter(workflowId)
      }
      if (['COMPLETED', 'COMPENSATED', 'FAILED_REQUIRES_INTERVENTION'].includes(next.state)) {
        void loadRecentRuns()
      }
    })
    source.onerror = () => setError('Live updates disconnected. The recorded timeline remains visible.')
    streamRef.current = source
  }

  async function loadRecentRuns() {
    try {
      const response = await fetch('/api/v1/runs')
      if (response.ok) setRecentRuns(await response.json())
    } catch {
      // History is supplementary; launching a new experiment remains available.
    }
  }

  async function loadDeploymentStatus() {
    try {
      const response = await fetch('/api/v1/deployment/status')
      if (!response.ok) throw new Error(`Deployment status returned HTTP ${response.status}`)
      setDeployment(await response.json())
    } catch {
      setDeployment((current) => current ? ({ ...current, acceptingExperiments: false,
        dependencies: current.dependencies.map((dependency) => ({ ...dependency, status: 'DOWN' })) }) : ({
        environment: 'unavailable', version: 'unknown', mode: 'READ_ONLY', acceptingExperiments: false,
        evidencePipeline: { enabled: false, status: 'ERROR' }, dependencies: [],
      }))
    }
  }

  async function loadEvidence(workflowId: string) {
    const requestSequence = ++evidenceRequestRef.current
    try {
      const response = await fetch(`/api/v1/runs/${workflowId}/evidence`)
      if (response.ok) {
        const report: EvidenceReport = await response.json()
        if (requestSequence === evidenceRequestRef.current) setEvidenceReport(report)
      }
    } catch {
      // The timeline remains usable while evidence assessment is temporarily unavailable.
    }
  }

  async function loadConsistency(workflowId: string) {
    try {
      const response = await fetch(`/api/v1/runs/${workflowId}/consistency`)
      if (response.ok) setRunConsistency(await response.json())
    } catch {
      setRunConsistency((current) => current && ({ ...current, status: 'SOURCE_UNAVAILABLE',
        explanation: "Workflow's authoritative state is temporarily unavailable" }))
    }
  }

  async function loadDeadLetter(workflowId: string) {
    try {
      const response = await fetch(`/api/v1/runs/${workflowId}/dead-letter`)
      if (response.ok) setDeadLetterInspection(await response.json())
    } catch {
      // Broker inspection is supplementary to the durable evidence report.
    }
  }

  async function inspectRun(workflowId: string, navigate = true) {
    streamRef.current?.close()
    setStarting(true)
    setError('')
    try {
      const response = await fetch(`/api/v1/runs/${workflowId}`)
      if (!response.ok) throw new Error(response.status === 404
        ? 'This experiment could not be found.'
        : `The Run Inspector returned HTTP ${response.status}`)
      const details: RunDetails = await response.json()
      setRun(details)
      setEvents(details.timeline)
      setActiveScenario(details.scenarioId)
      setActivePlan(details.experimentPlan)
      await loadEvidence(workflowId)
      await loadConsistency(workflowId)
      if (details.timeline.some((event) => event.state === 'DEAD_LETTERED'
        || event.state === 'POISON_DEAD_LETTERED')) await loadDeadLetter(workflowId)
      else setDeadLetterInspection(null)
      setLaunchSequence((current) => current + 1)
      if (navigate) window.history.pushState({}, '', `/runs/${workflowId}`)
      subscribe(workflowId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not inspect the experiment')
      setLaunchSequence((current) => current + 1)
    } finally {
      setStarting(false)
    }
  }

  async function copyEvidenceLink() {
    if (!run) return
    await navigator.clipboard.writeText(`${window.location.origin}/runs/${run.workflowId}`)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1800)
  }

  async function recover() {
    if (!run) return
    setRecovering(true)
    setError('')
    try {
      const response = await fetch(`/api/v1/runs/${run.workflowId}/recover`, { method: 'POST' })
      if (!response.ok) throw new Error(`Recovery returned HTTP ${response.status}`)
      await loadDeadLetter(run.workflowId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not recover the workflow')
    } finally {
      setRecovering(false)
    }
  }

  const completed = events.some((event) => event.state === 'COMPLETED')
  const duplicateCount = events.filter((event) => event.duplicateDelivery).length
  const deadLettered = events.some((event) => event.state === 'DEAD_LETTERED' || event.state === 'POISON_DEAD_LETTERED')
  const poisonDeadLettered = events.some((event) => event.state === 'POISON_DEAD_LETTERED')
  const compensated = events.some((event) => event.state === 'COMPENSATED')
  const staleIgnored = events.some((event) => event.state === 'STALE_IGNORED')
  const interventionRequired = events.some((event) => event.state === 'FAILED_REQUIRES_INTERVENTION')
  const planObserved = activePlan && (
    activePlan.fulfilmentBehavior === 'BUSINESS_REJECTION' ? compensated
      : activePlan.fulfilmentBehavior === 'STALE_AFTER_SUCCESS' ? completed && staleIgnored
        : activePlan.fulfilmentBehavior === 'UNSUPPORTED_CONTRACT' ? interventionRequired
        : completed
  ) && (activePlan.paymentResultDeliveries === 1 || duplicateCount === 1)
  const comparedRuns = comparison.map((workflowId) => recentRuns.find((item) => item.workflowId === workflowId))
  const acceptingExperiments = deployment?.acceptingExperiments ?? true

  return (
    <main>
      <header className="hero">
        <nav aria-label="Primary navigation">
          <a className="wordmark" href="#top" aria-label="EventLab home"><span className="mark">EL</span>EventLab</a>
          <div className="nav-links"><a href="./?tour">Project tour</a><a href={grafanaDashboardUrl()} target="_blank" rel="noreferrer">Operations</a><span className="build-status"><i /> EventLab · interactive</span></div>
        </nav>
        <div className="hero-copy" id="top">
          <p className="eyebrow">Distributed systems under pressure</p>
          <h1>Break the workflow.<br /><em>Understand the recovery.</em></h1>
          <p className="lede">A hands-on laboratory for watching messages duplicate, services fail, retries exhaust, and sagas compensate—one trace at a time.</p>
        </div>
        <div className="signal" aria-hidden="true"><span /><span /><span /><span /><span /><span /></div>
      </header>

      <section className={`control-center ${deployment?.mode.toLowerCase() ?? 'checking'}`} aria-labelledby="control-center-title">
        <div><p className="eyebrow">Live Lab Control Center</p><h2 id="control-center-title">{deployment ? deployment.mode.replace('_', ' ') : 'Checking deployment'}</h2><p>{deployment?.mode === 'READ_ONLY' ? 'New experiments are paused before scheduled teardown. Existing evidence remains available.' : deployment?.mode === 'EXPIRED' ? 'This environment has reached its scheduled expiry.' : deployment && !deployment.acceptingExperiments ? 'New experiments are paused because the evidence pipeline is unavailable.' : 'The lab is accepting new experiments.'}</p></div>
        <dl><dt>Environment</dt><dd>{deployment?.environment ?? 'detecting'}</dd><dt>Build</dt><dd><code>{deployment?.version === 'development' ? 'development' : deployment?.version.slice(0, 12) ?? 'detecting'}</code></dd><dt>Lifetime</dt><dd>{formatRemaining(deployment?.expiresAt, clockTick)}</dd></dl>
        <div className="dependency-health" aria-label="Service health">{deployment?.dependencies.map((dependency) => <span key={dependency.name} className={dependency.status.toLowerCase()}><i />{dependency.name} {dependency.status}</span>) ?? <span>Loading health…</span>}</div>
        <div className={`evidence-pipeline ${deployment?.evidencePipeline?.status.toLowerCase() ?? 'starting'}`}><strong>Evidence pipeline</strong><span><i />{deployment?.evidencePipeline?.status ?? 'STARTING'}</span><small>{deployment?.evidencePipeline?.lastEventAt ? `Last event ${new Date(deployment.evidencePipeline.lastEventAt).toLocaleTimeString()}` : 'Waiting for an observed event'}</small></div>
        {deployment && deployment.environment !== 'local' && <div className="owner-controls"><strong>Owner operations</strong><span>GitHub permissions restrict deployment changes to repository collaborators.</span><a href="https://github.com/wiznick79/EventLab/actions/workflows/azure-deploy.yml" target="_blank" rel="noreferrer">Extend / redeploy ↗</a><a href="https://github.com/wiznick79/EventLab/actions/workflows/azure-destroy.yml" target="_blank" rel="noreferrer">Destroy environment ↗</a></div>}
      </section>

      <nav className="lab-navigation" aria-label="Interactive labs"><a href="#scenario-title">Curated scenarios</a><a href="#builder-title">Scenario builder</a><a href="#load-lab-title">Load lab</a><a href="#history-title">Evidence archive</a></nav>

      <section className={`workspace ${activeScenario === 'custom-plan' ? 'custom-result-active' : 'preset-result-active'}`} aria-labelledby="scenario-title">
        <div className="section-heading">
          <div><p className="eyebrow">Scenario library</p><h2 id="scenario-title">Run the system, then break it</h2></div>
          <p>The successful payment path establishes the baseline. Upcoming experiments introduce one controlled failure at a time.</p>
        </div>
        <div className="scenario-grid">
          <article className="scenario-card active-card">
            <span className="scenario-number">01</span><span className="scenario-tag">Baseline</span>
            <h3>Successful payment workflow</h3>
            <p>Start a workflow, authorize payment over Azure Service Bus, and watch the projected event timeline update live.</p>
            <button className="run-button" type="button" onClick={() => startScenario('happy-path')} disabled={starting || !acceptingExperiments}>
              {starting && activeScenario === 'happy-path' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
          <article className="scenario-card active-card">
            <span className="scenario-number">05</span><span className="scenario-tag">Versioning</span>
            <h3>Out-of-order update</h3>
            <p>Complete fulfilment at version 2, then deliver a delayed version-1 rejection without regressing state.</p>
            <button className="run-button" type="button" onClick={() => startScenario('out-of-order-event')} disabled={starting || !acceptingExperiments}>
              {starting && activeScenario === 'out-of-order-event' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
          <article className="scenario-card active-card">
            <span className="scenario-number">02</span><span className="scenario-tag">Idempotency</span>
            <h3>Duplicate payment result</h3>
            <p>Deliver one logical payment result twice while the Workflow inbox permits exactly one state transition.</p>
            <button className="run-button" type="button" onClick={() => startScenario('duplicate-payment-result')} disabled={starting || !acceptingExperiments}>
              {starting && activeScenario === 'duplicate-payment-result' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
          <article className="scenario-card active-card">
            <span className="scenario-number">03</span><span className="scenario-tag">Retry + DLQ</span>
            <h3>Fulfilment unavailable</h3>
            <p>Exhaust four delivery attempts, restore the dependency, and replay the quarantined command safely.</p>
            <button className="run-button" type="button" onClick={() => startScenario('fulfilment-unavailable')} disabled={starting || !acceptingExperiments}>
              {starting && activeScenario === 'fulfilment-unavailable' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
          <article className="scenario-card active-card">
            <span className="scenario-number">04</span><span className="scenario-tag">Compensation</span>
            <h3>Fulfilment rejected</h3>
            <p>Authorize payment, reject fulfilment, and watch the persisted saga void the payment.</p>
            <button className="run-button" type="button" onClick={() => startScenario('fulfilment-rejected')} disabled={starting || !acceptingExperiments}>
              {starting && activeScenario === 'fulfilment-rejected' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
        </div>

        <section className="scenario-builder" aria-labelledby="builder-title">
          <div className="builder-copy"><p className="eyebrow">Scenario Builder</p><h2 id="builder-title">Compose a real experiment</h2><p>Choose a bounded message-delivery plan. The same Workflow, Payment, Fulfilment, Service Bus, databases, and traces execute it.</p></div>
          <div className="builder-controls">
            <label>Payment-result deliveries<select aria-label="Payment-result deliveries" value={builderPlan.paymentResultDeliveries} onChange={(event) => setBuilderPlan((current) => ({ ...current, paymentResultDeliveries: Number(event.target.value) }))}><option value="1">1 · normal delivery</option><option value="2">2 · duplicate delivery</option></select></label>
            <label>Fulfilment behavior<select aria-label="Fulfilment behavior" value={builderPlan.fulfilmentBehavior} onChange={(event) => setBuilderPlan((current) => ({ ...current, fulfilmentBehavior: event.target.value as FulfilmentBehavior, recoveryMode: event.target.value === 'TEMPORARY_UNAVAILABLE' ? current.recoveryMode : 'MANUAL' }))}><option value="SUCCESS">Succeed</option><option value="TEMPORARY_UNAVAILABLE">Unavailable → DLQ → recovery</option><option value="BUSINESS_REJECTION">Reject → compensate payment</option><option value="STALE_AFTER_SUCCESS">Succeed → deliver stale update</option><option value="UNSUPPORTED_CONTRACT">Unsupported contract → poison DLQ</option></select></label>
            {builderPlan.fulfilmentBehavior === 'TEMPORARY_UNAVAILABLE' && <>
              <label>Retry budget<select aria-label="Retry budget" value={builderPlan.fulfilmentMaxAttempts} onChange={(event) => setBuilderPlan((current) => ({ ...current, fulfilmentMaxAttempts: Number(event.target.value) }))}>{[2, 3, 4, 5, 6].map((attempts) => <option key={attempts} value={attempts}>{attempts} attempts</option>)}</select></label>
              <label>Recovery policy<select aria-label="Recovery policy" value={builderPlan.recoveryMode} onChange={(event) => setBuilderPlan((current) => ({ ...current, recoveryMode: event.target.value as RecoveryMode }))}><option value="MANUAL">Manual · operator replay</option><option value="AUTOMATIC">Automatic · policy replay</option></select></label>
            </>}
          </div>
          <div className="builder-invariant"><span>Expected invariant</span><strong>{expectedInvariant(builderPlan)}</strong></div>
          <button className="builder-run" type="button" onClick={() => startScenario('custom-plan', builderPlan)} disabled={starting || !acceptingExperiments}>{starting && activeScenario === 'custom-plan' ? 'Starting…' : acceptingExperiments ? 'Run custom experiment →' : 'New experiments paused'}</button>
        </section>

        <section className="load-lab" aria-labelledby="load-lab-title">
          <div className="load-lab-copy"><p className="eyebrow">Load &amp; Concurrency Lab</p><h2 id="load-lab-title">Put the guarantees under measurable pressure</h2><p>This launches real workflows concurrently through the same APIs, broker, consumers, databases, and evidence pipeline. The result is correct only when every accepted workflow reaches its promised outcome.</p></div>
          <div className="load-controls">
            <label>Workflows<select aria-label="Load workflow count" value={loadConfig.workflowCount} onChange={(event) => setLoadConfig((current) => ({ ...current, workflowCount: Number(event.target.value) }))}>{(deployment?.environment === 'local' ? [10, 25, 50, 100] : [10, 25]).map((count) => <option key={count} value={count}>{count} workflows</option>)}</select></label>
            <label>Traffic pattern<select aria-label="Load traffic pattern" value={loadConfig.trafficPattern} onChange={(event) => setLoadConfig((current) => ({ ...current, trafficPattern: event.target.value }))}><option value="BURST">Burst · launch concurrently</option><option value="STEADY">Steady · controlled arrival</option></select></label>
            <label>Duplicate mix<select aria-label="Load duplicate percentage" value={loadConfig.duplicatePercentage} onChange={(event) => setLoadConfig((current) => ({ ...current, duplicatePercentage: Number(event.target.value) }))}><option value="0">0% normal deliveries</option><option value="10">10% duplicate deliveries</option><option value="20">20% duplicate deliveries</option><option value="50">50% duplicate deliveries</option></select></label>
            <label>Consumer concurrency<select aria-label="Consumer concurrency" value={loadConfig.consumerConcurrency} onChange={(event) => setLoadConfig((current) => ({ ...current, consumerConcurrency: Number(event.target.value) }))}><option value="1">1 · sequential baseline</option><option value="4">4 · moderate parallelism</option><option value="8">8 · high parallelism</option></select></label>
            {loadConfig.trafficPattern === 'STEADY' && <label>Arrival interval<select aria-label="Load arrival interval" value={loadConfig.intervalMillis} onChange={(event) => setLoadConfig((current) => ({ ...current, intervalMillis: Number(event.target.value) }))}><option value="100">100 ms</option><option value="200">200 ms</option><option value="500">500 ms</option><option value="1000">1 second</option></select></label>}
          </div>
          <div className="load-safety"><strong>Bounded by design</strong><span>{deployment?.environment === 'local' ? 'Local ceiling: 100 workflows.' : 'Public demo ceiling: 25 workflows.'} One active load experiment at a time.</span></div>
          <div className="load-actions"><button className="builder-run" type="button" onClick={startLoadExperiment} disabled={loadStarting || comparisonRunning || !acceptingExperiments || loadExperiment?.status === 'LAUNCHING' || loadExperiment?.status === 'RUNNING'}>{loadStarting ? 'Starting pressure test…' : 'Run load experiment →'}</button><button className="comparison-run" type="button" onClick={runConcurrencyComparison} disabled={loadStarting || comparisonRunning || !acceptingExperiments || loadExperiment?.status === 'LAUNCHING' || loadExperiment?.status === 'RUNNING'}>{comparisonRunning ? comparisonProgress : 'Run balanced 9-run comparison'}</button></div>
          {comparisonRunning && <p className="comparison-progress" aria-live="polite">{comparisonProgress}. Keep this page open; each result is persisted as it completes.</p>}
          {loadError && <p className="run-error">{loadError}</p>}
        </section>

        {loadExperiment && <section className={`load-report ${loadExperiment.status.toLowerCase()}`} ref={loadPanelRef} tabIndex={-1} aria-labelledby="load-report-title">
          <div className="load-report-heading"><div><p className="eyebrow">Live aggregate evidence</p><h2 id="load-report-title">{loadExperiment.status.replace('_', ' ')}</h2></div><code>{loadExperiment.id}</code></div>
          <div className="load-progress launch-progress"><span style={{ width: `${loadExperiment.processedLaunches / loadExperiment.requestedWorkflows * 100}%` }} /><strong>{loadExperiment.processedLaunches} / {loadExperiment.requestedWorkflows} launch responses</strong></div>
          <div className="load-stage-flow" aria-label="Distributed workflow progress">
            <div><span>Accepted</span><strong>{loadExperiment.acceptedWorkflows}</strong><i style={{ width: `${loadExperiment.acceptedWorkflows / loadExperiment.requestedWorkflows * 100}%` }} /></div>
            <b>→</b>
            <div><span>Payment observed</span><strong>{loadExperiment.paymentObservedWorkflows}</strong><i style={{ width: `${loadExperiment.paymentObservedWorkflows / loadExperiment.requestedWorkflows * 100}%` }} /></div>
            <b>→</b>
            <div><span>Fulfilment observed</span><strong>{loadExperiment.fulfilmentObservedWorkflows}</strong><i style={{ width: `${loadExperiment.fulfilmentObservedWorkflows / loadExperiment.requestedWorkflows * 100}%` }} /></div>
            <b>→</b>
            <div><span>Terminal evidence</span><strong>{loadExperiment.terminalWorkflows}</strong><i style={{ width: `${loadExperiment.terminalWorkflows / loadExperiment.requestedWorkflows * 100}%` }} /></div>
          </div>
          <div className="phase-timing" aria-label="Load experiment phase timing">
            <div><span>Launch complete</span><strong>{(loadExperiment.launchDurationMillis / 1000).toFixed(2)}s</strong></div><b>→</b>
            <div><span>Payment wave</span><strong>{(loadExperiment.firstPaymentDelayMillis / 1000).toFixed(2)}–{(loadExperiment.lastPaymentDelayMillis / 1000).toFixed(2)}s</strong></div><b>→</b>
            <div><span>Command queued wave</span><strong>{(loadExperiment.firstFulfilmentQueuedDelayMillis / 1000).toFixed(2)}–{(loadExperiment.lastFulfilmentQueuedDelayMillis / 1000).toFixed(2)}s</strong></div><b>→</b>
            <div><span>Fulfilment wave</span><strong>{(loadExperiment.firstFulfilmentDelayMillis / 1000).toFixed(2)}–{(loadExperiment.lastFulfilmentDelayMillis / 1000).toFixed(2)}s</strong></div><b>→</b>
            <div><span>Terminal wave</span><strong>{(loadExperiment.firstTerminalDelayMillis / 1000).toFixed(2)}–{(loadExperiment.drainDurationMillis / 1000).toFixed(2)}s</strong></div>
          </div>
          <section className="broker-pressure" aria-label="Live Service Bus queue pressure">
            <div className="broker-pressure-heading"><div><span>Live pipeline pressure</span><strong>{loadExperiment.brokerPressure.available ? 'BACKLOG · CURRENT / PEAK' : 'COUNTERS UNAVAILABLE'}</strong></div><p>{loadExperiment.brokerPressure.status === 'NATIVE_SERVICE_BUS_COUNTS' ? 'Native environment-wide Service Bus active-message counts.' : loadExperiment.brokerPressure.status === 'LOGICAL_EXPERIMENT_BACKLOG' ? 'Experiment-specific logical backlog derived from persisted stage evidence.' : 'Pressure source unavailable.'}</p></div>
            {loadExperiment.brokerPressure.available ? <div className="broker-pressure-stages">
              <div><span>Payment commands</span><strong>{loadExperiment.brokerPressure.paymentCommands.current} / {loadExperiment.brokerPressure.paymentCommands.peak}</strong></div><b>→</b>
              <div><span>Workflow events</span><strong>{loadExperiment.brokerPressure.workflowEvents.current} / {loadExperiment.brokerPressure.workflowEvents.peak}</strong></div><b>→</b>
              <div><span>Fulfilment commands</span><strong>{loadExperiment.brokerPressure.fulfilmentCommands.current} / {loadExperiment.brokerPressure.fulfilmentCommands.peak}</strong></div><b>→</b>
              <div><span>Evidence events</span><strong>{loadExperiment.brokerPressure.evidenceEvents.current} / {loadExperiment.brokerPressure.evidenceEvents.peak}</strong></div>
            </div> : <p className="broker-pressure-unavailable">{loadExperiment.brokerPressure.status}. Workflow evidence and phase timings remain available.</p>}
          </section>
          <dl className="load-metrics">
            <div><dt>Pending launches</dt><dd>{loadExperiment.pendingLaunches}</dd></div>
            <div><dt>Accepted</dt><dd>{loadExperiment.acceptedWorkflows} / {loadExperiment.requestedWorkflows}</dd></div>
            <div><dt>Consumer concurrency</dt><dd>{loadExperiment.consumerConcurrency}</dd></div>
            <div><dt>Evidence proved</dt><dd>{loadExperiment.provedWorkflows}</dd></div>
            <div><dt>Invariant violations</dt><dd>{loadExperiment.invariantViolations}</dd></div>
            <div><dt>Current backlog</dt><dd>{loadExperiment.backlog}</dd></div>
            <div><dt>Max in flight</dt><dd>{loadExperiment.maxInFlight}</dd></div>
            <div><dt>Throughput</dt><dd>{loadExperiment.throughputPerSecond.toFixed(2)} / sec</dd></div>
            <div><dt>Median latency</dt><dd>{(loadExperiment.medianLatencyMillis / 1000).toFixed(2)} s</dd></div>
            <div><dt>p95 latency</dt><dd>{(loadExperiment.p95LatencyMillis / 1000).toFixed(2)} s</dd></div>
            <div><dt>Duplicates observed</dt><dd>{loadExperiment.duplicateDeliveries}</dd></div>
          </dl>
          <p className="load-verdict">{loadExperiment.status === 'PROVED' ? 'PROVED · every accepted workflow reached a terminal state and its individual evidence report passed.' : loadExperiment.statusReason === 'LAUNCH_INTERRUPTED' ? 'INTERRUPTED · the Lab Console restarted before every requested workflow could be launched. This is an incomplete experiment, not a capacity or invariant result.' : loadExperiment.status === 'FAILED' ? 'FAILED · at least one launch or distributed invariant did not pass.' : loadExperiment.status === 'LAUNCHING' ? 'LAUNCHING · accepted members appear immediately while the remaining requests are still in flight.' : 'IN PROGRESS · launch is complete; the accepted-work backlog and terminal evidence update once per second.'}</p>
          {loadExperiment.workflowIds.length > 0 && <button className="inspect-member" type="button" onClick={() => inspectRun(loadExperiment.workflowIds[0])}>Inspect one member’s timeline and traces →</button>}
        </section>}

        {recentLoadExperiments.length > 0 && <section className="load-comparison" aria-labelledby="load-comparison-title">
          <div><p className="eyebrow">Concurrency comparison</p><h2 id="load-comparison-title">Same guarantees, different consumer parallelism</h2><p>The five latest completed runs below update after every experiment. A campaign warms each profile once, then measures nine runs in rotated order so transient startup and ordering effects have less influence.</p></div>
          <div className="load-comparison-table" role="region" aria-label="Recent load experiment comparison" tabIndex={0}>
            <table><thead><tr><th>Concurrency</th><th>Workload</th><th>Result</th><th>Proved</th><th>Violations</th><th>Throughput</th><th>Median</th><th>p95</th></tr></thead>
              <tbody>{recentLoadExperiments.slice(0, 5).map((item) => <tr key={item.id}><td><strong>{item.consumerConcurrency}</strong></td><td>{item.requestedWorkflows} · {item.trafficPattern.toLowerCase()} · {item.duplicatePercentage}% dupes</td><td><span className={`comparison-status ${item.status.toLowerCase()}`}>{item.status}</span></td><td>{item.provedWorkflows}/{item.acceptedWorkflows}</td><td>{item.invariantViolations}</td><td>{item.throughputPerSecond.toFixed(2)}/s</td><td>{(item.medianLatencyMillis / 1000).toFixed(2)}s</td><td>{(item.p95LatencyMillis / 1000).toFixed(2)}s</td></tr>)}</tbody>
            </table>
          </div>
          <p className="comparison-note">Concurrency is applied to the real Service Bus processors for the duration of one experiment, then restored to the sequential baseline. Higher is not automatically faster: broker prefetch, local CPU, database contention, and processor restart warm-up all affect the measured result.</p>
          {comparisonResults.length > 0 && <div className="comparison-summary"><h3>Current balanced comparison</h3><div>{([1, 4, 8] as ConcurrencyProfile[]).map((calls) => {
            const runs = comparisonResults.filter((item) => item.consumerConcurrency === calls)
            const stats = comparisonStatistics(runs)
            return <article key={calls}><span>Concurrency {calls}</span><strong>{runs.length ? `${stats.median.toFixed(2)}/s` : 'Pending'}</strong><small>{runs.length}/3 measured · min {stats.minimum.toFixed(2)} · max {stats.maximum.toFixed(2)} · spread {stats.spread.toFixed(0)}% · {runs.length < 3 ? 'COLLECTING' : stats.stable ? 'STABLE' : 'INCONCLUSIVE'} · {runs.reduce((sum, item) => sum + item.invariantViolations, 0)} violations</small></article>
          })}</div></div>}
        </section>}

        <section className="run-history" aria-labelledby="history-title">
          <div className="history-heading"><div><p className="eyebrow">Run Inspector</p><h2 id="history-title">Recent experiment evidence</h2></div><p>Every run has a durable URL. Reopen its plan, outcome, timeline, and exact trace evidence after a refresh.</p></div>
          {recentRuns.length > 0 ? <ol className="history-list">{recentRuns.map((recent) => <li key={recent.workflowId}>
            <button type="button" onClick={() => inspectRun(recent.workflowId)}>
              <span><strong>{recent.state.replaceAll('_', ' ')}</strong><small>{new Date(recent.createdAt).toLocaleString()}</small></span>
              <span className="history-plan">{recent.experimentPlan.paymentResultDeliveries}× payment result · {recent.experimentPlan.fulfilmentBehavior.replaceAll('_', ' ')}{recent.experimentPlan.fulfilmentBehavior === 'TEMPORARY_UNAVAILABLE' && ` · ${recent.experimentPlan.fulfilmentMaxAttempts} attempts · ${recent.experimentPlan.recoveryMode.toLowerCase()}`}</span>
              <code>{recent.workflowId.slice(0, 8)}</code>
            </button>
          </li>)}</ol> : <p className="history-empty">Run an experiment to create the first shareable evidence record.</p>}
          {recentRuns.length >= 2 && <div className="run-comparison">
            <div className="comparison-controls">
              <label>First run<select aria-label="First comparison run" value={comparison[0]} onChange={(event) => setComparison([event.target.value, comparison[1]])}><option value="">Choose a run</option>{recentRuns.map((item) => <option key={item.workflowId} value={item.workflowId}>{item.workflowId.slice(0, 8)} · {item.state}</option>)}</select></label>
              <span>versus</span>
              <label>Second run<select aria-label="Second comparison run" value={comparison[1]} onChange={(event) => setComparison([comparison[0], event.target.value])}><option value="">Choose a run</option>{recentRuns.map((item) => <option key={item.workflowId} value={item.workflowId}>{item.workflowId.slice(0, 8)} · {item.state}</option>)}</select></label>
            </div>
            {comparedRuns[0] && comparedRuns[1] && <div className="comparison-grid">{comparedRuns.map((item) => item && <article key={item.workflowId}>
              <strong>{item.state.replaceAll('_', ' ')}</strong><code>{item.workflowId}</code>
              <dl><dt>Deliveries</dt><dd>{item.experimentPlan.paymentResultDeliveries}</dd><dt>Fulfilment</dt><dd>{item.experimentPlan.fulfilmentBehavior.replaceAll('_', ' ')}</dd>{item.experimentPlan.fulfilmentBehavior === 'TEMPORARY_UNAVAILABLE' && <><dt>Policy</dt><dd>{item.experimentPlan.fulfilmentMaxAttempts} attempts · {item.experimentPlan.recoveryMode.toLowerCase()}</dd></>}<dt>Invariant</dt><dd>{item.expectedInvariant}</dd></dl>
              <button type="button" onClick={() => inspectRun(item.workflowId)}>Inspect evidence →</button>
            </article>)}</div>}
          </div>}
        </section>

        {(starting || run || error) && <section className="run-panel" ref={runPanelRef} tabIndex={-1} aria-live="polite">
          <div className="run-heading">
            <div><p className="eyebrow">Live experiment</p><h2>{completed
              ? 'Workflow completed'
              : compensated ? 'Workflow compensated'
                : interventionRequired ? 'Workflow requires intervention' : 'Workflow in progress'}</h2></div>
            {run && <div className="run-identifiers"><code>run {run.workflowId}</code><code>plan {run.experimentPlanId}</code><button type="button" onClick={copyEvidenceLink}>{copied ? 'Copied' : 'Copy evidence link'}</button></div>}
          </div>
          {activeScenario === 'duplicate-payment-result' && completed && duplicateCount > 0 && <div className="invariant">
            <strong>Invariant protected</strong>
            <span>{duplicateCount} duplicate delivery observed · 1 workflow completion</span>
          </div>}
          {(activeScenario === 'fulfilment-unavailable' || activePlan?.fulfilmentBehavior === 'TEMPORARY_UNAVAILABLE') && deadLettered && !completed && <div className="invariant">
            <strong>{activePlan?.recoveryMode === 'AUTOMATIC' ? 'Automatic recovery pending' : 'Command quarantined'}</strong>
            <span>{activePlan?.recoveryMode === 'AUTOMATIC' ? 'The backend policy claimed this DLQ entry and will replay it without browser intervention.' : 'The retry budget is exhausted. Restore the simulated dependency and replay this command.'}</span>
            {activePlan?.recoveryMode !== 'AUTOMATIC' && <button className="run-button" type="button" onClick={recover} disabled={recovering}>
              {recovering ? 'Recovering…' : 'Recover and replay'} <span>→</span>
            </button>}
          </div>}
          {activePlan?.fulfilmentBehavior === 'UNSUPPORTED_CONTRACT' && poisonDeadLettered && <div className="invariant">
            <strong>Poison message quarantined</strong>
            <span>The consumer did receive the command, rejected schema version 99 on three deliveries, and explicitly moved it to the native Service Bus DLQ. Fulfilment never completed; the saga later requires intervention.</span>
          </div>}
          {activeScenario === 'fulfilment-rejected' && compensated && <div className="invariant">
            <strong>Invariant restored</strong>
            <span>Fulfilment rejected · payment compensated · workflow terminal state COMPENSATED</span>
          </div>}
          {activeScenario === 'out-of-order-event' && staleIgnored && <div className="invariant">
            <strong>Version invariant protected</strong>
            <span>Workflow remained COMPLETED · delayed version 1 ignored behind current version 2</span>
          </div>}
          {activePlan && <div className={`plan-verdict ${planObserved ? 'proved' : ''}`}><span>Expected</span><p>{expectedInvariant(activePlan)}</p><span>Observed</span><p>{planObserved ? 'PROVED · the live timeline satisfies every selected rule.' : 'IN PROGRESS · collecting delivery and terminal-state evidence.'}</p></div>}
          {run && runConsistency && <section className={`consistency-proof ${runConsistency.status.toLowerCase()}`} aria-label="Evidence consistency">
            <div><span>Authoritative Workflow</span><strong>{runConsistency.authoritativeState ?? 'Unavailable'}</strong></div><b>↔</b><div><span>Evidence projection</span><strong>{runConsistency.projectedState}</strong></div><p><em>{runConsistency.status.replace('_', ' ')}</em>{runConsistency.explanation}{runConsistency.lagSeconds > 0 ? ` · ${runConsistency.lagSeconds}s` : ''}</p>
          </section>}
          {run && deadLettered && deadLetterInspection && <section className={`dead-letter-proof ${deadLetterInspection.status.toLowerCase()}`} aria-labelledby="dead-letter-title">
            <div className="dead-letter-heading"><div><span>Native Service Bus proof</span><h3 id="dead-letter-title">{deadLetterInspection.status === 'FOUND' ? 'DLQ entry found' : deadLetterInspection.status === 'NOT_FOUND' ? 'DLQ entry not present' : 'Broker inspection unavailable'}</h3></div><code>{deadLetterInspection.queue}</code></div>
            {deadLetterInspection.status === 'FOUND' && <dl>
              <dt>Message ID</dt><dd><code>{deadLetterInspection.messageId}</code></dd>
              <dt>Message type</dt><dd>{deadLetterInspection.subject}</dd>
              <dt>Dead-letter reason</dt><dd><strong>{deadLetterInspection.deadLetterReason}</strong></dd>
              <dt>Error description</dt><dd>{deadLetterInspection.errorDescription}</dd>
              <dt>Schema version</dt><dd>{deadLetterInspection.schemaVersion ?? 'not declared'}</dd>
              <dt>Broker delivery count</dt><dd>{deadLetterInspection.deliveryCount}</dd>
              <dt>Sequence number</dt><dd>{deadLetterInspection.sequenceNumber}</dd>
              <dt>Enqueued</dt><dd>{deadLetterInspection.enqueuedAt ? new Date(deadLetterInspection.enqueuedAt).toLocaleString() : 'unknown'}</dd>
              <dt>Replay policy</dt><dd>{deadLetterInspection.replayAllowed ? 'Guarded replay available' : 'Replay blocked'}</dd>
            </dl>}
            <p>{deadLetterInspection.operatorGuidance} The delivery count is the broker-reported value; application attempts are proved independently by the rejection events and traces.</p>
          </section>}
          {run && evidenceReport && <section className={`evidence-report ${evidenceReport.assessment.toLowerCase()}`} aria-labelledby="evidence-title">
            <div className="evidence-heading"><div><span>Backend assessment</span><h3 id="evidence-title">{evidenceReport.assessment.replace('_', ' ')}</h3></div><a href={`/api/v1/runs/${run.workflowId}/evidence`} download={`eventlab-${run.workflowId}-evidence.json`}>Download evidence JSON ↓</a></div>
            <ol>{evidenceReport.checks.map((check) => <li key={check.id}><strong>{check.status === 'PROVED' ? '✓' : check.status === 'FAILED' ? '!' : '…'} {check.label}</strong><span>{check.observation}</span>{check.traceIds.length > 0 && <small>{check.traceIds.length} supporting trace{check.traceIds.length === 1 ? '' : 's'}</small>}</li>)}</ol>
          </section>}
          {error && <p className="run-error">{error}</p>}
          <ol className="timeline">
            {events.map((event) => {
              const evidence = traceEvidence(event.state)
              return <li key={event.sequence}>
                <span className="timeline-dot" />
                <div className="timeline-meta"><strong>{event.service}</strong><time>{new Date(event.occurredAt).toLocaleTimeString()}</time></div>
                <div><h3>{event.state.replaceAll('_', ' ')} {event.duplicateDelivery && <mark>duplicate</mark>}</h3><p>{event.description}</p></div>
                {event.traceId && <div className="trace-evidence">
                  <a href={traceUrl(event.traceId)} target="_blank" rel="noreferrer">Open trace ↗</a>
                  {evidence && <small>Find <code>{evidence.span}</code><br />decision = {evidence.decision}</small>}
                </div>}
              </li>
            })}
            {events.length === 0 && run && <li className="waiting">Waiting for the first business event…</li>}
          </ol>
        </section>}
      </section>

      <section className="system-strip" aria-labelledby="system-title">
        <div className="section-heading compact"><div><p className="eyebrow">System topology</p><h2 id="system-title">Four focused deployables</h2></div><span className="transport">Azure Service Bus · local emulator</span></div>
        <div className="service-row">{services.map(([name, role]) => <div className="service" key={name}><span className="service-state" /><div><strong>{name}</strong><small>{role}</small></div></div>)}</div>
      </section>
      <footer><p>Built to make failure visible—not to hide it.</p><p>Java · Spring Boot · Azure Service Bus · OpenTelemetry</p></footer>
    </main>
  )
}
