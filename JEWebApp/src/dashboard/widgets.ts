/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { AnalysisChartConfig, AnalysisConfig, AnalysisData, AnalysisRowConfig } from '../api/analysis'
import type { DashboardData, WidgetConfig, WidgetData, WidgetLink } from '../api/dashboard'
import { buildChartViews, type ChartView } from '../analysis/model'
import { DEFAULT_TIME_PATTERN, formatJoda } from './joda'
import { analysisQuery, dashboardQuery, type TimeBarState } from './timeframe'
import { parseTime } from './values'

/** Component family of a widget type. */
export type WidgetKind =
  | 'title'
  | 'value'
  | 'string'
  | 'table'
  | 'pie'
  | 'chart'
  | 'gauge'
  | 'linearGauge'
  | 'battery'
  | 'shape'
  | 'arrow'
  | 'netGraph'
  | 'sankey'
  | 'timeFrame'
  | 'alarmCount'
  | 'image'
  | 'link'
  | 'input'
  | 'empty'

const KINDS: Record<string, WidgetKind> = {
  Title: 'title',
  Value: 'value',
  'String Value': 'string',
  Table: 'table',
  Pie: 'pie',
  Chart: 'chart',
  Gauge: 'gauge',
  LinearGauge: 'linearGauge',
  Battery: 'battery',
  Shape: 'shape',
  Arrow: 'arrow',
  NetGraph: 'netGraph',
  Sankey: 'sankey',
  'Time Frame': 'timeFrame',
  'Alarm Count': 'alarmCount',
  Image: 'image',
  'Analyse-Link': 'link',
  'Dashboard-Link': 'link',
  'Report-Link': 'link',
  'Value Editor': 'input',
  'Toggle Switch': 'input',
  Slider: 'input',
  PlusMinus: 'input',
}

export function widgetKind(type: string | null | undefined): WidgetKind {
  return (type && KINDS[type]) || 'empty'
}

/** The link of a link or image widget, if it has one. */
export function widgetLink(widget: Pick<WidgetConfig, 'config'>): WidgetLink | null {
  const link = (widget.config as { link?: WidgetLink | null } | null)?.link
  return link && typeof link === 'object' && link.id != null ? link : null
}

/* ---------- charts (Chart and Pie widgets reuse the analysis view model) ---------- */

function analysisRow(row: WidgetConfig['rows'][number]): AnalysisRowConfig {
  return { axis: 0, chartType: 'DEFAULT', ...row }
}

function chartView(chart: AnalysisChartConfig, data: WidgetData | undefined, noAccessName: string, pie: boolean): ChartView | null {
  const config: AnalysisConfig = {
    id: 0,
    name: '',
    names: {},
    chartsPerScreen: 1,
    horizontalPies: 1,
    horizontalTables: 1,
    autoSize: true,
    defaults: { timeFrame: 'TODAY', aggregation: 'NONE', manipulation: 'NONE' },
    charts: [{ ...chart, id: 0 }],
  }
  const rows = (data?.rows ?? []).map((r) => ({
    key: r.key,
    status: r.status,
    message: r.message,
    unit: r.unit,
    samples: r.samples,
    stats: r.stats,
    pieValue: pie && typeof r.value === 'number' ? r.value : undefined,
  }))
  const analysisData: AnalysisData | null = data ? { from: data.from ?? '', to: data.to ?? '', charts: [{ id: 0, rows }] } : null
  return buildChartViews(config, analysisData, noAccessName)[0] ?? null
}

/** Chart widget: its chart (like an analysis chart) with the samples of the answer. */
export function chartWidgetView(widget: Pick<WidgetConfig, 'chart' | 'rows' | 'title'>, data: WidgetData | undefined, noAccessName: string): ChartView | null {
  const chart: AnalysisChartConfig = widget.chart ?? {
    id: 0,
    name: widget.title ?? '',
    type: 'LINE',
    minFractionDigits: 2,
    maxFractionDigits: 2,
    height: -1,
    orientation: 'HORIZONTAL',
    fixYAxisToZero: false,
    xAxisTitle: null,
    yAxisTitle: null,
    groupingInterval: 1,
    colorMapping: 'GREEN_YELLOW_RED',
    rows: (widget.rows ?? []).map(analysisRow),
  }
  return chartView({ ...chart, rows: (chart.rows ?? []).map(analysisRow) }, data, noAccessName, chart.type === 'PIE')
}

