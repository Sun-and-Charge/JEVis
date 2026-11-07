/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/** What a column needs to render its cells. */
export interface ColumnContext {
  locale: string
  t: (key: string, ...args: never[]) => string
}

export type SortValue = string | number | boolean | null | undefined

/** A column of the generic ISO list. `label` is an i18n key. */
export interface ColumnDef<R> {
  id: string
  label: string
  /** cell text */
  text: (row: R, ctx: ColumnContext) => string
  /** value to sort by (default: the cell text) */
  sort?: (row: R) => SortValue
  /** right aligned, tabular figures */
  numeric?: boolean
  /** no line breaks (dates, numbers) */
  nowrap?: boolean
  /** cell is a link to this external URL (rel=noopener) */
  href?: (row: R) => string | null
  /** cell is a state badge with the class is-<state> (text and colour) */
  badge?: (row: R) => string | null
}

const STORAGE_PREFIX = 'jevis.iso.columns.'

export function columnStorageKey(module: string): string {
  return STORAGE_PREFIX + module
}

type Storage = Pick<globalThis.Storage, 'getItem' | 'setItem' | 'removeItem'>

function storage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

/** Visible ids in the order of `all`; unknown ids are dropped. */
export function orderColumns(ids: readonly string[], all: readonly string[]): string[] {
  const wanted = new Set(ids)
  return all.filter((id) => wanted.has(id))
}

/** Stored visible columns of a module, or the defaults (nothing stored, unreadable, or nothing known left). */
export function loadColumnPrefs(module: string, all: readonly string[], defaults: readonly string[], store: Storage | null = storage()): string[] {
  const fallback = orderColumns(defaults, all)
  try {
    const raw = store?.getItem(columnStorageKey(module))
    if (!raw) return fallback
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return fallback
    const ids = orderColumns(
      parsed.filter((v): v is string => typeof v === 'string'),
      all,
    )
    return ids.length ? ids : fallback
  } catch {
    return fallback
  }
}

/** Stores the visible columns; the defaults remove the entry. Errors (private mode, quota) are ignored. */
export function saveColumnPrefs(module: string, visible: readonly string[], defaults: readonly string[], store: Storage | null = storage()): void {
  try {
    const same = visible.length === defaults.length && visible.every((id) => defaults.includes(id))
    if (same) store?.removeItem(columnStorageKey(module))
    else store?.setItem(columnStorageKey(module), JSON.stringify(visible))
  } catch {
    /* only for this page */
  }
}

/** Shows or hides a column; the last visible column stays. */
export function toggleColumn(visible: readonly string[], id: string, all: readonly string[]): string[] {
  if (visible.includes(id)) return visible.length > 1 ? visible.filter((v) => v !== id) : [...visible]
  return orderColumns([...visible, id], all)
}

export type SortDirection = 'asc' | 'desc'

function isEmpty(v: SortValue): v is null | undefined | '' {
  return v == null || v === '' || (typeof v === 'number' && Number.isNaN(v))
}

/** Compares sort values: numbers numerically, texts naturally (AP 2 before AP 10); empty values last in both directions. */
export function compareValues(a: SortValue, b: SortValue, direction: SortDirection, locale: string): number {
  const ea = isEmpty(a)
  const eb = isEmpty(b)
  if (ea || eb) return ea === eb ? 0 : ea ? 1 : -1
  let result: number
  if (typeof a === 'number' && typeof b === 'number') result = a - b
  else if (typeof a === 'boolean' && typeof b === 'boolean') result = Number(a) - Number(b)
  else result = String(a).localeCompare(String(b), locale, { numeric: true, sensitivity: 'base' })
  return direction === 'asc' ? result : -result
}

/** Rows sorted by a column (stable); unknown column: unchanged. */
export function sortRows<R>(rows: readonly R[], column: ColumnDef<R> | undefined, direction: SortDirection, ctx: ColumnContext): R[] {
  if (!column) return [...rows]
  const key = column.sort ?? ((row: R) => column.text(row, ctx))
  return rows
    .map((row, index) => ({ row, index, value: key(row) }))
    .sort((x, y) => compareValues(x.value, y.value, direction, ctx.locale) || x.index - y.index)
    .map((x) => x.row)
}
