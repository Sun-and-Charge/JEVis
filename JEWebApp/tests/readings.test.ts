/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import { loadReadings, readingPhotoPath, readingWarnings, saveReading, uploadReadingPhoto } from '../src/api/readings'
import {
  addPeriod,
  dueState,
  estimate,
  isZeroPeriod,
  outsideExpected,
  parseNumber,
  parsePeriod,
  readingPoints,
  wallTimeToMs,
  type Point,
} from '../src/readings/expected'
import { httpError } from './fixtures/edit'

/*
 * Meter reading (E8.1): the rules ported from the app (ExpectedValues.estimate, Due.state, EntryValidator.parseNumber)
 * with the cases the server's tests use, and the API client.
 */

const DAY = 86_400_000
const T0 = Date.parse('2026-01-01T00:00:00Z')
const at = (days: number, value: number): Point => ({ ts: T0 + days * DAY, value })
const counter = { counter: true, period: 'P1M' }
const monthly = { counter: false, period: 'P1M' }

function expectRange(actual: { value: number; low: number; high: number } | null, value: number, low: number, high: number) {
  expect(actual).not.toBeNull()
  expect(actual!.value).toBeCloseTo(value, 6)
  expect(actual!.low).toBeCloseTo(low, 6)
  expect(actual!.high).toBeCloseTo(high, 6)
}

describe('expected value of counters', () => {
  it('uses the average rate of the intervals, the range from the lowest to the highest rate', () => {
    // rates 10 / day and 20 / day → average 15, spread 10 → 5 … 25 per day, 10 days after the last reading
    const readings = [at(0, 1000), at(10, 1100), at(20, 1300)]
    expectRange(estimate(counter, readings, T0 + 30 * DAY), 1450, 1350, 1550)
  })

  it('leaves out a decreasing interval (meter change, overflow)', () => {
    // 1300 → 1250 says nothing about the consumption: rates 10 and 20 per day as before, from the last reading 1250
    const readings = [at(0, 1000), at(10, 1100), at(20, 1300), at(30, 1250)]
    expectRange(estimate(counter, readings, T0 + 40 * DAY), 1400, 1300, 1500)
  })

  it('accepts any order of the readings', () => {
    const readings = [at(20, 1300), at(0, 1000), at(30, 1250), at(10, 1100)]
    expectRange(estimate(counter, readings, T0 + 40 * DAY), 1400, 1300, 1500)
  })

  it('keeps a spread of at least 20 % of the average rate', () => {
    // equal rates of 10 per day: spread 2 per day
    const readings = [at(0, 100), at(10, 200), at(20, 300)]
    expectRange(estimate(counter, readings, T0 + 30 * DAY), 400, 380, 420)
  })

  it('does not go below the last reading', () => {
    // rates 1 and 100 per day: average 50.5, spread 99 → low rate 0
    const readings = [at(0, 0), at(10, 10), at(20, 1010)]
    expectRange(estimate(counter, readings, T0 + 30 * DAY), 1515, 1010, 2505)
  })

  it('uses the last 11 readings only', () => {
    // two old intervals with 1000 per day are outside the window, ten intervals with 10 per day remain
    const old = [at(0, 0), at(1, 1000), at(2, 2000)]
    const recent = Array.from({ length: 10 }, (_, i) => at(3 + i, 2000 + 10 * (i + 1)))
    expectRange(estimate(counter, [...old, ...recent], T0 + 13 * DAY), 2110, 2108, 2112)
  })

  it('needs two readings, a timestamp after the last reading and a usable interval', () => {
    expect(estimate(counter, [], T0)).toBeNull()
    expect(estimate(counter, [at(0, 100)], T0 + DAY)).toBeNull()
    const readings = [at(0, 100), at(10, 200)]
    // before and at the last reading
    expect(estimate(counter, readings, T0 + 5 * DAY)).toBeNull()
    expect(estimate(counter, readings, T0 + 10 * DAY)).toBeNull()
    expect(estimate(counter, readings, T0 + 10 * DAY + 999)).toBeNull()
    // only decreasing intervals or no time between
    expect(estimate(counter, [at(0, 200), at(10, 100)], T0 + 20 * DAY)).toBeNull()
    expect(estimate(counter, [at(0, 100), at(0, 200)], T0 + 20 * DAY)).toBeNull()
  })

  it('counters are estimated also without period', () => {
    expect(estimate({ counter: true, period: null }, [at(0, 100), at(10, 200)], T0 + 20 * DAY)).not.toBeNull()
  })
})

