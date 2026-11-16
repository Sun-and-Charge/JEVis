/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReadingList, ReadingMeter, ReviewItem, ReviewList } from '../src/api/readings'
import { toDateTimeInput } from '../src/edit/edit'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import { useNavigationStore } from '../src/stores/navigation'
import ReadingsView from '../src/views/ReadingsView.vue'
import { httpError } from './fixtures/edit'

/* E8.5 review of contradictory readings on the reading page, with neutral data ("Musterwerk", "Lager Nord"). */

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
    canPhoto: false,
    canExchange: true,
    row: {
      id: id + 1000,
      name: name + ' - Daten',
      unit: 'kWh',
      counter: true,
      period: 'P1M',
      mode: 'DAY',
      timeZone: 'Europe/Berlin',
      limits: null,
      canEnter: true,
      canDelete: true,
      decimals: 1,
    },
    due: 'OK',
    suggestedTimestamp: '2026-12-01T00:00:00.000+01:00',
    expected: null,
    readings: [
      { timestamp: '2026-11-01T00:00:00.000+01:00', value: 12600, note: null, manual: true, photo: false },
      { timestamp: '2026-10-01T00:00:00.000+02:00', value: 11800, note: null, manual: true, photo: false },
    ],
    problem: null,
    ...overrides,
  }
}

function readings(): ReadingList {
  return {
    today: '2026-11-13',
    groups: [
      { key: '5001', name: 'Musterwerk', site: { id: 1001, name: 'Musterwerk' }, meters: [meter(5101, 'Hauptzähler Strom'), meter(5102, 'Wasser Kantine', { medium: 'WATER' })] },
      { key: '5002', name: 'Lager Nord', site: { id: 1002, name: 'Lager Nord' }, meters: [meter(5201, 'Lager Strom')] },
    ],
  }
}

function item(meterId: number, meterName: string, overrides: Partial<ReviewItem> = {}): ReviewItem {
  return {
    meterId,
    meterName,
    site: { id: 1001, name: 'Musterwerk' },
    row: { id: meterId + 1000, name: meterName + ' - Daten', unit: 'kWh', counter: true, canEnter: true, canDelete: true, canNote: true },
    timestamp: '2026-10-01T00:00:00.000+02:00',
    value: 11800,
    note: null,
    manual: true,
    previous: { timestamp: '2026-09-01T00:00:00.000+02:00', value: 12100 },
    expected: { value: 12350, low: 12200, high: 12500 },
    issues: ['COUNTER_DECREASE'],
    canExchange: true,
    ...overrides,
  }
}

function review(): ReviewList {
  return {
    items: [
      item(5101, 'Hauptzähler Strom', { note: 'Manuelle Dateneingabe 02.10.2026 – Zähler verschmutzt' }),
      item(5102, 'Wasser Kantine', {
        timestamp: '2026-09-15T00:00:00.000+02:00',
        value: 900,
        previous: { timestamp: '2026-08-15T00:00:00.000+02:00', value: 450 },
        expected: { value: 480, low: 460, high: 500 },
        issues: ['FAR_FROM_EXPECTED', 'LIMIT'],
        row: { id: 6102, name: 'Wasser - Daten', unit: 'm³', counter: false, canEnter: true, canDelete: false, canNote: true },
        canExchange: false,
      }),
      item(5201, 'Lager Strom', { site: { id: 1002, name: 'Lager Nord' }, value: 50, issues: ['COUNTER_DECREASE'] }),
    ],
    count: 3,
    truncated: false,
  }
}

