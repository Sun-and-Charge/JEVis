/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../src/api/http'
import type { InvoiceDetail, InvoiceList } from '../src/api/invoices'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import InvoicesView from '../src/views/InvoicesView.vue'
import { invoiceDetail, invoiceSet } from './fixtures/accounting'

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

const saveBlob = vi.hoisted(() => vi.fn())
vi.mock('../src/iso/download', async (importOriginal) => ({ ...(await importOriginal<typeof import('../src/iso/download')>()), saveBlob }))

const Stub = defineComponent({ render: () => h('div') })

/** Text of all text nodes joined by spaces (the compiled templates leave no white space between elements). */
function txt(w: { element: Node }): string {
  const parts: string[] = []
  const walk = (n: Node) => {
    if (n.nodeType === 3) parts.push(n.nodeValue ?? '')
    else n.childNodes.forEach(walk)
  }
  walk(w.element)
  return parts.map((p) => p.trim()).filter(Boolean).join(' ')
}

type Answer<T> = T | Error | (() => T | Error)
const resolve = <T>(a: Answer<T>): T => {
  const v = typeof a === 'function' ? (a as () => T | Error)() : a
  if (v instanceof Error) throw v
  return v
}

function api(list: Answer<InvoiceList>, details: Record<number, Answer<InvoiceDetail>> = {}, fileError?: Error) {
  const get = vi.fn(async (path: string) => {
    if (path.startsWith('/web/invoices?')) return resolve(list)
    const m = /^\/web\/invoices\/(\d+)$/.exec(path)
    if (m) {
      const id = Number(m[1])
      if (details[id]) return resolve(details[id])
      const base = invoiceSet().find((i) => i.id === id)
      if (!base) throw new HttpError(404, '')
      return invoiceDetail(base)
    }
    throw new Error('unexpected ' + path)
  })
  const getBlob = vi.fn(async (path: string) => {
    if (fileError) throw fileError
    return path.includes('24604') ? new Blob(['<xml/>'], { type: 'application/xml' }) : new Blob(['%PDF'], { type: 'application/octet-stream' })
  })
  return { get, getBlob }
}

