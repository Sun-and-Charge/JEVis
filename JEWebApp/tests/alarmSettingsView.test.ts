/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AlarmSettingsList, RowAlarmSettings } from '../src/api/alarmSettings'
import type { ReadingMeter } from '../src/api/readings'
import RowAlarmSettingsDialog from '../src/components/alarms/RowAlarmSettingsDialog.vue'
import ReadingPanel from '../src/components/readings/ReadingPanel.vue'
import RegisterDetails from '../src/components/registers/RegisterDetails.vue'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import AlarmsView from '../src/views/AlarmsView.vue'
import { alarm, alarmList } from './fixtures/alarms'
import { V1, V2, alarmConfig, rowSettings, settingsList } from './fixtures/alarmSettings'
import { httpError } from './fixtures/edit'
import { meter as registerMeter } from './fixtures/registers'

/* Alarm settings (E7) in the web app with a mocked API; neutral placeholder data. */

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

const Stub = defineComponent({ render: () => h('div') })
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []

function api(list: () => AlarmSettingsList, row: () => RowAlarmSettings = () => rowSettings()) {
  return vi.fn(async (path: string) => {
    if (path.startsWith('/web/alarms?')) return alarmList([alarm('a1')])
    if (path.startsWith('/web/alarms/settings?')) return list()
    if (path.startsWith('/web/rows/8100/alarm-settings?')) return row()
    if (path.startsWith('/web/rows/9001/alarm-settings?')) return row()
    if (path.startsWith('/web/rows/2001/alarm-settings?')) return row()
    if (path.startsWith('/web/rows/8100/samples')) return { unit: 'kWh', samples: [], truncated: false }
    if (path.startsWith('/web/rows/tree')) return []
    throw new Error('unexpected ' + path)
  })
}

async function withRouter() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }] })
  await router.push('/')
  return router
}

