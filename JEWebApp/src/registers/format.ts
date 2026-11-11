/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { formatNumber, type Decimals } from '../analysis/format'
import type { FileValue, LastValue, RegisterColumn, RegisterHistory, RegisterRow, RegisterValue, TargetValue } from '../api/registers'
import { formatLocal, formatLocalDate, parseLocal } from '../modules/dates'

/*
 * Display of register values. Timestamps keep the wall-clock time as stored (like modules/dates), so the browser's
 * zone never moves a monthly value into the previous month.
 */

type Translate = (key: string) => string

const isNumber = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v)

export function isFileValue(v: unknown): v is FileValue {
  return !!v && typeof v === 'object' && typeof (v as FileValue).file === 'string'
}

export function isTargetValue(v: unknown): v is TargetValue {
  return !!v && typeof v === 'object' && typeof (v as TargetValue).id === 'number' && 'readable' in (v as object)
}

const ATTRIBUTE_DECIMALS: Decimals = { min: 0, max: 4 }
const INTEGER_DECIMALS: Decimals = { min: 0, max: 0 }
const LAST_DECIMALS: Decimals = { min: 0, max: 2 }

/** "12,5 kWh"; '' if not a number. */
export function numberWithUnit(value: number | null | undefined, unit: string | null | undefined, locale: string, decimals = ATTRIBUTE_DECIMALS): string {
  if (!isNumber(value)) return ''
  const text = formatNumber(value, locale, decimals)
  return unit ? `${text} ${unit}` : text
}

/** Name of a target: its name if readable, else a hint (the id is never shown as a name). */
export function targetText(target: TargetValue | null | undefined, t: Translate): string {
  if (!target) return ''
  if (target.readable && target.name) return target.name
  return t('registers.targetHidden')
}

/** An attribute value formatted by its column type (NUMBER with locale and unit, BOOLEAN as yes/no, DATE local). */
export function formatAttribute(value: RegisterValue | undefined, column: Pick<RegisterColumn, 'type' | 'unit'>, locale: string, t: Translate): string {
  if (value == null || value === '') return ''
  if (isFileValue(value)) return value.file
  if (isTargetValue(value)) return targetText(value, t)
  switch (column.type) {
    case 'NUMBER':
      return isNumber(value) ? numberWithUnit(value, column.unit, locale) : String(value)
    case 'INTEGER':
      return isNumber(value) ? numberWithUnit(value, column.unit, locale, INTEGER_DECIMALS) : String(value)
    case 'BOOLEAN':
      if (typeof value === 'boolean') return t(value ? 'registers.yes' : 'registers.no')
      if (value === 'true' || value === '1' || value === 1) return t('registers.yes')
      if (value === 'false' || value === '0' || value === 0) return t('registers.no')
      return String(value)
    case 'DATE':
      return typeof value === 'string' ? formatLocalDate(value, locale) : String(value)
    case 'DATETIME':
      return typeof value === 'string' ? formatLocal(value, locale) : String(value)
    default:
      if (typeof value === 'boolean') return t(value ? 'registers.yes' : 'registers.no')
      if (isNumber(value)) return numberWithUnit(value, column.unit, locale)
      return String(value)
  }
}

/** Value to sort and export an attribute by: numbers and dates as they are, others as their text. */
export function attributeSortValue(value: RegisterValue | undefined): string | number | boolean | null {
  if (value == null) return null
  if (isFileValue(value)) return value.file
  if (isTargetValue(value)) return value.name ?? null
  return value
}

const formats = new Map<string, Intl.DateTimeFormat>()

function utcFormat(locale: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const key = locale + JSON.stringify(options)
  let f = formats.get(key)
  if (!f) {
    f = new Intl.DateTimeFormat(locale, { ...options, timeZone: 'UTC' })
    formats.set(key, f)
  }
  return f
}

/**
 * A timestamp formatted by the period of the data like the Control Center: P1M → month/year, P1D (or longer days,
 * weeks) → date, P1Y → year, otherwise date and time. '' if missing.
 */
export function formatByPeriod(timestamp: string | null | undefined, period: string | null | undefined, locale: string): string {
  if (!timestamp) return ''
  const ms = parseLocal(timestamp)
  if (ms == null) return timestamp
  const p = (period ?? '').toUpperCase()
  if (/^P\d+Y$/.test(p)) return utcFormat(locale, { year: 'numeric' }).format(ms)
  if (/^P\d+M$/.test(p)) return utcFormat(locale, { month: 'long', year: 'numeric' }).format(ms)
  if (/^P\d+[DW]$/.test(p)) return formatLocalDate(timestamp, locale)
  return formatLocal(timestamp, locale)
}

