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
}

export interface ContractGroup {
  kind: ContractKind | string
  objects: ContractObject[]
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
  samples: { timestamp: string; value: number | RangingStep[] | null }[]
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
