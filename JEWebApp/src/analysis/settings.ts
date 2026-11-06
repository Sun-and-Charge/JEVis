/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { AnalysisDataRequest, AnalysisDefaults } from '../api/analysis'

export const TIME_FRAMES = [
  'TODAY',
  'YESTERDAY',
  'LAST_7_DAYS',
  'THIS_WEEK',
  'LAST_WEEK',
  'LAST_30_DAYS',
  'THIS_MONTH',
  'LAST_MONTH',
  'THIS_YEAR',
  'LAST_YEAR',
  'THE_YEAR_BEFORE_LAST',
  'CUSTOM',
] as const
export type TimeFrame = (typeof TIME_FRAMES)[number]

export const AGGREGATIONS = ['NONE', 'QUARTER_HOURLY', 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'] as const

export const MANIPULATIONS = [
  'NONE',
  'AVERAGE',
  'SUM',
  'MIN',
  'MAX',
  'MEDIAN',
  'RUNNING_MEAN',
  'CENTRIC_RUNNING_MEAN',
  'SORTED_MIN',
  'SORTED_MAX',
  'CUMULATE',
] as const

/** Toolbar settings of the analysis view. from/to only for CUSTOM. */
export interface AnalysisSettings {
  timeFrame: TimeFrame
  from: Date | null
  to: Date | null
  aggregation: string
  manipulation: string
}

function isTimeFrame(value: unknown): value is TimeFrame {
  return typeof value === 'string' && (TIME_FRAMES as readonly string[]).includes(value)
}

/** Settings from the defaults of the analysis; unknown time frames (e.g. CUSTOM_START_END) become TODAY. */
export function defaultSettings(defaults: AnalysisDefaults | null | undefined): AnalysisSettings {
  const timeFrame = defaults && isTimeFrame(defaults.timeFrame) && defaults.timeFrame !== 'CUSTOM' ? defaults.timeFrame : 'TODAY'
  return {
    timeFrame,
    from: null,
    to: null,
    aggregation: defaults?.aggregation || 'NONE',
    manipulation: defaults?.manipulation || 'NONE',
  }
}

type QueryValue = string | null | undefined | (string | null)[]
type Query = Record<string, QueryValue>

function single(value: QueryValue): string | undefined {
  if (Array.isArray(value)) return value[0] ?? undefined
  return value ?? undefined
}

function parseDate(value: string | undefined): Date | null {
  if (!value) return null
  // a "+" of the offset typed into a link arrives as space
  const ms = Date.parse(value.trim().replace(/ (\d{2}:?\d{2})$/, '+$1'))
  return Number.isNaN(ms) ? null : new Date(ms)
}

/** Reads the settings from the URL query (tf, from, to, agg, man); missing or invalid values come from the defaults. */
export function settingsFromQuery(query: Query, defaults: AnalysisDefaults | null | undefined): AnalysisSettings {
  const settings = defaultSettings(defaults)
  const tf = single(query.tf)
  if (tf === 'CUSTOM') {
    const from = parseDate(single(query.from))
    const to = parseDate(single(query.to))
    if (from && to && from.getTime() <= to.getTime()) {
      settings.timeFrame = 'CUSTOM'
      settings.from = from
      settings.to = to
    }
  } else if (isTimeFrame(tf)) {
    settings.timeFrame = tf
  }
  const agg = single(query.agg)
  if (agg && ((AGGREGATIONS as readonly string[]).includes(agg) || agg === defaults?.aggregation)) settings.aggregation = agg
  const man = single(query.man)
  if (man && ((MANIPULATIONS as readonly string[]).includes(man) || man === defaults?.manipulation)) settings.manipulation = man
  return settings
}

/** URL query for the settings (from/to only with CUSTOM). */
export function settingsToQuery(settings: AnalysisSettings): Record<string, string> {
  const query: Record<string, string> = { tf: settings.timeFrame, agg: settings.aggregation, man: settings.manipulation }
  if (settings.timeFrame === 'CUSTOM' && settings.from && settings.to) {
    query.from = toIsoLocal(settings.from)
    query.to = toIsoLocal(settings.to)
  }
  return query
}

const pad = (n: number, width = 2) => String(Math.abs(n)).padStart(width, '0')

/** ISO 8601 in the local time zone of the browser with offset, e.g. 2026-09-01T00:00:00.000+02:00. */
export function toIsoLocal(date: Date): string {
  const offset = -date.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}` +
    `${sign}${pad(Math.floor(Math.abs(offset) / 60))}:${pad(Math.abs(offset) % 60)}`
  )
}

/** Value for an <input type="datetime-local"> (local time, minutes). */
export function toDateTimeInput(date: Date | null): string {
  if (!date) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** Parses the value of an <input type="datetime-local"> as local time. */
export function fromDateTimeInput(value: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/.exec(value)
  if (!m) return null
  return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], m[6] ? +m[6] : 0)
}

export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}

export function dataRequest(settings: AnalysisSettings, timeZone: string): AnalysisDataRequest {
  const custom = settings.timeFrame === 'CUSTOM' && settings.from && settings.to
  return {
    timeFrame: settings.timeFrame,
    from: custom ? toIsoLocal(settings.from!) : null,
    to: custom ? toIsoLocal(settings.to!) : null,
    aggregation: settings.aggregation,
    manipulation: settings.manipulation,
    timeZone,
  }
}

/** Length of a period for previous/next. Calendar units, so DST and month lengths are respected. */
export type ShiftUnit = { days: number } | { months: number } | { ms: number }

const PRESET_UNITS: Partial<Record<TimeFrame, ShiftUnit>> = {
  TODAY: { days: 1 },
  YESTERDAY: { days: 1 },
  LAST_7_DAYS: { days: 7 },
  THIS_WEEK: { days: 7 },
  LAST_WEEK: { days: 7 },
  LAST_30_DAYS: { days: 30 },
  THIS_MONTH: { months: 1 },
  LAST_MONTH: { months: 1 },
  THIS_YEAR: { months: 12 },
  LAST_YEAR: { months: 12 },
  THE_YEAR_BEFORE_LAST: { months: 12 },
}

function addUnit(date: Date, unit: ShiftUnit, factor: number): Date {
  const d = new Date(date.getTime())
  if ('ms' in unit) return new Date(d.getTime() + factor * unit.ms)
  if ('days' in unit) {
    d.setDate(d.getDate() + factor * unit.days)
    return d
  }
  const day = d.getDate()
  d.setDate(1)
  d.setMonth(d.getMonth() + factor * unit.months)
  // keep the day, but not beyond the end of the month (31 Jan + 1 month = 28/29 Feb)
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()
  d.setDate(Math.min(day, last))
  return d
}

const sameTimeOfDay = (a: Date, b: Date) =>
  a.getHours() === b.getHours() && a.getMinutes() === b.getMinutes() && a.getSeconds() === b.getSeconds() && a.getMilliseconds() === b.getMilliseconds()

/**
 * Period length of a custom range [from, to] (to inclusive, usually ...59.999): whole months when the range covers
 * whole months (also with a workday start), whole days when it covers whole days, otherwise its exact duration.
 */
export function inferUnit(from: Date, to: Date): ShiftUnit {
  const end = new Date(to.getTime() + 1)
  const exclusiveEnd = sameTimeOfDay(from, end)
  const endRef = exclusiveEnd ? end : to
  if (exclusiveEnd || sameTimeOfDay(from, to)) {
    const months = (endRef.getFullYear() - from.getFullYear()) * 12 + endRef.getMonth() - from.getMonth()
    if (months > 0 && endRef.getDate() === from.getDate()) return { months }
    let days = Math.round((Date.UTC(endRef.getFullYear(), endRef.getMonth(), endRef.getDate()) - Date.UTC(from.getFullYear(), from.getMonth(), from.getDate())) / 86400000)
    if (days > 0) return { days }
  }
  return { ms: Math.max(1, to.getTime() - from.getTime() + 1) }
}

/** The unit for previous/next of a time frame (the resolved range is needed for CUSTOM). */
export function shiftUnit(timeFrame: TimeFrame, from: Date, to: Date): ShiftUnit {
  return PRESET_UNITS[timeFrame] ?? inferUnit(from, to)
}

/**
 * The range before (direction -1) or after (+1) the resolved range [from, to]. The new range adjoins the old one, so
 * "previous" of a running period (THIS_MONTH up to now) is the whole previous month.
 */
export function shiftRange(timeFrame: TimeFrame, from: Date, to: Date, direction: -1 | 1): { from: Date; to: Date } {
  const unit = shiftUnit(timeFrame, from, to)
  if (direction < 0) {
    return { from: addUnit(from, unit, -1), to: new Date(from.getTime() - 1) }
  }
  const start = new Date(to.getTime() + 1)
  // a range that ended "now" (not at the end of a period): continue from the end of the full period
  const fullEnd = addUnit(from, unit, 1)
  const nextFrom = fullEnd.getTime() > start.getTime() ? fullEnd : start
  return { from: nextFrom, to: new Date(addUnit(nextFrom, unit, 1).getTime() - 1) }
}

/** Whether "next" leads to a range that starts in the future. */
export function nextInFuture(timeFrame: TimeFrame, from: Date, to: Date, now: Date = new Date()): boolean {
  return shiftRange(timeFrame, from, to, 1).from.getTime() > now.getTime()
}
