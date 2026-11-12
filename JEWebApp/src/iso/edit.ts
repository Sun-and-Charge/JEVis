/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { HttpError } from '../api/http'
import type { Action, ActionPlan, CheckList, IsoFields, IsoItem, IsoKind, LegalPlan, Nonconformity, NonconformityPlan, Obligation } from '../api/iso'
import { splitTags } from './filter'

/*
 * Editing of action plans, nonconformities and the legal register (E2): the editable fields per module (request keys
 * = the stored JSON keys), form values read from a list item, the diff that is sent (only changed fields), checks of
 * required fields and plan lists, and small list updates after a write.
 */

export type IsoPlan = ActionPlan | NonconformityPlan | LegalPlan

export type FieldKind = 'text' | 'textarea' | 'date' | 'select' | 'multi' | 'bool' | 'checks' | 'link'

/** Form value: text / date (yyyy-MM-dd, '' = empty) / select ('' = none), multi select, boolean, check list. */
export type FormValue = string | string[] | boolean | Record<string, boolean>
export type FormValues = Record<string, FormValue>

export interface FieldOption {
  value: string
  label: string
  /** a stored value that is no longer in the plan's list (shown and kept unless changed) */
  stale?: boolean
}

/** One entry of a check list: stored key (sent) and key of the read answer (label `<labels>.<webKey>`). */
export interface CheckDef {
  key: string
  webKey: string
}

export type FormSection = 'general' | 'notes' | 'checkList'

export interface FieldDef {
  /** request key = stored JSON key */
  key: string
  /** i18n key of the label */
  label: string
  kind: FieldKind
  section: FormSection
  required?: boolean
  /** select / multi: the plan's list */
  options?: (plan: IsoPlan) => FieldOption[]
  /** checks: entries and the i18n prefix of their labels */
  checks?: readonly CheckDef[]
  checkLabels?: string
  /** form value of a list item */
  read: (item: IsoItem) => FormValue
}

/** Check list of actions: stored key → key of the read answer (CheckLists in JECommons); booleans only. */
export const ACTION_CHECKS: readonly CheckDef[] = [
  { key: 'Process Document', webKey: 'processDocument' },
  { key: 'isNeedWorkInstruction', webKey: 'workInstruction' },
  { key: 'Test Instruction', webKey: 'testInstruction' },
  { key: 'Drawing', webKey: 'drawing' },
  { key: 'Other', webKey: 'other' },
  { key: 'Additional Action', webKey: 'additionalAction' },
  { key: 'Affect Others', webKey: 'affectsOthers' },
  { key: 'Consumption Documented', webKey: 'consumptionDocumented' },
  { key: 'Target Reached', webKey: 'targetReached' },
  { key: 'Document Correction', webKey: 'documentCorrection' },
  { key: 'Document Correction Text', webKey: 'documentCorrectionText' },
  { key: 'Additional Meter', webKey: 'additionalMeter' },
]

/** Check list of nonconformities: stored key → key of the read answer. */
export const NONCONFORMITY_CHECKS: readonly CheckDef[] = [
  { key: 'Immediate Action Required', webKey: 'immediateActionRequired' },
  { key: 'Effect on ongoing Processes', webKey: 'effectOnOngoingProcesses' },
  { key: 'Routinely Affected', webKey: 'routinelyAffected' },
  { key: 'Employee Trained', webKey: 'employeeTrained' },
  { key: 'Documents Changes Needed', webKey: 'documentsChangesNeeded' },
  { key: 'Process Instructions', webKey: 'processInstructions' },
  { key: 'Work Instructions', webKey: 'workInstructions' },
  { key: 'Test Instructions', webKey: 'testInstructions' },
  { key: 'Design', webKey: 'design' },
  { key: 'Model', webKey: 'model' },
  { key: 'Miscellaneous', webKey: 'miscellaneous' },
  { key: 'Metrics', webKey: 'metrics' },
]

const DATE = /^(\d{4}-\d{2}-\d{2})/

/** Value of an `<input type="date">` for a stored date (the day as written), '' if none. */
export function dateInput(value: string | null | undefined): string {
  const m = value ? DATE.exec(value) : null
  return m ? m[1] : ''
}

const text = (v: string | null | undefined): string => v ?? ''
const plain = (values: readonly (string | null)[] | null | undefined): FieldOption[] =>
  splitTags(values ?? []).map((v) => ({ value: v, label: v }))

