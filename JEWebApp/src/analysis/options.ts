/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { EChartsOption } from 'echarts'
import { escapeHtml, formatDate, formatDateTime, formatMonth, formatNumber, formatValue } from './format'
import { buildHeatMap, colorScale } from './heatmap'
import { lastValue, numericSamples, rowTotal, type ChartView, type RowView, type XyType } from './model'

/** Texts and locale for the chart options. */
export interface OptionContext {
  locale: string
  labels: {
    noData: string
    noPermission: string
    error: string
    /** "Anzahl" (bubble size) */
    count: string
  }
}

const GREY = '#9aa3b2'
const INK = '#1a2a45'
const LINE = '#d9dee6'
const MUTED = '#5b6576'

/** Short text for a row that has no values, or null for OK rows. */
export function stateText(row: Pick<RowView, 'state' | 'message'>, ctx: OptionContext): string | null {
  switch (row.state) {
    case 'NO_DATA':
      return ctx.labels.noData
    case 'NO_PERMISSION':
      return ctx.labels.noPermission
    case 'ERROR':
      return row.message ? `${ctx.labels.error}: ${row.message}` : ctx.labels.error
    default:
      return null
  }
}

/** Legend with greyed entries (and a short text) for rows without values. */
function legend(rows: RowView[], ctx: OptionContext): EChartsOption['legend'] {
  const suffix = new Map<string, string>()
  for (const row of rows) {
    const text = stateText(row, ctx)
    if (text) suffix.set(row.name, text)
  }
  // rich text instead of a text style per item: ECharts then measures the greyed entries correctly
  const rich = (text: string) => text.replace(/[{}|]/g, ' ')
  return {
    type: 'scroll',
    top: 0,
    left: 0,
    right: 0,
    icon: 'roundRect',
    itemWidth: 14,
    itemHeight: 8,
    textStyle: { color: INK, fontFamily: 'Montserrat, Segoe UI, Arial, sans-serif', rich: { off: { color: GREY } } },
    data: rows.map((row) => (suffix.has(row.name) ? { name: row.name, itemStyle: { color: GREY, opacity: 0.5 } } : { name: row.name })),
    formatter: (name: string) => (suffix.has(name) ? `{off|${rich(name)} (${rich(suffix.get(name)!)})}` : name),
  }
}

function axisUnits(rows: RowView[], axis: 0 | 1): string {
  return [...new Set(rows.filter((r) => r.axis === axis && r.readable && r.unit).map((r) => r.unit))].join(', ')
}

function valueLabelFormatter(chart: ChartView, ctx: OptionContext) {
  return (value: number) => formatNumber(value, ctx.locale, { min: 0, max: Math.max(chart.decimals.max, 0) })
}

const baseText = { fontFamily: 'Montserrat, Segoe UI, Arial, sans-serif', color: INK }

function valueLine(row: RowView, value: unknown, note: string | null, ctx: OptionContext, marker: string): string {
  const v = typeof value === 'number' || typeof value === 'string' ? formatValue(value, ctx.locale, row.decimals) : ''
  const unit = row.unit ? ' ' + escapeHtml(row.unit) : ''
  const noteText = note ? `<div style="color:${MUTED};margin-left:14px">${escapeHtml(note)}</div>` : ''
  return `<div>${marker}${escapeHtml(row.name)}: <b>${escapeHtml(v)}</b>${unit}</div>${noteText}`
}

interface TooltipParam {
  seriesIndex?: number
  seriesName?: string
  marker?: unknown
  value?: unknown
  axisValue?: unknown
  name?: string
  percent?: number
  dataIndex?: number
}

const markerOf = (p: TooltipParam) => (typeof p.marker === 'string' ? p.marker : '')

