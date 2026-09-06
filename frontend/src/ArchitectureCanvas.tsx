import { type KeyboardEvent, useEffect, useState } from 'react'
import './architecture-canvas.css'

export type CanvasEvent = {
  sequence: number
  eventId: string
  eventType: string
  service: string
  state: string
  description: string
  observedAt: string
  traceId?: string
  duplicateDelivery: boolean
}

type NodeId = 'workflow' | 'payment' | 'fulfilment' | 'broker' | 'console'
type RouteId = 'workflow-lane' | 'payment-lane' | 'fulfilment-lane' | 'evidence-lane'
type FlowKind = 'normal' | 'duplicate' | 'failure' | 'compensation' | 'recovery'
type EventFlow = { path: string; label: string; kind: FlowKind }
type RouteDefinition = {
  id: RouteId
  title: string
  path: string
  direction: string
  transport: string
  contracts: string[]
  guarantee: string
  matches: (event: CanvasEvent) => boolean
}

const nodes: { id: NodeId; title: string; detail: string; x: number; y: number }[] = [
  { id: 'workflow', title: 'Workflow', detail: 'Saga · inbox · outbox · database', x: 25, y: 35 },
  { id: 'payment', title: 'Payment', detail: 'Authorize / compensate · database', x: 675, y: 35 },
  { id: 'broker', title: 'Service Bus', detail: 'Commands + business events', x: 350, y: 185 },
  { id: 'console', title: 'Lab Console', detail: 'Persisted evidence → browser SSE', x: 25, y: 335 },
  { id: 'fulfilment', title: 'Fulfilment', detail: 'Execute / reject / retry · database', x: 675, y: 335 },
]

const reliabilityByNode: Record<NodeId, string[]> = {
  workflow: ['inbox', 'outbox', 'saga DB'],
  payment: ['inbox', 'outbox', 'payment DB'],
  fulfilment: ['inbox', 'outbox', 'DLQ', 'fulfilment DB'],
  broker: ['2 command queues', 'events topic', '2 subscriptions'],
  console: ['evidence DB', 'SSE projection'],
}

const routes: RouteDefinition[] = [
  {
    id: 'workflow-lane',
    title: 'Workflow orchestration lane',
    path: 'M300 85 H487 V185',
    direction: 'Workflow ↔ Service Bus',
    transport: 'Command queues and business-events / workflow-events',
    contracts: ['workflow.started', 'payment.authorized', 'fulfilment.command-queued', 'workflow.completed', 'workflow.compensated'],
    guarantee: 'The saga consumes through an idempotent inbox and commits outgoing commands to a transactional outbox.',
    matches: event => event.service.startsWith('Workflow'),
  },
  {
    id: 'payment-lane',
    title: 'Payment lane',
    path: 'M675 85 H512 V185',
    direction: 'Workflow → Service Bus → Payment → business events',
    transport: 'payment-commands queue and business-events topic',
    contracts: ['payment.authorize', 'payment.authorized', 'payment.compensate', 'payment.compensated'],
    guarantee: 'Logical event IDs are deduplicated by the service inbox; results leave through its transactional outbox.',
    matches: event => event.eventType.startsWith('payment.'),
  },
  {
    id: 'fulfilment-lane',
    title: 'Fulfilment, retry and DLQ lane',
    path: 'M512 280 V385 H675',
    direction: 'Workflow → Service Bus → Fulfilment',
    transport: 'fulfilment-commands queue, business-events topic and native DLQ',
    contracts: ['fulfilment.request', 'fulfilment.attempt-failed', 'fulfilment.completed', 'fulfilment.rejected', 'fulfilment.dead-lettered'],
    guarantee: 'Transient failures are retried within a bounded budget; exhausted or incompatible commands are quarantined in the DLQ.',
    matches: event => event.eventType.startsWith('fulfilment.') && event.eventType !== 'fulfilment.recovery-requested',
  },
  {
    id: 'evidence-lane',
    title: 'Evidence subscription',
    path: 'M487 280 V385 H300',
    direction: 'Business events → Lab Console → browser SSE',
    transport: 'business-events / lab-console-events subscription',
    contracts: ['all projected business events', 'trace IDs', 'event IDs', 'duplicate decisions'],
    guarantee: 'The console persists its projection before streaming it. Canvas routes are inferred from that evidence, not broker-hop telemetry.',
    matches: () => true,
  },
]

