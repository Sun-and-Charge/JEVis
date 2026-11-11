/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { parseTime } from '../alarms/alarms'
import type { ReportFormat, ReportOutput, ReportSummary } from '../api/reports'
import { safeFileName } from '../iso/download'
import { matchesTerms, searchTerms } from '../modules/search'

/*
 * Reports (W4.6, view only) like the Control Center's Report Plugin: the reports grouped by site, a search, the
 * outputs of the selected report and their file names.
 */

const siteName = (r: Pick<ReportSummary, 'site'>, locale: string) => (r.site ? r.site.names?.[locale] || r.site.name : '')

function compareText(a: string | null | undefined, b: string | null | undefined, locale: string): number {
  return (a ?? '').localeCompare(b ?? '', locale, { sensitivity: 'base', numeric: true })
}

/**
 * Like the server: site name, then path (none first), then name (case-insensitive); reports without site last. The view
 * keeps the order of the answer; this is for tests and other callers.
 */
export function sortReports(reports: readonly ReportSummary[], locale: string): ReportSummary[] {
  return [...reports].sort((a, b) => {
    if (!a.site !== !b.site) return a.site ? -1 : 1
    return (
      compareText(siteName(a, locale), siteName(b, locale), locale) ||
      (a.site && b.site ? a.site.id - b.site.id : 0) ||
      compareText(a.path, b.path, locale) ||
      compareText(a.name, b.name, locale) ||
      a.id - b.id
    )
  })
}

/** Reports in which every search word occurs in the name, path, site or the file name of the newest output. */
export function filterReports(reports: readonly ReportSummary[], text: string, locale: string): ReportSummary[] {
  const terms = searchTerms(text, locale)
  if (!terms.length) return [...reports]
  return reports.filter((r) => matchesTerms(terms, [r.name, r.path, siteName(r, locale), r.latest?.name], locale))
}

export interface ReportGroup {
  /** site id or "none" */
  key: string
  /** site name, null for reports without site */
  site: string | null
  reports: ReportSummary[]
}

/** Groups the reports by site, keeping the order of the answer (the server sorts already, see {@link sortReports}). */
export function groupReports(reports: readonly ReportSummary[], locale: string): ReportGroup[] {
  const groups = new Map<string, ReportGroup>()
  for (const r of reports) {
    const key = r.site ? String(r.site.id) : 'none'
    let group = groups.get(key)
    if (!group) {
      group = { key, site: r.site ? siteName(r, locale) : null, reports: [] }
      groups.set(key, group)
    }
    group.reports.push(r)
  }
  return [...groups.values()]
}

/** A report has output (a PDF) that can be shown; the others are shown disabled like in the Control Center. */
export const hasOutput = (r: Pick<ReportSummary, 'latest'>): boolean => r.latest != null

/** Schedules of JEReport that have a translation (reports.schedule.*); other values are shown as stored. */
export const KNOWN_SCHEDULES = ['MINUTELY', 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'NONE'] as const

/** i18n key of a stored schedule, null if unknown or empty. */
export function scheduleKey(schedule: string | null | undefined): string | null {
  const s = (schedule ?? '').trim().toUpperCase()
  return (KNOWN_SCHEDULES as readonly string[]).includes(s) ? 'reports.schedule.' + s : null
}

/** Schedule for the UI: translated if known, else the stored value; empty without schedule. */
export function scheduleLabel(schedule: string | null | undefined, t: (key: string) => string): string {
  const key = scheduleKey(schedule)
  return key ? t(key) : (schedule ?? '').trim()
}

/** Date and time of a sample for the UI, the stored text if it cannot be read. */
export function outputTime(timestamp: string | null | undefined, formatTime: (ms: number) => string): string {
  const ms = parseTime(timestamp)
  return ms == null ? (timestamp ?? '') : formatTime(ms)
}

/** Entry of the output selector: "01.11.2026, 06:00 · Monatsbericht Energie_202610.pdf". */
export function outputLabel(output: Pick<ReportOutput, 'timestamp' | 'name'>, formatTime: (ms: number) => string): string {
  return [outputTime(output.timestamp, formatTime), output.name].filter(Boolean).join(' · ')
}

/** Outputs newest first (the server sorts already; kept stable for equal or unreadable times). */
export function sortOutputs(files: readonly ReportOutput[]): ReportOutput[] {
  return files
    .map((f, i) => ({ f, i, ms: parseTime(f.timestamp) }))
    .sort((a, b) => (a.ms != null && b.ms != null ? b.ms - a.ms : a.ms == null ? (b.ms == null ? 0 : 1) : -1) || a.i - b.i)
    .map((x) => x.f)
}

/** The ts of a format: the PDF timestamp or the XLSX timestamp (null: this output has no such file). */
export function outputTs(output: ReportOutput, format: ReportFormat): string | null {
  return format === 'pdf' ? output.timestamp : output.xlsx
}

const EXTENSION = /\.(pdf|xlsx?)$/i

/**
 * File name of a download: the stored PDF name; the XLSX gets the same name with ".xlsx" (the blob download cannot
 * read the Content-Disposition). Without a stored name: report name and date (2026-11-01).
 */
export function outputFileName(output: Pick<ReportOutput, 'timestamp' | 'name'> | null, format: ReportFormat, reportName: string): string {
  const stored = safeFileName(output?.name, '')
  let base = stored.replace(EXTENSION, '')
  if (!base) {
    const day = output?.timestamp?.slice(0, 10)
    base = safeFileName([reportName, day && /^\d{4}-\d{2}-\d{2}$/.test(day) ? day : ''].filter(Boolean).join('_'), 'report')
  }
  return `${base}.${format}`
}

/** The ?id= of the route as report id, null if missing or invalid. */
export function parseReportId(value: unknown): number | null {
  const text = Array.isArray(value) ? value[0] : value
  if (typeof text !== 'string' || !/^\d+$/.test(text)) return null
  const id = Number(text)
  return id > 0 && Number.isSafeInteger(id) ? id : null
}
