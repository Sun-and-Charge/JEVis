/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import { hasModules, moduleCounts } from '../src/api/navigation'
import { DEFAULT_APPOINTMENT_FILTER, dueState, filterAppointments, relevantDue, sortAppointments } from '../src/modules/appointments'
import { bannerEntries, bannerKey, DISMISSED_STORAGE_KEY, loadDismissed, pruneDismissed, saveDismissed } from '../src/modules/banner'
import { formatDuration, formatLocal, formatLocalDate, formatPeriod, parseLocal } from '../src/modules/dates'
import { DEFAULT_MAINTENANCE_FILTER, filterMaintenance, maintenanceState, sortMaintenance } from '../src/modules/maintenance'
import { compareTimes, matchesSite, siteOptions } from '../src/modules/search'
import { appointment, maintenanceEntry } from './fixtures/modules'

class MemoryStorage implements Storage {
  map = new Map<string, string>()
  get length() {
    return this.map.size
  }
  clear() {
    this.map.clear()
  }
  getItem(key: string) {
    return this.map.get(key) ?? null
  }
  key(i: number) {
    return [...this.map.keys()][i] ?? null
  }
  removeItem(key: string) {
    this.map.delete(key)
  }
  setItem(key: string, value: string) {
    this.map.set(key, value)
  }
}

const ids = (list: { id: number }[]) => list.map((e) => e.id)

describe('module dates', () => {
  it('parses local date-times as wall-clock time', () => {
    expect(parseLocal('2026-11-02T09:00')).toBe(Date.UTC(2026, 10, 2, 9, 0))
    expect(parseLocal('2026-10-31T00:33:51')).toBe(Date.UTC(2026, 9, 31, 0, 33, 51))
    expect(parseLocal('2026-11-06')).toBe(Date.UTC(2026, 10, 6))
    expect(parseLocal(null)).toBeNull()
    expect(parseLocal('soon')).toBeNull()
  })

  it('formats without shifting by the browser zone', () => {
    expect(formatLocal('2026-11-02T09:00', 'de')).toBe('02.11.2026, 09:00')
    expect(formatLocal('2026-03-29T02:30', 'de')).toBe('29.03.2026, 02:30')
    expect(formatLocal('2026-11-02T09:00', 'en')).toContain('2026')
    expect(formatLocal('2026-11-02', 'de')).toBe('02.11.2026')
    expect(formatLocalDate('2026-11-02T09:00', 'de')).toBe('02.11.2026')
    expect(formatLocal(null, 'de')).toBe('')
    expect(formatLocal('n/a', 'de')).toBe('n/a')
  })

  it('formats periods', () => {
    expect(formatPeriod('2026-11-06T18:00', '2026-11-06T18:30', 'de')).toBe('06.11.2026, 18:00–18:30')
    expect(formatPeriod('2026-11-06T18:00', '2026-11-07T02:00', 'de')).toBe('06.11.2026, 18:00 – 07.11.2026, 02:00')
    expect(formatPeriod('2026-11-06T18:00', null, 'de')).toBe('06.11.2026, 18:00')
    expect(formatPeriod(null, null, 'de')).toBe('')
  })

  it('formats durations', () => {
    expect(formatDuration(30, 'en')).toBe('30 min')
    expect(formatDuration(90, 'en')).toMatch(/^1 hr 30 min$/)
    expect(formatDuration(1440 + 60, 'en')).toMatch(/^1 day 1 hr$/)
    expect(formatDuration(0, 'en')).toBe('0 min')
    expect(formatDuration(null, 'en')).toBe('')
    expect(formatDuration(-5, 'en')).toBe('')
  })
})

describe('module search helpers', () => {
  it('lists distinct sites sorted with localized names', () => {
    const list = [
      appointment(1, { site: { id: 2, name: 'Zeta', names: {} } }),
      appointment(2, { site: { id: 1, name: 'Beta', names: { en: 'Alpha' } } }),
      appointment(3, { site: { id: 2, name: 'Zeta', names: {} } }),
      appointment(4, { site: null }),
    ]
    expect(siteOptions(list, 'en')).toEqual([
      { id: 1, name: 'Alpha' },
      { id: 2, name: 'Zeta' },
    ])
  })

  it('matches sites and compares missing times last', () => {
    const site = { id: 5, name: 'S', names: {} }
    expect(matchesSite(site, 'all')).toBe(true)
    expect(matchesSite(site, 5)).toBe(true)
    expect(matchesSite(site, 6)).toBe(false)
    expect(matchesSite(null, 'none')).toBe(true)
    expect(matchesSite(site, 'none')).toBe(false)
    expect([3, null, 1].sort((a, b) => compareTimes(a, b))).toEqual([1, 3, null])
    expect([3, null, 1].sort((a, b) => compareTimes(a, b, -1))).toEqual([3, 1, null])
  })
})

