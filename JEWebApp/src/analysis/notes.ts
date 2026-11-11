/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { RowNote } from '../api/analysis'
import { escapeHtml, formatDateTime } from './format'

/*
 * Notes of a data row as markers on its curve (W4.4): one pin per timestamp (several notes at the same time are
 * joined), placed on the value of the sample that covers the note time; tooltip with text, tags and user.
 */

/** Valid notes of a data answer (the field is missing on older servers): numbers as times, oldest first. */
export function rowNotes(raw: unknown): RowNote[] {
  if (!Array.isArray(raw)) return []
  const out: RowNote[] = []
  for (const n of raw) {
    if (!n || typeof n !== 'object') continue
    const o = n as Record<string, unknown>
    if (typeof o.timestamp !== 'number' || !Number.isFinite(o.timestamp)) continue
    out.push({
      timestamp: o.timestamp,
      text: typeof o.text === 'string' ? o.text : '',
      tags: Array.isArray(o.tags) ? o.tags.filter((t): t is string => typeof t === 'string' && t !== '') : [],
      user: typeof o.user === 'string' && o.user ? o.user : null,
    })
  }
  return out.sort((a, b) => a.timestamp - b.timestamp)
}

/**
 * The sample a note belongs to: the last one at or before the note time (aggregated values carry the start of
 * their period), the first one for notes before the first sample. `samples` sorted by time; null if empty.
 */
export function sampleAt(samples: readonly (readonly [number, number, ...unknown[]])[], ts: number): readonly [number, number, ...unknown[]] | null {
  if (!samples.length) return null
  let lo = 0
  let hi = samples.length - 1
  if (ts < samples[0][0]) return samples[0]
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1
    if (samples[mid][0] <= ts) lo = mid
    else hi = mid - 1
  }
  return samples[lo]
}

export interface NoteMarker {
  /** note time (x of the pin) */
  timestamp: number
  /** time of the sample the pin sits on */
  sampleTime: number
  /** y of the pin (value of that sample, for stacked rows plus the rows below) */
  value: number
  notes: RowNote[]
}

/**
 * Markers of a row: notes grouped by timestamp, each on the value of its sample. `offset` adds the stacked rows
 * below (value at the sample time). Without numeric samples there is no curve and no marker.
 */
export function noteMarkers(
  samples: readonly (readonly [number, number, ...unknown[]])[],
  notes: readonly RowNote[] | null | undefined,
  offset: (sampleTime: number) => number = () => 0,
): NoteMarker[] {
  if (!notes?.length || !samples.length) return []
  const byTime = new Map<number, RowNote[]>()
  for (const n of notes) {
    const list = byTime.get(n.timestamp)
    if (list) list.push(n)
    else byTime.set(n.timestamp, [n])
  }
  const markers: NoteMarker[] = []
  for (const [timestamp, group] of [...byTime.entries()].sort((a, b) => a[0] - b[0])) {
    const sample = sampleAt(samples, timestamp)
    if (!sample) continue
    markers.push({ timestamp, sampleTime: sample[0], value: sample[1] + offset(sample[0]), notes: group })
  }
  return markers
}

export interface NoteTooltipContext {
  locale: string
  /** "Notiz" */
  title: string
  tagName: (id: string) => string
}

const MAX_TEXT = 400

/** Tooltip of a marker (HTML, everything escaped; long texts shortened, line breaks kept). */
export function noteTooltip(marker: Pick<NoteMarker, 'timestamp' | 'notes'>, rowName: string, ctx: NoteTooltipContext): string {
  const head = `<div style="font-weight:600;margin-bottom:4px">${escapeHtml(ctx.title)} · ${escapeHtml(formatDateTime(marker.timestamp, ctx.locale))}</div>`
  const row = `<div style="color:#5b6576;margin-bottom:4px">${escapeHtml(rowName)}</div>`
  const items = marker.notes.map((n) => {
    const text = n.text.length > MAX_TEXT ? n.text.slice(0, MAX_TEXT) + '…' : n.text
    const tags = n.tags.filter((t) => t !== '0').map(ctx.tagName)
    const meta = [tags.join(', '), n.user ?? ''].filter(Boolean).join(' · ')
    return (
      `<div style="max-width:320px;white-space:pre-wrap;overflow-wrap:anywhere">${escapeHtml(text)}</div>` +
      (meta ? `<div style="color:#5b6576;font-size:12px">${escapeHtml(meta)}</div>` : '')
    )
  })
  return head + row + items.join('<div style="border-top:1px solid #d9dee6;margin:4px 0"></div>')
}

/** ECharts markPoint of a series with the note markers (undefined without markers). */
export function noteMarkPoint(markers: readonly NoteMarker[], rowName: string, color: string, ctx: NoteTooltipContext): Record<string, unknown> | undefined {
  if (!markers.length) return undefined
  return {
    symbol: 'pin',
    symbolSize: 26,
    animation: false,
    z: 5,
    itemStyle: { color, borderColor: '#ffffff', borderWidth: 1.5 },
    label: { show: true, formatter: (p: { dataIndex?: number }) => (markers[p.dataIndex ?? -1]?.notes.length ?? 0) > 1 ? String(markers[p.dataIndex!].notes.length) : '', color: '#ffffff', fontSize: 10, fontWeight: 600 },
    emphasis: { scale: 1.2 },
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (p: { dataIndex?: number }) => {
        const m = markers[p.dataIndex ?? -1]
        return m ? noteTooltip(m, rowName, ctx) : ''
      },
    },
    data: markers.map((m) => ({ name: 'note', coord: [m.timestamp, m.value], value: m.value })),
  }
}
