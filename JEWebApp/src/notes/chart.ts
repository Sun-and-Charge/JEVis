/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { EChartsOption } from 'echarts'
import { escapeHtml, formatDateTime, formatNumber } from '../analysis/format'
import { sampleAt } from '../analysis/notes'
import type { RowSamples } from '../api/notes'

/*
 * Small chart of a data row around a note (W4.4): the raw values from one day before to one day after the note,
 * a vertical line at the note time and a pin on the value at that time.
 */

export const DAY_MS = 86_400_000

/** Time window of the detail chart: ±1 day around the note. */
export function noteWindow(ts: number): { from: number; to: number } {
  return { from: ts - DAY_MS, to: ts + DAY_MS }
}

/** Numeric samples (text values and gaps left out), sorted by time. */
export function numericRowSamples(samples: RowSamples['samples'] | null | undefined): [number, number][] {
  const out: [number, number][] = []
  for (const s of samples ?? []) {
    if (Array.isArray(s) && typeof s[0] === 'number' && typeof s[1] === 'number' && Number.isFinite(s[1])) out.push([s[0], s[1]])
  }
  return out.sort((a, b) => a[0] - b[0])
}

/** Decimals for the axis and tooltip: up to 3, fewer for large values. */
function decimalsOf(values: readonly [number, number][]): { min: number; max: number } {
  const max = Math.max(0, ...values.map(([, v]) => Math.abs(v)))
  return { min: 0, max: max >= 1000 ? 1 : max >= 10 ? 2 : 3 }
}

const INK = '#1a2a45'
const MUTED = '#5b6576'
const LINE = '#d9dee6'
const ACCENT = '#00538a'
const SUN = '#ffde00'
const FONT = 'Montserrat, Segoe UI, Arial, sans-serif'

export interface NoteChartContext {
  locale: string
  /** "Notiz" */
  noteLabel: string
}

/** ECharts option of the detail chart; `samples` numeric and sorted (see {@link numericRowSamples}). */
export function noteChartOption(samples: readonly [number, number][], ts: number, name: string, unit: string | null, ctx: NoteChartContext): EChartsOption {
  const { from, to } = noteWindow(ts)
  const decimals = decimalsOf(samples)
  const suffix = unit ? ' ' + unit : ''
  const at = sampleAt(samples, ts)
  return {
    animation: false,
    textStyle: { fontFamily: FONT, color: INK },
    grid: { left: 8, right: 16, top: 28, bottom: 8, containLabel: true },
    xAxis: {
      type: 'time',
      min: from,
      max: to,
      axisLine: { lineStyle: { color: LINE } },
      axisLabel: { color: MUTED, hideOverlap: true },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      name: unit ?? '',
      nameTextStyle: { color: MUTED, align: 'left' },
      splitLine: { lineStyle: { color: LINE } },
      axisLabel: { color: MUTED, formatter: (v: number) => formatNumber(v, ctx.locale, decimals) },
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      formatter: (raw: unknown) => {
        const p = (Array.isArray(raw) ? raw[0] : raw) as { value?: [number, number] } | undefined
        if (!p?.value) return ''
        const [t, v] = p.value
        return `<div style="font-weight:600">${escapeHtml(formatDateTime(t, ctx.locale))}</div><div>${escapeHtml(name)}: <b>${escapeHtml(formatNumber(v, ctx.locale, decimals))}</b>${escapeHtml(suffix)}</div>`
      },
    },
    series: [
      {
        type: 'line',
        name,
        data: samples.map(([t, v]) => [t, v]),
        color: ACCENT,
        showSymbol: samples.length <= 60,
        symbolSize: 4,
        lineStyle: { width: 2 },
        sampling: samples.length > 2000 ? 'lttb' : undefined,
        markLine: {
          silent: true,
          symbol: 'none',
          animation: false,
          lineStyle: { color: INK, type: 'dashed', width: 1.5 },
          label: { show: true, position: 'insideEndTop', formatter: ctx.noteLabel, color: INK, fontFamily: FONT, fontWeight: 600 },
          data: [{ xAxis: ts }],
        },
        markPoint: at
          ? {
              symbol: 'pin',
              symbolSize: 30,
              animation: false,
              silent: true,
              itemStyle: { color: SUN, borderColor: INK, borderWidth: 1.5 },
              label: { show: false },
              data: [{ name: 'note', coord: [ts, at[1]] }],
            }
          : undefined,
      },
    ],
  } as EChartsOption
}
