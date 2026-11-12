/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { nextUuid, type RawDashboard, type RawWidget } from './raw'

/*
 * Canvas operations of the dashboard editor (E4), as pure functions on the source: grid snapping, moving and
 * resizing (8 handles), keyboard moves, rubber band, layers, duplicate and delete. Coordinates are dashboard pixels
 * (unscaled), as stored in `xPos` / `yPos` / `width` / `height`.
 */

/** Grid interval of the Control Center when none is stored. */
export const DEFAULT_GRID = 25
/** Smallest widget size accepted by the server. */
export const MIN_SIZE = 10
export const MAX_SIZE = 20000

export type Handle = 'n' | 's' | 'e' | 'w' | 'ne' | 'nw' | 'se' | 'sw'
export const HANDLES: readonly Handle[] = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w']

export interface Rect {
  x: number
  y: number
  width: number
  height: number
}

const finite = (v: unknown, fallback: number) => (typeof v === 'number' && Number.isFinite(v) ? v : fallback)

/** Grid interval of the dashboard (x, y); Shift: a quarter of it (at least 1 px). */
export function gridOf(source: Pick<RawDashboard, 'X Axis Grid Interval' | 'Y Axis Grid Interval'>, fine = false): { x: number; y: number } {
  const gx = Math.max(1, finite(source['X Axis Grid Interval'], DEFAULT_GRID))
  const gy = Math.max(1, finite(source['Y Axis Grid Interval'], DEFAULT_GRID))
  return fine ? { x: Math.max(1, gx / 4), y: Math.max(1, gy / 4) } : { x: gx, y: gy }
}

/** Value on the grid (grid 0 or less: unchanged, rounded to 0.01 px). */
export function snap(value: number, grid: number): number {
  if (!(grid > 0)) return Math.round(value * 100) / 100
  return Math.round(Math.round(value / grid) * grid * 100) / 100
}

export const rectOf = (w: Pick<RawWidget, 'xPos' | 'yPos' | 'width' | 'height'>): Rect => ({
  x: finite(w.xPos, 0),
  y: finite(w.yPos, 0),
  width: finite(w.width, MIN_SIZE),
  height: finite(w.height, MIN_SIZE),
})

/** Start positions of a move (uuid → x, y). */
export type Starts = Map<number, { x: number; y: number }>

export function startsOf(widgets: readonly RawWidget[], selection: ReadonlySet<number>): Starts {
  const starts: Starts = new Map()
  for (const w of widgets) if (selection.has(w.uuid)) starts.set(w.uuid, { x: finite(w.xPos, 0), y: finite(w.yPos, 0) })
  return starts
}

/**
 * Move by dragging: the widget under the pointer (`lead`) is snapped to the grid, the others keep their distance
 * to it. The group stays at x, y ≥ 0. Returns the new positions.
 */
export function dragPositions(starts: Starts, lead: number, dx: number, dy: number, grid: { x: number; y: number } | null): Starts {
  const leadStart = starts.get(lead) ?? starts.values().next().value
  if (!leadStart) return new Map()
  let mx = grid ? snap(leadStart.x + dx, grid.x) - leadStart.x : dx
  let my = grid ? snap(leadStart.y + dy, grid.y) - leadStart.y : dy
  const minX = Math.min(...[...starts.values()].map((s) => s.x))
  const minY = Math.min(...[...starts.values()].map((s) => s.y))
  if (minX + mx < 0) mx = -minX
  if (minY + my < 0) my = -minY
  const out: Starts = new Map()
  for (const [uuid, s] of starts) out.set(uuid, { x: round(s.x + mx), y: round(s.y + my) })
  return out
}

const round = (n: number) => Math.round(n * 100) / 100

/**
 * Resize with a handle: the dragged edges follow the pointer (snapped to the grid), the opposite edges stay.
 * At least MIN_SIZE, never left of / above 0.
 */
export function resizeRect(start: Rect, handle: Handle, dx: number, dy: number, grid: { x: number; y: number } | null): Rect {
  const sx = (v: number) => (grid ? snap(v, grid.x) : round(v))
  const sy = (v: number) => (grid ? snap(v, grid.y) : round(v))
  let left = start.x
  let top = start.y
  let right = start.x + start.width
  let bottom = start.y + start.height
  if (handle.includes('w')) left = Math.max(0, Math.min(sx(start.x + dx), right - MIN_SIZE))
  if (handle.includes('e')) right = Math.max(left + MIN_SIZE, Math.min(sx(right + dx), left + MAX_SIZE))
  if (handle.includes('n')) top = Math.max(0, Math.min(sy(start.y + dy), bottom - MIN_SIZE))
  if (handle.includes('s')) bottom = Math.max(top + MIN_SIZE, Math.min(sy(bottom + dy), top + MAX_SIZE))
  return { x: round(left), y: round(top), width: round(right - left), height: round(bottom - top) }
}