function seriesFor(row: RowView, data: [number, number, string | null][], stackCount: Map<string, number>): Record<string, unknown> {
  const many = data.length > 60
  const base: Record<string, unknown> = {
    name: row.name,
    id: row.key,
    yAxisIndex: row.axis,
    color: row.color,
    data,
    emphasis: { focus: 'series' },
  }
  const type: XyType = row.type
  switch (type) {
    case 'AREA':
    case 'STACKED_AREA':
    case 'LINE':
    case 'LOGICAL': {
      Object.assign(base, {
        type: 'line',
        showSymbol: !many,
        symbolSize: 5,
        sampling: data.length > 2000 ? 'lttb' : undefined,
        lineStyle: { width: 2 },
      })
      if (type === 'LOGICAL') base.step = 'end'
      if (type === 'AREA') base.areaStyle = { opacity: 0.25 }
      if (type === 'STACKED_AREA') {
        base.areaStyle = { opacity: 0.6 }
        base.stack = 'area-' + row.axis
      }
      break
    }
    case 'COLUMN':
    case 'STACKED_COLUMN':
      Object.assign(base, { type: 'bar', barMaxWidth: 28 })
      if (type === 'STACKED_COLUMN') base.stack = 'column-' + row.axis
      break
    default:
      // SCATTER, and BUBBLE rows inside a chart with a time axis
      Object.assign(base, { type: 'scatter', symbolSize: 7, large: data.length > 2000 })
  }
  if (base.stack) stackCount.set(base.stack as string, (stackCount.get(base.stack as string) ?? 0) + 1)
  return base
}

/** LINE, AREA, STACKED_AREA, COLUMN, STACKED_COLUMN, SCATTER, LOGICAL (also mixed per row): time axis, 1 or 2 y axes. */
export function xyOption(chart: ChartView, ctx: OptionContext): EChartsOption {
  const rows = chart.rows
  const stackCount = new Map<string, number>()
  const series = rows.map((row) => seriesFor(row, numericSamples(row), stackCount))
  const twoAxes = rows.some((r) => r.axis === 1)
  const onlyLogical = rows.length > 0 && rows.every((r) => r.type === 'LOGICAL')
  const hasBars = rows.some((r) => r.type === 'COLUMN' || r.type === 'STACKED_COLUMN')
  const yAxis = [0, 1].slice(0, twoAxes ? 2 : 1).map((axis) => {
    const name = axis === 0 && chart.yAxisTitle ? chart.yAxisTitle : axisUnits(rows, axis as 0 | 1)
    return {
      type: 'value' as const,
      name,
      nameTextStyle: { ...baseText, color: MUTED, align: axis === 0 ? ('left' as const) : ('right' as const) },
      position: axis === 0 ? ('left' as const) : ('right' as const),
      scale: !chart.fixYAxisToZero && !hasBars,
      min: chart.fixYAxisToZero || onlyLogical ? 0 : undefined,
      max: onlyLogical ? 1 : undefined,
      interval: onlyLogical ? 1 : undefined,
      splitLine: { show: axis === 0, lineStyle: { color: LINE } },
      axisLabel: { ...baseText, color: MUTED, formatter: valueLabelFormatter(chart, ctx) },
    }
  })
  const byIndex = rows
  return {
    animation: false,
    textStyle: baseText,
    legend: legend(rows, ctx),
    grid: { left: 8, right: twoAxes ? 8 : 16, top: 56, bottom: 56 },
    xAxis: {
      type: 'time',
      name: chart.xAxisTitle ?? undefined,
      nameLocation: 'middle',
      nameGap: 28,
      axisLine: { lineStyle: { color: LINE } },
      axisLabel: { ...baseText, color: MUTED, hideOverlap: true },
      splitLine: { show: false },
    },
    yAxis,
    tooltip: {
      trigger: rows.every((r) => r.type === 'SCATTER' || r.type === 'BUBBLE') ? 'item' : 'axis',
      confine: true,
      axisPointer: { type: 'line' },
      formatter: (raw: unknown) => {
        const params = (Array.isArray(raw) ? raw : [raw]) as TooltipParam[]
        if (!params.length) return ''
        const first = params[0].value as unknown[] | undefined
        const ts = Number(params[0].axisValue ?? first?.[0])
        const lines = params.map((p) => {
          const row = byIndex[p.seriesIndex ?? -1]
          const value = p.value as unknown[] | undefined
          return row ? valueLine(row, value?.[1], (value?.[2] as string | null) ?? null, ctx, markerOf(p)) : ''
        })
        return `<div style="font-weight:600;margin-bottom:4px">${escapeHtml(formatDateTime(ts, ctx.locale))}</div>${lines.join('')}`
      },
    },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0, filterMode: 'none' },
      { type: 'slider', xAxisIndex: 0, filterMode: 'none', height: 18, bottom: 6, brushSelect: false, labelFormatter: '' },
    ],
    series: series as EChartsOption['series'],
  } as EChartsOption
}

