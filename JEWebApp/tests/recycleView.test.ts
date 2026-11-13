/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AnalysisDirectory } from '../src/api/analysis'
import { deleteForever, isParentGone, loadRecycleBin, parseKind, restoreEntry, type RecycleEntry } from '../src/api/recycle'
import RecycleBinLink from '../src/components/recycle/RecycleBinLink.vue'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import RecycleBinView from '../src/views/RecycleBinView.vue'
import { httpError } from './fixtures/edit'

const Stub = defineComponent({ render: () => h('div') })
const App = defineComponent({ render: () => h(RouterView) })
const sp = (s: string) => s.replace(/\s+/g, ' ').trim()
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []

/** Neutral entries (no real data) */
function entry(overrides: Partial<RecycleEntry> = {}): RecycleEntry {
  return {
    id: 501,
    name: 'Übersicht Halle',
    kind: 'dashboards',
    deletedAt: '2026-11-12T10:30:00.000+01:00',
    deletedBy: 'max.muster',
    path: 'Beispiel GmbH / Dashboards',
    parentGone: false,
    canRestore: true,
    canDeleteForever: true,
    ...overrides,
  }
}

const directory = (id: number, path: string, canCreate = true): AnalysisDirectory => ({ id, name: path.split(' / ').pop()!, path, canCreate })

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/recycle', name: 'recycle', component: RecycleBinView },
      { path: '/dashboards', name: 'dashboards', component: Stub },
      { path: '/analyses', name: 'analyses', component: Stub },
      { path: '/dashboards/:id', name: 'dashboard', component: Stub },
      { path: '/analyses/:id', name: 'analysis', component: Stub },
    ],
  })
}

interface Server {
  bin: Record<string, RecycleEntry[] | Error>
  dirs?: unknown
  dashDirs?: unknown
}

async function mountView(server: Server, path = '/recycle?kind=dashboards', http: Partial<Record<'post' | 'delete', Fn>> = {}) {
  const get = vi.fn(async (p: string) => {
    if (p === '/web/navigation') return { sites: [], other: { analyses: [], dashboards: [] } }
    if (p === '/web/analyses/directories') return server.dirs ?? []
    if (p === '/web/dashboards/directories') return server.dashDirs ?? []
    const m = /^\/web\/recycle\?kind=(\w+)&lang=(\w+)$/.exec(p)
    if (m) {
      const answer = server.bin[m[1]]
      if (answer instanceof Error) throw answer
      return answer ?? []
    }
    throw new Error('GET ' + p)
  })
  const post = http.post ?? vi.fn()
  const del = http.delete ?? vi.fn(async () => undefined)
  Object.assign(useAuthStore().http, { get, post, delete: del })
  const router = makeRouter()
  await router.push(path)
  const wrapper = mount(App, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router, get, post, del }
}

const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]')
const button = (w: VueWrapper, text: string) => w.findAll('button').find((b) => sp(b.text()) === text)!
const dialogButton = (text: string) => Array.from(dialog()!.querySelectorAll('button')).find((b) => sp(b.textContent ?? '') === text)!
const navLoads = (get: Fn) => get.mock.calls.filter((c) => c[0] === '/web/navigation').length

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
})

/* ---------------------------------------------------------------- api */

describe('recycle api', () => {
  it('builds the requests and recognises a gone original place', async () => {
    const get = vi.fn(async () => [])
    const post = vi.fn(async () => ({}))
    const del = vi.fn(async () => undefined)
    await loadRecycleBin({ get } as never, 'analyses', 'de')
    expect(get).toHaveBeenCalledWith('/web/recycle?kind=analyses&lang=de')
    await restoreEntry({ post } as never, 7, 'en')
    expect(post).toHaveBeenLastCalledWith('/web/recycle/7/restore?lang=en', {})
    await restoreEntry({ post } as never, 7, 'de', 12)
    expect(post).toHaveBeenLastCalledWith('/web/recycle/7/restore?lang=de', { parent: 12 })
    await deleteForever({ delete: del } as never, 7)
    expect(del).toHaveBeenCalledWith('/web/recycle/7')

    expect(isParentGone(httpError(409, { parentGone: true }))).toBe(true)
    expect(isParentGone(httpError(409, 'other'))).toBe(false)
    expect(isParentGone(httpError(404, { parentGone: true }))).toBe(false)
    expect(parseKind('analyses')).toBe('analyses')
    expect(parseKind(['analyses'])).toBe('analyses')
    expect(parseKind('rows')).toBe('dashboards')
    expect(parseKind(undefined)).toBe('dashboards')
  })
})

/* ---------------------------------------------------------------- list */

