/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { AnalysisChartConfig, AnalysisConfig, AnalysisData, AnalysisRowData, RowNote, RowStats, Sample } from '../api/analysis'
import { decimalsFor, type Decimals } from './format'
import { rowNotes } from './notes'

/** Row types that can be drawn in one chart with a time axis. */
export type XyType = 'LINE' | 'AREA' | 'STACKED_AREA' | 'COLUMN' | 'STACKED_COLUMN' | 'SCATTER' | 'LOGICAL' | 'BUBBLE'

/** How a whole chart is drawn. */
export type ChartKind = 'xy' | 'bar' | 'pie' | 'heatmap' | 'bubble' | 'table'

export type TableOrientation = 'vertical' | 'horizontal'

export type RowState = 'OK' | 'NO_DATA' | 'NO_PERMISSION' | 'ERROR'

export interface RowView {
  key: string
  /** display name, unique within the chart (legend toggling works by name) */
  name: string
  color: string
  axis: 0 | 1
  type: XyType
  unit: string
  decimals: Decimals
  state: RowState
  message: string | null
  samples: Sample[]
  stats: RowStats | null
  readable: boolean
  /** role in a bubble chart: X, Y, SIZE or NONE */
  bubbleType: string
  /** value of a pie slice computed by the server (pie rows come without samples) */
  pieValue: number | null
  /** notes of the row (markers on the curve); empty or missing without notes */
  notes?: RowNote[]
  /** E1: object of the row (data row id for notes and manual values) */
  objectId?: number
  /** E1: the user may add notes to the row */
  canAddNote?: boolean
  /** E1: raw data row the user may enter values for */
  canEnter?: boolean
}

export interface ChartView {
  id: number
  name: string
  kind: ChartKind
  /** the chart type of the configuration (normalised) */
  type: string
  orientation: TableOrientation
  decimals: Decimals
  fixYAxisToZero: boolean
  xAxisTitle: string | null
  yAxisTitle: string | null
  groupingInterval: number
  colorMapping: string
  height: number
  rows: RowView[]
}

/** Fallback colours for rows without a colour (Control Center like). */
export const FALLBACK_COLORS = ['#00538a', '#e0a100', '#2e7d32', '#b3261e', '#6a3d9a', '#00838f', '#8d6e63', '#c2185b', '#5b6576', '#9e9d24']

const XY_TYPES: readonly string[] = ['LINE', 'AREA', 'STACKED_AREA', 'COLUMN', 'STACKED_COLUMN', 'SCATTER', 'LOGICAL', 'BUBBLE']

export function chartKind(type: string): ChartKind {
  switch (type) {
    case 'BAR':
      return 'bar'
    case 'PIE':
      return 'pie'
    case 'HEAT_MAP':
      return 'heatmap'
    case 'BUBBLE':
      return 'bubble'
    case 'TABLE':
    case 'TABLE_V':
    case 'TABLE_H':
      return 'table'
    default:
      return 'xy'
  }
}

/** TABLE_H: timestamps as columns, TABLE_V and TABLE: timestamps as lines. */
export function tableOrientation(type: string): TableOrientation {
  return type === 'TABLE_H' ? 'horizontal' : 'vertical'
}

/** Effective type of a row in a chart with a time axis: DEFAULT is the chart's type, types that cannot be mixed are lines. */
export function rowType(chartType: string, rowChartType: string | null | undefined): XyType {
  const type = !rowChartType || rowChartType === 'DEFAULT' ? chartType : rowChartType
  if (XY_TYPES.includes(type)) return type as XyType
  if (type === 'BAR') return 'COLUMN'
  return 'LINE'
}

function normaliseType(type: string | null | undefined): string {
  return type && type !== 'DEFAULT' ? type : 'LINE'
}

function rowState(data: AnalysisRowData | undefined, readable: boolean): RowState {
  if (!readable) return 'NO_PERMISSION'
  if (!data) return 'NO_DATA'
  switch (data.status) {
    case 'OK':
      return (data.samples && data.samples.length) || typeof data.pieValue === 'number' ? 'OK' : 'NO_DATA'
    case 'NO_PERMISSION':
      return 'NO_PERMISSION'
    case 'ERROR':
      return 'ERROR'
    default:
      return 'NO_DATA'
  }
}

