import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'

describe('EventLab application shell', () => {
  it('presents the three agreed initial scenarios', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'Duplicate payment result' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Fulfilment unavailable' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Fulfilment rejected' })).toBeInTheDocument()
  })
})