async function mountView(http: Partial<Record<'get' | 'post' | 'put' | 'delete', Fn>>) {
  Object.assign(useAuthStore().http, http)
  const wrapper = mount(AlarmsView, { global: { plugins: [await withRouter(), i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

const tab = (w: VueWrapper, text: string) => w.findAll('[role="tab"]').find((t) => t.text() === text)!
const cfgPanel = () => document.body.querySelector<HTMLElement>('.alm-cfg-backdrop [role="dialog"]')
const rowPanel = () => document.body.querySelector<HTMLElement>('.alm-row-backdrop [role="dialog"]')

function bodyButton(text: string, root: ParentNode = document.body): HTMLButtonElement {
  const b = Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((x) => x.textContent?.trim() === text)
  if (!b) throw new Error('no button ' + text)
  return b
}

function fieldIn(root: HTMLElement, label: string): HTMLInputElement {
  const l = Array.from(root.querySelectorAll('label')).find((x) => x.textContent?.replace(/\s*\*$/, '').trim() === label)
  if (!l) throw new Error('no field ' + label)
  return document.getElementById(l.getAttribute('for')!) as HTMLInputElement
}

async function type(el: HTMLInputElement | HTMLTextAreaElement, value: string) {
  el.value = value
  el.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function click(el: HTMLElement) {
  el.click()
  await flushPromises()
}

async function submit(root: HTMLElement) {
  root.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

async function openSettings(w: VueWrapper) {
  await tab(w, 'Einstellungen').trigger('click')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
  document.body.innerHTML = ''
})

describe('alarms page: tab "Einstellungen" (E7.1)', () => {
  it('loads the configurations only when the tab is opened and lists them', async () => {
    const get = api(() => settingsList([alarmConfig(9100), alarmConfig(9200, { name: 'Gas', enabled: false, scope: 'COMPLETE', multipleNotifications: true })]))
    const w = await mountView({ get })
    expect(get.mock.calls.some((c) => String(c[0]).startsWith('/web/alarms/settings'))).toBe(false)
    expect(tab(w, 'Alarme').attributes('aria-selected')).toBe('true')
    await openSettings(w)
    expect(get).toHaveBeenCalledWith('/web/alarms/settings?lang=de')
    const cards = w.findAll('.alm-cfg-card')
    expect(cards).toHaveLength(2)
    expect(cards[0].text()).toContain('Nur ausgewählte Datenreihen (1 Datenreihe)')
    expect(cards[0].text()).toContain('3 Empfänger')
    expect(cards[0].text()).toContain('Täglich')
    expect(cards[1].text()).toContain('Inaktiv')
    expect(cards[1].text()).toContain('mehrere E-Mail-Benachrichtigungen')
    await w.find('#alm-cfg-search').setValue('gas')
    expect(w.findAll('.alm-cfg-card')).toHaveLength(1)
  })

  it('edits a configuration: PUT with only the changes and the version', async () => {
    const put = vi.fn(async (_p: string, body: Record<string, unknown>) => alarmConfig(9100, { name: body.name as string, version: V2 }))
    const w = await mountView({ get: api(() => settingsList([alarmConfig(9100)])), put })
    await openSettings(w)
    await w.find('.alm-cfg-edit').trigger('click')
    await flushPromises()
    const panel = cfgPanel()!
    expect(panel.querySelector('h2')!.textContent).toBe('„Alarm 9100“ bearbeiten')
    expect(panel.textContent).toContain('Strom - Gesamt')
    expect(panel.textContent).toContain('Max Mustermann')
    await type(fieldIn(panel, 'Name'), 'Strom Alarm')
    await submit(panel)
    expect(put).toHaveBeenCalledWith('/web/alarms/settings/9100?lang=de', { name: 'Strom Alarm', version: V1 })
    expect(panel.textContent).toContain('„Strom Alarm“ wurde gespeichert.')
  })

  it('checks mail addresses and objects before sending', async () => {
    const put = vi.fn()
    const w = await mountView({ get: api(() => settingsList([alarmConfig(9100)])), put })
    await openSettings(w)
    await w.find('.alm-cfg-edit').trigger('click')
    await flushPromises()
    const panel = cfgPanel()!
    await type(fieldIn(panel, 'Weitere E-Mail-Adressen'), 'alarm@example.org, kaputt')
    await click(panel.querySelector<HTMLButtonElement>('.alm-object-remove')!)
    await submit(panel)
    expect(put).not.toHaveBeenCalled()
    expect(panel.textContent).toContain('Keine gültige E-Mail-Adresse: kaputt')
    expect(panel.textContent).toContain('Bitte mindestens eine Datenreihe wählen.')
  })

  it('creates a configuration in the only directory', async () => {
    const post = vi.fn(async () => alarmConfig(9300, { name: 'Neu', scope: 'COMPLETE', objects: [], notification: null }))
    const w = await mountView({ get: api(() => settingsList([])), post })
    await openSettings(w)
    expect(w.text()).toContain('Keine Alarmkonfigurationen vorhanden.')
    await w.find('.alm-cfg-new').trigger('click')
    await flushPromises()
    const panel = cfgPanel()!
    expect(panel.querySelector('h2')!.textContent).toBe('Neue Alarmkonfiguration')
    await type(fieldIn(panel, 'Name'), 'Neu')
    await submit(panel)
    expect(post).toHaveBeenCalledWith('/web/alarms/settings?lang=de', {
      name: 'Neu',
      enabled: true,
      scope: 'COMPLETE',
      objects: [],
      period: 'DAILY',
      bindToData: false,
      includeLimits: false,
      disableLink: false,
      parentId: 600,
    })
    expect(panel.textContent).toContain('„Neu“ wurde angelegt.')
  })

  it('409: "Weiter bearbeiten" keeps own changes on the newer version', async () => {
    const put = vi.fn().mockRejectedValueOnce(httpError(409, alarmConfig(9100, { period: 'WEEKLY', version: V2 }))).mockResolvedValueOnce(alarmConfig(9100, { version: 'v3' }))
    const w = await mountView({ get: api(() => settingsList([alarmConfig(9100)])), put })
    await openSettings(w)
    await w.find('.alm-cfg-edit').trigger('click')
    await flushPromises()
    const panel = cfgPanel()!
    await type(fieldIn(panel, 'Name'), 'Eigener Name')
    await submit(panel)
    expect(panel.querySelector('.reg-conflict')).not.toBeNull()
    await click(panel.querySelector<HTMLButtonElement>('.reg-conflict-keep')!)
    expect(fieldIn(panel, 'Name').value).toBe('Eigener Name')
    expect((fieldIn(panel, 'Prüfintervall') as unknown as HTMLSelectElement).value).toBe('WEEKLY')
    await submit(panel)
    expect(put.mock.calls[1][1]).toEqual({ name: 'Eigener Name', version: V2 })
  })

  it('several notifications: a warning, the notification is not shown for editing', async () => {
    const w = await mountView({ get: api(() => settingsList([alarmConfig(9100, { multipleNotifications: true })])) })
    await openSettings(w)
    await w.find('.alm-cfg-edit').trigger('click')
    await flushPromises()
    const panel = cfgPanel()!
    expect(panel.querySelector('.alm-multiple')).not.toBeNull()
    expect(panel.querySelector('.alm-mail-on')).toBeNull()
  })

  it('deletes a configuration with its version and asks before dropping changes', async () => {
    const del = vi.fn(async () => undefined)
    const w = await mountView({ get: api(() => settingsList([alarmConfig(9100)])), delete: del })
    await openSettings(w)
    await w.find('.alm-cfg-edit').trigger('click')
    await flushPromises()
    let panel = cfgPanel()!
    await type(fieldIn(panel, 'Name'), 'Geändert')
    await click(panel.querySelector<HTMLButtonElement>('.alm-cfg-close')!)
    expect(document.body.textContent).toContain('Ungespeicherte Änderungen')
    await click(bodyButton('Abbrechen', document.body.querySelector('.edit-backdrop:last-of-type')!))
    panel = cfgPanel()!
    await click(panel.querySelector<HTMLButtonElement>('.alm-cfg-delete')!)
    const dialogs = document.body.querySelectorAll<HTMLElement>('[role="dialog"]')
    await click(bodyButton('Löschen', dialogs[dialogs.length - 1]))
    expect(del).toHaveBeenCalledWith('/web/alarms/settings/9100?version=' + encodeURIComponent(V1))
    expect(cfgPanel()).toBeNull()
    expect(w.text()).toContain('wurde in den Papierkorb verschoben')
  })

  it('read-only configurations open with "Anzeigen" and no save button', async () => {
    const w = await mountView({ get: api(() => settingsList([alarmConfig(9100, { canWrite: false, canDelete: false })], { targets: [] })) })
    await openSettings(w)
    expect(w.find('.alm-cfg-new').exists()).toBe(false)
    expect(w.find('.alm-cfg-edit').text()).toBe('Anzeigen')
    await w.find('.alm-cfg-edit').trigger('click')
    await flushPromises()
    const panel = cfgPanel()!
    expect(panel.querySelector('.edit-submit')).toBeNull()
    expect(panel.textContent).toContain('Nur Ansicht')
  })

  it('opens the alarm settings of a configuration row', async () => {
    const get = api(() => settingsList([alarmConfig(9100)]))
    const w = await mountView({ get })
    await openSettings(w)
    await w.find('.alm-cfg-edit').trigger('click')
    await flushPromises()
    await click(cfgPanel()!.querySelector<HTMLButtonElement>('.alm-object-settings')!)
    expect(get).toHaveBeenCalledWith('/web/rows/8100/alarm-settings?lang=de')
    expect(rowPanel()!.querySelector('h2')!.textContent).toBe('Alarmeinstellungen: Strom - Gesamt')
  })
})

async function mountRow(http: Partial<Record<'get' | 'put', Fn>>) {
  Object.assign(useAuthStore().http, http)
  const wrapper = mount(RowAlarmSettingsDialog, { props: { rowId: 8100 }, global: { plugins: [await withRouter(), i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

describe('row alarm settings panel (E7.2)', () => {
  it('opens from an alarm of the list', async () => {
    const get = api(() => settingsList([]))
    const w = await mountView({ get })
    await w.find('tbody tr.alarms-row').trigger('click')
    await flushPromises()
    await w.find('.alarm-row-settings').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/rows/8100/alarm-settings?lang=de')
    expect(rowPanel()).not.toBeNull()
  })

  it('shows limits, rules, schedules and the reporting configurations', async () => {
    const w = await mountRow({ get: api(() => settingsList([])) })
    const panel = rowPanel()!
    expect(panel.textContent).toContain('Strom - Rohdaten')
    expect(panel.querySelector('.alm-row-configs')!.textContent).toContain('Alarm 9100')
    expect(fieldIn(panel, 'Maximalwert [kWh]').value).toBe('500')
    expect(panel.querySelectorAll('.alm-step')).toHaveLength(2)
    expect(panel.querySelector<HTMLSelectElement>('select[id$="step1-sub"]')!.selectedOptions[0].textContent).toContain('Interpolation')
    expect(panel.querySelector<HTMLSelectElement>('select[id$="-op"]')!.selectedOptions[0].textContent).toContain('> größer als')
    const pressed = Array.from(panel.querySelectorAll('.alm-day[aria-pressed="true"]')).map((b) => b.textContent?.trim())
    expect(pressed).toEqual(['Sa', 'So'])
    expect(panel.querySelector('.alm-fixed')!.textContent).toContain('0 0 12 ? * MON-FRI')
    w.unmount()
  })

  it('without configurations it warns; read-only without write right', async () => {
    await mountRow({ get: api(() => settingsList([]), () => rowSettings({ configurations: [], canWrite: false })) })
    const panel = rowPanel()!
    expect(panel.querySelector('.alm-no-config')).not.toBeNull()
    expect(panel.querySelector('.edit-submit')).toBeNull()
    expect(panel.textContent).toContain('Nur Ansicht')
  })

  it('saves changed limits only and shows the reprocess hint', async () => {
    const put = vi.fn(async () => ({ ...rowSettings({ version: V2 }), reprocessHint: true }))
    await mountRow({ get: api(() => settingsList([])), put })
    const panel = rowPanel()!
    await type(fieldIn(panel, 'Maximalwert [kWh]'), '450,5')
    await submit(panel)
    expect(put).toHaveBeenCalledTimes(1)
    const [path, body] = put.mock.calls[0] as unknown as [string, Record<string, unknown>]
    expect(path).toBe('/web/rows/8100/alarm-settings?lang=de')
    expect(body.version).toBe(V1)
    expect(body.alarms).toBeUndefined()
    expect((body.limits as { steps: { max: number }[] }).steps[0].max).toBe(450.5)
    expect(panel.querySelector('.alm-reprocess')!.textContent).toContain('ab jetzt verarbeitet')
  })

  it('adds a rule and checks it before sending', async () => {
    const put = vi.fn()
    await mountRow({ get: api(() => settingsList([])), put })
    const panel = rowPanel()!
    await click(panel.querySelector<HTMLButtonElement>('.alm-rule-add')!)
    expect(panel.querySelectorAll('.alm-rule')).toHaveLength(2)
    await submit(panel)
    expect(put).not.toHaveBeenCalled()
    expect(panel.textContent).toContain('Bitte einen Grenzwert eingeben.')
    const radios = panel.querySelectorAll<HTMLInputElement>('.alm-rule:last-of-type input[value="DYNAMIC"]')
    radios[0].checked = true
    radios[0].dispatchEvent(new Event('change'))
    await flushPromises()
    await submit(panel)
    expect(panel.textContent).toContain('Bitte eine Vergleichsreihe wählen.')
  })

  it('switching limits on without steps creates steps 1 and 2', async () => {
    await mountRow({ get: api(() => settingsList([]), () => rowSettings({ limits: { enabled: false, steps: [] } })) })
    const panel = rowPanel()!
    expect(panel.querySelectorAll('.alm-step')).toHaveLength(0)
    const on = panel.querySelector<HTMLInputElement>('.alm-limits-on')!
    on.checked = true
    on.dispatchEvent(new Event('change'))
    await flushPromises()
    expect(Array.from(panel.querySelectorAll('.alm-step legend')).map((l) => l.textContent)).toEqual(['Stufe 1', 'Stufe 2'])
  })

  it('409: "Neu laden" shows the current settings', async () => {
    const put = vi.fn().mockRejectedValue(httpError(409, rowSettings({ version: V2, limits: { enabled: true, steps: [{ name: 'Stufe 1', min: 1, max: 99, substitute: 'NONE', defaultMin: null, defaultMax: null }] } })))
    await mountRow({ get: api(() => settingsList([])), put })
    const panel = rowPanel()!
    await type(fieldIn(panel, 'Maximalwert [kWh]'), '450')
    await submit(panel)
    await click(panel.querySelector<HTMLButtonElement>('.reg-conflict-reload')!)
    expect(fieldIn(panel, 'Maximalwert [kWh]').value).toBe('99')
    expect(panel.querySelectorAll('.alm-step')).toHaveLength(1)
  })
})

describe('row alarm settings from registers and readings', () => {
  it('meter register details open the settings of the target row', async () => {
    const get = api(() => settingsList([]))
    Object.assign(useAuthStore().http, { get: vi.fn(async (p: string) => (p.startsWith('/web/registers/') ? { samples: [] } : get(p))) })
    const w = mount(RegisterDetails, {
      props: { kind: 'meters', row: registerMeter(1), columns: [], today: '2026-11-11' },
      global: { plugins: [await withRouter(), i18n] },
      attachTo: document.body,
    })
    mounted.push(w)
    await flushPromises()
    await w.find('.reg-alarm-settings').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/rows/9001/alarm-settings?lang=de')
    expect(rowPanel()).not.toBeNull()
  })

  it('the reading panel opens the settings of the meter row', async () => {
    const get = api(() => settingsList([]))
    Object.assign(useAuthStore().http, { get })
    const m = {
      id: 1001,
      name: 'Zähler A',
      className: 'Electricity Measurement Instrument',
      classLabel: 'Stromzähler',
      medium: 'ELECTRICITY',
      path: 'Musterwerk',
      serialNumber: null,
      meterPoint: null,
      location: null,
      canPhoto: false,
      canExchange: false,
      row: { id: 2001, name: 'Zähler A - Daten', unit: 'kWh', counter: true, period: 'P1M', mode: 'DAY', timeZone: 'Europe/Berlin', limits: null, canEnter: true, decimals: 1 },
      due: 'OK',
      suggestedTimestamp: null,
      expected: null,
      readings: [],
      problem: null,
    } as unknown as ReadingMeter
    const w = mount(ReadingPanel, { props: { meter: m }, global: { plugins: [await withRouter(), i18n] }, attachTo: document.body })
    mounted.push(w)
    await flushPromises()
    await click(document.body.querySelector<HTMLButtonElement>('.rd-alarm-settings')!)
    expect(get).toHaveBeenCalledWith('/web/rows/2001/alarm-settings?lang=de')
    expect(rowPanel()).not.toBeNull()
  })
})
