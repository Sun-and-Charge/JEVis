/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'

/** Newest PDF output of a report (GET /web/reports). */
export interface ReportLatest {
  /** time of the PDF sample, ISO 8601 with offset */
  timestamp: string
  pdf: boolean
  /** an XLSX within one minute of the PDF exists */
  xlsx: boolean
  /** stored file name of the PDF */
  name: string
}

/** One report of GET /web/reports (class "Report" or a subclass such as "Periodic Report"). */
export interface ReportSummary {
  id: number
  name: string
  className: string
  /** names below the building/organisation down to the parent of the report; null if empty */
  path: string | null
  /** next readable building above the report */
  site: { id: number; name: string; names?: Record<string, string> } | null
  enabled: boolean
  /** latest "Schedule" sample as stored (DAILY, MONTHLY, …), null if none */
  schedule: string | null
  /** newest PDF output, null if the report has none yet (shown disabled like in the Control Center) */
  latest: ReportLatest | null
  /** number of PDF outputs */
  count: number
}

/** Answer of GET /web/reports: sorted by site, path and name. */
export interface ReportList {
  reports: ReportSummary[]
}

/** One output of a report: the PDF and the XLSX written within one minute of it. */
export interface ReportOutput {
  /** time of the PDF sample (ts of format pdf) */
  timestamp: string
  /** stored file name of the PDF */
  name: string
  /** time of the matching XLSX sample (ts of format xlsx), null if none */
  xlsx: string | null
}

/** Answer of GET /web/reports/{id}/files: newest first. */
export interface ReportFiles {
  id: number
  name: string
  files: ReportOutput[]
}

export type ReportFormat = 'pdf' | 'xlsx'

type GetClient = Pick<HttpClient, 'get'>
type BlobClient = Pick<HttpClient, 'getBlob'>

export const REPORTS_PATH = '/web/reports'

export function reportFilesPath(id: number): string {
  return `/web/reports/${id}/files`
}

/** Path of one output; without ts the newest. inline: Content-Disposition inline (PDF preview). */
export function reportFilePath(id: number, format: ReportFormat, ts?: string | null, inline = false): string {
  const p = new URLSearchParams({ format })
  if (ts) p.set('ts', ts)
  if (inline) p.set('inline', 'true')
  return `/web/reports/${id}/file?${p.toString()}`
}

export function loadReports(http: GetClient): Promise<ReportList> {
  return http.get<ReportList>(REPORTS_PATH)
}

export function loadReportFiles(http: GetClient, id: number): Promise<ReportFiles> {
  return http.get<ReportFiles>(reportFilesPath(id))
}

export function loadReportFile(http: BlobClient, id: number, format: ReportFormat, ts?: string | null, inline = false): Promise<Blob> {
  return http.getBlob(reportFilePath(id, format, ts, inline))
}
