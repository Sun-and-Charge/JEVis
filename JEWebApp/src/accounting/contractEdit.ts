/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { ContractAttribute, ContractGroup, ContractObject, ContractUpdate, ContractUsage, RangingStep } from '../api/contracts'
import { HttpError } from '../api/http'
import { parseNumber } from '../readings/expected'
import { NAME_MAX, nameProblem } from '../registers/edit'

/*
 * Editing of contract partners, prices and governmental dues (E6.2), like the Control Center's "Enter data" tab of
 * the Accounting Plugin: the form of an object (name, contractor, text attributes, ranging tables), the checks of a
 * ranging table as the server does them, the "new value" of a price and the answers of a 409.
 */

/** Upper bound of the last range when it is "unbegrenzt" (as the demo data). */
export const UNLIMITED = 1e9
/** Rows of a ranging table (server: 1–50). */
export const RANGING_MAX = 50

export { nameProblem, NAME_MAX }

export interface Problem {
  key: string
  params?: Record<string, unknown>
}

/* ------------------------------------------------------------------ rights */

/** The object can be opened in the editor ("Bearbeiten"). */
export const canEditObject = (o: Pick<ContractObject, 'canWrite' | 'canDelete'>) => o.canWrite === true || o.canDelete === true

/** Anything on the page can be changed (then the read-only notice is replaced). */
export function anyContractWritable(groups: readonly ContractGroup[] | null | undefined): boolean {
  return (groups ?? []).some((g) => canCreateIn(g) || (g.objects ?? []).some((o) => canEditObject(o)))
}

/** "Neu" is offered: the right and at least one class and directory. */
export const canCreateIn = (g: Pick<ContractGroup, 'canCreate' | 'classes' | 'createTargets'>) =>
  g.canCreate === true && !!g.classes?.length && !!g.createTargets?.length

/* ------------------------------------------------------------------ numbers */

const pad = (n: number, len = 2) => String(n).padStart(len, '0')

/** A number as the text of an input: no thousands separators, decimal comma in German; '' if none. */
export function inputNumber(value: number | null | undefined, locale: string): string {
  if (value == null || !Number.isFinite(value)) return ''
  const text = Math.abs(value) >= 1e21 || (value !== 0 && Math.abs(value) < 1e-6) ? String(value) : String(Number(value.toPrecision(15)))
  return locale.startsWith('de') ? text.replace('.', ',') : text
}

/** Default "gültig ab" of a new value: the 1st of the next month (yyyy-MM-dd, local). */
export function firstOfNextMonth(now: Date = new Date()): string {
  const d = new Date(now.getFullYear(), now.getMonth() + 1, 1)
  return `${pad(d.getFullYear(), 4)}-${pad(d.getMonth() + 1)}-01`
}

/** True for a real calendar day yyyy-MM-dd. */
export function isDay(text: string | null | undefined): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text ?? '')
  if (!m) return false
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  return d.getFullYear() === Number(m[1]) && d.getMonth() === Number(m[2]) - 1 && d.getDate() === Number(m[3])
}

export interface NewValueCheck {
  value: number | null
  valueProblem: Problem | null
  dateProblem: Problem | null
}

/** Checks "Neuer Wert": a number ("," or "."), an integer for LONG attributes, and a valid-from day. */
export function checkNewValue(text: string, validFrom: string, primitive: string | null | undefined): NewValueCheck {
  let valueProblem: Problem | null = null
  const value = parseNumber(text)
  if (!(text ?? '').trim()) valueProblem = { key: 'contractEdit.problem.valueRequired' }
  else if (value == null) valueProblem = { key: 'registerEdit.problem.number' }
  else if (primitive === 'LONG' && !Number.isInteger(value)) valueProblem = { key: 'registerEdit.problem.integer' }
  const dateProblem = isDay(validFrom) ? null : { key: 'contractEdit.problem.date' }
  return { value: valueProblem ? null : value, valueProblem, dateProblem }
}

/* ------------------------------------------------------------------ ranging tables */

export interface RangingFormRow {
  from: string
  to: string
  value: string
  /** last row up to {@link UNLIMITED} */
  unlimited: boolean
}

/** Rows of the table editor, sorted by their start; an open or 1e9 end is "unbegrenzt". */
export function rangingFormRows(steps: readonly RangingStep[] | null | undefined, locale: string): RangingFormRow[] {
  return [...(Array.isArray(steps) ? steps : [])]
    .filter((s) => s && typeof s === 'object')
    .map((s, i) => ({ s, i }))
    .sort((a, b) => (a.s.from ?? -Infinity) - (b.s.from ?? -Infinity) || a.i - b.i)
    .map(({ s }) => {
      const unlimited = s.to == null || s.to >= UNLIMITED
      return { from: inputNumber(s.from, locale), to: unlimited ? '' : inputNumber(s.to, locale), value: inputNumber(s.value, locale), unlimited }
    })
}