export function eventNode(event: CanvasEvent): NodeId | undefined {
  if (event.service.startsWith('Workflow')) return 'workflow'
  if (event.service === 'Payment') return 'payment'
  if (event.service === 'Fulfilment') return 'fulfilment'
  if (event.service === 'Recovery') return 'console'
  return undefined
}

export function eventFlow(event: CanvasEvent): EventFlow | undefined {
  const paymentToWorkflow = 'M675 85 H512 V185 H487 V85 H300'
  const workflowToPayment = 'M300 85 H487 V185 H512 V85 H675'
  const workflowToFulfilment = 'M300 85 H487 V280 H512 V385 H675'
  const fulfilmentToWorkflow = 'M675 385 H512 V280 H487 V85 H300'
  const workflowToConsole = 'M300 85 H487 V280 V385 H300'
  const consoleToFulfilment = 'M300 385 H487 V280 H512 V385 H675'
  const brokerToFulfilment = 'M512 280 V385 H675'
  const fulfilmentToBroker = 'M675 385 H512 V280'

  if (event.duplicateDelivery || event.state === 'DUPLICATE_IGNORED') return { path: paymentToWorkflow, label: 'duplicate delivery ignored', kind: 'duplicate' }
  if (event.eventType === 'workflow.started') return { path: workflowToPayment, label: 'authorize payment', kind: 'normal' }
  if (event.eventType === 'payment.authorized') return { path: paymentToWorkflow, label: 'payment authorized', kind: 'normal' }
  if (event.eventType === 'fulfilment.command-queued') return { path: workflowToFulfilment, label: 'request fulfilment', kind: 'normal' }
  if (event.eventType === 'fulfilment.attempt-failed') return { path: brokerToFulfilment, label: 'delivery retry', kind: 'failure' }
  if (event.eventType === 'fulfilment.recovery-requested') return { path: consoleToFulfilment, label: 'guarded replay', kind: 'recovery' }
  if (event.eventType === 'fulfilment.completed') return { path: fulfilmentToWorkflow, label: 'fulfilment completed', kind: 'normal' }
  if (event.eventType === 'fulfilment.rejected') return { path: fulfilmentToWorkflow, label: 'compensation requested', kind: 'compensation' }
  if (event.eventType === 'payment.compensated') return { path: paymentToWorkflow, label: 'payment compensated', kind: 'compensation' }
  if (event.eventType.startsWith('workflow.')) return { path: workflowToConsole, label: event.eventType.replaceAll('.', ' '), kind: event.state.includes('FAILED') ? 'failure' : 'normal' }
  if (event.eventType.startsWith('fulfilment.')) return { path: fulfilmentToBroker, label: event.eventType === 'fulfilment.dead-lettered' ? 'moved to dead-letter queue' : event.eventType.replaceAll('.', ' '), kind: 'failure' }
  return undefined
}

