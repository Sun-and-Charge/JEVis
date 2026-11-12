/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, type Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Alarm } from '../src/api/alarms'
import type { Note } from '../src/api/notes'
import EditDialog from '../src/components/edit/EditDialog.vue'
import RegisterDetails from '../src/components/registers/RegisterDetails.vue'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import AlarmsView from '../src/views/AlarmsView.vue'
import AnalysisView from '../src/views/AnalysisView.vue'
import AppointmentsView from '../src/views/AppointmentsView.vue'
import MaintenanceView from '../src/views/MaintenanceView.vue'
import NotesView from '../src/views/NotesView.vue'
import { alarm, alarmList } from './fixtures/alarms'
import { at, chart, config, data, row } from './fixtures/analysis'
import { entryInfo, httpError } from './fixtures/edit'
import { appointment, maintenanceEntry } from './fixtures/modules'
import { note, noteList } from './fixtures/notes'
import { baseData, meter } from './fixtures/registers'
import { browserTimeZone } from '../src/analysis/settings'

// no canvas in jsdom: the chart is a placeholder that can emit clicks
vi.mock('vue-echarts', () => ({
  default: defineComponent({ name: 'VChart', props: ['option'], emits: ['click'], setup: () => () => h('div', { class: 'vchart' }) }),
}))

const Stub = defineComponent({ render: () => h('div') })
const sp = (s: string) => s.replace(/\s+/g, ' ').trim()
/** ?timeZone= of the note endpoints (the browser zone) */
const zq = () => 'timeZone=' + encodeURIComponent(browserTimeZone())
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []

async function mountWith(component: Component, http: Partial<Record<'get' | 'post' | 'put' | 'delete', Fn>>, props: Record<string, unknown> = {}, path = '/') {
  const auth = useAuthStore()
  Object.assign(auth.http, http)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/analyses/:id', name: 'analysis', component: Stub },
      { path: '/dashboards/:id', name: 'dashboard', component: Stub },
    ],
  })
  await router.push(path)
  const wrapper = mount(component, { props, global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

const dialog = (w: VueWrapper) => w.find('[role="dialog"]')

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
  vi.useRealTimers()
})

/* ---------------------------------------------------------------- dialog */

describe('EditDialog', () => {
  it('is labelled by its title, focuses the first field, keeps Tab inside and closes on Esc (not while busy)', async () => {
    const opener = document.createElement('button')
    document.body.appendChild(opener)
    opener.focus()
    const Host = defineComponent({
      props: { busy: Boolean },
      emits: ['close'],
      setup: (p, { emit }) => () =>
        h(EditDialog, { title: 'Titel', busy: p.busy, onClose: () => emit('close') }, { default: () => [h('input', { class: 'first' }), h('button', { class: 'last' }, 'OK')] }),
    })
    const w = mount(Host, { global: { plugins: [i18n] }, attachTo: document.body })
    mounted.push(w)
    await flushPromises()
    const d = dialog(w)
    expect(d.attributes('aria-modal')).toBe('true')
    expect(document.getElementById(d.attributes('aria-labelledby')!)?.textContent).toBe('Titel')
    // close button first, then the slot
    expect(document.activeElement?.className).toBe('edit-close')
    ;(w.find('button.last').element as HTMLElement).focus()
    await d.trigger('keydown', { key: 'Tab' })
    expect(document.activeElement?.className).toBe('edit-close')
    await d.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement?.className).toBe('last')

    await d.trigger('keydown', { key: 'Escape' })
    expect(w.emitted('close')).toHaveLength(1)
    await w.setProps({ busy: true })
    await d.trigger('keydown', { key: 'Escape' })
    expect(w.emitted('close')).toHaveLength(1)
    w.unmount()
    mounted.pop()
    expect(document.activeElement).toBe(opener)
    opener.remove()
  })
})

/* ---------------------------------------------------------------- alarms */