/** A new row after the last one: starts at its end; an "unbegrenzt" end moves to the new row. */
export function addRangingRow(rows: readonly RangingFormRow[]): RangingFormRow[] {
  const next = rows.map((r) => ({ ...r }))
  const last = next.at(-1)
  if (!last) return [{ from: '0', to: '', value: '', unlimited: true }]
  const unlimited = last.unlimited
  last.unlimited = false
  return [...next, { from: unlimited ? '' : last.to, to: '', value: '', unlimited }]
}

/** Removes a row; "unbegrenzt" of a removed last row goes to the new last row. */
export function removeRangingRow(rows: readonly RangingFormRow[], index: number): RangingFormRow[] {
  const next = rows.filter((_, i) => i !== index).map((r) => ({ ...r }))
  if (index === rows.length - 1 && rows[index]?.unlimited && next.length) {
    next[next.length - 1].unlimited = true
    next[next.length - 1].to = ''
  }
  return next
}

export type RangingField = 'from' | 'to' | 'value'

export interface RangingProblem extends Problem {
  /** row index; null for the whole table */
  row: number | null
  field?: RangingField
}

export interface RangingCheck {
  steps: { from: number; to: number; value: number }[] | null
  problems: RangingProblem[]
}

/** Checks as the server: numbers, from < to, sorted and not overlapping, 1–50 rows; `steps` only without problems. */
export function checkRanging(rows: readonly RangingFormRow[]): RangingCheck {
  const problems: RangingProblem[] = []
  if (!rows.length) problems.push({ row: null, key: 'contractEdit.ranging.problem.empty' })
  if (rows.length > RANGING_MAX) problems.push({ row: null, key: 'contractEdit.ranging.problem.tooMany', params: { max: RANGING_MAX } })
  const steps: { from: number; to: number; value: number }[] = []
  let complete = true
  rows.forEach((r, row) => {
    const num = (field: RangingField, text: string): number | null => {
      if (!(text ?? '').trim()) {
        problems.push({ row, field, key: 'contractEdit.ranging.problem.required' })
        return null
      }
      const n = parseNumber(text)
      if (n == null) problems.push({ row, field, key: 'registerEdit.problem.number' })
      return n
    }
    const from = num('from', r.from)
    const to = r.unlimited ? UNLIMITED : num('to', r.to)
    const value = num('value', r.value)
    if (from == null || to == null || value == null) {
      complete = false
      return
    }
    if (from >= to) problems.push({ row, field: 'to', key: 'contractEdit.ranging.problem.order' })
    steps.push({ from, to, value })
  })
  if (complete) {
    for (let i = 1; i < steps.length; i++) {
      if (steps[i].from < steps[i - 1].from) problems.push({ row: i, field: 'from', key: 'contractEdit.ranging.problem.sorted' })
      else if (steps[i].from < steps[i - 1].to) problems.push({ row: i, field: 'from', key: 'contractEdit.ranging.problem.overlap' })
    }
  }
  return { steps: problems.length ? null : steps, problems }
}

/** Comparable key of a table (parsed numbers, the text where it is no number). */
function rangingKey(rows: readonly RangingFormRow[]): string {
  const v = (text: string) => parseNumber(text) ?? (text ?? '').trim()
  return JSON.stringify(rows.map((r) => [v(r.from), r.unlimited ? UNLIMITED : v(r.to), v(r.value)]))
}

/* ------------------------------------------------------------------ the form */

/** Attributes edited in the form (texts and ranging tables that are editable); numbers get "Neuer Wert". */
export const isFormAttribute = (a: ContractAttribute) => a.editable !== false && (a.type === 'text' || a.type === 'ranging')
export const isPriceAttribute = (a: ContractAttribute) => a.type === 'number'

export interface ContractForm {
  name: string
  /** chosen contractor id; null: none */
  contractor: number | null
  texts: Record<string, string>
  ranging: Record<string, RangingFormRow[]>
}

export function contractForm(o: ContractObject, locale: string): ContractForm {
  const form: ContractForm = { name: o.name ?? '', contractor: o.contractor?.id ?? null, texts: {}, ranging: {} }
  for (const a of o.attributes ?? []) {
    if (!isFormAttribute(a)) continue
    if (a.type === 'ranging') form.ranging[a.name] = rangingFormRows(Array.isArray(a.value) ? a.value : [], locale)
    else form.texts[a.name] = a.value == null ? '' : String(a.value)
  }
  return form
}

