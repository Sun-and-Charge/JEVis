/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { formatNumber, type Decimals } from '../analysis/format'
import type { RegisterRow } from '../api/registers'
import type { ExpectedRange, ReadingMeter, ReadingSample, SavedReading } from '../api/readings'
import { entryMode, entryTimestamp, formatEntryTime, toEntryInput } from '../edit/entry'
import { matchesTerms, searchTerms } from '../modules/search'
import { estimate, readingPoints, wallTimeToMs } from './expected'

/*
 * List logic of the reading page (E8.1): filter "Fällig" / "Alle", search, due counts, the next due meter
 * ("Speichern & weiter"), time input per mode and the display of values.
 */

export type ReadingFilter = 'due' | 'all'

/** A reading was saved in the entry panel. */
export interface ReadingSavedEvent {
  meter: ReadingMeter
  result: SavedReading
  /** open the next due meter ("Speichern & weiter") */
  next: boolean
  /** a photo was taken but could not be uploaded (the user chose to go on without) */
  photoSkipped: boolean
}

/** Due or overdue. */
export function isDue(meter: Pick<ReadingMeter, 'due'>): boolean {
  return meter.due === 'DUE' || meter.due === 'OVERDUE'
}

/** A reading can be entered: the meter has a manual data row and the user may write it. */
export function canRead(meter: Pick<ReadingMeter, 'row'> & Partial<Pick<ReadingMeter, 'problem'>>): boolean {
  /* automatically recorded rows (data source, calculation, values below a day) are not read by hand */
  return !!meter.row && meter.row.canEnter && meter.problem !== 'AUTOMATIC'
}

export interface DueCounts {
  /** due or overdue */
  due: number
  overdue: number
}

export function dueCounts(meters: readonly Pick<ReadingMeter, 'due'>[]): DueCounts {
  let due = 0
  let overdue = 0
  for (const m of meters) {
    if (isDue(m)) due++
    if (m.due === 'OVERDUE') overdue++
  }
  return { due, overdue }
}

/** Default filter of a site: "Fällig" if any meter is due, else "Alle". */
export function defaultFilter(meters: readonly Pick<ReadingMeter, 'due'>[]): ReadingFilter {
  return meters.some(isDue) ? 'due' : 'all'
}

/** Meters of the filter whose name, serial number, meter point or location contain all search words. */
export function filterMeters<M extends Pick<ReadingMeter, 'due' | 'name' | 'serialNumber' | 'meterPoint' | 'location'>>(
  meters: readonly M[],
  filter: ReadingFilter,
  text: string,
  locale: string,
): M[] {
  const terms = searchTerms(text, locale)
  return meters.filter((m) => (filter === 'all' || isDue(m)) && matchesTerms(terms, [m.name, m.serialNumber, m.meterPoint, m.location], locale))
}

/** The next due meter that can be read after `currentId` in list order (wrapping around), null if none. */
export function nextDueMeter<M extends Pick<ReadingMeter, 'id' | 'due' | 'row'>>(list: readonly M[], currentId: number): M | null {
  const start = list.findIndex((m) => m.id === currentId)
  for (let i = 1; i <= list.length; i++) {
    const m = list[(start + i + list.length) % list.length]
    if (m.id !== currentId && isDue(m) && canRead(m)) return m
  }
  return null
}

/* ------------------------------------------------------------------ time input */

/** The input of a mode: date only for DAY / MONTH / YEAR (the server aligns), date-time otherwise. */
export function readingInputMode(mode: string | null | undefined): 'DAY' | 'SPECIFIC_DATETIME' {
  return entryMode(mode) === 'SPECIFIC_DATETIME' ? 'SPECIFIC_DATETIME' : 'DAY'
}

/** Prefill of the time: `suggestedTimestamp` if it is not in the future, else now (wall time of the site). */
export function defaultReadingInput(meter: Pick<ReadingMeter, 'suggestedTimestamp' | 'row'>, now: number): string {
  const suggested = meter.suggestedTimestamp ? Date.parse(meter.suggestedTimestamp) : NaN
  const time = !Number.isNaN(suggested) && suggested <= now ? suggested : now
  return toEntryInput(time, readingInputMode(meter.row?.mode), meter.row?.timeZone)
}