/** Arrow keys: one grid step, with Shift 1 px; positions stay ≥ 0. Returns whether something moved. */
export function nudge(widgets: readonly RawWidget[], selection: ReadonlySet<number>, key: string, fine: boolean, grid: { x: number; y: number }): boolean {
  const step = { x: fine ? 1 : grid.x, y: fine ? 1 : grid.y }
  let dx = 0
  let dy = 0
  if (key === 'ArrowLeft') dx = -step.x
  else if (key === 'ArrowRight') dx = step.x
  else if (key === 'ArrowUp') dy = -step.y
  else if (key === 'ArrowDown') dy = step.y
  else return false
  const selected = widgets.filter((w) => selection.has(w.uuid))
  if (!selected.length) return false
  // with grid steps the widgets land on the grid (as dragging does); 1 px steps are exact
  const minX = Math.min(...selected.map((w) => finite(w.xPos, 0)))
  const minY = Math.min(...selected.map((w) => finite(w.yPos, 0)))
  if (minX + dx < 0) dx = -minX
  if (minY + dy < 0) dy = -minY
  if (!dx && !dy) return false
  for (const w of selected) {
    const x = finite(w.xPos, 0) + dx
    const y = finite(w.yPos, 0) + dy
    w.xPos = fine || !dx ? round(x) : snap(x, grid.x)
    w.yPos = fine || !dy ? round(y) : snap(y, grid.y)
  }
  return true
}

/** Rectangle of two corner points (rubber band). */
export function bandRect(a: { x: number; y: number }, b: { x: number; y: number }): Rect {
  return { x: Math.min(a.x, b.x), y: Math.min(a.y, b.y), width: Math.abs(a.x - b.x), height: Math.abs(a.y - b.y) }
}

export const intersects = (a: Rect, b: Rect) => a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height

/** uuids of the widgets touched by the rubber band. */
export function widgetsInRect(widgets: readonly RawWidget[], rect: Rect): number[] {
  return widgets.filter((w) => intersects(rectOf(w), rect)).map((w) => w.uuid)
}

/** New selection after a click: replace, or toggle the widget (Ctrl / Shift). */
export function clickSelection(selection: ReadonlySet<number>, uuid: number, toggle: boolean): Set<number> {
  if (!toggle) return selection.has(uuid) && selection.size > 1 ? new Set(selection) : new Set([uuid])
  const next = new Set(selection)
  if (next.has(uuid)) next.delete(uuid)
  else next.add(uuid)
  return next
}

/** Layer up / down (+1 / −1, not below 0) of the selected widgets. */
export function changeLayer(widgets: readonly RawWidget[], selection: ReadonlySet<number>, delta: 1 | -1): boolean {
  let changed = false
  for (const w of widgets) {
    if (!selection.has(w.uuid)) continue
    const layer = Math.max(0, Math.round(finite(w.layer, 0)) + delta)
    if (layer !== w.layer) {
      w.layer = layer
      changed = true
    }
  }
  return changed
}

/** Removes the selected widgets; references of the other widgets to them become "none" (−1). */
export function deleteWidgets(source: RawDashboard, selection: ReadonlySet<number>): number {
  const before = source.Widget.length
  source.Widget = source.Widget.filter((w) => !selection.has(w.uuid))
  clearReferences(source, selection)
  return before - source.Widget.length
}

/** References to other widgets: `limit.source`, `percent.source`, `"Time Frame".selectedWidget.id`. */
export function clearReferences(source: RawDashboard, removed: ReadonlySet<number>): void {
  const clear = (node: unknown, key: string) => {
    if (node && typeof node === 'object') {
      const o = node as Record<string, unknown>
      if (o[key] != null && removed.has(Number(o[key]))) o[key] = -1
    }
  }
  for (const w of source.Widget) {
    clear(w.limit, 'source')
    clear(w.percent, 'source')
    const tf = w['Time Frame'] as Record<string, unknown> | undefined
    clear(tf?.selectedWidget, 'id')
  }
}

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T

/**
 * Strg+D: copies of the selected widgets (uuid = max + 1, title + " #2", offset by one grid step), added on top of
 * the list. Returns the new uuids.
 */
export function duplicateWidgets(source: RawDashboard, selection: ReadonlySet<number>, grid: { x: number; y: number }): number[] {
  const added: number[] = []
  for (const w of source.Widget.filter((x) => selection.has(x.uuid))) {
    const copy = clone(w)
    copy.uuid = nextUuid(source)
    copy.title = (typeof w.title === 'string' ? w.title : '') + ' #2'
    copy.xPos = round(finite(w.xPos, 0) + grid.x)
    copy.yPos = round(finite(w.yPos, 0) + grid.y)
    source.Widget.push(copy)
    added.push(copy.uuid)
  }
  return added
}

/** Position for a new widget: the top left of the visible area, one grid step inside, on the grid. */
export function placeInView(view: { left: number; top: number }, grid: { x: number; y: number }): { x: number; y: number } {
  return { x: snap(Math.max(0, view.left) + grid.x, grid.x), y: snap(Math.max(0, view.top) + grid.y, grid.y) }
}
