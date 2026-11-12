/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DashboardConfig } from '../src/api/dashboard'
import type { RawDashboard, RawWidget } from '../src/dashboard/edit/raw'
import { PREVIEW_DELAY } from '../src/dashboard/edit/useDashboardEditing'
import NewDashboardButton from '../src/components/dashboard/edit/NewDashboardButton.vue'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import DashboardView from '../src/views/DashboardView.vue'
import { dashboard, ddata, drow, widget } from './fixtures/dashboard'
import { rawRow, rawSource, rawWidget, SOURCE_VERSION, withRows } from './fixtures/dashboardEdit'
import { httpError } from './fixtures/edit'

/* Edit mode of dashboards (E4) and the input widgets, with a mocked API; neutral data ("Musterwerk"). */

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))
// the type settings are another module (tested on their own): a stub that shows its props
vi.mock('../src/dashboard/edit/typeEditors', async (original) => {
  const real = await original<typeof import('../src/dashboard/edit/typeEditors')>()
  const { defineComponent: dc, h: hh } = await import('vue')
  const Stub = dc({
    props: ['widget', 'source', 'dashboardId'],
    setup: (p: { widget: RawWidget; source: RawDashboard; dashboardId: number | null }) => () =>
      hh('button', { class: 'type-stub', type: 'button', onClick: () => (p.widget.dimIncompletePeriod = true) }, `type ${p.widget.uuid} ${p.source.Widget.length} ${p.dashboardId}`),
  })
  return { ...real, typeEditorFor: (type: string) => (type === 'Value' ? Stub : null) }
})

type Fn = ReturnType<typeof vi.fn>
const Stub = defineComponent({ render: () => h('div', { class: 'home' }) })
const App = defineComponent({ render: () => h(RouterView) })
const mounted: VueWrapper[] = []
const navigation = { user: { id: 1, name: 'Muster', sysAdmin: false }, sites: [], other: { analyses: [], dashboards: [] } }
const directories = [{ id: 50, name: 'Dashboards', path: 'Musterwerk / Dashboards', canCreate: true }]

function config(overrides: Partial<DashboardConfig> = {}, id = 7): DashboardConfig {
  return dashboard(
    [
      widget('1', 'Value', { rows: [drow('0-2001-Value', { objectId: 2001 })] }),
      widget('2', 'Title', { x: 300, y: 20 }),
      widget('3', 'Toggle Switch', { x: 10, y: 200, rows: [drow('0-2002-Value', { objectId: 2002 })], canEnter: true }),
    ],
    { id, version: SOURCE_VERSION, canWrite: true, canDelete: true, directoryId: 50, ...overrides },
  )
}

function source(): RawDashboard {
  return rawSource([withRows(rawWidget(1, 'Value'), [rawRow(2001)]), rawWidget(2, 'Title', { xPos: 300, yPos: 20 }), withRows(rawWidget(3, 'Toggle Switch', { xPos: 10, yPos: 200 }), [rawRow(2002)])])
}

interface Api {
  get: Fn
  post: Fn
  put: Fn
  delete: Fn
  putForm: Fn
  getBlob: Fn
}

function api(cfg = config()): Api {
  const get = vi.fn(async (path: string) => {
    if (path === '/web/dashboards/7') return cfg
    if (path === '/web/dashboards/7/source') return { version: SOURCE_VERSION, source: source() }
    if (path === '/web/dashboards/directories') return directories
    if (path === '/web/navigation') return navigation
    throw new Error('GET ' + path)
  })
  const post = vi.fn(async (path: string) => {
    if (path === '/web/dashboards/7/data') return ddata({ '1': { rows: [{ key: '0-2001-Value', status: 'OK', unit: 'kWh', value: 5 }] }, '3': { rows: [{ key: '0-2002-Value', status: 'OK', value: 1 }] } })
    if (path === '/web/dashboards/preview') return { dashboard: cfg, data: ddata({}) }
    throw new Error('POST ' + path)
  })
  return { get, post, put: vi.fn(), delete: vi.fn(), putForm: vi.fn(async () => undefined), getBlob: vi.fn(async () => new Blob()) }
}

async function mountView(http: Api, path = '/dashboards/7'): Promise<{ w: VueWrapper; router: Router }> {
  Object.assign(useAuthStore().http, http)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/dashboards/:id', name: 'dashboard', component: DashboardView, props: (r) => ({ id: Number(r.params.id) }) },
    ],
  })
  await router.push(path)
  const w = mount(App, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(w)
  await flushPromises()
  return { w, router }
}

