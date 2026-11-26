/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'
import type { ModuleRef, Recipients } from './modules'
import type { NavItem } from './navigation'
import type { RoundOptionPerson } from './readingRounds'

/*
 * Energy certificates of the buildings (tender point BA-07, docs/ENERGY-CERTIFICATES.md): GET /web/certificates,
 * create / change / delete, the PDF and "Jetzt übermitteln".
 */

export type CertificateType = 'CONSUMPTION' | 'DEMAND'
export type CertificateUse = 'RESIDENTIAL' | 'NON_RESIDENTIAL'
export type CertificateStatus = 'UNKNOWN' | 'VALID' | 'EXPIRING' | 'EXPIRED'

/** The stored fields (JSON "Data" of the certificate, JECommons EnergyCertificate). Dates yyyy-MM-dd. */
export interface CertificateData {
  type: CertificateType | null
  use?: CertificateUse | null
  registrationNumber?: string | null
  issued?: string | null
  validUntil: string | null
  issuer?: string | null
  responsible?: number[]
  energyClass?: string | null
  /** kWh/(m²·a) */
  finalEnergy?: number | null
  primaryEnergy?: number | null
  heatConsumption?: number | null
  electricityConsumption?: number | null
  /** m² */
  referenceArea?: number | null
  /** kg/(m²·a) */
  co2?: number | null
  /** months before the expiry with a reminder mail (default 6, 1) */
  reminderMonths?: number[]
  note?: string | null
  created?: string | null
  createdBy?: string | null
}

export interface Certificate {
  id: number
  name: string
  site: NavItem | null
  building: NavItem | null
  directory: ModuleRef | null
  data: CertificateData
  validUntil: string | null
  status: CertificateStatus
  /** days until validUntil (negative: expired), null without date */
  daysLeft: number | null
  responsible: Recipients
  file: { name: string } | null
  lastReminder: string | null
  reminderError: string | null
  /** transfer target id → time of the last transfer of this certificate */
  transfers: Record<string, string>
  canWrite: boolean
  canDelete: boolean
  version: string | null
}

export interface CertificateBuilding extends NavItem {
  directory: number | null
  canCreate: boolean
}

export interface CertificateTransfer {
  id: number
  name: string
  site: NavItem | null
  enabled: string | null
  channel: string | null
  format: string | null
  schedule: string | null
  lastTransfer: { at?: string; count?: number; result?: string; error?: string } | string | null
  requested: { at?: string; by?: string } | string | null
  canRun: boolean
}

export interface CertificateList {
  today: string
  certificates: Certificate[]
  buildings: CertificateBuilding[]
  transfers: CertificateTransfer[]
  people: RoundOptionPerson[]
}

export const ENERGY_CLASSES = ['A+', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'] as const

export function loadCertificates(http: Pick<HttpClient, 'get'>, site?: number | null): Promise<CertificateList> {
  return http.get<CertificateList>('/web/certificates' + (site != null ? `?site=${site}` : ''))
}

export function createCertificate(http: Pick<HttpClient, 'post'>, building: number, data: CertificateData): Promise<Certificate> {
  return http.post<Certificate>('/web/certificates', { building, data })
}

export function updateCertificate(http: Pick<HttpClient, 'put'>, id: number, version: string, data: CertificateData): Promise<Certificate> {
  return http.put<Certificate>(`/web/certificates/${id}`, { version, data })
}

export function deleteCertificate(http: Pick<HttpClient, 'delete'>, id: number, version: string): Promise<void> {
  return http.delete(`/web/certificates/${id}?version=${encodeURIComponent(version)}`)
}

export function uploadCertificateFile(http: Pick<HttpClient, 'postForm'>, id: number, file: File): Promise<Certificate> {
  const form = new FormData()
  form.append('file', file, file.name)
  return http.postForm<Certificate>(`/web/certificates/${id}/file`, form)
}

export function deleteCertificateFile(http: Pick<HttpClient, 'delete'>, id: number): Promise<void> {
  return http.delete(`/web/certificates/${id}/file`)
}

export function loadCertificateFile(http: Pick<HttpClient, 'getBlob'>, id: number): Promise<Blob> {
  return http.getBlob(`/web/certificates/${id}/file`)
}

export function requestTransfer(http: Pick<HttpClient, 'post'>, id: number): Promise<CertificateTransfer> {
  return http.post<CertificateTransfer>(`/web/certificates/transfers/${id}/run`)
}