describe('RecycleBinView list', () => {
  it('shows name, original place, deleted at / by and the actions', async () => {
    const { wrapper } = await mountView({
      bin: {
        dashboards: [
          entry(),
          entry({ id: 502, name: 'Alt', parentGone: true, path: null, deletedBy: null, deletedAt: '2026-11-01' }),
          entry({ id: 503, name: 'Fremd', path: null, canRestore: false, canDeleteForever: false }),
        ],
      },
    })
    expect(wrapper.find('h1').text()).toBe('Papierkorb')
    const items = wrapper.findAll('.rb-item')
    expect(items).toHaveLength(3)
    const first = sp(items[0].text())
    expect(first).toContain('Übersicht Halle')
    expect(first).toContain('Ursprünglicher Ort: Beispiel GmbH / Dashboards')
    expect(first).toMatch(/Gelöscht am .*2026.* von max\.muster/)
    expect(items[0].find('button').attributes('aria-label')).toBe('„Übersicht Halle“ wiederherstellen')
    expect(sp(items[1].text())).toContain('Ursprünglicher Ort existiert nicht mehr')
    expect(sp(items[1].text())).toContain('Wiederherstellen nach …')
    expect(sp(items[1].text())).not.toContain(' von ')
    expect(sp(items[2].text())).toContain('Ursprünglicher Ort nicht sichtbar')
    expect(items[2].findAll('button')).toHaveLength(0)
    expect(sp(items[2].text())).toContain('Keine Berechtigung')
    // switch: dashboards is the current page
    expect(wrapper.find('.rb-switch a[aria-current="page"]').text()).toBe('Dashboards')
  })

  it('switches the kind and shows an empty state', async () => {
    const { wrapper, router, get } = await mountView({ bin: { dashboards: [entry()], analyses: [] } })
    await router.push('/recycle?kind=analyses')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/recycle?kind=analyses&lang=de')
    expect(wrapper.find('.rb-empty').text()).toBe('Keine gelöschten Analysen im Papierkorb.')
    expect(wrapper.find('.rb-switch a[aria-current="page"]').text()).toBe('Analysen')
    expect(wrapper.find('.rb-back').text()).toBe('Zurück zu den Analysen')
  })

  it('shows a load error with retry', async () => {
    const server: Server = { bin: { dashboards: httpError(500) } }
    const { wrapper } = await mountView(server)
    expect(wrapper.find('[role="alert"]').text()).toContain('Der Papierkorb konnte nicht geladen werden.')
    server.bin.dashboards = [entry()]
    await button(wrapper, 'Erneut versuchen').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.rb-item')).toHaveLength(1)
  })
})

/* ---------------------------------------------------------------- restore */

describe('RecycleBinView restore', () => {
  it('restores to the original place, reloads list and navigation and links to the item', async () => {
    const server: Server = { bin: { dashboards: [entry()] } }
    const post = vi.fn(async () => {
      server.bin.dashboards = []
      return { id: 501, name: 'Übersicht Halle', kind: 'dashboards', parentId: 40, path: 'Beispiel GmbH / Dashboards' }
    })
    const { wrapper, get } = await mountView(server, undefined, { post })
    const before = navLoads(get)
    await button(wrapper, 'Wiederherstellen').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/recycle/501/restore?lang=de', {})
    expect(dialog()).toBeNull()
    expect(navLoads(get)).toBe(before + 1)
    expect(wrapper.findAll('.rb-item')).toHaveLength(0)
    const status = wrapper.find('.rb-done')
    expect(sp(status.text())).toContain('„Übersicht Halle“ wurde wiederhergestellt in Beispiel GmbH / Dashboards.')
    expect(status.find('a').attributes('href')).toBe('/dashboards/501')
  })

  it('asks for a target directory when the original place is gone', async () => {
    const server: Server = {
      bin: { analyses: [entry({ id: 601, name: 'Wärme', kind: 'analyses', parentGone: true, path: null })] },
      dirs: [directory(30, 'Beispiel GmbH / Analysen', true), directory(31, 'Beispiel GmbH / Archiv', false), directory(32, 'Beispiel GmbH / Andere', true)],
    }
    const post = vi.fn(async () => {
      server.bin.analyses = []
      return { id: 601, name: 'Wärme', kind: 'analyses', parentId: 32, path: 'Beispiel GmbH / Andere' }
    })
    const { wrapper, get } = await mountView(server, '/recycle?kind=analyses', { post })
    await button(wrapper, 'Wiederherstellen nach …').trigger('click')
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
    const d = dialog()!
    expect(sp(d.querySelector('h2')!.textContent!)).toBe('Ziel für die Wiederherstellung wählen')
    const options = Array.from(d.querySelectorAll('option')).map((o) => o.textContent)
    // only directories with create right, sorted
    expect(options).toEqual(['Beispiel GmbH / Analysen', 'Beispiel GmbH / Andere'])
    const select = d.querySelector('select')!
    select.value = '32'
    select.dispatchEvent(new Event('change'))
    await flushPromises()
    dialogButton('Wiederherstellen').click()
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/recycle/601/restore?lang=de', { parent: 32 })
    expect(dialog()).toBeNull()
    expect(navLoads(get)).toBeGreaterThan(0)
    expect(sp(wrapper.find('.rb-done').text())).toContain('wurde wiederhergestellt in Beispiel GmbH / Andere')
    expect(wrapper.find('.rb-done a').attributes('href')).toBe('/analyses/601')
  })

  it('opens the target dialog on 409 parentGone and says when there is no directory', async () => {
    const post = vi.fn(async () => {
      throw httpError(409, { parentGone: true })
    })
    const { wrapper } = await mountView({ bin: { dashboards: [entry()] }, dashDirs: [directory(40, 'Beispiel GmbH / Dashboards', false)] }, undefined, { post })
    await button(wrapper, 'Wiederherstellen').trigger('click')
    await flushPromises()
    expect(dialog()).not.toBeNull()
    expect(dialog()!.textContent).toContain('Es gibt kein Verzeichnis, in dem Sie anlegen dürfen.')
    expect(dialogButton('Wiederherstellen').disabled).toBe(true)
    dialogButton('Abbrechen').click()
    await flushPromises()
    expect(dialog()).toBeNull()
  })

  it('shows errors of the restore', async () => {
    const post = vi.fn(async () => {
      throw httpError(403, 'Not allowed to restore this object')
    })
    const { wrapper } = await mountView({ bin: { dashboards: [entry()] } }, undefined, { post })
    await button(wrapper, 'Wiederherstellen').trigger('click')
    await flushPromises()
    expect(wrapper.find('.alert-error').text()).toContain('Dafür fehlt Ihnen die Berechtigung.')
    expect(wrapper.findAll('.rb-item')).toHaveLength(1)
  })

  it('reloads the list when the entry is gone', async () => {
    const server: Server = { bin: { dashboards: [entry()] } }
    const post = vi.fn(async () => {
      server.bin.dashboards = []
      throw httpError(404, 'Not in the recycle bin')
    })
    const { wrapper } = await mountView(server, undefined, { post })
    await button(wrapper, 'Wiederherstellen').trigger('click')
    await flushPromises()
    expect(wrapper.find('.alert-error').text()).toContain('nicht mehr im Papierkorb')
    expect(wrapper.findAll('.rb-item')).toHaveLength(0)
  })
})

