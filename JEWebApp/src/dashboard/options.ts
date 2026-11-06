/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { EChartsOption } from 'echarts'
import { escapeHtml, formatNumber, type Decimals } from '../analysis/format'
import { FALLBACK_COLORS } from '../analysis/model'
import { cssColor } from './style'
import { num, toPercent, type WidgetRow } from './values'

const FONT = 'Montserrat, Segoe UI, Arial, sans-serif'
const TRACK = '#e3e7ed'

/* ---------- Gauge ---------- */

export interface GaugeSection {
  start: number
  end: number
  color: string
}

export interface GaugeOptions {
  minimum: number
  maximum: number
  inPercent: boolean
  showTitle: boolean
  showUnit: boolean
  showValue: boolean
  sections: GaugeSection[]
}

export function gaugeConfig(config: Record<string, unknown> | null | undefined): GaugeOptions {
  const c = config ?? {}
  const minimum = num(c.minimum, 0)
  const maximum = num(c.maximum, 100)
  return {
    minimum,
    maximum: maximum > minimum ? maximum : minimum + 1,
    inPercent: !!c.inPercent,
    showTitle: c.showTitle !== false,
    showUnit: c.showUnit !== false,
    showValue: c.showValue !== false,
    sections: Array.isArray(c.sections) ? (c.sections as GaugeSection[]).filter((s) => s && Number.isFinite(s.start) && Number.isFinite(s.end)) : [],
  }
}

/** Color stops of the gauge arc ([fraction, color]) from the sections; gaps are grey. */
export function gaugeStops(sections: GaugeSection[], minimum: number, maximum: number, fallback: string = TRACK): [number, string][] {
  const span = maximum - minimum
  if (!(span > 0)) return [[1, fallback]]
  const sorted = sections
    .map((s) => ({ start: Math.max(0, (Math.min(s.start, s.end) - minimum) / span), end: Math.min(1, (Math.max(s.start, s.end) - minimum) / span), color: cssColor(s.color) ?? fallback }))
    .filter((s) => s.end > s.start)
    .sort((a, b) => a.start - b.start)
  const stops: [number, string][] = []
  let pos = 0
  for (const s of sorted) {
    if (s.start > pos) stops.push([s.start, fallback])
    if (s.end > pos) {
      stops.push([s.end, s.color])
      pos = s.end
    }
  }
  if (pos < 1) stops.push([1, fallback])
  return stops
}

export interface GaugeInput {
  title: string
  value: number | null
  unit: string
  decimals: Decimals
  color: string
  fontSize: number
  locale: string
}

export function gaugeOption(cfg: GaugeOptions, input: GaugeInput): EChartsOption {
  const min = cfg.inPercent ? 0 : cfg.minimum
  const max = cfg.inPercent ? 100 : cfg.maximum
  const value = cfg.inPercent ? toPercent(input.value, cfg.minimum, cfg.maximum) : input.value
  const unit = cfg.inPercent ? '%' : input.unit
  const stops = cfg.sections.length ? gaugeStops(cfg.sections, cfg.minimum, cfg.maximum) : null
  const fs = Math.max(8, input.fontSize)
  return {
    animation: false,
    series: [
      {
        type: 'gauge',
        min,
        max,
        radius: '88%',
        center: ['50%', '58%'],
        progress: { show: !stops, width: Math.max(4, fs * 0.8), itemStyle: { color: input.color } },
        axisLine: { lineStyle: { width: Math.max(4, fs * 0.8), color: stops ?? [[1, TRACK]] } },
        pointer: { show: !!stops, width: Math.max(2, fs * 0.3), itemStyle: { color: input.color } },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        anchor: { show: false },
        title: { show: cfg.showTitle && !!input.title, offsetCenter: [0, '-25%'], color: input.color, fontFamily: FONT, fontSize: fs },
        detail: {
          show: cfg.showValue,
          valueAnimation: false,
          offsetCenter: [0, '30%'],
          color: input.color,
          fontFamily: FONT,
          fontSize: fs * 1.3,
          fontWeight: 600,
          formatter: (v: number) => (value == null ? '–' : formatNumber(v, input.locale, input.decimals) + (cfg.showUnit && unit ? ' ' + unit : '')),
        },
        data: [{ value: value ?? min, name: input.title }],
      },
    ],
  } as EChartsOption
}

/* ---------- NetGraph (radar) ---------- */

