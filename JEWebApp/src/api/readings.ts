/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { HttpError, type HttpClient } from './http'
import type { EntryMode } from './entry'

/*
 * Meter reading (E8.1, docs/WEB-API.md "Editing, stage E8.1: meter reading"): the meters the user may read per site
 * with due state, expected value and the last readings; a reading is written to the meter's data row as a manual
 * value (E1 rules, plausibility warnings → 409 unless `confirm`), a photo afterwards at the reading's timestamp.
 */

/** As `Due.state` of the app; `NONE`: the row has no period ("Kein Turnus"). */
export type DueState = 'NONE' | 'OK' | 'DUE' | 'OVERDUE'

/** Why a meter cannot be read in: no Online ID, target not readable, target is no raw Data / Base Data row. */
export type ReadingProblem = 'NO_TARGET' | 'NOT_READABLE' | 'NOT_MANUAL' | 'AUTOMATIC'

/** The data row of the meter's "Online ID" (as GET /web/rows/{id}/entry describes it). */
export interface ReadingRow {
  id: number
  name: string
  unit: string | null
  counter: boolean
  /** ISO period, e.g. P1M; null without */
  period: string | null
  mode: EntryMode | string
  timeZone: string | null
  limits: { min: number | null; max: number | null } | null
  canEnter: boolean
  canDelete?: boolean
  /** "Decimal Places" of the meter, else of the row */
  decimals: number | null
}

export interface ReadingSample {
  /** ISO 8601 with offset */
  timestamp: string
  value: number | string | null
  note: string | null
  /** entered manually (manual-entry note) */
  manual: boolean
  /** the meter has a "Reading Photo" at exactly this timestamp */
  photo: boolean
}

export interface ExpectedRange {
  value: number
  low: number
  high: number
}

export interface ReadingMeter {
  id: number
  name: string
  className: string
  classLabel: string | null
  /** ELECTRICITY, GAS, WATER, HEAT, … */
  medium: string | null
  path: string | null
  serialNumber: string | null
  meterPoint: string | null
  location: string | null
  /** the meter has "Reading Photo" and the user may write it */
  canPhoto: boolean
  /** meter exchange allowed (E5: write right on the row, counter) */
  canExchange: boolean
  /** null with `problem` */
  row: ReadingRow | null
  due: DueState
  /** next period after the last reading (ISO with offset), null without readings */
  suggestedTimestamp: string | null
  /** at `suggestedTimestamp` (or now), null if it cannot be estimated */
  expected: ExpectedRange | null
  /** last 11 samples, newest first */
  readings: ReadingSample[]
  problem: ReadingProblem | string | null
  /** only with `?round=`: read for the round's current due date (E8.2) */
  doneInRound?: boolean
}

export interface ReadingGroup {
  key: string
  name: string
  site: { id: number; name: string } | null
  meters: ReadingMeter[]
}

/** The round of a `?round=` answer (E8.2). */
export interface ReadingListRound {
  id: number
  name: string
  due: string | null
  nextDue: string | null
  state: string
}

export interface ReadingList {
  /** server date yyyy-MM-dd */
  today: string
  groups: ReadingGroup[]
  /** only with `?round=`: one group named after the round, meters in round order */
  round?: ReadingListRound | null
}

/** Body of POST /web/readings/{meterId}. */
export interface NewReading {
  /** as E1: ISO date-time without offset = wall time in the row's time zone, aligned by the server to the mode */
  timestamp: string
  value: number
  note: string | null
  confirm: boolean
}

/** 201 answer (the confirmed warnings); a 409 body has the same fields with the warnings to confirm. */
export interface SavedReading {
  timestamp: string
  value: number
  warnings: string[]
}

export interface SavedPhoto {
  timestamp: string
  file: string
}

/** Max. length of the note (server: trimmed, max. 500 characters, no control characters). */
export const NOTE_MAX = 500

/** Max. size of a photo (server: 15 MB). */
export const MAX_PHOTO_BYTES = 15 * 1024 * 1024

const lang = (locale: string) => `lang=${encodeURIComponent(locale)}`

/** GET /web/readings; `site` limits it to one building, `round` to the meters of a reading round (E8.2). */
export function loadReadings(http: Pick<HttpClient, 'get'>, locale: string, site?: number | null, round?: number | null): Promise<ReadingList> {
  const q = (site != null ? `&site=${site}` : '') + (round != null ? `&round=${round}` : '')
  return http.get<ReadingList>(`/web/readings?${lang(locale)}${q}`)
}

/** POST /web/readings/{meterId}: 201 with the saved reading, 409 with warnings (see {@link readingWarnings}). */
export function saveReading(http: Pick<HttpClient, 'post'>, meterId: number, body: NewReading, locale: string): Promise<SavedReading> {
  return http.post<SavedReading>(`/web/readings/${meterId}?${lang(locale)}`, body)
}

/** Path of the photo of a reading (GET: image inline, POST: upload). */
export function readingPhotoPath(meterId: number, timestamp: string): string {
  return `/web/readings/${meterId}/photo?timestamp=${encodeURIComponent(timestamp)}`
}

/** POST /web/readings/{meterId}/photo?timestamp=… (multipart `file`), after the reading was saved. */
export function uploadReadingPhoto(http: Pick<HttpClient, 'postForm'>, meterId: number, timestamp: string, file: File): Promise<SavedPhoto> {
  const form = new FormData()
  form.append('file', file, file.name)
  return http.postForm<SavedPhoto>(readingPhotoPath(meterId, timestamp), form)
}

/** The photo of a reading with the Bearer token. */
export function loadReadingPhoto(http: Pick<HttpClient, 'getBlob'>, meterId: number, timestamp: string): Promise<Blob> {
  return http.getBlob(readingPhotoPath(meterId, timestamp))
}

/** Warnings of a 409 answer (to confirm with "Trotzdem speichern"); null if it is no such answer. */
export function readingWarnings(e: unknown): string[] | null {
  if (!(e instanceof HttpError) || e.status !== 409) return null
  const list = e.json<{ warnings?: unknown }>()?.warnings
  return Array.isArray(list) ? list.filter((w): w is string => typeof w === 'string' && !!w.trim()) : []
}
