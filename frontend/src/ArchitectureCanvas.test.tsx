import '@testing-library/jest-dom/vitest'
import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ArchitectureCanvas, eventFlow, eventNode, type CanvasEvent } from './ArchitectureCanvas'

const events: CanvasEvent[] = [
  { sequence: 1, eventId: 'event-1', eventType: 'workflow.started', service: 'Workflow', state: 'PAYMENT_PENDING', description: 'Accepted', observedAt: '2026-09-06T12:00:00Z', traceId: 'trace-1', duplicateDelivery: false },
  { sequence: 2, eventId: 'event-2', eventType: 'payment.authorized', service: 'Payment', state: 'FULFILMENT_PENDING', description: 'Authorized', observedAt: '2026-09-06T12:00:01Z', traceId: 'trace-2', duplicateDelivery: false },
  { sequence: 3, eventId: 'event-2', eventType: 'payment.authorized', service: 'Workflow inbox', state: 'DUPLICATE_IGNORED', description: 'Second delivery rejected', observedAt: '2026-09-06T12:00:02Z', traceId: 'trace-3', duplicateDelivery: true },
  { sequence: 4, eventId: 'event-4', eventType: 'payment.compensated', service: 'Payment', state: 'PAYMENT_COMPENSATED', description: 'Payment voided', observedAt: '2026-09-06T12:00:03Z', traceId: 'trace-4', duplicateDelivery: false },
]

