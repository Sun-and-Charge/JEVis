/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { HttpError } from '../api/http'
import type { InvoiceApiError, InvoiceDirectory, InvoiceMedium, ParsedInvoice } from '../api/invoices'
import { writeErrorText } from '../edit/edit'
import { preferredBySite } from '../nav/catalog'
import type { SiteChoiceValue } from '../nav/prefs'
import type { Navigation } from '../api/navigation'
import { invoiceMedium } from './invoices'

/*
 * Invoice import in the web (E6.1), as the Control Center's InvoiceImportDialog: the chosen files are checked here
 * (type, 20 MB), parsed one at a time (POST /invoices/parse), reviewed (import checkbox, medium) and imported one at
 * a time (POST /invoices/import); a 409 DUPLICATE is "skipped". Pure state helpers; the dialog holds the rows.
 */

/** Largest file the web service accepts (413 above). */
export const MAX_INVOICE_FILE = 20 * 1024 * 1024

/** File types of the file chooser. */
export const INVOICE_ACCEPT = '.pdf,.xml,application/pdf,application/xml,text/xml'

/** pending: waits for the parse; parsing: request running; new / duplicate: parsed; error: refused here or by the server */
export type ParseState = 'pending' | 'parsing' | 'new' | 'duplicate' | 'error'
/** none: not imported yet; importing: request running; imported; skipped: 409 duplicate; error */
export type ImportState = 'none' | 'importing' | 'imported' | 'skipped' | 'error'

export interface ImportRow {
  key: number
  file: File
  parse: ParseState
  parsed: ParsedInvoice | null
  /** message of a parse error (translated) */
  error: string | null
  checked: boolean
  medium: InvoiceMedium
  result: ImportState
  /** message of an import error (translated) */
  resultMessage: string | null
  /** existing invoice of a duplicate (parse answer or the 409 of the import) */
  existing: number | null
  /** id of the invoice created by the import */
  invoiceId: number | null
  /** warnings of the import answer */
  importWarnings: string[]
}

export type FileProblem = 'type' | 'size' | null

/** A file the import cannot take: no PDF / XML (by name or type), larger than 20 MB. */
export function invoiceFileProblem(file: Pick<File, 'name' | 'size' | 'type'>): FileProblem {
  const name = (file.name ?? '').trim().toLowerCase()
  const type = (file.type ?? '').toLowerCase()
  const typeOk = /\.(pdf|xml)$/.test(name) || type === 'application/pdf' || type === 'application/xml' || type === 'text/xml'
  if (!typeOk) return 'type'
  if (file.size > MAX_INVOICE_FILE) return 'size'
  return null
}

/** New rows for chosen files: those with a problem are errors right away, the others wait for the parse. */
export function newRows(files: readonly File[], firstKey: number, problemText: (problem: Exclude<FileProblem, null>, file: File) => string): ImportRow[] {
  return files.map((file, i) => {
    const problem = invoiceFileProblem(file)
    return {
      key: firstKey + i,
      file,
      parse: problem ? 'error' : 'pending',
      parsed: null,
      error: problem ? problemText(problem, file) : null,
      checked: false,
      medium: 'OTHER',
      result: 'none',
      resultMessage: null,
      existing: null,
      invoiceId: null,
      importWarnings: [],
    }
  })
}

/** Takes the parse answer: new rows are checked, duplicates not (they may be checked); medium preselected. */
export function applyParsed(row: ImportRow, parsed: ParsedInvoice): void {
  row.parsed = parsed
  row.error = null
  row.medium = invoiceMedium({ medium: parsed?.medium ?? null })
  const duplicate = typeof parsed?.duplicate === 'number' ? parsed.duplicate : null
  row.existing = duplicate
  row.parse = duplicate != null ? 'duplicate' : 'new'
  row.checked = duplicate == null
}

export function applyParseError(row: ImportRow, message: string): void {
  row.parse = 'error'
  row.parsed = null
  row.error = message
  row.checked = false
}

