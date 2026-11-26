/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'
import type { RoundOptionPerson } from './readingRounds'

/*
 * Alarm settings (E7): alarm configurations with their e-mail notification (E7.1) and the limit steps and alarm rules
 * of a clean data row (E7.2), written by the server in the Control Center's formats.
 */

/** Which rows an alarm configuration checks (`AlarmScope`). */
export const ALARM_SCOPES = ['COMPLETE', 'SELECTED', 'WITHOUT_SELECTED', 'NONE'] as const
export type AlarmScope = (typeof ALARM_SCOPES)[number]

/** Periods JEAlarm accepts (the web offers these; `CUSTOM` only when the configuration already has one). */
export const ALARM_PERIODS = ['MINUTELY', 'QUARTER_HOURLY', 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'NONE'] as const

/** A row of an alarm configuration ("Alarm Objects" `id:Attribute`). */
export interface AlarmObjectRef {
  id: number
  attribute: string
  /** null if the user may not read the row */
  name: string | null
  path?: string | null
  readable: boolean
}

/** A user or group as stored in a notification (names only if readable). */
export interface PersonRef {
  id: number
  name: string | null
  kind: 'USER' | 'GROUP' | string
}

/** The "E-Mail Notification" child of a configuration. */
export interface AlarmNotification {
  id: number | null
  enabled: boolean
  recipientUsers: PersonRef[]
  recipientGroups: PersonRef[]
  recipients: string[]
  cc: string[]
  bcc: string[]
  subject: string | null
  message: string | null
  html: boolean
}

export interface AlarmSite {
  id: number
  name: string
}

/** One "Alarm Configuration" of GET /web/alarms/settings. */
export interface AlarmConfigSettings {
  id: number
  name: string
  path: string | null
  site: AlarmSite | null
  enabled: boolean
  scope: AlarmScope | string
  objects: AlarmObjectRef[]
  period: string
  bindToData: boolean
  includeLimits: boolean
  disableLink: boolean
  /** daily summary of alarms and outages (docs/ALARMS-OUTAGE.md); missing on older servers */
  dailySummary?: boolean
  /** "HH:mm" in the time zone of the site */
  dailySummaryTime?: string | null
  /** ISO, read-only (written by JEAlarm) */
  timeStamp: string | null
  /** null if the configuration has no notification */
  notification: AlarmNotification | null
  /** more than one notification: JEAlarm sends nothing, the web edits none */
  multipleNotifications?: boolean
  canWrite: boolean
  canDelete: boolean
  version: string | null
}

/** An "Alarm Directory" where the user may create a configuration. */
export interface AlarmSettingsTarget {
  id: number
  name: string
  path: string | null
  site: AlarmSite | null
}

/** Answer of GET /web/alarms/settings. */
export interface AlarmSettingsList {
  configurations: AlarmConfigSettings[]
  targets: AlarmSettingsTarget[]
  /** users and groups for the recipients (as E8.2 options); assumed additive, missing on older servers */
  people?: RoundOptionPerson[]
}

/** Notification part of a save. */
export interface AlarmNotificationBody {
  enabled: boolean
  recipientUsers: number[]
  recipientGroups: number[]
  recipients: string[]
  cc: string[]
  bcc: string[]
  subject: string
  message: string
  html: boolean
}

/** Fields of POST / PUT (PUT sends only the changed ones plus `version`). */
export interface AlarmConfigBody {
  name?: string
  enabled?: boolean
  scope?: string
  objects?: { id: number; attribute: string }[]
  period?: string
  bindToData?: boolean
  includeLimits?: boolean
  disableLink?: boolean
  dailySummary?: boolean
  dailySummaryTime?: string
  notification?: AlarmNotificationBody
}

export type AlarmConfigCreate = AlarmConfigBody & { parentId: number; name: string }
export type AlarmConfigUpdate = AlarmConfigBody & { version: string }

type GetClient = Pick<HttpClient, 'get'>
const lang = (l: string) => `lang=${encodeURIComponent(l)}`

export function loadAlarmSettings(http: GetClient, locale: string): Promise<AlarmSettingsList> {
  return http.get<AlarmSettingsList>(`/web/alarms/settings?${lang(locale)}`)
}

/** POST /web/alarms/settings: the new configuration (list format). */
export function createAlarmConfiguration(http: Pick<HttpClient, 'post'>, body: AlarmConfigCreate, locale: string): Promise<AlarmConfigSettings> {
  return http.post<AlarmConfigSettings>(`/web/alarms/settings?${lang(locale)}`, body)
}

