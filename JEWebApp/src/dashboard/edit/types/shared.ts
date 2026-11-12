/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { computed } from 'vue'
import { cssToFx, fxAlpha, widgetRows, type RawDashboard, type RawRow, type RawWidget } from '../raw'
import { createWidget, typeHasRows } from '../widgetDefaults'

/* Helpers of the per-type settings editors (E4): reading and writing the type block of a widget source in place. */

/** Props of every type settings component. */
export interface TypeEditorProps {
  widget: RawWidget
  source: RawDashboard
  dashboardId: number | null
}

export type Block = Record<string, unknown>

export const isObject = (v: unknown): v is Block => typeof v === 'object' && v !== null && !Array.isArray(v)

/** The type block `key` as the CC creates it for a new widget of `type` (its defaults; a fresh copy). */
export function defaultBlock(type: string, key: string): Block {
  const value = createWidget(type, 0, 0, 0)[key]
  return isObject(value) ? JSON.parse(JSON.stringify(value)) : {}
}

/**
 * The block `key` of the widget, created with the CC defaults if it is missing (the CC's `toNode` always writes the
 * whole block). Only called when writing, so looking at the settings does not change the source.
 */
export function ensureBlock(widget: RawWidget, key: string): Block {
  const current = widget[key]
  if (isObject(current)) return current
  const created = defaultBlock(widget.WidgetType, key)
  widget[key] = created
  return widget[key] as Block
}

/**
 * Reactive view of a type block: `cfg` reads (stored value or default), `set` writes (creating the block). `key` may be
 * a getter if it depends on the widget's type (a component instance may be reused for another widget).
 */
export function useBlock(props: { widget: RawWidget }, key: string | (() => string)) {
  const keyOf = typeof key === 'function' ? key : () => key
  const defaults = computed(() => defaultBlock(props.widget.WidgetType, keyOf()))
  const cfg = computed<Block>(() => {
    const current = props.widget[keyOf()]
    return isObject(current) ? current : defaults.value
  })
  const get = <T>(field: string, fallback: T): T => {
    const v = cfg.value[field]
    if (v === undefined || v === null) {
      const d = defaults.value[field]
      return (d === undefined ? fallback : d) as T
    }
    return v as T
  }
  const num = (field: string, fallback = 0): number => toNumber(get<unknown>(field, fallback)) ?? fallback
  const bool = (field: string, fallback = false): boolean => {
    const v = get<unknown>(field, fallback)
    return typeof v === 'boolean' ? v : typeof v === 'string' ? v === 'true' : fallback
  }
  const str = (field: string, fallback = ''): string => {
    const v = get<unknown>(field, fallback)
    return v === undefined || v === null ? fallback : String(v)
  }
  const block = () => ensureBlock(props.widget, keyOf())
  const set = (field: string, value: unknown) => {
    block()[field] = value
  }
  return { cfg, get, num, bool, str, set, block }
}

/** Number of a JSON value (numbers, numeric strings); null otherwise. */
export function toNumber(v: unknown): number | null {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  if (typeof v === 'string' && v.trim() !== '') {
    const n = Number(v.trim().replace(',', '.'))
    return Number.isFinite(n) ? n : null
  }
  return null
}

