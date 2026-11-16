/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ConfigurationEdit, ConfigurationInput, ContractList } from '../src/api/contracts'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import ContractsView from '../src/views/ContractsView.vue'
import { configuration, contractList } from './fixtures/accounting'
import { httpError } from './fixtures/edit'

/* Editing of accounting configurations (E6.3) on the contracts page with a mocked API; neutral placeholder data. */

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

const Stub = defineComponent({ render: () => h('div') })
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []
const V1 = '2026-11-01T10:00:00.000+01:00'
const V2 = '2026-11-16T09:00:00.000+01:00'

const TARGETS = [
  { id: 610, name: 'Abrechnung', path: 'Energieverträge', site: { id: 7100, name: 'Musterwerk' } },
  { id: 611, name: 'Abrechnung', path: 'Energieverträge', site: { id: 7101, name: 'Zweigwerk' } },
]

const TEMPLATES = [
  { id: 23960, name: 'Stromkosten', path: 'Berechnungen / Vorlagen', site: null },
  { id: 23961, name: 'Gaskosten', path: 'Berechnungen / Vorlagen', site: null },
]

function selector(key: string, overrides: Partial<ConfigurationInput> = {}): ConfigurationInput {
  return {
    key,
    variableNames: [key],
    objectClass: 'Clean Data',
    options: [
      { id: 501, name: 'Bereinigte Daten A', path: 'Musterwerk / Strom' },
      { id: 502, name: 'Bereinigte Daten B', path: 'Musterwerk / Strom' },
    ],
    selected: null,
    ...overrides,
  }
}

function model(overrides: Partial<ConfigurationEdit> = {}): ConfigurationEdit {
  return {
    id: 23950,
    name: 'Strom Musterwerk',
    version: V1,
    parentId: 610,
    contract: {
      contractNumber: 'V-1001',
      contractType: 'ELECTRICITY',
      marketLocation: 'DE0000000000000000000000000000001',
      contractDate: '2025-12-01',
      firstRate: '2026-01-01',
      periodOfNotice: '2027-09-30',
      start: '2026-01-01',
      end: '2027-12-31',
    },
    template: { id: 23960, name: 'Stromkosten', readable: true },
    inputs: [
      selector('i1', { variableNames: ['verbrauch'], selected: 501 }),
      selector('class:Energy Supply Contractor', {
        variableNames: ['arbeitspreis', 'grundpreis'],
        objectClass: 'Energy Supply Contractor',
        options: [{ id: 900, name: null, readable: false }],
        selected: 900,
      }),
      selector('i3', { variableNames: ['leistung'], selected: null }),
    ],
    unknownKeys: [],
    ...overrides,
  }
}

function editableList(): ContractList {
  const list = contractList()
  list.configurations = [configuration(23950, { canWrite: true, canDelete: true, version: V1, template: { id: 23960, name: 'Stromkosten', readable: true } })]
  list.configurationTargets = TARGETS
  list.canCreateConfiguration = true
  return list
}

const GAS_INPUTS = [selector('i1', { variableNames: ['verbrauch'] }), selector('i9', { variableNames: ['brennwert'] })]

function api(list: () => ContractList, edit: () => ConfigurationEdit = () => model()) {
  return vi.fn(async (path: string) => {
    if (path.startsWith('/web/contracts?')) return list()
    if (path.startsWith('/web/contracts/configurations/23950/edit?')) return edit()
    if (path.startsWith('/web/contracts/templates?')) return TEMPLATES
    if (path.startsWith('/web/contracts/templates/23961/inputs?')) return GAS_INPUTS
    if (path.startsWith('/web/contracts/templates/23960/inputs?')) return model().inputs.map((i) => ({ ...i, selected: null }))
    throw new Error('unexpected ' + path)
  })
}

let router: Router