/* ---------------------------------------------------------------- delete forever */

describe('RecycleBinView delete forever', () => {
  it('asks in a red confirmation, deletes and reloads', async () => {
    const server: Server = { bin: { dashboards: [entry()] } }
    const del = vi.fn(async () => {
      server.bin.dashboards = []
    })
    const { wrapper, get } = await mountView(server, undefined, { delete: del })
    const before = navLoads(get)
    await button(wrapper, 'Endgültig löschen').trigger('click')
    await flushPromises()
    const d = dialog()!
    expect(d.textContent).toContain('„Übersicht Halle“ wird unwiderruflich gelöscht.')
    const confirm = dialogButton('Endgültig löschen')
    expect(confirm.classList.contains('is-danger')).toBe(true)
    expect(del).not.toHaveBeenCalled()
    confirm.click()
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/recycle/501')
    expect(dialog()).toBeNull()
    expect(navLoads(get)).toBe(before + 1)
    expect(wrapper.find('.rb-done').text()).toBe('„Übersicht Halle“ wurde endgültig gelöscht.')
    expect(wrapper.find('.rb-empty').exists()).toBe(true)
  })

  it('cancel does not delete, errors stay in the dialog', async () => {
    const del = vi.fn(async () => {
      throw httpError(403, '')
    })
    const { wrapper } = await mountView({ bin: { dashboards: [entry()] } }, undefined, { delete: del })
    await button(wrapper, 'Endgültig löschen').trigger('click')
    await flushPromises()
    dialogButton('Abbrechen').click()
    await flushPromises()
    expect(dialog()).toBeNull()
    expect(del).not.toHaveBeenCalled()

    await button(wrapper, 'Endgültig löschen').trigger('click')
    await flushPromises()
    dialogButton('Endgültig löschen').click()
    await flushPromises()
    expect(dialog()!.querySelector('[role="alert"]')!.textContent).toContain('Dafür fehlt Ihnen die Berechtigung.')
    expect(wrapper.findAll('.rb-item')).toHaveLength(1)
  })
})

/* ---------------------------------------------------------------- header link */

describe('RecycleBinLink', () => {
  it('links to the bin of the kind', async () => {
    const router = makeRouter()
    await router.push('/')
    const w = mount(RecycleBinLink, { props: { kind: 'analyses' }, global: { plugins: [router, i18n] } })
    mounted.push(w)
    expect(w.find('a').attributes('href')).toBe('/recycle?kind=analyses')
    expect(sp(w.text())).toBe('Papierkorb')
  })
})