/** PUT /web/alarms/settings/{id}: the saved configuration (list format); 409 with the current one. */
export function updateAlarmConfiguration(http: Pick<HttpClient, 'put'>, id: number, body: AlarmConfigUpdate, locale: string): Promise<AlarmConfigSettings> {
  return http.put<AlarmConfigSettings>(`/web/alarms/settings/${id}?${lang(locale)}`, body)
}

export function deleteAlarmConfigurationPath(id: number, version: string): string {
  return `/web/alarms/settings/${id}?version=${encodeURIComponent(version)}`
}

export function deleteAlarmConfiguration(http: Pick<HttpClient, 'delete'>, id: number, version: string): Promise<void> {
  return http.delete<void>(deleteAlarmConfigurationPath(id, version))
}

/* ------------------------------------------------------------------ E7.2 alarm settings of a row */

/** Substitute types of a limit step (`GapFillingType`). */
export const SUBSTITUTES = ['NONE', 'STATIC', 'INTERPOLATION', 'DEFAULT_VALUE', 'MINIMUM', 'MAXIMUM', 'MEDIAN', 'AVERAGE', 'DELETE', 'REGRESSION'] as const

/** Operators of an alarm rule (`AlarmConstants.Operator`). */
export const OPERATORS = ['BIGGER', 'BIGGER_EQUALS', 'EQUALS', 'NOT_EQUALS', 'SMALLER', 'SMALLER_EQUALS'] as const

export const WEEKDAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const

/** One limit step (index 0 = step 1); unknown keys of `JsonLimitsConfig` are passed through. */
export interface LimitStep {
  name?: string | null
  min: number | null
  max: number | null
  substitute: string
  defaultMin: number | null
  defaultMax: number | null
  [key: string]: unknown
}

/** Silent / standby time: a weekday set with one time range, or a stored cron the web shows only. */
export type AlarmSchedule = { simple: true; days: string[]; from: string; to: string } | { simple: false; text: string }

export interface AlarmRuleLimitData {
  id: number
  attribute: string
  name: string | null
  readable: boolean
}

export interface AlarmRule {
  id: string | number | null
  name?: string | null
  kind: 'STATIC' | 'DYNAMIC' | 'NO_DATA' | string
  limit: number | null
  limitData: AlarmRuleLimitData | null
  operator: string
  tolerance: number | null
  silent: AlarmSchedule | null
  standby: AlarmSchedule | null
  /** the rule only applies to values inside these times (day/night limits); missing on older servers */
  valid?: AlarmSchedule | null
  /** NO_DATA: minutes without a new value */
  noData?: number | null
  /** minutes of a moving mean compared instead of the single value */
  average?: number | null
  /** false for stored rules JEAlarm cannot evaluate */
  complete?: boolean
}

/** Answer of GET / PUT /web/rows/{rowId}/alarm-settings. */
export interface RowAlarmSettings {
  row: { id: number; name: string; unit: string | null; rawId: number | null; rawName: string | null; lastValue?: string | null }
  canWrite: boolean
  version: string | null
  limits: { enabled: boolean; steps: LimitStep[] }
  alarms: { enabled: boolean; rules: AlarmRule[] }
  /** configurations whose scope includes the row */
  configurations: { id: number; name: string }[]
  /** PUT: limit changes only affect data processed from now on */
  reprocessHint?: boolean
}

export interface RowAlarmRuleBody {
  id: string | number | null
  name?: string | null
  kind: string
  limit: number | null
  limitData: { id: number; attribute: string } | null
  operator?: string
  tolerance: number
  silent: AlarmSchedule | null
  standby: AlarmSchedule | null
  valid?: AlarmSchedule | null
  noData?: number
  average?: number | null
}

export interface RowAlarmSettingsBody {
  version: string
  limits?: { enabled: boolean; steps: LimitStep[] }
  alarms?: { enabled: boolean; rules: RowAlarmRuleBody[] }
}

export function rowAlarmSettingsPath(rowId: number, locale: string): string {
  return `/web/rows/${rowId}/alarm-settings?${lang(locale)}`
}

export function loadRowAlarmSettings(http: GetClient, rowId: number, locale: string): Promise<RowAlarmSettings> {
  return http.get<RowAlarmSettings>(rowAlarmSettingsPath(rowId, locale))
}

export function saveRowAlarmSettings(http: Pick<HttpClient, 'put'>, rowId: number, body: RowAlarmSettingsBody, locale: string): Promise<RowAlarmSettings> {
  return http.put<RowAlarmSettings>(rowAlarmSettingsPath(rowId, locale), body)
}
