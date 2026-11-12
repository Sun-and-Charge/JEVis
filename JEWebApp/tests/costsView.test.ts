/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { toIsoLocal } from '../src/analysis/settings'
import type { CostDefinition, CostList, CostResult, CostResultRequest } from '../src/api/costs'
import { HttpError } from '../src/api/http'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import CostsView from '../src/views/CostsView.vue'
import { COST_LIST, configurationDefinition, costResult, NOVEMBER, templateDefinition, YEAR } from './fixtures/costs'

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))
const saveBlob = vi.hoisted(() => vi.fn())
vi.mock('../src/iso/download', async (importOriginal) => ({ ...(await importOriginal<typeof import('../src/iso/download')>()), saveBlob }))

const Stub = defineComponent({ render: () => h('div') })
const sp = (s: string) => s.replace(/\s+/g, ' ').trim()

type Answer<T> = T | Error | ((body?: CostResultRequest) => T | Error)

interface Api {
  list?: Answer<CostList>
  defs?: Record<number, Answer<CostDefinition>>
  results?: Record<number, Answer<CostResult>>
}

function api({ list = COST_LIST, defs = {}, results = {} }: Api = {}) {
  const allDefs: Record<number, Answer<CostDefinition>> = { 23950: configurationDefinition(), 23960: templateDefinition(), ...defs }
  const resolve = <T>(a: Answer<T>, body?: CostResultRequest): T => {
    const v = typeof a === 'function' ? (a as (b?: CostResultRequest) => T | Error)(body) : a
    if (v instanceof Error) throw v
    return v
  }
  const get = vi.fn(async (path: string) => {
    if (path === '/web/costs') return resolve(list)
    let m = /^\/web\/costs\/(\d+)\?lang=\w+$/.exec(path)
    if (m) {
      const def = allDefs[Number(m[1])]
      if (!def) throw new HttpError(404, '')
      return resolve(def)
    }
    m = /^\/web\/rows\/(\d+)\/samples\?/.exec(path)
    if (m) return { unit: 'kWh', samples: [[Date.parse(NOVEMBER.from), 10], [Date.parse(NOVEMBER.from) + 3_600_000, 12]], truncated: false }
    throw new Error('unexpected ' + path)
  })
  const post = vi.fn(async (path: string, body?: unknown) => {
    const m = /^\/web\/costs\/(\d+)\/result$/.exec(path)
    if (!m) throw new Error('unexpected ' + path)
    const r = results[Number(m[1])] ?? costResult()
    return resolve(r, body as CostResultRequest)
  })
  return { get, post }
}

