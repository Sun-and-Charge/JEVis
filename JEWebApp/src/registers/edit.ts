/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { FileValue, RegisterColumn, RegisterFieldChanges, RegisterRow, RegisterValue } from '../api/registers'
import { parseUserNumber } from '../edit/edit'
import { isFileValue, isTargetValue } from './format'

/*
 * Editing of meters and equipment (E5): the attribute form generated from the columns of a group (field kind per
 * column type / primitive type / GUI type), form values read from a row, the conversion to request values (numbers
 * with the decimal comma, dates yyyy-MM-dd, targets { id, attribute }), the diff with the versions of the changed
 * attributes, checks before sending, the name rule and the checks of a meter exchange.
 */

export type RegisterFieldKind = 'text' | 'textarea' | 'number' | 'integer' | 'boolean' | 'date' | 'target' | 'file' | 'readonly'

export interface RegisterFieldDef {
  /** attribute name = request key */
  key: string
  /** translated name */
  label: string
  kind: RegisterFieldKind
  /** input unit shown next to number fields */
  inputUnit: string | null
  /** the column as delivered (read-only display uses its formatting) */
  column: RegisterColumn
}

/** A target as edited: id and attribute, the name for display. */
export interface TargetInput {
  id: number
  attribute: string | null
  name: string | null
  readable?: boolean
}

/** text / textarea / number / integer / date: string ('' = empty); boolean; target (null = none); file and read-only: null. */
export type RegisterFormValue = string | boolean | TargetInput | null
export type RegisterFormValues = Record<string, RegisterFormValue>

/* types the web never edits, whatever `editable` says */
const NEVER_EDITED_PRIMITIVES = new Set(['PASSWORD_PBKDF2', 'SELECTION', 'MULTI_SELECTION'])
const NEVER_EDITED_GUI = new Set(['GPS'])
const DATE_GUI = new Set(['BASIC_TEXT_DATE_FULL', 'DATE'])
const TEXTAREA_GUI = new Set(['BASIC_TEXT_MULTI', 'TEXT_AREA'])
const TARGET_GUI = new Set(['TARGET_OBJECT', 'TARGET_ATTRIBUTE'])

const upper = (v: string | null | undefined) => (v ?? '').toUpperCase()

/**
 * Field kind of a column. FILE and TARGET are recognised from the column type, the primitive type or the GUI type;
 * passwords, selections and GPS (and every column with `editable: false`) are read-only; numbers by primitive
 * (DOUBLE / LONG) or column type (NUMBER / INTEGER); dates by GUI type or DATE / DATETIME columns.
 */
export function fieldKind(column: RegisterColumn): RegisterFieldKind {
  const type = upper(column.type)
  const primitive = upper(column.primitiveType)
  const gui = upper(column.guiType)
  // GPS is stored as a FILE attribute but edited only in the Control Center (map editor); the server refuses uploads
  if ((type === 'FILE' || primitive === 'FILE') && column.editable !== false && !NEVER_EDITED_GUI.has(gui)) return 'file'
  if (column.editable === false || NEVER_EDITED_PRIMITIVES.has(primitive) || NEVER_EDITED_GUI.has(gui)) return 'readonly'
  if (type === 'TARGET' || primitive === 'TARGET' || TARGET_GUI.has(gui)) return 'target'
  if (type === 'BOOLEAN' || primitive === 'BOOLEAN') return 'boolean'
  if (DATE_GUI.has(gui) || type === 'DATE' || type === 'DATETIME') return 'date'
  if (primitive === 'DOUBLE' || (!primitive && type === 'NUMBER')) return 'number'
  if (primitive === 'LONG' || (!primitive && type === 'INTEGER')) return 'integer'
  if (TEXTAREA_GUI.has(gui) || type === 'TEXTAREA') return 'textarea'
  if (!primitive || primitive === 'STRING') return 'text'
  /* an unknown primitive type: shown, not edited */
  return 'readonly'
}

