/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReadingList, ReadingMeter } from '../src/api/readings'
import { i18n } from '../src/i18n'
import {
  canRead,
  defaultFilter,
  defaultReadingInput,
  dueCounts,
  expectedAt,
  filterMeters,
  nextDueMeter,
  readingInputMode,
  readingTimestamp,
  userNote,
} from '../src/readings/readings'
import { useAuthStore } from '../src/stores/auth'
import ReadingsView from '../src/views/ReadingsView.vue'
import { httpError } from './fixtures/edit'

/* E8.1 meter reading page with neutral data ("Musterwerk", "Hauptzähler Strom"). */

const Stub = defineComponent({ render: () => h('div') })
const App = defineComponent({ render: () => h(RouterView) })
const sp = (s: string) => s.replace(/\s+/g, ' ').trim()
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []
const NOW = Date.parse('2026-11-13T10:00:00+01:00')

function meter(id: number, name: string, overrides: Partial<ReadingMeter> = {}): ReadingMeter {
  return {
    id,
    name,
    className: 'Electricity Measurement Instrument',
    classLabel: 'Stromzähler',
    medium: 'ELECTRICITY',
    path: 'Musterwerk / Messstellen',
    serialNumber: 'A-' + id,
    meterPoint: null,
    location: 'Keller',
    canPhoto: true,
    canExchange: true,
    row: {
      id: id + 1000,
      name: name + ' - Daten',
      unit: 'kWh',
      counter: true,
      period: 'P1M',
      mode: 'DAY',
      timeZone: 'Europe/Berlin',
      limits: { min: 0, max: 50000 },
      canEnter: true,
      canDelete: true,
      decimals: 1,
    },
    due: 'OVERDUE',
    suggestedTimestamp: '2026-11-01T00:00:00.000+01:00',
    expected: { value: 12345.6, low: 12100, high: 12600 },
    readings: [
      { timestamp: '2026-10-01T00:00:00.000+02:00', value: 11800, note: 'Manuelle Dateneingabe 01.10.2026 – Zähler verschmutzt', manual: true, photo: true },
      { timestamp: '2026-09-01T00:00:00.000+02:00', value: 11500, note: null, manual: false, photo: false },
    ],
    problem: null,
    ...overrides,
  }
}

function list(): ReadingList {
  return {
    today: '2026-11-13',
    groups: [
      {
        key: '5001',
        name: 'Musterwerk',
        site: { id: 1001, name: 'Musterwerk' },
        meters: [
          meter(5101, 'Hauptzähler Strom'),
          meter(5102, 'Unterzähler Halle 2', { due: 'DUE', location: 'Halle 2' }),
          meter(5103, 'Wasser Kantine', { due: 'OK', medium: 'WATER', serialNumber: 'W-77' }),
          meter(5104, 'Gas Heizung', { due: 'NONE', row: null, problem: 'NOT_MANUAL', medium: 'GAS', readings: [], expected: null }),
        ],
      },
      { key: '5002', name: 'Lager Nord', site: { id: 1002, name: 'Lager Nord' }, meters: [meter(5201, 'Lager Strom', { due: 'OK' })] },
    ],
  }
}

async function mountView(path = '/readings', http: Partial<Record<'post' | 'postForm', Fn>> = {}) {
  const get = vi.fn(async (p: string) => {
    if (p === '/web/navigation') return { sites: [], other: { analyses: [], dashboards: [] } }
    if (p === '/web/readings?lang=de') return list()
    throw new Error('GET ' + p)
  })
  const post = http.post ?? vi.fn(async (_p: string, body: { timestamp: string; value: number }) => ({ timestamp: '2026-11-01T00:00:00.000+01:00', value: body.value, warnings: [] }))
  const postForm = http.postForm ?? vi.fn(async () => ({ timestamp: '2026-11-01T00:00:00.000+01:00', file: 'Ablesung_20261101_0000.jpg' }))
  Object.assign(useAuthStore().http, { get, post, postForm, getBlob: vi.fn(async () => new Blob(['x'])) })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/readings', name: 'readings', component: ReadingsView },
    ],
  })
  await router.push(path)
  const wrapper = mount(App, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router, get, post, postForm }
}