const panel = (w: VueWrapper) => w.find('.dbe-panel')
const box = (w: VueWrapper, uuid: number) => w.find(`[data-edit-uuid="${uuid}"]`)
const dialog = () => document.querySelector('[role="dialog"]')
async function clickIn(selector: string) {
  const el = document.querySelector<HTMLElement>(`[role="dialog"] ${selector}`)
  if (!el) throw new Error('no ' + selector)
  el.click()
  await flushPromises()
}

function field(w: VueWrapper, label: string) {
  const l = panel(w)
    .findAll('label')
    .find((x) => x.text().trim() === label)
  if (!l) throw new Error('no field ' + label)
  return panel(w).find<HTMLInputElement>(`[id="${l.attributes('for')}"]`)
}

async function startEditing(w: VueWrapper) {
  await w.find('.db-edit').trigger('click')
  await flushPromises()
  expect(panel(w).exists()).toBe(true)
}

async function pick(w: VueWrapper, uuid: number, options: Record<string, unknown> = {}) {
  await pointer(box(w, uuid), 'pointerdown', { pointerId: 1, clientX: 0, clientY: 0, ...options })
  await pointer(box(w, uuid), 'pointerup', { pointerId: 1, clientX: 0, clientY: 0, ...options })
}

/** jsdom has no PointerEvent: a MouseEvent with a pointerId */
async function pointer(target: { element: Element }, type: string, init: { pointerId: number; clientX?: number; clientY?: number; shiftKey?: boolean; ctrlKey?: boolean; altKey?: boolean }) {
  const e = new MouseEvent(type, { bubbles: true, cancelable: true, button: 0, clientX: init.clientX ?? 0, clientY: init.clientY ?? 0, shiftKey: !!init.shiftKey, ctrlKey: !!init.ctrlKey, altKey: !!init.altKey })
  Object.defineProperty(e, 'pointerId', { value: init.pointerId })
  target.element.dispatchEvent(e)
  await flushPromises()
}

const lastBody = <T>(fn: Fn): T => fn.mock.calls[fn.mock.calls.length - 1][1] as T
const widgetOf = (s: RawDashboard, uuid: number) => s.Widget.find((x) => x.uuid === uuid)!

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1280 })
})
afterEach(() => {
  vi.useRealTimers()
  while (mounted.length) mounted.pop()!.unmount()
  document.body.innerHTML = ''
})

