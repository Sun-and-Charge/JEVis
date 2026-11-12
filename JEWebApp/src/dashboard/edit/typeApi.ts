/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from '../../api/http'
import { loadReports, type ReportSummary } from '../../api/reports'

/* HTTP helpers of the per-type settings editors of the dashboard editor (E4, docs/WEB-API.md). */

/** Answer of POST /web/dashboards/{id}/images: the new "File" child of the dashboard. */
export interface UploadedImage {
  objectId: number
  name: string
}

/** One hit of GET /web/rows/tree?q=… (E3 row picker; every readable object, with its class and path). */
export interface TreeHit {
  id: number
  name: string
  class: string
  path?: string
}

/** Classes an Alarm Count widget may filter by (AlarmCountWidget.openConfig: alarm directories and configurations). */
export const ALARM_CLASSES: readonly string[] = ['Alarm Configuration', 'Alarm Directory']

export function imagesPath(dashboardId: number): string {
  return `/web/dashboards/${dashboardId}/images`
}

/** Uploads an image for an Image widget (multipart field `file`). */
export function uploadImage(http: Pick<HttpClient, 'postForm'>, dashboardId: number, file: File): Promise<UploadedImage> {
  const form = new FormData()
  form.append('file', file, file.name)
  return http.postForm<UploadedImage>(imagesPath(dashboardId), form)
}

/** Readable reports (link targets of a Report-Link widget). */
export async function loadReportTargets(http: Pick<HttpClient, 'get'>): Promise<ReportSummary[]> {
  const list = await loadReports(http)
  return Array.isArray(list?.reports) ? list.reports : []
}

/** Searches alarm configurations / directories by name (at least 2 characters). */
export async function searchAlarmObjects(http: Pick<HttpClient, 'get'>, q: string, lang: string): Promise<TreeHit[]> {
  const p = new URLSearchParams({ q: q.trim(), lang })
  const hits = await http.get<TreeHit[]>(`/web/rows/tree?${p.toString()}`)
  return (Array.isArray(hits) ? hits : []).filter((h) => h && typeof h.id === 'number' && ALARM_CLASSES.includes(h.class))
}
