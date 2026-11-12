/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AnalysisConfig, AnalysisDefinition } from '../src/api/analysis'
import { emptyDefinition } from '../src/analysis/editor'
import { PREVIEW_DELAY } from '../src/analysis/useAnalysisEditing'
import { buildChartViews } from '../src/analysis/model'
import NewAnalysisButton from '../src/components/analysis/edit/NewAnalysisButton.vue'
import DataTable from '../src/components/analysis/DataTable.vue'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import { useNavigationStore } from '../src/stores/navigation'
import AnalysisView from '../src/views/AnalysisView.vue'
import { at, chart, config as baseConfig, data } from './fixtures/analysis'
import { directories, editConfig, editRow, VERSION } from './fixtures/analysisEdit'
import { httpError } from './fixtures/edit'

/* Edit mode of analyses (E3) with a mocked API; neutral data ("Musterwerk"). */

// no canvas in jsdom: the chart component is replaced by a placeholder
vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

type Fn = ReturnType<typeof vi.fn>
const Stub = defineComponent({ render: () => h('div', { class: 'home' }) })
const App = defineComponent({ render: () => h(RouterView) })
const mounted: VueWrapper[] = []
const navigation = { user: { id: 1, name: 'Muster', sysAdmin: false }, sites: [], other: { analyses: [], dashboards: [] } }

const dataAnswer = data([
  {
    id: 0,
    rows: [
      { key: '0-2001-Value', status: 'OK', unit: 'kWh', samples: [[at(1), 2.5, null]], stats: { min: 2.5, max: 2.5, avg: 2.5, sum: 2.5, count: 1 } },
      { key: '0-2002-Value', status: 'NO_PERMISSION' },
    ],
  },
  { id: 1, rows: [{ key: '1-2001-Value', status: 'OK', samples: [[at(1), 2.5, null]] }] },
])

interface Api {
  get: Fn
  post: Fn
  put: Fn
  delete: Fn
}

function api(cfg: AnalysisConfig = editConfig(), dirs = directories): Api {
  const get = vi.fn(async (path: string) => {
    if (path === '/web/analyses/1') return cfg
    if (path === '/web/analyses/directories') return dirs
    if (path === '/web/navigation') return navigation
    throw new Error('GET ' + path)
  })
  const post = vi.fn(async (path: string) => {
    if (path === '/web/analyses/1/data') return dataAnswer
    throw new Error('POST ' + path)
  })
  return { get, post, put: vi.fn(), delete: vi.fn() }
}

async function mountView(http: Api, path = '/analyses/1'): Promise<{ w: VueWrapper; router: Router }> {
  Object.assign(useAuthStore().http, http)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/analyses/:id', name: 'analysis', component: AnalysisView, props: (r) => ({ id: Number(r.params.id) }) },
    ],
  })
  await router.push(path)
  const w = mount(App, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(w)
  await flushPromises()
  return { w, router }
}

const panel = (w: VueWrapper) => w.find('.ae-panel')
const dialog = () => new DOMWrapperShim(document.querySelector('[role="dialog"]'))

/** a tiny wrapper for teleported / body-level dialogs */
class DOMWrapperShim {
  constructor(readonly el: Element | null) {}
  exists() {
    return !!this.el
  }
  text() {
    return this.el?.textContent ?? ''
  }
  async click(selector: string) {
    const target = this.el?.querySelector<HTMLElement>(selector)
    if (!target) throw new Error('no ' + selector)
    target.click()
    await flushPromises()
  }
}

/** The input / select of a labelled field in the panel. */
function control(w: VueWrapper, label: string): DOMWrapper<HTMLInputElement> {
  const l = panel(w)
    .findAll('label')
    .find((x) => x.text().trim() === label)
  if (!l) throw new Error('no field ' + label)
  return panel(w).find<HTMLInputElement>('#' + l.attributes('for'))
}

async function startEditing(w: VueWrapper) {
  await w.find('.an-edit').trigger('click')
  await flushPromises()
  expect(panel(w).exists()).toBe(true)
}

