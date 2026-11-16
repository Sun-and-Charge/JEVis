/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { ReadingMeter, ReviewItem } from '../api/readings'
import { toDateTimeInput, toMinute } from '../edit/edit'

/*
 * Review of contradictory readings (E8.5, docs/WEB-API.md "Editing, stage E8.5"): site tabs, issue badges, the
 * actions allowed per item, the meter for the correction panel and the prefill of the meter exchange.
 */

/** Key of the "Alle" tab. */
export const ALL_SITES = 'all'

export interface ReviewSiteTab {
  key: string
  name: string
  site: { id: number; name: string } | null
  count: number
}

const siteKey = (item: Pick<ReviewItem, 'site'>) => (item.site ? String(item.site.id) : 'none')

/** One tab per site of the items (sorted by name, items without site last), with the number of items. */
export function reviewSites(items: readonly Pick<ReviewItem, 'site'>[], locale: string, noSiteName: string): ReviewSiteTab[] {
  const map = new Map<string, ReviewSiteTab>()
  for (const item of items) {
    const key = siteKey(item)
    const tab = map.get(key)
    if (tab) tab.count++
    else map.set(key, { key, name: item.site?.name ?? noSiteName, site: item.site ?? null, count: 1 })
  }
  return [...map.values()].sort((a, b) => (a.site ? 0 : 1) - (b.site ? 0 : 1) || a.name.localeCompare(b.name, locale))
}

/** Items of a tab (all for {@link ALL_SITES}). */
export function itemsOfSite<I extends Pick<ReviewItem, 'site'>>(items: readonly I[], key: string): I[] {
  return key === ALL_SITES ? [...items] : items.filter((i) => siteKey(i) === key)
}

/** Badge class of an issue: a counter going backwards red, the others amber. */
export function issueClass(issue: string): string {
  switch (issue) {
    case 'COUNTER_DECREASE':
      return 'is-overdue'
    case 'FAR_FROM_EXPECTED':
    case 'LIMIT':
      return 'is-dueSoon'
    default:
      return 'is-unknown'
  }
}

/** Row class of an item (the worst issue). */
export function reviewRowClass(item: Pick<ReviewItem, 'issues'>): string {
  return item.issues.includes('COUNTER_DECREASE') ? 'is-overdue' : 'is-dueSoon'
}

export interface ReviewActions {
  correct: boolean
  exchange: boolean
  confirm: boolean
  ignore: boolean
}

/** Actions the user may take on an item (the server checks the rights again). */
export function reviewActions(item: Pick<ReviewItem, 'row' | 'canExchange'>): ReviewActions {
  return {
    correct: !!item.row?.canEnter,
    exchange: !!item.row?.counter && !!item.canExchange,
    confirm: !!item.row?.canNote,
    ignore: !!item.row?.canDelete,
  }
}

/**
 * The meter for the correction panel: the meter of the reading list if it is there (mode, time zone, readings), else
 * one built from the item (date and time input, no readings).
 */
export function reviewMeter(item: ReviewItem, meters: readonly ReadingMeter[]): ReadingMeter {
  const found = meters.find((m) => m.id === item.meterId)
  if (found?.row) return found
  return {
    id: item.meterId,
    name: item.meterName,
    className: 'Measurement Instrument',
    classLabel: null,
    medium: found?.medium ?? null,
    path: item.site?.name ?? null,
    serialNumber: found?.serialNumber ?? null,
    meterPoint: found?.meterPoint ?? null,
    location: found?.location ?? null,
    canPhoto: found?.canPhoto ?? false,
    canExchange: item.canExchange,
    row: {
      id: item.row.id,
      name: item.row.name,
      unit: item.row.unit,
      counter: item.row.counter,
      period: null,
      mode: 'SPECIFIC_DATETIME',
      timeZone: null,
      limits: null,
      canEnter: item.row.canEnter,
      canDelete: item.row.canDelete,
      decimals: null,
    },
    due: 'NONE',
    suggestedTimestamp: null,
    expected: item.expected,
    readings: [],
    problem: null,
  }
}

/** A number as the user would type it (no grouping, locale decimal separator); '' if it is no number. */
export function numberInput(value: number | string | null | undefined, locale: string): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return ''
  return new Intl.NumberFormat(locale, { useGrouping: false, maximumFractionDigits: 10 }).format(value)
}

/** Prefill of the meter exchange: the item's time and its value as the new meter's first reading. */
export function exchangePrefill(item: Pick<ReviewItem, 'timestamp' | 'value'>, locale: string): { when: string; newReading: string } {
  const ms = Date.parse(item.timestamp)
  return { when: Number.isNaN(ms) ? '' : toDateTimeInput(toMinute(ms)), newReading: numberInput(item.value, locale) }
}

/** A review comment: at most `max` characters, no control characters except line breaks (as the server). */
export function commentProblem(comment: string, max: number): 'tooLong' | 'invalid' | null {
  if (comment.trim().length > max) return 'tooLong'
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u0009\u000b\u000c\u000e-\u001f\u007f]/.test(comment)) return 'invalid'
  return null
}
