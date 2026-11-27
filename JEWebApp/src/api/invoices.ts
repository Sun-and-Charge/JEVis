/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'

/** Media of the energy invoices (attribute "Medium"); unknown values count as OTHER. */
export const INVOICE_MEDIA = ['ELECTRICITY', 'GAS', 'DISTRICT_HEATING', 'OTHER'] as const
export type InvoiceMedium = (typeof INVOICE_MEDIA)[number]

/** One energy invoice of GET /web/invoices (latest samples of the attributes; credit notes stored negative). */
export interface Invoice {
  id: number
  number: string | null
  /** yyyy-MM-dd */
  date: string | null
  format: string | null
  seller: string | null
  buyer: string | null
  periodStart: string | null
  periodEnd: string | null
  medium: string | null
  meterPoint: string | null
  consumption: number | null
  consumptionUnit: string | null
  net: number | null
  tax: number | null
  gross: number | null
  currency: string | null
  /** gross amount < 0 */
  credit: boolean
  /** stored file name of "Invoice File", null if none */
  file: string | null
  path: string | null
  site: { id: number; name: string; names?: Record<string, string> } | null
  /** E6.1: delete right on the invoice and write right on the series of its folder */
  canDelete?: boolean
  /** E6.1: latest change of the invoice's attributes, sent with DELETE (409 if outdated) */
  version?: string | null
  /** R03: result of the automatic invoice check, null if not checked */
  check?: InvoiceCheckResult | null
  /** R03: write right on the invoice ("Erneut prüfen") */
  canCheck?: boolean
}

/** One comparison of the invoice check: expected (measured / contract) against invoiced. */
export interface InvoiceCheckPart {
  expected: number
  invoiced: number
  difference: number
  /** null against an expected 0 */
  deviationPercent: number | null
  tolerancePercent: number
  ok: boolean
  unit?: string | null
}

/** "Check Result" of an invoice (docs/ENERGY-INVOICES.md "Invoice check"). */
export interface InvoiceCheckResult {
  status?: 'OK' | 'DEVIATION' | 'NOT_CHECKABLE' | string
  /** NOT_CHECKABLE: noMeterPoint, noContract, noPeriod, calculation, noCheckFormulas, noValues, error */
  reason?: string
  message?: string
  checkedAt?: string
  contract?: { id: number; name: string } | null
  quantity?: InvoiceCheckPart | null
  amount?: InvoiceCheckPart | null
  partial?: boolean
}

/** One line of the parsed "Line Items" JSON as stored (typical keys: description, quantity, unit, unitPrice, net, taxRate, periodStart, periodEnd). */
export type InvoiceItem = Record<string, unknown>

/** Answer of GET /web/invoices/{id}. */
export interface InvoiceDetail extends Invoice {
  sellerVatId: string | null
  items: InvoiceItem[]
}

/** Answer of GET /web/invoices: newest period first, at most 2000. */
export interface InvoiceList {
  invoices: Invoice[]
  truncated: boolean
  /** E6.1: the user may import into at least one Energy Invoices Directory */
  canImport?: boolean
}

export interface InvoiceQuery {
  /** yyyy-MM-dd, inclusive */
  from: string
  to: string
  timeZone: string
}

type GetClient = Pick<HttpClient, 'get'>
type BlobClient = Pick<HttpClient, 'getBlob'>

export function invoicesPath(query: InvoiceQuery): string {
  const p = new URLSearchParams({ from: query.from, to: query.to, timeZone: query.timeZone })
  return `/web/invoices?${p.toString()}`
}

export function invoicePath(id: number): string {
  return `/web/invoices/${id}`
}

/** The original file; inline: Content-Disposition inline (PDF preview). */
export function invoiceFilePath(id: number, inline = false): string {
  return `/web/invoices/${id}/file${inline ? '?inline=true' : ''}`
}

export function loadInvoices(http: GetClient, query: InvoiceQuery): Promise<InvoiceList> {
  return http.get<InvoiceList>(invoicesPath(query))
}

export function loadInvoice(http: GetClient, id: number): Promise<InvoiceDetail> {
  return http.get<InvoiceDetail>(invoicePath(id))
}

export function loadInvoiceFile(http: BlobClient, id: number, inline = false): Promise<Blob> {
  return http.getBlob(invoiceFilePath(id, inline))
}

/* ---------- E6.1: import and delete ---------- */

/** An Energy Invoices Directory the user may import into (GET /web/invoices/directories). */
export interface InvoiceDirectory {
  id: number
  name: string
  path: string | null
  site: { id: number; name: string } | null
}

