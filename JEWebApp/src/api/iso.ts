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

/** What the user may do with an item (E2); missing = not allowed (older servers). */
export interface ItemRights {
  /** timestamp of the latest Data sample, sent back on change / delete */
  version?: string | null
  canWrite?: boolean
  canDelete?: boolean
  /** may add attachments ("File" below the item) */
  canAttach?: boolean
}

/** Per plan: may create items (E2). */
export interface PlanRights {
  canCreate?: boolean
}

export interface Action extends ItemRights {
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

export interface ActionPlan extends PlanRights {
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

export interface Nonconformity extends ItemRights {
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

export interface NonconformityPlan extends PlanRights {
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

export interface Obligation extends ItemRights {
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

export interface LegalPlan extends PlanRights {
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
  /** may move the file to the recycle bin; missing = not delivered (then the item's canAttach decides) */
  canDelete?: boolean
}

/* ------------------------------------------------------------------ writes (E2) */

/** Path segment of the item endpoints. */
export type IsoKind = 'actions' | 'nonconformities' | 'obligations'

/** Item of any of the three modules. */
export type IsoItem = Action | Nonconformity | Obligation

/** Editable fields with the stored JSON keys ("Title", "Status Tags", …). */
export type IsoFields = Record<string, unknown>

/** Upper limit of an attachment (the server refuses larger files too). */
export const MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024

/** POST /web/{kind}/plans/{planId}/items with the editable fields → the new item (list format, with number). */
export function createIsoItem<T extends IsoItem>(http: Pick<HttpClient, 'post'>, kind: IsoKind, planId: number, fields: IsoFields): Promise<T> {
  return http.post<T>(`/web/${kind}/plans/${planId}/items`, fields)
}

/** PUT /web/{kind}/items/{id}: only the changed fields plus the loaded version → the updated item. */
export function updateIsoItem<T extends IsoItem>(http: Pick<HttpClient, 'put'>, kind: IsoKind, id: number, version: string, fields: IsoFields): Promise<T> {
  return http.put<T>(`/web/${kind}/items/${id}`, { version, fields })
}

/** DELETE /web/{kind}/items/{id}?version=… (soft delete, "Deleted": true). */
export function deleteIsoItem(http: Pick<HttpClient, 'delete'>, kind: IsoKind, id: number, version: string): Promise<void> {
  return http.delete<void>(`/web/${kind}/items/${id}?version=${encodeURIComponent(version)}`)
}

/** POST /web/items/{id}/attachments (multipart "file") → the new "File" object. */
export function uploadAttachment(http: Pick<HttpClient, 'postForm'>, itemId: number, file: File): Promise<Attachment> {
  const form = new FormData()
  form.append('file', file, file.name)
  return http.postForm<Attachment>(`/web/items/${itemId}/attachments`, form)
}

/** DELETE /web/attachments/{fileId}: the "File" object goes to the recycle bin. */
export function deleteAttachment(http: Pick<HttpClient, 'delete'>, fileId: number): Promise<void> {
  return http.delete<void>(`/web/attachments/${fileId}`)
}

type GetClient = Pick<HttpClient, 'get'>

/** GET /web/actionplans; `lang` names the "open"/"done" statuses the server adds when a plan lacks them. */
export function loadActionPlans(http: GetClient, lang?: string): Promise<ActionPlanList> {
  return http.get<ActionPlanList>(lang ? `/web/actionplans?lang=${encodeURIComponent(lang)}` : '/web/actionplans')
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