/** The samples of a row; a pie row (only a value) becomes one sample at the start of the time frame (CSV, table). */
function rowSamples(d: AnalysisRowData | undefined, data: AnalysisData | null): Sample[] {
  const samples = d?.samples ?? []
  if (samples.length || typeof d?.pieValue !== 'number') return samples
  const start = data ? Date.parse(data.from) : NaN
  return Number.isNaN(start) ? [] : [[start, d.pieValue, null]]
}

function uniqueName(name: string, used: Set<string>): string {
  let result = name
  for (let i = 2; used.has(result); i++) result = `${name} (${i})`
  used.add(result)
  return result
}

/**
 * Joins the configuration and the data answer of an analysis into one view model per chart.
 * `noAccessName` is the name shown for rows the user may not read.
 */
export function buildChartViews(config: AnalysisConfig, data: AnalysisData | null, noAccessName: string): ChartView[] {
  const dataCharts = new Map((data?.charts ?? []).map((c) => [c.id, c]))
  return (config.charts ?? []).map((chart: AnalysisChartConfig) => {
    const type = normaliseType(chart.type)
    const dataRows = new Map((dataCharts.get(chart.id)?.rows ?? []).map((r) => [r.key, r]))
    const used = new Set<string>()
    const rows = (chart.rows ?? []).map((row, index): RowView => {
      const d = dataRows.get(row.key)
      const readable = row.readable !== false
      const state = rowState(d, readable)
      return {
        key: row.key,
        name: uniqueName(readable && row.name ? row.name : noAccessName, used),
        color: row.color || FALLBACK_COLORS[index % FALLBACK_COLORS.length],
        axis: row.axis === 1 ? 1 : 0,
        type: rowType(type, row.chartType),
        unit: (d?.unit ?? row.unit ?? '') || '',
        decimals: decimalsFor(chart, row.decimalDigits),
        state,
        message: state === 'ERROR' ? (d?.message ?? null) : null,
        samples: state === 'OK' ? rowSamples(d, data) : [],
        stats: state === 'OK' ? (d?.stats ?? null) : null,
        readable,
        bubbleType: row.bubbleType ?? 'NONE',
        pieValue: state === 'OK' && typeof d?.pieValue === 'number' ? d.pieValue : null,
        notes: readable ? rowNotes(d?.notes) : [],
        objectId: row.objectId,
        canAddNote: readable && (row.canAddNote === true || d?.canAddNote === true),
        canEnter: readable && (row.canEnter === true || d?.canEnter === true),
      }
    })
    return {
      id: chart.id,
      name: chart.name ?? '',
      kind: chartKind(type),
      type,
      orientation: tableOrientation(type),
      decimals: decimalsFor(chart, -1),
      fixYAxisToZero: !!chart.fixYAxisToZero,
      xAxisTitle: chart.xAxisTitle || null,
      yAxisTitle: chart.yAxisTitle || null,
      groupingInterval: chart.groupingInterval > 0 ? chart.groupingInterval : 1,
      colorMapping: chart.colorMapping || 'GREEN_YELLOW_RED',
      height: chart.height,
      rows,
    }
  })
}

/** Numeric samples of a row (text values and gaps are left out). */
export function numericSamples(row: RowView): [number, number, string | null][] {
  const out: [number, number, string | null][] = []
  for (const s of row.samples) {
    if (typeof s[1] === 'number' && Number.isFinite(s[1])) out.push([s[0], s[1], s[2] ?? null])
  }
  return out
}

/** Value of a row as one number (pie slice, bar): the sum for quantities, otherwise the single value or the average. */
export function rowTotal(row: RowView): number | null {
  if (row.state !== 'OK') return null
  if (row.pieValue != null) return row.pieValue
  if (row.stats?.sum != null) return row.stats.sum
  const values = numericSamples(row)
  if (values.length === 1) return values[0][1]
  return row.stats?.avg ?? null
}

/** Last numeric value of a row (bar chart, like the Control Center). */
export function lastValue(row: RowView): number | null {
  const values = numericSamples(row)
  return values.length ? values[values.length - 1][1] : null
}
