/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { checkManual, effectiveTotals, lineNet, lineTotals, manualBody, manualDirty, newLine, newManualForm, type ManualForm } from '../src/accounting/manualInvoice'
import { saveManualInvoice, type InvoiceDirectory, type InvoiceList } from '../src/api/invoices'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import InvoicesView from '../src/views/InvoicesView.vue'
import { invoiceSet } from './fixtures/accounting'
import { httpError } from './fixtures/edit'

/* "Rechnung erfassen" (R01): invoices without e-invoice file; neutral placeholder data. */

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

function filled(): ManualForm {
  const f = newManualForm(900)
  f.medium = 'GAS'
  f.seller = 'Versorger Nord'
  f.number = 'M-7'
  f.date = '2026-10-05'
  f.periodStart = '2026-09-01'
  f.periodEnd = '2026-09-30'
  f.meterPoint = 'DE 0000 0001'
  f.lines = [
    newLine({ description: 'Arbeitspreis', quantity: '12000', unit: 'KWH', unitPrice: '0,08' }),
    newLine({ description: 'Netzentgelt', quantity: '12000', unit: 'KWH', unitPrice: '0,02' }),
    newLine({ description: 'Grundpreis', quantity: '1', unit: 'MON', unitPrice: '15,50' }),
  ]
  return f
}

describe('manual invoice form', () => {
  it('computes line amounts, totals with tax per line and the consumption once per quantity', () => {
    const f = filled()
    expect(lineNet(f.lines[0])).toBe(960)
    const t = lineTotals(f)
    /* 960 + 240 + 15.50 = 1215.50; tax 182.40 + 45.60 + 2.95 = 230.95 */
    expect(t.net).toBe(1215.5)
    expect(t.tax).toBe(230.95)
    expect(t.gross).toBe(1446.45)
    expect(t.consumption).toEqual({ value: 12000, unit: 'kWh' })
    f.lines[1].net = '250'
    expect(lineTotals(f).net).toBe(1225.5)
  })

  it('own totals and consumption override the lines', () => {
    const f = filled()
    f.ownTotals = true
    f.net = '1215,49'
    f.tax = '230,95'
    f.ownConsumption = true
    f.consumption = '12,5'
    f.consumptionUnit = 'MWH'
    const t = effectiveTotals(f)
    expect(t.gross).toBe(1446.44)
    expect(t.consumption).toEqual({ value: 12500, unit: 'kWh' })
    const body = manualBody(f)
    expect(body.net).toBe(1215.49)
    expect(body.gross).toBeUndefined()
    expect(body.consumption).toEqual({ value: 12500, unit: 'KWH' })
  })

  it('checks required fields, the period and the lines', () => {
    const empty = newManualForm()
    const p = checkManual(empty)
    expect([...p.keys()]).toEqual(expect.arrayContaining(['directory', 'medium', 'seller', 'number', 'date', 'lines']))
    const f = filled()
    expect(checkManual(f).size).toBe(0)
    f.periodEnd = '2026-08-01'
    f.lines[0].quantity = 'zwölf'
    f.lines[2].description = ''
    const q = checkManual(f)
    expect(q.get('period')).toBe('periodOrder')
    expect(q.get('lines[0].quantity')).toBe('number:quantity')
    expect(q.get('lines[2].description')).toBe('lineDescription')
    f.periodEnd = ''
    expect(checkManual(f).get('period')).toBe('period')
  })

  it('sends only filled lines, numbers and the consumption shown', () => {
    const f = filled()
    f.lines.push(newLine())
    const body = manualBody(f)
    expect(body.lines).toHaveLength(3)
    expect(body.lines[0]).toEqual({ description: 'Arbeitspreis', quantity: 12000, unit: 'KWH', unitPrice: 0.08, net: undefined, taxRate: 19 })
    expect(body.meterPoint).toBe('DE00000001')
    expect(body.periodStart).toBe('2026-09-01')
    expect(body.consumption).toEqual({ value: 12000, unit: 'KWH' })
    expect(body.net).toBeUndefined()
    expect(manualDirty(newManualForm(900))).toBe(false)
    expect(manualDirty(f)).toBe(true)
  })

  it('posts the data and the optional file as multipart', async () => {
    const postForm = vi.fn(async () => ({ invoiceId: 5, folderId: 6 }))
    const scan = new File(['x'], 'scan.pdf', { type: 'application/pdf' })
    await saveManualInvoice({ postForm } as never, 900, manualBody(filled()), scan)
    const [path, form] = postForm.mock.calls[0] as unknown as [string, FormData]
    expect(path).toBe('/invoices/manual?directory=900')
    expect(JSON.parse(form.get('data') as string).number).toBe('M-7')
    expect(form.get('file')).toBeInstanceOf(File)
    await saveManualInvoice({ postForm } as never, 900, manualBody(filled()), null)
    expect(((postForm.mock.calls[1] as unknown[])[1] as FormData).get('file')).toBeNull()
  })
})

