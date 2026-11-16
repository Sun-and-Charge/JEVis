/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import {
  MAX_INVOICE_FILE,
  applyParseError,
  applyParsed,
  checkedRows,
  directoryLabel,
  duplicateOf,
  importDirty,
  importSummary,
  importable,
  invoiceErrorText,
  invoiceFileProblem,
  newRows,
  parseProgress,
  preselectDirectory,
  type ImportRow,
} from '../src/accounting/invoiceImport'
import { HttpError } from '../src/api/http'
import { deleteInvoice, deleteInvoicePath, directoriesPath, importInvoiceFile, importPath, parseInvoiceFile, parsePath, type InvoiceDirectory } from '../src/api/invoices'
import { i18n } from '../src/i18n'
import { parsedInvoice } from './fixtures/accounting'
import { httpError } from './fixtures/edit'

/* Invoice import in the web (E6.1): pure helpers and API paths; neutral placeholder data. */

const file = (name: string, size = 100, type = '') => {
  const f = new File(['x'], name, { type })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

const parsed = parsedInvoice

const rowsOf = (...names: string[]): ImportRow[] => newRows(names.map((n) => file(n)), 1, (p, f) => `${p}:${f.name}`)

const t = (key: string, values?: Record<string, unknown>) => i18n.global.t(key, values ?? {})

describe('invoice import helpers', () => {
  it('accepts PDF and XML up to 20 MB, by name or type', () => {
    expect(invoiceFileProblem(file('a.pdf'))).toBeNull()
    expect(invoiceFileProblem(file('B.XML'))).toBeNull()
    expect(invoiceFileProblem(file('scan', 10, 'application/pdf'))).toBeNull()
    expect(invoiceFileProblem(file('bild.png', 10, 'image/png'))).toBe('type')
    expect(invoiceFileProblem(file('gross.pdf', MAX_INVOICE_FILE))).toBeNull()
    expect(invoiceFileProblem(file('gross.pdf', MAX_INVOICE_FILE + 1))).toBe('size')
  })

  it('creates rows: refused files are errors right away, the others wait', () => {
    const rows = newRows([file('a.pdf'), file('b.txt'), file('c.xml', MAX_INVOICE_FILE + 1)], 5, (p, f) => `${p}:${f.name}`)
    expect(rows.map((r) => [r.key, r.parse, r.error])).toEqual([
      [5, 'pending', null],
      [6, 'error', 'type:b.txt'],
      [7, 'error', 'size:c.xml'],
    ])
    expect(rows.every((r) => !r.checked && r.result === 'none')).toBe(true)
  })

  it('takes the parse answer: new rows checked, duplicates not, medium preselected (unknown → OTHER)', () => {
    const [a, b, c] = rowsOf('a.pdf', 'b.pdf', 'c.pdf')
    applyParsed(a, parsed())
    applyParsed(b, parsed({ duplicate: 24601, medium: 'GAS' }))
    applyParsed(c, parsed({ medium: 'WATER' }))
    expect([a.parse, a.checked, a.medium, a.existing]).toEqual(['new', true, 'ELECTRICITY', null])
    expect([b.parse, b.checked, b.medium, b.existing]).toEqual(['duplicate', false, 'GAS', 24601])
    expect(c.medium).toBe('OTHER')
    applyParseError(c, 'kaputt')
    expect([c.parse, c.checked, c.error, importable(c)]).toEqual(['error', false, 'kaputt', false])
  })

  it('counts chosen rows, progress, dirty state and the summary', () => {
    const rows = rowsOf('a.pdf', 'b.pdf', 'c.pdf')
    expect(parseProgress(rows)).toEqual({ done: 0, total: 3 })
    expect(importDirty(rows)).toBe(true)
    rows.forEach((r) => applyParsed(r, parsed()))
    expect(parseProgress(rows)).toEqual({ done: 3, total: 3 })
    expect(checkedRows(rows)).toHaveLength(3)
    rows[0].result = 'imported'
    rows[1].result = 'skipped'
    rows[2].result = 'error'
    expect(checkedRows(rows)).toEqual([rows[2]])
    expect(importDirty(rows)).toBe(true)
    rows[2].checked = false
    expect(importDirty(rows)).toBe(false)
    expect(importSummary(rows)).toEqual({ imported: 1, skipped: 1, failed: 1 })
  })

  it('translates error codes; 413 is too large; other messages from the server or the general text', () => {
    i18n.global.locale.value = 'de'
    expect(invoiceErrorText(httpError(400, { error: 'NOT_AN_E_INVOICE', message: 'no XML' }), t)).toBe('Keine E-Rechnung (ZUGFeRD, Factur-X oder XRechnung).')
    expect(invoiceErrorText(httpError(413, ''), t)).toBe('Die Datei ist größer als 20 MB.')
    expect(invoiceErrorText(httpError(400, { error: 'SOMETHING', message: 'Grund X' }), t)).toBe('Grund X')
    expect(invoiceErrorText(httpError(403, { error: 'FORBIDDEN', message: 'no' }), t)).toBe('Dafür fehlt Ihnen die Berechtigung.')
    expect(invoiceErrorText(new Error('network'), t)).toBe('Das Speichern ist fehlgeschlagen. Bitte später erneut versuchen.')
  })

  it('reads the existing id of a 409 DUPLICATE', () => {
    expect(duplicateOf(httpError(409, { error: 'DUPLICATE', existing: 24601 }))).toBe(24601)
    expect(duplicateOf(httpError(409, ''))).toBeNull()
    expect(duplicateOf(httpError(400, { error: 'DUPLICATE' }))).toBeUndefined()
  })

  it('labels and preselects folders by the site picker, else the only one', () => {
    const dirs: InvoiceDirectory[] = [
      { id: 1, name: 'Energierechnungen', path: 'Energieverträge', site: { id: 7100, name: 'Musterwerk' } },
      { id: 2, name: 'Rechnungen', path: null, site: { id: 7101, name: 'Zweigwerk' } },
    ]
    expect(directoryLabel(dirs[0])).toBe('Musterwerk – Energieverträge / Energierechnungen')
    expect(directoryLabel({ id: 3, name: 'Ablage', path: null, site: null })).toBe('Ablage')
    expect(preselectDirectory(dirs, 7101, null)).toBe(2)
    expect(preselectDirectory(dirs, 'all', null)).toBeNull()
    expect(preselectDirectory([dirs[0]], 'all', null)).toBe(1)
    expect(preselectDirectory([dirs[0]], 7101, null)).toBe(1)
    expect(preselectDirectory([], 7100, null)).toBeNull()
  })
})

describe('invoice import API', () => {
  it('builds the paths and sends the file as multipart field "file"', async () => {
    expect(directoriesPath('de')).toBe('/web/invoices/directories?lang=de')
    expect(parsePath(12)).toBe('/invoices/parse?directory=12')
    expect(importPath(12, 'DISTRICT_HEATING')).toBe('/invoices/import?directory=12&medium=DISTRICT_HEATING')
    expect(deleteInvoicePath(24601, '2026-11-03T10:00:00.000+01:00')).toBe('/web/invoices/24601?version=2026-11-03T10%3A00%3A00.000%2B01%3A00')
    const postForm = vi.fn(async () => ({}))
    const f = file('a.pdf')
    await parseInvoiceFile({ postForm } as never, 12, f)
    await importInvoiceFile({ postForm } as never, 12, 'GAS', f)
    expect(postForm.mock.calls.map((c) => (c as unknown[])[0])).toEqual(['/invoices/parse?directory=12', '/invoices/import?directory=12&medium=GAS'])
    expect(((postForm.mock.calls[0] as unknown[])[1] as FormData).get('file')).toBeInstanceOf(File)
    const del = vi.fn(async () => undefined)
    await deleteInvoice({ delete: del } as never, 5, 'v1')
    expect(del).toHaveBeenCalledWith('/web/invoices/5?version=v1')
  })

  it('HttpError json of the error body', () => {
    expect(new HttpError(400, '{"error":"UNREADABLE"}').json()).toEqual({ error: 'UNREADABLE' })
  })
})
