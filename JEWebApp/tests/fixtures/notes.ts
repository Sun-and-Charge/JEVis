/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { Note, NoteList } from '../../src/api/notes'

/** Mocked answers of /web/notes (neutral names, no real data). */
export function note(id: string, overrides: Partial<Note> = {}): Note {
  return {
    id,
    timestamp: '2026-11-03T09:15:00.000+01:00',
    text: 'Note ' + id,
    tags: ['4'],
    user: 'User A',
    notesObject: 9000,
    row: { id: 8000, name: 'Row A', className: 'Data', path: 'Data / Power', readable: true },
    site: { id: 7000, name: 'Site A' },
    ...overrides,
  }
}

export const STANDARD_TAGS = [
  { id: '0', name: 'Kein' },
  { id: '1', name: 'Audit' },
  { id: '2', name: 'Aufgabe' },
  { id: '4', name: 'Ereignis' },
  { id: '6', name: 'Fehler' },
]

export function noteList(notes: Note[], overrides: Partial<NoteList> = {}): NoteList {
  return {
    from: '2026-08-11T00:00:00.000+02:00',
    to: '2026-11-11T23:59:59.999+01:00',
    tags: STANDARD_TAGS,
    notes,
    truncated: false,
    ...overrides,
  }
}
