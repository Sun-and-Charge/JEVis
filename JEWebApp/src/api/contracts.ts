/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'

/** Groups of GET /web/contracts in the order of the answer (empty groups are left out by the server). */
export const CONTRACT_KINDS = ['suppliers', 'meteringOperators', 'gridOperators', 'contractors', 'dues'] as const
export type ContractKind = (typeof CONTRACT_KINDS)[number]

export interface ContractSite {
  id: number
  name: string
  names?: Record<string, string>
}

/** One step of a ranging value: from–to → value (to null: open end). */
export interface RangingStep {
  from: number | null
  to: number | null
  value: number | null
}

export type ContractAttributeType = 'number' | 'ranging' | 'text'

export interface ContractAttribute {
  name: string
  /** translated attribute name (class i18n), fallback the name */
  label: string
  type: ContractAttributeType | string
  /** number: latest sample; ranging: the steps; text: latest value as string; null without sample */
  value: number | RangingStep[] | string | null
  unit?: string | null
  timestamp: string | null
  /** more than one sample exists */
  history: boolean
  /** E6.2: false for files / passwords (shown read-only) */
  editable?: boolean
  /** E6.2, numbers only: label of the input unit (values are entered and stored in it) */
  inputUnit?: string | null
  /** E6.2: DOUBLE, LONG, STRING */
  primitive?: string | null
  guiType?: string | null
}

/** The linked contractor ("Contractor" attribute). */
export interface ContractorRef {
  id: number
  name?: string | null
  readable: boolean
}

export interface ContractObject {
  id: number
  name: string
  className: string
  classLabel: string
  path: string | null
  site: ContractSite | null
  contractor: ContractorRef | null
  attributes: ContractAttribute[]
  /** E6.2 rights */
  canWrite?: boolean
  canDelete?: boolean
  /** E6.2: version sent with a change / delete (409 if outdated); null if the object has no sample */
  version?: string | null
  /** E6.2: class whose objects may be chosen as contractor; null for contractors and dues */
  contractorClass?: string | null
}

/** E6.2: directory where objects of a group can be created. */
export interface ContractCreateTarget {
  id: number
  name: string
  path: string | null
  site: ContractSite | null
}

/** E6.2: a creatable class of a group (as the Control Center's "New" dialog). */
export interface ContractClassOption {
  class: string
  label: string
}

export interface ContractGroup {
  kind: ContractKind | string
  objects: ContractObject[]
  /** E6.2: the user may create objects of the group (then listed even when empty) */
  canCreate?: boolean
  createTargets?: ContractCreateTarget[]
  classes?: ContractClassOption[]
}

/** "Accounting Configuration": the contract fields of its template JSON (dates yyyy-MM-dd as stored). */
export interface AccountingConfiguration {
  id: number
  name: string
  path: string | null
  site: ContractSite | null
  contractNumber: string | null
  contractType: string | null
  marketLocation: string | null
  contractDate: string | null
  firstRate: string | null
  periodOfNotice: string | null
  start: string | null
  end: string | null
  energyType: string | null
  /** E6.3 rights */
  canWrite?: boolean
  canDelete?: boolean
  /** E6.3: timestamp of the latest "Template File" sample, "none" without */
  version?: string | null
  /** E6.3: the chosen "Result Calculation Template" */
  template?: TemplateRef | null
}

/** Answer of GET /web/contracts. */
export interface ContractList {
  groups: ContractGroup[]
  configurations: AccountingConfiguration[]
  /** E6.3: directories where the user may create an "Accounting Configuration" */
  configurationTargets?: ContractCreateTarget[]
  /** E6.3: configurationTargets is not empty */
  canCreateConfiguration?: boolean
}

/* ------------------------------------------------------------------ E6.3: accounting configurations */

/** Energy types of a configuration (`contractType`, the Control Center's "energy type"). */
export const CONTRACT_TYPES = ['ELECTRICITY', 'GAS', 'COMMUNITY_HEATING'] as const

/** A template as referenced by a configuration. */
export interface TemplateRef {
  id: number
  name: string | null
  readable: boolean
}

/** A readable "Result Calculation Template" of GET /web/contracts/templates. */
export interface TemplateOption {
  id: number
  name: string
  path: string | null
  site: ContractSite | null
}