/** Parses a number input as the CC's text fields (`Double.parseDouble(text.replaceAll(",", "."))`); null if invalid. */
export function parseInput(text: string): number | null {
  if (text.trim() === '') return null
  const n = Number(text.trim().replace(',', '.'))
  return Number.isFinite(n) ? n : null
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

/** New JavaFX colour from a colour input (`#rrggbb`), keeping the alpha of the old value. */
export function fxFromInput(hex: string, old: unknown): string | null {
  return cssToFx(hex, fxAlpha(old))
}

/** Object id of a JSON value (number or numeric string as the CC's ImageConfig writes it); 0 if none. */
export function objectId(v: unknown): number {
  const n = toNumber(v)
  return n !== null && n > 0 ? Math.trunc(n) : 0
}

/* ---- type names ---- */

/** i18n key (below `dashboardEdit.types.names`) of each type id (the ids contain blanks and hyphens). */
export const TYPE_NAME_KEYS: Readonly<Record<string, string>> = {
  Title: 'title',
  Chart: 'chart',
  Pie: 'pie',
  Table: 'table',
  'Analyse-Link': 'analysisLink',
  Value: 'value',
  'String Value': 'stringValue',
  'Value Editor': 'valueEditor',
  'Dashboard-Link': 'dashboardLink',
  Arrow: 'arrow',
  Sankey: 'sankey',
  Image: 'image',
  Gauge: 'gauge',
  LinearGauge: 'linearGauge',
  Shape: 'shape',
  'Time Frame': 'timeFrame',
  Battery: 'battery',
  NetGraph: 'netGraph',
  'Toggle Switch': 'toggleSwitch',
  Slider: 'slider',
  PlusMinus: 'plusMinus',
  'Report-Link': 'reportLink',
  'Alarm Count': 'alarmCount',
}

/** Full i18n key of a type's name; null for unknown types. */
export function typeLabelKey(type: string): string | null {
  const key = TYPE_NAME_KEYS[type]
  return key ? 'dashboardEdit.types.names.' + key : null
}

/* ---- widget references ---- */

/** The types whose widget is a CC `DataModelWidget` (Time Frame candidates) — the types with rows. */
export function isDataWidget(type: string): boolean {
  return typeHasRows(type)
}

/** Widgets of the source of one of `types`, sorted by uuid (as the CC's combo boxes), optionally without `exclude`. */
export function widgetsOf(source: RawDashboard, types: readonly string[] | ((type: string) => boolean), exclude?: number): RawWidget[] {
  const match = typeof types === 'function' ? types : (t: string) => types.includes(t)
  return (source.Widget ?? [])
    .filter((w) => match(w.WidgetType) && w.uuid !== exclude)
    .slice()
    .sort((a, b) => Number(a.uuid) - Number(b.uuid))
}

/* ---- rows of a widget (NetGraph, Sankey) ---- */

export interface RowRef {
  id: number
  name: string
}

/** The distinct objects of a widget's rows (the CC keys NetGraph/Sankey settings by object id), in row order. */
export function rowObjects(widget: RawWidget): RowRef[] {
  const seen = new Set<number>()
  const list: RowRef[] = []
  for (const row of widgetRows(widget) as RawRow[]) {
    const id = Number(row?.id)
    if (!Number.isFinite(id) || id <= 0 || seen.has(id)) continue
    seen.add(id)
    list.push({ id, name: typeof row.name === 'string' && row.name ? row.name : `#${id}` })
  }
  return list
}

export interface NetGraphRow {
  id: number
  min: number
  max: number
}

/**
 * NetGraph per-row min/max for the widget's rows: stored entries keep their values, new rows get 0/100 and entries of
 * removed rows are dropped (NetGraphPojo.getConfigTab's listener on the data table).
 */
export function syncNetGraphRows(stored: unknown, rows: readonly RowRef[]): NetGraphRow[] {
  const byId = new Map<number, NetGraphRow>()
  for (const e of Array.isArray(stored) ? stored : []) {
    if (!isObject(e)) continue
    const id = objectId(e.id)
    if (id) byId.set(id, { id, min: toNumber(e.min) ?? 0, max: toNumber(e.max) ?? 100 })
  }
  return rows.map((r) => byId.get(r.id) ?? { id: r.id, min: 0, max: 100 })
}

export interface SankeyRow {
  id: number
  children: number[]
}

/**
 * Sankey rows for the widget's rows: stored order first (the CC's "Vertikale Sortierung"), rows not stored yet at the
 * end, entries of removed rows dropped (SankeyPojo.addChangeListenerForDataTable); children only among the rows.
 */
export function syncSankeyRows(stored: unknown, rows: readonly RowRef[]): SankeyRow[] {
  const ids = new Set(rows.map((r) => r.id))
  const result: SankeyRow[] = []
  const seen = new Set<number>()
  for (const e of Array.isArray(stored) ? stored : []) {
    if (!isObject(e)) continue
    const id = objectId(e.id)
    if (!id || !ids.has(id) || seen.has(id)) continue
    seen.add(id)
    const children = (Array.isArray(e.children) ? e.children : []).map(objectId).filter((c) => c && c !== id && ids.has(c))
    result.push({ id, children: [...new Set(children)] })
  }
  for (const r of rows) if (!seen.has(r.id)) result.push({ id: r.id, children: [] })
  return result
}

/**
 * Longest chain of "flows into" links (SankeyPojo.getMaxLevel / calculateMaxLevel); a cycle is not followed twice
 * (the CC would recurse endlessly).
 */
export function sankeyMaxLevel(rows: readonly SankeyRow[]): number {
  const byId = new Map(rows.map((r) => [r.id, r]))
  const depth = (row: SankeyRow, level: number, path: Set<number>): number => {
    let max = level
    for (const child of row.children) {
      const next = byId.get(child)
      if (!next || path.has(child)) continue
      path.add(child)
      max = Math.max(max, depth(next, level + 1, path))
      path.delete(child)
    }
    return max
  }
  return rows.reduce((max, r) => Math.max(max, depth(r, 0, new Set([r.id]))), 0)
}

/** Same rows (ids, order, children) — used to write a synchronised list only when it changes. */
export function sameJson(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}