describe('alarms: confirming (E1)', () => {
  const nav = { user: { id: 1, name: 'admin', sysAdmin: false }, sites: [], other: { analyses: [], dashboards: [] }, modules: {} }
  const confirmable = (id: string, configId: number, extra: Partial<Alarm> = {}) =>
    alarm(id, { configuration: { id: configId, name: 'Strom - Gesamt - Grenzwert ' + configId, checked: false, checkedBy: null, checkedAt: null, canConfirm: true }, ...extra })

  function api(alarms: Alarm[]) {
    return vi.fn(async (path: string) => {
      if (path.startsWith('/web/alarms?')) return alarmList(alarms)
      if (path === '/web/navigation') return nav
      if (path.startsWith('/web/rows/')) return { unit: 'kWh', samples: [], truncated: false }
      throw new Error('unexpected ' + path)
    })
  }

  it('without canConfirm there are no buttons and the read-only notice stays', async () => {
    const w = await mountWith(AlarmsView, { get: api([alarm('a1')]), post: vi.fn() })
    expect(w.find('.alarm-confirm').exists()).toBe(false)
    expect(w.find('.alarms-confirm-all').exists()).toBe(false)
    expect(w.text()).toContain('Nur Ansicht – Alarme werden im Control Center quittiert.')
  })

  it('confirms a configuration in the row, updates the state and the menu badge', async () => {
    const get = api([confirmable('a1', 9100)])
    const post = vi.fn(async (): Promise<{ checked: boolean; checkedBy: string | null; checkedAt: string | null }> => ({ checked: true, checkedBy: 'admin', checkedAt: '2026-11-12T10:00:00.000+01:00' }))
    const w = await mountWith(AlarmsView, { get, post })
    expect(w.text()).toContain('Alarme werden je Alarmkonfiguration quittiert.')
    const button = w.find('tbody .alarm-confirm')
    expect(button.text()).toBe('Quittieren')
    await button.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/alarms/configurations/9100/confirm', { checked: true })
    expect(w.find('tbody tr.alarms-row .alarm-state').text()).toBe('Quittiert')
    expect(w.find('tbody .alarm-confirm').text()).toBe('Quittierung zurücknehmen')
    expect(w.find('.alarms-message').text()).toContain('wurde quittiert')
    expect(get).toHaveBeenCalledWith('/web/navigation')

    // and back
    post.mockResolvedValueOnce({ checked: false, checkedBy: null, checkedAt: null })
    await w.find('tbody .alarm-confirm').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenLastCalledWith('/web/alarms/configurations/9100/confirm', { checked: false })
    expect(w.find('tbody tr.alarms-row .alarm-state').text()).toBe('Offen')
  })

  it('shows the reason of a 403 and keeps the state', async () => {
    const post = vi.fn(async () => {
      throw httpError(403, 'Kein Schreibrecht')
    })
    const w = await mountWith(AlarmsView, { get: api([confirmable('a1', 9100)]), post })
    await w.find('tbody .alarm-confirm').trigger('click')
    await flushPromises()
    expect(w.find('.alarms-message').text()).toBe('Dafür fehlt Ihnen die Berechtigung. (Kein Schreibrecht)')
    expect(w.find('tbody tr.alarms-row .alarm-state').text()).toBe('Offen')
  })

  it('also confirms from the detail panel', async () => {
    const post = vi.fn(async () => ({ checked: true, checkedBy: 'admin', checkedAt: null }))
    const w = await mountWith(AlarmsView, { get: api([confirmable('a1', 9100)]), post })
    await w.find('tbody tr.alarms-row').trigger('click')
    await flushPromises()
    await w.find('.alarm-confirm-detail').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/alarms/configurations/9100/confirm', { checked: true })
  })

  it('"Alle angezeigten quittieren" asks first and reports confirmed and refused configurations', async () => {
    const get = api([confirmable('a1', 9100), confirmable('a2', 9100), confirmable('a3', 9102), alarm('a4')])
    const post = vi.fn(async () => ({ confirmed: [9100], refused: [9102] }))
    const w = await mountWith(AlarmsView, { get, post })
    const alarmLoads = () => get.mock.calls.filter((c) => String(c[0]).startsWith('/web/alarms?')).length
    expect(alarmLoads()).toBe(1)
    await w.find('.alarms-confirm-all').trigger('click')
    await flushPromises()
    expect(sp(dialog(w).text())).toContain('Die 2 Alarmkonfigurationen der angezeigten offenen Alarme quittieren?')
    expect(post).not.toHaveBeenCalled()
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/alarms/confirm-all', { ids: [9100, 9102] })
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.alarms-message').text()).toBe('1 quittiert, 1 ohne Berechtigung abgelehnt.')
    expect(alarmLoads()).toBe(2)
  })

  it('cancelling the confirm-all dialog sends nothing', async () => {
    const post = vi.fn()
    const w = await mountWith(AlarmsView, { get: api([confirmable('a1', 9100)]), post })
    await w.find('.alarms-confirm-all').trigger('click')
    await dialog(w).trigger('keydown', { key: 'Escape' })
    expect(dialog(w).exists()).toBe(false)
    expect(post).not.toHaveBeenCalled()
  })
})

/* ---------------------------------------------------------------- notes */

