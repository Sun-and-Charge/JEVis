/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { moduleCounts } from '../src/api/navigation'
import {
  catalog,
  effectiveSite,
  favouriteEntries,
  filterCatalog,
  openedItemKey,
  preferredBySite,
  recentEntries,
  siteAndDescendants,
  siteChoices,
} from '../src/nav/catalog'
import { groupOfRoute, menuGroups, openGroupOfRoute, singleModules, visibleModules } from '../src/nav/menu'
import { isPaletteShortcut, isTyping, paletteKey, paletteSearch, type PaletteItem } from '../src/nav/palette'
import { emptyPrefs, itemKey, loadPrefs, parseItemKey, parsePrefs, prefsKey, pushRecent, RECENT_MAX, savePrefs, toggleKey } from '../src/nav/prefs'
import { useAuthStore } from '../src/stores/auth'
import { useNavPrefsStore } from '../src/stores/navPrefs'
import { navigation, noModules } from './fixtures/nav'

describe('sidebar groups', () => {
  it('groups the visible modules, hides modules without entries and empty groups', () => {
    const groups = menuGroups(moduleCounts(navigation()))
    expect(groups.map((g) => g.id)).toEqual(['iso', 'operations', 'assets'])
    expect(groups[0].modules.map((m) => m.key)).toEqual(['actionPlans', 'legal'])
    expect(groups[1].modules.map((m) => m.key)).toEqual(['appointments', 'alarms'])
    expect(groups[1].modules[0]).toEqual({ key: 'appointments', route: 'appointments', count: 3, open: 0 })
    expect(groups[2].modules.map((m) => m.key)).toEqual(['meters'])
  })

  it('sums the open-item badges per group (open alarms)', () => {
    const groups = menuGroups(moduleCounts(navigation()))
    expect(groups.find((g) => g.id === 'operations')!.open).toBe(4)
    expect(groups.find((g) => g.id === 'iso')!.open).toBe(0)
    /* open alarms without readable alarm configurations: no alarms entry (as before) */
    expect(menuGroups({ ...noModules, alarmsOpen: 3 })).toEqual([])
  })

  it('all four groups in order when everything is readable', () => {
    const all = Object.fromEntries(Object.keys(noModules).map((k) => [k, 1])) as unknown as typeof noModules
    expect(menuGroups(all).map((g) => [g.id, g.modules.length])).toEqual([
      ['iso', 3],
      ['operations', 4],
      ['assets', 3],
      ['finance', 3],
    ])
    expect(menuGroups(all)[1].modules.map((m) => m.key)).toEqual(['appointments', 'maintenance', 'alarms', 'notes'])
    expect(menuGroups(all)[3].modules.map((m) => m.key)).toEqual(['contracts', 'invoices', 'costs'])
  })

  it('Berichte stands alone; the search lists all visible modules', () => {
    expect(singleModules(moduleCounts(navigation())).map((m) => m.key)).toEqual(['reports'])
    expect(singleModules(noModules)).toEqual([])
    expect(visibleModules(moduleCounts(navigation())).map((m) => m.key)).toEqual(['actionPlans', 'legal', 'appointments', 'alarms', 'meters', 'reports'])
  })

  it('finds the group of a route and opens it', () => {
    expect(groupOfRoute('nonconformities')).toBe('iso')
    expect(groupOfRoute('alarms')).toBe('operations')
    expect(groupOfRoute('equipment')).toBe('assets')
    expect(groupOfRoute('costs')).toBe('finance')
    expect(groupOfRoute('reports')).toBeNull()
    expect(groupOfRoute('dashboard')).toBeNull()
    expect(groupOfRoute(undefined)).toBeNull()
    const state = { iso: false }
    expect(openGroupOfRoute(state, 'legal')).toEqual({ iso: true })
    expect(openGroupOfRoute(state, 'home')).toBe(state)
    const open = { finance: true }
    expect(openGroupOfRoute(open, 'invoices')).toBe(open)
  })
})

