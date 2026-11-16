/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ContractHistory, ContractList, ContractObject } from '../src/api/contracts'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import ContractsView from '../src/views/ContractsView.vue'
import { attribute, contractList, contractObject } from './fixtures/accounting'
import { httpError } from './fixtures/edit'

/* Editing of contract partners, prices and dues (E6.2) with a mocked API; neutral placeholder data. */

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

const Stub = defineComponent({ render: () => h('div') })
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []
const V1 = '2026-01-01T00:00:00.000+01:00'
const V2 = '2026-11-16T10:00:00.000+01:00'

const rights = { canWrite: true, canDelete: true }

function supplier(overrides: Partial<ContractObject> = {}): ContractObject {
  return contractObject(23901, { ...rights, version: V1, contractorClass: 'Energy Supply Contractor', ...overrides })
}

function gridOperator(overrides: Partial<ContractObject> = {}): ContractObject {
  return contractObject(23920, {
    name: 'Netzbetreiber Süd',
    className: 'Energy Grid Operator',
    classLabel: 'Netzbetreiber',
    contractor: null,
    contractorClass: 'Energy Grid Operation Contractor',
    version: V1,
    ...rights,
    attributes: [
      attribute('Demand Charge', {
        label: 'Leistungspreis',
        type: 'ranging',
        value: [{ from: 0, to: 1e9, value: 12.5 }],
        unit: '€/kW',
        inputUnit: '€/kW',
        timestamp: V1,
      }),
    ],
    ...overrides,
  })
}

const TARGETS = [
  { id: 600, name: 'Energielieferung', path: 'Energieverträge', site: { id: 7100, name: 'Musterwerk' } },
  { id: 601, name: 'Energielieferung', path: 'Energieverträge', site: { id: 7101, name: 'Zweigwerk' } },
]

/** The list with rights: suppliers (one editable object), grid operators, contractors, an empty creatable dues group. */
function editableList(overrides: { supplier?: Partial<ContractObject>; grid?: Partial<ContractObject> } = {}): ContractList {
  const list = contractList()
  list.groups[0] = {
    kind: 'suppliers',
    objects: [supplier(overrides.supplier)],
    canCreate: true,
    createTargets: TARGETS,
    classes: [
      { class: 'Electricity Supply Contractor', label: 'Stromlieferant' },
      { class: 'Gas Supply Contractor', label: 'Gaslieferant' },
    ],
  }
  list.groups[1] = { kind: 'gridOperators', objects: [gridOperator(overrides.grid)] }
  list.groups.push({
    kind: 'dues',
    objects: [],
    canCreate: true,
    createTargets: [TARGETS[0]],
    classes: [{ class: 'Governmental Dues', label: 'Abgaben' }],
  })
  return list
}

const CONTRACTORS = [
  { id: 23910, name: 'Energieversorger Nord', path: 'Energieverträge / Partner' },
  { id: 23911, name: 'Stadtwerke Muster', path: 'Energieverträge / Partner' },
]

const HISTORY: ContractHistory = {
  unit: '€/kWh',
  type: 'number',
  samples: [
    { timestamp: '2026-01-01T00:00:00.000+01:00', value: 0.28, canDelete: true },
    { timestamp: '2025-01-01T00:00:00.000+01:00', value: 0.31, canDelete: false },
  ],
}

function api(list: () => ContractList) {
  return vi.fn(async (path: string) => {
    if (path.startsWith('/web/contracts?')) return list()
    if (path.startsWith('/web/contracts/contractors?')) return CONTRACTORS
    if (/^\/web\/contracts\/objects\/\d+\/history\?/.test(path)) return HISTORY
    throw new Error('unexpected ' + path)
  })
}