function checkValues(list: CheckList | null | undefined, checks: readonly CheckDef[]): Record<string, boolean> {
  const result: Record<string, boolean> = {}
  for (const c of checks) result[c.key] = list?.[c.webKey] === true
  return result
}

const act = (fn: (a: Action) => FormValue) => (i: IsoItem) => fn(i as Action)
const nc = (fn: (n: Nonconformity) => FormValue) => (i: IsoItem) => fn(i as Nonconformity)
const ob = (fn: (o: Obligation) => FormValue) => (i: IsoItem) => fn(i as Obligation)

export const ACTION_STATUS_KEY = 'Status Tags'
export const ACTION_DONE_DATE_KEY = 'Done Date'

export const ACTION_FIELDS: readonly FieldDef[] = [
  { key: 'Title', label: 'iso.actions.col.title', kind: 'text', section: 'general', required: true, read: act((a) => text(a.title)) },
  { key: 'Status Tags', label: 'iso.actions.col.status', kind: 'select', section: 'general', options: (p) => plain((p as ActionPlan).statuses), read: act((a) => text(a.status)) },
  { key: 'Responsible', label: 'iso.actions.col.responsible', kind: 'text', section: 'general', read: act((a) => text(a.responsible)) },
  { key: 'Distributor', label: 'iso.actions.col.distributor', kind: 'text', section: 'general', read: act((a) => text(a.distributor)) },
  {
    key: 'Medium Tags',
    label: 'iso.actions.col.medium',
    kind: 'select',
    section: 'general',
    options: (p) => ((p as ActionPlan).media ?? []).filter((m) => !!m.id).map((m) => ({ value: m.id, label: m.name || m.id })),
    read: act((a) => text(a.medium)),
  },
  { key: 'Field Tags', label: 'iso.actions.col.field', kind: 'multi', section: 'general', options: (p) => plain((p as ActionPlan).fields), read: act((a) => splitTags(a.field)) },
  { key: 'SEU Tags', label: 'iso.actions.col.seu', kind: 'multi', section: 'general', options: (p) => plain((p as ActionPlan).seus), read: act((a) => splitTags(a.seu)) },
  { key: 'Planned Date', label: 'iso.actions.col.plannedDate', kind: 'date', section: 'general', read: act((a) => dateInput(a.plannedDate)) },
  { key: 'Done Date', label: 'iso.actions.col.doneDate', kind: 'date', section: 'general', read: act((a) => dateInput(a.doneDate)) },
  { key: 'Description', label: 'iso.actions.col.description', kind: 'textarea', section: 'general', read: act((a) => text(a.description)) },
  { key: 'Note', label: 'iso.actions.col.note', kind: 'textarea', section: 'notes', read: act((a) => text(a.note)) },
  { key: 'Correction', label: 'iso.actions.notes.correction', kind: 'textarea', section: 'notes', read: act((a) => text(a.correction)) },
  { key: 'Follow Action', label: 'iso.actions.notes.followAction', kind: 'textarea', section: 'notes', read: act((a) => text(a.followAction)) },
  { key: 'Alternative Measures', label: 'iso.actions.notes.alternativeMeasures', kind: 'textarea', section: 'notes', read: act((a) => text(a.alternativeMeasures)) },
  { key: 'Affected Process Note', label: 'iso.actions.notes.affectedProcess', kind: 'textarea', section: 'notes', read: act((a) => text(a.affectedProcessNote)) },
  { key: 'Energy Flow Note', label: 'iso.actions.notes.energyFlow', kind: 'textarea', section: 'notes', read: act((a) => text(a.energyFlowNote)) },
  { key: 'Evaluation Note', label: 'iso.actions.notes.evaluation', kind: 'textarea', section: 'notes', read: act((a) => text(a.evaluationNote)) },
  {
    key: 'Check List',
    label: 'iso.section.checkList',
    kind: 'checks',
    section: 'checkList',
    checks: ACTION_CHECKS,
    checkLabels: 'iso.actions.check',
    read: act((a) => checkValues(a.checkList, ACTION_CHECKS)),
  },
]