/** Period of a base data row (in `last` or on the row). */
export function rowPeriod(row: Pick<RegisterRow, 'last' | 'period'>): string | null {
  return row.last?.period ?? row.period ?? null
}

function lastDecimals(last: LastValue): Decimals {
  const d = last.decimals
  if (isNumber(d) && d >= 0) {
    const n = Math.min(20, Math.round(d))
    return { min: n, max: n }
  }
  return LAST_DECIMALS
}

/** The last value with unit ("12.345,6 kWh"); '' if none. */
export function formatLast(last: LastValue | null | undefined, locale: string): string {
  if (!last || last.value == null || last.value === '') return ''
  if (typeof last.value === 'string') return last.unit ? `${last.value} ${last.unit}` : last.value
  return numberWithUnit(last.value, last.unit, locale, lastDecimals(last))
}

/** A history sample value with the decimals of the row and the unit of the history. */
export function formatSample(value: number | string | null | undefined, unit: string | null | undefined, locale: string, decimals?: number | null): string {
  if (value == null || value === '') return ''
  return formatLast({ value, unit: unit ?? null, timestamp: null, decimals }, locale)
}

/** Note of a reading that marks a meter exchange ("Meter Tausch" as written by the Control Center). */
export function isMeterExchange(note: string | null | undefined): boolean {
  return !!note && /meter\s*-?\s*tausch/i.test(note)
}

export type VerificationState = 'overdue' | 'dueSoon' | 'ok'

/** Days before the due date in which a verification is shown as due soon. */
export const DUE_SOON_DAYS = 90

/** State of the next verification: overdue (server flag or due before today), due within 90 days, ok; null without date. */
export function verificationState(row: Pick<RegisterRow, 'verification'>, today: string): VerificationState | null {
  const v = row.verification
  const due = parseLocal(v?.due)
  if (!v || due == null) return null
  const now = parseLocal(today)
  if (v.overdue || (now != null && due < now)) return 'overdue'
  if (now != null && due - now <= DUE_SOON_DAYS * 86_400_000) return 'dueSoon'
  return 'ok'
}

/** "31.12.2026 – überfällig"; '' without date. */
export function verificationText(row: Pick<RegisterRow, 'verification'>, today: string, locale: string, t: Translate): string {
  const state = verificationState(row, today)
  if (!state) return ''
  const date = formatLocalDate(row.verification!.due, locale)
  return `${date} – ${t('registers.verification.' + state)}`
}

/** True if the verification is due before the end of the year (the Control Center's "overdue until end of year X"). */
export function dueUntilEndOf(row: Pick<RegisterRow, 'verification'>, year: number): boolean {
  const due = parseLocal(row.verification?.due)
  return due != null && due < Date.UTC(year + 1, 0, 1)
}

/** Translated medium name, falling back to the class label and the technical name. */
export function mediumLabel(row: Pick<RegisterRow, 'medium' | 'classLabel' | 'className'>, t: Translate, te: (key: string) => boolean): string {
  if (row.medium) {
    const key = 'registers.medium.' + row.medium
    if (te(key)) return t(key)
  }
  return row.classLabel || row.medium || row.className
}


export interface HistoryLine {
  timestamp: string
  time: string
  value: string
  note: string
  /** meters: the reading of a meter exchange */
  exchange: boolean
}

/**
 * Lines of the short history (newest first as sent): base data with the timestamp formatted by the period of the
 * data, meter readings with date and time and the meter exchange marked.
 */
export function historyLines(
  history: RegisterHistory | null | undefined,
  options: { meter: boolean; period?: string | null; decimals?: number | null; unit?: string | null },
  locale: string,
): HistoryLine[] {
  const unit = history?.unit ?? options.unit ?? null
  return (history?.samples ?? []).map((s) => ({
    timestamp: s.timestamp,
    time: formatByPeriod(s.timestamp, options.meter ? null : options.period, locale),
    value: formatSample(s.value, unit, locale, options.decimals),
    note: s.note ?? '',
    exchange: options.meter && isMeterExchange(s.note),
  }))
}
