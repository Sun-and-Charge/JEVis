/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { HttpError, type HttpClient } from './http'

/*
 * Reading rounds (E8.2, docs/WEB-API.md "Editing, stage E8.2: reading rounds and route sheet"): rounds assign meters
 * in reading order to readers with a period; the "order" of the current period (due date, done meters, state) is
 * computed by the server from the readings. Writes carry the round's `version` (409 with the current round), 400 as
 * `{ "messages": [...] }`, deletes go to the recycle bin. The route sheet is a PDF.
 */

/** State of the current period, as the server computes it. */
export type RoundState = 'INACTIVE' | 'NOT_STARTED' | 'DONE' | 'OPEN' | 'LATE' | 'ESCALATED'

/** Periods a round can have (in this order in the editor). */
export const ROUND_PERIODS = ['P1W', 'P2W', 'P1M', 'P3M', 'P6M', 'P1Y'] as const
export type RoundPeriod = (typeof ROUND_PERIODS)[number]

export type PersonKind = 'USER' | 'GROUP'

/** A reader or supervisor; `name` null if the user may not read that user/group. */
export interface RoundPerson {
  id: number
  name: string | null
  kind: PersonKind | string
}

/** A meter of a round in round order; unreadable meters only with `id` and `readable: false`. */
export interface RoundMeter {
  id: number
  readable: boolean
  name?: string
  location?: string | null
  /** read for the current due date (automatic meters count as done) */
  done?: boolean
  automatic?: boolean
  last?: { timestamp: string; value: number | string | null; unit: string | null } | null
}

export interface ReadingRound {
  id: number
  name: string
  site: { id: number; name: string } | null
  directoryId: number | null
  enabled: boolean
  period: RoundPeriod | string
  /** yyyy-MM-dd */
  startDate: string
  leadDays: number
  reminderDays: number
  escalationDays: number
  remarks: string | null
  readers: RoundPerson[]
  supervisor: RoundPerson[]
  /** current due date yyyy-MM-dd, null before the start date */
  due: string | null
  nextDue: string | null
  state: RoundState | string
  done: number
  total: number
  meters: RoundMeter[]
  /** the user is a reader (directly or by group) */
  mine: boolean
  canWrite: boolean
  canDelete: boolean
  /** latest change of any attribute */
  version: string
}

export interface RoundList {
  /** server date yyyy-MM-dd */
  today: string
  rounds: ReadingRound[]
}

export interface RoundOptionMeter {
  id: number
  name: string
  location: string | null
  medium: string | null
  /** filled automatically (E8.1 problem AUTOMATIC): counts as done, not on the route sheet */
  automatic: boolean
}

export interface RoundOptionPerson {
  id: number
  name: string
  kind: PersonKind | string
  /** users: has an e-mail address (groups: always false / ignored) */
  email: boolean
}

export interface RoundOptions {
  canCreate: boolean
  directoryId: number | null
  meters: RoundOptionMeter[]
  people: RoundOptionPerson[]
}

/** Body of POST (plus `site`) and PUT (plus `version`). */
export interface RoundBody {
  name: string
  enabled: boolean
  meters: number[]
  readers: number[]
  supervisor: number[]
  startDate: string
  period: string
  leadDays: number
  reminderDays: number
  escalationDays: number
  remarks: string | null
}

/** Limits of the server's validation. */
export const ROUND_LIMITS = { name: 100, meters: 500, days: 60, remarks: 1000 } as const

const lang = (locale: string) => `lang=${encodeURIComponent(locale)}`

/** GET /web/reading-rounds; `site` limits it to one building. */
export function loadRounds(http: Pick<HttpClient, 'get'>, locale: string, site?: number | null): Promise<RoundList> {
  const q = site != null ? `&site=${site}` : ''
  return http.get<RoundList>(`/web/reading-rounds?${lang(locale)}${q}`)
}

/** GET /web/reading-rounds/options?site=…: meters and people for the editor, create right. */
export function loadRoundOptions(http: Pick<HttpClient, 'get'>, site: number, locale: string): Promise<RoundOptions> {
  return http.get<RoundOptions>(`/web/reading-rounds/options?site=${site}&${lang(locale)}`)
}

/** POST /web/reading-rounds: 201 with the round (list format). */
export function createRound(http: Pick<HttpClient, 'post'>, body: RoundBody & { site: number }, locale: string): Promise<ReadingRound> {
  return http.post<ReadingRound>(`/web/reading-rounds?${lang(locale)}`, body)
}

/** PUT /web/reading-rounds/{id}: 200 with the round, 409 with the current round if `version` is outdated. */
export function updateRound(http: Pick<HttpClient, 'put'>, id: number, body: RoundBody & { version: string }, locale: string): Promise<ReadingRound> {
  return http.put<ReadingRound>(`/web/reading-rounds/${id}?${lang(locale)}`, body)
}

/** DELETE /web/reading-rounds/{id}?version=… (recycle bin), 204. */
export function deleteRound(http: Pick<HttpClient, 'delete'>, id: number, version: string): Promise<void> {
  return http.delete(`/web/reading-rounds/${id}?version=${encodeURIComponent(version)}`)
}

/** Path of the route sheet PDF; `due` (yyyy-MM-dd) defaults to the round's current due date on the server. */
export function roundSheetPath(id: number, locale: string, due?: string | null): string {
  return `/web/reading-rounds/${id}/sheet.pdf?${lang(locale)}${due ? `&due=${encodeURIComponent(due)}` : ''}`
}

/** The route sheet with the Bearer token (a plain link cannot send it). */
export function loadRoundSheet(http: Pick<HttpClient, 'getBlob'>, id: number, locale: string, due?: string | null): Promise<Blob> {
  return http.getBlob(roundSheetPath(id, locale, due))
}

/** The current round of a 409 answer; null if it is no such answer or has no usable round. */
export function conflictRound(e: unknown): ReadingRound | null {
  if (!(e instanceof HttpError) || e.status !== 409) return null
  const body = e.json<unknown>()
  if (!body || typeof body !== 'object') return null
  /* the round itself, or wrapped as { round } */
  const o = body as Record<string, unknown>
  const round = (typeof o.id === 'number' ? o : o.round) as ReadingRound | undefined
  return round && typeof round === 'object' && typeof round.id === 'number' && typeof round.version === 'string' ? round : null
}