describe('dashboard view: edit mode (E4)', () => {
  it('"Bearbeiten" loads the source; selecting a widget and changing its title saves the whole source', async () => {
    const http = api()
    http.put.mockResolvedValue(config({ version: 'v2' }))
    const { w } = await mountView(http)
    await startEditing(w)
    expect(http.get).toHaveBeenCalledWith('/web/dashboards/7/source')
    expect(panel(w).findAll('[role="tab"]').map((t) => t.text())).toEqual(['Dashboard', 'Widget', 'Daten'])
    expect(panel(w).find('.dbe-save').attributes('disabled')).toBeDefined()
    expect(w.findAll('.dbe-box')).toHaveLength(3)

    await pick(w, 2)
    expect(box(w, 2).attributes('aria-selected')).toBe('true')
    expect(panel(w).find('.dbe-tab-widget').attributes('aria-selected')).toBe('true')
    await field(w, 'Titel').setValue('Musterwerk Übersicht')
    // drawn at once from the source
    expect(w.find('[data-uuid="2"]').text()).toBe('Musterwerk Übersicht')
    await field(w, 'Schriftgröße').setValue('20')
    expect(panel(w).find('.ae-state').text()).toBe('Ungespeicherte Änderungen')

    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    expect(String(http.put.mock.calls[0][0])).toBe('/web/dashboards/7?lang=de')
    const body = lastBody<{ version: string; source: RawDashboard; name?: string }>(http.put)
    expect(body.version).toBe(SOURCE_VERSION)
    expect(body.name).toBeUndefined()
    expect(widgetOf(body.source, 2)).toMatchObject({ title: 'Musterwerk Übersicht', fontSize: 20, 'Background Color': '0xffffffff' })
    // unknown keys survive
    expect(body.source['Unknown Key']).toEqual({ kept: true })
    expect(panel(w).exists()).toBe(false)
    expect(w.find('.db-edit-result').text()).toBe('Das Dashboard wurde gespeichert.')
  })

  it('keyboard: select, move by grid / 1 px, duplicate, delete, undo and redo', async () => {
    const http = api()
    http.put.mockResolvedValue(config())
    const { w } = await mountView(http)
    await startEditing(w)
    const b = box(w, 1)
    await b.trigger('keydown', { key: ' ' })
    expect(b.attributes('aria-selected')).toBe('true')
    await b.trigger('keydown', { key: 'ArrowRight' })
    expect(box(w, 1).attributes('style')).toContain('left: 25px')
    await b.trigger('keydown', { key: 'ArrowDown', shiftKey: true })
    expect(box(w, 1).attributes('style')).toContain('top: 21px')
    expect(box(w, 1).attributes('aria-label')).toContain('Position 25, 21')

    await b.trigger('keydown', { key: 'd', ctrlKey: true })
    expect(w.findAll('.dbe-box')).toHaveLength(4)
    expect(box(w, 4).attributes('aria-selected')).toBe('true')
    expect(box(w, 4).attributes('style')).toContain('left: 50px')

    await box(w, 4).trigger('keydown', { key: 'Delete' })
    expect(box(w, 4).exists()).toBe(false)
    const overlay = w.find('.dbe-overlay')
    await overlay.trigger('keydown', { key: 'z', ctrlKey: true })
    expect(box(w, 4).exists()).toBe(true)
    await overlay.trigger('keydown', { key: 'z', ctrlKey: true })
    expect(box(w, 4).exists()).toBe(false)
    await overlay.trigger('keydown', { key: 'y', ctrlKey: true })
    expect(box(w, 4).exists()).toBe(true)
    await overlay.trigger('keydown', { key: 'a', ctrlKey: true })
    expect(w.findAll('.dbe-box[aria-selected="true"]')).toHaveLength(4)
    await overlay.trigger('keydown', { key: 'Escape' })
    expect(w.findAll('.dbe-box[aria-selected="true"]')).toHaveLength(0)

    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    const saved = lastBody<{ source: RawDashboard }>(http.put).source
    expect(saved.Widget.map((x) => x.uuid)).toEqual([1, 2, 3, 4])
    expect(widgetOf(saved, 4)).toMatchObject({ title: 'Widget 1 #2', xPos: 50, yPos: 46 })
  })

  it('drags with the pointer (grid, Shift = quarter grid), resizes with a handle, layers and rubber band', async () => {
    const http = api()
    http.put.mockResolvedValue(config())
    const { w } = await mountView(http)
    await startEditing(w)
    const overlay = w.find('.dbe-overlay')
    await pointer(box(w, 1), 'pointerdown', { pointerId: 1, clientX: 100, clientY: 100 })
    await pointer(overlay, 'pointermove', { pointerId: 1, clientX: 130, clientY: 104 })
    expect(box(w, 1).attributes('style')).toContain('left: 50px')
    await pointer(overlay, 'pointermove', { pointerId: 1, clientX: 130, clientY: 104, shiftKey: true })
    expect(box(w, 1).attributes('style')).toContain('left: 37.5px')
    await pointer(overlay, 'pointerup', { pointerId: 1, clientX: 130, clientY: 104 })

    const handle = box(w, 1).find('[data-handle="se"]')
    await pointer(handle, 'pointerdown', { pointerId: 2, clientX: 0, clientY: 0 })
    await pointer(overlay, 'pointermove', { pointerId: 2, clientX: 40, clientY: 30 })
    await pointer(overlay, 'pointerup', { pointerId: 2, clientX: 40, clientY: 30 })
    expect(box(w, 1).attributes('style')).toMatch(/width: 2\d\d(\.\d+)?px/)

    // one undo step per gesture
    await overlay.trigger('keydown', { key: 'z', ctrlKey: true })
    expect(box(w, 1).attributes('style')).toContain('width: 200px')
    expect(box(w, 1).attributes('style')).toContain('left: 37.5px')

    await w.find('.dbe-layer-up').trigger('click')

    // rubber band over the first two widgets (Ctrl keeps nothing else selected before)
    await pointer(overlay, 'pointerdown', { pointerId: 3, clientX: 0, clientY: 0 })
    await pointer(overlay, 'pointermove', { pointerId: 3, clientX: 400, clientY: 60 })
    await pointer(overlay, 'pointerup', { pointerId: 3, clientX: 400, clientY: 60 })
    expect(w.findAll('.dbe-box[aria-selected="true"]').map((x) => x.attributes('data-edit-uuid'))).toEqual(['2', '1'])

    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    const saved = lastBody<{ source: RawDashboard }>(http.put).source
    expect(widgetOf(saved, 1)).toMatchObject({ xPos: 37.5, layer: 1, width: 200 })
  })

  it('several widgets: common fields show "verschieden", a change applies to all', async () => {
    const http = api()
    http.put.mockResolvedValue(config())
    const { w } = await mountView(http)
    await startEditing(w)
    await pick(w, 1)
    await pick(w, 2, { ctrlKey: true })
    await panel(w).find('.dbe-tab-widget').trigger('click')
    expect(panel(w).find('.dbe-selected').text()).toContain('2 Widgets')
    expect(field(w, 'X (Pixel)').attributes('placeholder')).toBe('verschieden')
    await field(w, 'Schriftgröße').setValue('18')
    const font = panel(w).findAll('input[type="text"]').find((x) => x.attributes('id')?.endsWith('-w-font'))!
    await font.setValue('#123456')
    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    const saved = lastBody<{ source: RawDashboard }>(http.put).source
    expect([widgetOf(saved, 1).fontSize, widgetOf(saved, 2).fontSize, widgetOf(saved, 3).fontSize]).toEqual([18, 18, 13])
    expect(widgetOf(saved, 2)['Font Color']).toBe('0x123456ff')
  })

  it('preview: debounced, with the time frame, aborts an older request; moving needs none', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const http = api()
    const signals: AbortSignal[] = []
    let first = true
    const base = http.post.getMockImplementation() as (path: string) => Promise<unknown>
    http.post.mockImplementation(async (path: string, _body: unknown, signal?: AbortSignal) => {
      if (path !== '/web/dashboards/preview') return base(path)
      signals.push(signal!)
      if (first) {
        first = false
        return new Promise(() => undefined)
      }
      return { dashboard: config({}, 7), data: ddata({ '1': { rows: [{ key: '0-2001-Value', status: 'OK', unit: 'kWh', value: 42.5 }] } }) }
    })
    const { w } = await mountView(http, '/dashboards/7?tf=P1W')
    await startEditing(w)
    const previews = () => http.post.mock.calls.filter((c) => c[0] === '/web/dashboards/preview')

    await box(w, 1).trigger('keydown', { key: ' ' })
    await box(w, 1).trigger('keydown', { key: 'ArrowRight' })
    vi.advanceTimersByTime(PREVIEW_DELAY * 2)
    await flushPromises()
    expect(previews()).toHaveLength(0)

    await panel(w).find('.dbe-tab-widget').trigger('click')
    await field(w, 'Titel').setValue('A')
    vi.advanceTimersByTime(PREVIEW_DELAY / 2)
    await field(w, 'Titel').setValue('AB')
    vi.advanceTimersByTime(PREVIEW_DELAY - 1)
    expect(previews()).toHaveLength(0)
    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(previews()).toHaveLength(1)
    const body = previews()[0][1] as { source: RawDashboard; id: number; timeFrame: string; timeZone: string }
    expect(body.id).toBe(7)
    expect(body.timeFrame).toBe('P1W')
    expect(widgetOf(body.source, 1)).toMatchObject({ title: 'AB', xPos: 25 })
    expect(panel(w).find('.ae-preview').text()).toContain('wird aktualisiert')

    await field(w, 'Titel').setValue('ABC')
    vi.advanceTimersByTime(PREVIEW_DELAY)
    await flushPromises()
    expect(previews()).toHaveLength(2)
    expect(signals[0].aborted).toBe(true)
    expect(w.find('[data-uuid="1"]').text()).toContain('42,50')
    // the canvas keeps the position of the source, not the one of the preview
    expect(w.find('[data-uuid="1"]').attributes('style')).toContain('left: 25px')
  })

  it('data tab: rows from the picker with aggregation / manipulation; type settings get widget, source and id', async () => {
    const http = api()
    const base = http.get.getMockImplementation() as (path: string) => Promise<unknown>
    http.get.mockImplementation(async (path: string) => {
      if (path === '/web/rows/tree?lang=de') return [{ id: 10, name: 'Musterwerk', class: 'Organization', hasChildren: true, selectable: false }]
      if (path === '/web/rows/tree?parent=10&lang=de') return [{ id: 3001, name: 'Musterwerk Gas', class: 'Data', hasChildren: false, selectable: true, unit: 'm³', attributes: ['Value'] }]
      if (path === '/web/rows/3001/units?attribute=Value') return [{ prefix: 'NONE', formula: 'm³', label: 'm³' }]
      if (path.startsWith('/web/rows/')) return []
      return base(path)
    })
    http.put.mockResolvedValue(config())
    const { w } = await mountView(http)
    await startEditing(w)
    await pick(w, 1)
    await panel(w).find('.dbe-tab-data').trigger('click')
    expect(panel(w).findAll('.dbe-row')).toHaveLength(1)
    expect(panel(w).find('.type-stub').text()).toBe('type 1 3 7')
    await panel(w).find('.type-stub').trigger('click')

    await panel(w).find('.dbe-add-rows').trigger('click')
    await flushPromises()
    await clickIn('.rp-expand')
    const boxes = [...document.querySelectorAll<HTMLInputElement>('[role="dialog"] input[type="checkbox"]')]
    boxes[0].click()
    await flushPromises()
    await clickIn('.rp-submit')
    expect(panel(w).findAll('.dbe-row')).toHaveLength(2)
    await panel(w).findAll('.dbe-row')[1].find('.ae-card-toggle').trigger('click')
    await panel(w).find('.dbe-aggregation').setValue('DAILY')
    await panel(w).find('.dbe-manipulation').setValue('SUM')

    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    const saved = widgetOf(lastBody<{ source: RawDashboard }>(http.put).source, 1)
    expect(saved.dimIncompletePeriod).toBe(true)
    expect(saved.dataHandler!.chartModels![0].chartData![1]).toMatchObject({ id: 3001, attributeString: 'Value', aggregationPeriod: 'DAILY', manipulationMode: 'SUM', unitLabel: 'm³' })
  })

  it('"Widget hinzufügen" adds a widget of the type in the visible area; the dashboard tab edits the source', async () => {
    const http = api()
    http.put.mockResolvedValue(config())
    const { w } = await mountView(http)
    await startEditing(w)
    await w.find('.dbe-add').trigger('click')
    expect(dialog()?.textContent).toContain('Widget hinzufügen')
    await clickIn('[data-type="Gauge"]')
    expect(box(w, 4).attributes('aria-selected')).toBe('true')
    expect(box(w, 4).attributes('style')).toContain('left: 25px')

    await panel(w).find('.dbe-tab-dashboard').trigger('click')
    await field(w, 'Raster horizontal (Pixel)').setValue('10')
    await field(w, 'Breite (Pixel)').setValue('1500')
    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    const saved = lastBody<{ source: RawDashboard }>(http.put).source
    expect(widgetOf(saved, 4)).toMatchObject({ WidgetType: 'Gauge', xPos: 25, yPos: 25 })
    expect(saved).toMatchObject({ 'X Axis Grid Interval': 10, width: 1500 })
  })

  it('renaming sends the name; client checks block saving', async () => {
    const http = api()
    http.put.mockResolvedValue(config({ name: 'Neu' }))
    const { w } = await mountView(http)
    await startEditing(w)
    await field(w, 'Name').setValue('')
    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    expect(http.put).not.toHaveBeenCalled()
    expect(panel(w).find('.ae-error-box').text()).toContain('markierte Eingabe')
    await field(w, 'Name').setValue('Musterwerk neu')
    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    expect(lastBody<{ name: string }>(http.put).name).toBe('Musterwerk neu')
    expect(http.get).toHaveBeenCalledWith('/web/navigation')
  })

  it('409 on save offers reload: the current state is loaded and editing goes on', async () => {
    const http = api()
    http.put.mockRejectedValue(httpError(409, JSON.stringify(config())))
    const { w } = await mountView(http)
    await startEditing(w)
    await pick(w, 2)
    await field(w, 'Titel').setValue('Geändert')
    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    expect(dialog()?.textContent).toContain('Dashboard wurde geändert')
    const sources = () => http.get.mock.calls.filter((c) => c[0] === '/web/dashboards/7/source').length
    expect(sources()).toBe(1)
    await clickIn('.db-conflict-reload')
    expect(sources()).toBe(2)
    expect(panel(w).exists()).toBe(true)
    expect(panel(w).find('.ae-state').text()).toBe('Keine Änderungen')
    expect(w.find('.db-edit-result').text()).toContain('neu geladen')
  })

  it('"Speichern unter" with a taken name overwrites that dashboard after asking', async () => {
    const http = api()
    const base = http.get.getMockImplementation() as (path: string) => Promise<unknown>
    http.get.mockImplementation(async (path: string) => (path === '/web/dashboards/9' ? config({ version: 'v9' }, 9) : base(path)))
    http.post.mockImplementation(async (path: string) => {
      if (path.startsWith('/web/dashboards/7/copy')) throw httpError(409, JSON.stringify({ nameTaken: true, id: 9 }))
      if (path === '/web/dashboards/7/data' || path === '/web/dashboards/9/data') return ddata({})
      throw new Error(path)
    })
    http.put.mockResolvedValue(config({}, 9))
    const { w, router } = await mountView(http)
    await startEditing(w)
    await panel(w).find('.dbe-save-as').trigger('click')
    const name = document.querySelector<HTMLInputElement>('[role="dialog"] input[type="text"]')!
    expect(name.value).toBe('Dashboard A (de) (Kopie)')
    await clickIn('button[type="submit"]')
    const copy = http.post.mock.calls.find((c) => String(c[0]).startsWith('/web/dashboards/7/copy'))!
    expect(copy[1]).toMatchObject({ directoryId: 50, name: 'Dashboard A (de) (Kopie)' })
    expect((copy[1] as { source: RawDashboard }).source.Widget).toHaveLength(3)
    expect(dialog()?.textContent).toContain('überschreiben')
    await clickIn('.edit-submit')
    expect(String(http.put.mock.calls[0][0])).toBe('/web/dashboards/9?lang=de')
    expect(lastBody<{ version: string }>(http.put).version).toBe('v9')
    expect(router.currentRoute.value.params.id).toBe('9')
  })

  it('"Löschen" asks, deletes with the version and goes home', async () => {
    const http = api()
    http.delete.mockResolvedValue(undefined)
    const { w, router } = await mountView(http)
    await startEditing(w)
    await panel(w).find('.dbe-delete').trigger('click')
    expect(dialog()?.textContent).toContain('Papierkorb')
    await clickIn('.edit-submit')
    expect(http.delete).toHaveBeenCalledWith(`/web/dashboards/7?version=${encodeURIComponent(SOURCE_VERSION)}`)
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('asks before unsaved changes are dropped', async () => {
    const { w, router } = await mountView(api())
    await startEditing(w)
    await box(w, 1).trigger('keydown', { key: 'Delete' })
    await box(w, 2).trigger('keydown', { key: ' ' })
    await box(w, 2).trigger('keydown', { key: 'Delete' })
    void router.push('/')
    await flushPromises()
    expect(dialog()?.textContent).toContain('Ungespeicherte Änderungen')
    await clickIn('.edit-btn')
    expect(router.currentRoute.value.name).toBe('dashboard')
    await panel(w).find('.dbe-discard').trigger('click')
    await flushPromises()
    await clickIn('.edit-submit')
    expect(panel(w).exists()).toBe(false)
  })

  it('uploads a background image and draws it with a new URL', async () => {
    const http = api()
    const { w } = await mountView(http)
    await startEditing(w)
    const input = panel(w).find<HTMLInputElement>('.dbe-file')
    const file = new File(['x'], 'bg.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    await flushPromises()
    expect(http.putForm).toHaveBeenCalledTimes(1)
    expect(http.putForm.mock.calls[0][0]).toBe('/web/dashboards/7/background')
    expect((http.putForm.mock.calls[0][1] as FormData).get('file')).toBeInstanceOf(File)
    expect(http.getBlob).toHaveBeenCalledWith('/web/dashboards/7/background?v=1')
    expect(panel(w).find('.dbe-upload-result').text()).toContain('gespeichert')
  })

  it('below tablet width editing is not offered, ?edit=1 is dropped', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 600 })
    const { w, router } = await mountView(api(), '/dashboards/7?edit=1')
    expect(w.find('.db-edit').exists()).toBe(false)
    expect(w.find('.db-edit-hint').text()).toContain('900')
    expect(panel(w).exists()).toBe(false)
    expect(router.currentRoute.value.query.edit).toBeUndefined()
  })

  it('without write right: only "Speichern unter"', async () => {
    const { w } = await mountView(api(config({ canWrite: false, canDelete: false })))
    await startEditing(w)
    expect(panel(w).find('.dbe-save').exists()).toBe(false)
    expect(panel(w).find('.dbe-delete').exists()).toBe(false)
    expect(panel(w).find('.dbe-save-as').exists()).toBe(true)
    expect(panel(w).find('.ae-readonly').exists()).toBe(true)
  })
})

