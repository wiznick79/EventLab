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
}

type RunResponse = { workflowId: string; state: string }

type TraceEvidence = { span: string; decision: string }

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
  const streamRef = useRef<EventSource | null>(null)

  useEffect(() => () => streamRef.current?.close(), [])

  async function startScenario(scenarioId: string) {
    streamRef.current?.close()
    setStarting(true)
    setError('')
    setEvents([])
    setActiveScenario(scenarioId)
    try {
      const response = await fetch('/api/v1/runs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scenarioId, amount: 129.90, currency: 'EUR' }),
      })
      if (!response.ok) throw new Error(`The Lab Console returned HTTP ${response.status}`)
      const created: RunResponse = await response.json()
      setRun(created)

      const source = new EventSource(`/api/v1/runs/${created.workflowId}/stream`)
      source.addEventListener('timeline-event', (message) => {
        const next: TimelineEvent = JSON.parse((message as MessageEvent).data)
        setEvents((current) => current.some((event) => event.sequence === next.sequence)
          ? current
          : [...current, next].sort((a, b) => a.sequence - b.sequence))
      })
      source.onerror = () => setError('Live updates disconnected. The recorded timeline remains visible.')
      streamRef.current = source
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not start the workflow')
    } finally {
      setStarting(false)
    }
  }

  async function recover() {
    if (!run) return
    setRecovering(true)
    setError('')
    try {
      const response = await fetch(`/api/v1/runs/${run.workflowId}/recover`, { method: 'POST' })
      if (!response.ok) throw new Error(`Recovery returned HTTP ${response.status}`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not recover the workflow')
    } finally {
      setRecovering(false)
    }
  }

  const completed = events.some((event) => event.state === 'COMPLETED')
  const duplicateCount = events.filter((event) => event.duplicateDelivery).length
  const deadLettered = events.some((event) => event.state === 'DEAD_LETTERED')
  const compensated = events.some((event) => event.state === 'COMPENSATED')
  const staleIgnored = events.some((event) => event.state === 'STALE_IGNORED')

  return (
    <main>
      <header className="hero">
        <nav aria-label="Primary navigation">
          <a className="wordmark" href="#top" aria-label="EventLab home"><span className="mark">EL</span>EventLab</a>
          <div className="nav-links"><a href="./?tour">Project tour</a><a href={grafanaDashboardUrl()} target="_blank" rel="noreferrer">Operations</a><span className="build-status"><i /> Milestone 7 · portfolio polish</span></div>
        </nav>
        <div className="hero-copy" id="top">
          <p className="eyebrow">Distributed systems under pressure</p>
          <h1>Break the workflow.<br /><em>Understand the recovery.</em></h1>
          <p className="lede">A hands-on laboratory for watching messages duplicate, services fail, retries exhaust, and sagas compensate—one trace at a time.</p>
        </div>
        <div className="signal" aria-hidden="true"><span /><span /><span /><span /><span /><span /></div>
      </header>

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
            <button className="run-button" type="button" onClick={() => startScenario('happy-path')} disabled={starting}>
              {starting && activeScenario === 'happy-path' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
          <article className="scenario-card active-card">
            <span className="scenario-number">05</span><span className="scenario-tag">Versioning</span>
            <h3>Out-of-order update</h3>
            <p>Complete fulfilment at version 2, then deliver a delayed version-1 rejection without regressing state.</p>
            <button className="run-button" type="button" onClick={() => startScenario('out-of-order-event')} disabled={starting}>
              {starting && activeScenario === 'out-of-order-event' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
          <article className="scenario-card active-card">
            <span className="scenario-number">02</span><span className="scenario-tag">Idempotency</span>
            <h3>Duplicate payment result</h3>
            <p>Deliver one logical payment result twice while the Workflow inbox permits exactly one state transition.</p>
            <button className="run-button" type="button" onClick={() => startScenario('duplicate-payment-result')} disabled={starting}>
              {starting && activeScenario === 'duplicate-payment-result' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
          <article className="scenario-card active-card">
            <span className="scenario-number">03</span><span className="scenario-tag">Retry + DLQ</span>
            <h3>Fulfilment unavailable</h3>
            <p>Exhaust four delivery attempts, restore the dependency, and replay the quarantined command safely.</p>
            <button className="run-button" type="button" onClick={() => startScenario('fulfilment-unavailable')} disabled={starting}>
              {starting && activeScenario === 'fulfilment-unavailable' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
          <article className="scenario-card active-card">
            <span className="scenario-number">04</span><span className="scenario-tag">Compensation</span>
            <h3>Fulfilment rejected</h3>
            <p>Authorize payment, reject fulfilment, and watch the persisted saga void the payment.</p>
            <button className="run-button" type="button" onClick={() => startScenario('fulfilment-rejected')} disabled={starting}>
              {starting && activeScenario === 'fulfilment-rejected' ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
        </div>

        {(run || error) && <section className="run-panel" aria-live="polite">
          <div className="run-heading">
            <div><p className="eyebrow">Live experiment</p><h2>{completed
              ? 'Workflow completed'
              : compensated ? 'Workflow compensated' : 'Workflow in progress'}</h2></div>
            {run && <code>{run.workflowId}</code>}
          </div>
          {activeScenario === 'duplicate-payment-result' && completed && duplicateCount > 0 && <div className="invariant">
            <strong>Invariant protected</strong>
            <span>{duplicateCount} duplicate delivery observed · 1 workflow completion</span>
          </div>}
          {activeScenario === 'fulfilment-unavailable' && deadLettered && !completed && <div className="invariant">
            <strong>Command quarantined</strong>
            <span>The retry budget is exhausted. Restore the simulated dependency and replay this command.</span>
            <button className="run-button" type="button" onClick={recover} disabled={recovering}>
              {recovering ? 'Recovering…' : 'Recover and replay'} <span>→</span>
            </button>
          </div>}
          {activeScenario === 'fulfilment-rejected' && compensated && <div className="invariant">
            <strong>Invariant restored</strong>
            <span>Fulfilment rejected · payment compensated · workflow terminal state COMPENSATED</span>
          </div>}
          {activeScenario === 'out-of-order-event' && staleIgnored && <div className="invariant">
            <strong>Version invariant protected</strong>
            <span>Workflow remained COMPLETED · delayed version 1 ignored behind current version 2</span>
          </div>}
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