async function tab(w: VueWrapper, name: 'general' | 'charts' | 'rows') {
  await panel(w).find('.ae-tab-' + name).trigger('click')
}

function lastBody<T>(fn: Fn): T {
  return fn.mock.calls[fn.mock.calls.length - 1][1] as T
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  vi.useRealTimers()
  while (mounted.length) mounted.pop()!.unmount()
  document.body.innerHTML = ''
})

describe('analysis view: edit mode (E3)', () => {
  it('"Bearbeiten" with write right opens the panel with all buttons', async () => {
    const { w } = await mountView(api())
    await startEditing(w)
    expect(panel(w).findAll('[role="tab"]').map((t) => t.text())).toEqual(['Allgemein', 'Diagramme', 'Datenreihen'])
    expect(panel(w).find('.ae-save').attributes('disabled')).toBeDefined()
    expect(panel(w).find('.ae-save-as').exists()).toBe(true)
    expect(panel(w).find('.ae-delete').exists()).toBe(true)
    expect(panel(w).text()).toContain('Keine Änderungen')
    expect(w.find('.an-edit').exists()).toBe(false)
  })

  it('without write right but with a directory: only "Speichern unter"; without both: no "Bearbeiten"', async () => {
    const { w } = await mountView(api(editConfig({ canWrite: false, canDelete: false })))
    await startEditing(w)
    expect(panel(w).find('.ae-save').exists()).toBe(false)
    expect(panel(w).find('.ae-delete').exists()).toBe(false)
    expect(panel(w).find('.ae-save-as').exists()).toBe(true)
    expect(panel(w).text()).toContain('Speichern unter')
    // no rename without write right
    expect(panel(w).findAll('label').some((l) => l.text() === 'Name')).toBe(false)

    setActivePinia(createPinia())
    const { w: w2 } = await mountView(api(editConfig({ canWrite: false }), [{ ...directories[0], canCreate: false }]))
    expect(w2.find('.an-edit').exists()).toBe(false)
  })

  it('saves the definition with version and new name; locked rows go back unchanged; the navigation reloads', async () => {
    const http = api()
    const saved = editConfig({ name: 'Analyse B', names: { de: 'Analyse B' }, chartsPerScreen: 3, version: 'v2' })
    http.put.mockResolvedValue(saved)
    const { w } = await mountView(http)
    await startEditing(w)
    await control(w, 'Name').setValue('Analyse B')
    await control(w, 'Anzahl Diagramme pro Bildschirm').setValue('3')
    expect(panel(w).text()).toContain('Ungespeicherte Änderungen')
    await panel(w).find('.ae-save').trigger('click')
    await flushPromises()

    expect(http.put).toHaveBeenCalledTimes(1)
    const [path, body] = http.put.mock.calls[0] as [string, { version: string; name: string; definition: AnalysisDefinition }]
    expect(path).toBe('/web/analyses/1?lang=de')
    expect(body.version).toBe(VERSION)
    expect(body.name).toBe('Analyse B')
    expect(body.definition.chartsPerScreen).toBe(3)
    expect(body.definition).not.toHaveProperty('version')
    expect(body.definition.charts[0].rows[1]).toEqual({
      objectId: 2002,
      attribute: 'Value',
      name: null,
      color: '#b3261e',
      axis: 1,
      chartType: 'DEFAULT',
      decimalDigits: 3,
      calculation: false,
      bubbleType: 'NONE',
    })
    expect(panel(w).exists()).toBe(false)
    expect(w.find('h1').text()).toBe('Analyse B')
    expect(w.find('.analysis-edit-result').text()).toContain('gespeichert')
    expect(http.get).toHaveBeenCalledWith('/web/navigation')
    // the data of the saved analysis are loaded again
    expect(http.post.mock.calls.filter((c) => c[0] === '/web/analyses/1/data')).toHaveLength(2)
  })

  it('without a rename the name is not sent', async () => {
    const http = api()
    http.put.mockResolvedValue(editConfig({ version: 'v2' }))
    const { w } = await mountView(http)
    await startEditing(w)
    await control(w, 'Anzahl horizontaler Kuchen').setValue('4')
    await panel(w).find('.ae-save').trigger('click')
    await flushPromises()
    expect(lastBody<Record<string, unknown>>(http.put)).not.toHaveProperty('name')
    expect(http.get).not.toHaveBeenCalledWith('/web/navigation')
  })

  it('client validation blocks saving and marks the field', async () => {
    const http = api()
    const { w } = await mountView(http)
    await startEditing(w)
    const field = control(w, 'Anzahl Diagramme pro Bildschirm')
    await field.setValue('0')
    expect(field.attributes('aria-invalid')).toBe('true')
    expect(document.getElementById(field.attributes('aria-describedby')!)?.textContent).toContain('1 bis 20')
    await panel(w).find('.ae-save').trigger('click')
    await flushPromises()
    expect(http.put).not.toHaveBeenCalled()
    expect(panel(w).find('.ae-error-box').text()).toContain('1 markierte Eingabe')
  })

  it('400 of the server: its messages are listed in the panel', async () => {
    const http = api()
    http.put.mockRejectedValue(httpError(400, { messages: ['Datenreihe 3001: keine Berechnung gefunden'] }))
    const { w } = await mountView(http)
    await startEditing(w)
    await control(w, 'Anzahl horizontaler Tabellen').setValue('2')
    await panel(w).find('.ae-save').trigger('click')
    await flushPromises()
    expect(panel(w).find('.ae-messages').text()).toContain('keine Berechnung gefunden')
    expect(panel(w).exists()).toBe(true)
  })

  it('409: reload takes over the current analysis and stays in edit mode', async () => {
    const http = api()
    const current = editConfig({ version: 'v2', names: { de: 'Analyse A (neu)' }, chartsPerScreen: 4 })
    http.put.mockRejectedValue(httpError(409, current))
    const { w } = await mountView(http)
    await startEditing(w)
    await control(w, 'Anzahl Diagramme pro Bildschirm').setValue('3')
    await panel(w).find('.ae-save').trigger('click')
    await flushPromises()
    expect(dialog().text()).toContain('inzwischen von jemand anderem gespeichert')
    await dialog().click('.an-conflict-reload')
    expect(w.find('h1').text()).toBe('Analyse A (neu)')
    expect(control(w, 'Anzahl Diagramme pro Bildschirm').element.value).toBe('4')
    expect(panel(w).text()).toContain('Keine Änderungen')
    expect(w.find('.analysis-edit-result').text()).toContain('neu geladen')
  })

  it('409: "Speichern unter" keeps the draft and opens the name dialog', async () => {
    const http = api()
    http.put.mockRejectedValue(httpError(409, editConfig({ version: 'v2' })))
    const { w } = await mountView(http)
    await startEditing(w)
    await control(w, 'Anzahl Diagramme pro Bildschirm').setValue('3')
    await panel(w).find('.ae-save').trigger('click')
    await flushPromises()
    await dialog().click('.an-conflict-save-as')
    expect(dialog().text()).toContain('Analyse speichern unter')
    expect((document.querySelector('[role="dialog"] input') as HTMLInputElement).value).toBe('Analyse A (Kopie)')
    expect(control(w, 'Anzahl Diagramme pro Bildschirm').element.value).toBe('3')
  })

  it('"Speichern unter" copies the draft into the chosen directory and opens the copy', async () => {
    const http = api()
    http.post.mockImplementation(async (path: string) => {
      if (path === '/web/analyses/1/data' || path === '/web/analyses/7/data') return dataAnswer
      if (path === '/web/analyses/1/copy?lang=de') return { ...editConfig(), id: 7 }
      throw new Error(path)
    })
    http.get.mockImplementation(async (path: string) => {
      if (path === '/web/analyses/1') return editConfig()
      if (path === '/web/analyses/7') return { ...editConfig(), id: 7, names: { de: 'Kopie' } }
      if (path === '/web/analyses/directories') return directories
      if (path === '/web/navigation') return navigation
      throw new Error(path)
    })
    const { w, router } = await mountView(http)
    await startEditing(w)
    await control(w, 'Anzahl Diagramme pro Bildschirm').setValue('3')
    await panel(w).find('.ae-save-as').trigger('click')
    const dirSelect = document.querySelector('[role="dialog"] select') as HTMLSelectElement
    // only the directories with create right
    expect([...dirSelect.options].map((o) => o.text)).toEqual(['Musterwerk / Analysen'])
    const name = document.querySelector('[role="dialog"] input') as HTMLInputElement
    name.value = 'Musterwerk Kopie'
    name.dispatchEvent(new Event('input'))
    ;(document.querySelector('[role="dialog"] form') as HTMLFormElement).dispatchEvent(new Event('submit'))
    await flushPromises()
    const call = http.post.mock.calls.find((c) => c[0] === '/web/analyses/1/copy?lang=de')!
    expect(call[1]).toMatchObject({ directoryId: 50, name: 'Musterwerk Kopie', definition: { chartsPerScreen: 3 } })
    expect(router.currentRoute.value.fullPath).toBe('/analyses/7')
    await flushPromises()
    expect(w.find('h1').text()).toBe('Kopie')
    expect(w.find('.analysis-edit-result').text()).toContain('Musterwerk Kopie')
    expect(http.get).toHaveBeenCalledWith('/web/navigation')
  })

  it('a taken name offers to overwrite that analysis (PUT with its version)', async () => {
    const http = api()
    http.post.mockImplementation(async (path: string) => {
      if (path.endsWith('/data')) return dataAnswer
      if (path === '/web/analyses/1/copy?lang=de') throw httpError(409, { nameTaken: true, id: 9 })
      throw new Error(path)
    })
    http.get.mockImplementation(async (path: string) => {
      if (path === '/web/analyses/1') return editConfig()
      if (path === '/web/analyses/9') return { ...editConfig(), id: 9, version: 'v9' }
      if (path === '/web/analyses/directories') return directories
      if (path === '/web/navigation') return navigation
      throw new Error(path)
    })
    http.put.mockResolvedValue({ ...editConfig(), id: 9, version: 'v10' })
    const { w, router } = await mountView(http)
    await startEditing(w)
    await panel(w).find('.ae-save-as').trigger('click')
    ;(document.querySelector('[role="dialog"] form') as HTMLFormElement).dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(dialog().text()).toContain('Analyse überschreiben')
    expect(dialog().text()).toContain('Analyse A (Kopie)')
    await dialog().click('.edit-submit')
    expect(http.put).toHaveBeenCalledWith('/web/analyses/9?lang=de', expect.objectContaining({ version: 'v9', definition: expect.any(Object) }))
    expect(router.currentRoute.value.fullPath).toBe('/analyses/9')
  })

  it('a taken name on rename also offers to overwrite', async () => {
    const http = api()
    http.put.mockRejectedValueOnce(httpError(409, { nameTaken: true, id: 9 }))
    const { w } = await mountView(http)
    await startEditing(w)
    await control(w, 'Name').setValue('Vorhanden')
    await panel(w).find('.ae-save').trigger('click')
    await flushPromises()
    expect(dialog().text()).toContain('„Vorhanden“ gibt es in diesem Verzeichnis bereits')
  })

  it('deletes after the question and leaves to the start page', async () => {
    const http = api()
    http.delete.mockResolvedValue(undefined)
    const { w, router } = await mountView(http)
    await startEditing(w)
    await panel(w).find('.ae-delete').trigger('click')
    expect(dialog().text()).toContain('wirklich löschen')
    await dialog().click('.edit-submit')
    expect(http.delete).toHaveBeenCalledWith(`/web/analyses/1?version=${encodeURIComponent(VERSION)}`)
    expect(router.currentRoute.value.name).toBe('home')
    expect(http.get).toHaveBeenCalledWith('/web/navigation')
  })

  it('asks before unsaved changes are dropped (button, route change, closing the page)', async () => {
    const { w, router } = await mountView(api())
    await startEditing(w)
    // nothing changed: no question
    await panel(w).find('.ae-discard').trigger('click')
    await flushPromises()
    expect(panel(w).exists()).toBe(false)

    await startEditing(w)
    await control(w, 'Anzahl Diagramme pro Bildschirm').setValue('3')
    await panel(w).find('.ae-discard').trigger('click')
    expect(dialog().text()).toContain('Ungespeicherte Änderungen')
    await dialog().click('.edit-btn')
    expect(panel(w).exists()).toBe(true)

    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)

    void router.push('/')
    await flushPromises()
    expect(dialog().text()).toContain('nicht gespeichert')
    await dialog().click('.edit-btn')
    expect(router.currentRoute.value.fullPath).toBe('/analyses/1')

    void router.push('/')
    await flushPromises()
    await dialog().click('.edit-submit')
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('rows the user may not read are shown as "Keine Berechtigung" and cannot be edited', async () => {
    const { w } = await mountView(api())
    await startEditing(w)
    await tab(w, 'rows')
    const cards = panel(w).findAll('.ae-row')
    expect(cards).toHaveLength(2)
    expect(cards[1].classes()).toContain('is-locked')
    expect(cards[1].text()).toContain('Keine Berechtigung')
    expect(cards[1].findAll('input, select, button')).toHaveLength(0)
    await cards[0].find('.ae-card-toggle').trigger('click')
    expect(cards[0].find('input[type="text"]').exists()).toBe(true)
  })

  it('a time frame chosen while editing is previewed; leaving edit mode loads the saved data for it', async () => {
    const http = api()
    http.post.mockImplementation(async (path: string) => {
      if (path === '/web/analyses/1/data' || path === '/web/analyses/preview/data') return dataAnswer
      throw new Error(path)
    })
    const { w, router } = await mountView(http)
    await startEditing(w)
    await router.replace({ query: { tf: 'LAST_MONTH' } })
    await new Promise((r) => setTimeout(r, PREVIEW_DELAY + 50))
    await flushPromises()
    expect(panel(w).exists()).toBe(true)
    const dataCalls = () => http.post.mock.calls.filter((c) => c[0] === '/web/analyses/1/data')
    expect(dataCalls()).toHaveLength(1)
    expect(http.post.mock.calls.some((c) => c[0] === '/web/analyses/preview/data' && (c[1] as { timeFrame: string }).timeFrame === 'LAST_MONTH')).toBe(true)
    await panel(w).find('.ae-discard').trigger('click')
    await flushPromises()
    expect(dataCalls()).toHaveLength(2)
    expect((dataCalls()[1][1] as { timeFrame: string }).timeFrame).toBe('LAST_MONTH')
  })

  it('opens in edit mode with ?edit=1 (new analysis) and removes the parameter', async () => {
    const { w, router } = await mountView(api(), '/analyses/1?edit=1')
    expect(panel(w).exists()).toBe(true)
    expect(router.currentRoute.value.query.edit).toBeUndefined()
  })
})

describe('analysis view: live preview (E3)', () => {
  it('debounces, sends the draft with the toolbar settings, aborts an older request and skips display-only changes', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const http = api()
    const signals: AbortSignal[] = []
    const preview = data([
      { id: 0, rows: [{ key: '0-2001-Value', status: 'OK', unit: 'MWh', samples: [[at(1), 7.25, null]], stats: { min: 7.25, max: 7.25, avg: 7.25, sum: 7.25, count: 1 } }] },
    ])
    let first = true
    http.post.mockImplementation(async (path: string, _body: unknown, signal?: AbortSignal) => {
      if (path === '/web/analyses/1/data') return dataAnswer
      if (path === '/web/analyses/preview/data') {
        signals.push(signal!)
        if (first) {
          first = false
          return new Promise(() => undefined)
        }
        return preview
      }
      throw new Error(path)
    })
    const { w } = await mountView(http, '/analyses/1?tf=LAST_MONTH')
    await startEditing(w)
    const previews = () => http.post.mock.calls.filter((c) => c[0] === '/web/analyses/preview/data')

    const interval = control(w, 'Zeitliche Auflösung')
    await interval.setValue('P1D')
    vi.advanceTimersByTime(PREVIEW_DELAY / 2)
    await interval.setValue('PT1H')
    vi.advanceTimersByTime(PREVIEW_DELAY - 1)
    expect(previews()).toHaveLength(0)
    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(previews()).toHaveLength(1)
    const body = previews()[0][1] as { definition: AnalysisDefinition; timeFrame: string; timeZone: string; id: number }
    expect(body.definition.forcedInterval).toBe('PT1H')
    expect(body.id).toBe(1)
    expect(body.timeFrame).toBe('LAST_MONTH')
    expect(typeof body.timeZone).toBe('string')
    expect(panel(w).find('.ae-preview').text()).toContain('wird aktualisiert')

    await interval.setValue('P1W')
    vi.advanceTimersByTime(PREVIEW_DELAY)
    await flushPromises()
    expect(previews()).toHaveLength(2)
    expect(signals[0].aborted).toBe(true)
    expect(w.find('table.summary').text()).toContain('7,25')

    // names, colours and layout are drawn at once, without a request
    await control(w, 'Anzahl Diagramme pro Bildschirm').setValue('3')
    vi.advanceTimersByTime(PREVIEW_DELAY * 2)
    await flushPromises()
    expect(previews()).toHaveLength(2)
  })
})