/** BAR: horizontal bars, one per row (its last value), like the Control Center. */
export function barOption(chart: ChartView, ctx: OptionContext): EChartsOption {
  const rows = chart.rows.filter((r) => lastValue(r) !== null)
  const units = [...new Set(rows.map((r) => r.unit).filter(Boolean))].join(', ')
  return {
    animation: false,
    textStyle: baseText,
    grid: { left: 8, right: 24, top: 16, bottom: 32 },
    xAxis: {
      type: 'value',
      name: chart.yAxisTitle || units,
      nameLocation: 'middle',
      nameGap: 26,
      nameTextStyle: { color: MUTED },
      min: chart.fixYAxisToZero ? 0 : undefined,
      splitLine: { lineStyle: { color: LINE } },
      axisLabel: { color: MUTED, formatter: valueLabelFormatter(chart, ctx), hideOverlap: true },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((r) => r.name),
      axisLabel: { color: INK, width: 160, overflow: 'truncate' },
      axisLine: { lineStyle: { color: LINE } },
    },
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (raw: unknown) => {
        const p = raw as TooltipParam
        const row = rows[p.dataIndex ?? -1]
        return row ? valueLine(row, p.value, null, ctx, markerOf(p)) : ''
      },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 32,
        data: rows.map((r) => ({ value: lastValue(r)!, itemStyle: { color: r.color } })),
        label: {
          show: true,
          position: 'right',
          color: INK,
          formatter: (p: { dataIndex: number; value: unknown }) => formatValue(p.value as number, ctx.locale, rows[p.dataIndex].decimals),
        },
      },
    ],
  } as EChartsOption
}

/** PIE: one slice per row, its sum (quantities) or its single value / average. */
export function pieOption(chart: ChartView, ctx: OptionContext): EChartsOption {
  const rows = chart.rows
  // rows without a positive value stay in the data as '-' (not drawn) so the legend shows them
  const slices = rows.map((row) => {
    const value = rowTotal(row)
    return { row, value: value !== null && value > 0 ? value : null }
  })
  return {
    animation: false,
    textStyle: baseText,
    legend: legend(rows, ctx),
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (raw: unknown) => {
        const p = raw as TooltipParam
        const slice = slices[p.dataIndex ?? -1]
        if (!slice || slice.value === null) return ''
        const percent = formatNumber(p.percent ?? 0, ctx.locale, { min: 1, max: 1 })
        return valueLine(slice.row, slice.value, null, ctx, markerOf(p)) + `<div style="color:${MUTED}">${percent} %</div>`
      },
    },
    series: [
      {
        type: 'pie',
        top: 40,
        radius: ['38%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: {
          color: INK,
          formatter: (p: { percent?: number; name?: string }) => `${p.name}\n${formatNumber(p.percent ?? 0, ctx.locale, { min: 0, max: 1 })} %`,
        },
        data: slices.map(({ row, value }) => ({ name: row.name, value: value ?? '-', itemStyle: { color: row.color } })),
      },
    ],
  } as EChartsOption
}

const pad2 = (n: number) => String(n).padStart(2, '0')