/**
 * Timestamp to send for an input (wall time without offset, as E1), aligned like the server to the start of the
 * month / year for MONTH / YEAR rows; null if invalid.
 */
export function readingTimestamp(input: string, mode: string | null | undefined): string | null {
  const ts = entryTimestamp(input, readingInputMode(mode))
  if (!ts) return null
  switch (entryMode(mode)) {
    case 'MONTH':
      return ts.slice(0, 8) + '01T00:00:00'
    case 'YEAR':
      return ts.slice(0, 5) + '01-01T00:00:00'
    default:
      return ts
  }
}

/** Expected value at an input time (null if the time is invalid or nothing can be estimated). */
export function expectedAt(meter: Pick<ReadingMeter, 'row' | 'readings'>, input: string): ExpectedRange | null {
  const row = meter.row
  if (!row) return null
  const ts = wallTimeToMs(readingTimestamp(input, row.mode), row.timeZone)
  if (ts == null) return null
  return estimate(row, readingPoints(meter.readings), ts)
}

/* ------------------------------------------------------------------ display */

/** Decimals of a meter: "Decimal Places" if set, else up to 3. */
export function meterDecimals(meter: Pick<ReadingMeter, 'row'>): Decimals {
  const d = meter.row?.decimals
  return typeof d === 'number' && d >= 0 && d <= 10 ? { min: d, max: d } : { min: 0, max: 3 }
}

export function formatReading(value: number | string | null | undefined, meter: Pick<ReadingMeter, 'row'>, locale: string, withUnit = true): string {
  if (value == null || value === '') return ''
  if (typeof value === 'string') return value
  const unit = withUnit && meter.row?.unit ? ' ' + meter.row.unit : ''
  return formatNumber(value, locale, meterDecimals(meter)) + unit
}

/** "12.100 … 12.600 kWh" */
export function formatRange(range: ExpectedRange, meter: Pick<ReadingMeter, 'row'>, locale: string): string {
  return `${formatReading(range.low, meter, locale, false)} … ${formatReading(range.high, meter, locale)}`
}

/** Date of a reading as precise as the row's mode, in the site's zone. */
export function formatReadingTime(timestamp: string | null | undefined, meter: Pick<ReadingMeter, 'row'>, locale: string): string {
  const mode = meter.row?.mode
  /* MONTH / YEAR readings show the day they were aligned to */
  return formatEntryTime(timestamp, readingInputMode(mode), locale, meter.row?.timeZone)
}

/** The note the user typed: after "<manual text> <time> – " of a manual entry, else the whole note. */
export function userNote(sample: Pick<ReadingSample, 'note' | 'manual'>): string {
  const note = (sample.note ?? '').trim()
  if (!sample.manual) return note
  const i = note.indexOf(' – ')
  return i >= 0 ? note.slice(i + 3).trim() : ''
}

/** The note is one line: control characters are not accepted (as the server). */
export function noteProblem(note: string, max: number): 'tooLong' | 'invalid' | null {
  if (note.trim().length > max) return 'tooLong'
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f]/.test(note)) return 'invalid'
  return null
}

/** The meter as the E5 exchange dialog expects it (name, data row, unit). */
export function exchangeRow(meter: ReadingMeter): RegisterRow {
  return {
    id: meter.id,
    name: meter.name,
    className: meter.className,
    classLabel: meter.classLabel,
    path: meter.path,
    medium: meter.medium,
    values: {},
    target: meter.row ? { id: meter.row.id, attribute: 'Value', name: meter.row.name, readable: true } : null,
    last: { value: typeof meter.readings[0]?.value === 'number' ? meter.readings[0].value : null, unit: meter.row?.unit ?? null, timestamp: meter.readings[0]?.timestamp ?? null },
  }
}
