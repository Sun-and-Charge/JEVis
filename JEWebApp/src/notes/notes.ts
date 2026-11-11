/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { CSV_BOM, csvField, csvSeparators, fileName } from '../analysis/table'
import { localTimestamp } from '../analysis/format'
import type { Note, NoteTagInfo } from '../api/notes'
import type { ColumnDef } from '../iso/columns'
import { matchesSite, matchesTerms, searchTerms, siteOptions, type SiteFilter, type SiteOption } from '../modules/search'

/*
 * Notes (W4.4, view only): filter, search and tags like the Control Center's Notes Plugin, sort columns and CSV.
 */

/** Tag id of "no tag"; notes without tags count as this tag. */
export const TAG_NONE = '0'

/** Translates a tag id (the names of the answer first, then the app's own names of the standard tags, else the id). */
export type TagName = (id: string) => string

/**
 * Tag names of the answer as a lookup. `fallback` names ids missing from the answer (e.g. the tags of analysis
 * notes, which carry only ids).
 */
export function tagNamer(tags: readonly NoteTagInfo[] | null | undefined, fallback: TagName = (id) => id): TagName {
  const names = new Map<string, string>()
  for (const tag of tags ?? []) if (tag && typeof tag.id === 'string' && tag.name) names.set(tag.id, tag.name)
  return (id) => names.get(id) ?? fallback(id)
}

/** Fallback names of the standard tags from the app's translations (notes.tags.<id>), else the id itself. */
export function i18nTagName(t: (key: string) => string, te: (key: string) => boolean): TagName {
  return (id) => (/^\d+$/.test(id) && te('notes.tags.' + id) ? t('notes.tags.' + id) : id)
}

/** Tag ids of a note for filtering: without tags it is "none". */
export function noteTagIds(note: Pick<Note, 'tags'>): string[] {
  const tags = Array.isArray(note.tags) ? note.tags.filter((t) => typeof t === 'string' && t !== '') : []
  return tags.length ? tags : [TAG_NONE]
}

/** Tags shown as badges: "none" is left out. */
export function visibleTags(note: Pick<Note, 'tags'>): string[] {
  return noteTagIds(note).filter((t) => t !== TAG_NONE)
}

export interface TagOption extends NoteTagInfo {
  count: number
}

/**
 * Tag filter chips: the tags of the answer (in its order) that occur in the notes, with their number of notes;
 * selected tags stay visible even without notes. Tags used by notes but missing from the answer are appended.
 */
export function tagOptions(tags: readonly NoteTagInfo[], notes: readonly Note[], selected: readonly string[] = [], name: TagName = (id) => id): TagOption[] {
  const counts = new Map<string, number>()
  for (const note of notes) for (const id of new Set(noteTagIds(note))) counts.set(id, (counts.get(id) ?? 0) + 1)
  const result: TagOption[] = []
  const seen = new Set<string>()
  for (const tag of tags) {
    if (!tag || seen.has(tag.id)) continue
    seen.add(tag.id)
    const count = counts.get(tag.id) ?? 0
    if (count > 0 || selected.includes(tag.id)) result.push({ id: tag.id, name: tag.name || name(tag.id), count })
  }
  for (const [id, count] of counts) if (!seen.has(id)) result.push({ id, name: name(id), count })
  return result
}

/** Turns a tag of the filter on or off. */
export function toggleTag(selected: readonly string[], id: string): string[] {
  return selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id]
}

export interface NoteFilter {
  text: string
  /** search in: note text, user, data row (name and path), tag names (the Control Center's toggles) */
  inText: boolean
  inUser: boolean
  inRow: boolean
  inTag: boolean
  /** selected tag ids; none selected: all notes */
  tags: string[]
  site: SiteFilter
}

export function defaultNoteFilter(): NoteFilter {
  return { text: '', inText: true, inUser: true, inRow: true, inTag: true, tags: [], site: 'all' }
}

/** Name of the data row: the row name, or `noAccess` if it may not be read. */
export function rowName(note: Pick<Note, 'row'>, noAccess: string): string {
  const row = note.row
  if (!row) return ''
  if (!row.readable) return noAccess
  return row.name || `#${row.id}`
}

/** Path and name of the data row: "Daten / Strom / Strom - Gesamt". */
export function rowFullName(note: Pick<Note, 'row'>, noAccess: string): string {
  const row = note.row
  if (!row) return ''
  if (!row.readable) return noAccess
  return [row.path, row.name || `#${row.id}`].filter(Boolean).join(' / ')
}

/**
 * Notes matching the filter: one of the selected tags (OR, like the Control Center), the site, and every search
 * word in one of the fields switched on. With search words but no field switched on nothing matches (as in the
 * Control Center).
 */
export function filterNotes(notes: readonly Note[], filter: NoteFilter, tagName: TagName, locale: string): Note[] {
  const terms = searchTerms(filter.text, locale)
  const tags = new Set(filter.tags)
  const anyField = filter.inText || filter.inUser || filter.inRow || filter.inTag
  return notes.filter((note) => {
    if (tags.size && !noteTagIds(note).some((id) => tags.has(id))) return false
    if (!matchesSite(note.site ? { id: note.site.id, name: note.site.name, names: note.site.names ?? {} } : null, filter.site)) return false
    if (!terms.length) return true
    if (!anyField) return false
    const texts: (string | null | undefined)[] = []
    if (filter.inText) texts.push(note.text)
    if (filter.inUser) texts.push(note.user)
    if (filter.inRow && note.row?.readable) texts.push(note.row.path, note.row.name)
    if (filter.inTag) texts.push(...visibleTags(note).map(tagName))
    return matchesTerms(terms, texts, locale)
  })
}