/** The row can still be imported: parsed, not imported or skipped yet. */
export function importable(row: ImportRow): boolean {
  return (row.parse === 'new' || row.parse === 'duplicate') && row.result !== 'imported' && row.result !== 'skipped' && row.result !== 'importing'
}

export function checkedRows(rows: readonly ImportRow[]): ImportRow[] {
  return rows.filter((r) => r.checked && importable(r))
}

export function parsing(rows: readonly ImportRow[]): boolean {
  return rows.some((r) => r.parse === 'pending' || r.parse === 'parsing')
}

/** Progress of the parse: rows done (parsed or refused) of all rows. */
export function parseProgress(rows: readonly ImportRow[]): { done: number; total: number } {
  return { done: rows.filter((r) => r.parse !== 'pending' && r.parse !== 'parsing').length, total: rows.length }
}

/** Unsaved work: files still parsed, or rows chosen but not imported. */
export function importDirty(rows: readonly ImportRow[]): boolean {
  return parsing(rows) || checkedRows(rows).length > 0
}

export interface ImportSummary {
  imported: number
  skipped: number
  failed: number
}

export function importSummary(rows: readonly ImportRow[]): ImportSummary {
  return {
    imported: rows.filter((r) => r.result === 'imported').length,
    skipped: rows.filter((r) => r.result === 'skipped').length,
    failed: rows.filter((r) => r.result === 'error').length,
  }
}

/** Error body of the parse / import endpoints, null if none or no JSON. */
export function invoiceApiError(e: unknown): InvoiceApiError | null {
  if (!(e instanceof HttpError)) return null
  const body = e.json<InvoiceApiError>()
  return body && typeof body === 'object' && !Array.isArray(body) ? body : null
}

/** Error codes with an own text. */
export const INVOICE_ERROR_CODES = ['NOT_AN_E_INVOICE', 'UNREADABLE', 'NO_FILE', 'TOO_LARGE', 'INVALID_MEDIUM', 'INVALID_DIRECTORY'] as const

/**
 * Translated message of a failed parse / import: the text of a known error code (`invoiceEdit.import.error.<code>`,
 * 413 = TOO_LARGE), else the server's message, else the general write error (403, 401, network …).
 */
export function invoiceErrorText(e: unknown, t: (key: string, values?: Record<string, unknown>) => string): string {
  const body = invoiceApiError(e)
  const code = e instanceof HttpError && e.status === 413 ? 'TOO_LARGE' : (body?.error ?? '')
  if ((INVOICE_ERROR_CODES as readonly string[]).includes(code)) return t('invoiceEdit.import.error.' + code)
  if (e instanceof HttpError && (e.status === 400 || e.status === 500) && typeof body?.message === 'string' && body.message.trim()) {
    return body.message.trim()
  }
  return writeErrorText(e, t)
}

/** 409 DUPLICATE of the import: the existing invoice id (null if not given); undefined if it is no duplicate answer. */
export function duplicateOf(e: unknown): number | null | undefined {
  if (!(e instanceof HttpError) || e.status !== 409) return undefined
  const existing = invoiceApiError(e)?.existing
  return typeof existing === 'number' ? existing : null
}

/** "Musterwerk – Energieverträge / Energierechnungen" (site, path above, name). */
export function directoryLabel(d: InvoiceDirectory): string {
  const where = [d.path, d.name].filter((p) => p && p.trim()).join(' / ')
  return d.site?.name ? `${d.site.name} – ${where}` : where
}

/** Preselected folder: the one of the site chosen in the site picker, else the only one; null if open. */
export function preselectDirectory(dirs: readonly InvoiceDirectory[], choice: SiteChoiceValue, nav: Pick<Navigation, 'sites'> | null): number | null {
  if (!dirs.length) return null
  const bySite = preferredBySite(
    dirs.map((d) => ({ key: d.id, site: d.site })),
    choice,
    nav,
  )
  if (bySite != null) return bySite
  return dirs.length === 1 ? dirs[0].id : null
}
