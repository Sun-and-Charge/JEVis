/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'

/** `ChartType` of the Control Center (chart type and row type). */
export type ChartType =
  | 'AREA'
  | 'LOGICAL'
  | 'LINE'
  | 'BAR'
  | 'COLUMN'
  | 'BUBBLE'
  | 'SCATTER'
  | 'PIE'
  | 'TABLE'
  | 'HEAT_MAP'
  | 'DEFAULT'
  | 'TABLE_V'
  | 'STACKED_AREA'
  | 'STACKED_COLUMN'
  | 'TABLE_H'

export interface AnalysisRowConfig {
  key: string
  objectId: number
  attribute: string
  /** null when the row is not readable */
  name: string | null
  color: string | null
  axis: number
  chartType: ChartType | string
  unit: string | null
  /** overrides the decimals of the chart when >= 0 */
  decimalDigits: number
  calculation: boolean
  /** role in a bubble chart: X, Y, SIZE or NONE */
  bubbleType?: string
  readable: boolean
}

export interface AnalysisChartConfig {
  id: number
  name: string
  type: ChartType | string
  minFractionDigits: number
  maxFractionDigits: number
  height: number
  orientation: string
  fixYAxisToZero: boolean
  xAxisTitle: string | null
  yAxisTitle: string | null
  groupingInterval: number
  colorMapping: string
  rows: AnalysisRowConfig[]
}

export interface AnalysisDefaults {
  timeFrame: string
  aggregation: string
  manipulation: string
}

/** Answer of GET /web/analyses/{id}. */
export interface AnalysisConfig {
  id: number
  name: string
  names: Record<string, string>
  chartsPerScreen: number
  horizontalPies: number
  horizontalTables: number
  autoSize: boolean
  defaults: AnalysisDefaults
  charts: AnalysisChartConfig[]
}

/** Request of POST /web/analyses/{id}/data. */
export interface AnalysisDataRequest {
  timeFrame: string
  from: string | null
  to: string | null
  aggregation: string
  manipulation: string
  timeZone: string
}

export type RowStatus = 'OK' | 'NO_DATA' | 'NO_PERMISSION' | 'ERROR'

/** [epoch milliseconds, value (number or text), note] */
export type Sample = [number, number | string | null, string | null]

export interface RowStats {
  min: number | null
  max: number | null
  avg: number | null
  /** only for quantities */
  sum: number | null
  count: number
}

/** A note of a data row within the time frame (W4.4); tags are tag ids. */
export interface RowNote {
  timestamp: number
  text: string
  tags: string[]
  user: string | null
}

export interface AnalysisRowData {
  key: string
  status: RowStatus | string
  message?: string | null
  unit?: string | null
  samples?: Sample[]
  stats?: RowStats | null
  /** rows of pie charts: the value of the slice (then without samples) */
  pieValue?: number | null
  /** notes of the row, oldest first (W4.4; missing on older servers and for pie rows) */
  notes?: RowNote[]
}

export interface AnalysisChartData {
  id: number
  rows: AnalysisRowData[]
}

/** Answer of POST /web/analyses/{id}/data. */
export interface AnalysisData {
  from: string
  to: string
  charts: AnalysisChartData[]
}

/** The parts of the HTTP client that are used here (tests pass a stub). */
export type JsonClient = Pick<HttpClient, 'get' | 'post'>

export function loadAnalysis(http: JsonClient, id: number): Promise<AnalysisConfig> {
  return http.get<AnalysisConfig>(`/web/analyses/${id}`)
}

export function loadAnalysisData(http: JsonClient, id: number, request: AnalysisDataRequest): Promise<AnalysisData> {
  return http.post<AnalysisData>(`/web/analyses/${id}/data`, request)
}