const Stub = defineComponent({ render: () => h('div') })
const DIRS: InvoiceDirectory[] = [{ id: 900, name: 'Energierechnungen', path: 'Energieverträge', site: { id: 7100, name: 'Musterwerk' } }]

function api() {
  return vi.fn(async (path: string) => {
    if (path.startsWith('/web/invoices?')) return { invoices: invoiceSet(), truncated: false, canImport: true } satisfies InvoiceList
    if (path.startsWith('/web/invoices/directories')) return DIRS
    throw new Error('unexpected ' + path)
  })
}

async function mountView(postForm: ReturnType<typeof vi.fn>) {
  Object.assign(useAuthStore().http, { get: api(), postForm })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }] })
  await router.push('/')
  const w = mount(InvoicesView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  await w.find('.inv-manual').trigger('click')
  await flushPromises()
  return w
}

const panel = () => document.body.querySelector<HTMLElement>('.inv-manual-backdrop [role="dialog"]')!
function field(label: string): HTMLInputElement {
  const l = Array.from(panel().querySelectorAll('label')).find((x) => x.textContent?.replace(/\s*\*$/, '').trim() === label)
  if (!l) throw new Error('no field ' + label)
  return document.getElementById(l.getAttribute('for')!) as HTMLInputElement
}
async function set(el: HTMLInputElement | HTMLSelectElement, value: string) {
  el.value = value
  el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input'))
  await flushPromises()
}
async function submit() {
  panel().querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  document.body.innerHTML = ''
})

describe('"Rechnung erfassen" in the invoice register', () => {
  it('checks the form, saves it and reports the saved invoice', async () => {
    const postForm = vi.fn(async () => ({ invoiceId: 77, folderId: 6 }))
    const w = await mountView(postForm)
    expect((field('Ordner') as unknown as HTMLSelectElement).value).toBe('900')
    await submit()
    expect(postForm).not.toHaveBeenCalled()
    expect(panel().textContent).toContain('Bitte die markierten Felder prüfen.')

    await set(field('Medium') as unknown as HTMLSelectElement, 'ELECTRICITY')
    await set(field('Lieferant'), 'Versorger Nord')
    await set(field('Rechnungsnr.'), 'M-9')
    await set(field('Rechnungsdatum'), '2026-10-05')
    const descriptions = Array.from(panel().querySelectorAll<HTMLInputElement>('.inv-line-desc input'))
    await set(descriptions[0], 'Arbeitspreis')
    const quantities = Array.from(panel().querySelectorAll<HTMLInputElement>('input[id$="-q"]'))
    const prices = Array.from(panel().querySelectorAll<HTMLInputElement>('input[id$="-p"]'))
    await set(quantities[0], '1000')
    await set(prices[0], '0,25')
    expect(panel().querySelector('.inv-total-net')!.textContent).toContain('250,00')
    expect(panel().querySelector('.inv-total-gross')!.textContent).toContain('297,50')
    expect(panel().querySelector('.inv-total-consumption')!.textContent).toContain('1.000 kWh')

    await submit()
    expect(postForm).toHaveBeenCalledTimes(1)
    const [path, form] = postForm.mock.calls[0] as unknown as [string, FormData]
    expect(path).toBe('/invoices/manual?directory=900')
    const data = JSON.parse(form.get('data') as string)
    expect(data).toMatchObject({ medium: 'ELECTRICITY', seller: 'Versorger Nord', number: 'M-9', date: '2026-10-05' })
    expect(data.lines).toHaveLength(1)
    expect(document.body.querySelector('.inv-manual-backdrop')).toBeNull()
    expect(w.find('.inv-message').text()).toContain('Die Rechnung wurde gespeichert.')
    w.unmount()
  })

  it('a duplicate keeps the form open with a hint', async () => {
    const postForm = vi.fn(async () => {
      throw httpError(409, { error: 'DUPLICATE', existing: 3 })
    })
    const w = await mountView(postForm)
    await set(field('Medium') as unknown as HTMLSelectElement, 'GAS')
    await set(field('Lieferant'), 'Versorger Nord')
    await set(field('Rechnungsnr.'), 'M-7')
    await set(field('Rechnungsdatum'), '2026-10-05')
    panel().querySelectorAll<HTMLInputElement>('.inv-manual-check input')[1].click()
    await flushPromises()
    await set(field('Netto'), '100')
    await set(field('Brutto'), '119')
    await submit()
    expect(postForm).toHaveBeenCalledTimes(1)
    expect(panel().querySelector('.inv-manual-duplicate')).not.toBeNull()
    w.unmount()
  })
})
