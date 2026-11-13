/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReadingRound, RoundList, RoundOptions } from '../src/api/readingRounds'
import type { ReadingList, ReadingMeter } from '../src/api/readings'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import ReadingsView from '../src/views/ReadingsView.vue'
import { httpError } from './fixtures/edit'

/* E8.2 reading rounds on the reading page, with neutral data ("Musterwerk", "Max Mustermann"). */

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
    canExchange: false,
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
    due: 'OVERDUE',
    suggestedTimestamp: '2026-11-01T00:00:00.000+01:00',
    expected: null,
    readings: [{ timestamp: '2026-10-01T00:00:00.000+02:00', value: 11800, note: null, manual: true, photo: false }],
    problem: null,
    ...overrides,
  }
}

function readings(): ReadingList {
  return {
    today: '2026-11-13',
    groups: [
      {
        key: '5001',
        name: 'Musterwerk',
        site: { id: 1001, name: 'Musterwerk' },
        meters: [meter(5101, 'Hauptzähler Strom'), meter(5102, 'Unterzähler Halle 2', { due: 'DUE' }), meter(5103, 'Wasser Kantine', { due: 'OK', medium: 'WATER' })],
      },
      { key: '5002', name: 'Lager Nord', site: { id: 1002, name: 'Lager Nord' }, meters: [meter(5201, 'Lager Strom', { due: 'OK' })] },
    ],
  }
}

/* the round's meters in round order: Wasser Kantine, Hauptzähler (done), Unterzähler */
function roundReadings(): ReadingList {
  return {
    today: '2026-11-13',
    round: { id: 7001, name: 'Monatsablesung Musterwerk', due: '2026-11-01', nextDue: '2026-12-01', state: 'LATE' },
    groups: [
      {
        key: 'round-7001',
        name: 'Monatsablesung Musterwerk',
        site: { id: 1001, name: 'Musterwerk' },
        meters: [
          meter(5103, 'Wasser Kantine', { due: 'OK', medium: 'WATER', doneInRound: false }),
          meter(5101, 'Hauptzähler Strom', { doneInRound: true }),
          meter(5102, 'Unterzähler Halle 2', { due: 'DUE', doneInRound: false }),
        ],
      },
    ],
  }
}

function round(id: number, name: string, overrides: Partial<ReadingRound> = {}): ReadingRound {
  return {
    id,
    name,
    site: { id: 1001, name: 'Musterwerk' },
    directoryId: 7000,
    enabled: true,
    period: 'P1M',
    startDate: '2026-01-01',
    leadDays: 3,
    reminderDays: 3,
    escalationDays: 7,
    remarks: null,
    readers: [{ id: 41, name: 'Max Mustermann', kind: 'USER' }],
    supervisor: [],
    due: '2026-11-01',
    nextDue: '2026-12-01',
    state: 'OPEN',
    done: 1,
    total: 3,
    meters: [
      { id: 5103, readable: true, name: 'Wasser Kantine', done: false, automatic: false },
      { id: 5101, readable: true, name: 'Hauptzähler Strom', done: true, automatic: false },
      { id: 5102, readable: true, name: 'Unterzähler Halle 2', done: false, automatic: false },
    ],
    mine: false,
    canWrite: true,
    canDelete: true,
    version: 'v1',
    ...overrides,
  }
}

function rounds(): RoundList {
  return {
    today: '2026-11-13',
    rounds: [
      round(7004, 'Alte Runde', { state: 'INACTIVE', enabled: false, canWrite: false, canDelete: false }),
      round(7003, 'Lager Nord jährlich', { state: 'ESCALATED', site: { id: 1002, name: 'Lager Nord' }, readers: [{ id: 42, name: 'Energieteam', kind: 'GROUP' }], period: 'P1Y' }),
      round(7002, 'Wasser Quartal', { state: 'DONE', mine: true, period: 'P3M', done: 2, total: 2 }),
      round(7001, 'Monatsablesung Musterwerk', { state: 'LATE', mine: true }),
    ],
  }
}