describe('"Neues Dashboard" while another dashboard is open (E3 lesson)', () => {
  it('edits and saves the new dashboard, not the one that was open before', async () => {
    const http = api()
    const created = config({ name: 'Neues Musterdashboard', names: {} }, 8)
    created.widgets = []
    const base = http.get.getMockImplementation() as (path: string) => Promise<unknown>
    http.get.mockImplementation(async (path: string) => {
      if (path === '/web/dashboards/8') return created
      if (path === '/web/dashboards/8/source') return { version: 'v8', source: rawSource([]) }
      return base(path)
    })
    const basePost = http.post.getMockImplementation() as (path: string) => Promise<unknown>
    http.post.mockImplementation(async (path: string) => (path === '/web/dashboards/8/data' ? ddata({}) : basePost(path)))
    http.put.mockResolvedValue(created)
    const { w, router } = await mountView(http)
    expect(w.find('h1').text()).toBe('Dashboard A (de)')
    await router.push({ name: 'dashboard', params: { id: 8 }, query: { edit: '1' } })
    await flushPromises()
    expect(panel(w).exists()).toBe(true)
    expect(http.get).not.toHaveBeenCalledWith('/web/dashboards/7/source')
    expect(field(w, 'Name').element.value).toBe('Neues Musterdashboard')
    expect(w.findAll('.dbe-box')).toHaveLength(0)
    expect(router.currentRoute.value.query.edit).toBeUndefined()
    await field(w, 'Breite (Pixel)').setValue('1600')
    await panel(w).find('.dbe-save').trigger('click')
    await flushPromises()
    expect(http.put).toHaveBeenCalledTimes(1)
    expect(String(http.put.mock.calls[0][0])).toBe('/web/dashboards/8?lang=de')
    expect(lastBody<{ version: string }>(http.put).version).toBe('v8')
  })
})

