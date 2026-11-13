/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { RegisterKind, RegisterList, RegisterRow } from '../src/api/registers'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import RegisterView from '../src/views/RegisterView.vue'
import { httpError } from './fixtures/edit'
import { baseData, column, equipment, group, meter, registerList } from './fixtures/registers'

/* Editing of meters and equipment (E5) with a mocked API; neutral placeholder data ("Musterwerk"). */

const Stub = defineComponent({ render: () => h('div') })
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []
const V1 = '2026-11-01T10:00:00.000+01:00'
const V2 = '2026-11-05T08:30:00.000+01:00'
const V3 = '2026-11-12T09:00:00.000+01:00'

const meterColumns = [
  column('Serial Number', { name: 'Seriennummer', primitiveType: 'STRING', guiType: 'BASIC_TEXT', editable: true, inputUnit: null }),
  column('Factor', { name: 'Faktor', type: 'NUMBER', primitiveType: 'DOUBLE', editable: true, inputUnit: 'kWh/Imp' }),
  column('Calibrated', { name: 'Geeicht', type: 'BOOLEAN', primitiveType: 'BOOLEAN', editable: true }),
  column('Installation Date', { name: 'Einbaudatum', type: 'DATE', primitiveType: 'STRING', guiType: 'BASIC_TEXT_DATE_FULL', editable: true }),
  column('Online ID', { name: 'Datenpunkt', type: 'TARGET', primitiveType: 'TARGET', guiType: 'TARGET_OBJECT', editable: true }),
  column('Picture', { name: 'Bild', type: 'FILE', primitiveType: 'FILE', editable: true }),
  column('Location GPS', { name: 'GPS', primitiveType: 'STRING', guiType: 'GPS', editable: false }),
]

const rights = { canWrite: true, canDelete: true, canUpload: true, canEnter: true }

function meterRow(overrides: Partial<RegisterRow> = {}): RegisterRow {
  return meter(5101, {
    name: 'Musterwerk Hauptzähler',
    values: {
      'Serial Number': 'SN-0001',
      Factor: 1.5,
      Calibrated: false,
      'Installation Date': '2015-10-01',
      'Online ID': { id: 2001, attribute: 'Value', name: 'Musterwerk Strom', readable: true },
      Picture: null,
      'Location GPS': '52.1,8.2',
    },
    versions: { 'Serial Number': V1, Factor: V2, Calibrated: null, 'Installation Date': V1, 'Online ID': V1, Picture: null, 'Location GPS': V1 },
    ...rights,
    ...overrides,
  })
}

function meterList(row = meterRow(), groupOverrides = {}): RegisterList {
  return registerList('meters', [group('5001', [row], { name: 'Gebäude A', columns: meterColumns, canCreate: true, createTarget: 5001, ...groupOverrides })])
}

async function mountView(kind: RegisterKind, http: Partial<Record<'get' | 'post' | 'put' | 'delete' | 'postForm' | 'getBlob', Fn>>) {
  const auth = useAuthStore()
  Object.assign(auth.http, { getBlob: vi.fn(async () => new Blob(['x'])), ...http })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/login', name: 'login', component: Stub },
    ],
  })
  await router.push('/')
  const wrapper = mount(RegisterView, { props: { kind }, global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

/** GET answers: the list (a function, so a reload can answer differently), history, create options, row tree. */
function api(list: () => RegisterList, extra: Record<string, unknown> = {}) {
  return vi.fn(async (path: string) => {
    for (const [prefix, answer] of Object.entries(extra)) if (path.startsWith(prefix)) return answer
    if (path.startsWith('/web/registers/objects/')) return { unit: 'kWh', samples: [] }
    if (path.startsWith('/web/registers/')) return list()
    throw new Error('unexpected ' + path)
  })
}

const dialogs = () => Array.from(document.body.querySelectorAll<HTMLElement>('[role="dialog"]'))
const dialog = (w: VueWrapper) => w.find('[role="dialog"]')

/** The control of a labelled field in the first dialog (label text without unit / required mark). */
function control(w: VueWrapper, label: string): DOMWrapper<HTMLInputElement> {
  const l = dialog(w)
    .findAll('label')
    .find((x) => x.text().replace(/\s*\*$/, '').replace(/\s*\[.*\]$/, '').trim() === label)
  if (!l) throw new Error('no field ' + label)
  return dialog(w).find<HTMLInputElement>('#' + l.attributes('for'))
}

async function openRow(w: VueWrapper, index = 0) {
  await w.findAll('tbody tr.mod-row')[index].find('button.mod-toggle').trigger('click')
  await flushPromises()
}

async function openEdit(w: VueWrapper) {
  await openRow(w)
  await w.find('.reg-edit').trigger('click')
  await flushPromises()
}

async function submit(w: VueWrapper) {
  await dialog(w).find('form').trigger('submit')
  await flushPromises()
}

const button = (w: VueWrapper, text: string) => {
  const b = w.findAll('button').find((x) => x.text().trim() === text)
  if (!b) throw new Error('no button ' + text)
  return b
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
  window.localStorage.clear()
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() }))
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
  vi.unstubAllGlobals()
})

