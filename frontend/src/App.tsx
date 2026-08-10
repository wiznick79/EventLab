import { useEffect, useRef, useState } from 'react'

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
}

type RunResponse = { workflowId: string; state: string }

const futureScenarios = [
  ['Duplicate payment result', 'Observe at-least-once delivery while inbox idempotency protects workflow state.', 'Idempotency'],
  ['Fulfilment unavailable', 'Follow retries and exponential backoff into the dead-letter queue and recovery.', 'Retry + DLQ'],
  ['Fulfilment rejected', 'Watch the saga reverse an authorized payment through a compensation command.', 'Compensation'],
]

const services = [
  ['Workflow', 'Orchestrator'],
  ['Payment', 'Participant'],
  ['Fulfilment', 'Participant'],
  ['Lab Console', 'Control plane'],
]

export function App() {
  const [run, setRun] = useState<RunResponse | null>(null)
  const [events, setEvents] = useState<TimelineEvent[]>([])
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState('')
  const streamRef = useRef<EventSource | null>(null)

  useEffect(() => () => streamRef.current?.close(), [])

  async function startHappyPath() {
    streamRef.current?.close()
    setStarting(true)
    setError('')
    setEvents([])
    try {
      const response = await fetch('/api/v1/runs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scenarioId: 'happy-path', amount: 129.90, currency: 'EUR' }),
      })
      if (!response.ok) throw new Error(`The Lab Console returned HTTP ${response.status}`)
      const created: RunResponse = await response.json()
      setRun(created)

      const source = new EventSource(`/api/v1/runs/${created.workflowId}/stream`)
      source.addEventListener('timeline-event', (message) => {
        const next: TimelineEvent = JSON.parse((message as MessageEvent).data)
        setEvents((current) => current.some((event) => event.eventId === next.eventId)
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

  const completed = events.some((event) => event.state === 'COMPLETED')

  return (
    <main>
      <header className="hero">
        <nav aria-label="Primary navigation">
          <a className="wordmark" href="#top" aria-label="EventLab home"><span className="mark">EL</span>EventLab</a>
          <span className="build-status"><i /> Milestone 1 · live workflow</span>
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
            <button className="run-button" type="button" onClick={startHappyPath} disabled={starting}>
              {starting ? 'Starting…' : 'Run experiment'} <span>→</span>
            </button>
          </article>
          {futureScenarios.map(([label, description, tag], index) => (
            <article className="scenario-card" key={label}>
              <span className="scenario-number">0{index + 2}</span><span className="scenario-tag">{tag}</span>
              <h3>{label}</h3><p>{description}</p>
              <button type="button" disabled aria-label={`${label} is not implemented yet`}>Coming next <span>→</span></button>
            </article>
          ))}
        </div>

        {(run || error) && <section className="run-panel" aria-live="polite">
          <div className="run-heading">
            <div><p className="eyebrow">Live experiment</p><h2>{completed ? 'Workflow completed' : 'Workflow in progress'}</h2></div>
            {run && <code>{run.workflowId}</code>}
          </div>
          {error && <p className="run-error">{error}</p>}
          <ol className="timeline">
            {events.map((event) => <li key={event.eventId}>
              <span className="timeline-dot" />
              <div className="timeline-meta"><strong>{event.service}</strong><time>{new Date(event.occurredAt).toLocaleTimeString()}</time></div>
              <div><h3>{event.state.replaceAll('_', ' ')}</h3><p>{event.description}</p></div>
              {event.traceId && <a href={`http://localhost:3000/explore?schemaVersion=1&panes=%7B%22trace%22:%7B%22datasource%22:%22tempo%22,%22queries%22:%5B%7B%22query%22:%22${event.traceId}%22,%22queryType%22:%22traceql%22%7D%5D%7D%7D&orgId=1`} target="_blank" rel="noreferrer">Open trace ↗</a>}
            </li>)}
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