describe('notes: writing (E1)', () => {
  const row = { id: 8000, name: 'Strom - Gesamt', className: 'Data', path: 'Musterwerk / Strom', readable: true, canAddNote: true, notesTarget: 8000 }
  const editableNote = (overrides: Partial<Note> = {}) => note('9000-1', { text: 'Zählerwechsel', canEdit: true, canDelete: true, version: 'v1', row, ...overrides })

  function api(notes: Note[]) {
    return vi.fn(async (path: string) => {
      if (path.startsWith('/web/notes?')) return noteList(notes)
      if (path.startsWith('/web/rows/')) return { unit: 'kWh', samples: [], truncated: false }
      throw new Error('unexpected ' + path)
    })
  }

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 11, 12, 0))
  })

  it('without rights: no "Neue Notiz", no edit buttons, read-only notice', async () => {
    const w = await mountWith(NotesView, { get: api([note('n1')]) })
    expect(w.find('.notes-new').exists()).toBe(false)
    expect(w.text()).toContain('Nur Ansicht – Notizen werden im Control Center bearbeitet.')
    await w.find('tbody tr.notes-row').trigger('click')
    await flushPromises()
    expect(w.find('.note-edit').exists()).toBe(false)
    expect(w.find('.note-add-row').exists()).toBe(false)
  })

  it('"Neue Notiz": picks a shown data point, time to the minute, text and tags, then reloads', async () => {
    const get = api([editableNote()])
    const post = vi.fn(async () => editableNote({ id: '9000-2' }))
    const w = await mountWith(NotesView, { get, post })
    expect(w.text()).toContain('Notizen können hier angelegt')
    await w.find('.notes-new').trigger('click')
    const d = dialog(w)
    expect(d.find('h2').text()).toBe('Neue Notiz')
    const select = d.find('select.note-row-select')
    expect(select.findAll('option').map((o) => o.text())).toEqual(['Musterwerk / Strom / Strom - Gesamt'])
    expect((d.find('input[type="datetime-local"]').element as HTMLInputElement).value).toBe('2026-11-11T12:00')
    await d.find('input[type="datetime-local"]').setValue('2026-11-10T14:15')
    await d.find('textarea').setValue('  Anlage abgeschaltet  ')
    const chips = d.findAll('.edit-chip')
    expect(chips).toHaveLength(13)
    await chips.find((c) => c.text() === 'Ereignis')!.trigger('click')
    await chips.find((c) => c.text() === 'Wartung')!.trigger('click')
    await d.find('form').trigger('submit')
    await flushPromises()
    const [path, body] = post.mock.calls[0] as unknown as [string, { timestamp: string; text: string; tags: string[] }]
    expect(path).toBe('/web/rows/8000/notes?' + zq())
    expect(body.text).toBe('Anlage abgeschaltet')
    expect(body.tags).toEqual(['4', '13'])
    expect(Date.parse(body.timestamp)).toBe(new Date(2026, 10, 10, 14, 15).getTime())
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.notes-message').text()).toBe('Die Notiz wurde gespeichert.')
    expect(get.mock.calls.filter((c) => String(c[0]).startsWith('/web/notes?'))).toHaveLength(2)
  })

  it('an empty text is not sent; a 409 on create says that a note exists', async () => {
    const post = vi.fn(async () => {
      throw httpError(409, '')
    })
    const w = await mountWith(NotesView, { get: api([editableNote()]), post })
    await w.find('tbody tr.notes-row').trigger('click')
    await flushPromises()
    await w.find('.note-add-row').trigger('click')
    const d = dialog(w)
    expect(d.find('select').exists()).toBe(false)
    await d.find('form').trigger('submit')
    expect(post).not.toHaveBeenCalled()
    expect(d.find('[role="alert"]').text()).toBe('Bitte einen Text eingeben.')
    await d.find('textarea').setValue('Doppelt')
    await d.find('form').trigger('submit')
    await flushPromises()
    expect(post).toHaveBeenCalledTimes(1)
    expect(dialog(w).find('[role="alert"]').text()).toContain('Zu diesem Zeitpunkt gibt es bereits eine Notiz.')
  })

  it('edit: sends text, tags and version; a 409 takes over the current note and the next save uses its version', async () => {
    const get = api([editableNote()])
    const put = vi.fn(async (_path: string, _body: unknown): Promise<Note> => {
      throw httpError(409, editableNote({ text: 'Inzwischen geändert', tags: ['6'], version: 'v2' }))
    })
    const w = await mountWith(NotesView, { get, put })
    await w.find('tbody tr.notes-row').trigger('click')
    await flushPromises()
    await w.find('.note-edit').trigger('click')
    const d = dialog(w)
    expect(d.find('h2').text()).toBe('Notiz bearbeiten')
    expect(d.find('input[type="datetime-local"]').exists()).toBe(false)
    expect((d.find('textarea').element as HTMLTextAreaElement).value).toBe('Zählerwechsel')
    await d.find('textarea').setValue('Zählerwechsel Nr. 2')
    await d.find('form').trigger('submit')
    await flushPromises()
    // tags unchanged: left out
    expect(put).toHaveBeenCalledWith('/web/notes/9000-1?' + zq(), { text: 'Zählerwechsel Nr. 2', version: 'v1' })
    expect(dialog(w).find('[role="alert"]').text()).toContain('Die Notiz wurde inzwischen geändert.')
    expect((dialog(w).find('textarea').element as HTMLTextAreaElement).value).toBe('Inzwischen geändert')
    expect(get.mock.calls.filter((c) => String(c[0]).startsWith('/web/notes?')).length).toBeGreaterThanOrEqual(2)

    put.mockResolvedValueOnce(editableNote({ text: 'ok' }))
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(put).toHaveBeenLastCalledWith('/web/notes/9000-1?' + zq(), { text: 'Inzwischen geändert', version: 'v2' })
    expect(w.find('.notes-message').text()).toBe('Die Notiz wurde gespeichert.')
  })

  it('delete asks inside the dialog and sends the version', async () => {
    const del = vi.fn(async () => undefined)
    const w = await mountWith(NotesView, { get: api([editableNote()]), delete: del })
    await w.find('tbody tr.notes-row').trigger('click')
    await flushPromises()
    await w.find('.note-edit').trigger('click')
    await dialog(w).find('.note-delete').trigger('click')
    expect(del).not.toHaveBeenCalled()
    await dialog(w).find('.note-delete-confirm').trigger('click')
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/notes/9000-1?version=v1&' + zq())
    expect(w.find('.notes-message').text()).toBe('Die Notiz wurde gelöscht.')
  })

  it('Delete is offered only with canDelete; canDelete alone opens a delete-only dialog', async () => {
    const w = await mountWith(NotesView, { get: api([editableNote({ canDelete: false })]) })
    await w.find('tbody tr.notes-row').trigger('click')
    await flushPromises()
    await w.find('.note-edit').trigger('click')
    expect(dialog(w).find('.note-delete').exists()).toBe(false)
    expect(dialog(w).find('.edit-submit').exists()).toBe(true)
    w.unmount()
    mounted.pop()

    const del = vi.fn(async () => undefined)
    const v = await mountWith(NotesView, { get: api([editableNote({ canEdit: false })]), delete: del })
    await v.find('tbody tr.notes-row').trigger('click')
    await flushPromises()
    const open = v.find('.note-edit')
    expect(open.text()).toBe('Löschen')
    await open.trigger('click')
    const d = dialog(v)
    expect(d.find('h2').text()).toBe('Notiz löschen')
    expect(d.find('.edit-submit').exists()).toBe(false)
    expect(d.find('textarea').attributes('readonly')).toBeDefined()
    await d.find('.note-delete').trigger('click')
    await dialog(v).find('.note-delete-confirm').trigger('click')
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/notes/9000-1?version=v1&' + zq())
  })

  it('notes without version or notesTarget get no edit / add buttons', async () => {
    const w = await mountWith(NotesView, { get: api([editableNote({ version: null, row: { ...row, notesTarget: null } })]) })
    expect(w.find('.notes-new').exists()).toBe(false)
    await w.find('tbody tr.notes-row').trigger('click')
    await flushPromises()
    expect(w.find('.note-edit').exists()).toBe(false)
    expect(w.find('.note-add-row').exists()).toBe(false)
  })

  it('a 400 shows the server message', async () => {
    const post = vi.fn(async () => {
      throw httpError(400, 'Unbekannter Tag')
    })
    const w = await mountWith(NotesView, { get: api([editableNote()]), post })
    await w.find('.notes-new').trigger('click')
    await dialog(w).find('textarea').setValue('x')
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(dialog(w).find('[role="alert"]').text()).toBe('Die Eingabe wurde nicht angenommen. (Unbekannter Tag)')
  })
})

