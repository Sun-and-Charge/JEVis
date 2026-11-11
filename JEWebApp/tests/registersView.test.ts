/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { RegisterKind, RegisterList } from '../src/api/registers'
import { i18n } from '../src/i18n'
import { registerColumnKey } from '../src/registers/columns'
import { useAuthStore } from '../src/stores/auth'
import RegisterView from '../src/views/RegisterView.vue'
import { baseData, column, equipment, group, meter, registerList } from './fixtures/registers'

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
      { path: '/login', name: 'login', component: Stub },
      { path: '/meters', name: 'meters', component: Stub },
      { path: '/basedata', name: 'basedata', component: Stub },
      { path: '/equipment', name: 'equipment', component: Stub },
      { path: '/analyses/:id', name: 'analysis', component: Stub },
      { path: '/dashboards/:id', name: 'dashboard', component: Stub },
    ],
  })
}

async function mountView(kind: RegisterKind, get: ReturnType<typeof vi.fn>, getBlob = vi.fn(async () => new Blob(['x']))) {
  const auth = useAuthStore()
  Object.assign(auth.http, { get, getBlob })
  const router = makeRouter()
  await router.push('/')
  const wrapper = mount(RegisterView, { props: { kind }, global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

/** GET answers by path prefix. */
function api(list: RegisterList, history: unknown = { unit: 'kWh', samples: [] }) {
  return vi.fn(async (path: string) => {
    if (path.startsWith('/web/registers/objects/')) return history
    if (path.startsWith('/web/registers/')) return list
    throw new Error('unexpected ' + path)
  })
}

const headers = (w: Awaited<ReturnType<typeof mountView>>) => w.findAll('thead th').slice(1).map((th) => sp(th.text()))
const names = (w: Awaited<ReturnType<typeof mountView>>) => w.findAll('tbody tr.mod-row').map((r) => sp(r.findAll('td')[1].text()))

describe('register views (mocked API)', () => {
  let createObjectURL: ReturnType<typeof vi.fn>
  let revokeObjectURL: ReturnType<typeof vi.fn>

  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    window.localStorage.clear()
    createObjectURL = vi.fn(() => 'blob:x')
    revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }))
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  const meterList = () =>
    registerList('meters', [
      group(
        '5001',
        [
          meter(1, {
            name: 'Main meter',
            values: { 'Serial Number': 'A-123', Type: 'Main', Location: 'Hall 1', Picture: { file: 'meter.jpg', image: true }, Manual: { file: 'manual.pdf', image: false }, Remark: 'line 1\nline 2' },
            verification: { due: '2026-12-31', overdue: false },
          }),
          meter(2, { name: 'Gas meter', medium: 'GAS', classLabel: 'Gaszähler', values: { Type: 'Sub' }, verification: { due: '2026-01-01', overdue: true }, target: null, last: null }),
        ],
        {
          name: 'Building A',
          columns: [
            column('Serial Number', { name: 'Seriennummer' }),
            column('Type', { name: 'Typ' }),
            column('Location', { name: 'Ort', visible: false }),
            column('Picture', { name: 'Bild', type: 'FILE', visible: false }),
            column('Manual', { name: 'Datenblatt', type: 'FILE', visible: false }),
            column('Remark', { name: 'Bemerkung', type: 'TEXTAREA', visible: false }),
          ],
        },
      ),
      group('5002', [meter(3, { name: 'Water meter', medium: 'WATER', classLabel: 'Wasserzähler' })], { name: 'Building B', columns: [column('Serial Number', { name: 'Seriennummer' })] }),
    ])

  it('shows meters with tabs, key figures, filters, columns and the language of the UI', async () => {
    const get = api(meterList())
    const wrapper = await mountView('meters', get)
    expect(get).toHaveBeenCalledWith('/web/registers/meters?lang=de')

    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs.map((t) => sp(t.text()))).toEqual(['Building A 2', 'Building B 1'])
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(headers(wrapper)).toEqual(['Name', 'Medium', 'Letzter Wert', 'Letzter Wert am', 'Nächste Eichung', 'Seriennummer', 'Typ'])
    expect(names(wrapper)).toEqual(['Main meter', 'Gas meter'])

    const first = sp(wrapper.findAll('tbody tr.mod-row')[0].text())
    expect(first).toContain('1.234,5 kWh')
    expect(first).toContain('10.11.2026, 23:45')
    expect(first).toContain('31.12.2026 – bald fällig')
    expect(wrapper.findAll('tbody tr.mod-row')[1].classes()).toContain('is-overdue')
    expect(wrapper.find('tbody tr.mod-row .mod-badge.is-overdue').text()).toContain('überfällig')

    const stats = words(wrapper.find('.iso-stats'))
    expect(stats).toContain('Messstellen 2')
    expect(stats).toContain('Eichung fällig bis Ende 2026 2')
    expect(stats).toContain('Strom 1')
    expect(stats).toContain('Gas 1')

    /* filters */
    await wrapper.find('#reg-meters-medium').setValue('Gas')
    expect(names(wrapper)).toEqual(['Gas meter'])
    await wrapper.find('#reg-meters-medium').setValue('*')
    await wrapper.find('#reg-meters-type').setValue('Main')
    expect(names(wrapper)).toEqual(['Main meter'])
    await wrapper.find('#reg-meters-type').setValue('*')
    await wrapper.find('#reg-meters-search').setValue('hall MAIN')
    expect(names(wrapper)).toEqual(['Main meter'])
    await wrapper.find('#reg-meters-search').setValue('')
    await wrapper.find('.reg-overdue input[type="checkbox"]').setValue(true)
    await wrapper.find('#reg-meters-year').setValue('2025')
    expect(names(wrapper)).toEqual([])
    await wrapper.find('#reg-meters-year').setValue('2026')
    expect(names(wrapper)).toEqual(['Main meter', 'Gas meter'])
    await wrapper.find('.reg-overdue input[type="checkbox"]').setValue(false)

    /* column chooser, stored per kind */
    const ortBox = wrapper.findAll('.iso-columns-panel label').find((l) => l.text() === 'Ort')!.find('input')
    await ortBox.setValue(true)
    expect(headers(wrapper)).toContain('Ort')
    expect(JSON.parse(window.localStorage.getItem(registerColumnKey('meters'))!)).toEqual({ shown: ['a:Location'], hidden: [] })

    /* sorting */
    await wrapper.findAll('thead .iso-sort')[0].trigger('click')
    expect(names(wrapper)).toEqual(['Gas meter', 'Main meter'])

    /* second tab, keyboard */
    await tabs[0].trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.findAll('[role="tab"]')[1].attributes('aria-selected')).toBe('true')
    expect(names(wrapper)).toEqual(['Water meter'])

    /* language change: reload with the new language */
    i18n.global.locale.value = 'en'
    await flushPromises()
    expect(get).toHaveBeenLastCalledWith('/web/registers/meters?lang=en')
    expect(wrapper.findAll('[role="tab"]')[1].attributes('aria-selected')).toBe('true')
    expect(headers(wrapper)).toContain('Last value')
    wrapper.unmount()
  })

  it('shows the details of a meter with files, history and QR code', async () => {
    const history = {
      unit: 'kWh',
      samples: [
        { timestamp: '2026-11-01T00:00:00+01:00', value: 1200, note: 'Meter Tausch' },
        { timestamp: '2026-10-01T00:00:00+02:00', value: 1100, note: null },
      ],
    }
    const get = api(meterList(), history)
    const getBlob = vi.fn(async () => new Blob(['x']))
    const wrapper = await mountView('meters', get, getBlob)

    await wrapper.find('tbody tr.mod-row .mod-toggle').trigger('click')
    await flushPromises()
    const detail = wrapper.find('tr.mod-detail')
    expect(get).toHaveBeenCalledWith('/web/registers/objects/1/history?limit=12')
    const text = words(detail)
    expect(text).toContain('Datenpunkt Data 1')
    expect(text).toContain('Seriennummer A-123')
    expect(detail.find('.reg-multiline').text()).toBe('line 1\nline 2')

    const rows = detail.findAll('.reg-history tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].classes()).toContain('reg-exchange')
    expect(sp(rows[0].text())).toContain('Zählertausch')
    expect(sp(rows[0].text())).toContain('1.200,0 kWh')
    expect(rows[1].classes()).not.toContain('reg-exchange')

    expect(getBlob).toHaveBeenCalledWith('/web/registers/objects/1/files/Picture?inline=true')
    expect(getBlob).toHaveBeenCalledWith('/web/registers/meters/1/qr.svg')
    expect(getBlob).not.toHaveBeenCalledWith(expect.stringContaining('Manual'))
    expect(detail.find('img.reg-thumb').attributes('alt')).toBe('Vorschau von Bild')
    expect(detail.find('img.reg-qr-img').attributes('src')).toBe('blob:x')

    const manual = detail.findAll('button.iso-file').find((b) => b.text() === 'manual.pdf')!
    await manual.trigger('click')
    await flushPromises()
    expect(getBlob).toHaveBeenCalledWith('/web/registers/objects/1/files/Manual')

    /* object URLs released on close */
    const revoked = revokeObjectURL.mock.calls.length
    await wrapper.find('tbody tr.mod-row .mod-toggle').trigger('click')
    expect(revokeObjectURL.mock.calls.length).toBeGreaterThanOrEqual(revoked + 2)
    wrapper.unmount()
  })

  it('exports the visible columns of the tab as CSV', async () => {
    const wrapper = await mountView('meters', api(meterList()))
    let blob: Blob | null = null
    createObjectURL.mockImplementation((b: Blob) => {
      blob = b
      return 'blob:csv'
    })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    await wrapper.find('.reg-export').trigger('click')
    expect(click).toHaveBeenCalled()
    const csv = await (blob as unknown as Blob).text()
    const lines = csv.replace(/^﻿/, '').split('\r\n')
    expect(lines[0]).toBe('Name;Medium;Letzter Wert;Letzter Wert am;Nächste Eichung;Seriennummer;Typ')
    expect(lines[1]).toBe('Main meter;Strom;1234,5;10.11.2026, 23:45;2026-12-31;A-123;Main')
    expect(lines[2]).toBe('Gas meter;Gas;;;2026-01-01;;Sub')
    click.mockRestore()
    wrapper.unmount()
  })

  it('shows base data with the timestamp by period and the last values', async () => {
    const list = registerList('basedata', [group('7001', [baseData(1, { name: 'Floor area' }), baseData(2, { name: 'Staff', last: { value: 12, unit: null, timestamp: '2026-01-01T00:00:00+01:00', period: 'P1Y' } })])])
    const history = { unit: 'm²', samples: [{ timestamp: '2026-10-01T00:00:00+02:00', value: 42, note: null }] }
    const get = api(list, history)
    const wrapper = await mountView('basedata', get)
    expect(get).toHaveBeenCalledWith('/web/registers/basedata?lang=de')
    expect(wrapper.find('[role="tab"]').exists()).toBe(false)
    expect(wrapper.find('.iso-stats').exists()).toBe(false)
    expect(wrapper.find('#reg-basedata-medium').exists()).toBe(false)
    expect(headers(wrapper)).toEqual(['Name', 'Pfad', 'Letzter Wert', 'Zeitstempel'])
    const cells = wrapper.findAll('tbody tr.mod-row').map((r) => sp(r.text()))
    expect(cells[0]).toContain('42 m²')
    expect(cells[0]).toContain('Oktober 2026')
    expect(cells[1]).toContain('2026')

    await wrapper.find('tbody tr.mod-row .mod-toggle').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/registers/objects/1/history?limit=12')
    expect(sp(wrapper.find('.reg-history').text())).toContain('Oktober 2026')
    expect(wrapper.find('.reg-qr').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows equipment with path and name first and switches the kind', async () => {
    const list = registerList('equipment', [
      group('Pump', [equipment(1, { values: { Power: 7.5, Active: true, Built: '2015-10-01' } })], {
        name: 'Pumpe',
        columns: [column('Power', { name: 'Leistung', type: 'NUMBER', unit: 'kW' }), column('Active', { name: 'Aktiv', type: 'BOOLEAN' }), column('Built', { name: 'Baujahr', type: 'DATE' })],
      }),
    ])
    const get = vi.fn(async (path: string) => (path.startsWith('/web/registers/equipment') ? list : registerList('basedata', [group('1', [baseData(5)])])))
    const wrapper = await mountView('equipment', get)
    expect(headers(wrapper)).toEqual(['Pfad', 'Name', 'Leistung [kW]', 'Aktiv', 'Baujahr'])
    expect(words(wrapper.find('tbody tr.mod-row'))).toBe('› Site A / Equipment Pump 1 7,5 Ja 01.10.2015')

    await wrapper.find('tbody tr.mod-row .mod-toggle').trigger('click')
    await flushPromises()
    expect(get).not.toHaveBeenCalledWith(expect.stringContaining('/history'))

    await wrapper.setProps({ kind: 'basedata' })
    await flushPromises()
    expect(get).toHaveBeenLastCalledWith('/web/registers/basedata?lang=de')
    expect(headers(wrapper)).toEqual(['Name', 'Pfad', 'Letzter Wert', 'Zeitstempel'])
    wrapper.unmount()
  })

  it('shows cards on phone width', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    const wrapper = await mountView('meters', api(meterList()))
    await nextTick()
    const cards = wrapper.findAll('.mod-card')
    expect(cards).toHaveLength(2)
    expect(cards[0].find('h2').text()).toBe('Main meter')
    expect(cards[1].classes()).toContain('is-overdue')
    wrapper.unmount()
  })

  it('shows an error with retry and an empty state', async () => {
    const get = vi.fn().mockRejectedValueOnce(new Error('down')).mockResolvedValueOnce(registerList('meters', []))
    const wrapper = await mountView('meters', get)
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    await wrapper.find('.retry').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Keine Objekte vorhanden.')
    wrapper.unmount()
  })
})