/** Sites of the notes for the site filter. */
export function noteSites(notes: readonly Note[], locale: string): SiteOption[] {
  return siteOptions(
    notes.map((n) => ({ site: n.site ? { id: n.site.id, name: n.site.name, names: n.site.names ?? {} } : null })),
    locale,
  )
}

/** Epoch milliseconds of a note, null if the timestamp cannot be read. */
export function noteTime(note: Pick<Note, 'timestamp'>): number | null {
  const ms = Date.parse(note.timestamp)
  return Number.isNaN(ms) ? null : ms
}

export interface NoteColumnEnv {
  noAccess: string
  tagName: TagName
  formatTime: (ms: number) => string
}

/** Columns of the notes table (labels are i18n keys); date sorts by time, tags by their names. */
export function noteColumns(env: NoteColumnEnv): ColumnDef<Note>[] {
  return [
    {
      id: 'date',
      label: 'notes.col.date',
      nowrap: true,
      text: (n) => {
        const ms = noteTime(n)
        return ms == null ? n.timestamp : env.formatTime(ms)
      },
      sort: (n) => noteTime(n),
    },
    { id: 'site', label: 'notes.col.site', text: (n) => n.site?.name ?? '' },
    { id: 'row', label: 'notes.col.row', text: (n) => rowFullName(n, env.noAccess) },
    { id: 'text', label: 'notes.col.text', text: (n) => n.text ?? '' },
    { id: 'tags', label: 'notes.col.tags', text: (n) => visibleTags(n).map(env.tagName).join(', ') },
    { id: 'user', label: 'notes.col.user', text: (n) => n.user ?? '' },
  ]
}

export type NoteSort = { id: string; dir: 'asc' | 'desc' }

/** The table starts newest first (like the answer). */
export const DEFAULT_NOTE_SORT: NoteSort = { id: 'date', dir: 'desc' }

export interface NoteCsvHeaders {
  date: string
  site: string
  path: string
  row: string
  text: string
  tags: string
  user: string
}

/**
 * CSV of the shown notes: local time (2026-11-03 09:15:00), site, path, data row, note, tags (names, ", "), user.
 * UTF-8 with BOM and CRLF, separators of the locale (Excel).
 */
export function notesCsv(notes: readonly Note[], headers: NoteCsvHeaders, env: Pick<NoteColumnEnv, 'noAccess' | 'tagName'>, locale: string): string {
  const { field } = csvSeparators(locale)
  const head = [headers.date, headers.site, headers.path, headers.row, headers.text, headers.tags, headers.user]
  const lines = [head.map((h) => csvField(h, field)).join(field)]
  for (const n of notes) {
    const ms = noteTime(n)
    const readable = !!n.row?.readable
    lines.push(
      [
        ms == null ? n.timestamp : localTimestamp(ms),
        n.site?.name ?? '',
        readable ? (n.row?.path ?? '') : '',
        rowName(n, env.noAccess),
        n.text ?? '',
        visibleTags(n).map(env.tagName).join(', '),
        n.user ?? '',
      ]
        .map((v) => (v === '' ? '' : csvField(v, field)))
        .join(field),
    )
  }
  return CSV_BOM + lines.join('\r\n') + '\r\n'
}

/** "Notizen 2026-08-11 - 2026-11-11.csv" */
export function notesCsvName(title: string, from: string, to: string): string {
  return fileName(`${title} ${from} - ${to}`) + '.csv'
}

const pad = (n: number) => String(n).padStart(2, '0')

/** yyyy-MM-dd of a date in the browser's time zone. */
export function isoDay(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Same day `months` earlier; the day is clamped to the month's length (31 May − 3 months = 28/29 Feb). */
export function minusMonths(d: Date, months: number): Date {
  const target = new Date(d.getFullYear(), d.getMonth() - months, 1)
  const last = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  return new Date(target.getFullYear(), target.getMonth(), Math.min(d.getDate(), last))
}

/** Default time frame: the last 3 months up to today (like the server's default). */
export function defaultNoteRange(now: Date = new Date()): { from: string; to: string } {
  return { from: isoDay(minusMonths(now, 3)), to: isoDay(now) }
}

export type RangeCheck = 'ok' | 'missing' | 'order' | 'tooLong'

const DAY = /^\d{4}-\d{2}-\d{2}$/

/**
 * Checks a time frame before it is sent: both days set, `to` not before `from`, at most `maxYears` years
 * (server: 400).
 */
export function checkRange(from: string, to: string, maxYears: number): RangeCheck {
  if (!DAY.test(from) || !DAY.test(to)) return 'missing'
  if (to < from) return 'order'
  const [y, m, d] = from.split('-').map(Number)
  const limit = `${String(y + maxYears).padStart(4, '0')}-${pad(m)}-${pad(d)}`
  return to > limit ? 'tooLong' : 'ok'
}

/** Time frame of the notes: at most 3 years. */
export function checkNoteRange(from: string, to: string): RangeCheck {
  return checkRange(from, to, 3)
}

/** Text for the detail panel / tooltips: tags as names, joined. */
export function tagText(note: Pick<Note, 'tags'>, tagName: TagName): string {
  return visibleTags(note).map(tagName).join(', ')
}

