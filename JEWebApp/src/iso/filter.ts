/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { NavItem } from '../api/navigation'

/** Value of a tag select meaning "all". */
export const ALL = ''

/** A plan id or 'all' (overview of all plans with a plan column). */
export type PlanFilter = number | 'all'

/** Reference to the plan a row belongs to. */
export interface PlanRef {
  id: number
  name: string
  site: NavItem | null
}

/** One list row: the item and its plan. */
export interface PlanRow<P extends PlanRef, T> {
  plan: P
  item: T
}

/** Flat rows of all plans (in plan order, items as answered). */
export function planRows<P extends PlanRef, T>(plans: readonly P[], items: (plan: P) => readonly T[] | null | undefined): PlanRow<P, T>[] {
  return plans.flatMap((plan) => (items(plan) ?? []).map((item) => ({ plan, item })))
}

export function matchesPlan(plan: PlanRef, filter: PlanFilter): boolean {
  return filter === 'all' || plan.id === filter
}

/** Plans the filter selects. */
export function selectedPlans<P extends PlanRef>(plans: readonly P[], filter: PlanFilter): P[] {
  return plans.filter((p) => matchesPlan(p, filter))
}

/**
 * Tags of a value: lists as they are, texts split at ';' like the Control Center (old entries keep several tags in
 * one text). Trimmed, empty ones dropped.
 */
export function splitTags(value: string | readonly (string | null)[] | null | undefined): string[] {
  if (value == null) return []
  const parts = typeof value === 'string' ? value.split(';') : value.flatMap((v) => (v == null ? [] : v.split(';')))
  return parts.map((p) => p.trim()).filter(Boolean)
}

/** True if no tag is selected or one of the tags equals it (ignoring case, as the Control Center). */
export function matchesTag(value: string | readonly (string | null)[] | null | undefined, selected: string): boolean {
  if (!selected) return true
  const wanted = selected.toLowerCase()
  return splitTags(value).some((tag) => tag.toLowerCase() === wanted)
}

/**
 * Options of a tag filter: the configured tags of the plans first (in their order), then tags only found in the
 * items (sorted). Duplicates ignoring case are dropped.
 */
export function tagOptions(configured: readonly (readonly string[] | null | undefined)[], values: readonly (string | readonly (string | null)[] | null | undefined)[], locale: string): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  const add = (tag: string) => {
    const key = tag.toLowerCase()
    if (seen.has(key)) return false
    seen.add(key)
    result.push(tag)
    return true
  }
  for (const list of configured) for (const tag of splitTags(list ?? [])) add(tag)
  const extra: string[] = []
  for (const value of values) for (const tag of splitTags(value)) if (!seen.has(tag.toLowerCase())) extra.push(tag)
  const sorted = [...new Set(extra)].sort((a, b) => a.localeCompare(b, locale))
  for (const tag of sorted) add(tag)
  return result
}

const DAY_MS = 86_400_000
const DATE = /^(\d{4})-(\d{2})-(\d{2})/

/**
 * The calendar day of a stored date (as written, the offset is ignored so the day never shifts with the browser's
 * zone) as UTC milliseconds; null if missing or not a date.
 */
export function dayOf(text: string | null | undefined): number | null {
  const m = text ? DATE.exec(text) : null
  if (!m) return null
  const ms = Date.UTC(+m[1], +m[2] - 1, +m[3])
  return Number.isNaN(ms) ? null : ms
}

/** Whole days from one stored date to another (negative if `to` is earlier); null if one is missing. */
export function daysBetween(from: string | null | undefined, to: string | null | undefined): number | null {
  const a = dayOf(from)
  const b = dayOf(to)
  return a == null || b == null ? null : Math.round((b - a) / DAY_MS)
}

/** Date range of a list: the date field to filter by and from / until as yyyy-MM-dd ('' = open). */
export interface DateRange<F extends string = string> {
  dateField: F
  from: string
  until: string
}

/**
 * True if the date lies in the range (days inclusive). Without from and until everything passes; with a range,
 * entries without that date are hidden (as in the Control Center).
 */
export function matchesDateRange(date: string | null | undefined, from: string, until: string): boolean {
  if (!from && !until) return true
  const day = dayOf(date)
  if (day == null) return false
  const start = dayOf(from)
  const end = dayOf(until)
  if (start != null && day < start) return false
  if (end != null && day > end) return false
  return true
}