/** Pie widget: one slice per row with the value of the interval. */
export function pieWidgetView(
  widget: Pick<WidgetConfig, 'rows' | 'title' | 'decimals'>,
  data: WidgetData | undefined,
  noAccessName: string,
): ChartView | null {
  const d = typeof widget.decimals === 'number' && widget.decimals >= 0 ? widget.decimals : 2
  const chart: AnalysisChartConfig = {
    id: 0,
    name: widget.title ?? '',
    type: 'PIE',
    minFractionDigits: d,
    maxFractionDigits: d,
    height: -1,
    orientation: 'HORIZONTAL',
    fixYAxisToZero: false,
    xAxisTitle: null,
    yAxisTitle: null,
    groupingInterval: 1,
    colorMapping: 'GREEN_YELLOW_RED',
    rows: (widget.rows ?? []).map(analysisRow),
  }
  return chartView(chart, data, noAccessName, true)
}

/* ---------- Time Frame widget ---------- */

export interface TimeFrameWidgetConfig {
  source: string | number | null
  start: string
  end: string
  format: string | null
  count: boolean
}

/**
 * Text of a Time Frame widget like the Control Center: with own rows the newest timestamp; otherwise from the
 * source widget its period start/end (or newest timestamp), or the number of its samples.
 * Without a source widget the period of the dashboard is used.
 */
export function timeFrameText(widget: Pick<WidgetConfig, 'uuid' | 'rows' | 'config'>, data: DashboardData | null, locale: string): string {
  if (!data) return ''
  const cfg = (widget.config ?? {}) as Partial<TimeFrameWidgetConfig>
  const pattern = cfg.format || DEFAULT_TIME_PATTERN
  const fmt = (ms: number | null) => (ms == null ? null : formatJoda(ms, pattern, locale))
  const own = data.widgets?.[String(widget.uuid)]
  if ((widget.rows ?? []).length) {
    const ts = parseTime(own?.rows?.[0]?.lastTimestamp)
    return fmt(ts) ?? ''
  }
  const hasSource = cfg.source != null && cfg.source !== ''
  const source: WidgetData | undefined = hasSource ? data.widgets?.[String(cfg.source)] : { from: data.from, to: data.to }
  if (!source) return ''
  if (cfg.count) {
    const row = source.rows?.[0]
    return String(row?.samples?.length ?? row?.stats?.count ?? 0)
  }
  const start = cfg.start === 'NONE' ? null : fmt(parseTime(source.from))
  const end = cfg.end === 'NONE' ? null : cfg.end === 'LAST_TS' ? fmt(parseTime(source.rows?.[0]?.lastTimestamp)) : fmt(parseTime(source.to))
  return [start, end].filter((s) => s).join(' - ')
}

/* ---------- links ---------- */

export type LinkTarget =
  | { kind: 'route'; to: { name: 'analysis' | 'dashboard'; params: { id: number }; query: Record<string, string> } }
  | { kind: 'disabled'; reason: 'report' | 'noAccess' }

/**
 * Where a link of an Analyse-, Dashboard- or Report-Link (or Image) widget leads: analyses open with the time frame
 * of the dashboard where possible; reports are not available in the web app yet.
 */
export function linkTarget(link: WidgetLink, state: TimeBarState, shown: { from: number; to: number } | null): LinkTarget {
  const kind = String(link.kind).toUpperCase()
  if (kind === 'REPORT') return { kind: 'disabled', reason: 'report' }
  if (!link.readable || !(Number(link.id) > 0)) return { kind: 'disabled', reason: 'noAccess' }
  if (kind === 'ANALYSIS') return { kind: 'route', to: { name: 'analysis', params: { id: Number(link.id) }, query: analysisQuery(link, state, shown) } }
  return { kind: 'route', to: { name: 'dashboard', params: { id: Number(link.id) }, query: dashboardQuery(link) } }
}
