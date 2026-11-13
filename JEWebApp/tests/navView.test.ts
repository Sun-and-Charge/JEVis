/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Navigation } from '../src/api/navigation'
import { i18n } from '../src/i18n'
import { emptyPrefs, prefsKey, type NavPrefs } from '../src/nav/prefs'
import { useAuthStore } from '../src/stores/auth'
import { useNavigationStore } from '../src/stores/navigation'
import ActionPlansView from '../src/views/ActionPlansView.vue'
import AnalysesView from '../src/views/AnalysesView.vue'
import AppShell from '../src/views/AppShell.vue'
import DashboardsView from '../src/views/DashboardsView.vue'
import HomeView from '../src/views/HomeView.vue'
import RegisterView from '../src/views/RegisterView.vue'
import { directories } from './fixtures/analysisEdit'
import { action, actionPlan } from './fixtures/iso'
import { item, navigation } from './fixtures/nav'
import { group, meter, registerList } from './fixtures/registers'

// the logo of the shell is a public asset the test environment cannot resolve
vi.mock('/suc_logo_dark.png', () => ({ default: '/suc_logo_dark.png' }))

/* Navigation of the shell (sidebar, site picker, search) and the overview pages with a mocked API; neutral data. */

const Stub = defineComponent({ render: () => h('div', { class: 'stub' }) })
const App = defineComponent({ render: () => h(RouterView) })
const USER = 7
const mounted: VueWrapper[] = []
const sp = (s: string) => s.replace(/\s+/g, ' ').trim()

const MODULE_ROUTES = ['appointments', 'maintenance', 'actionPlans', 'nonconformities', 'legal', 'readings', 'meters', 'basedata', 'equipment', 'notes', 'alarms', 'reports', 'contracts', 'invoices', 'costs']

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: Stub },
      {
        path: '/',
        component: AppShell,
        children: [
          { path: '', name: 'home', component: HomeView },
          { path: 'password', name: 'password', component: Stub },
          { path: 'dashboards', name: 'dashboards', component: DashboardsView },
          { path: 'analyses', name: 'analyses', component: AnalysesView },
          { path: 'dashboards/:id', name: 'dashboard', component: Stub },
          { path: 'analyses/:id', name: 'analysis', component: Stub },
          ...MODULE_ROUTES.map((name) => ({ path: name.toLowerCase(), name, component: Stub })),
        ],
      },
    ],
  })
}

function storePrefs(prefs: Partial<NavPrefs>) {
  window.localStorage.setItem(prefsKey(USER), JSON.stringify({ ...emptyPrefs(), ...prefs }))
}
const storedPrefs = (): NavPrefs => JSON.parse(window.localStorage.getItem(prefsKey(USER)) ?? 'null')

async function mountShell(path = '/', nav: Navigation = navigation(), dirs: unknown = []) {
  const get = vi.fn(async (p: string) => {
    if (p === '/web/navigation') return nav
    if (p === '/web/analyses/directories') return dirs
    throw new Error('GET ' + p)
  })
  const auth = useAuthStore()
  auth.user = { id: USER, name: 'Muster' }
  Object.assign(auth.http, { get })
  const router = makeRouter()
  await router.push(path)
  const w = mount(App, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(w)
  await flushPromises()
  return { w, router, get }
}

const side = (w: VueWrapper) => w.get('nav#side-nav')
const groupBtn = (w: VueWrapper, label: string) => side(w).findAll('button.group-btn').find((b) => b.text().includes(label))!
const palette = () => document.querySelector('[role="dialog"]') as HTMLElement | null
const options = () => Array.from(document.querySelectorAll('[role="option"]')).map((o) => sp(o.querySelector('.pal-label')!.textContent ?? ''))

function key(target: EventTarget, k: string, mods: KeyboardEventInit = {}) {
  target.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true, ...mods }))
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
  window.localStorage.clear()
})

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
  document.body.innerHTML = ''
})

