/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'

/** A tag of the answer (standard tags 0–10 translated, unknown texts as their own id). */
export interface NoteTagInfo {
  id: string
  name: string
}

/** The data row of a note (parent of the "Data Notes" object); name and path only if readable. */
export interface NoteRow {
  id: number
  name?: string | null
  className?: string | null
  path?: string | null
  readable: boolean
}

/** One note of GET /web/notes. */
export interface Note {
  /** `<notesObject>-<epoch ms>` */
  id: string
  /** ISO 8601 with offset */
  timestamp: string
  text: string
  /** tag ids */
  tags: string[]
  user: string | null
  notesObject: number
  row: NoteRow | null
  site: { id: number; name: string; names?: Record<string, string> } | null
}

/** Answer of GET /web/notes. */
export interface NoteList {
  from: string
  to: string
  tags: NoteTagInfo[]
  /** newest first */
  notes: Note[]
  truncated: boolean
}

export interface NoteQuery {
  /** yyyy-MM-dd, inclusive */
  from: string
  to: string
  timeZone: string
  lang: string
}

/** Answer of GET /web/rows/{id}/samples: [epoch ms, value], oldest first. */
export interface RowSamples {
  unit: string | null
  samples: [number, number | string | null][]
  truncated: boolean
}

type GetClient = Pick<HttpClient, 'get'>

export function notesPath(query: NoteQuery): string {
  const p = new URLSearchParams({ from: query.from, to: query.to, timeZone: query.timeZone, lang: query.lang })
  return `/web/notes?${p.toString()}`
}

export function loadNotes(http: GetClient, query: NoteQuery): Promise<NoteList> {
  return http.get<NoteList>(notesPath(query))
}

export function rowSamplesPath(id: number, from: number, to: number): string {
  const p = new URLSearchParams({ from: new Date(from).toISOString(), to: new Date(to).toISOString() })
  return `/web/rows/${id}/samples?${p.toString()}`
}

/** Raw samples of a data row between two instants (epoch ms; at most 31 days). */
export function loadRowSamples(http: GetClient, id: number, from: number, to: number): Promise<RowSamples> {
  return http.get<RowSamples>(rowSamplesPath(id, from, to))
}
