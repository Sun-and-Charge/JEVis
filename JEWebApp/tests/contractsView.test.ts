/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ContractHistory, ContractList } from '../src/api/contracts'
import { HttpError } from '../src/api/http'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import ContractsView from '../src/views/ContractsView.vue'
import { contractList } from './fixtures/accounting'

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

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

const HISTORY: ContractHistory = {
  unit: '€/kWh',
  type: 'number',
  samples: [
    { timestamp: '2026-01-01T00:00:00.000+01:00', value: 0.28 },
    { timestamp: '2025-01-01T00:00:00.000+01:00', value: 0.31 },
  ],
}

const RANGING_HISTORY: ContractHistory = {
  unit: null,
  type: 'ranging',
  samples: [{ timestamp: '2026-01-01T00:00:00.000+01:00', value: [{ from: 0, to: null, value: 12.5 }] }],
}

function api(list: ContractList | Error | (() => ContractList | Error), history: Record<string, ContractHistory | Error> = { 'Energy Price HT': HISTORY, 'Demand Charge': RANGING_HISTORY }) {
  return vi.fn(async (path: string) => {
    if (path.startsWith('/web/contracts?')) {
      const v = typeof list === 'function' ? list() : list
      if (v instanceof Error) throw v
      return v
    }
    const m = /^\/web\/contracts\/objects\/(\d+)\/history\?(.*)$/.exec(path)
    if (m) {
      const attribute = new URLSearchParams(m[2]).get('attribute') ?? ''
      const v = history[attribute]
      if (!v) throw new HttpError(404, '')
      if (v instanceof Error) throw v
      return v
    }
    throw new Error('unexpected ' + path)
  })
}