async function mountView(client: ReturnType<typeof api>) {
  const auth = useAuthStore()
  Object.assign(auth.http, client)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }] })
  await router.push('/')
  const wrapper = mount(InvoicesView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

type W = Awaited<ReturnType<typeof mountView>>
const rows = (w: W) => w.findAll('tbody tr.inv-row')
const LIST = (): InvoiceList => ({ invoices: invoiceSet(), truncated: false })

describe('invoices view (mocked API)', () => {
  let created: string[]
  let revoked: string[]

  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    saveBlob.mockClear()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 11, 12, 0))
    created = []
    revoked = []
    let n = 0
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn(() => {
          const url = `blob:test/${++n}`
          created.push(url)
          return url
        }),
        revokeObjectURL: vi.fn((url: string) => revoked.push(url)),
      }),
    )
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('requests the last 24 months with the time zone and lists the invoices, credit notes marked', async () => {
    const client = api(LIST())
    const w = await mountView(client)
    const path = client.get.mock.calls[0][0] as string
    expect(path).toContain('/web/invoices?from=2024-12-01&to=2026-11-30&timeZone=')
    expect(w.text()).toContain('Nur Ansicht – Verträge und Rechnungen werden im Control Center gepflegt bzw. importiert.')
    expect(rows(w)).toHaveLength(4)
    expect(txt(rows(w)[0])).toBe('› 01.10.2026 – 31.10.2026 EN-202610-S 03.11.2026 Energieversorger Nord Strom DE0000000000000000000000000000001 41.250 kWh 11.550,00 2.194,50 13.744,50 EUR')
    const credit = rows(w)[3]
    expect(credit.classes()).toContain('is-credit')
    expect(credit.find('.inv-credit').text()).toBe('Gutschrift')
    expect(credit.findAll('td.is-negative').map((c) => c.text())).toEqual(['-1.000 kWh', '-280,00', '-53,20', '-333,20'])
    expect(w.find('.mod-count').text()).toBe('4 von 4 Rechnungen')
    w.unmount()
  })

  it('shows totals per medium and a monthly chart per medium; credit notes reduce them', async () => {
    const w = await mountView(api(LIST()))
    const tiles = w.findAll('.inv-tile')
    expect(tiles.map((t) => t.attributes('data-medium'))).toEqual(['ELECTRICITY', 'GAS'])
    expect(txt(tiles[0])).toContain('3 Rechnungen , davon 1 Gutschrift')
    expect(tiles[0].find('.inv-sum-consumption').text()).toBe('80.250 kWh')
    expect(tiles[0].find('.inv-sum-net').text()).toBe('22.470,00 EUR')
    expect(tiles[0].find('.inv-sum-gross').text()).toBe('26.739,30 EUR')
    expect(tiles[1].find('.inv-sum-consumption').text()).toBe('1.200 m³')
    const charts = w.findAllComponents({ name: 'VChart' })
    expect(charts).toHaveLength(2)
    const option = charts[0].props('option') as { series: { data: (number | null)[] }[]; xAxis: { data: string[] } }
    expect(option.xAxis.data).toHaveLength(2)
    expect(option.series[0].data).toEqual([39000, 41250])
    expect(option.series[1].data[0]).toBeCloseTo(13328 - 333.2)
    w.unmount()
  })

  it('filters by medium chips, search and site; totals follow the shown invoices', async () => {
    const w = await mountView(api(LIST()))
    const chips = w.findAll('.notes-chip')
    expect(chips.map((c) => txt(c))).toEqual(['Alle', 'Strom 3', 'Gas 1'])
    await w.find('.notes-chip[data-medium="GAS"]').trigger('click')
    expect(rows(w)).toHaveLength(1)
    expect(w.findAll('.inv-tile').map((t) => t.attributes('data-medium'))).toEqual(['GAS'])
    expect(w.find('.notes-chip[data-medium="GAS"]').attributes('aria-pressed')).toBe('true')
    await chips[0].trigger('click')
    expect(rows(w)).toHaveLength(4)
    await w.find('#invoices-search').setValue('gu-2026')
    expect(rows(w)).toHaveLength(1)
    await w.find('#invoices-search').setValue('')
    await w.find('#invoices-site').setValue('7101')
    expect(rows(w)).toHaveLength(1)
    expect(rows(w)[0].text()).toContain('GS-202610')
    await w.find('#invoices-search').setValue('nichts')
    expect(w.text()).toContain('Keine Rechnung passt zum Filter.')
    w.unmount()
  })

  it('checks the time frame: invalid ones are not sent, valid ones load again, reset goes back', async () => {
    const client = api(LIST())
    const w = await mountView(client)
    await w.find('#invoices-from').setValue('2015-01-01')
    await flushPromises()
    expect(w.find('#invoices-range-msg').text()).toBe('Der Zeitraum darf höchstens 10 Jahre lang sein.')
    expect(w.find('#invoices-from').attributes('aria-invalid')).toBe('true')
    expect(client.get).toHaveBeenCalledTimes(1)
    await w.find('#invoices-from').setValue('2027-01-01')
    expect(w.find('#invoices-range-msg').text()).toContain('vor dem Startdatum')
    await w.find('#invoices-from').setValue('2026-01-01')
    await flushPromises()
    expect(client.get).toHaveBeenCalledTimes(2)
    expect(client.get.mock.calls[1][0]).toContain('from=2026-01-01&to=2026-11-30')
    await w.find('.notes-reset').trigger('click')
    await flushPromises()
    expect(client.get.mock.calls[2][0]).toContain('from=2024-12-01&to=2026-11-30')
    w.unmount()
  })

  it('opens the detail panel with all fields and the line items', async () => {
    const client = api(LIST())
    const w = await mountView(client)
    await rows(w)[0].trigger('click')
    await flushPromises()
    expect(client.get).toHaveBeenCalledWith('/web/invoices/24601')
    expect(rows(w)[0].find('.mod-toggle').attributes('aria-expanded')).toBe('true')
    const panel = w.find('#invoice-24601-d')
    const text = txt(panel)
    expect(text).toContain('Rechnung EN-202610-S')
    expect(text).toContain('USt-IdNr. Lieferant DE000000001')
    expect(text).toContain('Rechnungsempfänger Musterkunde GmbH')
    expect(text).toContain('Format ZUGFeRD 2.3 EN16931')
    expect(text).toContain('Brutto 13.744,50 EUR')
    const items = panel.find('.inv-items')
    expect(items.findAll('thead th').map((th) => th.text())).toEqual(['Beschreibung', 'Menge', 'Einheit', 'Einzelpreis', 'Netto', 'Steuersatz', 'Zeitraum'])
    expect(items.findAll(':scope > tbody > tr').map((r) => r.findAll('td').map((c) => c.text()))).toEqual([
      ['Arbeitspreis', '41.250', 'kWh', '0,25', '10.312,50', '19 %', '01.10.2026 – 31.10.2026'],
      ['Grundpreis', '1', 'Monat', '1.237,50', '1.237,50', '19 %', ''],
    ])
    await rows(w)[0].trigger('click')
    expect(w.find('#invoice-24601-d').exists()).toBe(false)
    w.unmount()
  })

  it('shows the PDF inline on request, downloads the file and revokes the preview URL', async () => {
    const client = api(LIST())
    const w = await mountView(client)
    await rows(w)[0].trigger('click')
    await flushPromises()
    expect(client.getBlob).not.toHaveBeenCalled()
    await w.find('.inv-show').trigger('click')
    await flushPromises()
    expect(client.getBlob).toHaveBeenCalledWith('/web/invoices/24601/file?inline=true')
    const pdf = w.find('object.rep-pdf')
    expect(pdf.attributes('data')).toBe('blob:test/1')
    expect(pdf.attributes('type')).toBe('application/pdf')
    expect(w.find('.inv-open').attributes('href')).toBe('blob:test/1')
    await w.find('.inv-download').trigger('click')
    await flushPromises()
    expect(client.getBlob).toHaveBeenLastCalledWith('/web/invoices/24601/file')
    expect(saveBlob.mock.calls[0][1]).toBe('EN-202610-S.pdf')
    await w.find('.inv-hide').trigger('click')
    expect(revoked).toEqual(['blob:test/1'])
    expect(w.find('object.rep-pdf').exists()).toBe(false)
    await w.find('.inv-show').trigger('click')
    await flushPromises()
    w.unmount()
    expect(revoked).toEqual(['blob:test/1', 'blob:test/2'])
  })

  it('offers only the download for XML files and nothing without file', async () => {
    const client = api(LIST())
    const w = await mountView(client)
    await rows(w)[1].trigger('click')
    await flushPromises()
    expect(w.find('.inv-show').exists()).toBe(false)
    await w.find('.inv-download').trigger('click')
    await flushPromises()
    expect(client.getBlob).toHaveBeenCalledWith('/web/invoices/24604/file')
    expect(saveBlob.mock.calls[0][1]).toBe('GS-202610.xml')
    expect(created).toEqual([])
    await rows(w)[3].trigger('click')
    await flushPromises()
    expect(w.find('.inv-download').exists()).toBe(false)
    expect(w.text()).toContain('keine Datei gespeichert')
    w.unmount()
  })

  it('shows file errors (404) and preview errors with retry', async () => {
    const client = api(LIST(), {}, new HttpError(404, ''))
    const w = await mountView(client)
    await rows(w)[0].trigger('click')
    await flushPromises()
    await w.find('.inv-download').trigger('click')
    await flushPromises()
    expect(w.find('.inv-file [role="alert"]').text()).toContain('nicht mehr vorhanden')
    expect(saveBlob).not.toHaveBeenCalled()
    await w.find('.inv-show').trigger('click')
    await flushPromises()
    expect(w.find('.inv-preview .alert-error').text()).toContain('nicht mehr vorhanden')
    client.getBlob.mockImplementationOnce(async () => new Blob(['%PDF'], { type: 'application/pdf' }))
    await w.find('.inv-preview .retry').trigger('click')
    await flushPromises()
    expect(w.find('object.rep-pdf').attributes('data')).toBe('blob:test/1')
    w.unmount()
  })

  it('shows unreadable invoices (404) and detail errors with retry', async () => {
    let calls = 0
    const client = api(LIST(), {
      24601: new HttpError(404, ''),
      24600: () => (++calls === 1 ? new HttpError(500, '') : invoiceDetail(invoiceSet()[2], { items: [] })),
    })
    const w = await mountView(client)
    await rows(w)[0].trigger('click')
    await flushPromises()
    expect(w.find('.inv-items-section .alert-error').text()).toContain('nicht lesbar')
    // the fields of the list stay visible
    expect(txt(w.find('.inv-details'))).toContain('Rechnungsnr. EN-202610-S')
    await rows(w)[2].trigger('click')
    await flushPromises()
    const alert = w.find('.inv-items-section .alert-error')
    expect(alert.text()).toContain('konnte nicht geladen')
    await alert.find('.retry').trigger('click')
    await flushPromises()
    expect(w.find('.inv-items-section').text()).toContain('keine Positionen')
    w.unmount()
  })

  it('exports the shown invoices as CSV named by the time frame', async () => {
    const w = await mountView(api(LIST()))
    await w.find('.notes-chip[data-medium="ELECTRICITY"]').trigger('click')
    await w.find('.iso-toolbar .notes-btn').trigger('click')
    expect(saveBlob).toHaveBeenCalledTimes(1)
    const [blob, name] = saveBlob.mock.calls[0] as [Blob, string]
    expect(name).toBe('Energierechnungen 2024-12-01 - 2026-11-30.csv')
    const text = await blob.text()
    const lines = text.replace(/^﻿/, '').split('\r\n')
    expect(lines[0].split(';').slice(0, 3)).toEqual(['Zeitraum von', 'Zeitraum bis', 'Rechnungsnr.'])
    expect(lines).toHaveLength(5)
    expect(lines[3]).toContain(';-333,2;EUR;ja;')
    w.unmount()
  })

  it('shows cards on phone width with the detail toggle', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    const w = await mountView(api(LIST()))
    expect(w.find('table.inv-table').exists()).toBe(false)
    const cards = w.findAll('.inv-card')
    expect(cards).toHaveLength(4)
    expect(cards[3].classes()).toContain('is-credit')
    expect(txt(cards[0])).toContain('Brutto 13.744,50 EUR')
    await cards[0].find('.mod-toggle').trigger('click')
    await flushPromises()
    expect(cards[0].find('.inv-details').exists()).toBe(true)
    w.unmount()
  })

  it('shows load errors with retry, the empty state and the truncation hint', async () => {
    let calls = 0
    let w = await mountView(api(() => (++calls === 1 ? new HttpError(500, '') : LIST())))
    expect(w.find('.alert-error').exists()).toBe(true)
    await w.find('.alert-error .retry').trigger('click')
    await flushPromises()
    expect(rows(w)).toHaveLength(4)
    w.unmount()
    w = await mountView(api({ invoices: [], truncated: false }))
    expect(w.text()).toContain('Im Zeitraum gibt es keine Energierechnungen')
    expect(w.find('.inv-summary').exists()).toBe(false)
    w.unmount()
    w = await mountView(api({ invoices: invoiceSet(), truncated: true }))
    expect(w.find('.notes-truncated').text()).toContain('nur die neuesten 4 Rechnungen')
    w.unmount()
  })
})