/** The form fields of a group's columns (in their order); the row's own input units win over the column's. */
export function registerFieldDefs(columns: readonly RegisterColumn[], row?: Pick<RegisterRow, 'inputUnits'> | null): RegisterFieldDef[] {
  return columns.map((column) => {
    const own = row?.inputUnits && Object.prototype.hasOwnProperty.call(row.inputUnits, column.key) ? row.inputUnits[column.key] : undefined
    return {
      key: column.key,
      label: column.name || column.key,
      kind: fieldKind(column),
      inputUnit: (own !== undefined ? own : column.inputUnit) || null,
      column,
    }
  })
}

/** True if the field is changed by the attribute form (files are uploaded separately, read-only fields never). */
export const isFormField = (def: RegisterFieldDef) => def.kind !== 'file' && def.kind !== 'readonly'

const DATE = /^(\d{4}-\d{2}-\d{2})/

/** A number as typed in the form: no thousands separators, decimal comma in German. */
export function numberInput(value: number, locale: string): string {
  if (!Number.isFinite(value)) return ''
  const text = String(value)
  return locale.startsWith('de') ? text.replace('.', ',') : text
}

function booleanOf(value: RegisterValue | undefined): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value === 1
  if (typeof value === 'string') return ['true', '1', 'yes', 'ja'].includes(value.trim().toLowerCase())
  return false
}

/** Form value of a stored value. */
export function formValue(def: RegisterFieldDef, value: RegisterValue | undefined, locale: string): RegisterFormValue {
  switch (def.kind) {
    case 'file':
    case 'readonly':
      return null
    case 'boolean':
      return booleanOf(value)
    case 'target':
      return isTargetValue(value) ? { id: value.id, attribute: value.attribute ?? null, name: value.name ?? null, readable: value.readable } : null
    case 'date': {
      const m = typeof value === 'string' ? DATE.exec(value) : null
      return m ? m[1] : ''
    }
    case 'number':
    case 'integer':
      if (typeof value === 'number') return numberInput(value, locale)
      return value == null ? '' : String(value)
    default:
      if (value == null || isFileValue(value) || typeof value === 'object') return ''
      return String(value)
  }
}

/** Form values of a row (a new object: from empty values). */
export function rowFormValues(defs: readonly RegisterFieldDef[], row: Pick<RegisterRow, 'values'> | null, locale: string): RegisterFormValues {
  return Object.fromEntries(defs.map((d) => [d.key, formValue(d, row?.values?.[d.key], locale)]))
}

/** Copy of the values (targets are not shared). */
export function cloneFormValues(values: RegisterFormValues): RegisterFormValues {
  const copy: RegisterFormValues = {}
  for (const [k, v] of Object.entries(values)) copy[k] = v && typeof v === 'object' ? { ...v } : v
  return copy
}

/**
 * Value as sent: text trimmed (empty = empty string), multi-line text as typed, numbers parsed with the decimal
 * comma (empty = null), integers whole, booleans, dates yyyy-MM-dd (empty = null), targets { id, attribute } (none = null).
 * `undefined` for a value that cannot be sent (invalid number; the checks catch it before).
 */
export function requestValue(def: RegisterFieldDef, value: RegisterFormValue, locale: string): unknown {
  switch (def.kind) {
    case 'text':
      return String(value ?? '').trim()
    case 'textarea':
      return String(value ?? '')
    case 'boolean':
      return value === true
    case 'date': {
      const text = String(value ?? '').trim()
      return text ? text : null
    }
    case 'number':
    case 'integer': {
      const text = String(value ?? '').trim()
      if (!text) return null
      const n = parseUserNumber(text, locale)
      if (n == null || (def.kind === 'integer' && !Number.isInteger(n))) return undefined
      return n
    }
    case 'target': {
      const target = value && typeof value === 'object' ? value : null
      return target ? { id: target.id, attribute: target.attribute || 'Value' } : null
    }
    default:
      return undefined
  }
}

