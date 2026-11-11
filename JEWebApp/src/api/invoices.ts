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
