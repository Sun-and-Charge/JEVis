/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/*
 * The entries store local date-times of the server without zone (2026-11-02T09:00). They are shown as they are:
 * parsed as UTC wall-clock time and formatted in UTC, so the browser's zone and DST never shift them.
 */

const LOCAL = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/

/** Wall-clock time as UTC milliseconds (for sorting and formatting), null if not a date. */
export function parseLocal(text: string | null | undefined): number | null {
  const m = text ? LOCAL.exec(text) : null
  if (!m) return null
  const ms = Date.UTC(+m[1], +m[2] - 1, +m[3], +(m[4] ?? 0), +(m[5] ?? 0), +(m[6] ?? 0))
  return Number.isNaN(ms) ? null : ms
}

function hasTime(text: string): boolean {
  return /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/.test(text)
}

const formats = new Map<string, Intl.DateTimeFormat>()

function format(locale: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const key = locale + JSON.stringify(options)
  let f = formats.get(key)
  if (!f) {
    f = new Intl.DateTimeFormat(locale, { ...options, timeZone: 'UTC' })
    formats.set(key, f)
  }
  return f
}

/** Only the date: 02.11.2026 */
export function formatLocalDate(text: string | null | undefined, locale: string): string {
  if (!text) return ''
  const ms = parseLocal(text)
  return ms == null ? text : format(locale, { dateStyle: 'medium' }).format(ms)
}

/** 02.11.2026, 09:00 (only the date for date values); '' if missing, the text itself if not parseable. */
export function formatLocal(text: string | null | undefined, locale: string): string {
  if (!text) return ''
  const ms = parseLocal(text)
  if (ms == null) return text
  return hasTime(text) ? format(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(ms) : formatLocalDate(text, locale)
}

/** Start–end; the date only once if both are on the same day. */
export function formatPeriod(start: string | null | undefined, end: string | null | undefined, locale: string): string {
  const from = formatLocal(start, locale)
  if (!end) return from
  if (!from) return formatLocal(end, locale)
  const e = parseLocal(end)
  if (start && e != null && start.slice(0, 10) === end.slice(0, 10) && hasTime(start) && hasTime(end)) {
    return `${from}–${format(locale, { timeStyle: 'short' }).format(e)}`
  }
  return `${from} – ${formatLocal(end, locale)}`
}

const units = new Map<string, Intl.NumberFormat>()

function unit(locale: string, name: 'day' | 'hour' | 'minute'): Intl.NumberFormat {
  const key = locale + name
  let f = units.get(key)
  if (!f) {
    f = new Intl.NumberFormat(locale, { style: 'unit', unit: name, unitDisplay: 'short' })
    units.set(key, f)
  }
  return f
}

/** 90 → "1 Std., 30 Min." / "1 hr, 30 min"; '' if missing or negative. */
export function formatDuration(minutes: number | null | undefined, locale: string): string {
  if (minutes == null || !Number.isFinite(minutes) || minutes < 0) return ''
  const total = Math.round(minutes)
  if (total === 0) return unit(locale, 'minute').format(0)
  const days = Math.floor(total / 1440)
  const hours = Math.floor((total % 1440) / 60)
  const mins = total % 60
  const parts: string[] = []
  if (days) parts.push(unit(locale, 'day').format(days))
  if (hours) parts.push(unit(locale, 'hour').format(hours))
  if (mins) parts.push(unit(locale, 'minute').format(mins))
  return parts.join(' ')
}
