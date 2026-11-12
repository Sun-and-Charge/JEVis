/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { EntryMode } from '../api/entry'

/*
 * Manual values (E1): the timestamp input per entry mode (like the Control Center's DataTypeBox). Inputs show and
 * send wall time in the site's time zone (`EntryInfo.timeZone`); the server reads timestamps without offset in it.
 */

export const ENTRY_MODES: readonly EntryMode[] = ['SPECIFIC_DATETIME', 'DAY', 'MONTH', 'YEAR']

/** Known mode, unknown texts become SPECIFIC_DATETIME. */
export function entryMode(mode: string | null | undefined): EntryMode {
  return (ENTRY_MODES as readonly string[]).includes(mode ?? '') ? (mode as EntryMode) : 'SPECIFIC_DATETIME'
}

export type EntryInputType = 'datetime-local' | 'date' | 'month' | 'number'

/** The input element type for a mode (years as a number field). */
export function entryInputType(mode: string | null | undefined): EntryInputType {
  switch (entryMode(mode)) {
    case 'DAY':
      return 'date'
    case 'MONTH':
      return 'month'
    case 'YEAR':
      return 'number'
    default:
      return 'datetime-local'
  }
}

const pad = (n: number, len = 2) => String(n).padStart(len, '0')

interface WallTime {
  year: number
  month: number
  day: number
  hour: number
  minute: number
}

/** Wall time of an instant in a time zone (the browser's if none or unknown). */
export function wallTime(ms: number, timeZone?: string | null): WallTime {
  if (timeZone) {
    try {
      const parts = new Intl.DateTimeFormat('en-US', {
        timeZone,
        hourCycle: 'h23',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      }).formatToParts(new Date(ms))
      const get = (type: string) => Number(parts.find((p) => p.type === type)?.value)
      const w = { year: get('year'), month: get('month'), day: get('day'), hour: get('hour') % 24, minute: get('minute') }
      if (Object.values(w).every(Number.isFinite)) return w
    } catch {
      /* unknown zone: browser time */
    }
  }
  const d = new Date(ms)
  return { year: d.getFullYear(), month: d.getMonth() + 1, day: d.getDate(), hour: d.getHours(), minute: d.getMinutes() }
}

const toMs = (time: string | number | null | undefined): number | null => {
  if (time == null || time === '') return null
  const ms = typeof time === 'number' ? time : Date.parse(time)
  return Number.isNaN(ms) ? null : ms
}

/** Input value of an instant (ISO or epoch ms) for a mode, as wall time in `timeZone`; '' if invalid. */
export function toEntryInput(time: string | number | null | undefined, mode: string | null | undefined, timeZone?: string | null): string {
  const ms = toMs(time)
  if (ms == null) return ''
  const w = wallTime(ms, timeZone)
  const day = `${pad(w.year, 4)}-${pad(w.month)}-${pad(w.day)}`
  switch (entryMode(mode)) {
    case 'YEAR':
      return String(w.year)
    case 'MONTH':
      return `${pad(w.year, 4)}-${pad(w.month)}`
    case 'DAY':
      return day
    default:
      return `${day}T${pad(w.hour)}:${pad(w.minute)}`
  }
}

/**
 * Wall time of an input value, aligned like the server (year → 1 Jan, month → day 1, day → 00:00), as
 * `yyyy-MM-ddTHH:mm:00` without offset; null if invalid.
 */
export function entryTimestamp(value: string, mode: string | null | undefined): string | null {
  const v = (value ?? '').trim()
  let y: number, mo = 1, d = 1, h = 0, mi = 0
  let m: RegExpExecArray | null
  switch (entryMode(mode)) {
    case 'YEAR':
      if (!/^\d{4}$/.test(v)) return null
      y = Number(v)
      break
    case 'MONTH':
      m = /^(\d{4})-(\d{2})$/.exec(v)
      if (!m) return null
      ;[y, mo] = [Number(m[1]), Number(m[2])]
      break
    case 'DAY':
      m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v)
      if (!m) return null
      ;[y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])]
      break
    default:
      m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(v)
      if (!m) return null
      ;[y, mo, d, h, mi] = [Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4]), Number(m[5])]
  }
  // calendar check without time zone effects
  const check = new Date(Date.UTC(y, mo - 1, d, h, mi))
  if (check.getUTCFullYear() !== y || check.getUTCMonth() !== mo - 1 || check.getUTCDate() !== d || h > 23 || mi > 59) return null
  return `${pad(y, 4)}-${pad(mo)}-${pad(d)}T${pad(h)}:${pad(mi)}:00`
}

/** Display of a timestamp as precise as the mode, in `timeZone`: "2026", "November 2026", "12.11.2026", "12.11.2026, 14:15". */
export function formatEntryTime(time: string | number | null | undefined, mode: string | null | undefined, locale: string, timeZone?: string | null): string {
  if (time == null || time === '') return ''
  const ms = toMs(time)
  if (ms == null) return String(time)
  const zone = timeZone ? { timeZone } : {}
  const format = (options: Intl.DateTimeFormatOptions) => {
    try {
      return new Intl.DateTimeFormat(locale, { ...options, ...zone }).format(ms)
    } catch {
      return new Intl.DateTimeFormat(locale, options).format(ms)
    }
  }
  switch (entryMode(mode)) {
    case 'YEAR':
      return String(wallTime(ms, timeZone).year)
    case 'MONTH':
      return format({ month: 'long', year: 'numeric' })
    case 'DAY':
      return format({ day: '2-digit', month: '2-digit', year: 'numeric' })
    default:
      return format({ day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  }
}