describe('"Neues Dashboard" on the overview (E4)', () => {
  async function mountButton(post: Fn) {
    const get = vi.fn(async (path: string) => {
      if (path === '/web/dashboards/directories') return directories
      if (path === '/web/navigation') return navigation
      throw new Error(path)
    })
    Object.assign(useAuthStore().http, { get, post })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: Stub },
        { path: '/dashboards/:id', name: 'dashboard', component: Stub },
      ],
    })
    await router.push('/')
    const w = mount(NewDashboardButton, { global: { plugins: [router, i18n] }, attachTo: document.body })
    mounted.push(w)
    await flushPromises()
    return { w, router, get }
  }

  it('creates the dashboard and opens it in edit mode', async () => {
    const post = vi.fn(async () => ({ id: 12 }))
    const { w, router, get } = await mountButton(post)
    await w.find('.new-dashboard').trigger('click')
    expect(dialog()?.textContent).toContain('Neues Dashboard')
    const name = document.querySelector<HTMLInputElement>('[role="dialog"] input[type="text"]')!
    name.value = 'Musterwerk Strom'
    name.dispatchEvent(new Event('input'))
    await clickIn('button[type="submit"]')
    expect(post).toHaveBeenCalledWith('/web/dashboards?lang=de', { directoryId: 50, name: 'Musterwerk Strom' })
    expect(router.currentRoute.value.fullPath).toBe('/dashboards/12?edit=1')
    expect(get).toHaveBeenCalledWith('/web/navigation')
  })

  it('a taken name links to the existing dashboard', async () => {
    const post = vi.fn(async () => {
      throw httpError(409, JSON.stringify({ nameTaken: true, id: 4 }))
    })
    const { w, router } = await mountButton(post)
    await w.find('.new-dashboard').trigger('click')
    const name = document.querySelector<HTMLInputElement>('[role="dialog"] input[type="text"]')!
    name.value = 'Vorhanden'
    name.dispatchEvent(new Event('input'))
    await clickIn('button[type="submit"]')
    expect(dialog()?.textContent).toContain('bereits ein Dashboard')
    await clickIn('.and-open')
    expect(router.currentRoute.value.fullPath).toBe('/dashboards/4')
  })
})

