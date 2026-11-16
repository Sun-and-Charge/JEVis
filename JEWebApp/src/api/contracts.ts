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
}

/** Answer of GET /web/contracts. */
export interface ContractList {
  groups: ContractGroup[]
  configurations: AccountingConfiguration[]
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