describe('sidebar', () => {
  it('Start, Dashboards, Analysen, the groups with visible modules and Berichte', async () => {
    const { w } = await mountShell()
    const nav = side(w)
    expect(nav.attributes('aria-label')).toBe('Hauptnavigation')
    const top = nav.findAll('.nav-list > li > .nav-link').map((l) => sp(l.text()))
    expect(top[0]).toBe('Start')
    expect(top[1]).toBe('Dashboards')
    expect(top[2]).toBe('Analysen')
    expect(nav.findAll('button.group-btn .label').map((b) => b.text())).toEqual(['ISO 50001', 'Betrieb', 'Anlagen'])
    expect(nav.text()).not.toContain('Finanzen')
    expect(top[top.length - 1]).toContain('Berichte')
    // no dashboards / analyses tree any more, no "Neue Analyse" in the sidebar
    expect(nav.text()).not.toContain('Lageplan')
    expect(nav.find('.new-analysis').exists()).toBe(false)
  })

  it('collapsed groups show their module count and the open alarms; state is remembered', async () => {
    const { w } = await mountShell()
    const iso = groupBtn(w, 'ISO 50001')
    expect(iso.attributes('aria-expanded')).toBe('false')
    expect(sp(iso.find('.group-count').text())).toContain('(2)')
    const ops = groupBtn(w, 'Betrieb')
    expect(ops.find('.module-count.is-alert').text()).toContain('4')
    expect(ops.find('.visually-hidden').text()).toBe('4 offene Alarme (7 Tage)')

    await ops.trigger('click')
    expect(groupBtn(w, 'Betrieb').attributes('aria-expanded')).toBe('true')
    const list = side(w).get('#nav-group-operations')
    expect(list.isVisible()).toBe(true)
    expect(list.findAll('a .label').map((a) => a.text())).toEqual(['Ablesung', 'Termine', 'Alarme'])
    expect(list.findAll('a .module-count').map((c) => c.text())).toEqual(['5', '3', '44 offene Alarme (7 Tage)'])
    expect(list.get('a .module-count.is-alert').attributes('title')).toBe('4 offene Alarme (7 Tage)')
    // open: the badges are on the modules, not on the header
    expect(groupBtn(w, 'Betrieb').find('.module-count').exists()).toBe(false)
    expect(storedPrefs().groups).toEqual({ operations: true })

    await groupBtn(w, 'Betrieb').trigger('click')
    expect(storedPrefs().groups).toEqual({ operations: false })
  })

  it('the group of the current route opens, the module is the current page', async () => {
    const { w, router } = await mountShell('/legal')
    expect(groupBtn(w, 'ISO 50001').attributes('aria-expanded')).toBe('true')
    expect(groupBtn(w, 'Betrieb').attributes('aria-expanded')).toBe('false')
    expect(side(w).get('a[aria-current="page"]').text()).toContain('Rechtskataster')
    await router.push('/meters')
    await flushPromises()
    expect(groupBtn(w, 'Anlagen').attributes('aria-expanded')).toBe('true')
    expect(storedPrefs().groups).toEqual({ iso: true, assets: true })
  })

  it('favourites below Dashboards and Analysen, sorted by name, at most eight', async () => {
    const many = navigation({
      other: { dashboards: Array.from({ length: 10 }, (_, i) => item(200 + i, `Zusatz ${String.fromCharCode(74 - i)}`)), analyses: [] },
    })
    storePrefs({ favourites: ['d:100', 'a:302', 'd:101', ...Array.from({ length: 10 }, (_, i) => 'd:' + (200 + i))] })
    const { w } = await mountShell('/', many)
    const lists = side(w).findAll('.fav-list')
    expect(lists).toHaveLength(2)
    expect(lists[0].attributes('aria-label')).toBe('Favoriten: Dashboards')
    const dash = lists[0].findAll('a .label').map((a) => a.text())
    expect(dash).toHaveLength(8)
    expect(dash.slice(0, 3)).toEqual(['Lageplan', 'Übersicht', 'Zusatz A'])
    expect(lists[1].findAll('a .label').map((a) => a.text())).toEqual(['Wasser'])
    expect(lists[0].get('a').attributes('title')).toBe('Muster AG › Musterwerk')
  })

  it('hides Dashboards / Analysen without items and shows the hint without anything', async () => {
    const empty = navigation({ sites: [], other: { dashboards: [], analyses: [] }, modules: null })
    const { w } = await mountShell('/', empty)
    expect(side(w).findAll('.nav-list > li').map((l) => sp(l.text()))).toEqual(['Start'])
    expect(side(w).text()).toContain('Keine Analysen oder Dashboards freigegeben.')
  })

  it('Start opens the start dashboard if there is one', async () => {
    const { w, router } = await mountShell('/', navigation({ startDashboard: 101 }))
    expect(router.currentRoute.value.fullPath).toBe('/dashboards/101')
    expect(side(w).get('.nav-list > li > a').attributes('href')).toBe('/dashboards/101')
  })

  it('collapses to an icon rail (names stay accessible, tooltips) and expands again', async () => {
    const { w } = await mountShell()
    await side(w).get('.collapse-btn').trigger('click')
    expect(w.get('.shell').classes()).toContain('rail')
    expect(storedPrefs().collapsed).toBe(true)
    const start = side(w).get('.nav-list > li > a')
    expect(start.attributes('title')).toBe('Start')
    expect(start.get('.label').text()).toBe('Start')
    expect(start.find('svg[aria-hidden="true"]').exists()).toBe(true)
    expect(side(w).find('.fav-list').exists()).toBe(false)
    expect(side(w).get('.collapse-btn').attributes('aria-label')).toBe('Navigation ausklappen')
    // a group in the rail: expands the sidebar with the group open
    await groupBtn(w, 'Anlagen').trigger('click')
    expect(w.get('.shell').classes()).not.toContain('rail')
    expect(groupBtn(w, 'Anlagen').attributes('aria-expanded')).toBe('true')
    expect(storedPrefs().collapsed).toBe(false)
  })

  it('starts collapsed when remembered', async () => {
    storePrefs({ collapsed: true })
    const { w } = await mountShell()
    expect(w.get('.shell').classes()).toContain('rail')
  })

  it('records opened dashboards and analyses as recently opened', async () => {
    const { router } = await mountShell('/dashboards/100')
    await router.push('/analyses/301')
    await router.push('/meters')
    await router.push('/dashboards/100')
    await flushPromises()
    expect(storedPrefs().recent).toEqual(['d:100', 'a:301'])
  })
})

