/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'
import type { NoteRow } from './notes'

/** Which alarms GET /web/alarms answers: of unconfirmed configurations (default), of confirmed ones or all. */
export type AlarmState = 'open' | 'checked' | 'all'

export const ALARM_STATES: readonly AlarmState[] = ['open', 'checked', 'all']

/** The "Alarm Configuration" of an alarm; confirmation is per configuration (latest "Alarm Checked"). */
export interface AlarmConfiguration {
  id: number
  name: string
  checked: boolean
  /** account that confirmed (latest "Alarm Checked" sample if true), null if unknown or not confirmed */
  checkedBy: string | null
  /** ISO 8601 with offset, null if not confirmed */
  checkedAt: string | null
}

/** One alarm of GET /web/alarms (one entry of a JEAlarm log file). */
export interface Alarm {
  /** `<configuration>-<epoch ms of the alarm>-<index in the file>` */
  id: string
  /** time of the alarm, ISO 8601 with offset */
  timestamp: string
  /** time of the log file sample */
  logged: string
  configuration: AlarmConfiguration
  /** the object of the alarm (same rules as the notes' row); null if it no longer exists */
  row: NoteRow | null
  site: { id: number; name: string; names?: Record<string, string> } | null
  attribute: string | null
  isValue: number | null
  /** normalised: > ≥ = ≠ < ≤ (other texts unchanged) */
  operator: string | null
  shouldBeValue: number | null
  tolerance: number | null
  /** display unit, "" if none */
  unit: string
  /** L1, L2, D1, D2, DYNAMIC, STATIC, GAP, NONE */
  type: string
  /** translated by the server (lang), e.g. "Grenzwert Stufe 1" */
  typeName: string
  /** 0 system, 1 normal, 2 standby, 4 silent; null if missing (name then "System") */
  logValue: number | null
  logValueName: string
}

/** Answer of GET /web/alarms. */
export interface AlarmList {
  from: string
  to: string
  state: AlarmState
  /** newest timestamp first, at most 5000 */
  alarms: Alarm[]
  truncated: boolean
}

export interface AlarmQuery {
  /** yyyy-MM-dd, inclusive */
  from: string
  to: string
  state: AlarmState
  timeZone: string
  lang: string
}

type GetClient = Pick<HttpClient, 'get'>

export function alarmsPath(query: AlarmQuery): string {
  const p = new URLSearchParams({ from: query.from, to: query.to, state: query.state, timeZone: query.timeZone, lang: query.lang })
  return `/web/alarms?${p.toString()}`
}

export function loadAlarms(http: GetClient, query: AlarmQuery): Promise<AlarmList> {
  return http.get<AlarmList>(alarmsPath(query))
}
