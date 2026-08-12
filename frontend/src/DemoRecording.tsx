type DemoStep = {
  service: string
  state: string
  description: string
  decision?: string
}

type Demo = {
  label: string
  title: string
  invariant: string
  traceId: string
  steps: DemoStep[]
}

export const recordedDemos: Record<string, Demo> = {
  duplicate: {
    label: 'Idempotency',
    title: 'Duplicate payment result',
    invariant: 'Two deliveries · one state transition · one completion',
    traceId: '17088932321ea06646f5b539bdd5aafb',
    steps: [
      { service: 'Workflow', state: 'PAYMENT PENDING', description: 'Workflow accepted; authorization requested' },
      { service: 'Payment', state: 'FULFILMENT PENDING', description: 'Payment result delivered for the first time', decision: 'PAYMENT_ACCEPTED · state change true' },
      { service: 'Workflow inbox', state: 'DUPLICATE IGNORED', description: 'The same logical event arrived again', decision: 'DUPLICATE_IGNORED · state change false' },
      { service: 'Fulfilment', state: 'FULFILLED', description: 'Exactly one fulfilment command completed' },
      { service: 'Workflow', state: 'COMPLETED', description: 'Invariant protected at the terminal state' },
    ],
  },
  recovery: {
    label: 'Retry + DLQ',
    title: 'Dead-letter recovery',
    invariant: 'Bounded retries · quarantined command · audited replay',
    traceId: '8b47e2fb7188617d0e55a07ff3d729e2',
    steps: [
      { service: 'Fulfilment', state: 'RETRY SCHEDULED', description: 'Attempt 1 failed; wait 250 ms', decision: 'RETRY_SCHEDULED · attempt 1' },
      { service: 'Fulfilment', state: 'RETRY SCHEDULED', description: 'Attempt 2 failed; wait 500 ms', decision: 'RETRY_SCHEDULED · attempt 2' },
      { service: 'Fulfilment', state: 'RETRY SCHEDULED', description: 'Attempt 3 failed; wait 1000 ms', decision: 'RETRY_SCHEDULED · attempt 3' },
      { service: 'Fulfilment', state: 'DEAD LETTERED', description: 'Attempt 4 exhausted the retry budget', decision: 'DEAD_LETTERED · dead letter true' },
      { service: 'Recovery', state: 'RECOVERY ACCEPTED', description: 'Dependency restored; original message replayed under a new ID', decision: 'RECOVERY_ACCEPTED · replay audited' },
      { service: 'Workflow', state: 'COMPLETED', description: 'Replay completed through the normal idempotent path' },
    ],
  },
  compensation: {
    label: 'Saga',
    title: 'Payment compensation',
    invariant: 'Payment authorized · fulfilment rejected · payment reversed',
    traceId: 'd12dd61db096b013d91861fd110e2e35',
    steps: [
      { service: 'Payment', state: 'AUTHORIZED', description: 'Forward transaction committed' },
      { service: 'Fulfilment', state: 'REJECTED', description: 'Capacity rule rejected the request', decision: 'FULFILMENT_REJECTED · state change true' },
      { service: 'Workflow', state: 'COMPENSATION PENDING', description: 'Persisted saga issued a reversal command' },
      { service: 'Payment', state: 'PAYMENT COMPENSATED', description: 'Authorized payment was voided idempotently', decision: 'PAYMENT_COMPENSATED · state change true' },
      { service: 'Workflow', state: 'COMPENSATED', description: 'Saga reached its compensated terminal state' },
    ],
  },
}

export function DemoRecording({ scenario, step }: { scenario: string; step: number }) {
  const demo = recordedDemos[scenario] ?? recordedDemos.duplicate
  const visibleSteps = demo.steps.slice(0, Math.max(1, Math.min(step, demo.steps.length)))
  const finished = visibleSteps.length === demo.steps.length

  return <main className="recording-page">
    <header><a className="wordmark"><span className="mark">EL</span>EventLab</a><span className="recording-badge"><i /> Recorded demonstration</span></header>
    <section className="recording-heading"><div><p className="eyebrow">{demo.label} · deterministic scenario</p><h1>{demo.title}</h1><p>{demo.invariant}</p></div><div className="recording-trace"><small>Trace</small><code>{demo.traceId}</code></div></section>
    <section className="recording-timeline">{visibleSteps.map((item, index) => <article className={index === visibleSteps.length - 1 ? 'current' : ''} key={`${item.state}-${index}`}>
      <span className="timeline-index">{String(index + 1).padStart(2, '0')}</span>
      <div><small>{item.service}</small><h2>{item.state}</h2><p>{item.description}</p></div>
      {item.decision && <code>{item.decision}</code>}
    </article>)}</section>
    <footer className="recording-footer"><span>{finished ? 'INVARIANT PROVED' : 'DELIVERY OBSERVED'}</span><div className="recording-progress">{demo.steps.map((_, index) => <i className={index < visibleSteps.length ? 'active' : ''} key={index} />)}</div><span>{visibleSteps.length} / {demo.steps.length}</span></footer>
  </main>
}
