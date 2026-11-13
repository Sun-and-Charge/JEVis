/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { DueState, ExpectedRange, ReadingSample } from '../api/readings'
import { wallTime } from '../edit/entry'

/*
 * Rules of the meter reading (E8.1), ported from the Sun and Charge app (shared/.../domain: ExpectedValues.estimate,
 * Due.state, EntryValidator.parseNumber) so that the web shows the same expected value as the server and the app.
 * The server computes `expected` at the suggested timestamp; the page recomputes it here when the user picks
 * another time.
 */

/** A reading at an instant (epoch ms). */
export interface Point {
  ts: number
  value: number
}

/** Number of recent readings that are used (ExpectedValues.WINDOW). */
export const WINDOW = 10

/** Extra tolerance on top of the observed spread. */
const MARGIN = 0.2

/**
 * Parses a number as typed by the user (EntryValidator.parseNumber): "," and "." are both decimal separators; if
 * both occur, the last one is the decimal separator and the other one groups thousands. Spaces and apostrophes are
 * ignored. null if empty or no finite number.
 */
export function parseNumber(text: string | null | undefined): number | null {
  const s = (text ?? '').trim().replace(/[\s  ']/g, '').replace(/−/g, '-')
  if (!s) return null
  const lastComma = s.lastIndexOf(',')
  const lastDot = s.lastIndexOf('.')
  let normalized: string
  if (lastComma >= 0 && lastDot >= 0) {
    normalized = lastComma > lastDot ? s.replace(/\./g, '').replace(/,/g, '.') : s.replace(/,/g, '')
  } else if (lastComma >= 0) {
    normalized = s.replace(/,/g, '.')
  } else {
    normalized = s
  }
  if (!/^[+-]?(\d+\.?\d*|\.\d+)(e[+-]?\d+)?$/i.test(normalized)) return null
  const n = Number(normalized)
  return Number.isFinite(n) ? n : null
}

/* ------------------------------------------------------------------ periods */

export interface Period {
  years: number
  months: number
  weeks: number
  days: number
  hours: number
  minutes: number
  seconds: number
}

const PERIOD = /^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$/

/** An ISO 8601 period (P1M, PT15M, P1D, …); null if missing or invalid. */
export function parsePeriod(text: string | null | undefined): Period | null {
  const s = (text ?? '').trim().toUpperCase()
  if (s.length <= 1 || s.endsWith('T')) return null
  const m = PERIOD.exec(s)
  if (!m) return null
  const n = (i: number) => (m[i] ? Number(m[i]) : 0)
  return { years: n(1), months: n(2), weeks: n(3), days: n(4), hours: n(5), minutes: n(6), seconds: n(7) }
}

/** No period: missing, invalid or zero ("Kein Turnus"). */
export function isZeroPeriod(period: Period | null): boolean {
  return !period || Object.values(period).every((v) => v === 0)
}

const MINUTE = 60_000

/** Offset of a time zone at an instant in ms (browser zone if none or unknown). */
function offsetAt(ms: number, timeZone?: string | null): number {
  const base = Math.floor(ms / MINUTE) * MINUTE
  const w = wallTime(base, timeZone)
  return Date.UTC(w.year, w.month - 1, w.day, w.hour, w.minute) - base
}

/**
 * Instant of a wall time in a time zone, as java.time resolves it: in an overlap (DST end) the earlier instant, in
 * a gap (DST start) the wall time moved forward by the length of the gap.
 */
export function zonedToMs(year: number, month: number, day: number, hour: number, minute: number, second: number, timeZone?: string | null): number {
  const local = Date.UTC(year, month - 1, day, hour, minute, second)
  const before = offsetAt(local - 86_400_000, timeZone)
  const after = offsetAt(local + 86_400_000, timeZone)
  const valid = [...new Set([before, after, offsetAt(local, timeZone)])]
    .map((offset) => local - offset)
    .filter((ms) => local - offsetAt(ms, timeZone) === ms)
  return valid.length ? Math.min(...valid) : local - before
}

/** Instant of an input timestamp `yyyy-MM-ddTHH:mm[:ss]` (E1 wall time) in a time zone; null if invalid. */
export function wallTimeToMs(text: string | null | undefined, timeZone?: string | null): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2}))?)?$/.exec((text ?? '').trim())
  if (!m) return null
  const ms = zonedToMs(Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4] ?? 0), Number(m[5] ?? 0), Number(m[6] ?? 0), timeZone)
  return Number.isNaN(ms) ? null : ms
}

/**
 * Adds a period like java.time's ZonedDateTime (JEVisPeriod.addTo): years, months, weeks and days on the wall time
 * of the zone (end of month clamped), hours, minutes and seconds as durations.
 */
