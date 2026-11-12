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
  /** E1: the user may add notes to the row */
  canAddNote?: boolean
  /** E1: raw data row the user may enter values for */
  canEnter?: boolean
  /** E1: class of the row object */
  rowClass?: string | null
  /** E3: stored unit (`unit` stays the display label) */
  unitPrefix?: string | null
  unitFormula?: string | null
  unitLabel?: string | null
  /** E3: the calculation whose output is the row (set by the server) */
  calculationId?: number | null
  /** E3: own time frame of the row */
  interval?: RowInterval | null
  /** E3: custom CSS of the row (table cells in the Control Center) */
  css?: string | null
}

/** E3: own time frame of a row (as stored: ISO date-times). */
export interface RowInterval {
  enabled: boolean
  start: string | null
  end: string | null
}

/** E3: horizontal limit line of an analysis (drawn on its axis in all charts with a time axis). */
export interface LimitLine {
  value: number
  label: string
  color: string
  /** 0 = left, 1 = right axis */
  axis: number
  /** NONE, ABOVE or BELOW */
  fill: string
}

/** E3: day filter as stored: ISO weekdays "6,7", holidays, dates "2026-12-24,2026-12-31". */
export interface DayFilterConfig {
  weekdays: string
  holidays: boolean
  dates: string
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
  /** E3 (all optional for older servers) */
  fractionDigitsAuto?: boolean | null
  filterEnabled?: boolean
  coloringEnabled?: boolean
  showColumnSums?: boolean
  showRowSums?: boolean
  /** HH:mm:ss or null */
  dayStart?: string | null
  dayEnd?: string | null
  /** filter text per column title (row name) of table charts */
  filterTexts?: Record<string, string> | null
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
  /** E3: timestamp of the stored analysis ("none" if never saved); sent back on changes */
  version?: string | null
  canWrite?: boolean
  canDelete?: boolean
  /** parent "Analyses Directory" (null if the parent is something else) */
  directoryId?: number | null
  forcedInterval?: string | null
  dayFilter?: DayFilterConfig | null
  limitLines?: LimitLine[] | null
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
  /** E1: `<notesObject>-<epoch ms>` as in GET /web/notes (needed to change the note) */
  id?: string
  /** E1: the "Data Notes" object of the note */
  notesObject?: number
  canEdit?: boolean
  canDelete?: boolean
  version?: string | null
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
  /** E1: class of the row object (null if not readable) */
  rowClass?: string | null
  /** E1: the user may add notes to the row */
  canAddNote?: boolean
  /** E1: raw "Data" / "Base Data" row with write right (manual values) */
  canEnter?: boolean
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
export type WriteClient = Pick<HttpClient, 'get' | 'post' | 'put' | 'delete'>

export function loadAnalysis(http: JsonClient, id: number): Promise<AnalysisConfig> {
  return http.get<AnalysisConfig>(`/web/analyses/${id}`)
}

export function loadAnalysisData(http: JsonClient, id: number, request: AnalysisDataRequest): Promise<AnalysisData> {
  return http.post<AnalysisData>(`/web/analyses/${id}/data`, request)
}

/* ---------------------------------------------------------------- E3: editing */

/** A row of the definition sent to the server (the GET shape without the read-only keys). */
export interface RowDefinition {
  objectId: number
  attribute: string
  name?: string | null
  color?: string | null
  axis?: number
  chartType?: string
  unitPrefix?: string | null
  unitFormula?: string | null
  unitLabel?: string | null
  decimalDigits?: number
  calculation?: boolean
  bubbleType?: string
  interval?: RowInterval | null
  css?: string | null
}

export interface ChartDefinition {
  id: number
  name: string
  type: string
  minFractionDigits: number
  maxFractionDigits: number
  fractionDigitsAuto: boolean | null
  height: number
  orientation: string
  fixYAxisToZero: boolean
  xAxisTitle: string | null
  yAxisTitle: string | null
  groupingInterval: number
  colorMapping: string
  filterEnabled: boolean
  coloringEnabled: boolean
  showColumnSums: boolean
  showRowSums: boolean
  dayStart: string | null
  dayEnd: string | null
  filterTexts: Record<string, string>
  rows: RowDefinition[]
}

/** The editable part of an analysis (body of PUT / POST / preview). */
export interface AnalysisDefinition {
  chartsPerScreen: number
  horizontalPies: number
  horizontalTables: number
  autoSize: boolean
  forcedInterval: string
  dayFilter: DayFilterConfig
  limitLines: LimitLine[]
  charts: ChartDefinition[]
}

/** GET /web/analyses/directories */
export interface AnalysisDirectory {
  id: number
  name: string
  /** "Org / Building / Analysen" */
  path: string
  canCreate: boolean
}

/** GET /web/rows/tree */
export interface RowTreeNode {
  id: number
  name: string
  class: string
  hasChildren: boolean
  selectable: boolean
  /** display unit of "Value" */
  unit?: string | null
  attributes?: string[]
  /** search hits: path of the object */
  path?: string | null
}

/** GET /web/rows/{id}/units */
export interface UnitOption {
  prefix: string | null
  formula: string | null
  label: string | null
}

/** 409 answer of create / rename / copy with a name that exists in the directory. */
export interface NameTaken {
  nameTaken: true
  id: number
}

const lang = (locale: string) => `lang=${encodeURIComponent(locale)}`

export function loadDirectories(http: JsonClient): Promise<AnalysisDirectory[]> {
  return http.get<AnalysisDirectory[]>('/web/analyses/directories')
}

export function loadRowTree(http: JsonClient, locale: string, parent?: number | null, q?: string): Promise<RowTreeNode[]> {
  const params = [parent != null ? `parent=${parent}` : '', q ? `q=${encodeURIComponent(q)}` : '', lang(locale)].filter(Boolean)
  return http.get<RowTreeNode[]>(`/web/rows/tree?${params.join('&')}`)
}

export function loadRowUnits(http: JsonClient, objectId: number, attribute: string): Promise<UnitOption[]> {
  return http.get<UnitOption[]>(`/web/rows/${objectId}/units?attribute=${encodeURIComponent(attribute)}`)
}

/**
 * Data of an unsaved definition (live preview). `id`: the analysis being edited (the server then resolves the time
 * frame with its workday and lets its stored unreadable rows answer NO_PERMISSION, as for saving).
 */
export function loadPreviewData(
  http: Pick<HttpClient, 'post'>,
  definition: AnalysisDefinition,
  request: AnalysisDataRequest,
  signal?: AbortSignal,
  id?: number,
): Promise<AnalysisData> {
  const body: Record<string, unknown> = { definition, ...request }
  if (id != null) body.id = id
  return http.post<AnalysisData>('/web/analyses/preview/data', body, signal)
}

export function createAnalysis(http: WriteClient, locale: string, directoryId: number, name: string, definition: AnalysisDefinition): Promise<AnalysisConfig> {
  return http.post<AnalysisConfig>(`/web/analyses?${lang(locale)}`, { directoryId, name, definition })
}

export function updateAnalysis(http: WriteClient, locale: string, id: number, version: string, definition: AnalysisDefinition, name?: string): Promise<AnalysisConfig> {
  const body: Record<string, unknown> = { version, definition }
  if (name !== undefined) body.name = name
  return http.put<AnalysisConfig>(`/web/analyses/${id}?${lang(locale)}`, body)
}

export function copyAnalysis(http: WriteClient, locale: string, id: number, directoryId: number, name: string, definition?: AnalysisDefinition): Promise<AnalysisConfig> {
  const body: Record<string, unknown> = { directoryId, name }
  if (definition) body.definition = definition
  return http.post<AnalysisConfig>(`/web/analyses/${id}/copy?${lang(locale)}`, body)
}

export function deleteAnalysis(http: WriteClient, id: number, version: string): Promise<void> {
  return http.delete(`/web/analyses/${id}?version=${encodeURIComponent(version)}`)
}