/** The contract fields of a configuration (dates yyyy-MM-dd). */
export interface ConfigurationContract {
  contractNumber: string | null
  contractType: string | null
  marketLocation: string | null
  contractDate: string | null
  firstRate: string | null
  periodOfNotice: string | null
  start: string | null
  end: string | null
}

/** An object that can be bound to an input; `readable: false` (name null) for a chosen object the user cannot read. */
export interface InputOption {
  id: number
  name: string | null
  path?: string | null
  readable?: boolean
}

/** One input selector as the Control Center's input pane groups them (`key` = input id resp. `class:<objectClass>`). */
export interface ConfigurationInput {
  key: string
  label?: string | null
  variableNames: string[]
  objectClass: string | null
  filter?: string | null
  options: InputOption[]
  selected: number | null
}

/** GET /web/contracts/configurations/{id}/edit: the editor model (also the answer of a save and of a 409). */
export interface ConfigurationEdit {
  id: number
  name: string
  version: string | null
  parentId: number | null
  contract: ConfigurationContract
  template: TemplateRef | null
  inputs: ConfigurationInput[]
  unknownKeys?: string[]
  /** answer of a save: selectors without a selection etc. */
  warnings?: string[]
}

/** Body of PUT /web/contracts/configurations/{id}. */
export interface ConfigurationSave {
  version?: string
  name?: string
  contract: ConfigurationContract
  templateId: number
  selections: Record<string, number | null>
}

/** Body of POST /web/contracts/configurations. */
export interface ConfigurationCreate extends ConfigurationSave {
  parentId: number
  name: string
}

/** Answer of GET /web/contracts/objects/{id}/history (newest first). */
export interface ContractHistory {
  unit: string | null
  type: 'number' | 'ranging' | string
  samples: ContractHistorySample[]
}

export interface ContractHistorySample {
  timestamp: string
  value: number | RangingStep[] | null
  /** E6.2: the value may be deleted */
  canDelete?: boolean
}

/** E6.2: an object of GET /web/contracts/contractors. */
export interface ContractorOption {
  id: number
  name: string
  path: string | null
}

/** E6.2: body of POST /web/contracts/objects. */
export interface ContractCreate {
  parentId: number
  class: string
  name: string
}

/** E6.2: body of PUT /web/contracts/objects/{id}: only the changed parts. */
export interface ContractUpdate {
  version: string
  name?: string
  contractor?: number | null
  fields?: Record<string, string | { from: number; to: number; value: number }[]>
}

/** E6.2: body of POST /web/contracts/objects/{id}/values. */
export interface ContractValue {
  attribute: string
  validFrom: string
  value: number
  confirm: boolean
}

/** E6.2: a contract or object that still uses an object (409 of a delete). */
export interface ContractUsage {
  id: number
  name: string
  kind: 'configuration' | 'object' | string
}

type GetClient = Pick<HttpClient, 'get'>

export function contractsPath(lang: string): string {
  return `/web/contracts?${new URLSearchParams({ lang }).toString()}`
}

export function contractHistoryPath(id: number, attribute: string, limit = 100): string {
  const p = new URLSearchParams({ attribute, limit: String(limit) })
  return `/web/contracts/objects/${id}/history?${p.toString()}`
}

export function loadContracts(http: GetClient, lang: string): Promise<ContractList> {
  return http.get<ContractList>(contractsPath(lang))
}

export function loadContractHistory(http: GetClient, id: number, attribute: string, limit = 100): Promise<ContractHistory> {
  return http.get<ContractHistory>(contractHistoryPath(id, attribute, limit))
}

const langParam = (lang: string) => new URLSearchParams({ lang }).toString()

/* ------------------------------------------------------------------ E6.2: editing */

export function contractorsPath(contractorClass: string): string {
  return `/web/contracts/contractors?${new URLSearchParams({ class: contractorClass }).toString()}`
}

export function loadContractors(http: GetClient, contractorClass: string): Promise<ContractorOption[]> {
  return http.get<ContractorOption[]>(contractorsPath(contractorClass))
}

