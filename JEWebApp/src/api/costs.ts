/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'

/** Site of a list entry (next readable building above it). */
export interface CostSite {
  id: number
  name: string
  names?: Record<string, string>
}

/** An "Accounting Configuration" of GET /web/costs: a template applied to concrete objects. */
export interface CostConfigurationSummary {
  id: number
  name: string
  path: string | null
  site: CostSite | null
  /** the selected template; readable false if it is missing or not readable (the result answers 409) */
  template: { id: number | null; name: string | null; readable: boolean } | null
}

/** A "Result Calculation Template" of GET /web/costs. */
export interface CostTemplateSummary {
  id: number
  name: string
  path: string | null
  site: CostSite | null
}

/** Answer of GET /web/costs, sorted like the reports (site, path, name). */
export interface CostList {
  configurations: CostConfigurationSummary[]
  templates: CostTemplateSummary[]
}

export type CostKind = 'configuration' | 'template'

export interface CostTimeFrame {
  /** P1M, P1Y, … as resolved by the server */
  id: string
  /** translated name */
  name: string
}

/** Contract fields of a configuration (dates yyyy-MM-dd as stored). */
export interface CostContract {
  contractNumber: string | null
  marketLocation: string | null
  energyType: string | null
  start: string | null
  end: string | null
  contractType?: string | null
}

export interface CostObjectRef {
  id: number
  name: string
  readable?: boolean
}

export interface CostInput {
  id: string
  variableName: string
  label: string | null
  objectClass: string | null
  /** template: the user picks one of `options`; configuration: bound */
  selectable: boolean
  selected: CostObjectRef | null
  options: CostObjectRef[]
}

/** Answer of GET /web/costs/{id}?lang=. */
export interface CostDefinition {
  id: number
  kind: CostKind
  name: string
  templateName: string | null
  timeFrames: CostTimeFrame[]
  defaultTimeFrame: string | null
  contract: CostContract | null
  inputs: CostInput[]
  grid: { rows: number; columns: number }
}

/** Body of POST /web/costs/{id}/result. */
export interface CostResultRequest {
  /** one of the definition's time frames or CUSTOM */
  timeFrame: string
  /** ISO 8601, only with CUSTOM */
  from: string | null
  to: string | null
  timeZone: string
  /** templates only: input id → object id */
  selections?: Record<string, number>
}

export type CostCellKind = 'value' | 'text' | 'label' | 'separator'

/** One template output in the result grid (0-based row/column). */
export interface CostCell {
  row: number
  column: number
  rowSpan: number
  columnSpan: number
  kind: CostCellKind | string
  name?: string | null
  nameBold?: boolean
  /** kind value: the formula result; null if it could not be calculated */
  value?: number | null
  /** kind text */
  text?: string | null
  unit?: string | null
  resultBold?: boolean
  tooltip?: string | null
  /** the Control Center's analysis link: one input row of the formula */
  analysis?: { objectId: number; attribute: string } | null
  /** separators: horizontal if columnSpan > 1, vertical if rowSpan > 1 */
  orientation?: 'horizontal' | 'vertical' | string | null
}

/** Answer of POST /web/costs/{id}/result. */
export interface CostResult {
  from: string
  to: string
  rows: number
  columns: number
  cells: CostCell[]
  warnings: string[]
}

type GetClient = Pick<HttpClient, 'get'>
type PostClient = Pick<HttpClient, 'post'>

export const COSTS_PATH = '/web/costs'

export function costDefinitionPath(id: number, lang: string): string {
  return `/web/costs/${id}?${new URLSearchParams({ lang }).toString()}`
}

export function costResultPath(id: number): string {
  return `/web/costs/${id}/result`
}

export function loadCosts(http: GetClient): Promise<CostList> {
  return http.get<CostList>(COSTS_PATH)
}

export function loadCostDefinition(http: GetClient, id: number, lang: string): Promise<CostDefinition> {
  return http.get<CostDefinition>(costDefinitionPath(id, lang))
}

export function calculateCost(http: PostClient, id: number, request: CostResultRequest): Promise<CostResult> {
  return http.post<CostResult>(costResultPath(id), request)
}