function options(): RoundOptions {
  return {
    canCreate: true,
    directoryId: 7000,
    meters: [
      { id: 5101, name: 'Hauptzähler Strom', location: 'Keller', medium: 'ELECTRICITY', automatic: false },
      { id: 5102, name: 'Unterzähler Halle 2', location: 'Halle 2', medium: 'ELECTRICITY', automatic: false },
      { id: 5103, name: 'Wasser Kantine', location: 'Kantine', medium: 'WATER', automatic: false },
      { id: 5104, name: 'Gas Heizung', location: 'Keller', medium: 'GAS', automatic: true },
    ],
    people: [
      { id: 41, name: 'Max Mustermann', kind: 'USER', email: true },
      { id: 43, name: 'Erika Beispiel', kind: 'USER', email: false },
      { id: 42, name: 'Energieteam', kind: 'GROUP', email: false },
    ],
  }
}

async function mountView(path: string, http: Partial<Record<'post' | 'put' | 'delete' | 'getBlob', Fn>> = {}) {
  const get = vi.fn(async (p: string) => {
    if (p === '/web/navigation') return { sites: [], other: { analyses: [], dashboards: [] } }
    if (p === '/web/readings?lang=de') return readings()
    if (p === '/web/readings?lang=de&round=7001') return roundReadings()
    if (p === '/web/reading-rounds?lang=de') return rounds()
    if (p === '/web/reading-rounds/options?site=1001&lang=de') return options()
    if (p === '/web/reading-rounds/options?site=1002&lang=de') return { ...options(), canCreate: false, meters: [] }
    throw new Error('GET ' + p)
  })
  const post = http.post ?? vi.fn(async (_p: string, body: { value?: number; name?: string }) => ({ timestamp: '2026-11-01T00:00:00.000+01:00', value: body.value, warnings: [] }))
  const put = http.put ?? vi.fn()
  const del = http.delete ?? vi.fn(async () => undefined)
  const getBlob = http.getBlob ?? vi.fn(async () => new Blob(['%PDF'], { type: 'application/pdf' }))
  Object.assign(useAuthStore().http, { get, post, put, delete: del, getBlob })
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
  return { wrapper, router, get, post, put, del, getBlob }
}

const button = (w: VueWrapper | ReturnType<VueWrapper['find']>, text: string) => w.findAll('button').find((b) => sp(b.text()) === text)
const byLabel = (w: VueWrapper | ReturnType<VueWrapper['find']>, label: string) => w.findAll('button').find((b) => b.attributes('aria-label') === label)
const dialog = (w: VueWrapper) => w.find('.rd-round-backdrop [role="dialog"]')
const roundNames = (w: VueWrapper) => w.findAll('.rd-round-row .rd-round-name').map((n) => sp(n.text()))
const field = (w: VueWrapper, suffix: string) => dialog(w).find(`[id$="-${suffix}"]`)

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

