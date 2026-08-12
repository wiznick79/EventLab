import '@testing-library/jest-dom/vitest'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App, grafanaDashboardUrl, grafanaTraceUrl, traceEvidence, traceUrl } from './App'
import { PortfolioTour } from './PortfolioTour'

describe('EventLab experiment console', () => {
  it('presents the executable baseline and the three planned failure scenarios', () => {
    render(<App />)

    const runButtons = screen.getAllByRole('button', { name: /run experiment/i })
    expect(runButtons).toHaveLength(5)
    expect(runButtons[0]).toBeEnabled()
    expect(screen.getByRole('heading', { name: 'Successful payment workflow' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Duplicate payment result' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Fulfilment unavailable' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Fulfilment rejected' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Out-of-order update' })).toBeInTheDocument()
  })

  it('builds a Grafana Explore link that looks up a trace by ID', () => {
    const url = new URL(grafanaTraceUrl('0123456789abcdef'))
    const left = JSON.parse(url.searchParams.get('left') ?? '{}')

    expect(left.datasource).toBe('tempo')
    expect(left.queries).toEqual([
      { query: '0123456789abcdef', queryType: 'traceql' },
    ])
    expect(left.range).toEqual({ from: 'now-1h', to: 'now' })
  })

  it('uses the runtime Grafana instance when configured', () => {
    const url = traceUrl('0123456789abcdef', 'https://grafana.example.test')

    expect(url).toMatch(/^https:\/\/grafana\.example\.test\/explore\?left=/)
    expect(decodeURIComponent(url)).toContain('0123456789abcdef')
  })

  it('links to the provisioned operations dashboard', () => {
    expect(grafanaDashboardUrl('https://grafana.example.test')).toBe(
      'https://grafana.example.test/d/eventlab-operations/eventlab-operations?from=now-1h&to=now',
    )
  })

  it('maps invariant claims to explicit trace evidence', () => {
    expect(traceEvidence('DUPLICATE_IGNORED')).toEqual({
      span: 'eventlab.workflow.inbox.decision',
      decision: 'DUPLICATE_IGNORED',
    })
    expect(traceEvidence('STALE_IGNORED')).toEqual({
      span: 'eventlab.workflow.version.decision',
      decision: 'STALE_IGNORED',
    })
    expect(traceEvidence('COMPLETED')).toBeUndefined()
  })

  it('renders a backend-independent portfolio tour', () => {
    const tour = render(<PortfolioTour />)
    const page = within(tour.container)

    expect(page.getByRole('heading', { name: /reliable messaging/i })).toBeInTheDocument()
    expect(page.getByRole('heading', { name: 'What each experiment proves' })).toBeInTheDocument()
    expect(page.getByText(/STALE_IGNORED · received 1 · current 2/)).toBeInTheDocument()
    expect(page.getByRole('heading', { name: 'Watch the invariant emerge' })).toBeInTheDocument()
    expect(page.getAllByRole('img', { name: /EventLab timeline recording/ })).toHaveLength(3)
    expect(page.queryByRole('button', { name: /run experiment/i })).not.toBeInTheDocument()
  })
})
