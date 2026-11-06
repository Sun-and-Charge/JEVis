/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'
import type { NavItem } from './navigation'

/** Id and name of the folder an entry is in. */
export interface ModuleRef {
  id: number
  name: string
}

/** Recipients of the reminder / information mails: names only of those the user may read, count = all. */
export interface Recipients {
  count: number
  names: string[]
}

export type Recurrence = 'NONE' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY'
export type AppointmentStatus = 'OPEN' | 'DONE'

/** One appointment of GET /web/appointments. Date-times are the server's local time (yyyy-MM-ddTHH:mm). */
export interface Appointment {
  id: number
  name: string
  site: NavItem | null
  directory: ModuleRef | null
  title: string
  description: string | null
  due: string | null
  nextDue: string | null
  recurrence: Recurrence | string | null
  anchorDay: number | null
  reminderDays: number | null
  reminderDay: string | null
  /** null if the JSON of the entry could not be read */
  status: AppointmentStatus | null
  overdue: boolean
  dueSoon: boolean
  lastDone: string | null
  /** without read right only id and readable: false */
  linkedObject: { id: number; name?: string | null; readable: boolean } | null
  recipients: Recipients | null
  lastMail: string | null
  created: string | null
  createdBy: string | null
}

export interface AppointmentList {
  /** server date used for overdue / dueSoon */
  today: string
  appointments: Appointment[]
}

export type MaintenanceType = 'MAINTENANCE' | 'KNOWN_ISSUE'
export type MaintenanceStatus = 'PLANNED' | 'IN_PROGRESS' | 'DONE'

/** One entry of GET /web/maintenance. */
export interface MaintenanceEntry {
  id: number
  name: string
  site: NavItem | null
  directory: ModuleRef | null
  type: MaintenanceType | string | null
  title: string
  description: string | null
  impact: string | null
  start: string | null
  /** start + duration, null without duration */
  end: string | null
  durationMinutes: number | null
  status: MaintenanceStatus | null
  /** PLANNED or IN_PROGRESS: shown as banner */
  active: boolean
  recipients: Recipients | null
  informRequested: string | null
  doneAt: string | null
  lastMail: string | null
  created: string | null
  createdBy: string | null
}

export interface MaintenanceList {
  now: string
  entries: MaintenanceEntry[]
}

type GetClient = Pick<HttpClient, 'get'>

export function loadAppointments(http: GetClient): Promise<AppointmentList> {
  return http.get<AppointmentList>('/web/appointments')
}

export function loadMaintenance(http: GetClient): Promise<MaintenanceList> {
  return http.get<MaintenanceList>('/web/maintenance')
}
