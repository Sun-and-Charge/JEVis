/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'
import type { NavItem } from './navigation'

/*
 * Meters, base data and equipment (W4.3, view only): tables over the native attributes of the objects, like the
 * Control Center's "Meter Plugin", "Base Data Plugin" and "Equipment Plugin".
 */

export type RegisterKind = 'meters' | 'basedata' | 'equipment'

export const REGISTER_KINDS: readonly RegisterKind[] = ['meters', 'basedata', 'equipment']

export type AttributeType = 'TEXT' | 'NUMBER' | 'INTEGER' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'FILE' | 'TARGET' | 'TEXTAREA'

/** An attribute column of a group (translated name, sorted by GUI position). */
export interface RegisterColumn {
  key: string
  name: string
  type: AttributeType | string
  unit: string | null
  position: number | null
  /** shown by default */
  visible: boolean
}

/** Value of a file attribute (download via {@link loadRegisterFile}). */
export interface FileValue {
  file: string
  image: boolean
}

/** Value of a target attribute; name only if readable. */
export interface TargetValue {
  id: number
  attribute: string | null
  name: string | null
  readable: boolean
}

/** Latest sample per attribute: number, boolean, string, date yyyy-MM-dd, file or target. */
export type RegisterValue = string | number | boolean | FileValue | TargetValue | null

/** Latest sample of a meter's target or of a base data object's "Value". */
export interface LastValue {
  value: number | string | null
  unit: string | null
  /** ISO 8601 with offset */
  timestamp: string | null
  /** meters: "Decimal Places" of the meter, else of the target */
  decimals?: number | null
  /** base data: ISO period of the data (P1M → month) */
  period?: string | null
}

export interface Verification {
  /** next due date yyyy-MM-dd */
  due: string | null
  /** due before today */
  overdue: boolean
}

export interface RegisterRow {
  id: number
  name: string
  className: string
  classLabel: string | null
  path: string | null
  /** meters: ELECTRICITY, GAS, WATER, HEAT, … from the class; null otherwise */
  medium?: string | null
  values: Record<string, RegisterValue>
  target?: TargetValue | null
  last?: LastValue | null
  verification?: Verification | null
  /** base data: period of the data (if not given in `last`) */
  period?: string | null
}

export interface RegisterGroup {
  key: string
  name: string
  site: NavItem | null
  columns: RegisterColumn[]
  rows: RegisterRow[]
}

export interface RegisterList {
  kind: RegisterKind
  /** server date used for verification.overdue */
  today: string
  groups: RegisterGroup[]
}

export interface HistorySample {
  timestamp: string
  value: number | string | null
  note: string | null
}

export interface RegisterHistory {
  unit: string | null
  samples: HistorySample[]
}

type GetClient = Pick<HttpClient, 'get'>
type BlobClient = Pick<HttpClient, 'getBlob'>

export function loadRegisters(http: GetClient, kind: RegisterKind, lang: string): Promise<RegisterList> {
  return http.get<RegisterList>(`/web/registers/${kind}?lang=${encodeURIComponent(lang)}`)
}

export function loadRegisterHistory(http: GetClient, id: number, limit = 12): Promise<RegisterHistory> {
  return http.get<RegisterHistory>(`/web/registers/objects/${id}/history?limit=${limit}`)
}

/** Path of the latest file of a file attribute; `inline` for images shown as thumbnail. */
export function registerFilePath(id: number, attribute: string, inline = false): string {
  return `/web/registers/objects/${id}/files/${encodeURIComponent(attribute)}${inline ? '?inline=true' : ''}`
}

/** Path of a meter's QR code (SVG). */
export function meterQrPath(id: number): string {
  return `/web/registers/meters/${id}/qr.svg`
}

/** The latest file of a file attribute with the Bearer token (download). */
export function loadRegisterFile(http: BlobClient, id: number, attribute: string, inline = false): Promise<Blob> {
  return http.getBlob(registerFilePath(id, attribute, inline))
}
