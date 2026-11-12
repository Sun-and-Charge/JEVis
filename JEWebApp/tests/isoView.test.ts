/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n } from '../src/i18n'
import { columnStorageKey } from '../src/iso/columns'
import { useAuthStore } from '../src/stores/auth'
import ActionPlansView from '../src/views/ActionPlansView.vue'
import LegalView from '../src/views/LegalView.vue'
import NonconformitiesView from '../src/views/NonconformitiesView.vue'
import { action, actionPlan, legalPlan, nonconformity, nonconformityPlan, npv, obligation } from './fixtures/iso'

const Stub = defineComponent({ render: () => h('div') })
const sp = (s: string) => s.replace(/\s+/g, ' ').trim()

/** Text of all text nodes joined with spaces (adjacent cells would otherwise run together). */
function words(w: { element: Element }): string {
  const walker = document.createTreeWalker(w.element, NodeFilter.SHOW_TEXT)
  const parts: string[] = []
  for (let n = walker.nextNode(); n; n = walker.nextNode()) parts.push(n.textContent ?? '')
  return sp(parts.join(' '))
}

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/password', name: 'password', component: Stub },
      { path: '/appointments', name: 'appointments', component: Stub },
      { path: '/maintenance', name: 'maintenance', component: Stub },
      { path: '/actionplans', name: 'actionPlans', component: Stub },
      { path: '/nonconformities', name: 'nonconformities', component: Stub },
      { path: '/legal', name: 'legal', component: Stub },
      { path: '/login', name: 'login', component: Stub },
    ],
  })
}