describe('appointments', () => {
  it('derives the due state', () => {
    expect(dueState(appointment(1, { overdue: true }))).toBe('overdue')
    expect(dueState(appointment(1, { dueSoon: true }))).toBe('dueSoon')
    expect(dueState(appointment(1))).toBe('open')
    expect(dueState(appointment(1, { status: 'DONE', overdue: true }))).toBe('done')
    expect(dueState(appointment(1, { status: null }))).toBe('unknown')
  })

  it('uses the next due date of done recurring entries', () => {
    expect(relevantDue(appointment(1, { due: '2026-11-02T09:00', nextDue: '2027-11-02T09:00' }))).toBe('2026-11-02T09:00')
    expect(relevantDue(appointment(1, { status: 'DONE', due: '2026-11-02T09:00', nextDue: '2027-11-02T09:00' }))).toBe('2027-11-02T09:00')
    expect(relevantDue(appointment(1, { due: null, nextDue: '2027-01-01T00:00' }))).toBe('2027-01-01T00:00')
  })

  it('sorts open by due ascending, then done descending, unreadable last', () => {
    const list = [
      appointment(1, { status: 'DONE', due: '2026-01-01T00:00' }),
      appointment(2, { due: '2026-12-24T10:00' }),
      appointment(3, { status: null, due: null }),
      appointment(4, { due: '2026-11-02T09:00', overdue: true }),
      appointment(5, { due: null }),
      appointment(6, { status: 'DONE', due: '2026-06-01T00:00' }),
    ]
    expect(ids(sortAppointments(list))).toEqual([4, 2, 5, 6, 1, 3])
  })

  it('filters by status, site and text', () => {
    const list = [
      appointment(1, { title: 'Calibration meter', site: { id: 1, name: 'North', names: {} } }),
      appointment(2, { status: 'DONE', title: 'Filter change' }),
      appointment(3, { title: 'Inspection', site: null, linkedObject: { id: 9, name: 'Pump X', readable: true } }),
      appointment(4, { status: null, title: 'Broken' }),
      appointment(5, { title: 'Review', recipients: { count: 2, names: ['Team Blue'] } }),
    ]
    const f = DEFAULT_APPOINTMENT_FILTER
    expect(ids(filterAppointments(list, f, 'de'))).toEqual([1, 3, 4, 5])
    expect(ids(filterAppointments(list, { ...f, status: 'done' }, 'de'))).toEqual([2])
    expect(ids(filterAppointments(list, { ...f, status: 'all' }, 'de'))).toEqual([1, 2, 3, 4, 5])
    expect(ids(filterAppointments(list, { ...f, site: 1 }, 'de'))).toEqual([1])
    expect(ids(filterAppointments(list, { ...f, site: 'none' }, 'de'))).toEqual([3])
    expect(ids(filterAppointments(list, { ...f, text: 'pump' }, 'de'))).toEqual([3])
    expect(ids(filterAppointments(list, { ...f, text: '  CALIBRATION  north ' }, 'de'))).toEqual([1])
    expect(ids(filterAppointments(list, { ...f, text: 'blue' }, 'de'))).toEqual([5])
    expect(ids(filterAppointments(list, { ...f, text: 'nothing' }, 'de'))).toEqual([])
  })
})

describe('maintenance', () => {
  it('maps the status', () => {
    expect(maintenanceState({ status: 'PLANNED' })).toBe('planned')
    expect(maintenanceState({ status: 'IN_PROGRESS' })).toBe('inProgress')
    expect(maintenanceState({ status: 'DONE' })).toBe('done')
    expect(maintenanceState({ status: null })).toBe('unknown')
  })

  it('sorts active first by start, then the others by latest start', () => {
    const list = [
      maintenanceEntry(1, { status: 'DONE', active: false, start: '2026-01-01T00:00' }),
      maintenanceEntry(2, { start: '2026-11-10T08:00' }),
      maintenanceEntry(3, { status: 'IN_PROGRESS', start: '2026-11-06T08:00' }),
      maintenanceEntry(4, { status: 'DONE', active: false, start: '2026-10-01T00:00' }),
      maintenanceEntry(5, { start: null }),
    ]
    expect(ids(sortMaintenance(list))).toEqual([3, 2, 5, 4, 1])
  })

  it('filters by status, type, site and text', () => {
    const list = [
      maintenanceEntry(1, { impact: 'Web app not available' }),
      maintenanceEntry(2, { type: 'KNOWN_ISSUE', status: 'IN_PROGRESS' }),
      maintenanceEntry(3, { status: 'DONE', active: false, site: { id: 7, name: 'Site B', names: {} } }),
    ]
    const f = DEFAULT_MAINTENANCE_FILTER
    expect(ids(filterMaintenance(list, f, 'en'))).toEqual([1, 2])
    expect(ids(filterMaintenance(list, { ...f, status: 'done' }, 'en'))).toEqual([3])
    expect(ids(filterMaintenance(list, { ...f, status: 'all', type: 'KNOWN_ISSUE' }, 'en'))).toEqual([2])
    expect(ids(filterMaintenance(list, { ...f, status: 'all', site: 7 }, 'en'))).toEqual([3])
    expect(ids(filterMaintenance(list, { ...f, text: 'web app' }, 'en'))).toEqual([1])
  })
})