export function createContractObject(http: Pick<HttpClient, 'post'>, body: ContractCreate, lang: string): Promise<ContractObject> {
  return http.post<ContractObject>(`/web/contracts/objects?${langParam(lang)}`, body)
}

export function updateContractObject(http: Pick<HttpClient, 'put'>, id: number, body: ContractUpdate, lang: string): Promise<ContractObject> {
  return http.put<ContractObject>(`/web/contracts/objects/${id}?${langParam(lang)}`, body)
}

export function addContractValue(http: Pick<HttpClient, 'post'>, id: number, body: ContractValue, lang: string): Promise<{ timestamp: string; value: number }> {
  return http.post<{ timestamp: string; value: number }>(`/web/contracts/objects/${id}/values?${langParam(lang)}`, body)
}

export function contractValuePath(id: number, attribute: string, timestamp: string): string {
  return `/web/contracts/objects/${id}/values?${new URLSearchParams({ attribute, timestamp }).toString()}`
}

export function deleteContractValue(http: Pick<HttpClient, 'delete'>, id: number, attribute: string, timestamp: string): Promise<void> {
  return http.delete(contractValuePath(id, attribute, timestamp))
}

export function deleteContractObjectPath(id: number, version: string, confirm: boolean): string {
  return `/web/contracts/objects/${id}?${new URLSearchParams({ version, confirm: String(confirm) }).toString()}`
}

/** DELETE /web/contracts/objects/{id} (recycle bin); 409 with `usedBy` unless `confirm`. */
export function deleteContractObject(http: Pick<HttpClient, 'delete'>, id: number, version: string, confirm = false): Promise<void> {
  return http.delete(deleteContractObjectPath(id, version, confirm))
}

/* ------------------------------------------------------------------ E6.3: accounting configurations */

/** A list answer that may come bare or wrapped (`{ "<key>": [...] }`). */
function listOf<T>(answer: unknown, key: string): T[] {
  if (Array.isArray(answer)) return answer as T[]
  const inner = answer && typeof answer === 'object' ? (answer as Record<string, unknown>)[key] : null
  return Array.isArray(inner) ? (inner as T[]) : []
}

export function configurationEditPath(id: number, lang: string): string {
  return `/web/contracts/configurations/${id}/edit?${langParam(lang)}`
}

export function loadConfigurationEdit(http: GetClient, id: number, lang: string): Promise<ConfigurationEdit> {
  return http.get<ConfigurationEdit>(configurationEditPath(id, lang))
}

export async function loadTemplates(http: GetClient, lang: string): Promise<TemplateOption[]> {
  return listOf<TemplateOption>(await http.get<unknown>(`/web/contracts/templates?${langParam(lang)}`), 'templates')
}

export function templateInputsPath(templateId: number, configuration: number | null, lang: string): string {
  const p = new URLSearchParams({ lang })
  if (configuration != null) p.set('configuration', String(configuration))
  return `/web/contracts/templates/${templateId}/inputs?${p.toString()}`
}

/** The input selectors of a template; `selected` from the configuration when it uses the same template. */
export async function loadTemplateInputs(http: GetClient, templateId: number, configuration: number | null, lang: string): Promise<ConfigurationInput[]> {
  return listOf<ConfigurationInput>(await http.get<unknown>(templateInputsPath(templateId, configuration, lang)), 'inputs')
}

export function createConfiguration(http: Pick<HttpClient, 'post'>, body: ConfigurationCreate, lang: string): Promise<ConfigurationEdit> {
  return http.post<ConfigurationEdit>(`/web/contracts/configurations?${langParam(lang)}`, body)
}

export function updateConfiguration(http: Pick<HttpClient, 'put'>, id: number, body: ConfigurationSave, lang: string): Promise<ConfigurationEdit> {
  return http.put<ConfigurationEdit>(`/web/contracts/configurations/${id}?${langParam(lang)}`, body)
}

export function deleteConfigurationPath(id: number, version: string): string {
  return `/web/contracts/configurations/${id}?${new URLSearchParams({ version }).toString()}`
}

/** DELETE /web/contracts/configurations/{id} (recycle bin). */
export function deleteConfiguration(http: Pick<HttpClient, 'delete'>, id: number, version: string): Promise<void> {
  return http.delete(deleteConfigurationPath(id, version))
}