async function mountView(client: ReturnType<typeof api>, query: Record<string, string> = {}) {
  const auth = useAuthStore()
  Object.assign(auth.http, client)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/costs', name: 'costs', component: Stub }] })
  await router.push({ path: '/costs', query })
  const wrapper = mount(CostsView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

/** Lets the debounce run out and the answers arrive. */
async function settle() {
  await flushPromises()
  vi.advanceTimersByTime(1000)
  await flushPromises()
}

const items = (w: ReturnType<typeof mount>) => w.findAll('.cost-item')
const posts = (post: ReturnType<typeof vi.fn>) => post.mock.calls.map((c) => c[1] as CostResultRequest)

describe('costs view (mocked API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    saveBlob.mockClear()
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('lists configurations and templates grouped by site, search filters', async () => {
    const client = api()
    const { wrapper: w } = await mountView(client)
    expect(w.text()).toContain('Nur Ansicht – Vorlagen und Abrechnungen werden im Control Center eingerichtet.')
    expect(w.findAll('.cost-section-title').map((s) => s.text())).toEqual(['Abrechnungen', 'Vorlagen'])
    expect(w.findAll('.rep-group-title').map((g) => g.text())).toEqual(['Musterwerk', 'Musterwerk', 'Ohne Standort'])
    expect(items(w).map((i) => i.find('.rep-item-name').text())).toEqual(['Strom Musterwerk', 'Gas Musterwerk', 'Stromkosten', 'Wärmekosten'])
    expect(items(w)[0].find('.rep-item-meta').text()).toBe('Vorlage: Stromkosten')
    const missing = items(w)[1]
    expect(missing.classes()).toContain('is-empty')
    expect(missing.text()).toContain('Vorlage fehlt oder ist nicht lesbar')
    expect(items(w)[2].find('.rep-item-meta').exists()).toBe(false)
    expect(w.find('.rep-placeholder').exists()).toBe(true)

    await w.find('#costs-search').setValue('wärme')
    expect(items(w).map((i) => i.find('.rep-item-name').text())).toEqual(['Wärmekosten'])
    expect(w.findAll('.cost-section-title').map((s) => s.text())).toEqual(['Vorlagen'])
    expect(client.post).not.toHaveBeenCalled()
    w.unmount()
  })

  it('selects a configuration, shows contract fields and renders the result grid', async () => {
    const client = api()
    const { wrapper: w, router } = await mountView(client)
    await items(w)[0].trigger('click')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ id: '23950', tf: 'P1M' })
    expect(client.get).toHaveBeenCalledWith('/web/costs/23950?lang=de')
    expect(posts(client.post)).toEqual([{ timeFrame: 'P1M', from: null, to: null, timeZone: expect.any(String) }])

    const meta = sp(w.find('.cost-meta').text())
    expect(meta).toContain('Abrechnung')
    expect(meta).toContain('Stromkosten')
    expect(meta).toContain('V-1001')
    expect(meta).toContain('Strom')
    expect(meta).toContain('01.01.2026 – 31.12.2027')
    expect(w.findAll('.cost-inputs dt').map((d) => d.text())).toEqual(['Verbrauch', 'leistung'])
    expect(w.findAll('.cost-inputs dd').map((d) => d.text())).toEqual(['Bereinigte Daten', 'Kein Leserecht'])

    expect(w.find('.cost-period').text()).toContain('01.11.2026 – 30.11.2026')
    const grid = w.find('.cost-grid')
    expect(grid.attributes('style')).toContain('grid-template-columns: repeat(3, minmax(0, 1fr))')
    const label = grid.find('.cost-label')
    expect(label.attributes('role')).toBe('heading')
    expect(label.text()).toBe('Strom')
    expect(label.attributes('style')).toContain('grid-column: 1 / span 3')

    const cells = grid.findAll('.cost-cell')
    expect(cells.map((c) => c.find('.cost-name').text())).toEqual(['Arbeitspreis', 'Netz', 'Tarif'])
    const price = cells[0]
    expect(sp(price.find('.cost-amount').text())).toBe('1.234,56 €')
    expect(price.find('.cost-amount').classes()).toContain('is-bold')
    expect(price.attributes('title')).toBe('Verbrauch × Preis')
    expect(price.attributes('style')).toContain('grid-row: 2 / span 1')
    expect(price.attributes('style')).toContain('grid-column: 1 / span 2')
    expect(sp(cells[1].find('.cost-amount').text())).toBe('–')
    expect(cells[1].find('.cost-name').classes()).toContain('is-bold')
    expect(sp(cells[2].find('.cost-amount').text())).toBe('HT/NT')

    const seps = grid.findAll('[role="separator"]')
    expect(seps.map((s) => s.attributes('aria-orientation'))).toEqual(['vertical', 'horizontal'])
    expect(seps[0].classes()).toContain('is-vertical')
    expect(seps[0].attributes('style')).toContain('grid-row: 2 / span 2')

    const warnings = w.find('details.cost-warnings')
    expect(warnings.find('summary').text()).toBe('1 Hinweis')
    expect(warnings.text()).toContain('Eingabe "leistung" hat keine Werte')
    w.unmount()
  })

  it('changes the time frame (debounced), writes it to the URL and supports a custom range', async () => {
    const client = api()
    const { wrapper: w, router } = await mountView(client, { id: '23950' })
    await settle()
    expect(client.post).toHaveBeenCalledTimes(1)
    const tf = w.find('select.cost-timeframe')
    expect(tf.findAll('option').map((o) => o.text())).toEqual(['Monat', 'Jahr', 'Benutzerdefiniert'])

    await tf.setValue('P1Y')
    await flushPromises()
    // not yet: debounced
    expect(client.post).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.query.tf).toBe('P1Y')
    await settle()
    expect(posts(client.post)[1]).toMatchObject({ timeFrame: 'P1Y', from: null, to: null })

    await tf.setValue('CUSTOM')
    await settle()
    // starts with the period shown before
    expect(posts(client.post)[2]).toMatchObject({ timeFrame: 'CUSTOM', from: toIsoLocal(new Date(Date.parse(NOVEMBER.from))) })
    await w.find('input.cost-from').setValue('2026-11-05T00:00')
    await settle()
    expect(posts(client.post)[3].from).toBe(toIsoLocal(new Date(2026, 10, 5)))
    expect(router.currentRoute.value.query).toMatchObject({ id: '23950', tf: 'CUSTOM', from: toIsoLocal(new Date(2026, 10, 5)) })
    // end before start: hint, no request
    await w.find('input.cost-to').setValue('2026-11-01T00:00')
    await settle()
    expect(client.post).toHaveBeenCalledTimes(4)
    expect(w.text()).toContain('Das Ende liegt vor dem Beginn.')
    w.unmount()
  })

  it('ignores answers of older requests', async () => {
    let release: ((r: CostResult) => void) | null = null
    const client = api()
    const { wrapper: w } = await mountView(client, { id: '23950' })
    await settle()
    client.post.mockImplementationOnce(() => new Promise<CostResult>((r) => (release = r)))
    await w.find('select.cost-timeframe').setValue('P1Y')
    vi.advanceTimersByTime(1000)
    await flushPromises()
    await w.find('select.cost-timeframe').setValue('P1M')
    await settle()
    release!(costResult(NOVEMBER, { cells: [{ row: 0, column: 0, rowSpan: 1, columnSpan: 1, kind: 'label', name: 'Veraltet' }] }))
    await flushPromises()
    expect(w.find('.cost-grid').text()).not.toContain('Veraltet')
    expect(w.find('.cost-grid').text()).toContain('Arbeitspreis')
    w.unmount()
  })

  it('offers the selectable inputs of a template and sends the selection', async () => {
    const client = api()
    const { wrapper: w, router } = await mountView(client, { id: '23960', tf: 'P1Y', 'in.i1': '23685' })
    await settle()
    expect(sp(w.find('.cost-meta').text())).toContain('Vorlage')
    expect(w.find('.cost-inputs').exists()).toBe(false)
    const select = w.find('select[data-input="i1"]')
    expect(w.find(`label[for="${select.attributes('id')}"]`).text()).toBe('Verbrauch')
    expect(select.findAll('option').map((o) => o.text())).toEqual(['Kein Objekt', 'Bereinigte Daten', 'Zähler Halle 2'])
    expect((select.element as HTMLSelectElement).value).toBe('23685')
    expect(posts(client.post)[0]).toMatchObject({ timeFrame: 'P1Y', selections: { i1: 23685 } })

    await select.setValue('23684')
    await settle()
    expect(posts(client.post)[1].selections).toEqual({ i1: 23684 })
    expect(router.currentRoute.value.query['in.i1']).toBe('23684')

    // another entry drops the settings of this one
    await items(w)[0].trigger('click')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ id: '23950', tf: 'P1M' })
    w.unmount()
  })

  it('opens the row chart for a month, disables it for a year', async () => {
    const client = api()
    const { wrapper: w } = await mountView(client, { id: '23950' })
    await settle()
    const btn = w.find('.cost-chart-btn')
    expect(btn.attributes('disabled')).toBeUndefined()
    expect(btn.attributes('aria-expanded')).toBe('false')
    await btn.trigger('click')
    await flushPromises()
    const call = client.get.mock.calls.map((c) => c[0] as string).find((p) => p.startsWith('/web/rows/'))
    expect(call).toBe(`/web/rows/23684/samples?from=${encodeURIComponent(new Date(Date.parse(NOVEMBER.from)).toISOString())}&to=${encodeURIComponent(new Date(Date.parse(NOVEMBER.to)).toISOString())}`)
    expect(w.find('.cost-chart').text()).toContain('Verlauf: Arbeitspreis')
    expect(w.findComponent({ name: 'VChart' }).exists()).toBe(true)
    expect(w.find('.cost-chart-btn').attributes('aria-expanded')).toBe('true')
    await w.find('.cost-chart-close').trigger('click')
    expect(w.find('.cost-chart').exists()).toBe(false)
    w.unmount()

    const year = api({ results: { 23950: costResult(YEAR) } })
    const { wrapper: y } = await mountView(year, { id: '23950', tf: 'P1Y' })
    await settle()
    const disabled = y.find('.cost-chart-btn')
    expect(disabled.attributes('disabled')).toBeDefined()
    expect(disabled.attributes('title')).toBe('Zeitraum zu lang für die Vorschau')
    expect(y.find('.cost-chart-hint').text()).toBe('Zeitraum zu lang für die Vorschau')
    y.unmount()
  })

  it('exports the result as CSV', async () => {
    const client = api()
    const { wrapper: w } = await mountView(client, { id: '23950' })
    await settle()
    await w.find('.cost-csv').trigger('click')
    expect(saveBlob).toHaveBeenCalledTimes(1)
    const [blob, name] = saveBlob.mock.calls[0] as [Blob, string]
    expect(name).toBe('Strom Musterwerk 2026-11-01_2026-11-30.csv')
    const text = await blob.text()
    expect(text).toContain('Arbeitspreis;1234,56;€')
    const print = vi.fn()
    vi.stubGlobal('print', print)
    await w.find('.cost-print').trigger('click')
    expect(print).toHaveBeenCalled()
    w.unmount()
  })

  it('shows 404, 409 and 400 answers', async () => {
    const client = api({
      defs: { 23961: new HttpError(404, '') },
      results: {
        23951: new HttpError(409, ''),
        23950: (body) => (body?.timeFrame === 'P1Y' ? new HttpError(400, 'Unbekannter Zeitraum') : costResult()),
      },
    })
    client.get.mockImplementation(async (path: string) => {
      if (path === '/web/costs') return COST_LIST
      if (path.startsWith('/web/costs/23951')) return configurationDefinition({ id: 23951, name: 'Gas Musterwerk', templateName: null })
      if (path.startsWith('/web/costs/23950')) return configurationDefinition()
      throw new HttpError(404, '')
    })
    const { wrapper: w } = await mountView(client, { id: '23961' })
    await settle()
    expect(w.find('.cost-detail .alert-error').text()).toContain('Die Berechnung ist nicht vorhanden oder Sie dürfen sie nicht lesen.')
    expect(client.post).not.toHaveBeenCalled()

    await items(w)[1].trigger('click')
    await settle()
    expect(sp(w.find('.cost-meta').text())).toContain('Vorlage fehlt oder ist nicht lesbar')
    expect(w.find('.cost-error').text()).toBe('Vorlage fehlt oder ist nicht lesbar.')
    expect(w.find('.cost-grid').exists()).toBe(false)
    expect(w.find('.cost-csv').attributes('disabled')).toBeDefined()

    await items(w)[0].trigger('click')
    await settle()
    await w.find('select.cost-timeframe').setValue('P1Y')
    await settle()
    expect(sp(w.find('.cost-error').text())).toBe('Die Einstellungen sind ungültig. Unbekannter Zeitraum')
    w.unmount()
  })

  it('offers a retry when the list or the result fails', async () => {
    let failList = true
    let failResult = true
    const client = api({
      list: () => (failList ? new Error('offline') : COST_LIST),
      results: { 23950: () => (failResult ? new Error('offline') : costResult()) },
    })
    const { wrapper: w } = await mountView(client, { id: '23950' })
    expect(w.find('.alert-error').text()).toContain('Erneut versuchen')
    failList = false
    await w.find('.alert-error .retry').trigger('click')
    await settle()
    expect(w.find('.cost-error').text()).toContain('Das Ergebnis konnte nicht berechnet werden.')
    failResult = false
    await w.find('.cost-error .retry').trigger('click')
    await settle()
    expect(w.find('.cost-grid').exists()).toBe(true)
    w.unmount()
  })

  it('shows a message for an id that is not in the list', async () => {
    const { wrapper: w } = await mountView(api(), { id: '99999' })
    expect(w.find('.rep-missing').text()).toContain('nicht vorhanden')
    w.unmount()
  })
})
