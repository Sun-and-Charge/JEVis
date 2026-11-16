/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import { loadReview, reviewReading, type ReadingMeter, type ReviewItem } from '../src/api/readings'
import { toDateTimeInput } from '../src/edit/edit'
import {
  ALL_SITES,
  commentProblem,
  exchangePrefill,
  issueClass,
  itemsOfSite,
  numberInput,
  reviewActions,
  reviewMeter,
  reviewRowClass,
  reviewSites,
} from '../src/readings/review'

/* E8.5 review of contradictory readings: list logic, with neutral data ("Musterwerk", "Lager Nord"). */

function item(meterId: number, overrides: Partial<ReviewItem> = {}): ReviewItem {
  return {
    meterId,
    meterName: 'Zähler ' + meterId,
    site: { id: 1001, name: 'Musterwerk' },
    row: { id: meterId + 1000, name: 'Daten ' + meterId, unit: 'kWh', counter: true, canEnter: true, canDelete: true, canNote: true },
    timestamp: '2026-10-01T00:00:00.000+02:00',
    value: 11800,
    note: null,
    manual: true,
    previous: { timestamp: '2026-09-01T00:00:00.000+02:00', value: 12100 },
    expected: { value: 12350, low: 12200, high: 12500 },
    issues: ['COUNTER_DECREASE'],
    canExchange: true,
    ...overrides,
  }
}

describe('review list logic', () => {
  it('builds one tab per site with counts, sorted by name, items without site last', () => {
    const items = [
      item(1),
      item(2, { site: { id: 1002, name: 'Lager Nord' } }),
      item(3),
      item(4, { site: null }),
    ]
    const tabs = reviewSites(items, 'de', 'Ohne Standort')
    expect(tabs.map((t) => [t.key, t.name, t.count])).toEqual([
      ['1002', 'Lager Nord', 1],
      ['1001', 'Musterwerk', 2],
      ['none', 'Ohne Standort', 1],
    ])
    expect(itemsOfSite(items, '1001').map((i) => i.meterId)).toEqual([1, 3])
    expect(itemsOfSite(items, 'none').map((i) => i.meterId)).toEqual([4])
    expect(itemsOfSite(items, ALL_SITES)).toHaveLength(4)
    expect(reviewSites([], 'de', 'x')).toEqual([])
  })

  it('colours the issues: counter decrease red, the others amber', () => {
    expect(issueClass('COUNTER_DECREASE')).toBe('is-overdue')
    expect(issueClass('FAR_FROM_EXPECTED')).toBe('is-dueSoon')
    expect(issueClass('LIMIT')).toBe('is-dueSoon')
    expect(issueClass('SOMETHING_NEW')).toBe('is-unknown')
    expect(reviewRowClass(item(1, { issues: ['LIMIT', 'COUNTER_DECREASE'] }))).toBe('is-overdue')
    expect(reviewRowClass(item(1, { issues: ['FAR_FROM_EXPECTED'] }))).toBe('is-dueSoon')
  })

  it('allows the actions by the rights of the row; exchange only for counters', () => {
    expect(reviewActions(item(1))).toEqual({ correct: true, exchange: true, confirm: true, ignore: true })
    const row = { ...item(1).row, counter: false, canEnter: false, canDelete: false, canNote: false }
    expect(reviewActions(item(1, { row }))).toEqual({ correct: false, exchange: false, confirm: false, ignore: false })
    expect(reviewActions(item(1, { canExchange: false })).exchange).toBe(false)
  })

  it('takes the meter of the reading list for the correction, else builds one from the item', () => {
    const listed = { id: 1, name: 'Zähler 1', row: { id: 1001, mode: 'DAY', timeZone: 'Europe/Berlin' } } as unknown as ReadingMeter
    expect(reviewMeter(item(1), [listed])).toBe(listed)
    const built = reviewMeter(item(2, { expected: null }), [listed])
    expect(built.id).toBe(2)
    expect(built.name).toBe('Zähler 2')
    expect(built.row).toMatchObject({ id: 1002, unit: 'kWh', counter: true, mode: 'SPECIFIC_DATETIME', canEnter: true })
    expect(built.readings).toEqual([])
    expect(built.canExchange).toBe(true)
  })

  it('prefills the meter exchange with the time and the value as typed by the user', () => {
    expect(numberInput(11800.5, 'de')).toBe('11800,5')
    expect(numberInput(1234567.25, 'en')).toBe('1234567.25')
    expect(numberInput('n/a', 'de')).toBe('')
    expect(numberInput(null, 'de')).toBe('')
    const p = exchangePrefill(item(1, { value: 15.5, timestamp: '2026-10-01T06:30:42.000+02:00' }), 'de')
    expect(p).toEqual({ when: toDateTimeInput(Date.parse('2026-10-01T06:30:00.000+02:00')), newReading: '15,5' })
    expect(exchangePrefill(item(1, { timestamp: 'x' }), 'de').when).toBe('')
  })

  it('checks the comment like the server (line breaks allowed)', () => {
    expect(commentProblem('', 500)).toBeNull()
    expect(commentProblem('Vor Ort geprüft\nZähler ok', 500)).toBeNull()
    expect(commentProblem('a'.repeat(501), 500)).toBe('tooLong')
    expect(commentProblem('  ' + 'a'.repeat(500) + '  ', 500)).toBeNull()
    expect(commentProblem('a\u0007b', 500)).toBe('invalid')
    expect(commentProblem('a\tb', 500)).toBe('invalid')
  })

  it('calls the review endpoints', async () => {
    const get = vi.fn(async () => ({ items: [], count: 0, truncated: false }))
    await loadReview({ get } as never, 'de', 365)
    await loadReview({ get } as never, 'en', 90, 1001)
    expect(get.mock.calls.map((c) => (c as unknown[])[0])).toEqual(['/web/readings/review?lang=de&days=365', '/web/readings/review?lang=en&days=90&site=1001'])
    const post = vi.fn(async () => ({ timestamp: 't', action: 'CONFIRM' }))
    await reviewReading({ post } as never, 5101, { timestamp: 't', action: 'CONFIRM', comment: null, value: 1 }, 'de')
    expect(post).toHaveBeenCalledWith('/web/readings/review/5101?lang=de', { timestamp: 't', action: 'CONFIRM', comment: null, value: 1 })
  })
})