describe('analysis view: rows (E3)', () => {
  it('adds rows from the picker with their own unit and saves them', async () => {
    const http = api()
    http.get.mockImplementation(async (path: string) => {
      if (path === '/web/analyses/1') return editConfig()
      if (path === '/web/analyses/directories') return directories
      if (path === '/web/navigation') return navigation
      if (path === '/web/rows/tree?lang=de') return [{ id: 10, name: 'Musterwerk', class: 'Organization', hasChildren: true, selectable: false }]
      if (path === '/web/rows/tree?parent=10&lang=de')
        return [
          { id: 2001, name: 'Musterwerk Strom', class: 'Clean Data', hasChildren: false, selectable: true, unit: 'kWh', attributes: ['Value'] },
          { id: 3001, name: 'Musterwerk Gas', class: 'Data', hasChildren: false, selectable: true, unit: 'm³', attributes: ['Value'] },
        ]
      if (path === '/web/rows/3001/units?attribute=Value') return [{ prefix: 'NONE', formula: 'm³', label: 'm³' }, { prefix: 'KILO', formula: 'm³', label: 'km³' }]
      throw new Error(path)
    })
    http.put.mockResolvedValue(editConfig({ version: 'v2' }))
    const { w } = await mountView(http)
    await startEditing(w)
    await tab(w, 'rows')
    await panel(w).find('.ae-add-rows').trigger('click')
    await flushPromises()
    expect(dialog().text()).toContain('Musterwerk')
    await dialog().click('.rp-expand')
    const boxes = [...document.querySelectorAll<HTMLInputElement>('[role="dialog"] input[type="checkbox"]')]
    // the row already in the chart is disabled
    expect(boxes[0].disabled).toBe(true)
    expect(dialog().text()).toContain('bereits im Diagramm')
    boxes[1].click()
    await flushPromises()
    await dialog().click('.rp-submit')
    expect(dialog().exists()).toBe(false)
    const cards = panel(w).findAll('.ae-row')
    expect(cards).toHaveLength(3)
    expect(cards[2].text()).toContain('Musterwerk Gas')

    await panel(w).find('.ae-save').trigger('click')
    await flushPromises()
    const def = lastBody<{ definition: AnalysisDefinition }>(http.put).definition
    expect(def.charts[0].rows[2]).toMatchObject({ objectId: 3001, attribute: 'Value', name: 'Musterwerk Gas', unitPrefix: 'NONE', unitFormula: 'm³', unitLabel: 'm³' })
  })

  it('searches rows by name from two characters', async () => {
    const http = api()
    http.get.mockImplementation(async (path: string) => {
      if (path === '/web/analyses/1') return editConfig()
      if (path === '/web/analyses/directories') return directories
      if (path === '/web/rows/tree?lang=de') return []
      if (path === '/web/rows/tree?q=Gas&lang=de') return [{ id: 3001, name: 'Musterwerk Gas', class: 'Data', hasChildren: false, selectable: true, path: 'Musterwerk / Zähler' }]
      throw new Error(path)
    })
    const { w } = await mountView(http)
    await startEditing(w)
    await tab(w, 'rows')
    await panel(w).find('.ae-add-rows').trigger('click')
    await flushPromises()
    const q = document.querySelector('[role="dialog"] input[type="search"]') as HTMLInputElement
    q.value = 'G'
    q.dispatchEvent(new Event('input'))
    await new Promise((r) => setTimeout(r, 350))
    expect(http.get).not.toHaveBeenCalledWith('/web/rows/tree?q=G&lang=de')
    q.value = 'Gas'
    q.dispatchEvent(new Event('input'))
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()
    expect(dialog().text()).toContain('Musterwerk / Zähler')
  })

  it('"für alle übernehmen" of the colour changes every readable row of the chart', async () => {
    const cfg = editConfig()
    cfg.charts[0].rows.push(editRow('0-2003-Value', 2003, { name: 'Musterwerk Wärme', color: '#2e7d32' }))
    const http = api(cfg)
    http.put.mockResolvedValue(editConfig({ version: 'v2' }))
    const { w } = await mountView(http)
    await startEditing(w)
    await tab(w, 'rows')
    await panel(w).findAll('.ae-row')[0].find('.ae-card-toggle').trigger('click')
    const color = control(w, 'Farbe')
    await color.setValue('#123456')
    await panel(w).find('.ae-color-all').trigger('click')
    await panel(w).find('.ae-save').trigger('click')
    await flushPromises()
    const rows = lastBody<{ definition: AnalysisDefinition }>(http.put).definition.charts[0].rows
    expect(rows.map((r) => r.color)).toEqual(['#123456', '#b3261e', '#123456'])
  })
})