async function mountView(path: string, http: Partial<Record<'post', Fn>> = {}, reviewAnswer: () => ReviewList = review) {
  const get = vi.fn(async (p: string) => {
    if (p === '/web/navigation') return { sites: [], other: { analyses: [], dashboards: [] }, modules: { readings: 3, readingsReview: 2 } }
    if (p === '/web/readings?lang=de') return readings()
    if (p === '/web/readings/review?lang=de&days=365') return reviewAnswer()
    if (p === '/web/readings/review?lang=de&days=90') return { items: [], count: 0, truncated: false }
    throw new Error('GET ' + p)
  })
  const post = http.post ?? vi.fn(async (_p: string, body: { value?: number; timestamp?: string; action?: string }) => ({ timestamp: body.timestamp, value: body.value, action: body.action, warnings: [] }))
  Object.assign(useAuthStore().http, { get, post })
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
  return { wrapper, router, get, post }
}

const button = (w: VueWrapper | ReturnType<VueWrapper['find']>, text: string) => w.findAll('button').find((b) => sp(b.text()) === text)
const byLabel = (w: VueWrapper, label: string) => w.findAll('button').find((b) => b.attributes('aria-label') === label)
const meterNames = (w: VueWrapper) => w.findAll('.rd-review-row .rd-review-meter').map((n) => sp(n.text()))
const lastDialog = (w: VueWrapper) => w.findAll('[role="dialog"]').pop()!
const reviewCalls = (get: Fn) => get.mock.calls.filter((c) => String(c[0]).startsWith('/web/readings/review')).length

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('reading review view', () => {
  it('shows the count on the tab and lists the items with issues, site tabs and period', async () => {
    const nav = useNavigationStore()
    nav.data = { user: { id: 1, name: 'Muster', sysAdmin: false }, sites: [], other: { analyses: [], dashboards: [] }, modules: { readings: 3, readingsReview: 2 } }
    const { wrapper: w, router, get } = await mountView('/readings')
    const tab = w.findAll('.rd-view')[2]
    expect(sp(tab.find('[aria-hidden="true"]').text())).toBe('2')
    expect(tab.find('.visually-hidden').text()).toBe('2 Ablesungen zu prüfen')
    expect(reviewCalls(get)).toBe(0)

    await tab.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ view: 'review' })
    expect(w.findAll('.rd-view')[2].attributes('aria-selected')).toBe('true')
    /* the loaded list's count replaces the navigation's */
    expect(sp(w.findAll('.rd-view')[2].find('.rd-view-count [aria-hidden="true"]').text())).toBe('3')

    expect(w.findAll('.reg-tab').map((t) => sp(t.text()))).toEqual(['Alle 3', 'Lager Nord 1', 'Musterwerk 2'])
    expect(meterNames(w)).toEqual(['Hauptzähler Strom', 'Wasser Kantine', 'Lager Strom'])
    const first = w.findAll('.rd-review-row')[0]
    expect(first.text()).toContain('01.10.2026')
    expect(first.find('.rd-review-value').text()).toBe('11.800,0 kWh')
    expect(first.text()).toContain('12.100,0 kWh')
    expect(first.text()).toContain('12.200,0 … 12.500,0 kWh')
    expect(first.text()).toContain('Musterwerk')
    expect(first.find('.rd-note').text()).toBe('Zähler verschmutzt')
    expect(first.findAll('.rd-issue').map((b) => b.text())).toEqual(['Rücklauf'])
    expect(first.find('.rd-issue').classes()).toContain('is-overdue')
    const second = w.findAll('.rd-review-row')[1]
    expect(second.findAll('.rd-issue').map((b) => b.text())).toEqual(['Abweichung vom Erwartungswert', 'Grenzwert'])
    expect(second.find('.rd-issue').classes()).toContain('is-dueSoon')
    /* no exchange for a non-counter, no ignore without delete right */
    expect(second.find('.rd-review-exchange').exists()).toBe(false)
    expect(second.find('.rd-review-ignore').exists()).toBe(false)
    expect(second.find('.rd-review-confirm').exists()).toBe(true)

    await button(w, 'Lager Nord 1')!.trigger('click')
    expect(meterNames(w)).toEqual(['Lager Strom'])

    await w.find('#rd-review-days').setValue('90')
    await flushPromises()
    expect(get.mock.calls.some((c) => c[0] === '/web/readings/review?lang=de&days=90')).toBe(true)
    expect(w.find('.rd-review-empty').text()).toBe('Keine widersprüchlichen Stände in den letzten 90 Tagen.')
  })

  it('confirms a value with a comment, then reloads the list and the navigation', async () => {
    const { wrapper: w, get, post } = await mountView('/readings?view=review')
    const before = reviewCalls(get)
    await byLabel(w, 'Wert von „Hauptzähler Strom“ vom 01.10.2026 bestätigen')!.trigger('click')
    const dialog = lastDialog(w)
    expect(sp(dialog.find('h2').text())).toBe('Wert bestätigen')
    expect(dialog.text()).toContain('11.800,0 kWh von „Hauptzähler Strom“ am 01.10.2026 als plausibel bestätigen?')
    await dialog.find('textarea').setValue('Vor Ort geprüft')
    await button(dialog, 'Bestätigen')!.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/readings/review/5101?lang=de', {
      timestamp: '2026-10-01T00:00:00.000+02:00',
      action: 'CONFIRM',
      comment: 'Vor Ort geprüft',
      value: 11800,
    })
    expect(w.find('.rd-review .rd-message').text()).toBe('Wert von „Hauptzähler Strom“ bestätigt: 11.800,0 kWh am 01.10.2026.')
    expect(reviewCalls(get)).toBe(before + 1)
    expect(get.mock.calls.some((c) => c[0] === '/web/navigation')).toBe(true)
  })

  it('ignores a value after the confirmation text; a changed value reloads the list', async () => {
    const post = vi.fn(async () => {
      throw httpError(409, { meterId: 5201 })
    })
    const { wrapper: w, get } = await mountView('/readings?view=review', { post })
    await byLabel(w, 'Wert von „Lager Strom“ vom 01.10.2026 ignorieren')!.trigger('click')
    let dialog = lastDialog(w)
    expect(sp(dialog.find('h2').text())).toBe('Wert ignorieren')
    expect(dialog.text()).toContain('Der Wert wird aus der Datenreihe entfernt und als Notiz festgehalten.')
    /* an invalid comment is refused without a request */
    await dialog.find('textarea').setValue('a\u0007b')
    await button(dialog, 'Ignorieren')!.trigger('click')
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
    expect(dialog.find('.iso-field-error').text()).toBe('Der Kommentar enthält ungültige Zeichen.')

    dialog = lastDialog(w)
    await dialog.find('textarea').setValue('Ablesefehler')
    const before = reviewCalls(get)
    await button(dialog, 'Ignorieren')!.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/readings/review/5201?lang=de', { timestamp: '2026-10-01T00:00:00.000+02:00', action: 'IGNORE', comment: 'Ablesefehler', value: 50 })
    expect(w.find('.rd-review .rd-message').text()).toBe('Der Wert von „Lager Strom“ wurde inzwischen geändert – die Liste wurde neu geladen.')
    expect(reviewCalls(get)).toBe(before + 1)
    expect(w.findAll('[role="dialog"]')).toHaveLength(0)
  })

  it('ignores a value and shows the result', async () => {
    const { wrapper: w, post } = await mountView('/readings?view=review')
    await byLabel(w, 'Wert von „Lager Strom“ vom 01.10.2026 ignorieren')!.trigger('click')
    await button(lastDialog(w), 'Ignorieren')!.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/readings/review/5201?lang=de', { timestamp: '2026-10-01T00:00:00.000+02:00', action: 'IGNORE', comment: null, value: 50 })
    expect(w.find('.rd-review .rd-message').text()).toBe('Wert von „Lager Strom“ ignoriert: 50,0 kWh am 01.10.2026 wurde entfernt und als Notiz festgehalten.')
  })

  it('corrects a value in the reading panel with the time fixed and confirm', async () => {
    const { wrapper: w, get, post } = await mountView('/readings?view=review')
    await byLabel(w, 'Wert von „Hauptzähler Strom“ vom 01.10.2026 korrigieren')!.trigger('click')
    const panel = w.find('.rd-panel-backdrop [role="dialog"]')
    expect(sp(panel.find('h2').text())).toBe('Hauptzähler Strom')
    expect(panel.find('.rd-correction-badge').text()).toBe('Korrektur')
    expect(panel.find('.rd-correction-old').text()).toBe('11.800,0 kWh')
    expect(panel.find('.rd-correction').text()).toContain('12.100,0 kWh')
    expect(panel.find('.rd-correction-time').text()).toBe('01.10.2026')
    /* no time input, no exchange, no "Speichern & weiter"; the server's expected range */
    expect(panel.find('input[type="date"]').exists()).toBe(false)
    expect(panel.find('.rd-exchange').exists()).toBe(false)
    expect(sp(panel.find('.rd-expected').text())).toContain('12.350,0 kWh')
    await panel.find('.rd-value').setValue('11000')
    expect(panel.find('.rd-expected-warn').text()).toBe('Der Stand ist kleiner als der letzte Stand – bitte prüfen.')
    await panel.find('.rd-value').setValue('12300,5')
    expect(panel.find('.rd-expected-warn').exists()).toBe(false)
    const before = reviewCalls(get)
    expect(sp(button(w, 'Korrektur speichern')!.text())).toBe('Korrektur speichern')
    await panel.find('form').trigger('submit')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/readings/5101?lang=de', { timestamp: '2026-10-01T00:00:00.000+02:00', value: 12300.5, note: null, confirm: true })
    expect(w.find('.rd-panel-backdrop').exists()).toBe(false)
    expect(w.find('.rd-review .rd-message').text()).toBe('Wert von „Hauptzähler Strom“ korrigiert: 12.300,5 kWh am 01.10.2026.')
    expect(reviewCalls(get)).toBe(before + 1)
  })

  it('opens the meter exchange prefilled with the time and the value as the new meter reading', async () => {
    const { wrapper: w } = await mountView('/readings?view=review')
    await byLabel(w, 'Zählerwechsel für „Hauptzähler Strom“ am 01.10.2026 erfassen')!.trigger('click')
    const dialog = lastDialog(w)
    expect(sp(dialog.find('h2').text())).toBe('Zählerwechsel')
    expect(dialog.text()).toContain('Hauptzähler Strom')
    expect((dialog.find('input[type="datetime-local"]').element as HTMLInputElement).value).toBe(toDateTimeInput(Date.parse('2026-10-01T00:00:00.000+02:00')))
    expect((dialog.find('[id$="-newReading"]').element as HTMLInputElement).value).toBe('11800')
    expect((dialog.find('[id$="-oldReading"]').element as HTMLInputElement).value).toBe('')
  })

  it('shows cards on phones and a note when the list is cut', async () => {
    vi.stubGlobal('matchMedia', (q: string) => ({ matches: true, media: q, addEventListener: () => undefined, removeEventListener: () => undefined }))
    const { wrapper: w } = await mountView('/readings?view=review', {}, () => ({ ...review(), count: 500, truncated: true }))
    const cards = w.findAll('.rd-review-card')
    expect(cards.map((c) => sp(c.find('h2').text()))).toEqual(['Hauptzähler Strom', 'Wasser Kantine', 'Lager Strom'])
    expect(cards[0].find('.rd-review-correct').exists()).toBe(true)
    expect(cards[0].findAll('.rd-issue').map((b) => b.text())).toEqual(['Rücklauf'])
    expect(w.find('.rd-review-table').exists()).toBe(false)
    expect(w.find('.rd-review-truncated').text()).toBe('Es werden nur die neuesten 3 Einträge angezeigt.')
  })
})