export function ArchitectureCanvas({ events, traceUrl }: { events: CanvasEvent[]; traceUrl: (id: string) => string }) {
  const [selected, setSelected] = useState<NodeId>('workflow')
  const [selectedRoute, setSelectedRoute] = useState<RouteId>()
  const [zoom, setZoom] = useState(1)
  const [following, setFollowing] = useState(true)
  const [showReliability, setShowReliability] = useState(false)
  const [isPlaying, setIsPlaying] = useState(true)
  const [speed, setSpeed] = useState(1)
  const [playbackIndex, setPlaybackIndex] = useState(() => events.length - 1)
  const visibleEvents = playbackIndex >= 0 ? events.slice(0, playbackIndex + 1) : []
  const selectedNode = nodes.find(node => node.id === selected)!
  const activeRoute = routes.find(route => route.id === selectedRoute)
  const observed = selected === 'console' ? visibleEvents : visibleEvents.filter(event => eventNode(event) === selected)
  const latest = playbackIndex >= 0 ? events[playbackIndex] : undefined
  const latestFlow = latest && eventFlow(latest)
  const queuedEvents = Math.max(0, events.length - playbackIndex - 1)
  const atEnd = playbackIndex >= events.length - 1

  useEffect(() => {
    if (events.length === 0 && playbackIndex >= 0) {
      setPlaybackIndex(-1)
      return
    }
    if (!isPlaying || atEnd) return
    const timer = window.setTimeout(
      () => setPlaybackIndex(current => Math.min(current + 1, events.length - 1)),
      playbackIndex < 0 ? 180 : 2700 / speed,
    )
    return () => window.clearTimeout(timer)
  }, [atEnd, events.length, isPlaying, playbackIndex, speed])

  useEffect(() => {
    const activeNode = latest && eventNode(latest)
    if (following && activeNode) {
      setSelected(activeNode)
      setSelectedRoute(undefined)
    }
  }, [following, latest])

  function inspectRoute(route: RouteDefinition) {
    setSelectedRoute(route.id)
    setFollowing(false)
  }

  function handleRouteKey(event: KeyboardEvent<SVGPathElement>, route: RouteDefinition) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      inspectRoute(route)
    }
  }

  function step(offset: number) {
    setIsPlaying(false)
    setPlaybackIndex(current => Math.min(Math.max(current + offset, 0), events.length - 1))
  }

  return <section className="architecture-canvas" aria-label="Live evidence map">
    <div className="canvas-heading">
      <div><p className="eyebrow">Live evidence map</p><h3>Follow the evidence through the system</h3></div>
      <div className="canvas-controls">
        <button type="button" aria-label="Zoom out architecture" disabled={zoom <= 1} onClick={() => setZoom(value => Math.max(1, value - .25))}>−</button>
        <output aria-label="Architecture zoom">{Math.round(zoom * 100)}%</output>
        <button type="button" aria-label="Zoom in architecture" disabled={zoom >= 2} onClick={() => setZoom(value => Math.min(2, value + .25))}>+</button>
        <button type="button" onClick={() => setZoom(1)}>Fit map</button>
        <button type="button" className={showReliability ? 'following' : ''} aria-pressed={showReliability} onClick={() => setShowReliability(value => !value)}>Reliability {showReliability ? 'on' : 'off'}</button>
        <button type="button" className={following ? 'following' : ''} aria-pressed={following} onClick={() => setFollowing(value => !value)}>{following ? 'Following live' : 'Follow live'}</button>
      </div>
    </div>
    <p>Animated tokens replay an inferred route when persisted evidence arrives; they do not measure individual broker hops. Select a lane to inspect its contracts and guarantee.</p>

    <div className="canvas-live-status" aria-live="polite">
      <i className={latestFlow?.kind ?? 'idle'} />
      {latest ? <><strong>{latest.eventType}</strong><span>{latestFlow?.label ?? 'event observed'} · evidence #{latest.sequence}{queuedEvents > 0 && <>{' · '}{queuedEvents} queued</>}</span></> : <><strong>Waiting for evidence</strong><span>Run an experiment to start live playback</span></>}
    </div>

    <div className="playback-bar" aria-label="Evidence playback controls">
      <button type="button" disabled={events.length === 0} onClick={() => { setPlaybackIndex(0); setIsPlaying(true) }}>↺ Replay</button>
      <button type="button" aria-label="Previous event" disabled={playbackIndex <= 0} onClick={() => step(-1)}>←</button>
      <button type="button" disabled={events.length === 0 || (isPlaying && atEnd)} onClick={() => setIsPlaying(value => !value)}>{isPlaying ? (atEnd ? 'Caught up' : 'Pause') : 'Resume'}</button>
      <button type="button" aria-label="Next event" disabled={playbackIndex >= events.length - 1} onClick={() => step(1)}>→</button>
      <label>Speed <select aria-label="Playback speed" value={speed} onChange={event => setSpeed(Number(event.target.value))}><option value={0.5}>0.5×</option><option value={1}>1×</option><option value={2}>2×</option></select></label>
      <output>{events.length === 0 ? '0 / 0' : `${Math.max(0, playbackIndex + 1)} / ${events.length}`}</output>
    </div>

    <div className="canvas-legend" aria-label="Flow color legend">
      <span><i className="normal" />Normal</span><span><i className="duplicate" />Duplicate</span><span><i className="failure" />Retry / DLQ</span><span><i className="compensation" />Compensation</span><span><i className="recovery" />Recovery</span>
    </div>

    <div className="canvas-workspace">
      <div className="canvas-viewport" tabIndex={0} role="region" aria-label="System map; scroll to explore when zoomed">
        <div className="canvas-surface" style={{ width: `${zoom * 100}%`, maxWidth: `${1600 * zoom}px` }}>
          <svg viewBox="0 0 1000 465" aria-label="Inspectable architecture routes">
            <path className="canvas-routes" aria-hidden="true" d="M300 85 H487 V185 M675 85 H512 V185 M487 280 V385 H300 M512 280 V385 H675" />
            <text aria-hidden="true" x="315" y="66">commands / events</text><text aria-hidden="true" x="557" y="66">authorize / void</text>
            <text aria-hidden="true" x="318" y="417">event subscription</text><text aria-hidden="true" x="549" y="417">request / result</text>
            {routes.map(route => <path key={route.id} d={route.path} role="button" tabIndex={0} aria-label={`Inspect ${route.title}`} className={`route-hit ${selectedRoute === route.id ? 'selected' : ''}`} onClick={() => inspectRoute(route)} onKeyDown={event => handleRouteKey(event, route)} />)}
            {latestFlow && <g key={latest!.sequence} className={`message-flow ${latestFlow.kind}`}>
              <path d={latestFlow.path} />
              <circle r="8"><animateMotion path={latestFlow.path} dur="2.4s" fill="freeze" /><animate attributeName="opacity" values="0;1;1;0" keyTimes="0;.08;.82;1" dur="2.4s" fill="freeze" /></circle>
            </g>}
          </svg>
          {nodes.map(node => {
            const count = node.id === 'console' ? visibleEvents.length : visibleEvents.filter(event => eventNode(event) === node.id).length
            const active = latest && eventNode(latest) === node.id
            return <button key={`${node.id}-${active ? latest?.sequence : 'idle'}`} type="button" className={`canvas-node ${!selectedRoute && selected === node.id ? 'selected' : ''} ${count > 0 ? 'observed' : ''} ${active ? 'latest' : ''}`} style={{ left: `${node.x / 10}%`, top: `${node.y / 4.65}%` }} aria-label={`${node.title} component`} aria-pressed={!selectedRoute && selected === node.id} onClick={() => { setSelected(node.id); setSelectedRoute(undefined); setFollowing(false) }}>
              <strong>{node.title}</strong><small>{node.detail}</small>
              <span>{node.id === 'broker' ? 'Transport · select a lane' : `${count} observed event${count === 1 ? '' : 's'}`}</span>
              {showReliability && <span className="reliability-badges">{reliabilityByNode[node.id].map(boundary => <em key={boundary}>{boundary}</em>)}</span>}
            </button>
          })}
        </div>
      </div>

      <div className="canvas-inspector">
        {activeRoute ? <>
          <h4>{activeRoute.title}</h4>
          <div className="route-explanation">
            <p className="eyebrow">Architectural route · not hop telemetry</p>
            <dl><dt>Direction</dt><dd>{activeRoute.direction}</dd><dt>Transport</dt><dd>{activeRoute.transport}</dd><dt>Guarantee</dt><dd>{activeRoute.guarantee}</dd></dl>
            <strong>{visibleEvents.filter(activeRoute.matches).length} matching evidence event{visibleEvents.filter(activeRoute.matches).length === 1 ? '' : 's'}</strong>
            <div className="contract-list">{activeRoute.contracts.map(contract => <code key={contract}>{contract}</code>)}</div>
          </div>
        </> : <>
          <h4>{selectedNode.title} · recorded evidence</h4>
          {selected === 'broker' ? <p>Payment and fulfilment commands travel through their queues. Business events fan out to Workflow and Lab Console subscriptions. Select a route on the map for its exact contracts and guarantee.</p> : <>
            {observed.length === 0 && <p>No observations for this component yet. This does not indicate a failure.</p>}
            <ul>{observed.map(event => <li key={event.sequence}><div><strong>{event.state.replaceAll('_', ' ')}</strong>{event.duplicateDelivery && <mark>duplicate</mark>}<p>{event.description}</p><small>{event.eventType} · observed {new Date(event.observedAt).toLocaleTimeString()}</small><details><summary>Event identity</summary><code>{event.eventId}</code></details></div>{event.traceId && <a href={traceUrl(event.traceId)} target="_blank" rel="noreferrer">Inspect trace ↗</a>}</li>)}</ul>
          </>}
        </>}
      </div>
    </div>
  </section>
}
