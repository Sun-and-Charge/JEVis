/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import {
  CONTRACT_TYPES,
  type AccountingConfiguration,
  type ConfigurationContract,
  type ConfigurationEdit,
  type ConfigurationInput,
  type ConfigurationSave,
  type ContractList,
  type InputOption,
} from '../api/contracts'
import { HttpError } from '../api/http'
import { isDay, nameProblem, type Problem } from './contractEdit'

/*
 * Editing of accounting configurations (E6.3), like the Control Center's "Configuration" tab of the Accounting
 * Plugin: the contract fields, the chosen "Result Calculation Template" and the objects bound to its input selectors.
 * The form, its checks as the server does them, the body of a save, the selections kept when the template changes and
 * the merge of own changes with a newer version (409 "Weiter bearbeiten").
 */

/** Text fields of the contract (server: at most 200 characters). */
export const CONTRACT_TEXT_FIELDS = ['contractNumber', 'marketLocation'] as const
/** Date fields of the contract (yyyy-MM-dd or empty). */
export const CONTRACT_DATE_FIELDS = ['contractDate', 'firstRate', 'periodOfNotice', 'start', 'end'] as const
/** All fields in the order of the form. */
export const CONTRACT_FIELDS = ['contractNumber', 'marketLocation', 'contractType', ...CONTRACT_DATE_FIELDS] as const
export type ContractField = (typeof CONTRACT_FIELDS)[number]
export const CONTRACT_TEXT_MAX = 200

/* ------------------------------------------------------------------ rights */

/** The configuration can be opened in the editor ("Bearbeiten"). */
export const canEditConfiguration = (c: Pick<AccountingConfiguration, 'canWrite' | 'canDelete'>) => c.canWrite === true || c.canDelete === true

/** "Neu" in the configurations tab: the right and at least one directory. */
export const canCreateConfiguration = (list: Pick<ContractList, 'canCreateConfiguration' | 'configurationTargets'> | null | undefined) =>
  !!list && list.canCreateConfiguration !== false && !!list.configurationTargets?.length

/* ------------------------------------------------------------------ the form */

export type ContractValues = Record<ContractField, string>

export interface ConfigurationForm {
  name: string
  /** the contract fields as texts ('' = empty) */
  contract: ContractValues
  templateId: number | null
  /** chosen object per selector key; null: not assigned */
  selections: Record<string, number | null>
}

const text = (v: unknown) => (v == null ? '' : String(v))

export function emptyContract(): ContractValues {
  return Object.fromEntries(CONTRACT_FIELDS.map((f) => [f, ''])) as ContractValues
}

/** Chosen objects of the selectors as loaded. */
export function selectionsOf(inputs: readonly ConfigurationInput[] | null | undefined): Record<string, number | null> {
  const out: Record<string, number | null> = {}
  for (const i of inputs ?? []) if (i && typeof i.key === 'string') out[i.key] = typeof i.selected === 'number' ? i.selected : null
  return out
}

export function configurationForm(model: ConfigurationEdit | null | undefined): ConfigurationForm {
  const contract = emptyContract()
  for (const f of CONTRACT_FIELDS) contract[f] = text(model?.contract?.[f])
  return {
    name: model?.name ?? '',
    contract,
    templateId: typeof model?.template?.id === 'number' ? model.template.id : null,
    selections: selectionsOf(model?.inputs),
  }
}

export function cloneConfigurationForm(f: ConfigurationForm): ConfigurationForm {
  return { name: f.name, contract: { ...f.contract }, templateId: f.templateId, selections: { ...f.selections } }
}

export interface ConfigurationChanges {
  name: boolean
  contract: ContractField[]
  template: boolean
  selections: string[]
}

export function configurationChanges(initial: ConfigurationForm, current: ConfigurationForm): ConfigurationChanges {
  const keys = new Set([...Object.keys(initial.selections), ...Object.keys(current.selections)])
  return {
    name: current.name.trim() !== initial.name.trim(),
    contract: CONTRACT_FIELDS.filter((f) => current.contract[f].trim() !== initial.contract[f].trim()),
    template: current.templateId !== initial.templateId,
    selections: [...keys].filter((k) => (current.selections[k] ?? null) !== (initial.selections[k] ?? null)),
  }
}

export const hasConfigurationChanges = (c: ConfigurationChanges) => c.name || c.contract.length > 0 || c.template || c.selections.length > 0

export interface ConfigurationCheck {
  /** problems per field: `name`, `parent`, `template` or a contract field */
  problems: Record<string, Problem>
  /** the save body (without version / parent); null with problems */
  body: ConfigurationSave | null
}

/**
 * Checks the form as the server does (name, energy type, dates and end not before start, texts ≤ 200 characters, a
 * readable template) and builds the body: the whole contract (empty → null), the template and a selection for every
 * selector of the current template. `nameChanged` false: an unchanged name is not checked and not sent.
 */
