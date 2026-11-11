/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { ColumnDef } from '../iso/columns'
import type { RegisterColumn, RegisterGroup, RegisterKind, RegisterRow } from '../api/registers'
import { attributeSortValue, formatAttribute, formatByPeriod, formatLast, mediumLabel, rowPeriod, verificationState, verificationText } from './format'

/*
 * Columns of the register tables: fixed ones per kind plus the attribute columns of the group (tab). `label` is the
 * text itself (attribute names come translated from the server). The column choice is stored per kind as changes
 * against the defaults, so it applies to every tab, also to columns that only some tabs have.
 */

export interface RegisterEnv {
  locale: string
  /** server date (verification state) */
  today: string
  t: (key: string) => string
  te: (key: string) => boolean
}

export interface RegisterColumnDef extends ColumnDef<RegisterRow> {
  /** shown without a stored choice */
  default: boolean
  /** value for the CSV file: numbers without formatting (default: the cell text) */
  csv?: (row: RegisterRow) => string | number | null
  /** the attribute column of the server (detail panel, files) */
  attribute?: RegisterColumn
}

export const ATTRIBUTE_PREFIX = 'a:'

const isNumber = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v)

function lastTimeSort(row: RegisterRow): string | null {
  return row.last?.timestamp ?? null
}

function fixedColumns(kind: RegisterKind, env: RegisterEnv): RegisterColumnDef[] {
  const { locale, t, te, today } = env
  const name: RegisterColumnDef = { id: 'name', label: t('registers.col.name'), default: true, text: (r) => r.name }
  const path: RegisterColumnDef = { id: 'path', label: t('registers.col.path'), default: true, text: (r) => r.path ?? '' }
  const last: RegisterColumnDef = {
    id: 'last',
    label: t('registers.col.last'),
    default: true,
    numeric: true,
    nowrap: true,
    text: (r) => formatLast(r.last, locale),
    sort: (r) => (isNumber(r.last?.value) ? r.last!.value : null),
    csv: (r) => (r.last?.value == null ? null : r.last.value),
  }
  const unit: RegisterColumnDef = { id: 'unit', label: t('registers.col.unit'), default: false, nowrap: true, text: (r) => r.last?.unit ?? '' }
  switch (kind) {
    case 'meters':
      return [
        name,
        { id: 'medium', label: t('registers.col.medium'), default: true, text: (r) => mediumLabel(r, t, te) },
        last,
        {
          id: 'lastTime',
          label: t('registers.col.lastTime'),
          default: true,
          nowrap: true,
          text: (r) => formatByPeriod(r.last?.timestamp, null, locale),
          sort: lastTimeSort,
        },
        {
          id: 'verification',
          label: t('registers.col.verification'),
          default: true,
          nowrap: true,
          text: (r) => verificationText(r, today, locale, t),
          sort: (r) => r.verification?.due ?? null,
          badge: (r) => verificationState(r, today),
          csv: (r) => r.verification?.due ?? null,
        },
        { ...path, default: false },
        unit,
      ]
    case 'basedata':
      return [
        name,
        path,
        last,
        {
          id: 'lastTime',
          label: t('registers.col.timestamp'),
          default: true,
          nowrap: true,
          text: (r) => formatByPeriod(r.last?.timestamp, rowPeriod(r), locale),
          sort: lastTimeSort,
        },
        unit,
      ]
    case 'equipment':
      return [path, name]
  }
}

/** Fixed columns of the kind, then the attribute columns of the group (as sorted by the server). */
export function registerColumns(kind: RegisterKind, group: Pick<RegisterGroup, 'columns'> | null | undefined, env: RegisterEnv): RegisterColumnDef[] {
  const attributes = (group?.columns ?? []).map<RegisterColumnDef>((column) => {
    const numeric = column.type === 'NUMBER' || column.type === 'INTEGER'
    return {
      id: ATTRIBUTE_PREFIX + column.key,
      label: column.unit && numeric ? `${column.name} [${column.unit}]` : column.name,
      default: column.visible !== false,
      numeric,
      nowrap: numeric || column.type === 'DATE' || column.type === 'DATETIME' || column.type === 'BOOLEAN',
      attribute: column,
      text: (r) => formatAttribute(r.values?.[column.key], { ...column, unit: numeric ? null : column.unit }, env.locale, env.t),
      sort: (r) => attributeSortValue(r.values?.[column.key]),
      csv: numeric ? (r) => (isNumber(r.values?.[column.key]) ? (r.values[column.key] as number) : null) : undefined,
    }
  })
  return [...fixedColumns(kind, env), ...attributes]
}

/** Columns the user has shown or hidden against the defaults. */
export interface ColumnChoice {
  shown: string[]
  hidden: string[]
}

const STORAGE_PREFIX = 'jevis.registers.columns.'

export function registerColumnKey(kind: RegisterKind): string {
  return STORAGE_PREFIX + kind
}

type Storage = Pick<globalThis.Storage, 'getItem' | 'setItem' | 'removeItem'>

function storage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

const strings = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : [])

export function emptyChoice(): ColumnChoice {
  return { shown: [], hidden: [] }
}

/** The stored choice of a kind; nothing stored or unreadable: none. */
export function loadColumnChoice(kind: RegisterKind, store: Storage | null = storage()): ColumnChoice {
  try {
    const raw = store?.getItem(registerColumnKey(kind))
    if (!raw) return emptyChoice()
    const parsed = JSON.parse(raw) as Partial<ColumnChoice> | null
    return { shown: strings(parsed?.shown), hidden: strings(parsed?.hidden) }
  } catch {
    return emptyChoice()
  }
}

/** Stores the choice; no changes remove the entry. Errors (private mode, quota) are ignored. */
export function saveColumnChoice(kind: RegisterKind, choice: ColumnChoice, store: Storage | null = storage()): void {
  try {
    if (!choice.shown.length && !choice.hidden.length) store?.removeItem(registerColumnKey(kind))
    else store?.setItem(registerColumnKey(kind), JSON.stringify(choice))
  } catch {
    /* only for this page */
  }
}

/** Visible column ids of a tab in column order; if the choice hides all, the defaults (or the first column). */
export function visibleColumnIds(columns: readonly Pick<RegisterColumnDef, 'id' | 'default'>[], choice: ColumnChoice): string[] {
  const shown = new Set(choice.shown)
  const hidden = new Set(choice.hidden)
  const ids = columns.filter((c) => (c.default ? !hidden.has(c.id) : shown.has(c.id))).map((c) => c.id)
  if (ids.length) return ids
  const defaults = columns.filter((c) => c.default).map((c) => c.id)
  return defaults.length ? defaults : columns.slice(0, 1).map((c) => c.id)
}

/** Shows or hides a column (`visible` = its current state). */
export function toggleColumnChoice(choice: ColumnChoice, column: Pick<RegisterColumnDef, 'id' | 'default'>, visible: boolean): ColumnChoice {
  const shown = choice.shown.filter((id) => id !== column.id)
  const hidden = choice.hidden.filter((id) => id !== column.id)
  if (visible && column.default) hidden.push(column.id)
  if (!visible && !column.default) shown.push(column.id)
  return { shown, hidden }
}