describe('navigation settings in the browser', () => {
  function memoryStorage(): Storage {
    const data = new Map<string, string>()
    return {
      get length() {
        return data.size
      },
      clear: () => data.clear(),
      getItem: (k) => data.get(k) ?? null,
      key: (i) => [...data.keys()][i] ?? null,
      removeItem: (k) => void data.delete(k),
      setItem: (k, v) => void data.set(k, String(v)),
    }
  }

  it('item keys', () => {
    expect(itemKey('dashboard', 5)).toBe('d:5')
    expect(itemKey('analysis', 6)).toBe('a:6')
    expect(parseItemKey('d:5')).toEqual({ kind: 'dashboard', id: 5 })
    expect(parseItemKey('x:5')).toBeNull()
  })

  it('stores per user and reads only valid values', () => {
    const storage = memoryStorage()
    const prefs = { ...emptyPrefs(), favourites: ['d:1'], site: 2, collapsed: true, groups: { iso: true } }
    expect(savePrefs(7, prefs, storage)).toBe(true)
    expect(storage.getItem(prefsKey(7))).not.toBeNull()
    expect(loadPrefs(7, storage)).toEqual(prefs)
    expect(loadPrefs(8, storage)).toEqual(emptyPrefs())
    expect(loadPrefs(null, storage)).toEqual(emptyPrefs())
    expect(savePrefs(null, prefs, storage)).toBe(false)

    storage.setItem(prefsKey(9), '{not json')
    expect(loadPrefs(9, storage)).toEqual(emptyPrefs())
    expect(
      parsePrefs({ favourites: ['d:1', 'd:1', 5, 'bad'], recent: ['a:1', 'a:2', 'a:3', 'a:4', 'a:5', 'a:6', 'a:7'], site: -1, collapsed: 'yes', groups: { iso: true, x: 1 } }),
    ).toEqual({ favourites: ['d:1'], recent: ['a:1', 'a:2', 'a:3', 'a:4', 'a:5', 'a:6'], site: 'all', collapsed: false, groups: { iso: true } })
    expect(parsePrefs(null)).toEqual(emptyPrefs())
  })

  it('works without storage or with a failing one', () => {
    expect(loadPrefs(1, null)).toEqual(emptyPrefs())
    expect(savePrefs(1, emptyPrefs(), null)).toBe(false)
    const broken = {
      getItem: () => {
        throw new Error('blocked')
      },
      setItem: () => {
        throw new Error('full')
      },
    } as unknown as Storage
    expect(loadPrefs(1, broken)).toEqual(emptyPrefs())
    expect(savePrefs(1, emptyPrefs(), broken)).toBe(false)
  })

  it('favourites toggle, recently opened keeps the newest six without duplicates', () => {
    expect(toggleKey(['d:1'], 'd:2')).toEqual(['d:1', 'd:2'])
    expect(toggleKey(['d:1', 'd:2'], 'd:1')).toEqual(['d:2'])
    let recent: string[] = []
    for (let i = 1; i <= 8; i++) recent = pushRecent(recent, 'a:' + i)
    expect(recent).toEqual(['a:8', 'a:7', 'a:6', 'a:5', 'a:4', 'a:3'])
    expect(recent).toHaveLength(RECENT_MAX)
    expect(pushRecent(recent, 'a:5')).toEqual(['a:5', 'a:8', 'a:7', 'a:6', 'a:4', 'a:3'])
  })

  describe('store', () => {
    beforeEach(() => {
      setActivePinia(createPinia())
      window.localStorage.clear()
    })
    afterEach(() => vi.restoreAllMocks())

    it('keeps the settings per user id in localStorage', async () => {
      const auth = useAuthStore()
      auth.user = { id: 7, name: 'Muster' }
      const prefs = useNavPrefsStore()
      prefs.toggleFavourite('d:100')
      prefs.recordOpened('a:301')
      prefs.recordOpened('a:301')
      prefs.setSite(2)
      prefs.setCollapsed(true)
      prefs.setGroupOpen('iso', true)
      expect(JSON.parse(window.localStorage.getItem('jevis.nav.7')!)).toEqual({ favourites: ['d:100'], recent: ['a:301'], site: 2, collapsed: true, groups: { iso: true } })
      expect(prefs.isFavourite('d:100')).toBe(true)

      auth.user = { id: 8, name: 'Andere' }
      await nextTick()
      expect(prefs.favourites).toEqual([])
      expect(prefs.site).toBe('all')
      auth.user = { id: 7, name: 'Muster' }
      await nextTick()
      expect(prefs.favourites).toEqual(['d:100'])
    })

    it('works for the page when the storage refuses', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('QuotaExceededError')
      })
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('SecurityError')
      })
      useAuthStore().user = { id: 7, name: 'Muster' }
      const prefs = useNavPrefsStore()
      prefs.toggleFavourite('d:1')
      expect(prefs.isFavourite('d:1')).toBe(true)
    })
  })
})