describe('site picker', () => {
  it('lists Alle and the sites (indented), remembers the choice', async () => {
    const { w } = await mountShell()
    const select = w.get('#site-picker')
    expect(w.get('label[for="site-picker"]').text()).toBe('Standort')
    expect(select.findAll('option').map((o) => o.element.textContent)).toEqual(['Alle', 'Lager Nord', 'Muster AG', '   Musterwerk', ' '.repeat(6) + 'Halle 2'])
    expect((select.element as HTMLSelectElement).value).toBe('all')
    await select.setValue('2')
    expect(storedPrefs().site).toBe(2)
  })

  it('a site that is gone shows as Alle', async () => {
    storePrefs({ site: 99 })
    const { w } = await mountShell()
    expect((w.get('#site-picker').element as HTMLSelectElement).value).toBe('all')
  })
})

describe('search (command palette)', () => {
  it('opens with Strg+K, finds dashboards, analyses and modules, opens with the keyboard', async () => {
    const { w, router } = await mountShell()
    expect(palette()).toBeNull()
    key(window, 'k', { ctrlKey: true })
    await flushPromises()
    const dialog = palette()!
    expect(dialog.getAttribute('aria-modal')).toBe('true')
    const input = dialog.querySelector('input')!
    expect(document.activeElement).toBe(input)
    expect(input.getAttribute('role')).toBe('combobox')
    expect(input.getAttribute('aria-controls')).toBe('palette-list')
    // without a search: pages and modules
    expect(options()).toContain('Messstellen')

    input.value = 'lage'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    // label start first, then hits in the group / site path ("Anlagen", "Lager Nord"), modules before analyses
    expect(options()).toEqual(['Lageplan', 'Messstellen', 'Wasser'])
    expect(input.getAttribute('aria-expanded')).toBe('true')
    expect(input.getAttribute('aria-activedescendant')).toBe('palette-opt-0')
    expect(document.querySelector('#palette-opt-0')!.getAttribute('aria-selected')).toBe('true')

    key(input, 'ArrowDown')
    await flushPromises()
    expect(input.getAttribute('aria-activedescendant')).toBe('palette-opt-1')
    key(input, 'ArrowUp')
    await flushPromises()
    key(input, 'Enter')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/dashboards/101')
    expect(palette()).toBeNull()
    expect(storedPrefs().recent).toEqual(['d:101'])
    expect(w.exists()).toBe(true)
  })

  it('searches the site path, shows the kind; no hits; Esc closes and returns the focus', async () => {
    const { w } = await mountShell()
    const btn = w.get('button.search-btn')
    expect(btn.attributes('aria-label')).toBe('Suche öffnen (Strg+K)')
    ;(btn.element as HTMLElement).focus()
    await btn.trigger('click')
    await flushPromises()
    const input = palette()!.querySelector('input')!
    input.value = 'halle'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(options()).toEqual(['Sankey'])
    const option = document.querySelector('[role="option"]')!
    expect(option.querySelector('.pal-detail')!.textContent).toBe('Muster AG › Musterwerk › Halle 2')
    expect(option.querySelector('.pal-kind')!.textContent).toBe('Dashboard')
    input.value = 'xyz'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(palette()!.textContent).toContain('Keine Treffer.')
    expect(input.getAttribute('aria-expanded')).toBe('false')
    key(input, 'Escape')
    await flushPromises()
    expect(palette()).toBeNull()
    expect(document.activeElement).toBe(btn.element)
  })

  it('"/" opens it outside text fields only; a module hit navigates', async () => {
    const { w, router } = await mountShell('/dashboards')
    const search = w.get('input[type="search"]').element
    key(search, '/')
    await flushPromises()
    expect(palette()).toBeNull()
    key(document.body, '/')
    await flushPromises()
    const input = palette()!.querySelector('input')!
    input.value = 'mess'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(options()).toEqual(['Messstellen'])
    ;(document.querySelector('[role="option"]') as HTMLElement).click()
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('meters')
  })

  it('recently opened come first without a search', async () => {
    storePrefs({ recent: ['a:302', 'd:100'] })
    await mountShell()
    key(window, 'k', { metaKey: true })
    await flushPromises()
    expect(options().slice(0, 3)).toEqual(['Wasser', 'Übersicht', 'Start'])
  })
})

