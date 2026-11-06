/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { Appointment, MaintenanceEntry } from '../../src/api/modules'

/** Neutral appointment (open, not due soon) with overrides. */
export function appointment(id: number, overrides: Partial<Appointment> = {}): Appointment {
  return {
    id,
    name: `Appointment ${id}`,
    site: { id: 1001, name: 'Site A', names: {} },
    directory: { id: 3000, name: 'Appointments' },
    title: `Appointment ${id}`,
    description: null,
    due: '2026-12-01T09:00',
    nextDue: null,
    recurrence: 'NONE',
    anchorDay: null,
    reminderDays: 3,
    reminderDay: '2026-11-28',
    status: 'OPEN',
    overdue: false,
    dueSoon: false,
    lastDone: null,
    linkedObject: null,
    recipients: { count: 0, names: [] },
    lastMail: null,
    created: '2026-10-01T08:00:00',
    createdBy: 'User 1',
    ...overrides,
  }
}

/** Neutral maintenance entry (planned, active) with overrides. */
export function maintenanceEntry(id: number, overrides: Partial<MaintenanceEntry> = {}): MaintenanceEntry {
  return {
    id,
    name: `Entry ${id}`,
    site: null,
    directory: { id: 3100, name: 'Maintenance' },
    type: 'MAINTENANCE',
    title: `Entry ${id}`,
    description: null,
    impact: null,
    start: '2026-11-06T18:00',
    end: '2026-11-06T18:30',
    durationMinutes: 30,
    status: 'PLANNED',
    active: true,
    recipients: { count: 0, names: [] },
    informRequested: null,
    doneAt: null,
    lastMail: null,
    created: '2026-10-01T08:00:00',
    createdBy: 'User 1',
    ...overrides,
  }
}
