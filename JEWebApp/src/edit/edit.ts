/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { ref, type Ref } from 'vue'
import { HttpError } from '../api/http'

/*
 * Shared helpers of the write actions (E1): error kinds, busy state against double submits, date inputs.
 */

export type WriteErrorKind = 'forbidden' | 'notFound' | 'conflict' | 'invalid' | 'session' | 'failed'

/** Kind of a failed write: 403, 404, 409, 400, 401, anything else (network, 5xx). */
export function writeErrorKind(e: unknown): WriteErrorKind {
  if (!(e instanceof HttpError)) return 'failed'
  switch (e.status) {
    case 400:
      return 'invalid'
    case 401:
      return 'session'
    case 403:
      return 'forbidden'
    case 404:
      return 'notFound'
    case 409:
      return 'conflict'
    default:
      return 'failed'
  }
}

/** Plain-text reason of a 400 / 403 answer (the server's message), '' if none or if it looks like HTML / JSON. */
export function serverReason(e: unknown): string {
  if (!(e instanceof HttpError)) return ''
  const body = (e.body ?? '').trim()
  if (!body || body.startsWith('<') || body.startsWith('{') || body.startsWith('[')) return ''
  return body.length > 300 ? body.slice(0, 300) + '…' : body
}

/**
 * Translated message of a failed write: `edit.error.<kind>` plus the server's reason for 400 / 403.
 * `conflict` can be overridden per feature (e.g. "note exists").
 */
export function writeErrorText(e: unknown, t: (key: string, values?: Record<string, unknown>) => string, conflictKey = 'edit.error.conflict'): string {
  const kind = writeErrorKind(e)
  const base = kind === 'conflict' ? t(conflictKey) : t('edit.error.' + kind)
  const reason = kind === 'invalid' || kind === 'forbidden' ? serverReason(e) : ''
  return reason ? `${base} (${reason})` : base
}

/** Busy flag of a write: run() ignores calls while one is running (no double submits). */
export function useBusy(): { busy: Ref<boolean>; run: <T>(action: () => Promise<T>) => Promise<T | undefined> } {
  const busy = ref(false)
  async function run<T>(action: () => Promise<T>): Promise<T | undefined> {
    if (busy.value) return undefined
    busy.value = true
    try {
      return await action()
    } finally {
      busy.value = false
    }
  }
  return { busy, run }
}

const pad = (n: number, len = 2) => String(n).padStart(len, '0')

/** ISO 8601 with the browser's offset, seconds kept: 2026-11-10T14:15:00+01:00. */
export function isoWithOffset(ms: number): string {
  const d = new Date(ms)
  const offset = -d.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const abs = Math.abs(offset)
  return (
    `${pad(d.getFullYear(), 4)}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}` +
    `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
  )
}

/** Value of an `<input type="datetime-local">` (to the minute) for an instant. */
export function toDateTimeInput(ms: number): string {
  const d = new Date(ms)
  return `${pad(d.getFullYear(), 4)}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Instant of a `datetime-local` value (local time; seconds ignored), null if invalid. */
export function fromDateTimeInput(value: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value ?? '')
  if (!m) return null
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), Number(m[4]), Number(m[5]), 0, 0)
  return Number.isNaN(d.getTime()) ? null : d.getTime()
}

/** Instant rounded down to the minute (notes are entered to the minute). */
export function toMinute(ms: number): number {
  return Math.floor(ms / 60000) * 60000
}

/**
 * Parses a number typed by the user: comma or point as decimal separator, thousands separators of the locale
 * ("1.234,5" in German, "1,234.5" in English) are removed. null if not a finite number.
 */
export function parseUserNumber(text: string, locale: string): number | null {
  let s = (text ?? '').trim().replace(/\s/g, '').replace(/−/g, '-')
  if (!s) return null
  const commaDecimal = locale.startsWith('de')
  if (s.includes(',') && s.includes('.')) {
    s = commaDecimal ? s.replace(/\./g, '').replace(',', '.') : s.replace(/,/g, '')
  } else if (s.includes(',')) {
    s = s.replace(',', '.')
  }
  if (!/^[+-]?(\d+\.?\d*|\.\d+)(e[+-]?\d+)?$/i.test(s)) return null
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}