/** True if two form values of a field are equal (numbers by value, texts ignoring outer spaces, targets by id and attribute). */
export function sameFormValue(def: RegisterFieldDef, a: RegisterFormValue, b: RegisterFormValue, locale: string): boolean {
  switch (def.kind) {
    case 'boolean':
      return !!a === !!b
    case 'target': {
      const x = a && typeof a === 'object' ? a : null
      const y = b && typeof b === 'object' ? b : null
      if (!x || !y) return !x && !y
      return x.id === y.id && (x.attribute || 'Value') === (y.attribute || 'Value')
    }
    case 'number':
    case 'integer': {
      const x = String(a ?? '').trim()
      const y = String(b ?? '').trim()
      if (x === y) return true
      const nx = parseUserNumber(x, locale)
      const ny = parseUserNumber(y, locale)
      return nx != null && ny != null && nx === ny
    }
    case 'textarea':
      return String(a ?? '') === String(b ?? '')
    case 'file':
    case 'readonly':
      return true
    default:
      return String(a ?? '').trim() === String(b ?? '').trim()
  }
}

/** Keys of the form fields whose value differs from the loaded one. */
export function changedKeys(defs: readonly RegisterFieldDef[], initial: RegisterFormValues, current: RegisterFormValues, locale: string): string[] {
  return defs.filter((d) => isFormField(d) && !sameFormValue(d, initial[d.key] ?? null, current[d.key] ?? null, locale)).map((d) => d.key)
}

/**
 * The attributes to send: only the changed form fields, each with the version it was loaded with (null if the
 * attribute had no sample; a new object has no versions).
 */
export function changedRegisterFields(
  defs: readonly RegisterFieldDef[],
  initial: RegisterFormValues,
  current: RegisterFormValues,
  versions: Readonly<Record<string, string | null>> | null | undefined,
  locale: string,
): RegisterFieldChanges {
  const result: RegisterFieldChanges = {}
  for (const key of changedKeys(defs, initial, current, locale)) {
    const def = defs.find((d) => d.key === key)!
    const value = requestValue(def, current[key] ?? null, locale)
    if (value === undefined) continue
    result[key] = { version: versions?.[key] ?? null, value }
  }
  return result
}

export interface RegisterProblem {
  key: string
  params?: Record<string, unknown>
}

/** Checks of the changed number fields (a number; integers whole). */
export function validateRegisterValues(
  defs: readonly RegisterFieldDef[],
  initial: RegisterFormValues,
  current: RegisterFormValues,
  locale: string,
): Record<string, RegisterProblem> {
  const problems: Record<string, RegisterProblem> = {}
  for (const key of changedKeys(defs, initial, current, locale)) {
    const def = defs.find((d) => d.key === key)!
    if (def.kind !== 'number' && def.kind !== 'integer') continue
    const text = String(current[key] ?? '').trim()
    if (!text) continue
    const n = parseUserNumber(text, locale)
    if (n == null) problems[key] = { key: 'registerEdit.problem.number' }
    else if (def.kind === 'integer' && !Number.isInteger(n)) problems[key] = { key: 'registerEdit.problem.integer' }
  }
  return problems
}

/**
 * After a 409 with "keep editing": the fields the user changed keep the user's values, all others take the current
 * values of the server, which become the new base of the diff.
 */
export function mergeAfterConflict(
  defs: readonly RegisterFieldDef[],
  initial: RegisterFormValues,
  current: RegisterFormValues,
  server: RegisterFormValues,
  locale: string,
): RegisterFormValues {
  const mine = new Set(changedKeys(defs, initial, current, locale))
  const merged = cloneFormValues(server)
  for (const key of mine) merged[key] = current[key] && typeof current[key] === 'object' ? { ...(current[key] as TargetInput) } : current[key]
  return merged
}

/* ------------------------------------------------------------------ name */

export const NAME_MAX = 100
const CONTROL = /[\u0000-\u001f\u007f]/

/** Problem of an object name (trimmed 1–100 characters, no control characters), null if fine. */
export function nameProblem(name: string): RegisterProblem | null {
  const text = (name ?? '').trim()
  if (!text) return { key: 'registerEdit.problem.nameRequired' }
  if (text.length > NAME_MAX) return { key: 'registerEdit.problem.nameLength', params: { max: NAME_MAX } }
  if (CONTROL.test(text)) return { key: 'registerEdit.problem.nameChars' }
  return null
}