describe('meters and equipment: rights (E5)', () => {
  it('without rights there are no edit controls and the read-only notice stays', async () => {
    const row = meterRow({ canWrite: false, canDelete: false, canUpload: false, canEnter: false })
    const w = await mountView('meters', { get: api(() => meterList(row, { canCreate: false })) })
    expect(w.text()).toContain('Nur Ansicht – Objekte werden im Control Center bearbeitet.')
    expect(w.find('.reg-new').exists()).toBe(false)
    await openRow(w)
    expect(w.find('.reg-edit').exists()).toBe(false)
    expect(w.find('.reg-delete').exists()).toBe(false)
    expect(w.find('.reg-exchange-btn').exists()).toBe(false)
  })

  it('shows each control only with its right', async () => {
    const row = meterRow({ canWrite: false, canDelete: true, canEnter: false })
    const w = await mountView('meters', { get: api(() => meterList(row, { canCreate: false })) })
    expect(w.text()).toContain('Messstellen können hier angelegt, geändert und gelöscht werden')
    await openRow(w)
    expect(w.find('.reg-edit').exists()).toBe(false)
    expect(w.find('.reg-delete').exists()).toBe(true)
    expect(w.find('.reg-exchange-btn').exists()).toBe(false)
    expect(w.find('.reg-new').exists()).toBe(false)
  })

  it('base data get no object editing even with rights in the answer', async () => {
    const list = registerList('basedata', [group('7', [baseData(1, { canWrite: true, canDelete: true })], { canCreate: true })])
    const w = await mountView('basedata', { get: api(() => list) })
    expect(w.find('.reg-new').exists()).toBe(false)
    expect(w.text()).toContain('Nur Ansicht')
    await openRow(w)
    expect(w.find('.reg-edit').exists()).toBe(false)
    expect(w.find('.reg-delete').exists()).toBe(false)
  })
})