describe('overview pages', () => {
  const cards = (w: VueWrapper, scope = '.overview > .ov-grid') => w.findAll(scope + ' .ov-card .ov-name').map((n) => n.text())

  it('dashboards as cards with site path, items without site under "Weitere"', async () => {
    const { w } = await mountShell('/dashboards')
    expect(w.get('h1').text()).toBe('Dashboards')
    expect(cards(w)).toEqual(['Lageplan', 'Sankey', 'Übersicht'])
    expect(w.findAll('.overview > .ov-grid .ov-site').map((s) => s.text())).toEqual(['Muster AG › Musterwerk', 'Muster AG › Musterwerk › Halle 2', 'Muster AG › Musterwerk'])
    expect(w.get('.ov-other h2').text()).toBe('Weitere')
    expect(cards(w, '.ov-other')).toEqual(['Wetter'])
    expect(w.get('.ov-total').text()).toBe('4 von 4')
    expect(w.get('.overview > .ov-grid a').attributes('href')).toBe('/dashboards/101')
  })

  it('search, site filter (sub-sites included) and "Nur Favoriten"', async () => {
    storePrefs({ favourites: ['d:102', 'd:103'] })
    const { w } = await mountShell('/dashboards')
    await w.get('#ov-dashboard-search').setValue('über')
    expect(cards(w)).toEqual(['Übersicht'])
    expect(w.find('.ov-other').exists()).toBe(false)
    await w.get('#ov-dashboard-search').setValue('')
    await w.get('#ov-dashboard-site').setValue('3')
    expect(cards(w)).toEqual(['Sankey'])
    await w.get('#ov-dashboard-site').setValue('1')
    expect(cards(w)).toEqual(['Lageplan', 'Sankey', 'Übersicht'])
    await w.get('#ov-dashboard-site').setValue('4')
    expect(w.text()).toContain('Keine Treffer für diese Auswahl.')
    await w.get('#ov-dashboard-site').setValue('all')
    const toggle = w.get('button.ov-toggle')
    expect(toggle.attributes('aria-pressed')).toBe('false')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-pressed')).toBe('true')
    expect(cards(w)).toEqual(['Sankey'])
    expect(cards(w, '.ov-other')).toEqual(['Wetter'])
    // the page filter does not change the remembered site
    expect(storedPrefs().site).toBe('all')
  })

  it('the site of the top bar is the default filter and follows changes there', async () => {
    storePrefs({ site: 2 })
    const { w } = await mountShell('/dashboards')
    expect((w.get('#ov-dashboard-site').element as HTMLSelectElement).value).toBe('2')
    expect(cards(w)).toEqual(['Lageplan', 'Sankey', 'Übersicht'])
    expect(w.find('.ov-other').exists()).toBe(false)
    await w.get('#site-picker').setValue('3')
    expect(cards(w)).toEqual(['Sankey'])
  })

  it('the star toggles a favourite (sidebar follows)', async () => {
    const { w } = await mountShell('/dashboards')
    const star = w.findAll('.overview > .ov-grid .ov-star')[0]
    expect(star.attributes('aria-pressed')).toBe('false')
    expect(star.attributes('aria-label')).toBe('„Lageplan“ als Favorit')
    expect(star.attributes('title')).toBe('„Lageplan“ zu den Favoriten hinzufügen')
    await star.trigger('click')
    expect(star.attributes('aria-pressed')).toBe('true')
    expect(star.attributes('title')).toBe('„Lageplan“ aus den Favoriten entfernen')
    expect(storedPrefs().favourites).toEqual(['d:101'])
    expect(side(w).get('.fav-list').text()).toContain('Lageplan')
    await star.trigger('click')
    expect(storedPrefs().favourites).toEqual([])
    expect(side(w).find('.fav-list').exists()).toBe(false)
  })

  it('recently opened of both kinds', async () => {
    storePrefs({ recent: ['a:300', 'd:100', 'a:999'] })
    const { w } = await mountShell('/dashboards')
    expect(w.findAll('.ov-recent a').map((a) => sp(a.text()))).toEqual(['Analyse: Strom gesamt', 'Dashboard: Übersicht'])
  })

  it('analyses page with "Neue Analyse" in the header', async () => {
    const { w } = await mountShell('/analyses', navigation(), directories)
    expect(w.get('h1').text()).toBe('Analysen')
    expect(w.get('.ov-head .new-analysis').text()).toContain('Neue Analyse')
    expect(cards(w)).toEqual(['Gas', 'Strom gesamt', 'Wasser'])
    expect(w.findAll('.overview > .ov-grid .ov-site').map((s) => s.text())).toEqual(['Muster AG › Musterwerk', 'Muster AG', 'Lager Nord'])
    await w.get('.new-analysis').trigger('click')
    expect(document.querySelector('[role="dialog"]')!.textContent).toContain('Neue Analyse')
  })

  it('analyses entry and page without analyses but with a directory to create in', async () => {
    const nav = navigation({ sites: [], other: { dashboards: [], analyses: [] } })
    const { w } = await mountShell('/analyses', nav, directories)
    expect(side(w).findAll('.nav-list > li > .nav-link').map((l) => sp(l.text()))).toContain('Analysen')
    expect(w.text()).toContain('Keine Analysen freigegeben.')
    expect(w.find('.new-analysis').exists()).toBe(true)
  })
})