async function mountView(http: Partial<Record<'get' | 'post' | 'put' | 'delete', Fn>>) {
  const auth = useAuthStore()
  Object.assign(auth.http, http)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }] })
  await router.push('/')
  const wrapper = mount(ContractsView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

const dialogs = () => Array.from(document.body.querySelectorAll<HTMLElement>('[role="dialog"]'))
const panel = (w: VueWrapper) => w.find('.ctr-edit-backdrop [role="dialog"]')
const tabByText = (w: VueWrapper, text: string) => w.findAll('[role="tab"]').find((t) => t.text().includes(text))!

function field(w: VueWrapper, label: string) {
  const l = w.findAll('[role="dialog"] label').find((x) => x.text().replace(/\s*\*$/, '').replace(/\s*\[.*\]$/, '').trim() === label)
  if (!l) throw new Error('no field ' + label)
  return w.find<HTMLInputElement>('#' + l.attributes('for'))
}

function bodyButton(text: string): HTMLButtonElement {
  const b = Array.from(document.body.querySelectorAll<HTMLButtonElement>('button')).find((x) => x.textContent?.trim() === text)
  if (!b) throw new Error('no button ' + text)
  return b
}

async function openEdit(w: VueWrapper, index = 0) {
  await w.findAll('.ctr-card')[index].find('.ctr-edit').trigger('click')
  await flushPromises()
}

async function submit(w: VueWrapper) {
  await panel(w).find('form.ctr-edit-form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
  vi.useRealTimers()
})

describe('contracts: editing (E6.2)', () => {
  it('without rights there are no edit controls and the read-only notice stays', async () => {
    const w = await mountView({ get: api(contractList) })
    expect(w.find('.ctr-edit').exists()).toBe(false)
    expect(w.find('.ctr-new').exists()).toBe(false)
    expect(w.text()).toContain('Nur Ansicht')
  })

  it('shows "Neu", "Bearbeiten" and an empty creatable group with rights', async () => {
    const w = await mountView({ get: api(() => editableList()) })
    expect(w.text()).not.toContain('Nur Ansicht')
    expect(w.text()).toContain('soweit Ihre Rechte reichen')
    expect(w.find('.ctr-new').attributes('aria-label')).toBe('Neuen Eintrag in „Lieferanten“ anlegen')
    expect(w.findAll('.ctr-card')[0].find('.ctr-edit').exists()).toBe(true)
    await tabByText(w, 'Abgaben').trigger('click')
    expect(w.text()).toContain('Noch keine Einträge')
    expect(w.find('.ctr-new').exists()).toBe(true)
    await tabByText(w, 'Vertragspartner').trigger('click')
    expect(w.find('.ctr-edit').exists()).toBe(false)
  })

  it('creates an object with class, folder and name, then opens it in the editor', async () => {
    let list = editableList()
    const created = supplier({ id: 23905, name: 'Gas Nord', className: 'Gas Supply Contractor', classLabel: 'Gaslieferant', version: null })
    const post = vi.fn(async () => {
      list = editableList()
      list.groups[0].objects.push(created)
      return created
    })
    const get = api(() => list)
    const w = await mountView({ get, post })
    await w.find('.ctr-new').trigger('click')
    await flushPromises()
    expect(w.find('[role="dialog"] h2').text()).toBe('Neu: Lieferanten')
    // two folders: a choice is needed
    await w.find('[role="dialog"] form').trigger('submit')
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
    expect(w.text()).toContain('Bitte einen Ordner wählen.')
    expect(w.text()).toContain('Bitte einen Namen eingeben.')

    await field(w, 'Art').setValue('Gas Supply Contractor')
    await field(w, 'Ordner').setValue('601')
    await field(w, 'Name').setValue('  Gas Nord ')
    await w.find('[role="dialog"] form').trigger('submit')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/contracts/objects?lang=de', { parentId: 601, class: 'Gas Supply Contractor', name: 'Gas Nord' })
    expect(w.find('.ctr-message').text()).toBe('„Gas Nord“ wurde angelegt.')
    expect(get.mock.calls.filter((c) => c[0] === '/web/contracts?lang=de')).toHaveLength(2)
    expect(panel(w).find('h2').text()).toBe('„Gas Nord“ bearbeiten')
  })

  it('a create 400 lists the server messages', async () => {
    const post = vi.fn().mockRejectedValue(httpError(400, { messages: ['Der Name ist schon vergeben.'] }))
    const w = await mountView({ get: api(() => editableList()), post })
    await tabByText(w, 'Abgaben').trigger('click')
    await w.find('.ctr-new').trigger('click')
    await flushPromises()
    expect(w.find('[role="dialog"]').text()).toContain('Abgaben')
    await field(w, 'Name').setValue('EEG-Umlage')
    await w.find('[role="dialog"] form').trigger('submit')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/contracts/objects?lang=de', { parentId: 600, class: 'Governmental Dues', name: 'EEG-Umlage' })
    expect(w.find('[role="dialog"] .iso-messages li').text()).toBe('Der Name ist schon vergeben.')
  })

  it('saves name, contractor and texts with the version, only the changed parts', async () => {
    const put = vi.fn(async () => supplier({ name: 'Strom Nord' }))
    const get = api(() => editableList())
    const w = await mountView({ get, put })
    await openEdit(w)
    expect(get).toHaveBeenCalledWith('/web/contracts/contractors?class=Energy+Supply+Contractor')
    expect(panel(w).text()).toContain('Stromlieferant')

    // nothing changed: no request
    await submit(w)
    expect(put).not.toHaveBeenCalled()
    expect(panel(w).text()).toContain('Keine Änderungen.')

    await field(w, 'Name').setValue('Strom Nord')
    await field(w, 'Kundennummer').setValue('K-0001')
    // search narrows the contractor list; the chosen one stays
    const search = panel(w).find<HTMLInputElement>('input[type="search"]')
    await search.setValue('stadtwerke')
    const select = panel(w).find<HTMLSelectElement>('.ctr-contractor-field select')
    expect(select.findAll('option').map((o) => o.text())).toEqual([
      '– kein Vertragspartner –',
      'Energieversorger Nord – Energieverträge / Partner',
      'Stadtwerke Muster – Energieverträge / Partner',
    ])
    await select.setValue('23911')
    expect(panel(w).find('.reg-edit-state').text()).toBe('Ungespeicherte Änderungen')
    await submit(w)
    expect(put).toHaveBeenCalledWith('/web/contracts/objects/23901?lang=de', {
      version: V1,
      name: 'Strom Nord',
      contractor: 23911,
      fields: { 'Customer Number': 'K-0001' },
    })
    expect(dialogs()).toHaveLength(0)
    expect(w.find('.ctr-message').text()).toBe('„Strom Nord“ wurde gespeichert.')
  })

  it('edits a ranging table: checks before the request, "unbegrenzt" is sent as 1e9', async () => {
    const put = vi.fn(async () => gridOperator())
    const w = await mountView({ get: api(() => editableList()), put })
    await tabByText(w, 'Netzbetreiber').trigger('click')
    await openEdit(w)
    const table = () => panel(w).find('.ctr-rng-table')
    expect(table().findAll('tbody tr')).toHaveLength(1)
    const unlimited = table().find<HTMLInputElement>('.ctr-rng-unlimited input')
    expect(unlimited.element.checked).toBe(true)
    await unlimited.setValue(false)
    await table().find('input[aria-label="Bis, Zeile 1"]').setValue('100')
    await panel(w).find('.ctr-rng-add').trigger('click')
    await flushPromises()
    expect(table().findAll('input[aria-label="Von, Zeile 2"]')).toHaveLength(1)
    expect(table().find<HTMLInputElement>('input[aria-label="Von, Zeile 2"]').element.value).toBe('100')
    // the new last row: value missing, from overlapping
    await table().find('input[aria-label="Von, Zeile 2"]').setValue('50')
    await submit(w)
    expect(put).not.toHaveBeenCalled()
    const problems = table().find('.ctr-rng-problem-row').text()
    expect(problems).toContain('Zeile 2:')
    expect(problems).toContain('Bitte alle Felder ausfüllen.')

    await table().find('input[aria-label="Von, Zeile 2"]').setValue('100')
    await table().find('input[aria-label="Bis, Zeile 2"]').setValue('80')
    await table().find('input[aria-label="Wert, Zeile 2"]').setValue('10,5')
    await submit(w)
    expect(put).not.toHaveBeenCalled()
    expect(table().text()).toContain('„Bis“ muss größer als „Von“ sein.')

    await panel(w).find('.ctr-rng-unlimited input').setValue(true)
    await submit(w)
    expect(put).toHaveBeenCalledWith('/web/contracts/objects/23920?lang=de', {
      version: V1,
      fields: {
        'Demand Charge': [
          { from: 0, to: 100, value: 12.5 },
          { from: 100, to: 1e9, value: 10.5 },
        ],
      },
    })
  })

  it('removes a ranging row; the last row cannot be checked away without an error', async () => {
    const put = vi.fn()
    const w = await mountView({ get: api(() => editableList()), put })
    await tabByText(w, 'Netzbetreiber').trigger('click')
    await openEdit(w)
    await panel(w).find('.ctr-rng-remove').trigger('click')
    await submit(w)
    expect(put).not.toHaveBeenCalled()
    expect(panel(w).text()).toContain('Bitte mindestens eine Zeile eintragen.')
  })

  it('writes a new price with comma and the 1st of next month; an overwrite needs "Trotzdem speichern"', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 16, 10, 0))
    const post = vi
      .fn()
      .mockRejectedValueOnce(httpError(409, { warnings: ['Für 01.12.2026 gibt es schon einen Wert: 0,26 – überschreiben?'] }))
      .mockResolvedValueOnce({ timestamp: '2026-12-01T00:00:00.000+01:00', value: 0.2875 })
    const get = api(() => editableList())
    const w = await mountView({ get, post })
    await openEdit(w)
    const price = panel(w).findAll('.ctr-price')[0]
    expect(price.find('h4').text()).toBe('Arbeitspreis HT')
    expect(price.find('.ctr-price-current').text()).toContain('0,28 €/kWh')
    expect(price.find('.ctr-price-current').text()).toContain('ab 01.01.2026')
    const from = price.find<HTMLInputElement>('input[type="date"]')
    expect(from.element.value).toBe('2026-12-01')

    // invalid number: no request
    await price.find('input[inputmode="decimal"]').setValue('0,28x')
    await price.find('form').trigger('submit')
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
    expect(price.text()).toContain('Bitte eine Zahl eingeben')

    await price.find('input[inputmode="decimal"]').setValue('0,2875')
    expect(panel(w).find('.ctr-close').text()).toBe('Schließen')
    await price.find('form').trigger('submit')
    await flushPromises()
    expect(post).toHaveBeenLastCalledWith('/web/contracts/objects/23901/values?lang=de', {
      attribute: 'Energy Price HT',
      validFrom: '2026-12-01',
      value: 0.2875,
      confirm: false,
    })
    expect(dialogs()).toHaveLength(2)
    expect(dialogs()[1].textContent).toContain('schon einen Wert: 0,26')
    bodyButton('Trotzdem speichern').click()
    await flushPromises()
    expect(post).toHaveBeenLastCalledWith('/web/contracts/objects/23901/values?lang=de', {
      attribute: 'Energy Price HT',
      validFrom: '2026-12-01',
      value: 0.2875,
      confirm: true,
    })
    expect(dialogs()).toHaveLength(1)
    expect(price.find('.ctr-price-done').text()).toBe('0,2875 €/kWh ab 01.12.2026 gespeichert.')
    expect(get.mock.calls.filter((c) => c[0] === '/web/contracts?lang=de')).toHaveLength(2)
    expect(panel(w).find('.reg-edit-state').text()).toBe('Werte gespeichert')
  })

  it('LONG attributes need an integer', async () => {
    const post = vi.fn()
    const list = editableList({
      supplier: { attributes: [attribute('Meter Count', { label: 'Zähleranzahl', value: 2, primitive: 'LONG', timestamp: V1 })] },
    })
    const w = await mountView({ get: api(() => list), post })
    await openEdit(w)
    const price = panel(w).find('.ctr-price')
    await price.find('input[inputmode="numeric"]').setValue('2,5')
    await price.find('form').trigger('submit')
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
    expect(price.text()).toContain('Bitte eine ganze Zahl eingeben.')
  })

  it('shows the history and deletes a value after the question', async () => {
    const del = vi.fn(async () => undefined)
    const get = api(() => editableList())
    const w = await mountView({ get, delete: del })
    await openEdit(w)
    const price = panel(w).findAll('.ctr-price')[0]
    await price.find('.ctr-price-history-btn').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/contracts/objects/23901/history?attribute=Energy+Price+HT&limit=100')
    const rows = price.findAll('.ctr-price-history tbody tr')
    expect(rows.map((r) => r.text())).toEqual(['01.01.20260,28 €/kWhLöschen', '01.01.20250,31 €/kWh'])
    await rows[0].find('.ctr-price-delete').trigger('click')
    await flushPromises()
    expect(dialogs()[1].textContent).toContain('Den Wert 0,28 €/kWh von „Arbeitspreis HT“ ab 01.01.2026 löschen?')
    dialogs()[1].querySelector<HTMLButtonElement>('.edit-submit')!.click()
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/contracts/objects/23901/values?attribute=Energy+Price+HT&timestamp=2026-01-01T00%3A00%3A00.000%2B01%3A00')
    expect(dialogs()).toHaveLength(1)
    expect(price.text()).toContain('Der Wert ab 01.01.2026 wurde gelöscht.')
    expect(get.mock.calls.filter((c) => c[0] === '/web/contracts?lang=de')).toHaveLength(2)
  })

  it('without write right: no "Neuer Wert", no save; delete stays', async () => {
    const w = await mountView({ get: api(() => editableList({ supplier: { canWrite: false } })) })
    await openEdit(w)
    expect(panel(w).find('.ctr-price-new').exists()).toBe(false)
    expect(panel(w).find('.edit-submit').exists()).toBe(false)
    expect(panel(w).find('.ctr-contractor-field').exists()).toBe(false)
    expect(panel(w).find('.ctr-delete').exists()).toBe(true)
  })

  it('deletes after the question; when still used, the list and "Trotzdem löschen"', async () => {
    const del = vi
      .fn()
      .mockRejectedValueOnce(
        httpError(409, {
          usedBy: [
            { id: 23950, name: 'Strom Musterwerk', kind: 'configuration' },
            { id: 23930, name: 'Netzbetreiber West', kind: 'object' },
          ],
        }),
      )
      .mockResolvedValueOnce(undefined)
    const get = api(() => editableList())
    const w = await mountView({ get, delete: del })
    await openEdit(w)
    await panel(w).find('.ctr-delete').trigger('click')
    await flushPromises()
    expect(dialogs()[1].textContent).toContain('in den Papierkorb verschieben?')
    dialogs()[1].querySelector<HTMLButtonElement>('.edit-submit')!.click()
    await flushPromises()
    expect(del).toHaveBeenLastCalledWith('/web/contracts/objects/23901?version=2026-01-01T00%3A00%3A00.000%2B01%3A00&confirm=false')
    expect(dialogs()[1].textContent).toContain('wird noch verwendet von:')
    expect(Array.from(dialogs()[1].querySelectorAll('.ctr-used-by li')).map((l) => l.textContent)).toEqual([
      'Strom Musterwerk (Abrechnung)',
      'Netzbetreiber West (Vertragsobjekt)',
    ])
    bodyButton('Trotzdem löschen').click()
    await flushPromises()
    expect(del).toHaveBeenLastCalledWith('/web/contracts/objects/23901?version=2026-01-01T00%3A00%3A00.000%2B01%3A00&confirm=true')
    expect(dialogs()).toHaveLength(0)
    expect(w.find('.ctr-message').text()).toBe('„Strom - Energieversorger Nord“ wurde in den Papierkorb verschoben.')
  })

  it('a delete with an outdated version: message in the panel and reload', async () => {
    const del = vi.fn().mockRejectedValue(httpError(409, supplier({ version: V2 })))
    const get = api(() => editableList())
    const w = await mountView({ get, delete: del })
    await openEdit(w)
    await panel(w).find('.ctr-delete').trigger('click')
    await flushPromises()
    dialogs()[1].querySelector<HTMLButtonElement>('.edit-submit')!.click()
    await flushPromises()
    expect(dialogs()).toHaveLength(1)
    expect(panel(w).text()).toContain('wurde inzwischen geändert und nicht gelöscht')
    expect(get.mock.calls.filter((c) => c[0] === '/web/contracts?lang=de')).toHaveLength(2)
  })

  it('409 on save: "Neu laden" takes the current object, "Weiter bearbeiten" keeps own changes with the new version', async () => {
    const currentObject = supplier({
      version: V2,
      attributes: supplier().attributes.map((a) => (a.name === 'Customer Number' ? { ...a, value: 'K-0002' } : a)),
    })
    const put = vi.fn().mockRejectedValueOnce(httpError(409, currentObject)).mockRejectedValueOnce(httpError(409, currentObject)).mockResolvedValueOnce(currentObject)
    const w = await mountView({ get: api(() => editableList()), put })
    await openEdit(w)
    await field(w, 'Kundennummer').setValue('K-0009')
    await submit(w)
    expect(panel(w).find('.reg-conflict').text()).toContain('Inzwischen geändert')
    expect(panel(w).find('.edit-submit').attributes('disabled')).toBeDefined()
    await panel(w).find('.reg-conflict-reload').trigger('click')
    expect(field(w, 'Kundennummer').element.value).toBe('K-0002')

    await field(w, 'Name').setValue('Strom Nord')
    await submit(w)
    await panel(w).find('.reg-conflict-keep').trigger('click')
    expect(field(w, 'Name').element.value).toBe('Strom Nord')
    await submit(w)
    expect(put).toHaveBeenLastCalledWith('/web/contracts/objects/23901?lang=de', { version: V2, name: 'Strom Nord' })
    expect(dialogs()).toHaveLength(0)
  })

  it('asks before unsaved changes are dropped, also for a typed new value', async () => {
    const w = await mountView({ get: api(() => editableList()) })
    await openEdit(w)
    await field(w, 'Kundennummer').setValue('K-0009')
    expect(panel(w).find('.ctr-close').text()).toBe('Abbrechen')
    await panel(w).find('.ctr-close').trigger('click')
    await flushPromises()
    expect(dialogs()).toHaveLength(2)
    expect(dialogs()[1].textContent).toContain('Die Änderungen wurden nicht gespeichert.')
    dialogs()[1].querySelectorAll<HTMLButtonElement>('button.edit-btn')[0].click()
    await flushPromises()
    expect(dialogs()).toHaveLength(1)
    await field(w, 'Kundennummer').setValue('K-4711')
    await panel(w).findAll('.ctr-price')[0].find('input[inputmode="decimal"]').setValue('0,3')
    await flushPromises()
    await panel(w).find('.ctr-close').trigger('click')
    await flushPromises()
    expect(dialogs()).toHaveLength(2)
    dialogs()[1].querySelector<HTMLButtonElement>('.edit-submit')!.click()
    await flushPromises()
    expect(dialogs()).toHaveLength(0)
  })

  it('"Vertragspartner anzeigen" closes the panel and jumps to the contractor', async () => {
    const w = await mountView({ get: api(() => editableList()) })
    await openEdit(w)
    await panel(w).find('.ctr-contractor-show').trigger('click')
    await flushPromises()
    expect(dialogs()).toHaveLength(0)
    expect(tabByText(w, 'Vertragspartner').attributes('aria-selected')).toBe('true')
    expect(w.findAll('.ctr-card')[0].classes()).toContain('is-highlighted')
  })
})