export const NONCONFORMITY_FIELDS: readonly FieldDef[] = [
  { key: 'Title', label: 'iso.nc.col.title', kind: 'text', section: 'general', required: true, read: nc((n) => text(n.title)) },
  { key: 'Responsible Person', label: 'iso.nc.col.responsible', kind: 'text', section: 'general', read: nc((n) => text(n.responsible)) },
  { key: 'Medium', label: 'iso.nc.col.medium', kind: 'select', section: 'general', options: (p) => plain((p as NonconformityPlan).media), read: nc((n) => text(n.medium)) },
  { key: 'SEU', label: 'iso.nc.col.seu', kind: 'select', section: 'general', options: (p) => plain((p as NonconformityPlan).seus), read: nc((n) => text(n.seu)) },
  { key: 'Field Tags', label: 'iso.nc.col.fields', kind: 'multi', section: 'general', options: (p) => plain((p as NonconformityPlan).fields), read: nc((n) => splitTags(n.fields)) },
  { key: 'Planned Date', label: 'iso.nc.col.plannedDate', kind: 'date', section: 'general', read: nc((n) => dateInput(n.plannedDate)) },
  { key: 'Done Date', label: 'iso.nc.col.doneDate', kind: 'date', section: 'general', read: nc((n) => dateInput(n.doneDate)) },
  { key: 'Description', label: 'iso.nc.col.description', kind: 'textarea', section: 'general', read: nc((n) => text(n.description)) },
  { key: 'Cause', label: 'iso.nc.col.cause', kind: 'textarea', section: 'notes', read: nc((n) => text(n.cause)) },
  { key: 'Immediate Measures', label: 'iso.nc.col.immediateMeasures', kind: 'textarea', section: 'notes', read: nc((n) => text(n.immediateMeasures)) },
  { key: 'Corrective Actions', label: 'iso.nc.col.correctiveActions', kind: 'textarea', section: 'notes', read: nc((n) => text(n.correctiveActions)) },
  { key: 'Action', label: 'iso.nc.col.action', kind: 'textarea', section: 'notes', read: nc((n) => text(n.action)) },
  {
    key: 'Check List',
    label: 'iso.section.checkList',
    kind: 'checks',
    section: 'checkList',
    checks: NONCONFORMITY_CHECKS,
    checkLabels: 'iso.nc.check',
    read: nc((n) => checkValues(n.checkList, NONCONFORMITY_CHECKS)),
  },
]

export const OBLIGATION_FIELDS: readonly FieldDef[] = [
  { key: 'title', label: 'iso.legal.col.title', kind: 'text', section: 'general', required: true, read: ob((o) => text(o.title)) },
  { key: 'Designation', label: 'iso.legal.col.designation', kind: 'text', section: 'general', read: ob((o) => text(o.designation)) },
  { key: 'category', label: 'iso.legal.col.category', kind: 'select', section: 'general', options: (p) => plain((p as LegalPlan).categories), read: ob((o) => text(o.category)) },
  { key: 'scope', label: 'iso.legal.col.scope', kind: 'select', section: 'general', options: (p) => plain((p as LegalPlan).scopes), read: ob((o) => text(o.scope)) },
  { key: 'Relevance 50001', label: 'isoEdit.relevant50001', kind: 'bool', section: 'general', read: ob((o) => o.relevant === true) },
  { key: 'link', label: 'iso.legal.col.link', kind: 'link', section: 'general', read: ob((o) => text(o.link)) },
  { key: 'issue date', label: 'iso.legal.col.issueDate', kind: 'date', section: 'general', read: ob((o) => dateInput(o.issueDate)) },
  { key: 'current version date', label: 'iso.legal.col.currentVersionDate', kind: 'date', section: 'general', read: ob((o) => dateInput(o.currentVersionDate)) },
  { key: 'Date of Examination', label: 'iso.legal.col.examinationDate', kind: 'date', section: 'general', read: ob((o) => dateInput(o.examinationDate)) },
  { key: 'Description', label: 'iso.legal.col.description', kind: 'textarea', section: 'general', read: ob((o) => text(o.description)) },
  { key: 'importance for the company', label: 'iso.legal.col.importance', kind: 'textarea', section: 'general', read: ob((o) => text(o.importance)) },
]

export function fieldsOf(kind: IsoKind): readonly FieldDef[] {
  return kind === 'actions' ? ACTION_FIELDS : kind === 'nonconformities' ? NONCONFORMITY_FIELDS : OBLIGATION_FIELDS
}

function emptyValue(def: FieldDef): FormValue {
  switch (def.kind) {
    case 'multi':
      return []
    case 'bool':
      return false
    case 'checks':
      return Object.fromEntries((def.checks ?? []).map((c) => [c.key, false]))
    default:
      return ''
  }
}