export function checkConfiguration(
  form: ConfigurationForm,
  inputs: readonly ConfigurationInput[],
  options: { nameChanged: boolean; templateReadable: boolean },
): ConfigurationCheck {
  const problems: Record<string, Problem> = {}
  if (options.nameChanged) {
    const p = nameProblem(form.name)
    if (p) problems.name = p
  }
  for (const f of CONTRACT_TEXT_FIELDS) {
    if (form.contract[f].trim().length > CONTRACT_TEXT_MAX) problems[f] = { key: 'configEdit.problem.textLength', params: { max: CONTRACT_TEXT_MAX } }
  }
  const type = form.contract.contractType.trim()
  if (type && !(CONTRACT_TYPES as readonly string[]).includes(type)) problems.contractType = { key: 'configEdit.problem.contractType' }
  for (const f of CONTRACT_DATE_FIELDS) {
    const v = form.contract[f].trim()
    if (v && !isDay(v)) problems[f] = { key: 'configEdit.problem.date' }
  }
  const start = form.contract.start.trim()
  const end = form.contract.end.trim()
  if (!problems.start && !problems.end && start && end && end < start) problems.end = { key: 'configEdit.problem.endBeforeStart' }
  if (form.templateId == null) problems.template = { key: 'configEdit.problem.template' }
  else if (!options.templateReadable) problems.template = { key: 'configEdit.problem.templateUnreadable' }
  if (Object.keys(problems).length) return { problems, body: null }

  const contract = {} as ConfigurationContract
  for (const f of CONTRACT_FIELDS) contract[f] = form.contract[f].trim() || null
  const selections: Record<string, number | null> = {}
  for (const i of inputs) selections[i.key] = form.selections[i.key] ?? null
  const body: ConfigurationSave = { contract, templateId: form.templateId!, selections }
  if (options.nameChanged) body.name = form.name.trim()
  return { problems, body }
}

/** Keys of the selectors without a chosen object ("nicht zugeordnet"). */
export function unassignedKeys(inputs: readonly ConfigurationInput[], selections: Record<string, number | null>): string[] {
  return inputs.filter((i) => selections[i.key] == null).map((i) => i.key)
}

/**
 * Selections after the template changed: a selector whose key also existed before keeps its object (when the object
 * is offered there or the selector is of the same class); the others take what the server sent (usually nothing).
 */
export function carrySelections(
  oldInputs: readonly ConfigurationInput[],
  oldSelections: Record<string, number | null>,
  newInputs: readonly ConfigurationInput[],
): Record<string, number | null> {
  const before = new Map(oldInputs.map((i) => [i.key, i]))
  const out: Record<string, number | null> = {}
  for (const i of newInputs) {
    const prev = before.get(i.key)
    const chosen = oldSelections[i.key] ?? null
    const fits = chosen != null && prev != null && ((i.options ?? []).some((o) => o.id === chosen) || prev.objectClass === i.objectClass)
    out[i.key] = fits ? chosen : typeof i.selected === 'number' ? i.selected : null
  }
  return out
}

/**
 * After a 409 "Weiter bearbeiten" (or a newer version): the server's form with the own changes kept. When the own
 * template changed, its selections stay as they are; otherwise own changed selections are kept where the key exists.
 */
export function mergeConfigurationForm(initial: ConfigurationForm, current: ConfigurationForm, server: ConfigurationForm): ConfigurationForm {
  const c = configurationChanges(initial, current)
  const merged = cloneConfigurationForm(server)
  if (c.name) merged.name = current.name
  for (const f of c.contract) merged.contract[f] = current.contract[f]
  if (c.template) {
    merged.templateId = current.templateId
    merged.selections = { ...current.selections }
  } else {
    for (const k of c.selections) if (k in merged.selections) merged.selections[k] = current.selections[k] ?? null
  }
  return merged
}

/* ------------------------------------------------------------------ labels */

/** Label of a selector: its variable names, else the label or the key. */
export function inputTitle(i: Pick<ConfigurationInput, 'variableNames' | 'label' | 'key'>): string {
  const names = (i.variableNames ?? []).filter((n) => typeof n === 'string' && n.trim())
  return names.length ? names.join(', ') : i.label || i.key
}

/** Label of an object option ("name – path"); `hidden` for an object the user cannot read. */
export function inputOptionLabel(o: InputOption, hidden: string): string {
  if (o.readable === false || !o.name) return hidden
  return o.path ? `${o.name} – ${o.path}` : o.name
}

/* ------------------------------------------------------------------ answers */

/** The editor model of an answer (save, 409), null if the body is none. */
export function editModelOf(json: unknown, id: number | null): ConfigurationEdit | null {
  if (!json || typeof json !== 'object' || Array.isArray(json)) return null
  const m = json as Partial<ConfigurationEdit>
  if (typeof m.id !== 'number' || (id != null && m.id !== id)) return null
  return { ...m, inputs: Array.isArray(m.inputs) ? m.inputs : [], contract: m.contract ?? ({} as ConfigurationContract) } as ConfigurationEdit
}

/** The editor model of a 409 answer; null if none. */
export function conflictModelOf(e: unknown, id: number): ConfigurationEdit | null {
  if (!(e instanceof HttpError) || e.status !== 409) return null
  return editModelOf(e.json<unknown>(), id)
}

/** Warnings of a save answer (texts only). */
export function warningsOfModel(model: Pick<ConfigurationEdit, 'warnings'> | null | undefined): string[] {
  return Array.isArray(model?.warnings) ? model.warnings.filter((w): w is string => typeof w === 'string' && !!w.trim()) : []
}