export function cloneForm(f: ContractForm): ContractForm {
  return {
    name: f.name,
    contractor: f.contractor,
    texts: { ...f.texts },
    ranging: Object.fromEntries(Object.entries(f.ranging).map(([k, rows]) => [k, rows.map((r) => ({ ...r }))])),
  }
}

export interface FormChanges {
  name: boolean
  contractor: boolean
  texts: string[]
  ranging: string[]
}

export function formChanges(initial: ContractForm, current: ContractForm): FormChanges {
  return {
    name: current.name.trim() !== initial.name.trim(),
    contractor: current.contractor !== initial.contractor,
    texts: Object.keys(current.texts).filter((k) => (current.texts[k] ?? '') !== (initial.texts[k] ?? '')),
    ranging: Object.keys(current.ranging).filter((k) => rangingKey(current.ranging[k] ?? []) !== rangingKey(initial.ranging[k] ?? [])),
  }
}

export const hasChanges = (c: FormChanges) => c.name || c.contractor || c.texts.length > 0 || c.ranging.length > 0

export interface FormCheck {
  /** the PUT body without the version; null with problems or without changes */
  body: Omit<ContractUpdate, 'version'> | null
  nameProblem: Problem | null
  ranging: Record<string, RangingProblem[]>
  changed: boolean
}

/** Checks the form and builds the changed parts of the PUT body. */
export function checkForm(initial: ContractForm, current: ContractForm): FormCheck {
  const changes = formChanges(initial, current)
  const name = changes.name ? nameProblem(current.name) : null
  const ranging: Record<string, RangingProblem[]> = {}
  const fields: NonNullable<ContractUpdate['fields']> = {}
  for (const k of changes.texts) fields[k] = current.texts[k] ?? ''
  for (const k of changes.ranging) {
    const check = checkRanging(current.ranging[k] ?? [])
    if (check.problems.length) ranging[k] = check.problems
    else fields[k] = check.steps!
  }
  const changed = hasChanges(changes)
  if (name || Object.keys(ranging).length || !changed) return { body: null, nameProblem: name, ranging, changed }
  const body: Omit<ContractUpdate, 'version'> = {}
  if (changes.name) body.name = current.name.trim()
  if (changes.contractor) body.contractor = current.contractor
  if (Object.keys(fields).length) body.fields = fields
  return { body, nameProblem: null, ranging, changed }
}

/** After a 409 "Weiter bearbeiten" or a reload: the server's form with the own changes kept. */
export function mergeForm(initial: ContractForm, current: ContractForm, server: ContractForm): ContractForm {
  const c = formChanges(initial, current)
  const merged = cloneForm(server)
  if (c.name) merged.name = current.name
  if (c.contractor) merged.contractor = current.contractor
  for (const k of c.texts) if (k in merged.texts) merged.texts[k] = current.texts[k]
  for (const k of c.ranging) if (k in merged.ranging) merged.ranging[k] = current.ranging[k].map((r) => ({ ...r }))
  return merged
}

/* ------------------------------------------------------------------ answers */

/** The object of a 409 answer (the current object), null if the body is none. */
export function objectOf(json: unknown, id: number): ContractObject | null {
  if (!json || typeof json !== 'object') return null
  const o = json as Partial<ContractObject>
  return o.id === id && Array.isArray(o.attributes) ? (o as ContractObject) : null
}

function json409(e: unknown): Record<string, unknown> | null {
  if (!(e instanceof HttpError) || e.status !== 409) return null
  const j = e.json<unknown>()
  return j && typeof j === 'object' && !Array.isArray(j) ? (j as Record<string, unknown>) : null
}

/** "usedBy" of a 409 delete answer; null if the 409 has none (then it is a version conflict). */
export function usedByOf(e: unknown): ContractUsage[] | null {
  const list = json409(e)?.usedBy
  if (!Array.isArray(list)) return null
  return list.filter((u): u is ContractUsage => !!u && typeof u === 'object' && typeof (u as ContractUsage).id === 'number')
}

/** Overwrite warnings of a 409 value answer; null if none. */
export function warningsOf(e: unknown): string[] | null {
  const list = json409(e)?.warnings
  if (!Array.isArray(list)) return null
  const texts = list.filter((w): w is string => typeof w === 'string' && !!w.trim())
  return texts.length ? texts : null
}