describe('dashboards and analyses of the navigation', () => {
  const nav = navigation()

  it('sites in tree order with paths', () => {
    expect(siteChoices(nav, 'de')).toEqual([
      { id: 4, name: 'Lager Nord', path: 'Lager Nord', depth: 0 },
      { id: 1, name: 'Muster AG', path: 'Muster AG', depth: 0 },
      { id: 2, name: 'Musterwerk', path: 'Muster AG › Musterwerk', depth: 1 },
      { id: 3, name: 'Halle 2', path: 'Muster AG › Musterwerk › Halle 2', depth: 2 },
    ])
    expect([...siteAndDescendants(nav, 2)]).toEqual([2, 3])
    expect([...siteAndDescendants(nav, 1)].sort()).toEqual([1, 2, 3])
    expect(siteChoices(null, 'de')).toEqual([])
  })

  it('flat, sorted lists with site path; items without site have none', () => {
    const dashboards = catalog(nav, 'dashboard', 'de')
    expect(dashboards.map((d) => [d.name, d.sitePath])).toEqual([
      ['Lageplan', 'Muster AG › Musterwerk'],
      ['Sankey', 'Muster AG › Musterwerk › Halle 2'],
      ['Übersicht', 'Muster AG › Musterwerk'],
      ['Wetter', null],
    ])
    expect(dashboards[0]).toMatchObject({ kind: 'dashboard', id: 101, key: 'd:101', siteId: 2 })
    expect(catalog(nav, 'dashboard', 'en').map((d) => d.name)).toContain('Overview')
    expect(catalog(nav, 'analysis', 'de').map((a) => a.key)).toEqual(['a:301', 'a:300', 'a:302'])
    expect(catalog(null, 'analysis', 'de')).toEqual([])
  })

  it('filters by name, site (with sub-sites) and favourites', () => {
    const dashboards = catalog(nav, 'dashboard', 'de')
    const f = (text: string, site: number | 'all', favouritesOnly = false, favs: string[] = []) =>
      filterCatalog(dashboards, { text, site, favouritesOnly }, favs, nav, 'de').map((d) => d.name)
    expect(f('', 'all')).toEqual(['Lageplan', 'Sankey', 'Übersicht', 'Wetter'])
    expect(f('über', 'all')).toEqual(['Übersicht'])
    expect(f('halle', 'all')).toEqual(['Sankey'])
    expect(f('', 2)).toEqual(['Lageplan', 'Sankey', 'Übersicht'])
    expect(f('', 3)).toEqual(['Sankey'])
    expect(f('', 1)).toEqual(['Lageplan', 'Sankey', 'Übersicht'])
    expect(f('', 4)).toEqual([])
    expect(f('', 'all', true, ['d:103', 'd:100'])).toEqual(['Übersicht', 'Wetter'])
    expect(filterCatalog(dashboards, { text: '', site: 'all', favouritesOnly: true }, new Set(['d:102']), nav, 'de').map((d) => d.id)).toEqual([102])
  })

  it('favourites by name (limited) and recently opened in order, missing ones skipped', () => {
    const all = [...catalog(nav, 'dashboard', 'de'), ...catalog(nav, 'analysis', 'de')]
    expect(favouriteEntries(catalog(nav, 'dashboard', 'de'), ['d:103', 'd:100', 'd:101'], 2).map((e) => e.name)).toEqual(['Lageplan', 'Übersicht'])
    expect(recentEntries(all, ['a:302', 'd:999', 'd:100']).map((e) => e.name)).toEqual(['Wasser', 'Übersicht'])
  })

  it('records opened dashboards and analyses only', () => {
    expect(openedItemKey('dashboard', { id: '100' })).toBe('d:100')
    expect(openedItemKey('analysis', { id: '7' })).toBe('a:7')
    expect(openedItemKey('analyses', {})).toBeNull()
    expect(openedItemKey('dashboard', { id: 'x' })).toBeNull()
  })

  it('the chosen site falls back to Alle when it is gone', () => {
    expect(effectiveSite(2, nav)).toBe(2)
    expect(effectiveSite(99, nav)).toBe('all')
    expect(effectiveSite(2, null)).toBe('all')
    expect(effectiveSite('all', nav)).toBe('all')
  })

  it('preselects the tab of the chosen site, else one below it', () => {
    const tabs = [
      { key: 'a', site: { id: 4 } },
      { key: 'b', site: { id: 3 } },
      { key: 'c', site: { id: 2 } },
      { key: 'd', site: null },
    ]
    expect(preferredBySite(tabs, 2, nav)).toBe('c')
    expect(preferredBySite(tabs, 3, nav)).toBe('b')
    expect(preferredBySite(tabs, 1, nav)).toBe('b')
    expect(preferredBySite(tabs, 'all', nav)).toBeNull()
    expect(preferredBySite(tabs.slice(0, 1), 2, nav)).toBeNull()
  })
})

