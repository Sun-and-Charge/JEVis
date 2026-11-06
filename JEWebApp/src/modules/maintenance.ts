/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { MaintenanceEntry } from '../api/modules'
import { localName } from '../api/navigation'
import { parseLocal } from './dates'
import { compareTimes, matchesSite, matchesTerms, searchTerms, type SiteFilter } from './search'

export type MaintenanceStatusFilter = 'active' | 'done' | 'all'
export type MaintenanceTypeFilter = 'all' | 'MAINTENANCE' | 'KNOWN_ISSUE'

export interface MaintenanceFilter {
  status: MaintenanceStatusFilter
  type: MaintenanceTypeFilter
  site: SiteFilter
  text: string
}

export const DEFAULT_MAINTENANCE_FILTER: MaintenanceFilter = { status: 'active', type: 'all', site: 'all', text: '' }

export type MaintenanceState = 'inProgress' | 'planned' | 'done' | 'unknown'

export function maintenanceState(e: Pick<MaintenanceEntry, 'status'>): MaintenanceState {
  switch (e.status) {
    case 'IN_PROGRESS':
      return 'inProgress'
    case 'PLANNED':
      return 'planned'
    case 'DONE':
      return 'done'
    default:
      return 'unknown'
  }
}

/** Active entries first (earliest start first), then the others (latest start first). */
export function sortMaintenance(list: readonly MaintenanceEntry[]): MaintenanceEntry[] {
  return [...list].sort((a, b) => {
    if (a.active !== b.active) return a.active ? -1 : 1
    const byStart = compareTimes(parseLocal(a.start), parseLocal(b.start), a.active ? 1 : -1)
    return byStart || a.title.localeCompare(b.title) || a.id - b.id
  })
}

export function filterMaintenance(list: readonly MaintenanceEntry[], filter: MaintenanceFilter, locale: string): MaintenanceEntry[] {
  const terms = searchTerms(filter.text, locale)
  return list.filter((e) => {
    if (filter.status === 'active' && !e.active) return false
    if (filter.status === 'done' && e.status !== 'DONE') return false
    if (filter.type !== 'all' && e.type !== filter.type) return false
    if (!matchesSite(e.site, filter.site)) return false
    return matchesTerms(terms, [e.title, e.name, e.description, e.impact, e.site ? localName(e.site, locale) : null, e.directory?.name], locale)
  })
}