async function mountView(http: Partial<Record<'get' | 'post' | 'put' | 'delete', Fn>>) {
  const auth = useAuthStore()
  Object.assign(auth.http, http)
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'contracts', component: Stub },
      { path: '/costs', name: 'costs', component: Stub },
    ],
  })
  await router.push('/')
  const wrapper = mount(ContractsView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

const panel = (w: VueWrapper) => w.find('.cfg-edit-backdrop [role="dialog"]')
const tabByText = (w: VueWrapper, text: string) => w.findAll('[role="tab"]').find((t) => t.text().includes(text))!

function field(w: VueWrapper, label: string) {
  const l = panel(w)
    .findAll('label')
    .find((x) => x.text().replace(/\s*\*$/, '').trim() === label)
  if (!l) throw new Error('no field ' + label)
  return w.find<HTMLInputElement>('#' + l.attributes('for'))
}

function bodyButton(text: string): HTMLButtonElement {
  const b = Array.from(document.body.querySelectorAll<HTMLButtonElement>('button')).find((x) => x.textContent?.trim() === text)
  if (!b) throw new Error('no button ' + text)
  return b
}

const selectorField = (w: VueWrapper, title: string) => panel(w).findAll('.ctr-input-field').find((f) => f.find('.ctr-input-title').text() === title)!

async function openConfigTab(w: VueWrapper) {
  await tabByText(w, 'Abrechnung').trigger('click')
}

async function openEdit(w: VueWrapper) {
  await openConfigTab(w)
  await w.find('.cfg-edit').trigger('click')
  await flushPromises()
}

async function submit(w: VueWrapper) {
  await panel(w).find('form.cfg-edit-form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
})

describe('contracts: accounting configurations (E6.3)', () => {
  it('without rights the configurations stay read-only', async () => {
    const w = await mountView({ get: api(contractList) })
    await openConfigTab(w)
    expect(w.find('.cfg-edit').exists()).toBe(false)
    expect(w.find('.cfg-new').exists()).toBe(false)
  })

  it('shows "Neu" and "Bearbeiten"; the hint no longer sends to the Control Center', async () => {
    const w = await mountView({ get: api(editableList) })
    expect(w.text()).toContain('Abgaben und Abrechnungen können hier angelegt')
    expect(w.text()).not.toContain('im Control Center gepflegt')
    await openConfigTab(w)
    expect(w.find('.cfg-new').attributes('aria-label')).toBe('Neue Abrechnung anlegen')
    expect(w.find('.cfg-edit').attributes('aria-label')).toBe('„Strom Musterwerk“ bearbeiten')
  })

  it('lists an empty configurations tab when configurations can be created', async () => {
    const list = editableList()
    list.configurations = []
    const w = await mountView({ get: api(() => list) })
    await openConfigTab(w)
    expect(w.text()).toContain('Noch keine Einträge')
    expect(w.find('.cfg-new').exists()).toBe(true)
  })

  it('opens the editor with fields, template and the input selectors', async () => {
    const get = api(editableList)
    const w = await mountView({ get })
    await openEdit(w)
    expect(get).toHaveBeenCalledWith('/web/contracts/configurations/23950/edit?lang=de')
    expect(get).toHaveBeenCalledWith('/web/contracts/templates?lang=de')
    expect(panel(w).find('h2').text()).toBe('„Strom Musterwerk“ bearbeiten')
    expect(field(w, 'Vertragsnummer').element.value).toBe('V-1001')
    expect(field(w, 'Energieart').element.value).toBe('ELECTRICITY')
    expect(field(w, 'Ende').element.value).toBe('2027-12-31')
    expect(field(w, 'Rechenvorlage').element.value).toBe('23960')
    expect(panel(w).find('.cfg-assigned').text()).toContain('2 von 3 Eingängen zugeordnet')

    const grouped = selectorField(w, 'arbeitspreis, grundpreis')
    expect(grouped.text()).toContain('Klasse: Energy Supply Contractor')
    expect(grouped.find('select option:checked').text()).toBe('Kein Leserecht')
    expect(grouped.find('.ctr-unassigned').exists()).toBe(false)
    expect(selectorField(w, 'leistung').find('.ctr-unassigned').text()).toBe('nicht zugeordnet')
    // the costs link of the saved configuration
    expect(panel(w).find('.cfg-costs-link').attributes('href')).toBe('/costs?id=23950')
  })

  it('saves the whole configuration with the version and shows the server warnings', async () => {
    const saved = model({ version: V2, contract: { ...model().contract, contractNumber: 'V-1002' }, warnings: ['Eingang „leistung“ ist nicht zugeordnet.'] })
    const put = vi.fn(async () => saved)
    const get = api(editableList)
    const w = await mountView({ get, put })
    await openEdit(w)

    await submit(w)
    expect(put).not.toHaveBeenCalled()
    expect(panel(w).text()).toContain('Keine Änderungen.')

    await field(w, 'Vertragsnummer').setValue('V-1002')
    // search narrows the options; the chosen one stays
    const sel = selectorField(w, 'verbrauch')
    await sel.find('input[type="search"]').setValue(' daten b')
    expect(sel.findAll('select option').map((o) => o.text())).toEqual([
      '– nicht zugeordnet –',
      'Bereinigte Daten A – Musterwerk / Strom',
      'Bereinigte Daten B – Musterwerk / Strom',
    ])
    await sel.find('select').setValue('502')
    await selectorField(w, 'leistung').find('select').setValue('501')
    expect(panel(w).find('.reg-edit-state').text()).toBe('Ungespeicherte Änderungen')
    await submit(w)
    expect(put).toHaveBeenCalledWith('/web/contracts/configurations/23950?lang=de', {
      version: V1,
      contract: {
        contractNumber: 'V-1002',
        marketLocation: 'DE0000000000000000000000000000001',
        contractType: 'ELECTRICITY',
        contractDate: '2025-12-01',
        firstRate: '2026-01-01',
        periodOfNotice: '2027-09-30',
        start: '2026-01-01',
        end: '2027-12-31',
      },
      templateId: 23960,
      selections: { i1: 502, 'class:Energy Supply Contractor': 900, i3: 501 },
    })
    // the panel stays open with the warnings, the list is reloaded
    expect(panel(w).exists()).toBe(true)
    expect(panel(w).find('.cfg-warnings').text()).toContain('Eingang „leistung“ ist nicht zugeordnet.')
    expect(panel(w).find('.reg-edit-state').text()).toBe('Gespeichert')
    expect(w.find('.ctr-message').text()).toBe('„Strom Musterwerk“ wurde gespeichert.')
    expect(get.mock.calls.filter((c) => c[0] === '/web/contracts?lang=de')).toHaveLength(2)
  })

  it('checks the dates before the request: end not before start', async () => {
    const put = vi.fn()
    const w = await mountView({ get: api(editableList), put })
    await openEdit(w)
    await field(w, 'Ende').setValue('2025-12-31')
    await submit(w)
    expect(put).not.toHaveBeenCalled()
    expect(panel(w).text()).toContain('Das Ende darf nicht vor dem Beginn liegen.')
    expect(field(w, 'Ende').attributes('aria-invalid')).toBe('true')
  })

  it('changing the template asks first and keeps the selections whose key exists', async () => {
    const put = vi.fn(async () => model())
    const get = api(editableList)
    const w = await mountView({ get, put })
    await openEdit(w)
    const tpl = field(w, 'Rechenvorlage')

    await tpl.setValue('23961')
    await flushPromises()
    expect(document.body.textContent).toContain('Rechenvorlage wechseln?')
    bodyButton('Abbrechen').click()
    await flushPromises()
    expect(tpl.element.value).toBe('23960')
    expect(get.mock.calls.some((c) => String(c[0]).startsWith('/web/contracts/templates/23961/'))).toBe(false)

    await tpl.setValue('23961')
    await flushPromises()
    bodyButton('Wechseln').click()
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/contracts/templates/23961/inputs?lang=de&configuration=23950')
    expect(tpl.element.value).toBe('23961')
    expect(panel(w).findAll('.ctr-input-title').map((x) => x.text())).toEqual(['verbrauch', 'brennwert'])
    expect((selectorField(w, 'verbrauch').find('select').element as HTMLSelectElement).value).toBe('501')
    await submit(w)
    expect((put.mock.calls[0] as unknown[])[1]).toMatchObject({ templateId: 23961, selections: { i1: 501, i9: null } })
  })

  it('a 409 offers "Neu laden" and "Weiter bearbeiten"', async () => {
    const newer = model({ version: V2, contract: { ...model().contract, end: '2028-12-31' } })
    const put = vi.fn().mockRejectedValueOnce(httpError(409, newer)).mockResolvedValueOnce(newer)
    const get = api(editableList)
    const w = await mountView({ get, put })
    await openEdit(w)
    await field(w, 'Vertragsnummer').setValue('V-2000')
    await submit(w)
    expect(panel(w).find('.reg-conflict').text()).toContain('Inzwischen geändert')

    await panel(w).find('.reg-conflict-keep').trigger('click')
    expect(field(w, 'Vertragsnummer').element.value).toBe('V-2000')
    expect(field(w, 'Ende').element.value).toBe('2028-12-31')
    await submit(w)
    expect(put.mock.calls[1][1]).toMatchObject({ version: V2, contract: { contractNumber: 'V-2000', end: '2028-12-31' } })
  })

  it('"Neu laden" after a 409 shows the current version', async () => {
    const newer = model({ version: V2, contract: { ...model().contract, contractNumber: 'V-3000' } })
    const put = vi.fn().mockRejectedValueOnce(httpError(409, newer))
    const w = await mountView({ get: api(editableList), put })
    await openEdit(w)
    await field(w, 'Vertragsnummer').setValue('V-2000')
    await submit(w)
    await panel(w).find('.reg-conflict-reload').trigger('click')
    expect(field(w, 'Vertragsnummer').element.value).toBe('V-3000')
    expect(panel(w).text()).toContain('Die aktuelle Fassung wurde geladen.')
  })

  it('creates a configuration with folder, name and template; the panel then offers the cost calculation', async () => {
    const created = model({ id: 23955, name: 'Gas Musterwerk', template: { id: 23961, name: 'Gaskosten', readable: true }, inputs: GAS_INPUTS })
    const post = vi.fn(async () => created)
    const get = api(editableList)
    const w = await mountView({ get, post })
    await openConfigTab(w)
    await w.find('.cfg-new').trigger('click')
    await flushPromises()
    expect(panel(w).find('h2').text()).toBe('Neue Abrechnung')
    expect(panel(w).find('.cfg-costs-link').exists()).toBe(false)
    expect(panel(w).text()).toContain('Bitte zuerst eine Rechenvorlage wählen.')

    await submit(w)
    expect(post).not.toHaveBeenCalled()
    expect(panel(w).text()).toContain('Bitte einen Ordner wählen.')
    expect(panel(w).text()).toContain('Bitte einen Namen eingeben.')
    expect(panel(w).text()).toContain('Bitte eine Rechenvorlage wählen.')

    await field(w, 'Ordner').setValue('611')
    await field(w, 'Name').setValue(' Gas Musterwerk ')
    await field(w, 'Energieart').setValue('GAS')
    await field(w, 'Beginn').setValue('2027-01-01')
    // the first template needs no question
    await field(w, 'Rechenvorlage').setValue('23961')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/contracts/templates/23961/inputs?lang=de')
    await selectorField(w, 'brennwert').find('select').setValue('502')
    await submit(w)
    expect(post).toHaveBeenCalledWith('/web/contracts/configurations?lang=de', {
      parentId: 611,
      name: 'Gas Musterwerk',
      contract: {
        contractNumber: null,
        marketLocation: null,
        contractType: 'GAS',
        contractDate: null,
        firstRate: null,
        periodOfNotice: null,
        start: '2027-01-01',
        end: null,
      },
      templateId: 23961,
      selections: { i1: null, i9: 502 },
    })
    expect(panel(w).find('h2').text()).toBe('„Gas Musterwerk“ bearbeiten')
    expect(panel(w).find('.cfg-costs-link').attributes('href')).toBe('/costs?id=23955')
    expect(w.find('.ctr-message').text()).toBe('„Gas Musterwerk“ wurde angelegt.')
  })

  it('deletes after a question with the version', async () => {
    const del = vi.fn(async () => undefined)
    const w = await mountView({ get: api(editableList), delete: del })
    await openEdit(w)
    await panel(w).find('.cfg-delete').trigger('click')
    expect(document.body.textContent).toContain('Abrechnung löschen')
    expect(del).not.toHaveBeenCalled()
    const confirm = Array.from(document.body.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')).filter((b) => b.textContent?.trim() === 'Löschen').at(-1)!
    confirm.click()
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/contracts/configurations/23950?version=2026-11-01T10%3A00%3A00.000%2B01%3A00')
    expect(panel(w).exists()).toBe(false)
    expect(w.find('.ctr-message').text()).toBe('„Strom Musterwerk“ wurde in den Papierkorb verschoben.')
  })

  it('asks before unsaved changes are dropped', async () => {
    const w = await mountView({ get: api(editableList) })
    await openEdit(w)
    await field(w, 'Marktlokation').setValue('DE0000000000000000000000000000002')
    await panel(w).find('.cfg-close').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('Die Änderungen wurden nicht gespeichert.')
    bodyButton('Verwerfen').click()
    await flushPromises()
    expect(panel(w).exists()).toBe(false)
  })

  it('a configuration with only the delete right is shown read-only', async () => {
    const list = editableList()
    list.configurations[0].canWrite = false
    const w = await mountView({ get: api(() => list) })
    await openEdit(w)
    expect(field(w, 'Vertragsnummer').attributes('readonly')).toBeDefined()
    expect(panel(w).find('.edit-submit').exists()).toBe(false)
    expect(panel(w).find('.cfg-delete').exists()).toBe(true)
  })
})
