/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import DashboardView from '../src/views/DashboardView.vue'
import { dashboard, ddata, drow, widget } from './fixtures/dashboard'

// no canvas in jsdom: the chart component is replaced by a placeholder
vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

const Stub = defineComponent({ render: () => h('div') })

describe('DashboardView (mocked API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  async function mountView(query: Record<string, string> = {}) {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/dashboards/:id', name: 'dashboard', component: DashboardView },
        { path: '/analyses/:id', name: 'analysis', component: Stub },
        { path: '/reports', name: 'reports', component: Stub },
      ],
    })
    await router.push({ path: '/dashboards/7', query })
    const auth = useAuthStore()
    const widgets = [
      widget('2', 'Value', { layer: 1, rows: [drow('a')], config: { percent: { source: '3', diff: false, minFractionDigits: 0, maxFractionDigits: 0 } } }),
      widget('3', 'Value', { layer: 0, rows: [drow('b', { readable: false, name: null })] }),
      widget('4', 'Title', { title: 'Heading X', style: { ...widget('x', 'Title').style, titlePosition: 'CENTER' } }),
      widget('5', 'Analyse-Link', { title: 'To analysis', config: { link: { kind: 'ANALYSIS', id: 11, timeFrame: null, readable: true } } }),
      widget('6', 'Report-Link', { title: 'Report', config: { link: { kind: 'REPORT', id: 12, timeFrame: null, readable: true } } }),
      widget('16', 'Report-Link', { title: 'Hidden report', config: { link: { kind: 'REPORT', id: 13, timeFrame: null, readable: false } } }),
      widget('7', 'Toggle Switch', { rows: [drow('t')] }),
      widget('8', 'Future Widget'),
      widget('9', 'Chart', { rows: [drow('c')] }),
      widget('10', 'Table', { rows: [drow('a'), drow('b', { readable: false, name: null })] }),
    ]
    const get = vi.fn(async () => dashboard(widgets))
    const post = vi.fn(async () =>
      ddata({
        '2': { rows: [{ key: 'a', status: 'OK', unit: 'kWh', value: 1234.5 }] },
        '3': { rows: [{ key: 'b', status: 'NO_PERMISSION' }] },
        '7': { rows: [{ key: 't', status: 'OK', value: 1 }] },
        '9': { rows: [{ key: 'c', status: 'OK', samples: [[Date.now(), 2, null]] }] },
        '10': { rows: [{ key: 'a', status: 'OK', unit: 'kWh', value: 3 }, { key: 'b', status: 'NO_PERMISSION' }] },
      }),
    )
    const getBlob = vi.fn(async () => new Blob())
    Object.assign(auth.http, { get, post, getBlob })
    const wrapper = mount(DashboardView, { props: { id: 7 }, global: { plugins: [router, i18n] }, attachTo: document.body })
    await flushPromises()
    return { wrapper, get, post, router }
  }

  it('loads configuration and data and draws the widgets in layer order', async () => {
    const { wrapper, get, post } = await mountView({ tf: 'P1W' })
    expect(get).toHaveBeenCalledWith('/web/dashboards/7')
    const [path, body] = post.mock.calls[0] as unknown as [string, Record<string, unknown>]
    expect(path).toBe('/web/dashboards/7/data')
    expect(body).toMatchObject({ timeFrame: 'P1W', date: null, from: null, to: null })
    expect(wrapper.find('h1').text()).toBe('Dashboard A (de)')

    const uuids = wrapper.findAll('.widget').map((w) => w.attributes('data-uuid'))
    expect(uuids[0]).toBe('3')
    expect(uuids).toContain('2')

    expect(wrapper.find('[data-uuid="2"]').text()).toBe('1.234,50 kWh')
    expect(wrapper.find('[data-uuid="3"]').text()).toBe('Keine Berechtigung')
    expect(wrapper.find('[data-uuid="4"]').text()).toBe('Heading X')
    expect(wrapper.find('[data-uuid="8"]').classes()).toContain('empty')
    expect(wrapper.find('[data-uuid="9"] .vchart').exists()).toBe(true)
    expect(wrapper.find('[data-uuid="10"]').text()).toContain('(kein Zugriff)')

    const link = wrapper.find('[data-uuid="5"] a')
    expect(link.attributes('href')).toBe('/analyses/11?tf=THIS_WEEK')
    const report = wrapper.find('[data-uuid="6"] a')
    expect(report.attributes('href')).toBe('/reports?id=12')
    expect(report.attributes('aria-label')).toBe('Report')
    const hidden = wrapper.find('[data-uuid="16"] button')
    expect(hidden.attributes('aria-disabled')).toBe('true')
    expect(hidden.attributes('title')).toBe('Das Ziel ist nicht für Sie freigegeben.')

    const toggle = wrapper.find('[data-uuid="7"] input')
    expect(toggle.attributes('disabled')).toBeDefined()
    expect((toggle.element as HTMLInputElement).checked).toBe(true)
    wrapper.unmount()
  })

  it('offers the time frames and steps back with previous of the answer', async () => {
    const { wrapper, post, router } = await mountView()
    const options = wrapper.findAll('#db-timeframe option').map((o) => o.text())
    expect(options).toEqual(['Tag', 'Woche', 'Benutzerdefiniert', 'Period X'])
    const [prev, next] = wrapper.findAll('.step')
    expect(next.attributes('disabled')).toBeDefined()
    await prev.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.date).toBeTruthy()
    expect(post).toHaveBeenCalledTimes(2)
    const body = post.mock.calls[1] as unknown as [string, { date: string }]
    expect(body[1].date).toBeTruthy()

    await wrapper.find('#db-timeframe').setValue('2005')
    await flushPromises()
    expect((post.mock.calls[2] as unknown as [string, { timeFrame: string }])[1].timeFrame).toBe('2005')
    wrapper.unmount()
  })

  it('refreshes automatically, but not while the tab is hidden', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    const { wrapper, post } = await mountView()
    expect(post).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(900_000)
    await flushPromises()
    expect(post).toHaveBeenCalledTimes(2)

    Object.defineProperty(document, 'hidden', { configurable: true, get: () => true })
    vi.advanceTimersByTime(900_000)
    await flushPromises()
    expect(post).toHaveBeenCalledTimes(2)
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(post).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('shows not found for dashboards that are not shared', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/dashboards/:id', component: DashboardView }] })
    await router.push('/dashboards/99')
    const auth = useAuthStore()
    const { HttpError } = await import('../src/api/http')
    Object.assign(auth.http, {
      get: vi.fn(async () => {
        throw new HttpError(404, '')
      }),
      post: vi.fn(),
    })
    const wrapper = mount(DashboardView, { props: { id: 99 }, global: { plugins: [router, i18n] } })
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('nicht')
  })
})