describe('live architecture canvas', () => {
  it('maps projected service names to topology nodes', () => {
    expect(eventNode(events[0])).toBe('workflow')
    expect(eventNode(events[1])).toBe('payment')
    expect(eventNode(events[2])).toBe('workflow')
  })

  it('filters persisted evidence by selected component and links its trace', () => {
    render(<ArchitectureCanvas events={events} traceUrl={id => `/traces/${id}`} />)
    const map = screen.getByRole('region', { name: /system map/i })
    expect(within(map).getByRole('button', { name: /^workflow component$/i })).toHaveTextContent('2 observed events')
    fireEvent.click(within(map).getByRole('button', { name: /^workflow component$/i }))
    expect(screen.getByText('DUPLICATE IGNORED')).toBeInTheDocument()

    fireEvent.click(within(map).getByRole('button', { name: /^payment component$/i }))
    expect(screen.getByText('PAYMENT COMPENSATED')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /inspect trace/i })[0]).toHaveAttribute('href', '/traces/trace-2')
  })

  it('labels broker routes as architectural rather than observed hops', () => {
    const view = render(<ArchitectureCanvas events={events} traceUrl={id => id} />)
    fireEvent.click(within(view.container).getByRole('button', { name: /service bus/i }))
    expect(within(view.container).getByText(/select a route on the map for its exact contracts and guarantee/i)).toBeInTheDocument()
  })
  it('explains clickable routes with their transport, contracts and guarantee', () => {
    const view = render(<ArchitectureCanvas events={events} traceUrl={id => id} />)
    const map = within(view.container).getByRole('region', { name: /system map/i })
    fireEvent.click(within(map).getByRole('button', { name: /inspect payment lane/i }))
    expect(within(view.container).getByRole('heading', { name: 'Payment lane' })).toBeInTheDocument()
    expect(within(view.container).getByText(/payment-commands queue and business-events topic/i)).toBeInTheDocument()
    expect(within(view.container).getByText('payment.authorized')).toBeInTheDocument()
    expect(within(view.container).getByText(/deduplicated by the service inbox/i)).toBeInTheDocument()
  })

  it('replays and steps through persisted evidence independently of arrival time', () => {
    const view = render(<ArchitectureCanvas events={events} traceUrl={id => id} />)
    fireEvent.click(within(view.container).getByRole('button', { name: /replay/i }))
    expect(within(view.container).getByText('workflow.started')).toBeInTheDocument()
    expect(within(view.container).getByText('1 / 4')).toBeInTheDocument()
    const map = within(view.container).getByRole('region', { name: /system map/i })
    expect(within(map).getByRole('button', { name: /^workflow component$/i })).toHaveTextContent('1 observed event')
    expect(within(map).getByRole('button', { name: /^payment component$/i })).toHaveTextContent('0 observed events')
    fireEvent.click(within(map).getByRole('button', { name: /^payment component$/i }))
    expect(within(view.container).getByText(/no observations for this component yet/i)).toBeInTheDocument()
    expect(within(view.container).queryByText('PAYMENT COMPENSATED')).not.toBeInTheDocument()
    fireEvent.click(within(view.container).getByRole('button', { name: /next event/i }))
    expect(within(view.container).getByText('payment.authorized')).toBeInTheDocument()
    expect(within(view.container).getByText('2 / 4')).toBeInTheDocument()
    expect(within(map).getByRole('button', { name: /^payment component$/i })).toHaveTextContent('1 observed event')
    expect(within(view.container).queryByText('PAYMENT COMPENSATED')).not.toBeInTheDocument()
    fireEvent.change(within(view.container).getByLabelText(/playback speed/i), { target: { value: '2' } })
    expect(within(view.container).getByLabelText(/playback speed/i)).toHaveValue('2')
  })

  it('reveals reliability boundaries without changing the topology', () => {
    const view = render(<ArchitectureCanvas events={events} traceUrl={id => id} />)
    fireEvent.click(within(view.container).getByRole('button', { name: /reliability off/i }))
    const map = within(view.container).getByRole('region', { name: /system map/i })
    const workflow = within(map).getByRole('button', { name: /^workflow component$/i })
    expect(within(workflow).getByText('inbox')).toBeInTheDocument()
    expect(within(workflow).getByText('outbox')).toBeInTheDocument()
  })

  it('caps the fitted map while preserving proportional zoom', () => {
    const view = render(<ArchitectureCanvas events={events} traceUrl={id => id} />)
    const surface = view.container.querySelector<HTMLElement>('.canvas-surface')!
    expect(surface).toHaveStyle({ width: '100%', maxWidth: '1600px' })

    fireEvent.click(within(view.container).getByRole('button', { name: /zoom in architecture/i }))
    expect(surface).toHaveStyle({ width: '125%', maxWidth: '2000px' })
    fireEvent.click(within(view.container).getByRole('button', { name: /fit map/i }))
    expect(surface).toHaveStyle({ width: '100%', maxWidth: '1600px' })
  })

  it('plays the latest evidence route and distinguishes duplicate and compensation flows', () => {
    expect(eventFlow(events[2])).toMatchObject({ kind: 'duplicate', label: 'duplicate delivery ignored' })
    expect(eventFlow(events[3])).toMatchObject({ kind: 'compensation', label: 'payment compensated' })
    expect(eventFlow({ ...events[0], eventType: 'fulfilment.attempt-failed', service: 'Fulfilment', state: 'RETRY_SCHEDULED' }))
      .toMatchObject({ kind: 'failure', label: 'delivery retry', path: 'M512 280 V385 H675' })
    expect(eventFlow({ ...events[0], eventType: 'fulfilment.dead-lettered', service: 'Fulfilment', state: 'DEAD_LETTERED' }))
      .toMatchObject({ kind: 'failure', label: 'moved to dead-letter queue', path: 'M675 385 H512 V280' })

    const view = render(<ArchitectureCanvas events={events} traceUrl={id => id} />)
    expect(within(view.container).getByText('payment.compensated')).toBeInTheDocument()
    expect(view.container.querySelector('.message-flow.compensation')).toBeInTheDocument()
    expect(within(view.container).getByRole('button', { name: /^payment component$/i })).toHaveAttribute('aria-pressed', 'true')
  })

  it('paces newly arriving evidence instead of skipping to the newest event', () => {
    vi.useFakeTimers()
    const view = render(<ArchitectureCanvas events={[]} traceUrl={id => id} />)
    view.rerender(<ArchitectureCanvas events={events.slice(0, 2)} traceUrl={id => id} />)

    act(() => vi.advanceTimersByTime(180))
    expect(within(view.container).getByText('workflow.started')).toBeInTheDocument()
    expect(within(view.container).getByText(/1 queued/)).toBeInTheDocument()

    act(() => vi.advanceTimersByTime(2700))
    expect(within(view.container).getByText('payment.authorized')).toBeInTheDocument()
    vi.useRealTimers()
  })

})