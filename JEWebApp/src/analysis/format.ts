/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

export interface Decimals {
  min: number
  max: number
}

const clampDigits = (n: number) => Math.min(20, Math.max(0, Math.round(Number.isFinite(n) ? n : 0)))

/** Decimals of a row: its decimalDigits when >= 0, otherwise the min/maxFractionDigits of the chart. */
export function decimalsFor(chart: { minFractionDigits: number; maxFractionDigits: number }, decimalDigits?: number | null): Decimals {
  if (decimalDigits != null && decimalDigits >= 0) {
    const d = clampDigits(decimalDigits)
    return { min: d, max: d }
  }
  const max = clampDigits(chart.maxFractionDigits ?? 2)
  const min = Math.min(clampDigits(chart.minFractionDigits ?? 0), max)
  return { min, max }
}

const numberFormats = new Map<string, Intl.NumberFormat>()

export function formatNumber(value: number | null | undefined, locale: string, decimals: Decimals): string {
  if (value == null || !Number.isFinite(value)) return ''
  const key = `${locale}|${decimals.min}|${decimals.max}`
  let format = numberFormats.get(key)
  if (!format) {
    format = new Intl.NumberFormat(locale, { minimumFractionDigits: decimals.min, maximumFractionDigits: decimals.max })
    numberFormats.set(key, format)
  }
  return format.format(value)
}

/** A sample value for display: numbers with the decimals of the row, text as it is. */
export function formatValue(value: number | string | null | undefined, locale: string, decimals: Decimals): string {
  if (typeof value === 'string') return value
  return formatNumber(value, locale, decimals)
}

const dateFormats = new Map<string, Intl.DateTimeFormat>()

function dateFormat(locale: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const key = locale + JSON.stringify(options)
  let format = dateFormats.get(key)
  if (!format) {
    format = new Intl.DateTimeFormat(locale, options)
    dateFormats.set(key, format)
  }
  return format
}

/** Date and time in the browser's time zone, e.g. 01.09.2026, 14:15 */
export function formatDateTime(ms: number, locale: string, seconds = false): string {
  return dateFormat(locale, { dateStyle: 'medium', timeStyle: seconds ? 'medium' : 'short' }).format(new Date(ms))
}

export function formatDate(ms: number, locale: string): string {
  return dateFormat(locale, { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(ms))
}

export function formatMonth(ms: number, locale: string): string {
  return dateFormat(locale, { month: 'short', year: 'numeric' }).format(new Date(ms))
}

const pad = (n: number, width = 2) => String(n).padStart(width, '0')

/** Local time as 2026-09-01 14:15:00 (for CSV files; Excel recognises it as date and time). */
export function localTimestamp(ms: number): string {
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const HTML_ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }

/** For text in ECharts tooltips (they are HTML). */
export function escapeHtml(text: string | null | undefined): string {
  return (text ?? '').replace(/[&<>"']/g, (c) => HTML_ESCAPES[c])
}