/** HEAT_MAP: first row with values; lines = days (or months), columns = time of day (or day of month). */
export function heatMapOption(chart: ChartView, ctx: OptionContext, from?: number | null, to?: number | null): EChartsOption {
  const row = chart.rows.find((r) => r.state === 'OK' && numericSamples(r).length > 0)
  if (!row) {
    return { animation: false, legend: legend(chart.rows, ctx), series: [] } as EChartsOption
  }
  const matrix = buildHeatMap(
    numericSamples(row).map(([t, v]) => [t, v]),
    row.stats?.sum != null,
    from,
    to,
  )
  const xLabels = matrix.columns.map((c) =>
    matrix.mode === 'intraday' ? `${pad2(Math.floor(c / 60))}:${pad2(c % 60)}` : matrix.mode === 'monthDays' ? String(c) : '',
  )
  const yLabels = matrix.lines.map((l) =>
    matrix.mode === 'intraday' ? formatDate(l, ctx.locale) : matrix.mode === 'monthDays' ? formatMonth(l, ctx.locale) : formatDateTime(l, ctx.locale),
  )
  const many = yLabels.length > 31
  return {
    animation: false,
    textStyle: baseText,
    grid: { left: 8, right: many ? 36 : 16, top: 8, bottom: 92 },
    tooltip: {
      confine: true,
      formatter: (raw: unknown) => {
        const p = raw as { value?: [number, number, number] }
        if (!p.value) return ''
        const [x, y, v] = p.value
        const head = matrix.mode === 'list' ? yLabels[y] : `${yLabels[y]} ${xLabels[x]}`
        return `<div style="font-weight:600;margin-bottom:4px">${escapeHtml(head)}</div>` + valueLine(row, v, null, ctx, '')
      },
    },
    xAxis: { type: 'category', data: xLabels, splitArea: { show: false }, axisLabel: { color: MUTED, hideOverlap: true } },
    yAxis: { type: 'category', data: yLabels, inverse: true, axisLabel: { color: MUTED, hideOverlap: true } },
    visualMap: {
      type: 'continuous',
      min: matrix.min ?? 0,
      max: matrix.max ?? 1,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      itemHeight: 160,
      itemWidth: 12,
      text: [row.unit, ''],
      textStyle: { color: MUTED },
      formatter: (v: unknown) => formatNumber(Number(v), ctx.locale, row.decimals),
      inRange: { color: colorScale(chart.colorMapping) },
    },
    dataZoom: many
      ? [
          { type: 'inside', yAxisIndex: 0 },
          { type: 'slider', yAxisIndex: 0, right: 4, width: 14, labelFormatter: '' },
        ]
      : undefined,
    series: [{ type: 'heatmap', name: row.name, data: matrix.cells, emphasis: { itemStyle: { borderColor: INK, borderWidth: 1 } } }],
  } as EChartsOption
}

/**
 * Points of a bubble chart like the Control Center's: the values of the first row (x) are grouped in steps of the
 * grouping interval; per group, y is the average of the other row's samples at the same timestamps and the bubble
 * size is the number of samples. With only one row, y is that number.
 */
export function bubblePoints(xRow: RowView, yRow: RowView | null, interval: number): { x: number; y: number; count: number }[] {
  const step = interval > 0 ? interval : 1
  const yByTime = new Map(yRow ? numericSamples(yRow).map(([t, v]) => [t, v]) : [])
  const groups = new Map<number, { count: number; ySum: number; yN: number }>()
  for (const [t, v] of numericSamples(xRow)) {
    const x = Math.floor(v / step) * step
    const g = groups.get(x) ?? { count: 0, ySum: 0, yN: 0 }
    g.count++
    const y = yByTime.get(t)
    if (y !== undefined) {
      g.ySum += y
      g.yN++
    }
    groups.set(x, g)
  }
  return [...groups.entries()]
    .sort((a, b) => a[0] - b[0])
    .filter(([, g]) => !yRow || g.yN > 0)
    .map(([x, g]) => ({ x, y: yRow ? g.ySum / g.yN : g.count, count: g.count }))
}

