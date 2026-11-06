/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { Appointment } from '../api/modules'
import { localName } from '../api/navigation'
import { parseLocal } from './dates'
import { compareTimes, matchesSite, matchesTerms, searchTerms, type SiteFilter } from './search'

export type AppointmentStatusFilter = 'open' | 'done' | 'all'

export interface AppointmentFilter {
  status: AppointmentStatusFilter
  site: SiteFilter
  text: string
}

export const DEFAULT_APPOINTMENT_FILTER: AppointmentFilter = { status: 'open', site: 'all', text: '' }

/** State of an entry for markers and badges (overdue / dueSoon as computed by the server). */
export type DueState = 'overdue' | 'dueSoon' | 'open' | 'done' | 'unknown'

export function dueState(a: Pick<Appointment, 'status' | 'overdue' | 'dueSoon'>): DueState {
  if (a.status == null) return 'unknown'
  if (a.status === 'DONE') return 'done'
  if (a.overdue) return 'overdue'
  if (a.dueSoon) return 'dueSoon'
  return 'open'
}

/** The date that matters for sorting: the due date; for done recurring entries the next one. */
export function relevantDue(a: Pick<Appointment, 'status' | 'due' | 'nextDue'>): string | null {
  if (a.status === 'DONE' && a.nextDue) return a.nextDue
  return a.due ?? a.nextDue ?? null
}

/** Open entries first (earliest due first, so overdue ones lead), then done ones (latest first), unreadable last. */
export function sortAppointments(list: readonly Appointment[]): Appointment[] {
  const group = (a: Appointment) => (a.status == null ? 2 : a.status === 'DONE' ? 1 : 0)
  return [...list].sort((a, b) => {
    const g = group(a) - group(b)
    if (g) return g
    const byDue = compareTimes(parseLocal(relevantDue(a)), parseLocal(relevantDue(b)), group(a) === 1 ? -1 : 1)
    return byDue || a.title.localeCompare(b.title) || a.id - b.id
  })
}

/** Status 'open' includes unreadable entries (status null), so they do not go unnoticed. */
export function filterAppointments(list: readonly Appointment[], filter: AppointmentFilter, locale: string): Appointment[] {
  const terms = searchTerms(filter.text, locale)
  return list.filter((a) => {
    if (filter.status === 'open' && a.status === 'DONE') return false
    if (filter.status === 'done' && a.status !== 'DONE') return false
    if (!matchesSite(a.site, filter.site)) return false
    return matchesTerms(
      terms,
      [a.title, a.name, a.description, a.site ? localName(a.site, locale) : null, a.linkedObject?.name, a.directory?.name, ...(a.recipients?.names ?? [])],
      locale,
    )
  })
}
