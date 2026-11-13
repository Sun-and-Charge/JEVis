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
  /** E5: primitive type of the class type (STRING, DOUBLE, LONG, BOOLEAN, FILE, PASSWORD_PBKDF2, SELECTION, TARGET, …) */
  primitiveType?: string | null
  /** E5: GUI type of the class type (BASIC_TEXT_MULTI, BASIC_TEXT_DATE_FULL, TARGET_OBJECT, GPS, …) */
  guiType?: string | null
  /** E5: false for passwords, GPS and types the web does not edit (shown read-only) */
  editable?: boolean
  /** E5: label of the type's input unit (numbers are entered and stored in it), null without */
  inputUnit?: string | null
  /** E5: reserved, always false for now */
  required?: boolean
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
  /** E1: values may be entered (meters: for the target data row `target.id`, which the entry endpoints get) */
  canEnter?: boolean
  /** E5: attributes and name may be changed */
  canWrite?: boolean
  /** E5: may be moved to the recycle bin */
  canDelete?: boolean
  /** E5: files may be uploaded to the file attributes */
  canUpload?: boolean
  /** E5: ISO timestamp of the latest sample per attribute (null: no sample yet) */
  versions?: Record<string, string | null>
  /** E5: input unit per attribute where it differs from the column's (subclasses) */
  inputUnits?: Record<string, string | null>
}

export interface RegisterGroup {
  key: string
  name: string
  site: NavItem | null
  columns: RegisterColumn[]
  rows: RegisterRow[]
  /** E5: new objects may be created in this group */
  canCreate?: boolean
  /** E5: meters: the directory / plan new meters go below; equipment: null (see the create options) */
  createTarget?: number | null
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
  /** E1: entered manually (manual-entry note) */
  manual?: boolean
  /** E1: may be deleted via DELETE /web/rows/{rowId}/values */
  canDelete?: boolean
}

export interface RegisterHistory {
  unit: string | null
  samples: HistorySample[]
  /** E1: the row the samples belong to (meters: the target row) */
  rowId?: number | null
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

/* ------------------------------------------------------------------ E5: editing meters and equipment */

/** The kinds that can be edited (base data values are entered with E1). */
export type EditableRegisterKind = 'meters' | 'equipment'

/** One changed attribute: the version it was loaded with (null without sample) and the new value. */
export interface RegisterFieldChange {
  version: string | null
  value: unknown
}

export type RegisterFieldChanges = Record<string, RegisterFieldChange>

/** A meter class the user may create in a group. */
export interface MeterClassOption {
  class: string
  label: string
  medium: string | null
}

export interface MeterCreateOptions {
  classes: MeterClassOption[]
  /** meters of the group a new meter can go below */
  parents: { id: number; name: string }[]
}

export interface EquipmentCreateOptions {
  parents: { id: number; name: string; path?: string | null }[]
}

export interface MeterCreate {
  groupKey: string
  class: string
  name: string
  parentId: number | null
  fields: RegisterFieldChanges
}

export interface EquipmentCreate {
  class: string
  parentId: number
  name: string
  fields: RegisterFieldChanges
}

export interface UploadedFile {
  attribute: string
  file: string
  image: boolean
  version: string | null
}

export interface MeterExchange {
  /** ISO time of the exchange */
  timestamp: string
  oldReading: number
  newReading: number
  confirm: boolean
}

/** 25 MB, checked here and on the server. */
export const MAX_REGISTER_FILE_BYTES = 25 * 1024 * 1024

const langParam = (lang: string) => `lang=${encodeURIComponent(lang)}`

/** PUT /web/registers/objects/{id}: only the changed attributes (with their versions) and an optional new name. */
export function updateRegisterObject(
  http: Pick<HttpClient, 'put'>,
  id: number,
  body: { fields: RegisterFieldChanges; name?: string },
  lang: string,
): Promise<RegisterRow> {
  return http.put<RegisterRow>(`/web/registers/objects/${id}?${langParam(lang)}`, body)
}

export function loadMeterCreateOptions(http: GetClient, groupKey: string, lang: string): Promise<MeterCreateOptions> {
  return http.get<MeterCreateOptions>(`/web/registers/meters/create-options?groupKey=${encodeURIComponent(groupKey)}&${langParam(lang)}`)
}

export function loadEquipmentCreateOptions(http: GetClient, className: string, lang: string): Promise<EquipmentCreateOptions> {
  return http.get<EquipmentCreateOptions>(`/web/registers/equipment/create-options?class=${encodeURIComponent(className)}&${langParam(lang)}`)
}

/** POST /web/registers/{kind}/objects → the new row (201). */
export function createRegisterObject(http: Pick<HttpClient, 'post'>, kind: 'meters', body: MeterCreate, lang: string): Promise<RegisterRow>
export function createRegisterObject(http: Pick<HttpClient, 'post'>, kind: 'equipment', body: EquipmentCreate, lang: string): Promise<RegisterRow>
export function createRegisterObject(http: Pick<HttpClient, 'post'>, kind: EditableRegisterKind, body: MeterCreate | EquipmentCreate, lang: string): Promise<RegisterRow> {
  return http.post<RegisterRow>(`/web/registers/${kind}/objects?${langParam(lang)}`, body)
}

/** DELETE /web/registers/objects/{id}?version=… (recycle bin). */
export function deleteRegisterObject(http: Pick<HttpClient, 'delete'>, id: number, version: string): Promise<void> {
  return http.delete(`/web/registers/objects/${id}?version=${encodeURIComponent(version)}`)
}

/** POST /web/registers/objects/{id}/files/{attribute} (multipart `file`). */
export function uploadRegisterFile(http: Pick<HttpClient, 'postForm'>, id: number, attribute: string, file: File): Promise<UploadedFile> {
  const form = new FormData()
  form.append('file', file, file.name)
  return http.postForm<UploadedFile>(`/web/registers/objects/${id}/files/${encodeURIComponent(attribute)}`, form)
}

/** POST /web/registers/meters/{id}/exchange: final reading of the old meter and first reading of the new one. */
export function saveMeterExchange(http: Pick<HttpClient, 'post'>, id: number, body: MeterExchange, lang: string): Promise<{ samples?: unknown[] }> {
  return http.post<{ samples?: unknown[] }>(`/web/registers/meters/${id}/exchange?${langParam(lang)}`, body)
}
