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
  /** E1: the user may add notes to this row */
  canAddNote?: boolean
  /** E1: id of the row new notes are stored below (raw parent of clean data), null if notes cannot be stored */
  notesTarget?: number | null
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
  /** E1: the user may change text and tags (write or execute right) */
  canEdit?: boolean
  /** E1: the user may delete the note (execute or delete right) */
  canDelete?: boolean
  /** E1: concurrency token sent back with PUT / DELETE */
  version?: string | null
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

/* ---- editing (E1) ---- */

/** Body of POST /web/rows/{rowId}/notes. */
export interface NewNote {
  /** ISO 8601 with offset (seconds are dropped by the server) */
  timestamp: string
  text: string
  /** standard tag ids 0–13; [] = no tag */
  tags: string[]
}

/** Body of PUT /web/notes/{noteId}. */
export interface NoteChange {
  text: string
  /** left out: tags unchanged (keeps custom tags of old notes) */
  tags?: string[]
  /** the list field `version` (required by the server) */
  version: string
}

type WriteClient = Pick<HttpClient, 'post' | 'put' | 'delete'>

/** Id of the data row new notes of a row are stored below: its `notesTarget`; null if notes cannot be stored there. */
export function noteTargetId(row: NoteRow | null | undefined): number | null {
  return typeof row?.notesTarget === 'number' ? row.notesTarget : null
}

/** `?timeZone=` of the note endpoints: timestamps without offset are read and answers printed in this zone. */
function zoneQuery(timeZone: string | null | undefined, sep = '?'): string {
  return timeZone ? `${sep}timeZone=${encodeURIComponent(timeZone)}` : ''
}

/** Creates a note on a data row (server picks / creates the "Data Notes" object); 409 if one exists at that time. */
export function createNote(http: Pick<WriteClient, 'post'>, rowId: number, note: NewNote, timeZone?: string): Promise<Note> {
  return http.post<Note>(`/web/rows/${rowId}/notes${zoneQuery(timeZone)}`, note)
}

/** Changes text and tags of a note; 409 with the current note if it was changed meanwhile. */
export function updateNote(http: Pick<WriteClient, 'put'>, noteId: string, change: NoteChange, timeZone?: string): Promise<Note> {
  return http.put<Note>(`/web/notes/${encodeURIComponent(noteId)}${zoneQuery(timeZone)}`, change)
}

/** Deletes a note (Value, Tag and User at its timestamp); 409 if it was changed meanwhile. */
export function deleteNote(http: Pick<WriteClient, 'delete'>, noteId: string, version: string, timeZone?: string): Promise<void> {
  return http.delete<void>(`/web/notes/${encodeURIComponent(noteId)}?version=${encodeURIComponent(version)}${zoneQuery(timeZone, '&')}`)
}
