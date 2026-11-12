/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'

/*
 * Manual values (E1) like the Control Center's EnterDataDialog: one value of a raw "Data" or "Base Data" row.
 */

/** How the timestamp is entered: free date-time, or aligned to day / month / year by the server. */
export type EntryMode = 'SPECIFIC_DATETIME' | 'DAY' | 'MONTH' | 'YEAR'

/** Answer of GET /web/rows/{id}/entry. */
export interface EntryInfo {
  id: number
  name: string
  unit: string | null
  /** write right on the row */
  canEnter: boolean
  /** delete right on the row (manual values can be deleted again) */
  canDelete?: boolean
  /** time zone of the site: timestamps without offset are read in it */
  timeZone?: string | null
  counter: boolean
  /** ISO period of the row, e.g. PT15M, P1D, P1M */
  period: string | null
  mode: EntryMode | string
  /** next period after the last sample, ISO 8601 with offset; null without samples */
  suggestedTimestamp: string | null
  last: { timestamp: string; value: number | string | null } | null
  limits: { min: number | null; max: number | null } | null
}

/** Body of POST /web/rows/{id}/values. */
export interface NewValue {
  /** ISO date-time without offset: wall time in the site's time zone (`EntryInfo.timeZone`) */
  timestamp: string
  value: number
  /** true: save despite plausibility warnings */
  confirm: boolean
}

/** Answer of POST /web/rows/{id}/values (201); 409 bodies have the same `warnings`. */
export interface SavedValue {
  timestamp: string
  value: number
  warnings: string[]
}

export function loadEntryInfo(http: Pick<HttpClient, 'get'>, rowId: number): Promise<EntryInfo> {
  return http.get<EntryInfo>(`/web/rows/${rowId}/entry`)
}

/** Saves a value; `lang` is the language of the plausibility warnings (409 `{ timestamp, value, warnings }`). */
export function saveValue(http: Pick<HttpClient, 'post'>, rowId: number, value: NewValue, lang?: string): Promise<SavedValue> {
  const q = lang ? `?lang=${encodeURIComponent(lang)}` : ''
  return http.post<SavedValue>(`/web/rows/${rowId}/values${q}`, value)
}

/** Deletes a manually entered value (only samples with the manual-entry note, else 403). */
export function deleteValue(http: Pick<HttpClient, 'delete'>, rowId: number, timestamp: string): Promise<void> {
  return http.delete<void>(`/web/rows/${rowId}/values?timestamp=${encodeURIComponent(timestamp)}`)
}
