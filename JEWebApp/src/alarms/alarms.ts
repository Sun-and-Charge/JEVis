/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { formatNumber, localTimestamp } from '../analysis/format'
import { CSV_BOM, csvField, csvNumber, csvSeparators } from '../analysis/table'
import type { Alarm } from '../api/alarms'
import type { ColumnDef } from '../iso/columns'
import { matchesSite, matchesTerms, searchTerms, siteOptions, type SiteFilter, type SiteOption } from '../modules/search'
import { checkRange, isoDay, rowFullName, rowName, type RangeCheck } from '../notes/notes'

/*
 * Alarms (W4.5, view only) like the Control Center's Alarm Plugin: search, type and site filter, sort columns, CSV
 * and the time frame (the days in which JEAlarm logged the alarms).
 */

/** Order of the type chips (AlarmType of JEAlarm); unknown types follow. */
export const ALARM_TYPE_ORDER = ['L1', 'L2', 'D1', 'D2', 'DYNAMIC', 'STATIC', 'GAP', 'NONE'] as const

export interface AlarmFilter {
  /** search in configuration, data point (name and path) and type name */
  text: string
  /** selected type codes; none selected: all alarms */
  types: string[]
  site: SiteFilter
}

export function defaultAlarmFilter(): AlarmFilter {
  return { text: '', types: [], site: 'all' }
}

export interface AlarmTypeOption {
  id: string
  name: string
  count: number
}

/** Type chips: the types of the alarms with their number; selected types stay visible without alarms. */
export function alarmTypeOptions(alarms: readonly Alarm[], selected: readonly string[] = []): AlarmTypeOption[] {
  const found = new Map<string, AlarmTypeOption>()
  for (const a of alarms) {
    const id = a.type || 'NONE'
    const option = found.get(id)
    if (option) option.count++
    else found.set(id, { id, name: a.typeName || id, count: 1 })
  }
  for (const id of selected) if (!found.has(id)) found.set(id, { id, name: id, count: 0 })
  const rank = (id: string) => {
    const i = (ALARM_TYPE_ORDER as readonly string[]).indexOf(id)
    return i < 0 ? ALARM_TYPE_ORDER.length : i
  }
  return [...found.values()].sort((a, b) => rank(a.id) - rank(b.id) || a.name.localeCompare(b.name))
}

const siteOf = (a: Pick<Alarm, 'site'>) => (a.site ? { id: a.site.id, name: a.site.name, names: a.site.names ?? {} } : null)

/** Alarms matching the filter: one of the selected types, the site and every search word in one of the fields. */
export function filterAlarms(alarms: readonly Alarm[], filter: AlarmFilter, locale: string): Alarm[] {
  const terms = searchTerms(filter.text, locale)
  const types = new Set(filter.types)
  return alarms.filter((a) => {
    if (types.size && !types.has(a.type || 'NONE')) return false
    if (!matchesSite(siteOf(a), filter.site)) return false
    const row = a.row?.readable ? [a.row.path, a.row.name] : []
    return matchesTerms(terms, [a.configuration?.name, ...row, a.typeName], locale)
  })
}

/** Sites of the alarms for the site filter. */
export function alarmSites(alarms: readonly Alarm[], locale: string): SiteOption[] {
  return siteOptions(alarms.map((a) => ({ site: siteOf(a) })), locale)
}

/** Epoch milliseconds of a timestamp, null if it cannot be read. */
export function parseTime(text: string | null | undefined): number | null {
  if (!text) return null
  const ms = Date.parse(text)
  return Number.isNaN(ms) ? null : ms
}

export const alarmTime = (a: Pick<Alarm, 'timestamp'>) => parseTime(a.timestamp)

const OPERATORS: Record<string, string> = { '>': '>', '>=': '≥', '': '≥', '=': '=', '==': '=', '!=': '≠', '<>': '≠', '<': '<', '<=': '≤' }

/** Operator as in the Control Center's column (the server normalises already; older answers are mapped here). */
export function alarmOperator(op: string | null | undefined): string {
  if (op == null) return ''
  const trimmed = op.trim()
  return OPERATORS[trimmed] ?? trimmed
}

/** A logged value in the UI locale: up to 3 decimals, up to 6 for small values (0.0042). */
export function formatAlarmNumber(value: number | null | undefined, locale: string): string {
  if (value == null || !Number.isFinite(value)) return ''
  const abs = Math.abs(value)
  return formatNumber(value, locale, { min: 0, max: abs > 0 && abs < 1 ? 6 : 3 })
}

/** Value with unit for cards and the detail panel ("812,5 kWh"); empty without value. */
export function valueWithUnit(value: number | null | undefined, unit: string | null | undefined, locale: string): string {
  const text = formatAlarmNumber(value, locale)
  return text && unit ? `${text} ${unit}` : text
}

export interface AlarmColumnEnv {
  noAccess: string
  formatTime: (ms: number) => string
  locale: string
  /** "Offen" / "Quittiert" */
  stateName: (checked: boolean) => string
}