describe('expected value of other rows', () => {
  it('averages the values with a margin of 20 % of the average', () => {
    // average 12, spread 4 → margin max(0.8, 2.4) = 2.4
    expectRange(estimate(monthly, [at(0, 10), at(31, 12), at(59, 14)], T0 + 90 * DAY), 12, 7.6, 16.4)
  })

  it('uses a margin of 20 % of the spread when it is larger', () => {
    // average 50, spread 100 → margin max(20, 10) = 20
    expectRange(estimate(monthly, [at(0, 0), at(31, 100)], T0 + 90 * DAY), 50, -20, 120)
  })

  it('uses the absolute average for negative values', () => {
    expectRange(estimate(monthly, [at(0, -10), at(31, -10)], T0 + 90 * DAY), -10, -12, -8)
  })

  it('uses the last 10 values only, whatever the timestamp', () => {
    const values = [at(0, 1000), at(1, 1000), ...Array.from({ length: 10 }, (_, i) => at(2 + i, 10))]
    expectRange(estimate(monthly, values, T0), 10, 8, 12)
  })

  it('needs two values and a period', () => {
    expect(estimate(monthly, [at(0, 10)], T0 + DAY)).toBeNull()
    expect(estimate({ counter: false, period: null }, [at(0, 10), at(1, 12)], T0 + DAY)).toBeNull()
    expect(estimate({ counter: false, period: 'PT0S' }, [at(0, 10), at(1, 12)], T0 + DAY)).toBeNull()
    expect(estimate({ counter: false, period: 'P0D' }, [at(0, 10), at(1, 12)], T0 + DAY)).toBeNull()
  })

  it('tells whether a value is outside the range', () => {
    const range = { value: 10, low: 8, high: 12 }
    expect(outsideExpected(7.9, range)).toBe(true)
    expect(outsideExpected(8, range)).toBe(false)
    expect(outsideExpected(12, range)).toBe(false)
    expect(outsideExpected(12.1, range)).toBe(true)
    expect(outsideExpected(1000, null)).toBe(false)
  })
})

describe('reading points', () => {
  it('keeps the numeric samples', () => {
    const points = readingPoints([
      { timestamp: '2026-10-01T00:00:00.000+02:00', value: 11800 },
      { timestamp: '2026-09-01T00:00:00.000+02:00', value: '11500,5' },
      { timestamp: '2026-08-01T00:00:00.000+02:00', value: null },
      { timestamp: 'kaputt', value: 1 },
      { timestamp: '2026-07-01T00:00:00.000+02:00', value: 'Zähler defekt' },
    ])
    expect(points).toEqual([
      { ts: Date.parse('2026-10-01T00:00:00.000+02:00'), value: 11800 },
      { ts: Date.parse('2026-09-01T00:00:00.000+02:00'), value: 11500.5 },
    ])
  })
})

describe('parseNumber', () => {
  it.each([
    ['12345,6', 12345.6],
    ['12345.6', 12345.6],
    ['1.234,5', 1234.5],
    ['1,234.5', 1234.5],
    ['1.234.567,8', 1234567.8],
    ['1,234,567.8', 1234567.8],
    ['12 345,6', 12345.6],
    ['12 345,6', 12345.6],
    ["1'234.5", 1234.5],
    ['  42  ', 42],
    ['-5,5', -5.5],
    ['+3', 3],
    ['0', 0],
    [',5', 0.5],
    ['1.234', 1.234],
    ['1,234', 1.234],
    ['1e3', 1000],
  ])('%s → %s', (text, value) => {
    expect(parseNumber(text)).toBeCloseTo(value, 9)
  })

  it.each(['', '   ', 'abc', '12a', '1,2,3', '1.2.3', '1,2.3,4', '-', 'NaN', 'Infinity', '1e999'])('%s → null', (text) => {
    expect(parseNumber(text)).toBeNull()
  })

  it('handles null and undefined', () => {
    expect(parseNumber(null)).toBeNull()
    expect(parseNumber(undefined)).toBeNull()
  })
})

