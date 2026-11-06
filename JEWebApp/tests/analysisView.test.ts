/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import AnalysisView from '../src/views/AnalysisView.vue'
import { at, chart, config, data, row } from './fixtures/analysis'

// no canvas in jsdom: the chart component is replaced by a placeholder
vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

describe('AnalysisView (mocked API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
  })

  async function mountView(query: Record<string, string>) {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/analyses/:id', component: AnalysisView }] })
    await router.push({ path: '/analyses/1', query })
    const auth = useAuthStore()
    const get = vi.fn(async () =>
      config([chart(0, 'LINE', [row('a'), row('b', { readable: false, name: null })]), chart(1, 'TABLE_V', [row('a')])]),
    )
    const post = vi.fn(async () =>
      data([
        {
          id: 0,
          rows: [
            { key: 'a', status: 'OK', unit: 'kWh', samples: [[at(1), 2.5, null]], stats: { min: 2.5, max: 2.5, avg: 2.5, sum: 2.5, count: 1 } },
            { key: 'b', status: 'NO_PERMISSION' },
          ],
        },
        { id: 1, rows: [{ key: 'a', status: 'OK', samples: [[at(1), 2.5, null]] }] },
      ]),
    )
    Object.assign(auth.http, { get, post })
    const wrapper = mount(AnalysisView, { props: { id: 1 }, global: { plugins: [router, i18n] } })
    await flushPromises()
    return { wrapper, get, post, router }
  }

  it('loads configuration and data with the settings of the URL', async () => {
    const { wrapper, get, post } = await mountView({ tf: 'LAST_MONTH', agg: 'HOURLY' })
    expect(get).toHaveBeenCalledWith('/web/analyses/1')
    expect(post).toHaveBeenCalledTimes(1)
    const [path, body] = post.mock.calls[0] as unknown as [string, Record<string, unknown>]
    expect(path).toBe('/web/analyses/1/data')
    expect(body).toMatchObject({ timeFrame: 'LAST_MONTH', aggregation: 'HOURLY', manipulation: 'NONE', from: null, to: null })
    expect(typeof body.timeZone).toBe('string')

    expect(wrapper.find('h1').text()).toBe('Analyse A')
    expect(wrapper.findAll('.vchart')).toHaveLength(1)
    const summary = wrapper.find('table.summary').text()
    expect(summary).toContain('Meter a')
    expect(summary).toContain('2,50')
    expect(summary).toContain('(kein Zugriff)')
    expect(summary).toContain('Keine Berechtigung')
    // the TABLE_V chart is a data table
    expect(wrapper.find('table.data').exists()).toBe(true)
  })

  it('previous period switches to CUSTOM in the URL and loads again', async () => {
    const { wrapper, post, router } = await mountView({})
    await wrapper.find('button[aria-label="Vorheriger Zeitraum"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.tf).toBe('CUSTOM')
    expect(post).toHaveBeenCalledTimes(2)
    const body = post.mock.calls[1] as unknown as [string, { timeFrame: string; from: string; to: string }]
    expect(body[1].timeFrame).toBe('CUSTOM')
    expect(Date.parse(body[1].to)).toBe(at(1) - 1)
  })
})

describe('routes', () => {
  it('match numeric ids of analyses and dashboards', async () => {
    const { router } = await import('../src/router')
    expect(router.resolve('/analyses/1001').name).toBe('analysis')
    expect(router.resolve({ name: 'analysis', params: { id: 12 } }).fullPath).toBe('/analyses/12')
    expect(router.resolve('/dashboards/7').name).toBe('dashboard')
    expect(router.resolve('/analyses/abc').name).toBe('notFound')
  })
})