/** Answer of POST /invoices/parse (docs/ENERGY-INVOICES.md); amounts as in the document. */
export interface ParsedInvoice {
  fileName?: string | null
  format: string | null
  number: string | null
  date: string | null
  seller: string | null
  sellerVatId?: string | null
  buyer?: string | null
  periodStart: string | null
  periodEnd: string | null
  currency: string | null
  net: number | null
  tax?: number | null
  gross: number | null
  consumption: { value: number; unit: string | null } | null
  meterPoint?: string | null
  medium: string | null
  mediumReasons?: string[] | null
  items?: InvoiceItem[] | null
  /** id of an existing invoice with the same seller and number below the directory, else null */
  duplicate: number | null
  warnings?: string[] | null
}

/** Answer of POST /invoices/import (201). */
export interface ImportedInvoice {
  invoiceId: number
  folderId: number
  series?: Record<string, number> | null
  warnings?: string[] | null
}

/** Error body of the parse / import endpoints: `{ error, message }`; 409 DUPLICATE carries `existing`. */
export interface InvoiceApiError {
  error?: string
  message?: string
  existing?: number
}

export function directoriesPath(lang: string): string {
  return `/web/invoices/directories?lang=${encodeURIComponent(lang)}`
}

export function parsePath(directory: number): string {
  return `/invoices/parse?directory=${directory}`
}

export function importPath(directory: number, medium: InvoiceMedium): string {
  return `/invoices/import?directory=${directory}&medium=${encodeURIComponent(medium)}`
}

export function deleteInvoicePath(id: number, version: string): string {
  return `/web/invoices/${id}?version=${encodeURIComponent(version)}`
}

export function loadInvoiceDirectories(http: GetClient, lang: string): Promise<InvoiceDirectory[]> {
  return http.get<InvoiceDirectory[]>(directoriesPath(lang))
}

function fileForm(file: File): FormData {
  const form = new FormData()
  form.append('file', file, file.name)
  return form
}

/** POST /JEWebService/v1/invoices/parse?directory= (multipart `file`): parsed without storing. */
export function parseInvoiceFile(http: Pick<HttpClient, 'postForm'>, directory: number, file: File): Promise<ParsedInvoice> {
  return http.postForm<ParsedInvoice>(parsePath(directory), fileForm(file))
}

/** POST /JEWebService/v1/invoices/import?directory=&medium= (multipart `file`): 409 DUPLICATE if it exists. */
export function importInvoiceFile(http: Pick<HttpClient, 'postForm'>, directory: number, medium: InvoiceMedium, file: File): Promise<ImportedInvoice> {
  return http.postForm<ImportedInvoice>(importPath(directory, medium), fileForm(file))
}

/* ---------- R01: manual invoice ---------- */

/** Body (part `data`) of POST /invoices/manual; amounts as numbers, dates yyyy-MM-dd. */
export interface ManualInvoiceBody {
  medium: InvoiceMedium
  seller: string
  sellerVatId?: string
  number: string
  date: string
  periodStart?: string
  periodEnd?: string
  meterPoint?: string
  creditNote?: boolean
  lines: { description: string; quantity?: number; unit?: string; unitPrice?: number; net?: number; taxRate?: number }[]
  net?: number
  tax?: number
  gross?: number
  consumption?: { value: number; unit: 'KWH' | 'MWH' | 'MTQ' }
}

export function manualPath(directory: number): string {
  return `/invoices/manual?directory=${directory}`
}

/** POST /JEWebService/v1/invoices/manual?directory= (multipart `data` JSON + optional `file`): 201 as the import, 409 DUPLICATE. */
export function saveManualInvoice(http: Pick<HttpClient, 'postForm'>, directory: number, body: ManualInvoiceBody, file: File | null): Promise<ImportedInvoice> {
  const form = new FormData()
  form.append('data', JSON.stringify(body))
  if (file) form.append('file', file, file.name)
  return http.postForm<ImportedInvoice>(manualPath(directory), form)
}

/** POST /web/invoices/{id}/check (R03): checks the invoice again and stores the result. */
export function recheckInvoice(http: Pick<HttpClient, 'post'>, id: number): Promise<InvoiceCheckResult> {
  return http.post<InvoiceCheckResult>(`/web/invoices/${id}/check`)
}

/** DELETE /web/invoices/{id}?version=: recycle bin, amounts taken out of the series; 409 if changed meanwhile. */
export function deleteInvoice(http: Pick<HttpClient, 'delete'>, id: number, version: string): Promise<void> {
  return http.delete(deleteInvoicePath(id, version))
}