describe('start page without start dashboard', () => {
  it('shows favourites and recently opened', async () => {
    storePrefs({ favourites: ['a:302', 'd:100'], recent: ['d:101'] })
    const { w } = await mountShell('/')
    const fav = w.get('.home-favourites')
    expect(fav.findAll('.ov-name').map((n) => n.text())).toEqual(['Übersicht', 'Wasser'])
    expect(w.get('.home-recent').text()).toContain('Lageplan')
    await fav.findAll('.ov-star')[0].trigger('click')
    expect(storedPrefs().favourites).toEqual(['a:302'])
  })

  it('hints when there is nothing yet', async () => {
    const { w } = await mountShell('/')
    expect(w.get('.home-favourites').text()).toContain('Noch keine Favoriten')
    expect(w.get('.home-recent').text()).toContain('Noch nichts geöffnet.')
  })
})

describe('module views preselect the tab of the chosen site', () => {
  async function mountView(component: unknown, props: Record<string, unknown>, get: ReturnType<typeof vi.fn>) {
    const auth = useAuthStore()
    auth.user = { id: USER, name: 'Muster' }
    Object.assign(auth.http, { get })
    const router = makeRouter()
    await router.push('/password')
    const w = mount(component as never, { props: props as never, global: { plugins: [router, i18n] }, attachTo: document.body })
    mounted.push(w)
    await flushPromises()
    return w
  }

  const site = (id: number, name: string) => item(id, name)
  const meters = () =>
    registerList('meters', [
      group('g4', [meter(1, { name: 'Zähler Lager' })], { name: 'Lager Nord', site: site(4, 'Lager Nord') }),
      group('g2', [meter(2, { name: 'Zähler Werk' })], { name: 'Musterwerk', site: site(2, 'Musterwerk') }),
      group('g0', [meter(3, { name: 'Zähler ohne' })], { name: 'Ohne' }),
    ])
  const selectedTab = (w: VueWrapper) => w.get('[role="tab"][aria-selected="true"]').text()

  it('meters: the tab of the site, else the first tab', async () => {
    storePrefs({ site: 2 })
    const w = await mountView(RegisterView, { kind: 'meters' }, vi.fn(async () => meters()))
    expect(selectedTab(w)).toContain('Musterwerk')
    // the user's choice stays after a reload
    await w.findAll('[role="tab"]')[0].trigger('click')
    expect(selectedTab(w)).toContain('Lager Nord')
  })

  it('meters: a site above the tabs picks the first tab below it; Alle keeps the first tab', async () => {
    storePrefs({ site: 1 })
    useNavigationStore().data = navigation()
    const w = await mountView(RegisterView, { kind: 'meters' }, vi.fn(async () => meters()))
    expect(selectedTab(w)).toContain('Musterwerk')
    w.unmount()
    mounted.pop()
    setActivePinia(createPinia())
    storePrefs({ site: 'all' as never })
    const w2 = await mountView(RegisterView, { kind: 'meters' }, vi.fn(async () => meters()))
    expect(selectedTab(w2)).toContain('Lager Nord')
  })

  it('action plans: the plan of the site', async () => {
    storePrefs({ site: 2 })
    const plans = [actionPlan(1, [action(1)], { name: 'Plan Lager', site: site(4, 'Lager Nord') }), actionPlan(2, [action(2)], { name: 'Plan Werk', site: site(2, 'Musterwerk') })]
    const get = vi.fn(async () => ({ today: '2026-11-06', plans }))
    const w = await mountView(ActionPlansView, {}, get)
    expect((w.get('#actions-plan').element as HTMLSelectElement).value).toBe('2')
  })

  it('action plans: without a chosen site all plans', async () => {
    const plans = [actionPlan(1, [action(1)], { site: site(4, 'Lager Nord') }), actionPlan(2, [action(2)], { site: site(2, 'Musterwerk') })]
    const w = await mountView(ActionPlansView, {}, vi.fn(async () => ({ today: '2026-11-06', plans })))
    expect((w.get('#actions-plan').element as HTMLSelectElement).value).toBe('all')
  })
})
