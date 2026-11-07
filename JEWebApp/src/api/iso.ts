/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'
import type { NavItem } from './navigation'

/*
 * Action plans, nonconformities and the legal register (W4.2, view only). Dates are ISO 8601 with offset as stored
 * by the Control Center (null if not set); they are shown with the date as written (see iso/filter.ts).
 */

/** A medium of an action plan; co2 = t CO₂ per MWh (CO₂ in t = co2 · kWh / 1000). */
export interface Medium {
  id: string
  name: string
  co2: number | null
}

export interface NpvYearRow {
  year: number
  deposit: number | null
  cost: number | null
  net: number | null
  discounted: number | null
}

/** Net present value inputs and stored results (NPVData of the Control Center). Rates in percent. */
export interface Npv {
  interestRate: number | null
  investment: number | null
  /** yearly savings in € */
  saving: number | null
  runningCost: number | null
  inflation: number | null
  years: number | null
  duration: number | null
  result: number | null
  profitabilityIndex: number | null
  resultOverX: number | null
  profitabilityIndexOverX: number | null
  sumDeposits: number | null
  sumPayouts: number | null
  sumNet: number | null
  yearRows: NpvYearRow[]
}

/** Consumption or EnPI before / after the action (stored values); diff = yearly improvement. */
export interface Consumption {
  before: number | null
  after: number | null
  diff: number | null
  unit: string | null
  objectId: number | null
  beforeFrom: string | null
  beforeUntil: string | null
  afterFrom: string | null
  afterUntil: string | null
}

/** Check list as stored: booleans, plus text values (e.g. newEnPI). */
export type CheckList = Record<string, boolean | string | number | null>

export interface Action {
  id: number
  no: number | null
  number: string
  title: string
  description: string | null
  note: string | null
  fromUser: string | null
  responsible: string | null
  distributor: string | null
  createDate: string | null
  plannedDate: string | null
  doneDate: string | null
  status: string | null
  field: string | null
  medium: string | null
  mediumName: string | null
  seu: string | null
  correction: string | null
  followAction: string | null
  alternativeMeasures: string | null
  affectedProcessNote: string | null
  energyFlowNote: string | null
  evaluationNote: string | null
  npv: Npv | null
  consumption: Consumption | null
  enpi: Consumption | null
  checkList: CheckList | null
  daysSinceDone: number | null
  savingsSinceDone: number | null
  /** number of readable attachments */
  attachments: number
}

export interface ActionPlan {
  id: number
  name: string
  site: NavItem | null
  prefix: string | null
  statuses: string[]
  fields: string[]
  seus: string[]
  media: Medium[]
  actions: Action[]
}

export interface ActionPlanList {
  /** server date used for daysSinceDone */
  today: string
  plans: ActionPlan[]
}

export type NonconformityWarning = 'IMMEDIATE_MEASURES_MISSING' | 'DONE_WITHOUT_ACTION' | 'DONE_IN_FUTURE'

export interface Nonconformity {
  id: number
  nr: number | null
  number: string
  title: string
  creator: string | null
  responsible: string | null
  description: string | null
  cause: string | null
  immediateMeasures: string | null
  correctiveActions: string | null
  action: string | null
  medium: string | null
  fields: string[] | null
  seu: string | null
  createDate: string | null
  plannedDate: string | null
  doneDate: string | null
  /** no done date */
  open: boolean
  /** open and planned date before today */
  overdue: boolean
  checkList: CheckList | null
  warnings: (NonconformityWarning | string)[] | null
  attachments: number
}

export interface NonconformityPlan {
  id: number
  name: string
  site: NavItem | null
  prefix: string | null
  media: string[]
  fields: string[]
  seus: string[]
  items: Nonconformity[]
}

export interface NonconformityList {
  today?: string
  plans: NonconformityPlan[]
}

export interface Obligation {
  id: number
  uuid: string | null
  nr: number | null
  title: string
  designation: string | null
  description: string | null
  importance: string | null
  /** only http(s) URLs, else null */
  link: string | null
  category: string | null
  scope: string | null
  relevant: boolean | null
  issueDate: string | null
  currentVersionDate: string | null
  examinationDate: string | null
  attachments: number
}

export interface LegalPlan {
  id: number
  name: string
  site: NavItem | null
  categories: string[]
  scopes: string[]
  obligations: Obligation[]
}

export interface LegalList {
  plans: LegalPlan[]
}

/** A "File" child of an item. */
export interface Attachment {
  id: number
  name: string
  size: number | null
  date: string | null
}

type GetClient = Pick<HttpClient, 'get'>

export function loadActionPlans(http: GetClient): Promise<ActionPlanList> {
  return http.get<ActionPlanList>('/web/actionplans')
}

export function loadNonconformities(http: GetClient): Promise<NonconformityList> {
  return http.get<NonconformityList>('/web/nonconformities')
}

export function loadLegal(http: GetClient): Promise<LegalList> {
  return http.get<LegalList>('/web/legal')
}

export function loadAttachments(http: GetClient, itemId: number): Promise<Attachment[]> {
  return http.get<Attachment[]>(`/web/objects/${itemId}/attachments`)
}

/** The file with the Bearer token (a plain link could not send it). */
export function loadAttachmentFile(http: Pick<HttpClient, 'getBlob'>, fileId: number): Promise<Blob> {
  return http.getBlob(`/web/files/${fileId}`)
}