describe('periods and due state', () => {
  const zone = 'Europe/Berlin'
  const ms = (iso: string) => Date.parse(iso)

  it('parses ISO periods', () => {
    expect(parsePeriod('P1M')).toEqual({ years: 0, months: 1, weeks: 0, days: 0, hours: 0, minutes: 0, seconds: 0 })
    expect(parsePeriod('PT15M')).toEqual({ years: 0, months: 0, weeks: 0, days: 0, hours: 0, minutes: 15, seconds: 0 })
    expect(parsePeriod('p1y2m3dt4h')).toEqual({ years: 1, months: 2, weeks: 0, days: 3, hours: 4, minutes: 0, seconds: 0 })
    expect(parsePeriod('P1W')?.weeks).toBe(1)
    expect(parsePeriod(null)).toBeNull()
    expect(parsePeriod('P')).toBeNull()
    expect(parsePeriod('PT')).toBeNull()
    expect(parsePeriod('monthly')).toBeNull()
    expect(isZeroPeriod(parsePeriod('PT0S'))).toBe(true)
    expect(isZeroPeriod(null)).toBe(true)
    expect(isZeroPeriod(parsePeriod('P1D'))).toBe(false)
  })

  it('adds periods on the wall time of the zone', () => {
    const p = (s: string) => parsePeriod(s)!
    expect(addPeriod(ms('2026-10-01T00:00:00+02:00'), p('P1M'), zone)).toBe(ms('2026-11-01T00:00:00+01:00'))
    expect(addPeriod(ms('2026-01-31T00:00:00+01:00'), p('P1M'), zone)).toBe(ms('2026-02-28T00:00:00+01:00'))
    expect(addPeriod(ms('2026-12-01T00:00:00+01:00'), p('P1M'), zone)).toBe(ms('2027-01-01T00:00:00+01:00'))
    // the day of the DST change has 23 hours
    expect(addPeriod(ms('2026-03-28T12:00:00+01:00'), p('P1D'), zone)).toBe(ms('2026-03-29T12:00:00+02:00'))
    expect(addPeriod(ms('2026-03-29T00:00:00+01:00'), p('PT15M'), zone)).toBe(ms('2026-03-29T00:15:00+01:00'))
    expect(addPeriod(ms('2026-11-01T00:00:00+01:00'), p('P1Y'), zone)).toBe(ms('2027-11-01T00:00:00+01:00'))
  })

  it('reads input wall times in the zone', () => {
    expect(wallTimeToMs('2026-11-13T08:30', zone)).toBe(ms('2026-11-13T08:30:00+01:00'))
    expect(wallTimeToMs('2026-07-01T00:00:00', zone)).toBe(ms('2026-07-01T00:00:00+02:00'))
    expect(wallTimeToMs('2026-11-13', zone)).toBe(ms('2026-11-13T00:00:00+01:00'))
    // gap: 02:30 does not exist on the day of the DST start → 03:30 summer time
    expect(wallTimeToMs('2026-03-29T02:30', zone)).toBe(ms('2026-03-29T03:30:00+02:00'))
    // overlap: the earlier instant
    expect(wallTimeToMs('2026-10-25T02:30', zone)).toBe(ms('2026-10-25T02:30:00+02:00'))
    expect(wallTimeToMs('13.11.2026', zone)).toBeNull()
  })

  it('is NONE without period and DUE without reading', () => {
    const now = ms('2026-11-13T10:00:00+01:00')
    expect(dueState(null, ms('2026-10-01T00:00:00+02:00'), null, now, zone)).toBe('NONE')
    expect(dueState('PT0S', null, null, now, zone)).toBe('NONE')
    expect(dueState('P1M', null, null, now, zone)).toBe('DUE')
  })

  it('is OK before the next period, DUE within it and OVERDUE after it', () => {
    const last = ms('2026-10-01T00:00:00+02:00')
    const next = ms('2026-11-01T00:00:00+01:00')
    expect(dueState('P1M', last, next, ms('2026-10-15T10:00:00+02:00'), zone)).toBe('OK')
    expect(dueState('P1M', last, next, ms('2026-11-01T00:00:00+01:00'), zone)).toBe('DUE')
    expect(dueState('P1M', last, next, ms('2026-11-13T10:00:00+01:00'), zone)).toBe('DUE')
    expect(dueState('P1M', last, next, ms('2026-12-01T00:00:00+01:00'), zone)).toBe('OVERDUE')
    // without the suggestion of the server: one period after the last reading
    expect(dueState('P1M', last, null, ms('2026-11-13T10:00:00+01:00'), zone)).toBe('DUE')
    expect(dueState('P1D', last, null, ms('2026-11-13T10:00:00+01:00'), zone)).toBe('OVERDUE')
  })
})