export function netGraphOption(
  rows: WidgetRow[],
  cfg: { minimum: number; maximum: number; inPercent: boolean },
  ctx: { locale: string; decimals: Decimals; color: string; noAccess: string; fontSize: number },
): EChartsOption {
  const min = cfg.inPercent ? 0 : cfg.minimum
  const max = cfg.inPercent ? 100 : cfg.maximum
  const values = rows.map((r) => (cfg.inPercent ? toPercent(r.value, cfg.minimum, cfg.maximum) : r.value))
  const name = (r: WidgetRow) => (r.config.readable === false ? ctx.noAccess : r.config.name || r.config.key)
  return {
    animation: false,
    textStyle: { fontFamily: FONT },
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: () =>
        rows
          .map((r, i) => `<div>${escapeHtml(name(r))}: <b>${values[i] == null ? '–' : escapeHtml(formatNumber(values[i], ctx.locale, ctx.decimals))}</b> ${escapeHtml(cfg.inPercent ? '%' : r.unit)}</div>`)
          .join(''),
    },
    radar: {
      radius: '65%',
      indicator: rows.map((r) => ({ name: name(r), min, max: max > min ? max : min + 1 })),
      axisName: { color: ctx.color, fontFamily: FONT, fontSize: Math.max(8, ctx.fontSize) },
      splitLine: { lineStyle: { color: TRACK } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: TRACK } },
    },
    series: [
      {
        type: 'radar',
        symbolSize: 4,
        lineStyle: { color: rows[0]?.config.color || FALLBACK_COLORS[0] },
        itemStyle: { color: rows[0]?.config.color || FALLBACK_COLORS[0] },
        areaStyle: { opacity: 0.2 },
        data: [{ value: values.map((v) => (v == null ? min : v)), name: '' }],
      },
    ],
  } as EChartsOption
}

/* ---------- Sankey ---------- */

export interface SankeyNodeConfig {
  key: string
  children: string[]
}

export interface SankeyOptions {
  showValue: boolean
  showPercent: boolean
  nodes: SankeyNodeConfig[]
}

export function sankeyConfig(config: Record<string, unknown> | null | undefined): SankeyOptions {
  const c = config ?? {}
  const nodes = Array.isArray(c.nodes) ? (c.nodes as SankeyNodeConfig[]).filter((n) => n && n.key != null).map((n) => ({ key: String(n.key), children: (n.children ?? []).map(String) })) : []
  return { showValue: c.showValue !== false, showPercent: !!c.showPercent, nodes }
}

export interface SankeyLink {
  source: string
  target: string
  value: number
}

/** Links from each node to its children with the child's value; links that would form a cycle are left out. */
export function sankeyLinks(nodes: SankeyNodeConfig[], values: Map<string, number | null>): SankeyLink[] {
  const links: SankeyLink[] = []
  const edges = new Map<string, Set<string>>()
  const reaches = (from: string, to: string, seen = new Set<string>()): boolean => {
    if (from === to) return true
    if (seen.has(from)) return false
    seen.add(from)
    for (const next of edges.get(from) ?? []) if (reaches(next, to, seen)) return true
    return false
  }
  for (const node of nodes) {
    for (const child of node.children) {
      const v = values.get(child)
      if (!values.has(node.key) || v == null || !(v > 0) || reaches(child, node.key)) continue
      if (!edges.has(node.key)) edges.set(node.key, new Set())
      edges.get(node.key)!.add(child)
      links.push({ source: node.key, target: child, value: v })
    }
  }
  return links
}

export function sankeyOption(
  rows: WidgetRow[],
  cfg: SankeyOptions,
  ctx: { locale: string; decimals: Decimals; color: string; noAccess: string; fontSize: number },
): EChartsOption {
  const values = new Map(rows.map((r) => [r.config.key, r.value]))
  const byKey = new Map(rows.map((r) => [r.config.key, r]))
  const links = sankeyLinks(cfg.nodes, values)
  const used = new Set(links.flatMap((l) => [l.source, l.target]))
  const parentOf = new Map(links.map((l) => [l.target, l.source]))
  const name = (key: string) => {
    const r = byKey.get(key)
    return r && r.config.readable === false ? ctx.noAccess : r?.config.name || key
  }
  const label = (key: string) => {
    const r = byKey.get(key)
    const parts = [name(key)]
    if (cfg.showValue && r?.value != null) parts.push(formatNumber(r.value, ctx.locale, ctx.decimals) + (r.unit ? ' ' + r.unit : ''))
    const parent = parentOf.get(key)
    const pv = parent ? values.get(parent) : null
    if (cfg.showPercent && r?.value != null && pv) parts.push(formatNumber((r.value / pv) * 100, ctx.locale, { min: 0, max: 1 }) + ' %')
    return parts.join('\n')
  }
  return {
    animation: false,
    textStyle: { fontFamily: FONT },
    tooltip: { trigger: 'item', confine: true, formatter: (p: unknown) => escapeHtml(label(String((p as { data?: { name?: string; target?: string } }).data?.target ?? (p as { name?: string }).name ?? ''))).replace(/\n/g, '<br>') },
    series: [
      {
        type: 'sankey',
        left: 8,
        right: 8 + ctx.fontSize * 8,
        top: 8,
        bottom: 8,
        nodeWidth: 14,
        nodeGap: 10,
        emphasis: { focus: 'adjacency' },
        label: { color: ctx.color, fontFamily: FONT, fontSize: Math.max(8, ctx.fontSize), formatter: (p: { name: string }) => label(p.name) },
        lineStyle: { color: 'gradient', opacity: 0.35 },
        data: [...used].map((key, i) => ({ name: key, itemStyle: { color: byKey.get(key)?.config.color || FALLBACK_COLORS[i % FALLBACK_COLORS.length] } })),
        links,
      },
    ],
  } as EChartsOption
}