/* ---------------------------------------------------------------- analysis */

describe('analysis: notes and manual values (E1)', () => {
  const samples: [number, number, null][] = [
    [at(1), 2.5, null],
    [at(2), 3, null],
  ]

  async function mountAnalysis(rights: { canAddNote?: boolean; canEnter?: boolean; noteEditable?: boolean }, extra: Partial<Record<'get' | 'post' | 'put', Fn>> = {}) {
    const { noteEditable = true, ...rowRights } = rights
    const cfg = config([chart(0, 'LINE', [row('a', { objectId: 8100, name: 'Strom - Gesamt', ...rowRights })])])
    const answer = data([
      {
        id: 0,
        rows: [
          {
            key: 'a',
            status: 'OK',
            unit: 'kWh',
            samples,
            stats: { min: 2.5, max: 3, avg: 2.75, sum: 5.5, count: 2 },
            notes: [{ timestamp: at(2), text: 'Wartung', tags: ['13'], user: 'admin', id: '9000-' + at(2), canEdit: noteEditable, version: 'v1' }],
          },
        ],
      },
    ])
    const get = extra.get ?? vi.fn(async (path: string) => (path.endsWith('/entry') ? entryInfo() : cfg))
    const post =
      extra.post ??
      vi.fn(async (path: string) => {
        if (path === '/web/analyses/1/data') return answer
        return {}
      })
    const w = await mountWith(AnalysisView, { get, post, put: extra.put ?? vi.fn() }, { id: 1 }, '/analyses/1')
    return { w, get, post }
  }

  const dataLoads = (post: Fn) => post.mock.calls.filter((c) => c[0] === '/web/analyses/1/data').length

  it('without rights: no row actions, no hint, clicks do nothing', async () => {
    const { w } = await mountAnalysis({ noteEditable: false })
    expect(w.find('.row-note').exists()).toBe(false)
    expect(w.find('.row-entry').exists()).toBe(false)
    expect(w.find('.note-hint').exists()).toBe(false)
    w.findComponent({ name: 'VChart' }).vm.$emit('click', { componentType: 'series', seriesIndex: 0, value: [at(1), 2.5, null] })
    w.findComponent({ name: 'VChart' }).vm.$emit('click', { componentType: 'markPoint', seriesIndex: 0, data: { coord: [at(2), 3] } })
    await flushPromises()
    expect(dialog(w).exists()).toBe(false)
  })

  it('an editable note alone makes the chart clickable (edit only, no new notes)', async () => {
    const { w } = await mountAnalysis({})
    expect(w.find('.note-hint').exists()).toBe(true)
    w.findComponent({ name: 'VChart' }).vm.$emit('click', { componentType: 'series', seriesIndex: 0, value: [at(1), 2.5, null] })
    await flushPromises()
    expect(dialog(w).exists()).toBe(false)
    w.findComponent({ name: 'VChart' }).vm.$emit('click', { componentType: 'markPoint', seriesIndex: 0, data: { coord: [at(2), 3] } })
    await flushPromises()
    expect(dialog(w).find('h2').text()).toBe('Notiz bearbeiten')
  })

  it('a click on a point opens "Neue Notiz" for that row and time and reloads the data after saving', async () => {
    const { w, post } = await mountAnalysis({ canAddNote: true })
    expect(w.find('.note-hint').text()).toContain('Notiz hinzufügen oder bearbeiten')
    w.findComponent({ name: 'VChart' }).vm.$emit('click', { componentType: 'series', seriesIndex: 0, value: [at(1), 2.5, null] })
    await flushPromises()
    const d = dialog(w)
    expect(d.find('h2').text()).toBe('Neue Notiz')
    expect(d.text()).toContain('Strom - Gesamt')
    expect((d.find('input[type="datetime-local"]').element as HTMLInputElement).value).toBe('2026-09-01T00:00')
    await d.find('textarea').setValue('Spitze')
    await d.find('form').trigger('submit')
    await flushPromises()
    const call = post.mock.calls.find((c) => c[0] === '/web/rows/8100/notes?' + zq()) as unknown as [string, { timestamp: string; text: string; tags: string[] }]
    expect(Date.parse(call[1].timestamp)).toBe(at(1))
    expect(call[1]).toMatchObject({ text: 'Spitze', tags: [] })
    expect(dataLoads(post)).toBe(2)
    expect(w.find('.analysis-message').text()).toBe('Die Notiz wurde gespeichert.')
  })

  it('a click on a note marker opens the note for editing', async () => {
    const put = vi.fn(async () => ({}))
    const { w, post } = await mountAnalysis({ canAddNote: true }, { put })
    w.findComponent({ name: 'VChart' }).vm.$emit('click', { componentType: 'markPoint', seriesIndex: 0, data: { coord: [at(2), 3] } })
    await flushPromises()
    const d = dialog(w)
    expect(d.find('h2').text()).toBe('Notiz bearbeiten')
    expect((d.find('textarea').element as HTMLTextAreaElement).value).toBe('Wartung')
    await d.find('form').trigger('submit')
    await flushPromises()
    expect(put).toHaveBeenCalledWith('/web/notes/9000-' + at(2) + '?' + zq(), { text: 'Wartung', version: 'v1' })
    expect(dataLoads(post)).toBe(2)
  })

  it('"Wert erfassen": mode DAY prefilled with the suggested day; 409 warnings, then "Trotzdem speichern" with confirm', async () => {
    let call = 0
    const answer = data([{ id: 0, rows: [{ key: 'a', status: 'OK', unit: 'kWh', samples, stats: { min: 2.5, max: 3, avg: 2.75, sum: 5.5, count: 2 } }] }])
    const post = vi.fn(async (path: string, _body?: unknown) => {
      if (path === '/web/analyses/1/data') return answer
      call++
      if (call === 1) throw httpError(409, { warnings: ['Der Zählerstand ist kleiner als der letzte Wert.'] })
      return { timestamp: new Date(2026, 10, 12).toISOString(), value: 1900.5, warnings: [] }
    })
    const { w, get } = await mountAnalysis({ canEnter: true }, { post })
    await w.find('.row-entry').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/rows/8100/entry')
    const d = dialog(w)
    expect(d.find('h2').text()).toBe('Wert erfassen')
    const time = d.find('input.entry-time')
    expect(time.attributes('type')).toBe('date')
    expect((time.element as HTMLInputElement).value).toBe('2026-11-12')
    expect(sp(d.find('.entry-last').text())).toBe('1.812,5 kWh · 11.11.2026')
    expect(d.text()).toContain('Zählerstand')

    await d.find('input.entry-value').setValue('1900,5')
    await d.find('form').trigger('submit')
    await flushPromises()
    const first = post.mock.calls.find((c) => c[0] === '/web/rows/8100/values?lang=de')!
    expect(first[1]).toMatchObject({ value: 1900.5, confirm: false })
    // wall time of the site's zone, without offset
    expect((first[1] as { timestamp: string }).timestamp).toBe('2026-11-12T00:00:00')
    expect(dialog(w).find('.entry-warnings').text()).toContain('Der Zählerstand ist kleiner als der letzte Wert.')

    await dialog(w).find('.entry-force').trigger('click')
    await flushPromises()
    const last = post.mock.calls.filter((c) => c[0] === '/web/rows/8100/values?lang=de').pop()!
    expect(last[1]).toMatchObject({ value: 1900.5, confirm: true })
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.analysis-message').text()).toBe('Wert 1.900,5 kWh für 12.11.2026 gespeichert.')
    expect(dataLoads(post)).toBe(2)
  })

  it('"Wert erfassen": invalid number is not sent, 403 shows the reason, no right hides the form', async () => {
    const post = vi.fn(async (path: string) => {
      if (path === '/web/analyses/1/data') return data([{ id: 0, rows: [{ key: 'a', status: 'OK', samples }] }])
      throw httpError(403, 'Kein Schreibrecht')
    })
    const { w } = await mountAnalysis({ canEnter: true }, { post })
    await w.find('.row-entry').trigger('click')
    await flushPromises()
    await dialog(w).find('input.entry-value').setValue('zwölf')
    await dialog(w).find('form').trigger('submit')
    expect(post.mock.calls.some((c) => String(c[0]).startsWith('/web/rows/8100/values'))).toBe(false)
    expect(dialog(w).find('[role="alert"]').text()).toBe('Bitte eine Zahl eingeben.')
    await dialog(w).find('input.entry-value').setValue('12')
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(dialog(w).find('[role="alert"]').text()).toBe('Dafür fehlt Ihnen die Berechtigung. (Kein Schreibrecht)')
  })

  it('"Wert erfassen" with canEnter false in the entry answer shows no form', async () => {
    const cfg = config([chart(0, 'LINE', [row('a', { objectId: 8100, canEnter: true })])])
    const get = vi.fn(async (path: string) => (path.endsWith('/entry') ? entryInfo({ canEnter: false }) : cfg))
    const { w } = await mountAnalysis({ canEnter: true }, { get })
    await w.find('.row-entry').trigger('click')
    await flushPromises()
    expect(dialog(w).text()).toContain('Sie dürfen für diese Datenreihe keine Werte erfassen.')
    expect(dialog(w).find('form').exists()).toBe(false)
    expect(dialog(w).find('.edit-submit').exists()).toBe(false)
  })

  it('without a suggestion the input starts at now; another site zone is named', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 12, 9, 30))
    const zone = browserTimeZone() === 'Asia/Tokyo' ? 'Europe/Berlin' : 'Asia/Tokyo'
    const cfg = config([chart(0, 'LINE', [row('a', { objectId: 8100, canEnter: true })])])
    const get = vi.fn(async (path: string) =>
      path.endsWith('/entry') ? entryInfo({ mode: 'SPECIFIC_DATETIME', suggestedTimestamp: null, last: null, timeZone: zone }) : cfg,
    )
    const { w } = await mountAnalysis({ canEnter: true }, { get })
    await w.find('.row-entry').trigger('click')
    await flushPromises()
    const expected = new Intl.DateTimeFormat('sv-SE', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
      .format(new Date(2026, 10, 12, 9, 30))
      .replace(' ', 'T')
    expect((dialog(w).find('input.entry-time').element as HTMLInputElement).value).toBe(expected)
    expect(dialog(w).text()).toContain('Zeitangabe in der Zeitzone des Standorts (' + zone + ').')
    expect(dialog(w).find('.entry-last').text()).toBe('Noch kein Wert')
  })

  it('the month and year modes use month / year inputs', async () => {
    for (const [mode, type, value] of [
      ['MONTH', 'month', '2026-12'],
      ['YEAR', 'number', '2026'],
      ['SPECIFIC_DATETIME', 'datetime-local', '2026-12-01T00:00'],
    ]) {
      const cfg = config([chart(0, 'LINE', [row('a', { objectId: 8100, canEnter: true })])])
      const get = vi.fn(async (path: string) => (path.endsWith('/entry') ? entryInfo({ mode, suggestedTimestamp: new Date(2026, 11, 1).toISOString() }) : cfg))
      const { w } = await mountAnalysis({ canEnter: true }, { get })
      await w.find('.row-entry').trigger('click')
      await flushPromises()
      const input = dialog(w).find('input.entry-time')
      expect(input.attributes('type')).toBe(type)
      expect((input.element as HTMLInputElement).value).toBe(value)
      w.unmount()
      mounted.splice(mounted.indexOf(w), 1)
    }
  })
})

