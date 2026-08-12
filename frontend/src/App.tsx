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
  const streamRef = useRef<EventSource | null>(null)
  const runPanelRef = useRef<HTMLElement | null>(null)
  const evidenceRequestRef = useRef(0)

  useEffect(() => {
    void loadRecentRuns()
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

      <section className="workspace" aria-labelledby="scenario-title">
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