const names = (w: VueWrapper) => w.findAll('.rd-row .rd-name').map((b) => sp(b.text()))
const button = (w: VueWrapper, text: string) => w.findAll('button').find((b) => sp(b.text()) === text)
const dialog = (w: VueWrapper) => w.find('[role="dialog"]')
const readingCalls = (get: Fn) => get.mock.calls.filter((c) => c[0] === '/web/readings?lang=de').length

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
  vi.useRealTimers()
})

describe('reading list logic', () => {
  const meters = list().groups[0].meters

  it('counts, filters and searches', () => {
    expect(dueCounts(meters)).toEqual({ due: 2, overdue: 1 })
    expect(defaultFilter(meters)).toBe('due')
    expect(defaultFilter([meters[2]])).toBe('all')
    expect(filterMeters(meters, 'due', '', 'de').map((m) => m.id)).toEqual([5101, 5102])
    expect(filterMeters(meters, 'all', 'w-77', 'de').map((m) => m.id)).toEqual([5103])
    expect(filterMeters(meters, 'all', 'halle', 'de').map((m) => m.id)).toEqual([5102])
    expect(canRead(meters[3])).toBe(false)
  })

  it('finds the next due meter after the current one, wrapping around', () => {
    expect(nextDueMeter(meters, 5101)?.id).toBe(5102)
    expect(nextDueMeter(meters, 5102)?.id).toBe(5101)
    expect(nextDueMeter(meters, 5103)?.id).toBe(5101)
    expect(nextDueMeter([meters[0]], 5101)).toBeNull()
  })

  it('prefills the time with the suggestion unless it is in the future', () => {
    expect(defaultReadingInput(meters[0], NOW)).toBe('2026-11-01')
    expect(defaultReadingInput({ ...meters[0], suggestedTimestamp: '2026-12-01T00:00:00.000+01:00' }, NOW)).toBe('2026-11-13')
    const free = { ...meters[0], row: { ...meters[0].row!, mode: 'SPECIFIC_DATETIME' } }
    expect(defaultReadingInput({ ...free, suggestedTimestamp: null }, NOW)).toBe('2026-11-13T10:00')
    expect(readingInputMode('MONTH')).toBe('DAY')
    expect(readingInputMode('SPECIFIC_DATETIME')).toBe('SPECIFIC_DATETIME')
    expect(readingTimestamp('2026-11-13', 'DAY')).toBe('2026-11-13T00:00:00')
    expect(readingTimestamp('2026-11-13', 'MONTH')).toBe('2026-11-01T00:00:00')
    expect(readingTimestamp('2026-11-13', 'YEAR')).toBe('2026-01-01T00:00:00')
    expect(readingTimestamp('2026-11-13T08:15', 'SPECIFIC_DATETIME')).toBe('2026-11-13T08:15:00')
    expect(readingTimestamp('kaputt', 'DAY')).toBeNull()
  })

  it('computes the expected value at the chosen time', () => {
    // 300 kWh in September (30 days): 10 per day; 31 days after 1 October
    const e = expectedAt(meters[0], '2026-11-01')!
    expect(e.value).toBeCloseTo(11800 + 300 * (31 * 24 + 1) / (30 * 24), 6)
    expect(expectedAt(meters[0], '2026-10-01')).toBeNull()
    expect(expectedAt(meters[3], '2026-11-01')).toBeNull()
  })

  it('shows the note the user typed', () => {
    expect(userNote({ note: 'Manuelle Dateneingabe 01.10.2026 – Zähler verschmutzt', manual: true })).toBe('Zähler verschmutzt')
    expect(userNote({ note: 'Manuelle Dateneingabe 01.10.2026', manual: true })).toBe('')
    expect(userNote({ note: 'Import', manual: false })).toBe('Import')
  })
})

