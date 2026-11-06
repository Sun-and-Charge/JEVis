/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { localName, type NavItem } from '../api/navigation'

/** Lower-case words of a search text. */
export function searchTerms(text: string, locale: string): string[] {
  return text.toLocaleLowerCase(locale).split(/\s+/).filter(Boolean)
}

/** True if every term occurs in one of the texts (no terms: always true). */
export function matchesTerms(terms: string[], texts: (string | null | undefined)[], locale: string): boolean {
  if (!terms.length) return true
  const haystack = texts.filter(Boolean).join('\n').toLocaleLowerCase(locale)
  return terms.every((term) => haystack.includes(term))
}

export interface SiteOption {
  id: number
  name: string
}

/** Distinct sites of the entries, sorted by name (for the site filter). */
export function siteOptions(entries: readonly { site: NavItem | null }[], locale: string): SiteOption[] {
  const sites = new Map<number, SiteOption>()
  for (const e of entries) if (e.site && !sites.has(e.site.id)) sites.set(e.site.id, { id: e.site.id, name: localName(e.site, locale) })
  return [...sites.values()].sort((a, b) => a.name.localeCompare(b.name, locale))
}

/** Site filter: a site id, 'all' or 'none' (entries without site). */
export type SiteFilter = number | 'all' | 'none'

export function matchesSite(site: NavItem | null, filter: SiteFilter): boolean {
  if (filter === 'all') return true
  if (filter === 'none') return site == null
  return site?.id === filter
}

/** Compares nullable times, missing ones last in both directions. */
export function compareTimes(a: number | null, b: number | null, direction: 1 | -1 = 1): number {
  if (a == null && b == null) return 0
  if (a == null) return 1
  if (b == null) return -1
  return (a - b) * direction
}