describe('search of the palette', () => {
  const it_ = (id: string, kind: PaletteItem['kind'], label: string, detail: string | null = null): PaletteItem => ({ id, kind, label, detail, route: { name: 'x' } })
  const items = [
    it_('m:meters', 'module', 'Messstellen', 'Anlagen'),
    it_('d:1', 'dashboard', 'Strom Übersicht', 'Musterwerk'),
    it_('a:1', 'analysis', 'Strom gesamt', 'Musterwerk'),
    it_('a:2', 'analysis', 'Gesamtstrom', 'Lager Nord'),
    it_('a:3', 'analysis', 'Wasser', 'Muster AG › Musterwerk'),
  ]
  const ids = (q: string) => paletteSearch(items, q, 'de').map((i) => i.id)

  it('label start first, then word start, then the rest; modules before dashboards before analyses', () => {
    expect(ids('strom')).toEqual(['d:1', 'a:1', 'a:2'])
    expect(ids('gesamt')).toEqual(['a:2', 'a:1'])
    expect(ids('übersicht')).toEqual(['d:1'])
  })

  it('every word must occur in label or site path', () => {
    expect(ids('strom lager')).toEqual(['a:2'])
    expect(ids('musterwerk')).toEqual(['d:1', 'a:1', 'a:3'])
    expect(ids('anlagen')).toEqual(['m:meters'])
    expect(ids('nichts')).toEqual([])
  })

  it('empty search shows the initial list; results are limited', () => {
    expect(paletteSearch(items, '  ', 'de', items.slice(0, 2)).map((i) => i.id)).toEqual(['m:meters', 'd:1'])
    expect(paletteSearch(items, 'strom', 'de', [], 2)).toHaveLength(2)
  })

  it('keys: arrows wrap, Enter opens, Esc closes', () => {
    expect(paletteKey('ArrowDown', 0, 3)).toEqual({ type: 'move', index: 1 })
    expect(paletteKey('ArrowDown', 2, 3)).toEqual({ type: 'move', index: 0 })
    expect(paletteKey('ArrowUp', 0, 3)).toEqual({ type: 'move', index: 2 })
    expect(paletteKey('ArrowUp', 2, 3)).toEqual({ type: 'move', index: 1 })
    expect(paletteKey('PageDown', 0, 3)).toEqual({ type: 'move', index: 2 })
    expect(paletteKey('PageUp', 2, 3)).toEqual({ type: 'move', index: 0 })
    expect(paletteKey('Enter', 1, 3)).toEqual({ type: 'open', index: 1 })
    expect(paletteKey('Enter', -1, 0)).toBeNull()
    expect(paletteKey('ArrowDown', -1, 0)).toBeNull()
    expect(paletteKey('Escape', 0, 0)).toEqual({ type: 'close' })
    expect(paletteKey('a', 0, 3)).toBeNull()
  })

  it('shortcut: Strg+K / Cmd+K always, "/" only outside text fields', () => {
    const input = document.createElement('input')
    const checkbox = Object.assign(document.createElement('input'), { type: 'checkbox' })
    const div = document.createElement('div')
    const ev = (key: string, target: EventTarget | null, mods: Partial<KeyboardEvent> = {}) => ({ key, target, ctrlKey: false, metaKey: false, altKey: false, shiftKey: false, ...mods })
    expect(isPaletteShortcut(ev('k', input, { ctrlKey: true }))).toBe(true)
    expect(isPaletteShortcut(ev('K', div, { metaKey: true }))).toBe(true)
    expect(isPaletteShortcut(ev('k', div))).toBe(false)
    expect(isPaletteShortcut(ev('k', div, { ctrlKey: true, altKey: true }))).toBe(false)
    expect(isPaletteShortcut(ev('/', div))).toBe(true)
    expect(isPaletteShortcut(ev('/', checkbox))).toBe(true)
    expect(isPaletteShortcut(ev('/', input))).toBe(false)
    expect(isPaletteShortcut(ev('/', document.createElement('textarea')))).toBe(false)
    expect(isTyping(document.createElement('select'))).toBe(true)
    const editable = document.createElement('div')
    editable.setAttribute('contenteditable', 'true')
    expect(isTyping(editable)).toBe(true)
    expect(isTyping(null)).toBe(false)
  })
})