/** Values of a new item (nothing set, check list all "no"). */
export function emptyValues(defs: readonly FieldDef[]): FormValues {
  return Object.fromEntries(defs.map((d) => [d.key, emptyValue(d)]))
}

/** Values of an existing item. */
export function itemValues(defs: readonly FieldDef[], item: IsoItem): FormValues {
  return Object.fromEntries(defs.map((d) => [d.key, d.read(item)]))
}

/** Copy of the values (arrays and check lists are not shared). */
export function cloneValues(values: FormValues): FormValues {
  const copy: FormValues = {}
  for (const [k, v] of Object.entries(values)) copy[k] = Array.isArray(v) ? [...v] : typeof v === 'object' && v !== null ? { ...v } : v
  return copy
}

/** Value as sent: texts trimmed, empty date / select = null, lists and check lists as they are. */
export function requestValue(def: FieldDef, value: FormValue): unknown {
  switch (def.kind) {
    case 'date':
    case 'select':
      return value === '' || value == null ? null : value
    case 'multi':
      return Array.isArray(value) ? [...value] : []
    case 'bool':
      return value === true
    case 'checks':
      return { ...(value as Record<string, boolean>) }
    default:
      return String(value ?? '').trim()
  }
}

/** True if two values of a field are equal (lists ignoring order, texts ignoring outer spaces). */
export function sameValue(def: FieldDef, a: FormValue, b: FormValue): boolean {
  switch (def.kind) {
    case 'multi': {
      const x = new Set(Array.isArray(a) ? a : [])
      const y = new Set(Array.isArray(b) ? b : [])
      return x.size === y.size && [...x].every((v) => y.has(v))
    }
    case 'checks': {
      const x = (a ?? {}) as Record<string, boolean>
      const y = (b ?? {}) as Record<string, boolean>
      return (def.checks ?? []).every((c) => !!x[c.key] === !!y[c.key])
    }
    case 'bool':
      return !!a === !!b
    case 'text':
    case 'textarea':
    case 'link':
      return String(a ?? '').trim() === String(b ?? '').trim()
    default:
      return (a ?? '') === (b ?? '')
  }
}

/**
 * The fields to send: only those that differ from the loaded values (for a new item: from the empty values). The
 * check list is sent as a whole (all its booleans) when one of them changed.
 */
export function changedFields(defs: readonly FieldDef[], initial: FormValues, current: FormValues): IsoFields {
  const result: IsoFields = {}
  for (const def of defs) {
    if (!sameValue(def, initial[def.key], current[def.key])) result[def.key] = requestValue(def, current[def.key])
  }
  return result
}

/**
 * Options of a select / multi field: the plan's list plus stored values of the item that are no longer in it
 * (marked stale, so they are shown and kept as long as the field is not changed).
 */
export function fieldOptions(def: FieldDef, plan: IsoPlan, initial: FormValue | undefined): FieldOption[] {
  const options = def.options ? def.options(plan) : []
  const known = new Set(options.map((o) => o.value))
  const stored = Array.isArray(initial) ? initial : typeof initial === 'string' && initial ? [initial] : []
  const stale = stored.filter((v) => !known.has(v)).map((v) => ({ value: v, label: v, stale: true }))
  return [...options, ...stale]
}

/** A problem of a field: i18n key and its values. */
export interface FieldProblem {
  key: string
  params?: Record<string, unknown>
}

const LINK = /^https?:\/\/\S+$/i

/**
 * Checks before sending: required fields, links (http/https), and values of changed list fields that are not in the
 * plan's list (the server answers 400 for them; unchanged old values are kept).
 */
export function validateValues(defs: readonly FieldDef[], plan: IsoPlan, initial: FormValues, current: FormValues): Record<string, FieldProblem> {
  const problems: Record<string, FieldProblem> = {}
  for (const def of defs) {
    const value = current[def.key]
    if (def.required && !String(value ?? '').trim()) {
      problems[def.key] = { key: 'isoEdit.required' }
      continue
    }
    if (def.kind === 'link') {
      const link = String(value ?? '').trim()
      if (link && !sameValue(def, initial[def.key], value) && !LINK.test(link)) problems[def.key] = { key: 'isoEdit.linkInvalid' }
      continue
    }
    if ((def.kind === 'select' || def.kind === 'multi') && !sameValue(def, initial[def.key], value)) {
      const allowed = new Set((def.options ? def.options(plan) : []).map((o) => o.value))
      const chosen = Array.isArray(value) ? value : typeof value === 'string' && value ? [value] : []
      const outside = chosen.filter((v) => !allowed.has(v))
      if (outside.length) problems[def.key] = { key: 'isoEdit.notInList', params: { value: outside.join(', ') } }
    }
  }
  return problems
}

