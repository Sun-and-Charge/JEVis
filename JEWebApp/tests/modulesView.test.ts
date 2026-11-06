/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Navigation } from '../src/api/navigation'
import { HttpError } from '../src/api/http'
import MaintenanceBanner from '../src/components/MaintenanceBanner.vue'
import { i18n } from '../src/i18n'
import { BANNER_REFRESH_MS, DISMISSED_STORAGE_KEY } from '../src/modules/banner'
import { useAuthStore } from '../src/stores/auth'
import { useNavigationStore } from '../src/stores/navigation'
import AppointmentsView from '../src/views/AppointmentsView.vue'
import MaintenanceView from '../src/views/MaintenanceView.vue'
import { appointment, maintenanceEntry } from './fixtures/modules'

const Stub = defineComponent({ render: () => h('div') })

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/appointments', name: 'appointments', component: Stub },
      { path: '/maintenance', name: 'maintenance', component: Stub },
    ],
  })
}

function navigation(modules: Navigation['modules']): Navigation {
  return { user: { id: 1, name: 'User 1', sysAdmin: false }, sites: [], other: { analyses: [], dashboards: [] }, modules }
}

describe('module views (mocked API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    window.localStorage.clear()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  async function mountWith(component: typeof AppointmentsView, get: ReturnType<typeof vi.fn>) {
    const auth = useAuthStore()
    Object.assign(auth.http, { get })
    const router = makeRouter()
    await router.push('/')
    const wrapper = mount(component, { global: { plugins: [router, i18n] }, attachTo: document.body })
    await flushPromises()
    return wrapper
  }

  it('lists appointments with state badges, filters and details', async () => {
    const get = vi.fn(async () => ({
      today: '2026-11-06',
      appointments: [
        appointment(1, { title: 'Later check', due: '2026-12-10T09:00' }),
        appointment(2, {
          title: 'Overdue check',
          due: '2026-11-02T09:00',
          nextDue: '2027-11-02T09:00',
          recurrence: 'YEARLY',
          overdue: true,
          description: 'Check text',
          recipients: { count: 3, names: ['Group 1'] },
          linkedObject: { id: 77, readable: false },
          lastMail: '2026-10-31T00:33:51',
        }),
        appointment(3, { title: 'Soon check', due: '2026-11-08T09:00', dueSoon: true, site: { id: 1002, name: 'Site B', names: {} } }),
        appointment(4, { title: 'Done check', status: 'DONE' }),
      ],
    }))
    const wrapper = await mountWith(AppointmentsView, get)
    expect(get).toHaveBeenCalledWith('/web/appointments')

    const rows = wrapper.findAll('tbody tr.mod-row')
    expect(rows.map((r) => r.find('th').text())).toEqual(['›Overdue check', '›Soon check', '›Later check'])
    expect(rows[0].classes()).toContain('is-overdue')
    expect(rows[0].text()).toContain('Überfällig')
    expect(rows[0].text()).toContain('02.11.2026, 09:00')
    expect(rows[0].text()).toContain('Jährlich')
    expect(rows[0].text()).toContain('3 Tage vorher')
    expect(rows[0].text()).toContain('Kein Leserecht (ID 77)')
    expect(rows[1].classes()).toContain('is-dueSoon')
    expect(rows[1].text()).toContain('Bald fällig')
    expect(wrapper.findAll('thead th').length).toBe(8)
    expect(wrapper.text()).toContain('Stand: 06.11.2026')

    // details
    const toggle = rows[0].find('button.mod-toggle')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    const detail = wrapper.find('#ap-d-2')
    expect(detail.text()).toContain('Check text')
    expect(detail.text()).toContain('Group 1 + 2 weitere ohne Leserecht')
    expect(detail.text()).toContain('02.11.2027, 09:00')
    expect(detail.text()).toContain('von User 1')

    // filters
    await wrapper.find('input[type="radio"][value="done"]').setValue()
    expect(wrapper.findAll('tbody tr.mod-row').map((r) => r.find('th').text())).toEqual(['›Done check'])
    await wrapper.find('input[type="radio"][value="all"]').setValue()
    expect(wrapper.findAll('tbody tr.mod-row').length).toBe(4)
    await wrapper.find('#ap-site').setValue('1002')
    expect(wrapper.findAll('tbody tr.mod-row').length).toBe(1)
    await wrapper.find('#ap-site').setValue('all')
    await wrapper.find('#ap-search').setValue('later')
    expect(wrapper.findAll('tbody tr.mod-row').length).toBe(1)
    expect(wrapper.text()).toContain('1 von 4 Einträgen')
    await wrapper.find('#ap-search').setValue('xyz')
    expect(wrapper.text()).toContain('Keine Termine für diese Auswahl.')
    wrapper.unmount()
  })

  it('shows cards on phone width', async () => {
    const matchMedia = vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    vi.stubGlobal('matchMedia', matchMedia)
    try {
      const get = vi.fn(async () => ({ today: '2026-11-06', appointments: [appointment(1, { overdue: true, description: 'Card text' })] }))
      const wrapper = await mountWith(AppointmentsView, get)
      expect(wrapper.find('table').exists()).toBe(false)
      const card = wrapper.find('li.mod-card')
      expect(card.classes()).toContain('is-overdue')
      expect(card.text()).toContain('Überfällig')
      await card.find('button.mod-toggle').trigger('click')
      expect(card.find('#ap-c-1').text()).toContain('Card text')
      wrapper.unmount()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('shows an error with retry', async () => {
    const get = vi.fn().mockRejectedValueOnce(new HttpError(500, '')).mockResolvedValueOnce({ today: '2026-11-06', appointments: [] })
    const wrapper = await mountWith(AppointmentsView, get)
    expect(wrapper.find('[role="alert"]').text()).toContain('nicht geladen')
    await wrapper.find('.retry').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Keine Termine')
    wrapper.unmount()
  })

  it('lists maintenance entries with type and type filter', async () => {
    i18n.global.locale.value = 'en'
    const get = vi.fn(async () => ({
      now: '2026-11-06T10:15',
      entries: [
        maintenanceEntry(1, { title: 'Server update', impact: 'Web app not available', durationMinutes: 90, end: '2026-11-06T19:30' }),
        maintenanceEntry(2, { title: 'Slow charts', type: 'KNOWN_ISSUE', status: 'IN_PROGRESS', start: '2026-11-05T08:00', end: null, durationMinutes: null }),
        maintenanceEntry(3, { title: 'Old update', status: 'DONE', active: false }),
      ],
    }))
    const wrapper = await mountWith(MaintenanceView, get)
    expect(get).toHaveBeenCalledWith('/web/maintenance')
    const rows = wrapper.findAll('tbody tr.mod-row')
    expect(rows.length).toBe(2)
    expect(rows[0].text()).toContain('Known issue')
    expect(rows[0].text()).toContain('In progress')
    expect(rows[0].classes()).toContain('is-inProgress')
    expect(rows[1].text()).toContain('Server update')
    expect(rows[1].text()).toContain('1 hr 30 min')
    expect(rows[1].text()).toContain('Web app not available')
    expect(wrapper.findAll('thead th').map((th) => th.text())[0]).toBe('Type')

    await wrapper.find('#mt-type').setValue('MAINTENANCE')
    expect(wrapper.findAll('tbody tr.mod-row').length).toBe(1)
    await wrapper.find('input[type="radio"][value="all"]').setValue()
    expect(wrapper.findAll('tbody tr.mod-row').length).toBe(2)
    wrapper.unmount()
  })
})

describe('maintenance banner', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    window.localStorage.clear()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  async function mountBanner(modules: Navigation['modules'], get: ReturnType<typeof vi.fn>) {
    const auth = useAuthStore()
    auth.user = { id: 1, name: 'User 1' }
    Object.assign(auth.http, { get })
    useNavigationStore().data = navigation(modules)
    const router = makeRouter()
    await router.push('/')
    const wrapper = mount(MaintenanceBanner, { global: { plugins: [router, i18n] }, attachTo: document.body })
    await flushPromises()
    return wrapper
  }

  it('makes no request without readable maintenance entries', async () => {
    const get = vi.fn()
    const wrapper = await mountBanner({ appointments: 2, maintenance: 0 }, get)
    expect(get).not.toHaveBeenCalled()
    expect(wrapper.find('.notice').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows active entries, hides dismissed ones until they change, and refreshes', async () => {
    vi.useFakeTimers()
    let entries = [
      maintenanceEntry(1, { title: 'Server update', impact: 'Short outage' }),
      maintenanceEntry(2, { title: 'Finished', status: 'DONE', active: false }),
    ]
    const get = vi.fn(async () => ({ now: '2026-11-06T10:15', entries }))
    const wrapper = await mountBanner({ appointments: 0, maintenance: 2 }, get)
    expect(get).toHaveBeenCalledTimes(1)
    const notices = wrapper.findAll('.notice')
    expect(notices.length).toBe(1)
    expect(notices[0].text()).toContain('Wartung: Server update')
    expect(notices[0].text()).toContain('06.11.2026, 18:00–18:30')
    expect(notices[0].text()).toContain('Short outage')
    expect(notices[0].find('a').attributes('href')).toBe('/maintenance')

    await notices[0].find('button.close').trigger('click')
    expect(wrapper.find('.notice').exists()).toBe(false)
    expect(window.localStorage.getItem(DISMISSED_STORAGE_KEY)).toContain('Server update')

    // unchanged after the refresh: stays hidden
    await vi.advanceTimersByTimeAsync(BANNER_REFRESH_MS)
    expect(get).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.notice').exists()).toBe(false)

    // changed: shown again
    entries = [maintenanceEntry(1, { title: 'Server update', impact: 'Short outage', status: 'IN_PROGRESS' })]
    await vi.advanceTimersByTimeAsync(BANNER_REFRESH_MS)
    expect(get).toHaveBeenCalledTimes(3)
    expect(wrapper.find('.notice').text()).toContain('Läuft')
    wrapper.unmount()

    // no more requests after unmount (logout)
    await vi.advanceTimersByTimeAsync(BANNER_REFRESH_MS)
    expect(get).toHaveBeenCalledTimes(3)
  })

  it('remembers dismissed entries across mounts', async () => {
    const get = vi.fn(async () => ({ now: '2026-11-06T10:15', entries: [maintenanceEntry(1)] }))
    const first = await mountBanner({ appointments: 0, maintenance: 1 }, get)
    await first.find('button.close').trigger('click')
    first.unmount()
    setActivePinia(createPinia())
    const second = await mountBanner({ appointments: 0, maintenance: 1 }, get)
    expect(second.find('.notice').exists()).toBe(false)
    second.unmount()
  })
})
