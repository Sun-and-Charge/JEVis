/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { AnalysisChartConfig, AnalysisRowConfig, RowStats, RowStatus, Sample } from './analysis'
import type { HttpClient } from './http'

/** `FIT_WIDTH`, `FIT_HEIGHT`, `FIT_SCREEN` or a factor (1.0 = native size). */
export type DashboardZoom = 'FIT_WIDTH' | 'FIT_HEIGHT' | 'FIT_SCREEN' | number

export interface DashboardBackground {
  color: string | null
  /** an image can be loaded from GET /web/dashboards/{id}/background */
  image: boolean
  /** DEFAULT (top left, native size), REPEAT, STRETCH */
  mode: string
}

export interface DashboardTimeFrameOption {
  /** P1D, P1W, …, CUSTOM or the object id of a custom period of the server */
  id: string
  /** name of a custom period */
  name?: string | null
  custom: boolean
}

export interface WidgetStyle {
  background: string | null
  fontColor: string | null
  fontSize: number | null
  /** JavaFX FontWeight name (NORMAL, BOLD, …) */
  fontWeight: string | null
  italic: boolean
  underline: boolean
  /** JavaFX Pos name (TOP_LEFT … BOTTOM_RIGHT) */
  titlePosition: string | null
  borderSize: number | null
  borderColor: string | null
  cornerRadius: number | null
  shadow: boolean
}

/** A data row of a widget: the fields of an analysis row; axis and chart type only where they matter. */
export type DashboardRowConfig = Omit<AnalysisRowConfig, 'axis' | 'chartType'> & Partial<Pick<AnalysisRowConfig, 'axis' | 'chartType'>>

/** Link of a link or image widget. */
export interface WidgetLink {
  kind: 'ANALYSIS' | 'DASHBOARD' | 'REPORT' | string
  id: number
  /** time frame id of the dashboard to open the target with (null = the one of the dashboard) */
  timeFrame: string | null
  readable: boolean
}

export interface WidgetConfig {
  /** id within the dashboard (references of other widgets use it) */
  uuid: string
  type: string
  x: number
  y: number
  width: number
  height: number
  layer: number
  title: string | null
  tooltip: string | null
  style: WidgetStyle
  decimals: number | null
  decimalsAuto: boolean
  showValue: boolean
  /** own fixed time frame (e.g. LASTVALUE, P1M), null = the dashboard's */
  timeFrame: string | null
  rows: DashboardRowConfig[]
  /** only Chart widgets */
  chart: AnalysisChartConfig | null
  /** type specific, see docs/WEB-API.md */
  config: Record<string, unknown>
}

/** Answer of GET /web/dashboards/{id}. */
export interface DashboardConfig {
  id: number
  name: string
  names: Record<string, string>
  width: number
  height: number
  zoom: DashboardZoom | string
  background: DashboardBackground | null
  /** seconds */
  updateRate: number
  timeFrame: string
  timeFrames: DashboardTimeFrameOption[]
  widgets: WidgetConfig[]
}

/** Request of POST /web/dashboards/{id}/data. */
export interface DashboardDataRequest {
  timeFrame: string
  /** reference time of the period, null = now */
  date: string | null
  from: string | null
  to: string | null
  timeZone: string
}

export interface WidgetRowData {
  key: string
  status: RowStatus | string
  message?: string | null
  unit?: string | null
  /** one value for the interval */
  value?: number | null
  /** text of String Value and input widgets */
  text?: string | null
  lastTimestamp?: string | null
  /** Chart widgets */
  samples?: Sample[]
  stats?: RowStats | null
}

export interface WidgetData {
  from?: string | null
  to?: string | null
  rows?: WidgetRowData[]
  alarmCount?: number | null
}

/** Answer of POST /web/dashboards/{id}/data. */
export interface DashboardData {
  from: string
  to: string
  /** reference times for the arrows of the time frame bar (next null: would start in the future) */
  previous: string | null
  next: string | null
  /** by widget uuid; only widgets with data */
  widgets: Record<string, WidgetData>
}

export type DashboardClient = Pick<HttpClient, 'get' | 'post' | 'getBlob'>

export function loadDashboard(http: Pick<HttpClient, 'get'>, id: number): Promise<DashboardConfig> {
  return http.get<DashboardConfig>(`/web/dashboards/${id}`)
}

export function loadDashboardData(http: Pick<HttpClient, 'post'>, id: number, request: DashboardDataRequest): Promise<DashboardData> {
  return http.post<DashboardData>(`/web/dashboards/${id}/data`, request)
}

export const backgroundPath = (dashboardId: number) => `/web/dashboards/${dashboardId}/background`
export const objectImagePath = (objectId: number) => `/web/objects/${objectId}/image`
