/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { AnalysisDirectory } from './analysis'
import type { DashboardConfig, DashboardData, DashboardDataRequest, WidgetData } from './dashboard'
import type { HttpClient } from './http'
import type { RawDashboard } from '../dashboard/edit/raw'

/*
 * Editing of dashboards (E4, docs/WEB-API.md "Editing, stage E4"): the source as the Control Center stores it,
 * preview, create / save / copy / delete, background upload and the input widgets.
 */

/** GET /web/dashboards/{id}/source */
export interface DashboardSource {
  version: string
  source: RawDashboard
}

/** POST /web/dashboards/preview */
export interface DashboardPreview {
  dashboard: DashboardConfig
  data: DashboardData
}

/** GET /web/dashboards/directories (same shape as the analysis directories) */
export type DashboardDirectory = AnalysisDirectory

/** Body of POST /web/dashboards/{id}/widgets/{uuid}/value. */
export interface WidgetValue {
  /** number (Value Editor, Slider), boolean (Toggle Switch), "plus" / "minus" (PlusMinus) */
  value: number | string | boolean
  rowKey?: string
  /** Value Editor: timestamp of the manual entry (default now) */
  timestamp?: string
  /** Value Editor: save despite plausibility warnings */
  confirm?: boolean
  timeZone?: string
}

type Client = Pick<HttpClient, 'get' | 'post' | 'put' | 'delete'>
const lang = (locale: string) => `lang=${encodeURIComponent(locale)}`

export function loadSource(http: Pick<HttpClient, 'get'>, id: number): Promise<DashboardSource> {
  return http.get<DashboardSource>(`/web/dashboards/${id}/source`)
}

export function loadDashboardDirectories(http: Pick<HttpClient, 'get'>): Promise<DashboardDirectory[]> {
  return http.get<DashboardDirectory[]>('/web/dashboards/directories')
}

/** Preview of an unsaved source: `id` = the dashboard being edited (stored unreadable items, workday). */
export function previewDashboard(
  http: Pick<HttpClient, 'post'>,
  source: RawDashboard,
  request: DashboardDataRequest,
  signal?: AbortSignal,
  id?: number | null,
  directoryId?: number | null,
): Promise<DashboardPreview> {
  const body: Record<string, unknown> = { source, ...request }
  if (id != null) body.id = id
  if (directoryId != null) body.directoryId = directoryId
  return http.post<DashboardPreview>('/web/dashboards/preview', body, signal)
}

/** New dashboard; without source the server creates the Control Center's empty dashboard. */
export function createDashboard(http: Client, locale: string, directoryId: number, name: string, source?: RawDashboard): Promise<DashboardConfig> {
  const body: Record<string, unknown> = { directoryId, name }
  if (source) body.source = source
  return http.post<DashboardConfig>(`/web/dashboards?${lang(locale)}`, body)
}

/** Saves the source and / or renames (only `name`: rename without a new file). */
export function updateDashboard(http: Client, locale: string, id: number, version: string, source?: RawDashboard, name?: string): Promise<DashboardConfig> {
  const body: Record<string, unknown> = { version }
  if (source !== undefined) body.source = source
  if (name !== undefined) body.name = name
  return http.put<DashboardConfig>(`/web/dashboards/${id}?${lang(locale)}`, body)
}

export function copyDashboard(http: Client, locale: string, id: number, directoryId: number, name: string, source?: RawDashboard): Promise<DashboardConfig> {
  const body: Record<string, unknown> = { directoryId, name }
  if (source) body.source = source
  return http.post<DashboardConfig>(`/web/dashboards/${id}/copy?${lang(locale)}`, body)
}

export function deleteDashboard(http: Pick<HttpClient, 'delete'>, id: number, version: string): Promise<void> {
  return http.delete(`/web/dashboards/${id}?version=${encodeURIComponent(version)}`)
}

/** Writes a new background image (PNG, JPEG, GIF, BMP, WebP, SVG; max 10 MB). */
export function uploadBackground(http: Pick<HttpClient, 'putForm'>, id: number, file: File): Promise<void> {
  const form = new FormData()
  form.append('file', file)
  return http.putForm<void>(`/web/dashboards/${id}/background`, form)
}

/** Input widgets: writes a value as the Control Center widget does; answers the widget's data. */
export function writeWidgetValue(http: Pick<HttpClient, 'post'>, dashboardId: number, uuid: string, value: WidgetValue, locale?: string): Promise<WidgetData> {
  const q = locale ? `?${lang(locale)}` : ''
  return http.post<WidgetData>(`/web/dashboards/${dashboardId}/widgets/${encodeURIComponent(uuid)}/value${q}`, value)
}
