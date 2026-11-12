/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { EChartsOption } from 'echarts'
import { escapeHtml, formatDateTime, formatNumber } from '../analysis/format'

/*
 * Small chart of the input row behind a result cell (the Control Center's analysis button): the raw values of the
 * row in the result's period.
 */

const INK = '#1a2a45'
const MUTED = '#5b6576'
const LINE = '#d9dee6'
const ACCENT = '#00538a'
const FONT = 'Montserrat, Segoe UI, Arial, sans-serif'

function decimalsOf(values: readonly [number, number][]): { min: number; max: number } {
  const max = Math.max(0, ...values.map(([, v]) => Math.abs(v)))
  return { min: 0, max: max >= 1000 ? 1 : max >= 10 ? 2 : 3 }
}

/** ECharts option of the row chart; `samples` numeric and sorted (see numericRowSamples). */
export function costRowChartOption(samples: readonly [number, number][], from: number, to: number, name: string, unit: string | null, locale: string): EChartsOption {
  const decimals = decimalsOf(samples)
  const suffix = unit ? ' ' + unit : ''
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
      axisLabel: { color: MUTED, formatter: (v: number) => formatNumber(v, locale, decimals) },
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      formatter: (raw: unknown) => {
        const p = (Array.isArray(raw) ? raw[0] : raw) as { value?: [number, number] } | undefined
        if (!p?.value) return ''
        const [t, v] = p.value
        return `<div style="font-weight:600">${escapeHtml(formatDateTime(t, locale))}</div><div>${escapeHtml(name)}: <b>${escapeHtml(formatNumber(v, locale, decimals))}</b>${escapeHtml(suffix)}</div>`
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
      },
    ],
  } as EChartsOption
}