describe('reading page', () => {
  it('shows the due meters of the first site with counts, all meters and the search', async () => {
    const { wrapper: w } = await mountView()
    expect(sp(w.find('h1').text())).toBe('Ablesung')
    expect(sp(w.find('.rd-head-count').text())).toBe('2 fällig, davon 1 überfällig')
    expect(w.findAll('.reg-tab').map((t) => sp(t.text()))).toEqual(['Musterwerk 2/4', 'Lager Nord 0/1'])
    expect(button(w, 'Fällig 2')!.attributes('aria-pressed')).toBe('true')
    expect(names(w)).toEqual(['Hauptzähler Strom', 'Unterzähler Halle 2'])
    expect(w.findAll('.rd-row .rd-due').map((b) => sp(b.text()))).toEqual(['Überfällig', 'Fällig'])

    await button(w, 'Alle 4')!.trigger('click')
    expect(names(w)).toEqual(['Hauptzähler Strom', 'Unterzähler Halle 2', 'Wasser Kantine', 'Gas Heizung'])
    expect(w.text()).toContain('Die Datenreihe wird automatisch befüllt')
    expect(w.findAll('.rd-row .rd-due').map((b) => sp(b.text()))[3]).toBe('Kein Turnus')
    expect(w.findAll('.rd-row')[0].text()).toContain('11.800,0 kWh')

    await w.find('input[type="search"]').setValue('W-77')
    expect(names(w)).toEqual(['Wasser Kantine'])

    /* a site without due meters starts with "Alle" */
    await w.findAll('.reg-tab')[1].trigger('click')
    expect(button(w, 'Alle 1')!.attributes('aria-pressed')).toBe('true')
  })

  it('opens a meter from a link and shows the last readings, expected value and problem', async () => {
    const { wrapper: w } = await mountView('/readings?meter=5101')
    const d = dialog(w)
    expect(d.exists()).toBe(true)
    expect(sp(d.find('h2').text())).toBe('Hauptzähler Strom')
    expect(d.text()).toContain('Letzte Ablesungen')
    expect(d.text()).toContain('Zähler verschmutzt')
    expect(d.findAll('.rd-photo-btn')).toHaveLength(1)
    expect((d.find('input[type="date"]').element as HTMLInputElement).value).toBe('2026-11-01')
    expect(d.find('.rd-expected').text()).toContain('Erwartet:')

    /* the expected value follows the time */
    const before = d.find('.rd-expected strong').text()
    await d.find('input[type="date"]').setValue('2026-11-10')
    expect(d.find('.rd-expected strong').text()).not.toBe(before)

    /* outside the range */
    await d.find('.rd-value').setValue('99999')
    expect(d.find('.rd-expected').classes()).toContain('is-outside')
  })

  it('shows meters without row read-only', async () => {
    const { wrapper: w } = await mountView('/readings?meter=5104')
    const d = dialog(w)
    expect(d.text()).toContain('Die Datenreihe wird automatisch befüllt')
    expect(d.find('.rd-value').exists()).toBe(false)
  })

  it('saves with warnings confirmed, uploads the photo and opens the next due meter', async () => {
    const post = vi.fn()
    post.mockRejectedValueOnce(httpError(409, { timestamp: '2026-11-01T00:00:00', value: 12400.5, warnings: ['Abweichung vom Erwartungswert: 12.345,6 (12.100 … 12.600)'] }))
    post.mockResolvedValueOnce({ timestamp: '2026-11-01T00:00:00.000+01:00', value: 12400.5, warnings: ['Abweichung vom Erwartungswert: 12.345,6 (12.100 … 12.600)'] })
    const { wrapper: w, get, postForm } = await mountView('/readings', { post })
    await w.findAll('.rd-row .rd-open')[0].trigger('click')
    const d = dialog(w)
    expect(sp(d.find('h2').text())).toBe('Hauptzähler Strom')
    await d.find('.rd-value').setValue('12.400,5')
    await d.find('input[placeholder="z. B. Zähler verschmutzt"]').setValue('  Glas beschlagen ')
    const file = new File(['jpeg'], 'IMG_0001.jpg', { type: 'image/jpeg' })
    const input = d.find('input[type="file"]')
    expect(input.attributes('accept')).toBe('image/*')
    expect(input.attributes('capture')).toBe('environment')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')

    expect(sp(d.find('.rd-save').text())).toBe('Speichern & weiter')
    await d.find('form').trigger('submit')
    await flushPromises()
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0]).toEqual(['/web/readings/5101?lang=de', { timestamp: '2026-11-01T00:00:00', value: 12400.5, note: 'Glas beschlagen', confirm: false }])
    expect(postForm).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('Abweichung vom Erwartungswert')

    await button(w, 'Trotzdem speichern')!.trigger('click')
    await flushPromises()
    expect(post.mock.calls[1][1]).toMatchObject({ confirm: true, value: 12400.5 })
    expect(postForm).toHaveBeenCalledTimes(1)
    expect(postForm.mock.calls[0][0]).toBe('/web/readings/5101/photo?timestamp=2026-11-01T00%3A00%3A00.000%2B01%3A00')
    expect(readingCalls(get)).toBe(2)
    /* the next due meter is open */
    expect(sp(dialog(w).find('h2').text())).toBe('Unterzähler Halle 2')
    expect(w.find('.rd-message').text()).toContain('Ablesung „Hauptzähler Strom“ gespeichert')
  })

  it('keeps the reading when the photo upload fails and goes on without it', async () => {
    const postForm = vi.fn(async () => {
      throw httpError(400, 'Kein Bild')
    })
    const { wrapper: w } = await mountView('/readings?meter=5102', { postForm })
    const d = dialog(w)
    await d.find('.rd-value').setValue('500')
    const input = d.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [new File(['x'], 'a.png', { type: 'image/png' })], configurable: true })
    await input.trigger('change')
    await button(w, 'Speichern')!.trigger('click')
    await flushPromises()
    expect(dialog(w).text()).toContain('Die Ablesung wurde gespeichert, das Foto aber nicht.')
    await button(w, 'Ohne Foto fortfahren')!.trigger('click')
    await flushPromises()
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.rd-message').text()).toContain('(ohne Foto)')
  })

  it('refuses invalid input without a request and asks before dropping input', async () => {
    const { wrapper: w, post, router } = await mountView('/readings?meter=5101')
    await dialog(w).find('.rd-value').setValue('zwölf')
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('Bitte eine Zahl eingeben.')

    const leaving = router.push('/')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/readings')
    expect(w.text()).toContain('Die Ablesung wurde nicht gespeichert.')
    await button(w, 'Verwerfen')!.trigger('click')
    await leaving
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('opens the meter exchange of E5 and prints the list', async () => {
    const print = vi.fn()
    vi.stubGlobal('print', print)
    window.print = print
    const { wrapper: w } = await mountView('/readings?meter=5101')
    await button(w, 'Zählerwechsel')!.trigger('click')
    expect(w.findAll('[role="dialog"]').map((d) => sp(d.find('h2').text()))).toContain('Zählerwechsel')
    await w.findAll('button').filter((b) => sp(b.text()) === 'Abbrechen').pop()!.trigger('click')

    expect(w.find('.rd-print').exists()).toBe(true)
    expect(w.findAll('.rd-print tbody tr')).toHaveLength(2)
    expect(w.find('.rd-print').text()).toContain('Kürzel')
    await button(w, 'Ableseliste drucken')!.trigger('click')
    expect(print).toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})