/** The "done" status of actions in the Control Center's languages (plugin.action.status.done). */
const DONE_STATUSES = ['erledigt', 'done']

export function isDoneStatus(status: string | null | undefined): boolean {
  return !!status && DONE_STATUSES.includes(status.trim().toLowerCase())
}

/**
 * Action status changed to a "done" status and no done date: the done date becomes today (as the Control Center's
 * GeneralTab and the server). Returns the new values, or null if nothing is to be set.
 */
export function withDoneDate(values: FormValues, today: string): FormValues | null {
  const status = values[ACTION_STATUS_KEY]
  if (typeof status !== 'string' || !isDoneStatus(status)) return null
  if (values[ACTION_DONE_DATE_KEY]) return null
  return { ...values, [ACTION_DONE_DATE_KEY]: today }
}

/** Today in the browser as yyyy-MM-dd. */
export function localToday(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/**
 * Messages of a 400 answer (e.g. the requirement check of nonconformities): a JSON list of texts, an object with
 * `messages` / `warnings` / `errors`, or plain text with one message per line.
 */
export function serverMessages(e: unknown): string[] {
  if (!(e instanceof HttpError) || e.status !== 400) return []
  const body = (e.body ?? '').trim()
  if (!body || body.startsWith('<')) return []
  const json = e.json<unknown>()
  const texts = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string' && !!x.trim()).map((x) => x.trim()) : [])
  if (Array.isArray(json)) return texts(json)
  if (json && typeof json === 'object') {
    const o = json as Record<string, unknown>
    const list = texts(o.messages).concat(texts(o.warnings), texts(o.errors))
    if (list.length) return list
    return typeof o.message === 'string' && o.message.trim() ? [o.message.trim()] : []
  }
  return body
    .split(/\r?\n/)
    .map((line) => line.replace(/^\s*[-•*]\s*/, '').trim())
    .filter(Boolean)
}

/* ------------------------------------------------------------------ list updates */

type ItemsKey = 'actions' | 'items' | 'obligations'

/** Key of the item list in a plan of the module. */
export const ITEMS_KEY: Record<IsoKind, ItemsKey> = { actions: 'actions', nonconformities: 'items', obligations: 'obligations' }

interface AnyList {
  plans: readonly object[]
}

type PlanRecord = { id: number } & Record<string, unknown>

/** The list with the item replaced (same id) or added to its plan. */
export function putItem<L extends AnyList>(list: L, kind: IsoKind, planId: number, item: IsoItem): L {
  const key = ITEMS_KEY[kind]
  const plans = (list.plans as PlanRecord[]).map((plan) => {
    const items = (plan[key] as IsoItem[] | undefined) ?? []
    const has = items.some((i) => i.id === item.id)
    if (has) return { ...plan, [key]: items.map((i) => (i.id === item.id ? item : i)) }
    if (plan.id === planId) return { ...plan, [key]: [...items, item] }
    return plan
  })
  return { ...list, plans }
}

/** The list without the item. */
export function dropItem<L extends AnyList>(list: L, kind: IsoKind, itemId: number): L {
  const key = ITEMS_KEY[kind]
  const plans = (list.plans as PlanRecord[]).map((plan) => {
    const items = (plan[key] as IsoItem[] | undefined) ?? []
    return items.some((i) => i.id === itemId) ? { ...plan, [key]: items.filter((i) => i.id !== itemId) } : plan
  })
  return { ...list, plans }
}

/** Number and title of an item for messages ("AP 004 – Lamps", "12 – Provision"). */
export function itemLabel(kind: IsoKind, item: IsoItem): string {
  const number = kind === 'obligations' ? ((item as Obligation).nr != null ? String((item as Obligation).nr) : '') : (item as Action | Nonconformity).number
  return [number, item.title].filter(Boolean).join(' – ')
}

/** True if the user may write anything in the module (else the view keeps the read-only notice). */
export function anyWritable(kind: IsoKind, plans: readonly IsoPlan[]): boolean {
  const key = ITEMS_KEY[kind]
  return plans.some((p) => !!p.canCreate || (((p as unknown as Record<string, unknown>)[key] as IsoItem[] | undefined) ?? []).some((i) => !!(i.canWrite || i.canDelete || i.canAttach)))
}
