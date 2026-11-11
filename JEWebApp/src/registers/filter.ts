/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { RegisterRow, RegisterValue } from '../api/registers'
import { matchesTerms, searchTerms } from '../modules/search'
import type { RegisterColumnDef, RegisterEnv } from './columns'
import { dueUntilEndOf, isFileValue, isTargetValue, mediumLabel } from './format'

/*
 * Search and the meter filters of the Control Center's Meter Plugin: medium (translated class), the attributes
 * "Type" and "Location", and "overdue until the end of year X" (verification date before 1 January of X + 1).
 */

export const ALL = '*'

/** Attribute keys of the meter filters (JC.MeasurementInstrument). */
export const TYPE_ATTRIBUTE = 'Type'
export const LOCATION_ATTRIBUTE = 'Location'

export interface RegisterFilter {
  text: string
  medium: string
  type: string
  location: string
  overdueOnly: boolean
  year: number
}

export function defaultFilter(today: string | null | undefined): RegisterFilter {
  const year = Number((today ?? '').slice(0, 4))
  return { text: '', medium: ALL, type: ALL, location: ALL, overdueOnly: false, year: Number.isInteger(year) && year > 0 ? year : new Date().getFullYear() }
}

/** Text of a plain attribute value for the filters ('' for files, targets and missing values). */
export function plainText(value: RegisterValue | undefined): string {
  if (value == null || isFileValue(value) || isTargetValue(value)) return ''
  return String(value).trim()
}

/** Texts a row is found by: name, path, class, target, and the cell text of every column (also hidden ones). */
export function searchTexts(row: RegisterRow, columns: readonly RegisterColumnDef[], env: Pick<RegisterEnv, 'locale'>): string[] {
  const ctx = { locale: env.locale, t: (k: string) => k }
  return [row.name, row.path, row.classLabel, row.target?.readable ? row.target.name : null, ...columns.map((c) => c.text(row, ctx))].filter(
    (s): s is string => !!s,
  )
}

/** Rows matching the search (all words, case-insensitive) and, for meters, the meter filters. */
export function filterRows(rows: readonly RegisterRow[], filter: RegisterFilter, columns: readonly RegisterColumnDef[], env: RegisterEnv, meters: boolean): RegisterRow[] {
  const terms = searchTerms(filter.text, env.locale)
  return rows.filter((row) => {
    if (meters) {
      if (filter.medium !== ALL && mediumLabel(row, env.t, env.te) !== filter.medium) return false
      if (filter.type !== ALL && plainText(row.values?.[TYPE_ATTRIBUTE]) !== filter.type) return false
      if (filter.location !== ALL && plainText(row.values?.[LOCATION_ATTRIBUTE]) !== filter.location) return false
      if (filter.overdueOnly && !dueUntilEndOf(row, filter.year)) return false
    }
    return matchesTerms(terms, searchTexts(row, columns, env), env.locale)
  })
}

export interface MeterOptions {
  media: string[]
  types: string[]
  locations: string[]
}

function distinct(values: Iterable<string>, locale: string): string[] {
  return [...new Set([...values].filter(Boolean))].sort((a, b) => a.localeCompare(b, locale, { numeric: true, sensitivity: 'base' }))
}

/** Choices of the meter filters (from the rows of the tab). */
export function meterOptions(rows: readonly RegisterRow[], env: RegisterEnv): MeterOptions {
  return {
    media: distinct(
      rows.map((r) => mediumLabel(r, env.t, env.te)),
      env.locale,
    ),
    types: distinct(
      rows.map((r) => plainText(r.values?.[TYPE_ATTRIBUTE])),
      env.locale,
    ),
    locations: distinct(
      rows.map((r) => plainText(r.values?.[LOCATION_ATTRIBUTE])),
      env.locale,
    ),
  }
}

/** Years of the "overdue until end of" choice: ten years back and ahead like the Control Center. */
export function yearOptions(year: number): number[] {
  return Array.from({ length: 20 }, (_, i) => year - 10 + i)
}

export interface Count {
  name: string
  count: number
}

export interface MeterSummary {
  total: number
  perMedium: Count[]
  perType: Count[]
  /** verification due before the end of the chosen year */
  overdue: number
}

function counts(names: string[], locale: string): Count[] {
  const map = new Map<string, number>()
  for (const n of names) map.set(n, (map.get(n) ?? 0) + 1)
  return [...map.entries()].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, locale))
}

/** Meter key figures of the shown rows: count per medium and per type (without type: "–"), overdue until end of year. */
export function meterSummary(rows: readonly RegisterRow[], year: number, env: RegisterEnv): MeterSummary {
  return {
    total: rows.length,
    perMedium: counts(
      rows.map((r) => mediumLabel(r, env.t, env.te)),
      env.locale,
    ),
    perType: counts(
      rows.map((r) => plainText(r.values?.[TYPE_ATTRIBUTE]) || env.t('registers.noType')),
      env.locale,
    ),
    overdue: rows.filter((r) => dueUntilEndOf(r, year)).length,
  }
}