describe('reading rounds view', () => {
  it('switches between meters and rounds and shows the own rounds first', async () => {
    const { wrapper: w, router } = await mountView('/readings')
    const views = w.findAll('.rd-view')
    expect(views.map((v) => sp(v.text()))).toEqual(['Zähler', 'Runden'])
    expect(views[0].attributes('aria-selected')).toBe('true')
    await views[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ view: 'rounds' })
    expect(w.findAll('.rd-view')[1].attributes('aria-selected')).toBe('true')
    expect(w.find('.rd-head-count').exists()).toBe(false)

    /* "Meine Runden" is the default because the user reads two rounds */
    expect(button(w, 'Meine Runden 2')!.attributes('aria-pressed')).toBe('true')
    expect(roundNames(w)).toEqual(['Monatsablesung Musterwerk', 'Wasser Quartal'])
    const first = w.findAll('.rd-round-row')[0]
    expect(sp(first.find('.rd-round-state').text())).toBe('Verspätet')
    expect(first.find('.rd-round-state').classes()).toContain('is-dueSoon')
    expect(first.text()).toContain('1 von 3')
    expect(first.text()).toContain('monatlich')
    expect(first.text()).toContain('Max Mustermann')
    expect(first.text()).toContain('01.11.2026')

    await button(w, 'Alle 4')!.trigger('click')
    expect(roundNames(w)).toEqual(['Monatsablesung Musterwerk', 'Wasser Quartal', 'Lager Nord jährlich', 'Alte Runde'])
    expect(w.findAll('.rd-round-row .rd-round-state').map((b) => sp(b.text()))).toEqual(['Verspätet', 'Erledigt', 'Eskaliert', 'Inaktiv'])
    expect(w.findAll('.rd-round-row .rd-round-state')[2].classes()).toContain('is-overdue')
    expect(w.findAll('.rd-round-row .rd-round-state')[3].classes()).toContain('is-none')
    /* no edit / delete without the right */
    expect(w.findAll('.rd-round-row')[3].find('.rd-round-edit').exists()).toBe(false)
    expect(w.findAll('.rd-round-row')[3].find('.rd-round-delete').exists()).toBe(false)

    await w.find('select').setValue('1002')
    expect(roundNames(w)).toEqual(['Lager Nord jährlich'])
    /* no create right at that site */
    await flushPromises()
    expect(button(w, 'Neue Runde')).toBeUndefined()
    await w.find('select').setValue('1001')
    await flushPromises()
    expect(button(w, 'Neue Runde')).toBeDefined()
  })

  it('reads a round in round order, skipping done meters on "Speichern & weiter"', async () => {
    const { wrapper: w, router, get, post } = await mountView('/readings?view=rounds')
    await byLabel(w, 'Runde „Monatsablesung Musterwerk“ ablesen')!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ round: '7001' })
    expect(get.mock.calls.some((c) => c[0] === '/web/readings?lang=de&round=7001')).toBe(true)
    expect(w.findAll('.rd-view')[0].attributes('aria-selected')).toBe('true')

    const banner = w.find('.rd-round-banner')
    expect(banner.text()).toContain('Monatsablesung Musterwerk')
    expect(banner.text()).toContain('Verspätet')
    expect(banner.text()).toContain('Stichtag 01.11.2026')
    expect(sp(w.find('.rd-head-count').text())).toBe('1 von 3 erledigt')
    /* no "Fällig" filter; round order with positions and done marks */
    expect(button(w, 'Fällig 2')).toBeUndefined()
    expect(w.findAll('.rd-row .rd-name').map((b) => sp(b.text()))).toEqual(['Wasser Kantine', 'Hauptzähler Strom', 'Unterzähler Halle 2'])
    expect(w.findAll('.rd-row .rd-pos-col').map((c) => sp(c.text()))).toEqual(['1', '2', '3'])
    expect(w.findAll('.rd-row')[1].find('.rd-done').text()).toBe('Erledigt')
    expect(w.findAll('.rd-row')[1].classes()).toContain('is-done')

    await w.findAll('.rd-row .rd-open')[0].trigger('click')
    const panel = w.find('.rd-panel-backdrop [role="dialog"]')
    expect(sp(panel.find('h2').text())).toBe('Wasser Kantine')
    expect(panel.find('.rd-save').attributes('title')).toBe('Speichern und „Unterzähler Halle 2“ öffnen')
    await panel.find('.rd-value').setValue('12500')
    await panel.find('form').trigger('submit')
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/web/readings/5103?lang=de')
    expect(sp(w.find('.rd-panel-backdrop [role="dialog"] h2').text())).toBe('Unterzähler Halle 2')

    await button(w, 'Abbrechen')!.trigger('click')
    await flushPromises()
    await button(w, 'Zurück zu den Runden')!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ view: 'rounds' })
    expect(roundNames(w).length).toBeGreaterThan(0)
  })

  it('opens the route sheet with the token in a new tab', async () => {
    const tab = { closed: false, close: vi.fn(), location: { href: '' }, document: { title: '', body: { textContent: '' } } }
    const open = vi.fn(() => tab)
    vi.stubGlobal('open', open)
    const createObjectURL = vi.fn(() => 'blob:sheet')
    const revoke = vi.fn()
    Object.assign(URL, { createObjectURL, revokeObjectURL: revoke })
    const { wrapper: w, getBlob } = await mountView('/readings?view=rounds')
    await byLabel(w, 'Laufzettel der Runde „Monatsablesung Musterwerk“ öffnen')!.trigger('click')
    await flushPromises()
    expect(open).toHaveBeenCalledWith('', '_blank')
    expect(getBlob).toHaveBeenCalledWith('/web/reading-rounds/7001/sheet.pdf?lang=de')
    expect(tab.location.href).toBe('blob:sheet')
    expect(tab.document.body.textContent).toBe('Laufzettel wird geladen …')
  })

  it('closes the tab and reports a failed route sheet', async () => {
    const tab = { closed: false, close: vi.fn(), location: { href: '' }, document: { title: '', body: { textContent: '' } } }
    vi.stubGlobal('open', vi.fn(() => tab))
    const getBlob = vi.fn(async () => {
      throw httpError(403)
    })
    const { wrapper: w } = await mountView('/readings?view=rounds', { getBlob })
    await byLabel(w, 'Laufzettel der Runde „Monatsablesung Musterwerk“ öffnen')!.trigger('click')
    await flushPromises()
    expect(tab.close).toHaveBeenCalled()
    expect(w.find('.rd-message').text()).toContain('Der Laufzettel konnte nicht geladen werden.')
  })

  it('creates a round with readers and meters in the chosen order', async () => {
    const post = vi.fn(async (_p: string, body: { name: string }) => round(7010, body.name, { mine: false }))
    const { wrapper: w, get } = await mountView('/readings?view=rounds', { post })
    await button(w, 'Neue Runde')!.trigger('click')
    await flushPromises()
    expect(sp(dialog(w).find('h2').text())).toBe('Neue Ableserunde')
    expect((field(w, 'site').element as HTMLSelectElement).value).toBe('1001')
    expect(dialog(w).findAll('#' + field(w, 'period').attributes('id') + ' option').map((o) => sp(o.text()))).toEqual([
      'wöchentlich',
      'alle 2 Wochen',
      'monatlich',
      'vierteljährlich',
      'halbjährlich',
      'jährlich',
    ])

    /* empty form: the rules are checked before sending */
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('Bitte einen Namen eingeben.')
    expect(dialog(w).text()).toContain('Bitte mindestens einen Ableser wählen.')
    expect(dialog(w).text()).toContain('Bitte mindestens einen Zähler hinzufügen.')
    expect(document.activeElement?.id).toBe(field(w, 'name').attributes('id'))

    await field(w, 'name').setValue('Wasser Quartal')
    await field(w, 'period').setValue('P1W')
    await field(w, 'leadDays').setValue('7')
    await field(w, 'readers').setValue('erika')
    await dialog(w).findAll('.rd-people')[0].find('.rd-people-match').trigger('click')
    expect(dialog(w).find('.rd-people-chosen').text()).toContain('Erika Beispiel')
    expect(dialog(w).find('.rd-people-warning').text()).toContain('Ohne E-Mail-Adresse')
    await field(w, 'supervisor').setValue('energie')
    await dialog(w).findAll('.rd-people')[1].find('.rd-people-match').trigger('click')
    const chief = dialog(w).findAll('.rd-people-chosen')[1].find('.rd-person')
    expect(chief.find('.rd-person-name').text()).toBe('Energieteam')
    expect(chief.find('.rd-person-kind').text()).toBe('Gruppe')

    /* meters: the automatic one is marked; add two, move the second up */
    expect(dialog(w).findAll('.rd-meter-match').find((b) => b.text().includes('Gas Heizung'))!.text()).toContain('automatisch')
    await byLabel(dialog(w), '„Hauptzähler Strom“ hinzufügen')!.trigger('click')
    await byLabel(dialog(w), '„Wasser Kantine“ hinzufügen')!.trigger('click')
    expect(dialog(w).findAll('.rd-order-item strong').map((s) => s.text())).toEqual(['Hauptzähler Strom', 'Wasser Kantine'])
    await byLabel(dialog(w), '„Wasser Kantine“ nach oben')!.trigger('click')
    await flushPromises()
    expect(dialog(w).findAll('.rd-order-item strong').map((s) => s.text())).toEqual(['Wasser Kantine', 'Hauptzähler Strom'])
    expect(document.activeElement?.getAttribute('aria-label')).toBe('„Wasser Kantine“ nach unten')
    expect(dialog(w).text()).toContain('„Wasser Kantine“ ist jetzt an Position 1 von 2.')

    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('Am Standort gibt es schon eine Runde mit diesem Namen.')
    expect(dialog(w).text()).toContain('Der Vorlauf muss kürzer als der Turnus sein (höchstens 6 Tage).')

    await field(w, 'name').setValue('Wochenrunde Halle')
    await field(w, 'leadDays').setValue('2')
    await field(w, 'remarks').setValue('  Schlüssel beim Pförtner ')
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0]).toEqual([
      '/web/reading-rounds?lang=de',
      {
        name: 'Wochenrunde Halle',
        enabled: true,
        meters: [5103, 5101],
        readers: [43],
        supervisor: [42],
        startDate: '2026-11-13',
        period: 'P1W',
        leadDays: 2,
        reminderDays: 3,
        escalationDays: 7,
        remarks: 'Schlüssel beim Pförtner',
        site: 1001,
      },
    ])
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.rd-message').text()).toBe('„Wochenrunde Halle“ wurde angelegt.')
    /* the new round is not the user's: "Alle" so that it is visible */
    expect(button(w, 'Alle 4')!.attributes('aria-pressed')).toBe('true')
    expect(get.mock.calls.filter((c) => c[0] === '/web/reading-rounds?lang=de')).toHaveLength(2)
  })

  it('reorders meters by drag and drop and removes them', async () => {
    const { wrapper: w } = await mountView('/readings?view=rounds')
    await byLabel(w, '„Monatsablesung Musterwerk“ bearbeiten')!.trigger('click')
    await flushPromises()
    const items = () => dialog(w).findAll('.rd-order-item')
    expect(items().map((i) => i.find('strong').text())).toEqual(['Wasser Kantine', 'Hauptzähler Strom', 'Unterzähler Halle 2'])
    await items()[0].trigger('dragstart')
    await items()[2].trigger('dragover')
    expect(items()[2].classes()).toContain('is-over')
    await items()[2].trigger('drop')
    expect(items().map((i) => i.find('strong').text())).toEqual(['Hauptzähler Strom', 'Unterzähler Halle 2', 'Wasser Kantine'])
    await byLabel(dialog(w), '„Unterzähler Halle 2“ aus der Runde entfernen')!.trigger('click')
    expect(items().map((i) => i.find('strong').text())).toEqual(['Hauptzähler Strom', 'Wasser Kantine'])
    expect(dialog(w).find('.rd-state').text()).toBe('Ungespeicherte Änderungen')

    /* closing asks first */
    await button(dialog(w), 'Abbrechen')!.trigger('click')
    await flushPromises()
    expect(w.text()).toContain('Die Änderungen wurden nicht gespeichert.')
    await button(w, 'Verwerfen')!.trigger('click')
    await flushPromises()
    expect(dialog(w).exists()).toBe(false)
  })

  it('handles a conflict by keeping the own changes on top of the current round', async () => {
    const current = round(7001, 'Monatsablesung Musterwerk', {
      version: 'v2',
      state: 'LATE',
      mine: true,
      readers: [
        { id: 41, name: 'Max Mustermann', kind: 'USER' },
        { id: 42, name: 'Energieteam', kind: 'GROUP' },
      ],
    })
    const put = vi.fn()
    put.mockRejectedValueOnce(httpError(409, current))
    put.mockResolvedValueOnce({ ...current, version: 'v3', remarks: 'Hof über Tor 2' })
    const { wrapper: w, get } = await mountView('/readings?view=rounds', { put })
    await byLabel(w, '„Monatsablesung Musterwerk“ bearbeiten')!.trigger('click')
    await flushPromises()
    expect(sp(dialog(w).find('h2').text())).toBe('„Monatsablesung Musterwerk“ bearbeiten')
    expect(field(w, 'site').exists()).toBe(false)
    await field(w, 'remarks').setValue('Hof über Tor 2')
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(put.mock.calls[0][0]).toBe('/web/reading-rounds/7001?lang=de')
    expect(put.mock.calls[0][1]).toMatchObject({ version: 'v1', remarks: 'Hof über Tor 2', readers: [41] })
    expect(dialog(w).find('.rd-conflict').text()).toContain('Inzwischen geändert')
    expect(get.mock.calls.filter((c) => c[0] === '/web/reading-rounds?lang=de')).toHaveLength(2)

    await button(dialog(w), 'Weiter bearbeiten')!.trigger('click')
    expect(dialog(w).find('.rd-people-chosen').text()).toContain('Energieteam')
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(put.mock.calls[1][1]).toMatchObject({ version: 'v2', remarks: 'Hof über Tor 2', readers: [41, 42] })
    expect(w.find('.rd-message').text()).toBe('„Monatsablesung Musterwerk“ wurde gespeichert.')
  })

  it('shows the server messages of a 400', async () => {
    const put = vi.fn(async () => {
      throw httpError(400, { messages: ['Zähler 5102 ist kein Messgerät des Standorts.'] })
    })
    const { wrapper: w } = await mountView('/readings?view=rounds', { put })
    await byLabel(w, '„Monatsablesung Musterwerk“ bearbeiten')!.trigger('click')
    await flushPromises()
    await field(w, 'name').setValue('Monatsablesung Werk')
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(dialog(w).find('.iso-messages').text()).toContain('Zähler 5102 ist kein Messgerät des Standorts.')
  })

  it('deletes a round with its version after asking', async () => {
    const { wrapper: w, del } = await mountView('/readings?view=rounds')
    await byLabel(w, '„Monatsablesung Musterwerk“ löschen')!.trigger('click')
    expect(w.text()).toContain('„Monatsablesung Musterwerk“ in den Papierkorb verschieben?')
    await w.find('[role="dialog"] .edit-submit').trigger('click')
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/reading-rounds/7001?version=v1')
    expect(w.find('.rd-message').text()).toBe('„Monatsablesung Musterwerk“ wurde in den Papierkorb verschoben.')
  })

  it('shows cards on phones', async () => {
    vi.stubGlobal('matchMedia', (q: string) => ({ matches: true, media: q, addEventListener: () => undefined, removeEventListener: () => undefined }))
    const { wrapper: w } = await mountView('/readings?view=rounds')
    const cards = w.findAll('.rd-round-card')
    expect(cards.map((c) => sp(c.find('h2').text()))).toEqual(['Monatsablesung Musterwerk', 'Wasser Quartal'])
    expect(cards[0].find('.rd-round-read').exists()).toBe(true)
    expect(cards[0].text()).toContain('1 von 3')
    expect(w.find('.rd-round-table').exists()).toBe(false)
  })
})