describe('input widgets in the view (E4)', () => {
  function inputConfig(): DashboardConfig {
    return dashboard(
      [
        widget('3', 'Toggle Switch', { rows: [drow('0-2002-Value', { objectId: 2002 })], canEnter: true }),
        widget('4', 'PlusMinus', { rows: [drow('0-2003-Value', { objectId: 2003 })], canEnter: true, config: { increment: 1 } }),
        widget('5', 'Slider', { rows: [drow('0-2004-Value', { objectId: 2004 })], canEnter: true, decimals: 0, config: { minimum: 0, maximum: 50 } }),
        widget('6', 'Value Editor', { title: 'Zählerstand', rows: [drow('0-2005-Value', { objectId: 2005 })], canEnter: true }),
        widget('7', 'Toggle Switch', { rows: [drow('0-2006-Value', { objectId: 2006 })], canEnter: false }),
      ],
      { id: 7 },
    )
  }

  function inputApi() {
    const http = api(inputConfig())
    const values: Record<string, unknown>[] = []
    http.post.mockImplementation(async (path: string, body: Record<string, unknown>) => {
      if (path === '/web/dashboards/7/data')
        return ddata({
          '3': { rows: [{ key: '0-2002-Value', status: 'OK', value: 1 }] },
          '4': { rows: [{ key: '0-2003-Value', status: 'OK', value: 5 }] },
          '5': { rows: [{ key: '0-2004-Value', status: 'OK', value: 10 }] },
          '6': { rows: [{ key: '0-2005-Value', status: 'OK', value: 100 }] },
        })
      const m = /^\/web\/dashboards\/7\/widgets\/(\d+)\/value\?lang=de$/.exec(path)
      if (m) {
        values.push({ uuid: m[1], ...body })
        if (m[1] === '6' && !body.confirm) throw httpError(409, JSON.stringify({ warnings: ['Wert kleiner als der letzte Zählerstand'] }))
        const key = { '3': '0-2002-Value', '4': '0-2003-Value', '5': '0-2004-Value', '6': '0-2005-Value' }[m[1]]
        const value = m[1] === '3' ? (body.value ? 1 : 0) : m[1] === '4' ? 6 : Number(body.value)
        return { rows: [{ key, status: 'OK', value }] }
      }
      throw new Error(path)
    })
    return { http, values }
  }

  it('toggle, plus / minus and slider write their value and show the answer', async () => {
    const { http, values } = inputApi()
    const { w } = await mountView(http)
    const toggle = w.find('[data-uuid="3"] [role="switch"]')
    expect(toggle.attributes('aria-checked')).toBe('true')
    await toggle.trigger('click')
    await flushPromises()
    expect(values[0]).toMatchObject({ uuid: '3', value: false, rowKey: '0-2002-Value' })
    expect(w.find('[data-uuid="3"] [role="switch"]').attributes('aria-checked')).toBe('false')

    await w.find('[data-uuid="4"] .pm-plus').trigger('click')
    await flushPromises()
    expect(values[1]).toMatchObject({ uuid: '4', value: 'plus' })
    expect(w.find('[data-uuid="4"] output').text()).toBe('6,00 kWh')

    const slider = w.find<HTMLInputElement>('[data-uuid="5"] input[type="range"]')
    slider.element.value = '30'
    await slider.trigger('change')
    await flushPromises()
    expect(values[2]).toMatchObject({ uuid: '5', value: 30 })

    // without write right read-only, no request
    expect(w.find('[data-uuid="7"] input').attributes('disabled')).toBeDefined()
  })

  it('the Value Editor enters a value; plausibility warnings can be confirmed', async () => {
    const { http, values } = inputApi()
    const { w } = await mountView(http)
    await w.find('[data-uuid="6"] .field-btn').trigger('click')
    expect(dialog()?.textContent).toContain('Wert erfassen: Zählerstand')
    const input = document.querySelector<HTMLInputElement>('[role="dialog"] input[inputmode="decimal"]')!
    input.value = '95,5'
    input.dispatchEvent(new Event('input'))
    await clickIn('.ive-submit')
    expect(values[0]).toMatchObject({ uuid: '6', value: 95.5, confirm: false })
    expect(typeof values[0].timestamp).toBe('string')
    expect(dialog()?.textContent).toContain('kleiner als der letzte')
    await clickIn('.ive-confirm')
    expect(values[1]).toMatchObject({ value: 95.5, confirm: true })
    expect(dialog()).toBeNull()
    expect(w.find('[data-uuid="6"] .field-btn').text()).toBe('95,50 kWh')
  })

  it('a refused write shows the error and keeps the value', async () => {
    const { http } = inputApi()
    http.post.mockImplementation(async (path: string) => {
      if (path === '/web/dashboards/7/data') return ddata({ '3': { rows: [{ key: '0-2002-Value', status: 'OK', value: 1 }] } })
      throw httpError(403, 'no write permission')
    })
    const { w } = await mountView(http)
    await w.find('[data-uuid="3"] [role="switch"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-uuid="3"] .err').text()).toContain('no write permission')
    expect(w.find('[data-uuid="3"] [role="switch"]').attributes('aria-checked')).toBe('true')
  })

  it('input widgets stay read-only while the dashboard is edited', async () => {
    const http = api()
    const { w } = await mountView(http)
    expect(w.find('[data-uuid="3"] [role="switch"]').element.tagName).toBe('BUTTON')
    await startEditing(w)
    expect(w.find('[data-uuid="3"] button[role="switch"]').exists()).toBe(false)
  })
})
