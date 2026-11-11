/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../src/api/http'
import type { NoteList } from '../src/api/notes'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import NotesView from '../src/views/NotesView.vue'
import { note, noteList } from './fixtures/notes'

vi.mock('vue-echarts', () => ({ default: defineComponent({ name: 'VChart', props: ['option'], setup: () => () => h('div', { class: 'vchart' }) }) }))

const Stub = defineComponent({ render: () => h('div') })
const sp = (s: string) => s.replace(/\s+/g, ' ').trim()

async function mountView(get: ReturnType<typeof vi.fn>) {
  const auth = useAuthStore()
  Object.assign(auth.http, { get })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }] })
  await router.push('/')
  const wrapper = mount(NotesView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

function api(list: NoteList | Error, samples: unknown = { unit: 'kWh', samples: [[Date.parse('2026-11-03T08:00:00Z'), 1.5]], truncated: false }) {
  return vi.fn(async (path: string) => {
    if (path.startsWith('/web/notes?')) {
      if (list instanceof Error) throw list
      return list
    }
    if (path.startsWith('/web/rows/')) {
      if (samples instanceof Error) throw samples
      return samples
    }
    throw new Error('unexpected ' + path)
  })
}

const rows = (w: Awaited<ReturnType<typeof mountView>>) => w.findAll('tbody tr.notes-row')

describe('notes view (mocked API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 11, 12, 0))
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  const list = () =>
    noteList([
      note('n1', { text: 'Peak at start', tags: ['4'], user: 'Alice' }),
      note('n2', { timestamp: '2026-10-01T08:00:00.000+02:00', text: 'Check', tags: ['6'], user: 'Bob', row: { id: 8001, readable: false }, site: { id: 7001, name: 'Site B' } }),
    ])

  it('requests the last 3 months with time zone and language and lists the notes', async () => {
    const get = api(list())
    const w = await mountView(get)
    const path = get.mock.calls[0][0] as string
    expect(path).toContain('from=2026-08-11&to=2026-11-11')
    expect(path).toContain('timeZone=')
    expect(path).toContain('lang=de')
    expect(rows(w)).toHaveLength(2)
    expect(sp(rows(w)[0].text())).toContain('Peak at start')
    expect(sp(rows(w)[0].text())).toContain('Ereignis')
    expect(sp(rows(w)[1].text())).toContain('Kein Leserecht')
    w.unmount()
  })

  it('filters by tag chip, search toggles and shows the empty states', async () => {
    const w = await mountView(api(list()))
    const chip = w.findAll('.notes-chip').find((b) => b.text().startsWith('Fehler'))!
    await chip.trigger('click')
    expect(rows(w)).toHaveLength(1)
    expect(sp(rows(w)[0].text())).toContain('Check')
    await chip.trigger('click')
    await w.find('#notes-search').setValue('alice')
    expect(rows(w)).toHaveLength(1)
    const boxes = w.findAll('.notes-in input')
    await boxes[1].setValue(false)
    expect(rows(w)).toHaveLength(0)
    expect(w.text()).toContain('Keine Notiz passt')
    w.unmount()
  })

  it('opens the detail panel with the curve ±1 day; unreadable rows load nothing', async () => {
    const get = api(list())
    const w = await mountView(get)
    await rows(w)[0].trigger('click')
    await flushPromises()
    const call = get.mock.calls.find((c) => String(c[0]).startsWith('/web/rows/'))![0] as string
    expect(call).toContain('/web/rows/8000/samples?from=2026-11-02T08%3A15%3A00.000Z&to=2026-11-04T08%3A15%3A00.000Z')
    expect(w.find('.mod-detail .vchart').exists()).toBe(true)
    get.mockClear()
    await rows(w)[1].trigger('click')
    await flushPromises()
    expect(get).not.toHaveBeenCalled()
    expect(w.find('.mod-detail').text()).toContain('nicht gelesen')
    w.unmount()
  })

  it('handles a 404 of the samples', async () => {
    const w = await mountView(api(list(), new HttpError(404, '')))
    await rows(w)[0].trigger('click')
    await flushPromises()
    expect(w.find('.mod-detail').text()).toContain('nicht lesbar')
    w.unmount()
  })

  it('shows the truncated hint, the empty list and load errors', async () => {
    let w = await mountView(api(noteList([note('a')], { truncated: true })))
    expect(w.find('.notes-truncated').exists()).toBe(true)
    w.unmount()
    w = await mountView(api(noteList([])))
    expect(w.text()).toContain('keine Notizen')
    w.unmount()
    w = await mountView(api(new HttpError(500, '')))
    expect(w.find('.alert-error').exists()).toBe(true)
    w.unmount()
  })

  it('does not load an invalid time frame', async () => {
    const get = api(list())
    const w = await mountView(get)
    get.mockClear()
    await w.find('#notes-from').setValue('2026-12-01')
    await flushPromises()
    expect(get).not.toHaveBeenCalled()
    expect(w.text()).toContain('Enddatum liegt vor dem Startdatum')
    w.unmount()
  })
})