/* ---------------------------------------------------------------- registers */

describe('registers: manual values in the details (E1)', () => {
  /** history of the target / base data row: rowId and per sample manual / canDelete from the server */
  const history = (rowId: number, canDelete = true) => ({
    unit: 'm²',
    rowId,
    samples: [
      { timestamp: '2026-11-01T00:00:00.000+01:00', value: 45, note: 'Manuelle Dateneingabe 2026-11-12 09:00', manual: true, canDelete },
      { timestamp: '2026-10-01T00:00:00.000+02:00', value: 42, note: 'Import', manual: false, canDelete: false },
    ],
  })

  function api(hist: unknown, entry: unknown = entryInfo()) {
    return vi.fn(async (path: string) => {
      if (path.startsWith('/web/registers/objects/')) return hist
      if (path.endsWith('/entry')) return entry
      throw new Error('unexpected ' + path)
    })
  }

  const props = (canEnter: boolean) => ({ kind: 'basedata', row: baseData(5100, { name: 'Musterwerk - Fläche', canEnter }), columns: [], today: '2026-11-12' })

  it('rows without canEnter: no "Wert erfassen", no /entry call, no delete', async () => {
    const get = api(history(5100, false))
    const w = await mountWith(RegisterDetails, { get }, props(false))
    expect(w.find('.reg-entry').exists()).toBe(false)
    expect(w.find('.reg-value-delete').exists()).toBe(false)
    expect(get.mock.calls.some((c) => String(c[0]).endsWith('/entry'))).toBe(false)
  })

  it('"Wert erfassen" for base data, saved value reloads history and the list', async () => {
    const get = api(history(5100), entryInfo({ id: 5100, name: 'Musterwerk - Fläche', unit: 'm²', counter: false, mode: 'MONTH', limits: null }))
    const post = vi.fn(async (_path: string, _body: unknown) => ({ timestamp: new Date(2026, 11, 1).toISOString(), value: 46, warnings: [] }))
    const w = await mountWith(RegisterDetails, { get, post }, props(true))
    expect(get.mock.calls.some((c) => String(c[0]).endsWith('/entry'))).toBe(false)
    await w.find('.reg-entry').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/rows/5100/entry')
    await dialog(w).find('input.entry-value').setValue('46')
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/web/rows/5100/values?lang=de')
    expect(w.find('.reg-entry-message').text()).toBe('Wert 46 m² für Dezember 2026 gespeichert.')
    expect(w.emitted('changed')).toHaveLength(1)
    expect(get.mock.calls.filter((c) => String(c[0]).startsWith('/web/registers/objects/'))).toHaveLength(2)
  })

  it('meters enter and delete values of the target data row, not of the meter', async () => {
    const get = api(history(9100), entryInfo({ id: 9100 }))
    const post = vi.fn(async (_path: string, _body: unknown) => ({ timestamp: new Date(2026, 10, 12).toISOString(), value: 1, warnings: [] }))
    const del = vi.fn(async () => undefined)
    const w = await mountWith(RegisterDetails, { get, post, delete: del }, { kind: 'meters', row: meter(100, { canEnter: true }), columns: [], today: '2026-11-12' })
    expect(get).toHaveBeenCalledWith('/web/registers/objects/100/history?limit=12')
    await w.find('.reg-entry').trigger('click')
    await flushPromises()
    expect(get).toHaveBeenCalledWith('/web/rows/9100/entry')
    await dialog(w).find('input.entry-value').setValue('1')
    await dialog(w).find('form').trigger('submit')
    await flushPromises()
    expect(post.mock.calls[0][0]).toBe('/web/rows/9100/values?lang=de')
    await w.find('.reg-value-delete').trigger('click')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/rows/9100/values?timestamp=2026-11-01T00%3A00%3A00.000%2B01%3A00')
  })

  it('only samples with canDelete get a delete button, deleted after a question via the history row', async () => {
    const del = vi.fn(async () => undefined)
    const w = await mountWith(RegisterDetails, { get: api(history(5100)), delete: del }, props(true))
    const buttons = w.findAll('.reg-value-delete')
    expect(buttons).toHaveLength(1)
    await buttons[0].trigger('click')
    expect(sp(dialog(w).text())).toContain('Den manuell erfassten Wert vom')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/rows/5100/values?timestamp=2026-11-01T00%3A00%3A00.000%2B01%3A00')
    expect(dialog(w).exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
  })

  it('a refused delete (403) stays in the dialog with the reason', async () => {
    const del = vi.fn(async () => {
      throw httpError(403, 'Kein manueller Wert')
    })
    const w = await mountWith(RegisterDetails, { get: api(history(5100)), delete: del }, props(true))
    await w.find('.reg-value-delete').trigger('click')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(dialog(w).find('[role="alert"]').text()).toBe('Dafür fehlt Ihnen die Berechtigung. (Kein manueller Wert)')
  })
})

/* ---------------------------------------------------------------- appointments / maintenance */

describe('appointments and maintenance (E1)', () => {
  it('appointments without canWrite: no "Erledigt", read-only notice', async () => {
    const get = vi.fn(async () => ({ today: '2026-11-12', appointments: [appointment(1)] }))
    const w = await mountWith(AppointmentsView, { get })
    await w.find('tbody .mod-toggle').trigger('click')
    expect(w.find('.ap-done').exists()).toBe(false)
    expect(w.text()).toContain('Nur Ansicht – Einträge werden im Control Center bearbeitet.')
  })

  it('without version there is no "Erledigt" (the server would answer 400 "version missing")', async () => {
    const get = vi.fn(async () => ({ today: '2026-11-12', appointments: [appointment(1, { canWrite: true })] }))
    const w = await mountWith(AppointmentsView, { get })
    await w.find('tbody .mod-toggle').trigger('click')
    expect(w.find('.ap-done').exists()).toBe(false)
  })

  it('a 400 (e.g. already done elsewhere) closes the question and reloads the list', async () => {
    const a = appointment(1, { canWrite: true, version: 'v1' })
    const get = vi.fn(async () => ({ today: '2026-11-12', appointments: [a] }))
    const post = vi.fn(async () => {
      throw httpError(400, 'already done')
    })
    const w = await mountWith(AppointmentsView, { get, post })
    await w.find('tbody .mod-toggle').trigger('click')
    await w.find('.ap-done').trigger('click')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.ap-message').text()).toBe('Die Eingabe wurde nicht angenommen. (already done)')
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('"Erledigt" on a recurring appointment asks, sends the version and shows the next due date', async () => {
    const a = appointment(1, { title: 'Zählerablesung Musterwerk', recurrence: 'MONTHLY', canWrite: true, version: 'v1' })
    const get = vi.fn(async () => ({ today: '2026-11-12', appointments: [a] }))
    const post = vi.fn(async () => ({ ...a, due: '2027-01-01T09:00', lastDone: '2026-11-12T10:00', version: 'v2' }))
    const w = await mountWith(AppointmentsView, { get, post })
    expect(w.text()).toContain('der Status kann hier geändert werden')
    await w.find('tbody .mod-toggle').trigger('click')
    await w.find('.ap-done').trigger('click')
    expect(post).not.toHaveBeenCalled()
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/appointments/1/done', { version: 'v1' })
    expect(w.find('.ap-message').text()).toBe('„Zählerablesung Musterwerk“ ist erledigt. Nächste Fälligkeit: 01.01.2027, 09:00.')
    expect(w.find('tbody').text()).toContain('01.01.2027')
  })

  it('a version conflict (409) reloads the list with a message', async () => {
    const a = appointment(1, { canWrite: true, version: 'v1' })
    const get = vi.fn(async () => ({ today: '2026-11-12', appointments: [a] }))
    const post = vi.fn(async () => {
      throw httpError(409, { ...a, version: 'v2' })
    })
    const w = await mountWith(AppointmentsView, { get, post })
    await w.find('tbody .mod-toggle').trigger('click')
    await w.find('.ap-done').trigger('click')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(w.find('.ap-message').text()).toContain('Der Eintrag wurde inzwischen geändert.')
    expect(get).toHaveBeenCalledTimes(2)
    expect(dialog(w).exists()).toBe(false)
  })

  it('maintenance: status buttons only with canWrite; a change sends status and version', async () => {
    const e = maintenanceEntry(2, { title: 'Wartung Musterwerk', canWrite: true, version: 'v1' })
    const get = vi.fn(async () => ({ now: '2026-11-12T10:00', entries: [e, maintenanceEntry(3)] }))
    const post = vi.fn(async () => ({ ...e, status: 'IN_PROGRESS', version: 'v2' }))
    const w = await mountWith(MaintenanceView, { get, post })
    const toggles = w.findAll('tbody .mod-toggle')
    await toggles[0].trigger('click')
    await toggles[1].trigger('click')
    const groups = w.findAll('.mt-status')
    expect(groups).toHaveLength(1)
    const buttons = groups[0].findAll('.mt-status-btn')
    expect(buttons.map((b) => [b.text(), b.attributes('aria-pressed')])).toEqual([
      ['Geplant', 'true'],
      ['In Arbeit', 'false'],
      ['Erledigt', 'false'],
    ])
    await buttons[1].trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/maintenance/2/status', { status: 'IN_PROGRESS', version: 'v1' })
    expect(w.find('.mt-message').text()).toBe('Status von „Wartung Musterwerk“: In Arbeit.')
    expect(w.find('.mt-status-btn[aria-pressed="true"]').text()).toBe('In Arbeit')
  })

  it('maintenance: 403 shows the reason without reloading', async () => {
    const e = maintenanceEntry(2, { canWrite: true, version: 'v1' })
    const get = vi.fn(async () => ({ now: '2026-11-12T10:00', entries: [e] }))
    const post = vi.fn(async () => {
      throw httpError(403, 'Kein Schreibrecht')
    })
    const w = await mountWith(MaintenanceView, { get, post })
    await w.find('tbody .mod-toggle').trigger('click')
    await w.findAll('.mt-status-btn')[2].trigger('click')
    await flushPromises()
    expect(w.find('.mt-message').text()).toBe('Dafür fehlt Ihnen die Berechtigung. (Kein Schreibrecht)')
    expect(get).toHaveBeenCalledTimes(1)
  })
})