export function addPeriod(ms: number, period: Period, timeZone?: string | null, count = 1): number {
  const w = wallTime(ms, timeZone)
  const sec = Math.floor((((ms % MINUTE) + MINUTE) % MINUTE) / 1000)
  const sub = ((ms % 1000) + 1000) % 1000
  let year = w.year + period.years * count
  let month = w.month + period.months * count
  year += Math.floor((month - 1) / 12)
  month = ((((month - 1) % 12) + 12) % 12) + 1
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const day = Math.min(w.day, lastDay)
  const dayMs = Date.UTC(year, month - 1, day) + (period.weeks * 7 + period.days) * count * 86_400_000
  const d = new Date(dayMs)
  let result = zonedToMs(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate(), w.hour, w.minute, sec, timeZone) + sub
  result += ((period.hours * 60 + period.minutes) * 60 + period.seconds) * 1000 * count
  return result
}

/* ------------------------------------------------------------------ expected value */

/** The numeric readings of the list (newest first in the list; any order is fine for {@link estimate}). */
export function readingPoints(samples: readonly Pick<ReadingSample, 'timestamp' | 'value'>[]): Point[] {
  const out: Point[] = []
  for (const s of samples) {
    const ts = Date.parse(s.timestamp)
    const value = typeof s.value === 'number' ? s.value : typeof s.value === 'string' ? parseNumber(s.value) : null
    if (!Number.isNaN(ts) && value != null && Number.isFinite(value)) out.push({ ts, value })
  }
  return out
}

/**
 * Expected value at `ts` (ExpectedValues.estimate):
 * - counters: last reading + average rate of the intervals of the last 11 readings × elapsed seconds; range from the
 *   lowest to the highest rate (spread at least 20 % of the average rate), low not below the last reading;
 *   intervals with a decreasing value or no time are left out;
 * - other rows with a period: average of the last 10 values, range min − margin … max + margin with
 *   margin = max(20 % of (max − min), 20 % of |average|);
 * - rows without period: null.
 * null with fewer than 2 readings, for counters also at or before the last reading or without usable interval.
 */
export function estimate(row: { counter: boolean; period: string | null }, readings: readonly Point[], ts: number): ExpectedRange | null {
  if (row.counter) return meterReading(readings, ts)
  if (isZeroPeriod(parsePeriod(row.period))) return null
  return average(readings)
}

const sorted = (readings: readonly Point[]) => [...readings].sort((a, b) => a.ts - b.ts)
const wholeSeconds = (ms: number) => Math.trunc(ms / 1000)

function meterReading(readings: readonly Point[], ts: number): ExpectedRange | null {
  const recent = sorted(readings).slice(-(WINDOW + 1))
  if (recent.length < 2) return null
  const last = recent[recent.length - 1]
  const seconds = wholeSeconds(ts - last.ts)
  if (seconds <= 0) return null
  const rates: number[] = []
  for (let i = 1; i < recent.length; i++) {
    const a = recent[i - 1]
    const b = recent[i]
    const dt = wholeSeconds(b.ts - a.ts)
    /* decreasing readings (meter change, overflow) say nothing about the consumption */
    if (dt <= 0 || b.value < a.value) continue
    rates.push((b.value - a.value) / dt)
  }
  if (!rates.length) return null
  const avg = rates.reduce((s, r) => s + r, 0) / rates.length
  const spread = Math.max(Math.max(...rates) - Math.min(...rates), avg * MARGIN)
  const low = Math.max(avg - spread, 0) * seconds
  const high = (avg + spread) * seconds
  return { value: last.value + avg * seconds, low: last.value + low, high: last.value + high }
}

function average(readings: readonly Point[]): ExpectedRange | null {
  const values = sorted(readings)
    .slice(-WINDOW)
    .map((r) => r.value)
  if (values.length < 2) return null
  const avg = values.reduce((s, v) => s + v, 0) / values.length
  const min = Math.min(...values)
  const max = Math.max(...values)
  const margin = Math.max((max - min) * MARGIN, Math.abs(avg) * MARGIN)
  return { value: avg, low: min - margin, high: max + margin }
}

/** True if the value lies outside the expected range. */
export function outsideExpected(value: number, expected: ExpectedRange | null): boolean {
  return !!expected && (value < expected.low || value > expected.high)
}

/* ------------------------------------------------------------------ due state */

/**
 * Due state (Due.state): `NONE` without period; `DUE` without any reading; otherwise with `next` = the next period
 * after the last reading (the server's `suggestedTimestamp`; one period after the last reading if not given):
 * `OK` if `next` is after now, `DUE` if `next + period` is after now, else `OVERDUE`.
 */
export function dueState(
  periodText: string | null | undefined,
  lastTs: number | null,
  next: number | null,
  now: number,
  timeZone?: string | null,
): DueState {
  const period = parsePeriod(periodText)
  if (!period || isZeroPeriod(period)) return 'NONE'
  if (lastTs == null) return 'DUE'
  const n = next ?? addPeriod(lastTs, period, timeZone)
  if (n > now) return 'OK'
  if (addPeriod(n, period, timeZone) > now) return 'DUE'
  return 'OVERDUE'
}