/** Columns of the alarms table (labels are i18n keys); the values sort numerically, the date by time. */
export function alarmColumns(env: AlarmColumnEnv): ColumnDef<Alarm>[] {
  const num = (id: string, get: (a: Alarm) => number | null): ColumnDef<Alarm> => ({
    id,
    label: 'alarms.col.' + id,
    numeric: true,
    nowrap: true,
    text: (a) => formatAlarmNumber(get(a), env.locale),
    sort: (a) => get(a),
  })
  return [
    {
      id: 'date',
      label: 'alarms.col.date',
      nowrap: true,
      text: (a) => {
        const ms = alarmTime(a)
        return ms == null ? a.timestamp : env.formatTime(ms)
      },
      sort: (a) => alarmTime(a),
    },
    { id: 'site', label: 'alarms.col.site', text: (a) => a.site?.name ?? '' },
    { id: 'configuration', label: 'alarms.col.configuration', text: (a) => a.configuration?.name ?? '' },
    { id: 'row', label: 'alarms.col.row', text: (a) => rowFullName(a, env.noAccess) },
    num('isValue', (a) => a.isValue),
    { id: 'operator', label: 'alarms.col.operator', nowrap: true, text: (a) => alarmOperator(a.operator) },
    num('shouldBeValue', (a) => a.shouldBeValue),
    num('tolerance', (a) => a.tolerance),
    { id: 'unit', label: 'alarms.col.unit', nowrap: true, text: (a) => a.unit ?? '' },
    { id: 'type', label: 'alarms.col.type', text: (a) => a.typeName || a.type || '' },
    { id: 'alarm', label: 'alarms.col.alarm', text: (a) => a.logValueName ?? '' },
    { id: 'status', label: 'alarms.col.status', text: (a) => env.stateName(!!a.configuration?.checked) },
  ]
}

export type AlarmSort = { id: string; dir: 'asc' | 'desc' }

/** The table starts newest first (like the answer). */
export const DEFAULT_ALARM_SORT: AlarmSort = { id: 'date', dir: 'desc' }

export interface AlarmCsvHeaders {
  date: string
  site: string
  configuration: string
  path: string
  row: string
  isValue: string
  operator: string
  shouldBeValue: string
  tolerance: string
  unit: string
  type: string
  alarm: string
  status: string
  checkedBy: string
  checkedAt: string
}

/**
 * CSV of the shown alarms: local times (2026-11-10 14:15:00), numbers without grouping and with the decimal
 * separator of the locale, UTF-8 with BOM and CRLF, separators of the locale (Excel), formulas escaped.
 */
export function alarmsCsv(alarms: readonly Alarm[], headers: AlarmCsvHeaders, env: Pick<AlarmColumnEnv, 'noAccess' | 'stateName'>, locale: string): string {
  const { field, decimal } = csvSeparators(locale)
  const head = [
    headers.date,
    headers.site,
    headers.configuration,
    headers.path,
    headers.row,
    headers.isValue,
    headers.operator,
    headers.shouldBeValue,
    headers.tolerance,
    headers.unit,
    headers.type,
    headers.alarm,
    headers.status,
    headers.checkedBy,
    headers.checkedAt,
  ]
  const text = (v: string | null | undefined) => (v ? csvField(v, field) : '')
  const number = (v: number | null | undefined) => (v == null || !Number.isFinite(v) ? '' : csvNumber(v, decimal))
  const time = (v: string | null | undefined) => {
    const ms = parseTime(v)
    return ms == null ? text(v) : localTimestamp(ms)
  }
  const lines = [head.map((h) => csvField(h, field)).join(field)]
  for (const a of alarms) {
    const checked = !!a.configuration?.checked
    lines.push(
      [
        time(a.timestamp),
        text(a.site?.name),
        text(a.configuration?.name),
        text(a.row?.readable ? a.row.path : ''),
        text(rowName(a, env.noAccess)),
        number(a.isValue),
        text(alarmOperator(a.operator)),
        number(a.shouldBeValue),
        number(a.tolerance),
        text(a.unit),
        text(a.typeName || a.type),
        text(a.logValueName),
        text(env.stateName(checked)),
        text(checked ? a.configuration.checkedBy : ''),
        checked ? time(a.configuration.checkedAt) : '',
      ].join(field),
    )
  }
  return CSV_BOM + lines.join('\r\n') + '\r\n'
}

/** Default time frame: the last 7 days up to today (like the server's default). */
export function defaultAlarmRange(now: Date = new Date()): { from: string; to: string } {
  return { from: isoDay(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 7)), to: isoDay(now) }
}

/** Time frame of the alarms: at most 1 year. */
export function checkAlarmRange(from: string, to: string): RangeCheck {
  return checkRange(from, to, 1)
}

/** Title of the status badge: who confirmed and when ("admin · 10.11.2026, 23:50"). */
export function checkedTitle(a: Pick<Alarm, 'configuration'>, formatTime: (ms: number) => string): string {
  const c = a.configuration
  if (!c?.checked) return ''
  const ms = parseTime(c.checkedAt)
  return [c.checkedBy, ms == null ? c.checkedAt : formatTime(ms)].filter(Boolean).join(' · ')
}
