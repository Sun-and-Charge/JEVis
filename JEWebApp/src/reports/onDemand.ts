/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { OnDemandRequest, ReportFormat } from '../api/reports'

/*
 * R04 report on demand: labels of the periods in the time zone of the report and the state of the request list.
 */

interface DateParts {
  year: number
  month: number
  day: number
}

/** year, month and day of the instant in the time zone */
export function partsIn(iso: string, timeZone: string): DateParts | null {
  const ms = Date.parse(iso)
  if (Number.isNaN(ms)) return null
  try {
    const parts = new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: 'numeric', day: 'numeric' }).formatToParts(new Date(ms))
    const get = (type: string) => Number(parts.find((p) => p.type === type)?.value)
    return { year: get('year'), month: get('month'), day: get('day') }
  } catch {
    return null
  }
}

/** ISO 8601 week number of a calendar date */
export function isoWeek(year: number, month: number, day: number): { week: number; year: number } {
  const d = new Date(Date.UTC(year, month - 1, day))
  const weekday = d.getUTCDay() || 7
  d.setUTCDate(d.getUTCDate() + 4 - weekday)
  const yearStart = Date.UTC(d.getUTCFullYear(), 0, 1)
  return { week: Math.ceil(((d.getTime() - yearStart) / 86400000 + 1) / 7), year: d.getUTCFullYear() }
}

/**
 * The period that starts at {@code start}: "Oktober 2026", "Q4 2026", "2026", "KW 47 2026" or the day.
 * Falls back to the time stamp when the date cannot be read.
 */
export function periodLabel(
  start: string,
  schedule: string | null,
  timeZone: string,
  locale: string,
  t: (key: string, values?: Record<string, unknown>) => string,
): string {
  const p = partsIn(start, timeZone)
  if (!p) return start
  switch (schedule) {
    case 'YEARLY':
      return String(p.year)
    case 'QUARTERLY':
      return t('reports.onDemand.quarter', { quarter: Math.floor((p.month - 1) / 3) + 1, year: p.year })
    case 'MONTHLY':
      return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(Date.UTC(p.year, p.month - 1, 1))
    case 'WEEKLY': {
      const w = isoWeek(p.year, p.month, p.day)
      return t('reports.onDemand.week', { week: w.week, year: w.year })
    }
    default:
      return new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' }).format(Date.UTC(p.year, p.month - 1, p.day))
  }
}

export const isOpen = (r: Pick<OnDemandRequest, 'status'>): boolean => r.status === 'PENDING' || r.status === 'RUNNING'

/** the list is polled while a request is open */
export const hasOpen = (requests: readonly Pick<OnDemandRequest, 'status'>[]): boolean => requests.some(isOpen)

/** at most three open requests per report (the server answers 429 otherwise) */
export const MAX_OPEN = 3

export const canAddRequest = (requests: readonly Pick<OnDemandRequest, 'status'>[]): boolean => requests.filter(isOpen).length < MAX_OPEN

/** formats offered for download */
export function formats(r: Pick<OnDemandRequest, 'status' | 'pdf' | 'xlsx'>): ReportFormat[] {
  if (r.status !== 'DONE') return []
  const list: ReportFormat[] = []
  if (r.pdf) list.push('pdf')
  if (r.xlsx) list.push('xlsx')
  return list
}

/** file name of a download: report name and period, e.g. "Energiebericht_Oktober_2026.pdf" */
export function onDemandFileName(reportName: string, label: string, format: ReportFormat): string {
  const clean = (s: string) => s.replace(/[\/:*?"<>|]+/g, '').trim().replace(/\s+/g, '_')
  return `${clean(reportName) || 'Bericht'}_${clean(label)}.${format}`
}
