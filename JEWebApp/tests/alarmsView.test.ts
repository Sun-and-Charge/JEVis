/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AlarmList } from '../src/api/alarms'
import { HttpError } from '../src/api/http'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import AlarmsView from '../src/views/AlarmsView.vue'
import { alarm, alarmList } from './fixtures/alarms'

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

const Stub = defineComponent({ render: () => h('div') })
const sp = (s: string) => s.replace(/\s+/g, ' ').trim()

async function mountView(get: ReturnType<typeof vi.fn>) {
  const auth = useAuthStore()
  Object.assign(auth.http, { get })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }] })
  await router.push('/')
  const wrapper = mount(AlarmsView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

function api(list: AlarmList | Error, samples: unknown = { unit: 'kWh', samples: [[Date.parse('2026-11-10T13:00:00Z'), 650]], truncated: false }) {
  return vi.fn(async (path: string) => {
    if (path.startsWith('/web/alarms?')) {
      if (list instanceof Error) throw list
      return list
    }
    if (path.startsWith('/web/rows/')) {
      if (samples instanceof Error) throw samples
      return samples
    }
    throw new Error('unexpected ' + path)
  })
}

const rows = (w: Awaited<ReturnType<typeof mountView>>) => w.findAll('tbody tr.alarms-row')

describe('alarms view (mocked API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 11, 12, 0))
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  const list = () =>
    alarmList([
      alarm('a1', { isValue: 1812.5 }),
      alarm('a2', {
        timestamp: '2026-11-08T08:00:00.000+01:00',
        type: 'GAP',
        typeName: 'Datenlücke',
        isValue: null,
        shouldBeValue: null,
        operator: null,
        configuration: { id: 9101, name: 'Gas - Lücken', checked: true, checkedBy: 'admin', checkedAt: '2026-11-09T10:00:00.000+01:00' },
        row: { id: 8101, readable: false },
        site: { id: 7101, name: 'Zweigwerk' },
      }),
    ])

  it('requests the last 7 days of open alarms with time zone and language and lists them', async () => {
    const get = api(list())
    const w = await mountView(get)
    const path = get.mock.calls[0][0] as string
    expect(path).toContain('from=2026-11-04&to=2026-11-11&state=open')
    expect(path).toContain('timeZone=')
    expect(path).toContain('lang=de')
    expect(w.text()).toContain('Nur Ansicht – Alarme werden im Control Center quittiert.')
    expect(w.text()).toContain('protokolliert')
    expect(rows(w)).toHaveLength(2)
    const first = sp(rows(w)[0].text())
    expect(first).toContain('Strom - Gesamt - Bereinigte Daten')
    expect(first).toContain('Daten / Strom')
    expect(first).toContain('1.812,5')
    expect(first).toContain('Grenzwert Stufe 1')
    expect(first).toContain('Offen')
    expect(sp(rows(w)[1].text())).toContain('Kein Leserecht')
    const badge = rows(w)[1].find('.alarm-state')
    expect(badge.text()).toBe('Quittiert')
    expect(badge.attributes('title')).toContain('admin')
    w.unmount()
  })

  it('reloads with the chosen state', async () => {
    const get = api(list())
    const w = await mountView(get)
    get.mockClear()
    await w.find('input[name="alarms-state"][value="all"]').setValue(true)
    await flushPromises()
    expect(get.mock.calls[0][0]).toContain('state=all')
    w.unmount()
  })

  it('filters by type chip, site and search and shows the no-match state', async () => {
    const w = await mountView(api(list()))
    const chip = w.findAll('.notes-chip').find((b) => b.text().startsWith('Datenlücke'))!
    expect(chip.attributes('aria-pressed')).toBe('false')
    await chip.trigger('click')
    expect(chip.attributes('aria-pressed')).toBe('true')
    expect(rows(w)).toHaveLength(1)
    expect(sp(rows(w)[0].text())).toContain('Gas - Lücken')
    await chip.trigger('click')
    await w.find('#alarms-site').setValue('7100')
    expect(rows(w)).toHaveLength(1)
    await w.find('#alarms-site').setValue('all')
    await w.find('#alarms-search').setValue('nichts davon')
    expect(rows(w)).toHaveLength(0)
    expect(w.text()).toContain('Kein Alarm passt')
    w.unmount()
  })

  it('opens one detail panel with the curve ±1 day and the limit; unreadable rows load nothing', async () => {
    const get = api(list())
    const w = await mountView(get)
    await rows(w)[0].trigger('click')
    await flushPromises()
    const call = get.mock.calls.find((c) => String(c[0]).startsWith('/web/rows/'))![0] as string
    expect(call).toContain('/web/rows/8100/samples?from=2026-11-09T13%3A15%3A00.000Z&to=2026-11-11T13%3A15%3A00.000Z')
    const chart = w.findComponent({ name: 'VChart' })
    const data = (chart.props('option') as { series: { markLine: { data: { yAxis?: number }[] } }[] }).series[0].markLine.data
    expect(data.some((d) => d.yAxis === 700)).toBe(true)
    expect(rows(w)[0].find('.mod-toggle').attributes('aria-expanded')).toBe('true')
    get.mockClear()
    await rows(w)[1].trigger('click')
    await flushPromises()
    expect(get).not.toHaveBeenCalled()
    expect(w.findAll('.mod-detail')).toHaveLength(1)
    expect(w.find('.mod-detail').text()).toContain('darf nicht gelesen')
    w.unmount()
  })

  it('shows alarms whose object no longer exists without loading a curve', async () => {
    const get = api(alarmList([alarm('gone', { row: null, logValue: null, logValueName: 'System' })]))
    const w = await mountView(get)
    expect(sp(rows(w)[0].text())).toContain('System')
    get.mockClear()
    await rows(w)[0].trigger('click')
    await flushPromises()
    expect(get).not.toHaveBeenCalled()
    expect(w.find('.mod-detail').text()).toContain('keinen Datenpunkt')
    w.unmount()
  })

  it('handles a 404 and other errors of the samples', async () => {
    let w = await mountView(api(list(), new HttpError(404, '')))
    await rows(w)[0].trigger('click')
    await flushPromises()
    expect(w.find('.mod-detail').text()).toContain('nicht lesbar')
    w.unmount()
    w = await mountView(api(list(), new HttpError(500, '')))
    await rows(w)[0].trigger('click')
    await flushPromises()
    expect(w.find('.mod-detail .alert-error').exists()).toBe(true)
    w.unmount()
  })

  it('shows the truncated hint, the empty list and load errors with retry', async () => {
    let w = await mountView(api(alarmList([alarm('a')], { truncated: true })))
    expect(w.find('.notes-truncated').exists()).toBe(true)
    w.unmount()
    w = await mountView(api(alarmList([])))
    expect(w.text()).toContain('keine Alarme')
    w.unmount()
    const get = api(new HttpError(500, ''))
    w = await mountView(get)
    expect(w.find('.alert-error').exists()).toBe(true)
    await w.find('.alert-error .retry').trigger('click')
    expect(get).toHaveBeenCalledTimes(2)
    w.unmount()
  })

  it('does not load an invalid time frame and resets to the last 7 days', async () => {
    const get = api(list())
    const w = await mountView(get)
    get.mockClear()
    await w.find('#alarms-from').setValue('2025-01-01')
    await flushPromises()
    expect(get).not.toHaveBeenCalled()
    expect(w.text()).toContain('höchstens 1 Jahr')
    expect(rows(w)).toHaveLength(2)
    await w.find('.notes-reset').trigger('click')
    await flushPromises()
    expect(get.mock.calls[0][0]).toContain('from=2026-11-04&to=2026-11-11')
    w.unmount()
  })
})