/**
 * The x row and the y rows of a bubble chart: the row marked X (else the first row) and the rows marked Y (else all
 * other rows), like the bubble type of the Control Center.
 */
export function bubbleRoles(rows: RowView[]): { xRow: RowView | undefined; yRows: RowView[] } {
  const xRow = rows.find((r) => r.bubbleType === 'X') ?? rows[0]
  const marked = rows.filter((r) => r !== xRow && r.bubbleType === 'Y')
  return { xRow, yRows: marked.length ? marked : rows.filter((r) => r !== xRow) }
}

/** BUBBLE: x = values of the x row, one bubble series per y row. */
export function bubbleOption(chart: ChartView, ctx: OptionContext): EChartsOption {
  const { xRow, yRows } = bubbleRoles(chart.rows)
  if (!xRow) return { series: [] }
  const targets: (RowView | null)[] = yRows.length ? yRows : [null]
  const pointSets = targets.map((y) => bubblePoints(xRow, y, chart.groupingInterval))
  const maxCount = Math.max(1, ...pointSets.flat().map((p) => p.count))
  const size = (count: number) => 8 + 32 * Math.sqrt(count / maxCount)
  const yUnit = yRows.length ? axisUnits(yRows, 0) || axisUnits(yRows, 1) : ctx.labels.count
  return {
    animation: false,
    textStyle: baseText,
    legend: legend(yRows.length ? [xRow, ...yRows] : [xRow], ctx),
    grid: { left: 8, right: 24, top: 56, bottom: 40 },
    xAxis: {
      type: 'value',
      scale: true,
      name: chart.xAxisTitle || (xRow.unit ? `${xRow.name} [${xRow.unit}]` : xRow.name),
      nameLocation: 'middle',
      nameGap: 26,
      nameTextStyle: { color: MUTED },
      axisLabel: { color: MUTED, formatter: valueLabelFormatter(chart, ctx) },
      splitLine: { lineStyle: { color: LINE } },
    },
    yAxis: {
      type: 'value',
      scale: !chart.fixYAxisToZero,
      min: chart.fixYAxisToZero ? 0 : undefined,
      name: chart.yAxisTitle || yUnit,
      nameTextStyle: { color: MUTED, align: 'left' },
      axisLabel: { color: MUTED, formatter: valueLabelFormatter(chart, ctx) },
      splitLine: { lineStyle: { color: LINE } },
    },
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (raw: unknown) => {
        const p = raw as TooltipParam
        const value = p.value as [number, number, number] | undefined
        if (!value) return ''
        const yRow = targets[p.seriesIndex ?? -1]
        const xText = valueLine(xRow, value[0], null, ctx, '')
        const yText = yRow
          ? valueLine(yRow, value[1], null, ctx, markerOf(p))
          : `<div>${escapeHtml(ctx.labels.count)}: <b>${value[1]}</b></div>`
        return xText + yText + `<div style="color:${MUTED}">${escapeHtml(ctx.labels.count)}: ${value[2]}</div>`
      },
    },
    series: pointSets.map((points, i) => ({
      type: 'scatter',
      name: targets[i]?.name ?? xRow.name,
      color: (targets[i] ?? xRow).color,
      itemStyle: { opacity: 0.7 },
      symbolSize: (v: number[]) => size(v[2]),
      data: points.map((p) => [p.x, p.y, p.count]),
    })),
  } as EChartsOption
}

/** ECharts option for a chart (not for table charts). `from`/`to`: resolved time frame (heat map lines). */
export function chartOption(chart: ChartView, ctx: OptionContext, from?: number | null, to?: number | null): EChartsOption | null {
  switch (chart.kind) {
    case 'xy':
      return xyOption(chart, ctx)
    case 'bar':
      return barOption(chart, ctx)
    case 'pie':
      return pieOption(chart, ctx)
    case 'heatmap':
      return heatMapOption(chart, ctx, from, to)
    case 'bubble':
      return bubbleOption(chart, ctx)
    default:
      return null
  }
}
