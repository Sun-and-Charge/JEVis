/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../src/api/http'
import type { Invoice, InvoiceDirectory, InvoiceList, ParsedInvoice } from '../src/api/invoices'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import { useNavPrefsStore } from '../src/stores/navPrefs'
import InvoicesView from '../src/views/InvoicesView.vue'
import { invoice, invoiceDetail, invoiceSet, parsedInvoice } from './fixtures/accounting'
import { httpError } from './fixtures/edit'

/* Invoice import and delete in the web (E6.1) with a mocked API; neutral placeholder data. */

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

const Stub = defineComponent({ render: () => h('div') })
type Fn = (...args: never[]) => unknown

const DIRS: InvoiceDirectory[] = [
  { id: 900, name: 'Energierechnungen', path: 'Energieverträge', site: { id: 7100, name: 'Musterwerk' } },
  { id: 901, name: 'Rechnungen', path: null, site: { id: 7101, name: 'Zweigwerk' } },
]
const V1 = '2026-11-03T10:00:00.000+01:00'

function list(overrides: Partial<InvoiceList> = {}, invoices: Invoice[] = invoiceSet().map((i) => ({ ...i, canDelete: true, version: V1 }))): InvoiceList {
  return { invoices, truncated: false, canImport: true, ...overrides }
}

function api(listAnswer: () => InvoiceList, dirs: () => InvoiceDirectory[] | Error = () => DIRS) {
  return vi.fn(async (path: string) => {
    if (path.startsWith('/web/invoices?')) return listAnswer()
    if (path.startsWith('/web/invoices/directories')) {
      const d = dirs()
      if (d instanceof Error) throw d
      return d
    }
    const m = /^\/web\/invoices\/(\d+)$/.exec(path)
    if (m) {
      const base = invoiceSet().find((i) => i.id === Number(m[1]))
      if (!base) throw new HttpError(404, '')
      return invoiceDetail(base)
    }
    throw new Error('unexpected ' + path)
  })
}