describe('"Neue Analyse" in the navigation (E3)', () => {
  async function mountButton(post: Fn) {
    const get = vi.fn(async (path: string) => {
      if (path === '/web/analyses/directories') return directories
      if (path === '/web/navigation') return navigation
      throw new Error(path)
    })
    Object.assign(useAuthStore().http, { get, post })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: Stub },
        { path: '/analyses/:id', name: 'analysis', component: Stub },
      ],
    })
    await router.push('/')
    const w = mount(NewAnalysisButton, { global: { plugins: [router, i18n] }, attachTo: document.body })
    mounted.push(w)
    await flushPromises()
    return { w, router, get }
  }

  async function submit(name: string) {
    const input = document.querySelector('[role="dialog"] input') as HTMLInputElement
    input.value = name
    input.dispatchEvent(new Event('input'))
    ;(document.querySelector('[role="dialog"] form') as HTMLFormElement).dispatchEvent(new Event('submit'))
    await flushPromises()
  }

  it('creates an empty analysis in the chosen directory and opens it in edit mode', async () => {
    const post = vi.fn(async () => ({ ...editConfig(), id: 5 }))
    const { w, router, get } = await mountButton(post)
    expect(useNavigationStore().creatableDirectories).toHaveLength(1)
    await w.find('.new-analysis').trigger('click')
    expect(dialog().text()).toContain('Neue Analyse')
    await submit('')
    expect(dialog().text()).toContain('Bitte ausfüllen')
    expect(post).not.toHaveBeenCalled()
    await submit('Musterwerk Neu')
    expect(post).toHaveBeenCalledWith('/web/analyses?lang=de', { directoryId: 50, name: 'Musterwerk Neu', definition: emptyDefinition('Diagramm 1') })
    expect(router.currentRoute.value.fullPath).toBe('/analyses/5?edit=1')
    expect(get).toHaveBeenCalledWith('/web/navigation')
  })

  it('a taken name links to the existing analysis', async () => {
    const post = vi.fn().mockRejectedValue(httpError(409, { nameTaken: true, id: 9 }))
    const { w, router } = await mountButton(post)
    await w.find('.new-analysis').trigger('click')
    await submit('Vorhanden')
    expect(dialog().text()).toContain('bereits eine Analyse mit diesem Namen')
    await dialog().click('.and-open')
    expect(router.currentRoute.value.fullPath).toBe('/analyses/9')
  })

  it('is hidden without a directory with create right', async () => {
    const get = vi.fn(async () => [{ ...directories[0], canCreate: false }])
    Object.assign(useAuthStore().http, { get })
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Stub }] })
    await router.push('/')
    const w = mount(NewAnalysisButton, { global: { plugins: [router, i18n] } })
    mounted.push(w)
    await flushPromises()
    expect(w.find('.new-analysis').exists()).toBe(false)
  })
})

