type Scenario = {
  label: string
  description: string
  tag: string
}

const scenarios: Scenario[] = [
  {
    label: 'Duplicate payment result',
    description: 'Observe at-least-once delivery while inbox idempotency protects workflow state.',
    tag: 'Idempotency',
  },
  {
    label: 'Fulfilment unavailable',
    description: 'Follow retries and exponential backoff into the dead-letter queue and recovery.',
    tag: 'Retry + DLQ',
  },
  {
    label: 'Fulfilment rejected',
    description: 'Watch the saga reverse an authorized payment through a compensation command.',
    tag: 'Compensation',
  },
]

const services = [
  ['Workflow', 'Orchestrator', 'ready'],
  ['Payment', 'Participant', 'ready'],
  ['Fulfilment', 'Participant', 'ready'],
  ['Lab Console', 'Control plane', 'ready'],
]

export function App() {
  return (
    <main>
      <header className="hero">
        <nav aria-label="Primary navigation">
          <a className="wordmark" href="#top" aria-label="EventLab home">
            <span className="mark">EL</span>
            EventLab
          </a>
          <span className="build-status"><i /> Architecture skeleton</span>
        </nav>

        <div className="hero-copy" id="top">
          <p className="eyebrow">Distributed systems under pressure</p>
          <h1>Break the workflow.<br /><em>Understand the recovery.</em></h1>
          <p className="lede">
            A hands-on laboratory for watching messages duplicate, services fail, retries exhaust,
            and sagas compensate—one trace at a time.
          </p>
        </div>

        <div className="signal" aria-hidden="true">
          <span /><span /><span /><span /><span /><span />
        </div>
      </header>

      <section className="workspace" aria-labelledby="scenario-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Scenario library</p>
            <h2 id="scenario-title">Choose a failure to investigate</h2>
          </div>
          <p>Curated, deterministic experiments will become executable in the next milestones.</p>
        </div>

        <div className="scenario-grid">
          {scenarios.map((scenario, index) => (
            <article className="scenario-card" key={scenario.label}>
              <span className="scenario-number">0{index + 1}</span>
              <span className="scenario-tag">{scenario.tag}</span>
              <h3>{scenario.label}</h3>
              <p>{scenario.description}</p>
              <button type="button" disabled aria-label={`${scenario.label} is not implemented yet`}>
                Coming next <span>→</span>
              </button>
            </article>
          ))}
        </div>
      </section>

      <section className="system-strip" aria-labelledby="system-title">
        <div className="section-heading compact">
          <div>
            <p className="eyebrow">System topology</p>
            <h2 id="system-title">Four focused deployables</h2>
          </div>
          <span className="transport">Azure Service Bus · planned</span>
        </div>
        <div className="service-row">
          {services.map(([name, role, status]) => (
            <div className="service" key={name}>
              <span className={`service-state ${status}`} />
              <div><strong>{name}</strong><small>{role}</small></div>
            </div>
          ))}
        </div>
      </section>

      <footer>
        <p>Built to make failure visible—not to hide it.</p>
        <p>Java · Spring Boot · Azure Service Bus · OpenTelemetry</p>
      </footer>
    </main>
  )
}