async function mountView(http: Partial<Record<'get' | 'postForm' | 'delete' | 'getBlob', Fn>>) {
  const auth = useAuthStore()
  Object.assign(auth.http, http)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }, { path: '/x', name: 'x', component: Stub }] })
  await router.push('/')
  const wrapper = mount(InvoicesView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

const dialogs = () => Array.from(document.querySelectorAll<HTMLElement>('[role="dialog"]'))
const importDialog = () => dialogs()[0]
const q = <T extends Element = HTMLElement>(sel: string, root: ParentNode = importDialog()) => root.querySelector<T>(sel)
const qa = <T extends Element = HTMLElement>(sel: string, root: ParentNode = importDialog()) => Array.from(root.querySelectorAll<T>(sel))
const file = (name: string, size = 100) => {
  const f = new File(['x'], name, { type: name.endsWith('.xml') ? 'application/xml' : name.endsWith('.pdf') ? 'application/pdf' : '' })
  if (size !== 100) Object.defineProperty(f, 'size', { value: size })
  return f
}

async function choose(files: File[]) {
  const input = q<HTMLInputElement>('input[type="file"]')!
  Object.defineProperty(input, 'files', { value: files, configurable: true })
  input.dispatchEvent(new Event('change'))
  await flushPromises()
}

async function click(el: Element | null) {
  expect(el).not.toBeNull()
  ;(el as HTMLElement).click()
  await flushPromises()
}

async function openImport(w: Awaited<ReturnType<typeof mountView>>['wrapper']) {
  await w.find('.inv-import').trigger('click')
  await flushPromises()
}

describe('invoice import and delete (mocked API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 16, 12, 0))
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('shows the import button only with canImport; the hint follows the rights', async () => {
    const { wrapper: w } = await mountView({ get: api(() => list({ canImport: false }, invoiceSet())) })
    expect(w.find('.inv-import').exists()).toBe(false)
    expect(w.text()).toContain('Nur Ansicht')
    w.unmount()
    const { wrapper: w2 } = await mountView({ get: api(() => list()) })
    expect(w2.find('.inv-import').text()).toBe('Rechnungen importieren')
    expect(w2.text()).toContain('Rechnungen können hier importiert und gelöscht werden')
    w2.unmount()
  })

  it('preselects the folder of the site picker, parses one file at a time with progress and shows the review', async () => {
    useNavPrefsStore().setSite(7101)
    const resolvers: ((v: ParsedInvoice) => void)[] = []
    const postForm = vi.fn((_path: string, _form: FormData) => new Promise<ParsedInvoice>((resolve) => resolvers.push(resolve)))
    const { wrapper: w } = await mountView({ get: api(() => list()), postForm })
    await openImport(w)
    const select = q<HTMLSelectElement>('select')!
    expect(select.value).toBe('901')
    expect(qa('option', select).map((o) => o.textContent)).toEqual(['Musterwerk – Energieverträge / Energierechnungen', 'Zweigwerk – Rechnungen'])

    await choose([file('a.pdf'), file('b.xml'), file('bild.png'), file('gross.pdf', 21 * 1024 * 1024)])
    /* one request at a time */
    expect(postForm).toHaveBeenCalledTimes(1)
    expect(postForm.mock.calls[0][0]).toBe('/invoices/parse?directory=901')
    expect(((postForm.mock.calls[0] as unknown[])[1] as FormData).get('file')).toBeInstanceOf(File)
    expect(q('.inv-import-progress')!.textContent).toContain('Dateien werden gelesen … 2 von 4')
    expect(q<HTMLSelectElement>('select')!.disabled).toBe(true)
    expect(q<HTMLButtonElement>('.edit-submit')!.disabled).toBe(true)

    resolvers[0](parsedInvoice())
    await flushPromises()
    expect(postForm).toHaveBeenCalledTimes(2)
    resolvers[1](parsedInvoice({ fileName: 'b.xml', number: 'R-2', medium: 'GAS', duplicate: 24604, warnings: ['Die Rechnung nennt den Verbrauch 2-mal.'] }))
    await flushPromises()
    expect(q('.inv-import-progress')).toBeNull()

    const rows = qa('tr.inv-import-row')
    expect(rows.map((r) => r.dataset.status)).toEqual(['new', 'duplicate', 'error', 'error'])
    expect(rows[0].textContent).toContain('R-1001')
    expect(rows[0].textContent).toContain('01.09.2026 – 30.09.2026')
    expect(rows[0].textContent).toContain('4.321 kWh')
    expect(rows[0].textContent).toContain('1.469,13 EUR')
    expect(q<HTMLInputElement>('input[type="checkbox"]', rows[0])!.checked).toBe(true)
    expect(q<HTMLInputElement>('input[type="checkbox"]', rows[1])!.checked).toBe(false)
    expect(q<HTMLInputElement>('input[type="checkbox"]', rows[2])!.disabled).toBe(true)
    const medium = q<HTMLSelectElement>('select', rows[1])!
    expect(medium.value).toBe('GAS')
    expect(qa('option', medium).map((o) => o.textContent)).toEqual(['Strom', 'Gas', 'Fernwärme', 'Sonstiges'])
    expect(q<HTMLSelectElement>('select', rows[0])!.title).toBe('Erkannt an: Einheit kWh')
    expect(q('.inv-status', rows[0])!.textContent).toBe('Neu')
    expect(q('.inv-existing', rows[1])!.textContent).toContain('Duplikat')
    expect(importDialog().textContent).toContain('Die Rechnung nennt den Verbrauch 2-mal.')
    expect(importDialog().textContent).toContain('„bild.png“ ist keine PDF- oder XML-Datei.')
    expect(importDialog().textContent).toContain('„gross.pdf“ ist größer als 20 MB.')
    expect(q('.edit-submit')!.textContent).toContain('Importieren (1)')
    w.unmount()
  })

  it('imports the checked rows one at a time: imported, skipped on 409, error; summary and reload', async () => {
    const get = api(() => list())
    const postForm = vi.fn(async (path: string, form: FormData) => {
      const name = (form.get('file') as File).name
      if (path.startsWith('/invoices/parse')) {
        if (name === 'kaputt.pdf') throw httpError(400, { error: 'NOT_AN_E_INVOICE', message: 'x' })
        return parsedInvoice({ number: name, duplicate: name === 'dup.pdf' ? 24601 : null })
      }
      if (name === 'dup.pdf') throw httpError(409, { error: 'DUPLICATE', existing: 24601 })
      if (name === 'c.pdf') throw httpError(403, { error: 'FORBIDDEN', message: 'no' })
      return { invoiceId: 25000, folderId: 901, series: {}, warnings: ['Einheit weicht ab.'] }
    })
    const { wrapper: w } = await mountView({ get, postForm })
    await openImport(w)
    q<HTMLSelectElement>('select')!.value = '900'
    q<HTMLSelectElement>('select')!.dispatchEvent(new Event('change'))
    await flushPromises()
    await choose([file('a.pdf'), file('dup.pdf'), file('c.pdf'), file('kaputt.pdf')])
    const rows = qa('tr.inv-import-row')
    expect(rows.map((r) => r.dataset.status)).toEqual(['new', 'duplicate', 'new', 'error'])
    expect(importDialog().textContent).toContain('Keine E-Rechnung (ZUGFeRD, Factur-X oder XRechnung).')
    /* the duplicate may be checked; medium changed */
    await click(q('input[type="checkbox"]', rows[1]))
    const medium = q<HTMLSelectElement>('select', rows[0])!
    medium.value = 'DISTRICT_HEATING'
    medium.dispatchEvent(new Event('change'))
    await flushPromises()
    expect(q('.edit-submit')!.textContent).toContain('Importieren (3)')
    const listCalls = () => get.mock.calls.filter((c) => (c[0] as string).startsWith('/web/invoices?')).length
    expect(listCalls()).toBe(1)

    await click(q('.edit-submit'))
    const imports = postForm.mock.calls.filter((c) => (c[0] as string).startsWith('/invoices/import'))
    expect(imports.map((c) => c[0])).toEqual([
      '/invoices/import?directory=900&medium=DISTRICT_HEATING',
      '/invoices/import?directory=900&medium=ELECTRICITY',
      '/invoices/import?directory=900&medium=ELECTRICITY',
    ])
    const after = qa('tr.inv-import-row')
    expect(after.map((r) => r.dataset.status)).toEqual(['imported', 'skipped', 'error', 'error'])
    expect(after.map((r) => q('.inv-status', r)!.textContent!.trim())).toEqual(['Importiert', 'Übersprungen ›', 'Fehler', 'Fehler'])
    expect(importDialog().textContent).toContain('Einheit weicht ab.')
    expect(importDialog().textContent).toContain('Dafür fehlt Ihnen die Berechtigung.')
    expect(q('.inv-import-summary')!.textContent).toContain('1 importiert, 1 übersprungen, 1 mit Fehler.')
    expect(listCalls()).toBe(2)
    /* the failed row stays chosen (try again); imported and skipped ones are locked */
    expect(q<HTMLInputElement>('input[type="checkbox"]', after[0])!.disabled).toBe(true)
    expect(q('.edit-submit')!.textContent).toContain('Importieren (1)')
    w.unmount()
  })

  it('shows the existing invoice of a duplicate in a dialog above the import', async () => {
    const postForm = vi.fn(async () => parsedInvoice({ duplicate: 24601 }))
    const { wrapper: w } = await mountView({ get: api(() => list()), postForm })
    await openImport(w)
    q<HTMLSelectElement>('select')!.value = '900'
    q<HTMLSelectElement>('select')!.dispatchEvent(new Event('change'))
    await flushPromises()
    await choose([file('a.pdf')])
    await click(q('.inv-existing'))
    expect(dialogs()).toHaveLength(2)
    expect(dialogs()[1].textContent).toContain('Vorhandene Rechnung')
    expect(dialogs()[1].querySelector('.inv-details')!.textContent).toContain('EN-202610-S')
    w.unmount()
  })

  it('asks before closing with chosen rows; without them it closes right away', async () => {
    const postForm = vi.fn(async () => parsedInvoice())
    const { wrapper: w } = await mountView({ get: api(() => list()), postForm })
    await openImport(w)
    await click(q('.inv-import-close'))
    expect(dialogs()).toHaveLength(0)

    await openImport(w)
    q<HTMLSelectElement>('select')!.value = '900'
    q<HTMLSelectElement>('select')!.dispatchEvent(new Event('change'))
    await flushPromises()
    await choose([file('a.pdf')])
    await click(q('.inv-import-close'))
    expect(dialogs()).toHaveLength(2)
    expect(dialogs()[1].textContent).toContain('Gewählte Rechnungen wurden noch nicht importiert.')
    await click(dialogs()[1].querySelector('.edit-btn'))
    expect(dialogs()).toHaveLength(1)
    await click(q('.inv-import-close'))
    await click(dialogs()[1].querySelector('.edit-submit'))
    expect(dialogs()).toHaveLength(0)
    w.unmount()
  })

  it('holds the browser back on unload while rows are chosen but not imported', async () => {
    const postForm = vi.fn(async () => parsedInvoice())
    const { wrapper: w } = await mountView({ get: api(() => list()), postForm })
    await openImport(w)
    const unload = () => {
      const e = new Event('beforeunload', { cancelable: true })
      window.dispatchEvent(e)
      return e.defaultPrevented
    }
    expect(unload()).toBe(false)
    q<HTMLSelectElement>('select')!.value = '900'
    q<HTMLSelectElement>('select')!.dispatchEvent(new Event('change'))
    await flushPromises()
    await choose([file('a.pdf')])
    expect(unload()).toBe(true)
    /* unchecked: nothing left to import */
    await click(q('input[type="checkbox"]'))
    expect(unload()).toBe(false)
    w.unmount()
  })

  it('without a folder there is no file choice; folder errors can be retried', async () => {
    let fail = true
    const { wrapper: w } = await mountView({ get: api(() => list(), () => (fail ? new HttpError(500, '') : [])) })
    await openImport(w)
    expect(importDialog().textContent).toContain('Die Ordner konnten nicht geladen werden.')
    fail = false
    await click(q('.retry'))
    expect(importDialog().textContent).toContain('Es gibt keinen Rechnungsordner, in den Sie importieren dürfen.')
    expect(q('input[type="file"]')).toBeNull()
    w.unmount()
  })

  it('several folders without site choice: none preselected, files only after choosing one', async () => {
    const postForm = vi.fn()
    const { wrapper: w } = await mountView({ get: api(() => list()), postForm })
    await openImport(w)
    expect(q<HTMLSelectElement>('select')!.value).toBe('')
    expect(q<HTMLInputElement>('input[type="file"]')!.disabled).toBe(true)
    expect(importDialog().textContent).toContain('Bitte zuerst einen Ordner wählen.')
    w.unmount()
  })

  it('shows cards on phone width', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    const postForm = vi.fn(async () => parsedInvoice())
    const { wrapper: w } = await mountView({ get: api(() => list()), postForm })
    await openImport(w)
    q<HTMLSelectElement>('select')!.value = '900'
    q<HTMLSelectElement>('select')!.dispatchEvent(new Event('change'))
    await flushPromises()
    await choose([file('a.pdf')])
    expect(q('table.inv-import-table')).toBeNull()
    const card = q('.inv-import-card')!
    expect(card.dataset.status).toBe('new')
    expect(card.textContent).toContain('R-1001')
    expect(card.textContent).toContain('Erkannt an: Einheit kWh')
    w.unmount()
  })

  it('deletes an invoice with its version after the question; 409 reloads with a message', async () => {
    let invoices = list()
    const get = api(() => invoices)
    const del = vi.fn(async () => {
      invoices = list({}, invoices.invoices.slice(1))
    })
    const { wrapper: w } = await mountView({ get, delete: del })
    await w.findAll('tbody tr.inv-row')[0].trigger('click')
    await flushPromises()
    await w.find('.inv-delete').trigger('click')
    expect(dialogs()[0].textContent).toContain('Rechnung löschen')
    expect(dialogs()[0].textContent).toContain('Die Rechnung wird in den Papierkorb gelegt und aus den Rechnungsreihen herausgerechnet.')
    await click(dialogs()[0].querySelector('.edit-submit'))
    expect(del).toHaveBeenCalledWith('/web/invoices/24601?version=' + encodeURIComponent(V1))
    expect(w.find('.inv-message').text()).toBe('Rechnung EN-202610-S wurde in den Papierkorb gelegt.')
    expect(w.findAll('tbody tr.inv-row')).toHaveLength(3)

    const conflict = vi.fn().mockRejectedValue(httpError(409, invoice(24604)))
    Object.assign(useAuthStore().http, { delete: conflict })
    await w.findAll('tbody tr.inv-row')[0].trigger('click')
    await flushPromises()
    await w.find('.inv-delete').trigger('click')
    await click(dialogs()[0].querySelector('.edit-submit'))
    expect(dialogs()).toHaveLength(0)
    expect(w.find('.inv-message').text()).toContain('wurde inzwischen geändert und nicht gelöscht')
    w.unmount()
  })

  it('no delete button without canDelete; other delete errors stay in the question', async () => {
    const { wrapper: w } = await mountView({ get: api(() => list({}, [invoice(24601, { canDelete: false, version: V1 }), invoice(24600, { number: 'X-1', canDelete: true, version: null })])), delete: vi.fn().mockRejectedValue(httpError(403, '')) })
    await w.findAll('tbody tr.inv-row')[0].trigger('click')
    await flushPromises()
    expect(w.find('.inv-delete').exists()).toBe(false)
    await w.findAll('tbody tr.inv-row')[1].trigger('click')
    await flushPromises()
    await w.find('.inv-delete').trigger('click')
    await click(dialogs()[0].querySelector('.edit-submit'))
    expect(dialogs()[0].textContent).toContain('Dafür fehlt Ihnen die Berechtigung.')
    w.unmount()
  })
})