async function mountWith(component: unknown, get: ReturnType<typeof vi.fn>, getBlob?: ReturnType<typeof vi.fn>) {
  const auth = useAuthStore()
  Object.assign(auth.http, { get, ...(getBlob ? { getBlob } : {}) })
  const router = makeRouter()
  await router.push('/')
  const wrapper = mount(component as never, { global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

const rowIds = (wrapper: Awaited<ReturnType<typeof mountWith>>) => wrapper.findAll('tbody tr.mod-row').map((r) => r.findAll('td')[1].text())

describe('ISO views (mocked API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    window.localStorage.clear()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists actions with key figures, filters, sorting, columns and details', async () => {
    const plans = [
      actionPlan(1, [
        action(1, {
          title: 'Replace lamps',
          status: 'Done',
          doneDate: '2026-10-07T00:00:00+02:00',
          npv: npv({ investment: 1000, saving: 365 }),
          consumption: { before: 5000, after: 1350, diff: 3650, unit: 'kWh', objectId: null, beforeFrom: '2025-01-01T00:00:00+01:00', beforeUntil: '2025-12-31T00:00:00+01:00', afterFrom: null, afterUntil: null },
          checkList: { processDocument: true, newEnPI: 'kWh per unit', unknownKey: false },
          note: 'Note text',
          attachments: 1,
        }),
        action(2, { title: 'Insulate pipes', medium: 'Gas', mediumName: 'Gas', npv: npv({ investment: 2000, saving: 100 }), consumption: null }),
      ]),
      actionPlan(2, [action(10, { title: 'Pump control' })], { name: 'Second plan' }),
    ]
    const get = vi.fn(async (path: string) => {
      if (path.startsWith('/web/actionplans')) return { today: '2026-11-06', plans }
      if (path === '/web/objects/1/attachments') return [{ id: 77, name: 'offer.pdf', size: 2048, date: '2026-10-01T10:00:00' }]
      throw new Error(path)
    })
    const getBlob = vi.fn(async () => new Blob(['x']))
    const createObjectURL = vi.fn(() => 'blob:x')
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }))
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const wrapper = await mountWith(ActionPlansView, get, getBlob)
    expect(get).toHaveBeenCalledWith('/web/actionplans?lang=de')

    // overview of all plans: plan column first
    const heads = wrapper.findAll('table.iso-table thead th').map((th) => th.text().replace(/[▲▼]/g, ''))
    expect(heads.slice(1, 4)).toEqual(['Plan', 'Nr.', 'Verantwortlich'])
    expect(heads).toHaveLength(13)
    expect(wrapper.findAll('tbody tr.mod-row')).toHaveLength(3)
    expect(words(wrapper.find('.iso-stats'))).toContain('Summe Investition 4.000,00 €')

    // key figures of the filtered rows
    await wrapper.find('#actions-plan').setValue('1')
    expect(wrapper.findAll('table.iso-table thead th').map((th) => th.text())).not.toContain('Plan')
    const stats = words(wrapper.find('.iso-stats'))
    expect(stats).toContain('Summe Investition 3.000,00 €')
    expect(stats).toContain('465,00 €')
    expect(stats).toContain('3.650 kWh')
    expect(stats).toContain('Einsparung seit Umsetzung 30,00 €')
    expect(stats).toContain('0,12 t')
    expect(stats).toContain('Done 1')

    await wrapper.find('#actions-from').setValue('2026-01-01')
    expect(wrapper.findAll('tbody tr.mod-row')).toHaveLength(2)
    expect(words(wrapper.find('.iso-stats'))).toContain('Einsparung seit 01.01.2026 30,00 €')
    await wrapper.find('#actions-from').setValue('')

    // filters
    await wrapper.find('#actions-medium').setValue('Gas')
    expect(rowIds(wrapper)).toEqual(['AP 002'])
    await wrapper.find('#actions-medium').setValue('')
    await wrapper.find('#actions-search').setValue('lamps')
    expect(rowIds(wrapper)).toEqual(['AP 001'])
    await wrapper.find('#actions-search').setValue('')

    // sorting by investment
    const invHead = wrapper.findAll('table.iso-table thead th').find((th) => th.text().startsWith('Investition'))!
    await invHead.find('button').trigger('click')
    await invHead.find('button').trigger('click')
    expect(invHead.attributes('aria-sort')).toBe('descending')
    expect(rowIds(wrapper)).toEqual(['AP 002', 'AP 001'])

    // column chooser, stored per module
    const box = wrapper.findAll('.iso-columns input[type="checkbox"]').find((c) => c.element.parentElement?.textContent?.includes('Bereich'))!
    await box.setValue(true)
    expect(wrapper.findAll('table.iso-table thead th').map((th) => th.text())).toContain('Bereich')
    expect(JSON.parse(window.localStorage.getItem(columnStorageKey('actions'))!)).toContain('field')

    // detail panel with sections
    const toggle = wrapper.findAll('tbody tr.mod-row')[1].find('button.mod-toggle')
    await toggle.trigger('click')
    await flushPromises()
    const detail = wrapper.find('#actions-d-1')
    const text = words(detail)
    for (const h of ['Allgemein', 'Details', 'Kapitalwert', 'Checkliste', 'Anhänge']) expect(text).toContain(h)
    expect(text).toContain('Vorher 5.000 kWh 01.01.2025 – 31.12.2025')
    expect(text).toContain('Note text')
    expect(text).toContain('Prozessanweisungen Ja')
    expect(text).toContain('Neuer EnPI kWh per unit')
    expect(text).toContain('Unknown Key Nein')
    expect(text).toContain('Zinssatz 10 %')
    expect(detail.findAll('.iso-mini tbody tr').length).toBeGreaterThan(2)
    expect(get).toHaveBeenCalledWith('/web/objects/1/attachments')

    await detail.find('button.iso-file').trigger('click')
    await flushPromises()
    expect(getBlob).toHaveBeenCalledWith('/web/files/77')
    expect(createObjectURL).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
    click.mockRestore()
    wrapper.unmount()
  })

  it('selects a single plan and shows cards on phone width', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    const get = vi.fn(async () => ({ today: '2026-11-06', plans: [actionPlan(1, [action(1, { title: 'Card action', description: 'Card text' })])] }))
    const wrapper = await mountWith(ActionPlansView, get)
    expect(wrapper.find('#actions-plan').exists()).toBe(false)
    expect(wrapper.find('table.iso-table').exists()).toBe(false)
    const card = wrapper.find('li.mod-card')
    expect(card.find('h2').text()).toBe('AP 001 – Card action')
    await card.find('button.mod-toggle').trigger('click')
    expect(card.find('#actions-c-1').text()).toContain('Card text')
    wrapper.unmount()
  })

  it('lists nonconformities with state, warnings and filters', async () => {
    const get = vi.fn(async () => ({
      plans: [
        nonconformityPlan(1, [
          nonconformity(1, { title: 'Leak', overdue: true, warnings: ['IMMEDIATE_MEASURES_MISSING', 'DONE_IN_FUTURE'], checkList: { immediateActionRequired: true } }),
          nonconformity(2, { title: 'Closed one', open: false, doneDate: '2026-04-01T00:00:00+02:00' }),
        ]),
      ],
    }))
    const wrapper = await mountWith(NonconformitiesView, get)
    expect(get).toHaveBeenCalledWith('/web/nonconformities')
    const rows = wrapper.findAll('tbody tr.mod-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].classes()).toContain('is-overdue')
    expect(rows[0].find('.mod-badge').text()).toBe('Überfällig')
    expect(rows[1].find('.mod-badge').text()).toBe('Abgeschlossen')
    await rows[0].find('button.mod-toggle').trigger('click')
    const detail = words(wrapper.find('#nonconformities-d-1'))
    expect(detail).toContain('Sofortmaßnahme erforderlich, aber keine Sofortmaßnahmen eingetragen')
    expect(detail).toContain('Abschlussdatum liegt in der Zukunft')
    expect(detail).toContain('Müssen Sofortmaßnahmen ergriffen werden? Ja')
    expect(detail).toContain('Keine Anhänge.')

    await wrapper.find('input[type="radio"][value="closed"]').setValue()
    expect(wrapper.findAll('tbody tr.mod-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('1 von 2 Einträgen')
    wrapper.unmount()
  })

  it('lists the legal register with safe external links and relevance filter', async () => {
    i18n.global.locale.value = 'en'
    const get = vi.fn(async () => ({
      plans: [
        legalPlan(1, [
          obligation(1, { title: 'Energy act', link: 'https://example.org/act', description: 'About energy' }),
          obligation(2, { title: 'Old rule', relevant: false, link: 'javascript:alert(1)' }),
        ]),
      ],
    }))
    const wrapper = await mountWith(LegalView, get)
    expect(get).toHaveBeenCalledWith('/web/legal')
    const links = wrapper.findAll('tbody a')
    expect(links).toHaveLength(1)
    expect(links[0].attributes('href')).toBe('https://example.org/act')
    expect(links[0].attributes('rel')).toContain('noopener')
    expect(links[0].attributes('target')).toBe('_blank')
    expect(wrapper.findAll('tbody tr.mod-row')[0].text()).toContain('Relevant')

    await wrapper.find('input[type="radio"][value="notRelevant"]').setValue()
    expect(wrapper.findAll('tbody tr.mod-row').map((r) => r.text())).toEqual([expect.stringContaining('Old rule')])
    await wrapper.find('input[type="radio"][value="all"]').setValue()
    await wrapper.findAll('tbody tr.mod-row')[0].find('button.mod-toggle').trigger('click')
    const detail = wrapper.find('#legal-d-1')
    expect(detail.text()).toContain('About energy')
    expect(detail.find('a').attributes('rel')).toContain('noopener')
    wrapper.unmount()
  })
})
