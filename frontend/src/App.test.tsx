import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App, grafanaTraceUrl } from './App'

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
    const panes = JSON.parse(url.searchParams.get('panes') ?? '{}')

    expect(panes.trace.datasource).toBe('tempo')
    expect(panes.trace.queries).toEqual([
      { refId: 'A', query: '0123456789abcdef', queryType: 'traceId' },
    ])
    expect(panes.trace.range).toEqual({ from: 'now-1h', to: 'now' })
  })
})