describe('maintenance banner', () => {
  it('changes the key when the entry changes', () => {
    const e = maintenanceEntry(1)
    expect(bannerKey(e)).toBe(bannerKey(maintenanceEntry(1, { description: 'other' })))
    expect(bannerKey(e)).not.toBe(bannerKey({ ...e, status: 'IN_PROGRESS' }))
    expect(bannerKey(e)).not.toBe(bannerKey({ ...e, start: '2026-11-07T18:00' }))
    expect(bannerKey(e)).not.toBe(bannerKey({ ...e, title: 'New title' }))
    expect(bannerKey(e)).not.toBe(bannerKey({ ...e, id: 2 }))
  })

  it('shows active, not dismissed entries by start', () => {
    const list = [
      maintenanceEntry(1, { start: '2026-11-08T08:00' }),
      maintenanceEntry(2, { start: '2026-11-07T08:00' }),
      maintenanceEntry(3, { status: 'DONE', active: false }),
      maintenanceEntry(4),
    ]
    expect(ids(bannerEntries(list, new Set()))).toEqual([4, 2, 1])
    expect(ids(bannerEntries(list, new Set([bannerKey(list[1])])))).toEqual([4, 1])
    // changed after dismissing: shown again
    const changed = { ...list[1], status: 'IN_PROGRESS' as const }
    expect(ids(bannerEntries([changed], new Set([bannerKey(list[1])])))).toEqual([2])
  })

  it('stores dismissed keys and survives broken or missing storage', () => {
    const storage = new MemoryStorage()
    saveDismissed(new Set(['a', 'b']), storage)
    expect([...loadDismissed(storage)]).toEqual(['a', 'b'])
    storage.setItem(DISMISSED_STORAGE_KEY, '{broken')
    expect(loadDismissed(storage).size).toBe(0)
    storage.setItem(DISMISSED_STORAGE_KEY, '[1,"x"]')
    expect([...loadDismissed(storage)]).toEqual(['x'])
    expect(loadDismissed(null).size).toBe(0)
    const throwing = {
      getItem() {
        throw new Error('denied')
      },
      setItem() {
        throw new Error('denied')
      },
    } as unknown as Storage
    expect(loadDismissed(throwing).size).toBe(0)
    expect(() => saveDismissed(new Set(['a']), throwing)).not.toThrow()
  })

  it('prunes keys of entries that ended or changed', () => {
    const e1 = maintenanceEntry(1)
    const e2 = maintenanceEntry(2)
    const keys = new Set([bannerKey(e1), bannerKey(e2), 'old'])
    const pruned = pruneDismissed(keys, [e1, { ...e2, status: 'DONE', active: false }])
    expect([...pruned]).toEqual([bannerKey(e1)])
  })
})

describe('navigation modules', () => {
  it('reads module counts, missing ones as 0', () => {
    const none = { appointments: 0, maintenance: 0, actionPlans: 0, nonconformities: 0, legal: 0, meters: 0, basedata: 0, equipment: 0, notes: 0, alarms: 0, alarmsOpen: 0, reports: 0, contracts: 0, invoices: 0, costs: 0 }
    expect(moduleCounts(null)).toEqual(none)
    expect(moduleCounts({})).toEqual(none)
    expect(moduleCounts({ modules: { appointments: 3 } })).toEqual({ ...none, appointments: 3 })
    expect(moduleCounts({ modules: { appointments: -1, maintenance: 2 } })).toEqual({ ...none, maintenance: 2 })
    expect(moduleCounts({ modules: { actionPlans: 2, nonconformities: 1, legal: 4 } })).toEqual({ ...none, actionPlans: 2, nonconformities: 1, legal: 4 })
    expect(moduleCounts({ modules: { meters: 7, basedata: 2, equipment: 0 } })).toEqual({ ...none, meters: 7, basedata: 2 })
    expect(hasModules(none)).toBe(false)
    expect(hasModules({ ...none, equipment: 1 })).toBe(true)
    expect(hasModules({ ...none, legal: 1 })).toBe(true)
    expect(moduleCounts({ modules: { alarms: 2, alarmsOpen: 5 } })).toEqual({ ...none, alarms: 2, alarmsOpen: 5 })
    expect(hasModules({ ...none, alarms: 1 })).toBe(true)
    // the badge count alone is no module
    expect(hasModules({ ...none, alarmsOpen: 3 })).toBe(false)
    expect(moduleCounts({ modules: { reports: 4 } })).toEqual({ ...none, reports: 4 })
    expect(hasModules({ ...none, reports: 1 })).toBe(true)
    expect(moduleCounts({ modules: { contracts: 6, invoices: 12 } })).toEqual({ ...none, contracts: 6, invoices: 12 })
    expect(hasModules({ ...none, contracts: 1 })).toBe(true)
    expect(hasModules({ ...none, invoices: 1 })).toBe(true)
    expect(moduleCounts({ modules: { costs: 3 } })).toEqual({ ...none, costs: 3 })
    expect(hasModules({ ...none, costs: 1 })).toBe(true)
  })
})