describe('meters: editing (E5)', () => {
  it('sends only the changed attributes with their versions and the new name, then reloads', async () => {
    const get = api(() => meterList())
    const saved = meterRow({ name: 'Musterwerk Hauptzähler Ost' })
    const put = vi.fn(async () => saved)
    const w = await mountView('meters', { get, put })
    await openEdit(w)

    const d = dialog(w)
    expect(d.text()).toContain('„Musterwerk Hauptzähler“ bearbeiten')
    expect(d.text()).toContain('Faktor [kWh/Imp]')
    /* read-only type greyed, the file field and the target */
    const gps = control(w, 'GPS')
    expect(gps.attributes('readonly')).toBeDefined()
    expect(gps.element.value).toBe('52.1,8.2')
    expect(d.find('.reg-edit-readonly').exists()).toBe(true)
    expect(d.text()).toContain('Grau dargestellte Felder')
    expect(d.text()).toContain('Musterwerk Strom')
    expect(d.find('.reg-file-field').text()).toContain('Keine Datei')
    expect(control(w, 'Faktor').element.value).toBe('1,5')
    expect(control(w, 'Einbaudatum').element.value).toBe('2015-10-01')
    expect(d.find('.reg-edit-state').text()).toBe('Keine Änderungen')

    await control(w, 'Name').setValue('Musterwerk Hauptzähler Ost')
    await control(w, 'Faktor').setValue('2,25')
    await control(w, 'Geeicht').setValue(true)
    expect(dialog(w).find('.reg-edit-state').text()).toBe('Ungespeicherte Änderungen')
    await submit(w)

    expect(put).toHaveBeenCalledWith('/web/registers/objects/5101?lang=de', {
      fields: { Factor: { version: V2, value: 2.25 }, Calibrated: { version: null, value: true } },
      name: 'Musterwerk Hauptzähler Ost',
    })
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.reg-message').text()).toBe('„Musterwerk Hauptzähler Ost“ wurde gespeichert.')
    expect(get.mock.calls.filter((c) => c[0] === '/web/registers/meters?lang=de')).toHaveLength(2)
  })

  it('nothing changed: no request; invalid numbers are marked', async () => {
    const put = vi.fn()
    const w = await mountView('meters', { get: api(() => meterList()), put })
    await openEdit(w)
    await submit(w)
    expect(put).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('Keine Änderungen.')

    await control(w, 'Faktor').setValue('1,2,3')
    await submit(w)
    expect(put).not.toHaveBeenCalled()
    const input = control(w, 'Faktor')
    expect(input.attributes('aria-invalid')).toBe('true')
    const err = dialog(w).find('#' + input.attributes('aria-describedby'))
    expect(err.text()).toBe('Bitte eine Zahl eingeben (Dezimalkomma erlaubt).')
    expect(document.activeElement).toBe(input.element)

    await control(w, 'Name').setValue('  ')
    await submit(w)
    expect(dialog(w).text()).toContain('Bitte einen Namen eingeben.')
  })

  it('picks the data row with the row picker in single-select mode', async () => {
    const tree = [
      { id: 3001, name: 'Musterwerk Gas', class: 'Data', hasChildren: false, selectable: true, attributes: ['Value'] },
      { id: 3002, name: 'Musterwerk Wasser', class: 'Data', hasChildren: false, selectable: true, attributes: ['Value'] },
    ]
    const get = api(() => meterList(), { '/web/rows/tree': tree })
    const put = vi.fn(async () => meterRow())
    const w = await mountView('meters', { get, put })
    await openEdit(w)
    await button(w, 'Ändern …').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/rows/tree?lang=de')
    const picker = dialogs()[1]
    expect(picker.textContent).toContain('Datenpunkt: Datenpunkt auswählen')
    const radios = picker.querySelectorAll<HTMLInputElement>('input[type="radio"]')
    expect(radios).toHaveLength(2)
    radios[0].checked = true
    radios[0].dispatchEvent(new Event('change'))
    radios[1].checked = true
    radios[1].dispatchEvent(new Event('change'))
    await flushPromises()
    const apply = picker.querySelector<HTMLButtonElement>('.rp-submit')!
    expect(apply.textContent!.trim()).toBe('Übernehmen')
    apply.click()
    await flushPromises()
    expect(dialogs()).toHaveLength(1)
    expect(dialog(w).text()).toContain('Musterwerk Wasser')
    await submit(w)
    expect(put).toHaveBeenCalledWith('/web/registers/objects/5101?lang=de', { fields: { 'Online ID': { version: V1, value: { id: 3002, attribute: 'Value' } } } })
  })

  it('clears a target with "Entfernen"', async () => {
    const put = vi.fn(async () => meterRow())
    const w = await mountView('meters', { get: api(() => meterList()), put })
    await openEdit(w)
    await button(w, 'Entfernen').trigger('click')
    expect(dialog(w).text()).toContain('Kein Datenpunkt')
    await submit(w)
    expect(put).toHaveBeenCalledWith('/web/registers/objects/5101?lang=de', { fields: { 'Online ID': { version: V1, value: null } } })
  })

  it('409: "Neu laden" takes the current row, "Weiter bearbeiten" keeps own changes with the new versions', async () => {
    const current = meterRow({
      values: { ...meterRow().values, 'Serial Number': 'SN-0002', Factor: 1.75 },
      versions: { ...meterRow().versions, 'Serial Number': V3, Factor: V3 },
    })
    const put = vi.fn().mockRejectedValueOnce(httpError(409, current)).mockRejectedValueOnce(httpError(409, current)).mockResolvedValueOnce(current)
    const get = api(() => meterList())
    const w = await mountView('meters', { get, put })
    await openEdit(w)
    await control(w, 'Faktor').setValue('3')
    await submit(w)
    expect(dialog(w).find('.reg-conflict').text()).toContain('Inzwischen geändert')
    expect(get.mock.calls.filter((c) => c[0] === '/web/registers/meters?lang=de')).toHaveLength(2)
    expect(dialog(w).find('.edit-submit').attributes('disabled')).toBeDefined()

    await dialog(w).find('.reg-conflict-reload').trigger('click')
    expect(control(w, 'Faktor').element.value).toBe('1,75')
    expect(control(w, 'Seriennummer').element.value).toBe('SN-0002')
    expect(dialog(w).text()).toContain('Die aktuelle Fassung wurde geladen.')

    await control(w, 'Faktor').setValue('4')
    await control(w, 'Seriennummer').setValue('SN-0002')
    await submit(w)
    await dialog(w).find('.reg-conflict-keep').trigger('click')
    expect(control(w, 'Faktor').element.value).toBe('4')
    await submit(w)
    expect(put).toHaveBeenLastCalledWith('/web/registers/objects/5101?lang=de', { fields: { Factor: { version: V3, value: 4 } } })
    expect(dialog(w).exists()).toBe(false)
  })

  it('400 with messages lists them in the form', async () => {
    const put = vi.fn().mockRejectedValue(httpError(400, { messages: ['Faktor: ungültig', 'Datenpunkt nicht lesbar'] }))
    const w = await mountView('meters', { get: api(() => meterList()), put })
    await openEdit(w)
    await control(w, 'Faktor').setValue('5')
    await submit(w)
    const box = dialog(w).find('.iso-messages')
    expect(box.text()).toContain('Die Eingaben wurden nicht angenommen:')
    expect(box.findAll('li').map((l) => l.text())).toEqual(['Faktor: ungültig', 'Datenpunkt nicht lesbar'])
  })

  it('asks before unsaved changes are dropped', async () => {
    const w = await mountView('meters', { get: api(() => meterList()) })
    await openEdit(w)
    await control(w, 'Seriennummer').setValue('SN-0009')
    await button(w, 'Abbrechen').trigger('click')
    await flushPromises()
    expect(dialogs()).toHaveLength(2)
    expect(dialogs()[1].textContent).toContain('Die Änderungen wurden nicht gespeichert.')
    /* keep editing */
    dialogs()[1].querySelectorAll<HTMLButtonElement>('button.edit-btn')[0].click()
    await flushPromises()
    expect(dialogs()).toHaveLength(1)
    expect(control(w, 'Seriennummer').element.value).toBe('SN-0009')
    /* discard */
    await button(w, 'Abbrechen').trigger('click')
    await flushPromises()
    dialogs()[1].querySelector<HTMLButtonElement>('.edit-submit')!.click()
    await flushPromises()
    expect(dialogs()).toHaveLength(0)
  })

  it('uploads a file right away and reloads the list', async () => {
    const postForm = vi.fn(async (_path: string, _form: FormData) => ({ attribute: 'Picture', file: 'zaehler.png', image: true, version: V3 }))
    const get = api(() => meterList())
    const w = await mountView('meters', { get, postForm })
    await openEdit(w)
    const field = dialog(w).find('.reg-file-field')
    expect(field.find('label').text()).toBe('Datei hochladen')
    const input = field.find<HTMLInputElement>('input[type="file"]')
    const file = new File(['x'], 'zaehler.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(postForm.mock.calls[0][0]).toBe('/web/registers/objects/5101/files/Picture')
    const form = postForm.mock.calls[0][1]
    expect((form.get('file') as File).name).toBe('zaehler.png')
    expect(dialog(w).text()).toContain('„zaehler.png“ wurde hochgeladen.')
    expect(dialog(w).find('img.reg-thumb').exists()).toBe(true)
    expect(dialog(w).find('.reg-edit-state').text()).toBe('Datei hochgeladen')
    expect(get.mock.calls.filter((c) => c[0] === '/web/registers/meters?lang=de')).toHaveLength(2)
  })

  it('refuses files over 25 MB without a request', async () => {
    const postForm = vi.fn()
    const w = await mountView('meters', { get: api(() => meterList()), postForm })
    await openEdit(w)
    const input = dialog(w).find<HTMLInputElement>('.reg-file-field input[type="file"]')
    const big = new File(['x'], 'gross.pdf')
    Object.defineProperty(big, 'size', { value: 26 * 1024 * 1024 })
    Object.defineProperty(input.element, 'files', { value: [big], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(postForm).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('„gross.pdf“ ist größer als 25 MB.')
  })
})

describe('meters: create, delete, exchange (E5)', () => {
  it('creates a meter with class, name and parent meter; the new row opens', async () => {
    const options = {
      classes: [
        { class: 'Electricity Measurement Instrument', label: 'Stromzähler', medium: 'ELECTRICITY' },
        { class: 'Gas Measurement Instrument', label: 'Gaszähler', medium: 'GAS' },
      ],
      parents: [{ id: 5101, name: 'Musterwerk Hauptzähler' }],
    }
    const created = meter(5200, { name: 'Musterwerk Gas 1', medium: 'GAS', ...rights })
    let list = meterList()
    const get = api(() => list, { '/web/registers/meters/create-options': options })
    const post = vi.fn(async () => {
      list = meterList()
      list.groups[0].rows.push(created)
      return created
    })
    const w = await mountView('meters', { get, post })
    const newButton = w.find('.reg-new')
    expect(newButton.attributes('aria-label')).toBe('Neue Messstelle in „Gebäude A“ anlegen')
    await newButton.trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/registers/meters/create-options?groupKey=5001&lang=de')
    const d = dialog(w)
    expect(d.text()).toContain('Neue Messstelle')
    const classes = d.findAll('.reg-class')
    expect(classes.map((c) => c.text())).toEqual(['Stromzähler', 'Gaszähler'])
    expect(classes[0].find('svg.reg-medium-icon').attributes('aria-hidden')).toBe('true')
    /* no attribute form for a new meter */
    expect(d.find('.iso-edit-section').exists()).toBe(false)

    await classes[1].find('input').setValue(true)
    await submit(w)
    expect(post).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('Bitte einen Namen eingeben.')

    await control(w, 'Name').setValue('  Musterwerk Gas 1 ')
    await control(w, 'Unterzähler von').setValue('5101')
    await submit(w)
    expect(post).toHaveBeenCalledWith('/web/registers/meters/objects?lang=de', {
      groupKey: '5001',
      class: 'Gas Measurement Instrument',
      name: 'Musterwerk Gas 1',
      parentId: 5101,
      fields: {},
    })
    expect(w.find('.reg-message').text()).toBe('„Musterwerk Gas 1“ wurde angelegt.')
    expect(w.findAll('tbody tr.mod-row')).toHaveLength(2)
    expect(w.find('#reg-meters-d-5200').exists()).toBe(true)
  })

  it('without classes the dialog says so and cannot be submitted', async () => {
    const get = api(() => meterList(), { '/web/registers/meters/create-options': { classes: [], parents: [] } })
    const w = await mountView('meters', { get })
    await w.find('.reg-new').trigger('click')
    await flushPromises()
    expect(dialog(w).text()).toContain('Sie dürfen in dieser Gruppe keine Messstellen anlegen.')
    expect(dialog(w).find('.edit-submit').attributes('disabled')).toBeDefined()
  })

  it('deletes after the question with the latest version and reloads', async () => {
    let list = meterList()
    const get = api(() => list)
    const del = vi.fn(async () => {
      list = meterList()
      list.groups[0].rows = []
      return undefined
    })
    const w = await mountView('meters', { get, delete: del })
    await openRow(w)
    await w.find('.reg-delete').trigger('click')
    expect(dialog(w).text()).toContain('Messstelle löschen')
    expect(dialog(w).text()).toContain('„Musterwerk Hauptzähler“ in den Papierkorb verschieben?')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/registers/objects/5101?version=' + encodeURIComponent(V2))
    expect(w.find('.reg-message').text()).toBe('„Musterwerk Hauptzähler“ wurde in den Papierkorb verschoben.')
    expect(w.findAll('tbody tr.mod-row')).toHaveLength(0)
  })

  it('delete 409: not deleted, message and reload', async () => {
    const get = api(() => meterList())
    const del = vi.fn().mockRejectedValue(httpError(409, meterRow()))
    const w = await mountView('meters', { get, delete: del })
    await openRow(w)
    await w.find('.reg-delete').trigger('click')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.reg-message').text()).toContain('wurde inzwischen geändert und nicht gelöscht')
    expect(get.mock.calls.filter((c) => c[0] === '/web/registers/meters?lang=de')).toHaveLength(2)
  })

  it('meter exchange: checks, warnings with "Trotzdem speichern", then confirm', async () => {
    const post = vi
      .fn()
      .mockRejectedValueOnce(httpError(409, { warnings: ['Der neue Zählerstand ist kleiner als der letzte.'] }))
      .mockResolvedValueOnce({ samples: [] })
    const get = api(() => meterList())
    const w = await mountView('meters', { get, post })
    await openRow(w)
    await w.find('.reg-exchange-btn').trigger('click')
    await flushPromises()
    expect(dialog(w).text()).toContain('Zählerwechsel')
    expect(dialog(w).text()).toContain('Data 5101')

    await submit(w)
    expect(post).not.toHaveBeenCalled()
    const old = control(w, 'Alter Zählerstand')
    expect(old.attributes('aria-invalid')).toBe('true')
    expect(old.attributes('aria-describedby')).toContain('-err')

    const future = new Date(Date.now() + 2 * 3600_000)
    const pad = (n: number) => String(n).padStart(2, '0')
    await control(w, 'Zeitpunkt des Wechsels').setValue(
      `${future.getFullYear()}-${pad(future.getMonth() + 1)}-${pad(future.getDate())}T${pad(future.getHours())}:${pad(future.getMinutes())}`,
    )
    await control(w, 'Alter Zählerstand').setValue('12.345,6')
    await control(w, 'Neuer Zählerstand').setValue('0,5')
    await submit(w)
    expect(post).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('Der Zeitpunkt darf nicht in der Zukunft liegen.')

    await control(w, 'Zeitpunkt des Wechsels').setValue('2026-11-10T08:15')
    await submit(w)
    expect(post).toHaveBeenCalledTimes(1)
    const [path, body] = post.mock.calls[0] as [string, { timestamp: string; oldReading: number; newReading: number; confirm: boolean }]
    expect(path).toBe('/web/registers/meters/5101/exchange?lang=de')
    expect(body.timestamp).toMatch(/^2026-11-10T08:15:00[+-]\d{2}:\d{2}$/)
    expect(body).toMatchObject({ oldReading: 12345.6, newReading: 0.5, confirm: false })
    expect(dialog(w).text()).toContain('Der neue Zählerstand ist kleiner als der letzte.')

    await dialog(w).find('.entry-force').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenLastCalledWith('/web/registers/meters/5101/exchange?lang=de', { ...body, confirm: true })
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.reg-entry-message').text()).toContain('Zählerwechsel vom 10.11.2026')
    expect(get.mock.calls.filter((c) => c[0] === '/web/registers/meters?lang=de')).toHaveLength(2)
  })
})

describe('equipment: create and edit (E5)', () => {
  const pumpColumns = [
    column('Manufacturer', { name: 'Hersteller', primitiveType: 'STRING', editable: true }),
    column('Power', { name: 'Leistung', type: 'NUMBER', primitiveType: 'DOUBLE', inputUnit: 'kW', editable: true }),
    column('Datasheet', { name: 'Datenblatt', type: 'FILE', primitiveType: 'FILE', editable: true }),
  ]
  const list = () => registerList('equipment', [group('Pump', [equipment(6101, { canWrite: true, versions: { Manufacturer: V1, Power: null, Datasheet: null } })], { name: 'Pumpe', columns: pumpColumns, canCreate: true })])

  it('creates equipment below a parent of the create options with the attribute form', async () => {
    const options = { parents: [{ id: 801, name: 'Anlagen', path: 'Musterwerk' }, { id: 802, name: 'Anlagen', path: 'Musterwerk / Halle 2' }] }
    const get = api(list, { '/web/registers/equipment/create-options': options })
    const created = equipment(6200, { name: 'Musterwerk Pumpe 2' })
    const post = vi.fn(async () => created)
    const w = await mountView('equipment', { get, post })
    await w.find('.reg-new').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/registers/equipment/create-options?class=Pump&lang=de')
    expect(dialog(w).text()).toContain('Neue Anlage')
    /* no file fields before the object exists */
    expect(dialog(w).find('.reg-file-field').exists()).toBe(false)
    expect(dialog(w).text()).toContain('Dateien und Bilder können nach dem Anlegen hochgeladen werden.')

    await control(w, 'Name').setValue('Musterwerk Pumpe 2')
    await submit(w)
    expect(post).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('Bitte einen Ablageort wählen.')

    await control(w, 'Ablageort').setValue('802')
    await control(w, 'Leistung').setValue('7,5')
    await submit(w)
    expect(post).toHaveBeenCalledWith('/web/registers/equipment/objects?lang=de', {
      class: 'Pump',
      parentId: 802,
      name: 'Musterwerk Pumpe 2',
      fields: { Power: { version: null, value: 7.5 } },
    })
    expect(w.find('.reg-message').text()).toBe('„Musterwerk Pumpe 2“ wurde angelegt.')
  })

  it('edits equipment; without canDelete no delete button and no meter exchange', async () => {
    const put = vi.fn(async () => equipment(6101))
    const w = await mountView('equipment', { get: api(list), put })
    await openRow(w)
    expect(w.find('.reg-delete').exists()).toBe(false)
    expect(w.find('.reg-exchange-btn').exists()).toBe(false)
    await w.find('.reg-edit').trigger('click')
    await flushPromises()
    expect(dialog(w).text()).toContain('Leistung [kW]')
    /* no upload without canUpload */
    expect(dialog(w).find('.reg-file-field input[type="file"]').exists()).toBe(false)
    await control(w, 'Hersteller').setValue('Musterfirma')
    await submit(w)
    expect(put).toHaveBeenCalledWith('/web/registers/objects/6101?lang=de', { fields: { Manufacturer: { version: V1, value: 'Musterfirma' } } })
  })
})

describe('unsaved-changes guard on route change (E5)', () => {
  it('asks before leaving the page with unsaved changes', async () => {
    const auth = useAuthStore()
    Object.assign(auth.http, { get: api(() => meterList()), getBlob: vi.fn(async () => new Blob(['x'])) })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: Stub },
        { path: '/meters', name: 'meters', component: RegisterView, props: { kind: 'meters' } },
      ],
    })
    await router.push('/meters')
    const Root = defineComponent({ render: () => h(RouterView) })
    const w = mount(Root, { global: { plugins: [router, i18n] }, attachTo: document.body })
    mounted.push(w)
    await flushPromises()
    await openEdit(w)
    await control(w, 'Seriennummer').setValue('SN-0010')

    const leaving = router.push('/')
    await flushPromises()
    expect(dialogs()).toHaveLength(2)
    expect(dialogs()[1].textContent).toContain('Ungespeicherte Änderungen')
    dialogs()[1].querySelectorAll<HTMLButtonElement>('button.edit-btn')[0].click()
    await leaving
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/meters')

    const again = router.push('/')
    await flushPromises()
    dialogs()[1].querySelector<HTMLButtonElement>('.edit-submit')!.click()
    await again
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/')
  })
})
