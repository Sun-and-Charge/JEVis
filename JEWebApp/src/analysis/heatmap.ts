/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/**
 * Matrix of a heat map like the Control Center's: one line per day and one column per time slot of the day
 * (15 minutes for finer data, otherwise the sample interval). Daily data: one line per month, one column per day of
 * the month. Weekly or coarser data: one line per sample. All in the browser's time zone.
 */
export interface HeatMapMatrix {
  mode: 'intraday' | 'monthDays' | 'list'
  /** minutes per column (intraday) */
  slotMinutes: number
  /** start of each column: minutes after midnight (intraday), day of month (monthDays), 0 (list) */
  columns: number[]
  /** start of each line (epoch ms, local midnight / first of month / sample time) */
  lines: number[]
  /** [column, line, value] */
  cells: [number, number, number][]
  min: number | null
  max: number | null
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
const SLOTS = [15, 30, 60, 120, 180, 240, 360, 480, 720, 1440]

/** Typical distance of the samples (median of the distances). */
export function typicalInterval(timestamps: number[]): number | null {
  const deltas: number[] = []
  for (let i = 1; i < timestamps.length; i++) {
    const d = timestamps[i] - timestamps[i - 1]
    if (d > 0) deltas.push(d)
  }
  if (!deltas.length) return null
  deltas.sort((a, b) => a - b)
  return deltas[Math.floor(deltas.length / 2)]
}

const startOfDay = (ms: number) => {
  const d = new Date(ms)
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}
const startOfMonth = (ms: number) => {
  const d = new Date(ms)
  return new Date(d.getFullYear(), d.getMonth(), 1).getTime()
}

/**
 * @param samples numeric samples [epoch ms, value], sorted by time
 * @param sum     true for quantities: samples that fall into the same cell are added, otherwise averaged
 * @param from    start of the time frame (lines start here even without data); optional
 * @param to      end of the time frame; optional
 */
export function buildHeatMap(samples: [number, number][], sum: boolean, from?: number | null, to?: number | null): HeatMapMatrix {
  const sorted = [...samples].sort((a, b) => a[0] - b[0])
  const interval = typicalInterval(sorted.map((s) => s[0])) ?? HOUR
  const first = from ?? sorted[0]?.[0] ?? Date.now()
  const last = to ?? sorted[sorted.length - 1]?.[0] ?? first

  let mode: HeatMapMatrix['mode']
  let slotMinutes = 0
  let columns: number[]
  let lines: number[] = []
  let cellOf: (ms: number) => [number, number] | null

  if (interval < 23 * HOUR) {
    mode = 'intraday'
    const minutes = interval / MINUTE
    slotMinutes = SLOTS.find((s) => s >= minutes - 0.5) ?? 1440
    columns = Array.from({ length: 1440 / slotMinutes }, (_, i) => i * slotMinutes)
    for (let d = startOfDay(first); d <= last; ) {
      lines.push(d)
      const next = new Date(d)
      next.setDate(next.getDate() + 1)
      d = next.getTime()
    }
    const index = new Map(lines.map((l, i) => [l, i]))
    cellOf = (ms) => {
      const line = index.get(startOfDay(ms))
      if (line === undefined) return null
      const t = new Date(ms)
      return [Math.floor((t.getHours() * 60 + t.getMinutes()) / slotMinutes), line]
    }
  } else if (interval < 6 * DAY) {
    mode = 'monthDays'
    columns = Array.from({ length: 31 }, (_, i) => i + 1)
    for (let m = startOfMonth(first); m <= last; ) {
      lines.push(m)
      const next = new Date(m)
      next.setMonth(next.getMonth() + 1)
      m = next.getTime()
    }
    const index = new Map(lines.map((l, i) => [l, i]))
    cellOf = (ms) => {
      const line = index.get(startOfMonth(ms))
      return line === undefined ? null : [new Date(ms).getDate() - 1, line]
    }
  } else {
    mode = 'list'
    columns = [0]
    lines = [...new Set(sorted.map((s) => s[0]))]
    const index = new Map(lines.map((l, i) => [l, i]))
    cellOf = (ms) => [0, index.get(ms)!]
  }

  const acc = new Map<string, { x: number; y: number; total: number; n: number }>()
  for (const [ms, value] of sorted) {
    const cell = cellOf(ms)
    if (!cell) continue
    const key = cell[0] + ':' + cell[1]
    const entry = acc.get(key)
    if (entry) {
      entry.total += value
      entry.n++
    } else acc.set(key, { x: cell[0], y: cell[1], total: value, n: 1 })
  }

  const cells: [number, number, number][] = []
  let min: number | null = null
  let max: number | null = null
  for (const { x, y, total, n } of acc.values()) {
    const v = sum ? total : total / n
    cells.push([x, y, v])
    min = min === null ? v : Math.min(min, v)
    max = max === null ? v : Math.max(max, v)
  }
  cells.sort((a, b) => a[1] - b[1] || a[0] - b[0])
  return { mode, slotMinutes, columns, lines, cells, min, max }
}

const COLOR_WORDS: Record<string, string> = {
  BLUE: '#2c7bb6',
  CYAN: '#00bcd4',
  GREEN: '#1a9641',
  LIME: '#a6d96a',
  YELLOW: '#ffde00',
  ORANGE: '#fdae61',
  RED: '#d7191c',
  BLACK: '#1a2a45',
  WHITE: '#ffffff',
  GRAY: '#9e9e9e',
  GREY: '#9e9e9e',
  PURPLE: '#7b3294',
  MAGENTA: '#c2185b',
}

/** Colour scale of a ColorMapping name of the Control Center (e.g. GREEN_YELLOW_RED); unknown names: green, yellow, red. */
export function colorScale(mapping: string | null | undefined): string[] {
  const colors = (mapping ?? '')
    .split('_')
    .map((w) => COLOR_WORDS[w])
    .filter((c): c is string => !!c)
  return colors.length >= 2 ? colors : [COLOR_WORDS.GREEN, COLOR_WORDS.YELLOW, COLOR_WORDS.RED]
}