/* ------------------------------------------------------------------ versions, delete */

/** The row's latest change: the newest of its versions (sent with a delete), null if it has none. */
export function latestVersion(row: Pick<RegisterRow, 'versions'>): string | null {
  let best: string | null = null
  let bestMs = -Infinity
  for (const v of Object.values(row.versions ?? {})) {
    if (!v) continue
    const ms = Date.parse(v)
    const key = Number.isNaN(ms) ? -Infinity : ms
    if (best === null || key > bestMs || (key === bestMs && v > best)) {
      best = v
      bestMs = key
    }
  }
  return best
}

/** True if the user may change anything in the list (else the read-only notice stays). */
export function anyRegisterWritable(groups: readonly { canCreate?: boolean; rows: readonly RegisterRow[] }[]): boolean {
  return groups.some((g) => !!g.canCreate || g.rows.some((r) => !!(r.canWrite || r.canDelete || r.canUpload)))
}

/** The file value of a row's attribute, null if none. */
export function fileOf(row: Pick<RegisterRow, 'values'> | null | undefined, key: string): FileValue | null {
  const v = row?.values?.[key]
  return isFileValue(v) ? v : null
}

/** A row with an uploaded file and its new version (the list is reloaded afterwards anyway). */
export function withUploadedFile(row: RegisterRow, attribute: string, file: FileValue, version: string | null): RegisterRow {
  return {
    ...row,
    values: { ...row.values, [attribute]: { file: file.file, image: file.image } },
    versions: { ...(row.versions ?? {}), [attribute]: version },
  }
}

/* ------------------------------------------------------------------ meter exchange */

export interface ExchangeInput {
  /** datetime-local value */
  when: string
  oldReading: string
  newReading: string
}

export interface ExchangeValues {
  timestamp: number
  oldReading: number
  newReading: number
}

/**
 * Checks of the meter exchange: a valid time not in the future (to the minute), both readings numbers ≥ 0.
 * Returns the problems per field and, without problems, the values.
 */
export function checkExchange(
  input: ExchangeInput,
  locale: string,
  now: number,
  parseTime: (value: string) => number | null,
): { problems: Record<keyof ExchangeInput, RegisterProblem | undefined>; values: ExchangeValues | null } {
  const problems: Record<keyof ExchangeInput, RegisterProblem | undefined> = { when: undefined, oldReading: undefined, newReading: undefined }
  const time = parseTime(input.when)
  if (time == null) problems.when = { key: 'registerEdit.exchange.problem.time' }
  else if (time > now) problems.when = { key: 'registerEdit.exchange.problem.future' }
  const reading = (text: string): number | null => {
    const n = parseUserNumber(text, locale)
    return n == null || n < 0 ? null : n
  }
  const oldReading = reading(input.oldReading)
  const newReading = reading(input.newReading)
  if (oldReading == null) problems.oldReading = { key: 'registerEdit.exchange.problem.reading' }
  if (newReading == null) problems.newReading = { key: 'registerEdit.exchange.problem.reading' }
  const ok = !problems.when && !problems.oldReading && !problems.newReading
  return { problems, values: ok ? { timestamp: time!, oldReading: oldReading!, newReading: newReading! } : null }
}

/** Warnings of a 409 answer (plausibility of E1), [] if none. */
export function warningsOf(body: unknown): string[] {
  const list = body && typeof body === 'object' ? (body as { warnings?: unknown }).warnings : null
  return Array.isArray(list) ? list.filter((w): w is string => typeof w === 'string' && !!w.trim()) : []
}

/** The current row of a 409 / save answer if it is the expected object, else null. */
export function rowOf(body: unknown, id: number): RegisterRow | null {
  return body && typeof body === 'object' && (body as RegisterRow).id === id && typeof (body as RegisterRow).values === 'object' ? (body as RegisterRow) : null
}
