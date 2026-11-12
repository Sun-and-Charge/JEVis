/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { buildSiteTree, localName, type NavItem, type Navigation, type SiteNode } from '../api/navigation'
import { matchesTerms, searchTerms } from '../modules/search'
import { itemKey, type ItemKind, type SiteChoiceValue } from './prefs'

/*
 * Dashboards and analyses of the navigation as flat lists (overview pages, favourites, recently opened, search) and
 * the sites as a hierarchical choice (site picker, overview filter).
 */

export interface CatalogEntry {
  kind: ItemKind
  id: number
  /** favourite / recent key */
  key: string
  name: string
  siteId: number | null
  /** names from the top site down to the item's site ("Muster AG › Musterwerk"); null for items without site */
  sitePath: string | null
}

export interface SiteChoice {
  id: number
  name: string
  path: string
  /** nesting level (0 = top) */
  depth: number
}

export const PATH_SEPARATOR = ' › '

const byName = (locale: string) => (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, locale, { sensitivity: 'base' })

/** Sites in tree order (children below their parent, siblings by name) with their path. */
export function siteChoices(nav: Pick<Navigation, 'sites'> | null, locale: string): SiteChoice[] {
  const out: SiteChoice[] = []
  const walk = (nodes: SiteNode[], depth: number, parentPath: string | null) => {
    const named = nodes.map((node) => ({ node, name: localName(node.site, locale) })).sort(byName(locale))
    for (const { node, name } of named) {
      const path = parentPath ? parentPath + PATH_SEPARATOR + name : name
      out.push({ id: node.site.id, name, path, depth })
      walk(node.children, depth + 1, path)
    }
  }
  walk(buildSiteTree(nav?.sites ?? []), 0, null)
  return out
}

/** The site and all sites below it. */
export function siteAndDescendants(nav: Pick<Navigation, 'sites'> | null, siteId: number): Set<number> {
  const ids = new Set<number>([siteId])
  const sites = nav?.sites ?? []
  let grown = true
  while (grown) {
    grown = false
    for (const s of sites) {
      if (s.parentId != null && ids.has(s.parentId) && !ids.has(s.id)) {
        ids.add(s.id)
        grown = true
      }
    }
  }
  return ids
}

/** All dashboards or analyses (sites first in tree order, then the ones without site), sorted by name. */
export function catalog(nav: Pick<Navigation, 'sites' | 'other'> | null, kind: ItemKind, locale: string): CatalogEntry[] {
  if (!nav) return []
  const paths = new Map(siteChoices(nav, locale).map((s) => [s.id, s.path]))
  const list = (items: NavItem[] | undefined, siteId: number | null): CatalogEntry[] =>
    (items ?? []).map((item) => ({
      kind,
      id: item.id,
      key: itemKey(kind, item.id),
      name: localName(item, locale),
      siteId,
      sitePath: siteId == null ? null : (paths.get(siteId) ?? null),
    }))
  const field = kind === 'dashboard' ? 'dashboards' : 'analyses'
  const entries = [...nav.sites.flatMap((s) => list(s[field], s.id)), ...list(nav.other?.[field], null)]
  /* an item linked at several sites appears once (first site) */
  const seen = new Set<string>()
  return entries
    .filter((e) => (seen.has(e.key) ? false : (seen.add(e.key), true)))
    .sort((a, b) => byName(locale)(a, b) || (a.sitePath ?? '￿').localeCompare(b.sitePath ?? '￿', locale))
}

/** Filter of the overview pages. */
export interface OverviewFilter {
  text: string
  site: SiteChoiceValue
  favouritesOnly: boolean
}

/**
 * Entries matching the filter: all search words in the name or site path, the site including its sub-sites (items
 * without site only with "Alle"), only favourites if asked.
 */
export function filterCatalog(
  entries: readonly CatalogEntry[],
  filter: OverviewFilter,
  favourites: ReadonlySet<string> | readonly string[],
  nav: Pick<Navigation, 'sites'> | null,
  locale: string,
): CatalogEntry[] {
  const favs = favourites instanceof Set ? favourites : new Set(favourites as readonly string[])
  const terms = searchTerms(filter.text, locale)
  const sites = filter.site === 'all' ? null : siteAndDescendants(nav, filter.site)
  return entries.filter(
    (e) =>
      (!filter.favouritesOnly || favs.has(e.key)) &&
      (sites === null || (e.siteId != null && sites.has(e.siteId))) &&
      matchesTerms(terms, [e.name, e.sitePath], locale),
  )
}

/** The favourites among the entries, sorted by name, at most `max` (sidebar). */
export function favouriteEntries(entries: readonly CatalogEntry[], favourites: readonly string[], max = Infinity): CatalogEntry[] {
  const favs = new Set(favourites)
  return entries.filter((e) => favs.has(e.key)).slice(0, max)
}

/** The recently opened items that still exist, most recent first. */
export function recentEntries(entries: readonly CatalogEntry[], recent: readonly string[]): CatalogEntry[] {
  const byKey = new Map(entries.map((e) => [e.key, e]))
  return recent.map((key) => byKey.get(key)).filter((e): e is CatalogEntry => e !== undefined)
}

/** Route of an entry. */
export function entryRoute(entry: Pick<CatalogEntry, 'kind' | 'id'>): { name: 'dashboard' | 'analysis'; params: { id: number } } {
  return { name: entry.kind, params: { id: entry.id } }
}

/** The dashboard or analysis a route shows (for "recently opened"), or null. */
export function openedItemKey(routeName: unknown, params: Record<string, unknown>): string | null {
  if (routeName !== 'dashboard' && routeName !== 'analysis') return null
  const id = Number(params.id)
  if (!Number.isInteger(id) || id <= 0) return null
  return itemKey(routeName, id)
}

/** The chosen site if it still exists in the navigation, else 'all'. */
export function effectiveSite(choice: SiteChoiceValue, nav: Pick<Navigation, 'sites'> | null): SiteChoiceValue {
  return choice !== 'all' && nav?.sites.some((s) => s.id === choice) ? choice : 'all'
}

/**
 * Preselection in module views with one tab (or plan) per site: the item of the chosen site, else the first item
 * of a site below it; null if there is none (or no site is chosen).
 */
export function preferredBySite<K>(
  items: readonly { key: K; site: { id: number } | null }[],
  choice: SiteChoiceValue,
  nav: Pick<Navigation, 'sites'> | null,
): K | null {
  if (choice === 'all') return null
  const exact = items.find((i) => i.site?.id === choice)
  if (exact) return exact.key
  const below = siteAndDescendants(nav, choice)
  return items.find((i) => i.site != null && below.has(i.site.id))?.key ?? null
}
