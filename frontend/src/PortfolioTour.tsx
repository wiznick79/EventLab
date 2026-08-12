const scenarios = [
  {
    number: '01',
    title: 'Duplicate delivery',
    mechanism: 'Transactional inbox',
    outcome: 'One logical payment result is delivered twice; only the first delivery advances the workflow.',
    evidence: 'DUPLICATE_IGNORED · state change false',
  },
  {
    number: '02',
    title: 'Retry and dead letter',
    mechanism: 'Bounded retry + DLQ',
    outcome: 'Four deterministic attempts exhaust the budget before a workflow-scoped replay completes safely.',
    evidence: 'RETRY_SCHEDULED → DEAD_LETTERED → RECOVERY_ACCEPTED',
  },
  {
    number: '03',
    title: 'Saga compensation',
    mechanism: 'Persisted orchestration',
    outcome: 'Fulfilment rejects an authorized order, so the orchestrator issues an idempotent payment reversal.',
    evidence: 'PAYMENT_COMPENSATED · terminal state COMPENSATED',
  },
  {
    number: '04',
    title: 'Out-of-order update',
    mechanism: 'Aggregate versions',
    outcome: 'A delayed version-1 rejection arrives after version 2 completed and cannot regress durable state.',
    evidence: 'STALE_IGNORED · received 1 · current 2',
  },
]

const guarantees = [
  ['Atomic publication', 'Business state and outgoing messages commit together through a transactional outbox.'],
  ['At-least-once safety', 'Consumer inbox claims make duplicate delivery visible without repeating state changes.'],
  ['Controlled recovery', 'Dead-letter replay records the original and replacement broker message identities.'],
  ['Traceable decisions', 'Business-decision spans explain why each delivery changed—or did not change—state.'],
]

export function PortfolioTour() {
  return <main className="tour-page">
    <header className="tour-hero" id="top">
      <nav aria-label="Portfolio navigation">
        <a className="wordmark" href="#top"><span className="mark">EL</span>EventLab</a>
        <div className="nav-links"><a href="#architecture">Architecture</a><a href="#scenarios">Scenarios</a><a href="./">Live lab</a></div>
      </nav>
      <div className="tour-hero-grid">
        <div>
          <p className="eyebrow">Static project tour · always available</p>
          <h1>Reliable messaging,<br /><em>made inspectable.</em></h1>
          <p className="lede">EventLab is a distributed-systems failure laboratory built to demonstrate the awkward parts: duplicate delivery, retry exhaustion, dead letters, replay, compensation, and stale messages.</p>
          <div className="tour-actions"><a className="primary-link" href="#scenarios">Explore the evidence ↓</a><a href="https://github.com/wiznick79/EventLab">View source ↗</a></div>
        </div>
        <aside className="tour-facts" aria-label="Project facts">
          <span>Runtime</span><strong>4 Spring Boot services</strong>
          <span>Transport</span><strong>Azure Service Bus</strong>
          <span>Reliability</span><strong>Outbox · inbox · saga</strong>
          <span>Evidence</span><strong>OpenTelemetry · Tempo</strong>
          <span>Deployment</span><strong>Terraform · Container Apps</strong>
        </aside>
      </div>
    </header>

    <section className="tour-section" id="architecture" aria-labelledby="architecture-title">
      <div className="section-heading"><div><p className="eyebrow">System map</p><h2 id="architecture-title">Small boundaries, real failure modes</h2></div><p>Each service owns its database. Commands and business events cross Azure Service Bus; no service reaches into another service's tables.</p></div>
      <div className="architecture-map" role="img" aria-label="Workflow sends commands through Azure Service Bus to Payment and Fulfilment. Business events return to Workflow and feed the Lab Console timeline. All services export traces to Tempo and Grafana.">
        <div className="architecture-node orchestrator"><small>Orchestrator</small><strong>Workflow</strong><span>State · outbox · inbox</span></div>
        <div className="architecture-bus"><small>Asynchronous boundary</small><strong>Azure Service Bus</strong><span>Queues · topic · DLQ</span></div>
        <div className="participant-stack"><div className="architecture-node"><small>Participant</small><strong>Payment</strong><span>Authorization · compensation</span></div><div className="architecture-node"><small>Participant</small><strong>Fulfilment</strong><span>Retry · rejection · versioning</span></div></div>
        <div className="architecture-node console"><small>Projection</small><strong>Lab Console</strong><span>Timeline · replay control</span></div>
        <div className="architecture-node telemetry"><small>Portable evidence</small><strong>Tempo + Grafana</strong><span>Trace context · decision spans</span></div>
      </div>
    </section>

    <section className="tour-section tour-scenarios" id="scenarios" aria-labelledby="tour-scenarios-title">
      <div className="section-heading"><div><p className="eyebrow">Failure catalogue</p><h2 id="tour-scenarios-title">What each experiment proves</h2></div><p>The claim, protection mechanism, and observable outcome are kept together so the demonstration remains understandable when the live environment is offline.</p></div>
      <div className="tour-scenario-grid">{scenarios.map((scenario) => <article key={scenario.number}>
        <span className="scenario-number">{scenario.number}</span><span className="scenario-tag">{scenario.mechanism}</span>
        <h3>{scenario.title}</h3><p>{scenario.outcome}</p><code>{scenario.evidence}</code>
      </article>)}</div>
    </section>

    <section className="tour-section proof-section" aria-labelledby="proof-title">
      <div><p className="eyebrow">Proof model</p><h2 id="proof-title">A green final state is not enough.</h2><p>EventLab combines three kinds of evidence: the projected timeline shows what a reviewer observed, decision spans explain which branch executed, and persisted records prove the durable invariant.</p></div>
      <ol><li><span>01</span><div><strong>Timeline</strong><p>Human-readable delivery and state history.</p></div></li><li><span>02</span><div><strong>Trace</strong><p>Correlated spans with explicit business decisions.</p></div></li><li><span>03</span><div><strong>Database invariant</strong><p>Inbox, outbox, aggregate version, and terminal state.</p></div></li></ol>
    </section>

    <section className="tour-section guarantees" aria-labelledby="guarantees-title"><p className="eyebrow">Engineering choices</p><h2 id="guarantees-title">Reliability mechanisms</h2><div>{guarantees.map(([title, copy]) => <article key={title}><strong>{title}</strong><p>{copy}</p></article>)}</div></section>

    <section className="tour-cta"><p className="eyebrow">Two ways to explore</p><h2>The tour explains the design.<br />The live lab lets you break it.</h2><p>The interactive environment is deliberately temporary to control Azure cost. This page contains no backend dependency and remains available between demos.</p><div className="tour-actions"><a className="primary-link" href="./">Check live lab availability →</a><a href="https://github.com/wiznick79/EventLab/blob/main/docs/runbooks/reading-traces.md">Read the trace guide ↗</a></div></section>
    <footer><p>EventLab · distributed systems under pressure</p><p>Java · Spring Boot · Azure Service Bus · OpenTelemetry</p></footer>
  </main>
}