describe('readings api', () => {
  it('builds the requests', async () => {
    const get = vi.fn(async () => ({ today: '2026-11-13', groups: [] }))
    const post = vi.fn(async () => ({ timestamp: 't', value: 1, warnings: [] }))
    const postForm = vi.fn(async () => ({ timestamp: 't', file: 'f' }))
    await loadReadings({ get } as never, 'de')
    expect(get).toHaveBeenLastCalledWith('/web/readings?lang=de')
    await loadReadings({ get } as never, 'en', 1001)
    expect(get).toHaveBeenLastCalledWith('/web/readings?lang=en&site=1001')
    const body = { timestamp: '2026-11-13T00:00:00', value: 12345.6, note: 'Zähler verschmutzt', confirm: false }
    await saveReading({ post } as never, 5101, body, 'de')
    expect(post).toHaveBeenCalledWith('/web/readings/5101?lang=de', body)
    expect(readingPhotoPath(5101, '2026-11-13T00:00:00.000+01:00')).toBe('/web/readings/5101/photo?timestamp=2026-11-13T00%3A00%3A00.000%2B01%3A00')
    const file = new File(['x'], 'foto.jpg', { type: 'image/jpeg' })
    await uploadReadingPhoto({ postForm } as never, 5101, '2026-11-13T00:00:00.000+01:00', file)
    const [path, form] = postForm.mock.calls[0] as unknown as [string, FormData]
    expect(path).toBe('/web/readings/5101/photo?timestamp=2026-11-13T00%3A00%3A00.000%2B01%3A00')
    expect((form.get('file') as File).name).toBe('foto.jpg')
  })

  it('reads the warnings of a 409', () => {
    expect(readingWarnings(httpError(409, { timestamp: 't', value: 1, warnings: ['Kleiner als der letzte Stand', 7, ''] }))).toEqual(['Kleiner als der letzte Stand'])
    expect(readingWarnings(httpError(409, 'kein JSON'))).toEqual([])
    expect(readingWarnings(httpError(400, { warnings: ['x'] }))).toBeNull()
    expect(readingWarnings(new Error('offline'))).toBeNull()
  })
})

describe('canRead', () => {
  const row = { canEnter: true } as unknown as NonNullable<import('../src/api/readings').ReadingMeter['row']>
  it('needs a row with the right to enter and no automatic recording', async () => {
    const { canRead } = await import('../src/readings/readings')
    expect(canRead({ row, problem: null })).toBe(true)
    expect(canRead({ row: null, problem: 'NO_TARGET' })).toBe(false)
    expect(canRead({ row: { ...row, canEnter: false }, problem: null })).toBe(false)
    expect(canRead({ row, problem: 'AUTOMATIC' })).toBe(false)
  })
})