describe('table charts: options of the Control Center (E3)', () => {
  const ctx = { locale: 'de', labels: { noData: 'Keine Daten', noPermission: 'Keine Berechtigung', error: 'Fehler', count: 'Anzahl' } }

  function tableView(overrides: Record<string, unknown>) {
    const cfg = baseConfig([
      chart(0, 'TABLE_V', [editRow('a', 1, { name: 'Strom' }), editRow('b', 2, { name: 'Gas' })], { minFractionDigits: 0, maxFractionDigits: 1, ...overrides }),
    ])
    const answer = data([
      {
        id: 0,
        rows: [
          { key: 'a', status: 'OK', samples: [[at(1), 10, null], [at(2), 20, null]], stats: { min: 10, max: 20, avg: 15, sum: 30, count: 2 } },
          { key: 'b', status: 'OK', samples: [[at(1), 1, null], [at(2), 3, null]], stats: { min: 1, max: 3, avg: 2, sum: null, count: 2 } },
        ],
      },
    ])
    return buildChartViews(cfg, answer, '-')[0]
  }

  it('row sums, column sums and colouring', () => {
    const w = mount(DataTable, { props: { chart: tableView({ showRowSums: true, showColumnSums: true, coloringEnabled: true }), ctx }, global: { plugins: [i18n] } })
    mounted.push(w)
    const head = w.findAll('thead th').map((th) => th.text())
    expect(head[head.length - 1]).toBe('Summe [kWh]')
    // sum over the rows per timestamp
    expect(w.findAll('tbody tr')[0].findAll('td').map((td) => td.text())).toEqual(['10', '1', '11'])
    // quantities summed, others averaged; the corner is the sum of both
    expect(w.findAll('tfoot th, tfoot td').map((c) => c.text())).toEqual(['Summe', '30', '2', '32'])
    const coloured = w.findAll('tbody td').filter((td) => td.attributes('style')?.includes('background'))
    expect(coloured).toHaveLength(4)
  })

  it('filter fields: stored texts apply, typing emits the change', async () => {
    const w = mount(DataTable, { props: { chart: tableView({ filterEnabled: true, filterTexts: { Strom: '20' } }), ctx }, global: { plugins: [i18n] } })
    mounted.push(w)
    expect(w.findAll('tbody tr')).toHaveLength(1)
    expect(w.text()).toContain('1 von 2 Zeitpunkten')
    const inputs = w.findAll('input.col-filter')
    expect((inputs[0].element as HTMLInputElement).value).toBe('20')
    await inputs[0].setValue('')
    expect(w.findAll('tbody tr')).toHaveLength(2)
    expect(w.emitted('filter')?.[0]).toEqual(['Strom', ''])
  })

  it('without the options the table is unchanged', () => {
    const w = mount(DataTable, { props: { chart: tableView({}), ctx }, global: { plugins: [i18n] } })
    mounted.push(w)
    expect(w.find('tfoot').exists()).toBe(false)
    expect(w.find('input.col-filter').exists()).toBe(false)
    expect(w.findAll('tbody td').some((td) => td.attributes('style'))).toBe(false)
  })
})