async function mountView(get: ReturnType<typeof vi.fn>) {
  const auth = useAuthStore()
  Object.assign(auth.http, { get })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }] })
  await router.push('/')
  const wrapper = mount(ContractsView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

type W = Awaited<ReturnType<typeof mountView>>
const tabs = (w: W) => w.findAll('[role="tab"]')
const cards = (w: W) => w.findAll('.ctr-card')
const tabByText = (w: W, text: string) => tabs(w).find((t) => t.text().includes(text))!

describe('contracts view (mocked API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('loads with the language and shows the groups as tabs in the fixed order with counts', async () => {
    const get = api(contractList())
    const w = await mountView(get)
    expect(get.mock.calls[0][0]).toBe('/web/contracts?lang=de')
    expect(w.text()).toContain('Nur Ansicht – Verträge und Rechnungen werden im Control Center gepflegt bzw. importiert.')
    expect(tabs(w).map((t) => txt(t).replace(/ \d+ von \d+ Einträgen$/, ''))).toEqual(['Lieferanten 1', 'Netzbetreiber 1', 'Vertragspartner 1', 'Abrechnung 1'])
    expect(tabs(w)[0].attributes('aria-selected')).toBe('true')
    expect(tabs(w)[1].attributes('tabindex')).toBe('-1')
    expect(w.find('[role="tabpanel"]').attributes('aria-labelledby')).toBe(tabs(w)[0].attributes('id'))
    w.unmount()
  })

  it('shows an object with class, site / path, contractor and formatted attributes', async () => {
    const w = await mountView(api(contractList()))
    expect(cards(w)).toHaveLength(1)
    const card = cards(w)[0]
    expect(card.find('h2').text()).toBe('Strom - Energieversorger Nord')
    expect(card.find('.ctr-where').text()).toBe('Musterwerk / Energieverträge / Energielieferung')
    expect(card.find('.ctr-class').text()).toBe('Stromlieferant')
    const text = txt(card)
    expect(text).toContain('Arbeitspreis HT 0,28 €/kWh ab 01.01.2026')
    expect(text).toContain('Kundennummer K-4711 ab 01.12.2025')
    expect(text).toContain('Arbeitspreis NT –')
    const ranging = card.find('.ctr-ranging')
    expect(ranging.findAll('tbody tr').map((r) => txt(r))).toEqual(['0 100 12,5', '100 offen 10'])
    // history toggles only for attributes with more than one sample
    expect(card.findAll('.ctr-history-btn')).toHaveLength(2)
    w.unmount()
  })

  it('loads the history on demand: step chart and table for numbers, only the table for ranging values', async () => {
    const get = api(contractList())
    const w = await mountView(get)
    const [priceBtn, rangingBtn] = cards(w)[0].findAll('.ctr-history-btn')
    expect(priceBtn.attributes('aria-expanded')).toBe('false')
    await priceBtn.trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/contracts/objects/23901/history?attribute=Energy+Price+HT&limit=100')
    expect(priceBtn.attributes('aria-expanded')).toBe('true')
    const panel = w.find('#' + priceBtn.attributes('aria-controls'))
    expect(panel.exists()).toBe(true)
    const chart = panel.findComponent({ name: 'VChart' })
    expect(chart.exists()).toBe(true)
    const option = chart.props('option') as { series: { step: string }[] }
    expect(option.series[0].step).toBe('end')
    expect(panel.findAll('tbody tr').map((r) => txt(r))).toEqual(['01.01.2026 0,28 €/kWh', '01.01.2025 0,31 €/kWh'])

    await rangingBtn.trigger('click')
    await flushPromises()
    const rangingPanel = w.find('#' + rangingBtn.attributes('aria-controls'))
    expect(rangingPanel.findComponent({ name: 'VChart' }).exists()).toBe(false)
    expect(txt(rangingPanel.find('tbody'))).toBe('01.01.2026 ab 0: 12,5')

    await priceBtn.trigger('click')
    expect(w.find('#' + priceBtn.attributes('aria-controls')).exists()).toBe(false)
    w.unmount()
  })

  it('shows history errors with retry and 404 as not available', async () => {
    let calls = 0
    const get = api(contractList(), { 'Energy Price HT': HISTORY })
    get.mockImplementation(async (path: string) => {
      if (path.startsWith('/web/contracts?')) return contractList()
      if (path.includes('Energy+Price+HT')) {
        if (++calls === 1) throw new HttpError(500, '')
        return HISTORY
      }
      throw new HttpError(404, '')
    })
    const w = await mountView(get)
    const [priceBtn, rangingBtn] = cards(w)[0].findAll('.ctr-history-btn')
    await priceBtn.trigger('click')
    await flushPromises()
    const alert = w.find('.ctr-history .alert-error')
    expect(alert.text()).toContain('Der Verlauf konnte nicht geladen werden.')
    await alert.find('.retry').trigger('click')
    await flushPromises()
    expect(w.find('.ctr-history .vchart').exists()).toBe(true)
    await rangingBtn.trigger('click')
    await flushPromises()
    expect(w.find('#' + rangingBtn.attributes('aria-controls')).text()).toContain('nicht verfügbar')
    w.unmount()
  })

  it('jumps to the linked contractor in the contractor tab; unreadable contractors are plain text', async () => {
    const w = await mountView(api(contractList()))
    const link = cards(w)[0].find('.ctr-contractor')
    expect(link.text()).toBe('Energieversorger Nord')
    expect(link.attributes('aria-label')).toBe('Vertragspartner „Energieversorger Nord“ anzeigen')
    await link.trigger('click')
    await flushPromises()
    expect(tabByText(w, 'Vertragspartner').attributes('aria-selected')).toBe('true')
    expect(cards(w)[0].attributes('id')).toBe('contract-obj-23910')
    expect(cards(w)[0].classes()).toContain('is-highlighted')
    expect(document.activeElement?.id).toBe('contract-obj-23910')

    await tabByText(w, 'Netzbetreiber').trigger('click')
    expect(cards(w)[0].find('.ctr-contractor').exists()).toBe(false)
    expect(txt(cards(w)[0])).toContain('Vertragspartner Kein Leserecht')
    w.unmount()
  })

  it('moves between tabs with the arrow keys', async () => {
    const w = await mountView(api(contractList()))
    await tabs(w)[0].trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(tabs(w)[1].attributes('aria-selected')).toBe('true')
    expect(document.activeElement?.id).toBe(tabs(w)[1].attributes('id'))
    await tabs(w)[1].trigger('keydown', { key: 'End' })
    expect(tabs(w)[3].attributes('aria-selected')).toBe('true')
    await tabs(w)[3].trigger('keydown', { key: 'ArrowRight' })
    expect(tabs(w)[0].attributes('aria-selected')).toBe('true')
    w.unmount()
  })

  it('shows the configurations with the hint on the cost calculation', async () => {
    const w = await mountView(api(contractList()))
    await tabByText(w, 'Abrechnung').trigger('click')
    expect(w.find('.ctr-hint').text()).toContain('Kostenberechnung folgt')
    const row = w.find('.ctr-config-table tbody tr')
    const text = txt(row)
    expect(text).toContain('Musterwerk / Energieverträge / Abrechnung Strom Musterwerk')
    expect(text).toContain('V-1001 Strom DE0000000000000000000000000000001 01.12.2025 01.01.2026 30.09.2027 01.01.2026 31.12.2027')
    w.unmount()
  })

  it('searches over all tabs, filters by site and shows the counts', async () => {
    const w = await mountView(api(contractList()))
    expect(w.find('#contracts-site').exists()).toBe(true)
    await w.find('#contracts-search').setValue('netzbetreiber')
    expect(txt(tabs(w)[0])).toContain('0/1')
    expect(w.text()).toContain('Kein Eintrag passt zur Suche.')
    await tabByText(w, 'Netzbetreiber').trigger('click')
    expect(cards(w)).toHaveLength(1)
    await w.find('#contracts-search').setValue('')
    await w.find('#contracts-site').setValue('7101')
    await tabByText(w, 'Lieferanten').trigger('click')
    expect(cards(w)).toHaveLength(0)
    await tabByText(w, 'Netzbetreiber').trigger('click')
    expect(cards(w)).toHaveLength(1)
    w.unmount()
  })

  it('a jump to a filtered-out contractor clears the filter', async () => {
    const w = await mountView(api(contractList()))
    await w.find('#contracts-search').setValue('strom')
    await cards(w)[0].find('.ctr-contractor').trigger('click')
    await flushPromises()
    expect((w.find('#contracts-search').element as HTMLInputElement).value).toBe('')
    expect(cards(w)[0].attributes('id')).toBe('contract-obj-23910')
    w.unmount()
  })

  it('shows cards for configurations on phone width', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    const w = await mountView(api(contractList()))
    await tabByText(w, 'Abrechnung').trigger('click')
    expect(w.find('.ctr-config-table').exists()).toBe(false)
    expect(txt(w.find('.ctr-config-card'))).toContain('Vertragsnummer V-1001')
    w.unmount()
    vi.unstubAllGlobals()
  })

  it('shows load errors with retry and the empty state', async () => {
    let calls = 0
    let w = await mountView(api(() => (++calls === 1 ? new HttpError(500, '') : contractList())))
    expect(w.find('.alert-error').exists()).toBe(true)
    await w.find('.alert-error .retry').trigger('click')
    await flushPromises()
    expect(tabs(w)).toHaveLength(4)
    w.unmount()
    w = await mountView(api({ groups: [], configurations: [] }))
    expect(w.text()).toContain('Es gibt keine Verträge')
    expect(tabs(w)).toHaveLength(0)
    w.unmount()
  })

  it('loads again in the new language', async () => {
    const get = api(contractList())
    const w = await mountView(get)
    i18n.global.locale.value = 'en'
    await flushPromises()
    expect(get.mock.calls.at(-1)?.[0]).toBe('/web/contracts?lang=en')
    expect(tabs(w)[0].text()).toContain('Suppliers')
    w.unmount()
  })
})
