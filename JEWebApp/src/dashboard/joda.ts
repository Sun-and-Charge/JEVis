/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/**
 * Formats a time with a Joda-Time pattern (`DateTimeFormat.forPattern`, used by the Time Frame widget) in the
 * browser's time zone. Supported: G y Y x M d D E e a H k h K m s S w Z z and quoted text ('...', '' for a quote).
 * Unknown letters are printed as they are.
 */
export function formatJoda(ms: number, pattern: string, locale: string): string {
  const d = new Date(ms)
  if (Number.isNaN(d.getTime())) return ''
  let out = ''
  let i = 0
  while (i < pattern.length) {
    const c = pattern[i]
    if (c === "'") {
      // quoted text; '' is a single quote
      if (pattern[i + 1] === "'") {
        out += "'"
        i += 2
        continue
      }
      let j = i + 1
      let text = ''
      while (j < pattern.length) {
        if (pattern[j] === "'") {
          if (pattern[j + 1] === "'") {
            text += "'"
            j += 2
            continue
          }
          break
        }
        text += pattern[j++]
      }
      out += text
      i = j + 1
      continue
    }
    if (!/[A-Za-z]/.test(c)) {
      out += c
      i++
      continue
    }
    let n = 1
    while (pattern[i + n] === c) n++
    out += field(d, c, n, locale)
    i += n
  }
  return out
}

const pad = (v: number, n: number) => String(Math.abs(v)).padStart(n, '0')

const cache = new Map<string, Intl.DateTimeFormat>()
function part(d: Date, locale: string, options: Intl.DateTimeFormatOptions, type: Intl.DateTimeFormatPartTypes): string {
  const key = locale + JSON.stringify(options)
  let f = cache.get(key)
  if (!f) {
    f = new Intl.DateTimeFormat(locale, options)
    cache.set(key, f)
  }
  return f.formatToParts(d).find((p) => p.type === type)?.value ?? ''
}

/** ISO 8601 week of the year and its year. */
export function isoWeek(d: Date): { week: number; year: number } {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()))
  const day = t.getUTCDay() || 7
  t.setUTCDate(t.getUTCDate() + 4 - day)
  const yearStart = Date.UTC(t.getUTCFullYear(), 0, 1)
  return { week: Math.ceil(((t.getTime() - yearStart) / 86_400_000 + 1) / 7), year: t.getUTCFullYear() }
}

function dayOfYear(d: Date): number {
  return Math.round((Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) - Date.UTC(d.getFullYear(), 0, 1)) / 86_400_000) + 1
}

function offset(d: Date, colon: boolean): string {
  const o = -d.getTimezoneOffset()
  const sign = o >= 0 ? '+' : '-'
  return `${sign}${pad(Math.floor(Math.abs(o) / 60), 2)}${colon ? ':' : ''}${pad(Math.abs(o) % 60, 2)}`
}

function year(y: number, n: number): string {
  return n === 2 ? pad(y % 100, 2) : pad(y, n)
}

function field(d: Date, c: string, n: number, locale: string): string {
  switch (c) {
    case 'G':
      return d.getFullYear() > 0 ? 'AD' : 'BC'
    case 'y':
    case 'Y':
      return year(d.getFullYear(), n)
    case 'x':
      return year(isoWeek(d).year, n)
    case 'M':
      if (n >= 4) return part(d, locale, { month: 'long' }, 'month')
      if (n === 3) return part(d, locale, { month: 'short' }, 'month').replace(/\.$/, '')
      return pad(d.getMonth() + 1, n)
    case 'd':
      return pad(d.getDate(), n)
    case 'D':
      return pad(dayOfYear(d), n)
    case 'E':
      return part(d, locale, { weekday: n >= 4 ? 'long' : 'short' }, 'weekday').replace(/\.$/, '')
    case 'e':
      return pad(d.getDay() || 7, n)
    case 'a':
      return d.getHours() < 12 ? 'AM' : 'PM'
    case 'H':
      return pad(d.getHours(), n)
    case 'k':
      return pad(d.getHours() || 24, n)
    case 'h':
      return pad(d.getHours() % 12 || 12, n)
    case 'K':
      return pad(d.getHours() % 12, n)
    case 'm':
      return pad(d.getMinutes(), n)
    case 's':
      return pad(d.getSeconds(), n)
    case 'S':
      // fraction of the second, n digits
      return pad(d.getMilliseconds(), 3).padEnd(n, '0').slice(0, n)
    case 'w':
      return pad(isoWeek(d).week, n)
    case 'Z':
      return n === 1 ? offset(d, false) : offset(d, true)
    case 'z':
      return part(d, locale, { timeZoneName: 'short' }, 'timeZoneName')
    default:
      return c.repeat(n)
  }
}

/** Default pattern of the Time Frame widget when none is configured. */
export const DEFAULT_TIME_PATTERN = 'dd.MM.yyyy HH:mm'
