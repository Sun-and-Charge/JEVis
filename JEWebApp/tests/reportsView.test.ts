/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../src/api/http'
import type { ReportFiles, ReportList } from '../src/api/reports'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import ReportsView from '../src/views/ReportsView.vue'
import { monthlyFiles, report } from './fixtures/reports'

const saveBlob = vi.hoisted(() => vi.fn())
vi.mock('../src/iso/download', async (importOriginal) => ({ ...(await importOriginal<typeof import('../src/iso/download')>()), saveBlob }))

const Stub = defineComponent({ render: () => h('div') })
const sp = (s: string) => s.replace(/\s+/g, ' ').trim()

const LIST: ReportList = {
  reports: [
    report(23801),
    report(23802, { name: 'Wochenbericht Gas', schedule: 'CUSTOM_X', enabled: false, path: 'Berichte / Gas', latest: { timestamp: '2026-11-09T07:00:00.000+01:00', pdf: true, xlsx: false, name: 'Wochenbericht Gas.pdf' } }),
    report(23803, { name: 'Jahresbericht Wasser', latest: null, count: 0, site: { id: 7101, name: 'Zweigwerk' } }),
  ],
}

type Answer<T> = T | Error | (() => T | Error)

function api(list: Answer<ReportList>, files: Record<number, Answer<ReportFiles>> = { 23801: monthlyFiles(), 23802: monthlyFiles(23802) }, pdfError?: Error) {
  const resolve = <T>(a: Answer<T>): T => {
    const v = typeof a === 'function' ? (a as () => T | Error)() : a
    if (v instanceof Error) throw v
    return v
  }
  const get = vi.fn(async (path: string) => {
    if (path === '/web/reports') return resolve(list)
    const m = /^\/web\/reports\/(\d+)\/files$/.exec(path)
    if (m && files[Number(m[1])]) return resolve(files[Number(m[1])])
    if (m) throw new HttpError(404, '')
    throw new Error('unexpected ' + path)
  })
  const getBlob = vi.fn(async (path: string) => {
    if (pdfError && path.includes('format=pdf') && path.includes('inline=true')) throw pdfError
    return new Blob(['%PDF'], { type: path.includes('format=xlsx') ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'application/pdf' })
  })
  return { get, getBlob }
}

async function mountView(client: ReturnType<typeof api>, query: Record<string, string> = {}) {
  const auth = useAuthStore()
  Object.assign(auth.http, client)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/reports', name: 'reports', component: Stub }] })
  await router.push({ path: '/reports', query })
  const wrapper = mount(ReportsView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

const items = (w: ReturnType<typeof mount>) => w.findAll('.rep-item')
const blobCalls = (getBlob: ReturnType<typeof vi.fn>) => getBlob.mock.calls.map((c) => c[0] as string)

describe('reports view (mocked API)', () => {
  let created: string[]
  let revoked: string[]

  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'de'
    saveBlob.mockClear()
    created = []
    revoked = []
    let n = 0
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn(() => {
          const url = `blob:test/${++n}`
          created.push(url)
          return url
        }),
        revokeObjectURL: vi.fn((url: string) => revoked.push(url)),
      }),
    )
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists the reports grouped by site, reports without output disabled', async () => {
    const client = api(LIST)
    const { wrapper: w } = await mountView(client)
    expect(w.text()).toContain('Nur Ansicht – Berichte werden im Control Center eingerichtet.')
    expect(w.findAll('.rep-group-title').map((g) => g.text())).toEqual(['Musterwerk', 'Zweigwerk'])
    expect(items(w)).toHaveLength(3)
    expect(items(w)[0].find('.rep-item-path').text()).toBe('Berichte')
    expect(items(w)[0].find('.rep-item-name').text()).toBe('Monatsbericht Energie')
    expect(items(w)[0].find('.rep-item-meta').text()).toMatch(/01\.11\.2026/)
    const empty = items(w)[2]
    expect(empty.attributes('disabled')).toBeDefined()
    expect(empty.text()).toContain('Noch kein Bericht')
    expect(w.find('.rep-placeholder').exists()).toBe(true)
    expect(client.get).toHaveBeenCalledTimes(1)
    expect(client.getBlob).not.toHaveBeenCalled()
    w.unmount()
  })

  it('selects a report, keeps ?id= in sync and shows outputs and the PDF preview', async () => {
    const client = api(LIST)
    const { wrapper: w, router } = await mountView(client)
    await items(w)[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.id).toBe('23801')
    expect(items(w)[0].attributes('aria-current')).toBe('true')
    expect(client.get).toHaveBeenCalledWith('/web/reports/23801/files')
    const detail = sp(w.find('.rep-detail').text())
    expect(detail).toContain('Musterwerk / Berichte')
    expect(detail).toContain('Monatlich')
    expect(detail).toContain('Aktiv')
    const options = w.findAll('.rep-output select option')
    expect(options).toHaveLength(2)
    expect(options[0].text()).toContain('Monatsbericht Energie_202610.pdf')
    expect(options[0].text()).toMatch(/01\.11\.2026/)
    expect(blobCalls(client.getBlob)).toEqual(['/web/reports/23801/file?format=pdf&ts=2026-11-01T06%3A00%3A12.000%2B01%3A00&inline=true'])
    const pdf = w.find('object.rep-pdf')
    expect(pdf.attributes('data')).toBe('blob:test/1')
    expect(pdf.attributes('type')).toBe('application/pdf')
    expect(w.find('.rep-open').attributes('href')).toBe('blob:test/1')
    w.unmount()
    expect(revoked).toEqual(['blob:test/1'])
  })

  it('opens a linked report, switches the output and revokes the old preview URL', async () => {
    const client = api(LIST)
    const { wrapper: w } = await mountView(client, { id: '23801' })
    expect(w.find('.rep-pdf').attributes('data')).toBe('blob:test/1')
    const xlsx = w.find('.rep-download[data-format="xlsx"]')
    expect(xlsx.attributes('disabled')).toBeUndefined()
    await w.find('.rep-output select').setValue('2026-10-01T06:00:09.000+02:00')
    await flushPromises()
    expect(revoked).toEqual(['blob:test/1'])
    expect(blobCalls(client.getBlob).at(-1)).toBe('/web/reports/23801/file?format=pdf&ts=2026-10-01T06%3A00%3A09.000%2B02%3A00&inline=true')
    expect(w.find('.rep-pdf').attributes('data')).toBe('blob:test/2')
    expect(w.find('.rep-download[data-format="xlsx"]').attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('keine Excel-Datei')
    w.unmount()
    expect(revoked).toEqual(['blob:test/1', 'blob:test/2'])
  })

  it('downloads PDF and Excel of the selected output with the right format and ts', async () => {
    const client = api(LIST)
    const { wrapper: w } = await mountView(client, { id: '23801' })
    client.getBlob.mockClear()
    await w.find('.rep-download[data-format="pdf"]').trigger('click')
    await flushPromises()
    await w.find('.rep-download[data-format="xlsx"]').trigger('click')
    await flushPromises()
    expect(blobCalls(client.getBlob)).toEqual([
      '/web/reports/23801/file?format=pdf&ts=2026-11-01T06%3A00%3A12.000%2B01%3A00',
      '/web/reports/23801/file?format=xlsx&ts=2026-11-01T06%3A00%3A11.000%2B01%3A00',
    ])
    expect(saveBlob.mock.calls.map((c) => c[1])).toEqual(['Monatsbericht Energie_202610.pdf', 'Monatsbericht Energie_202610.xlsx'])
    expect((saveBlob.mock.calls[1][0] as Blob).type).toContain('spreadsheetml')
    w.unmount()
  })

  it('shows a download error', async () => {
    const client = api(LIST)
    const { wrapper: w } = await mountView(client, { id: '23801' })
    client.getBlob.mockRejectedValueOnce(new HttpError(404, ''))
    await w.find('.rep-download[data-format="pdf"]').trigger('click')
    await flushPromises()
    expect(w.find('.rep-output [role="alert"]').text()).toContain('nicht mehr vorhanden')
    expect(saveBlob).not.toHaveBeenCalled()
    w.unmount()
  })

  it('shows stored schedules as they are and the disabled state', async () => {
    const { wrapper: w } = await mountView(api(LIST), { id: '23802' })
    const detail = sp(w.find('.rep-detail').text())
    expect(detail).toContain('CUSTOM_X')
    expect(detail).toContain('Deaktiviert')
    w.unmount()
  })

  it('shows a linked report without output without loading files', async () => {
    const client = api(LIST)
    const { wrapper: w } = await mountView(client, { id: '23803' })
    expect(w.find('.rep-detail').text()).toContain('noch keine Ausgabe')
    expect(client.get).toHaveBeenCalledTimes(1)
    expect(client.getBlob).not.toHaveBeenCalled()
    w.unmount()
  })

  it('shows unknown ids, unreadable reports (404) and file errors with retry', async () => {
    let { wrapper: w } = await mountView(api(LIST), { id: '999' })
    expect(w.find('.rep-missing').text()).toContain('nicht lesbar')
    w.unmount()

    let calls = 0
    const files = { 23801: () => (++calls === 1 ? new HttpError(404, '') : monthlyFiles()) }
    const client = api(LIST, files)
    ;({ wrapper: w } = await mountView(client, { id: '23801' }))
    expect(w.find('.rep-detail .alert-error').text()).toContain('Der Bericht ist nicht lesbar.')
    await w.find('.rep-detail .alert-error .retry').trigger('click')
    await flushPromises()
    expect(w.find('.rep-pdf').exists()).toBe(true)
    w.unmount()

    ;({ wrapper: w } = await mountView(api(LIST, { 23801: new HttpError(500, '') }), { id: '23801' }))
    expect(w.find('.rep-detail .alert-error').text()).toContain('konnten nicht geladen')
    w.unmount()
  })

  it('shows preview errors with retry', async () => {
    const client = api(LIST, undefined, new HttpError(404, ''))
    const { wrapper: w } = await mountView(client, { id: '23801' })
    expect(w.find('.rep-preview .alert-error').text()).toContain('PDF-Datei ist nicht lesbar')
    expect(created).toEqual([])
    client.getBlob.mockImplementationOnce(async () => new Blob(['%PDF'], { type: 'application/pdf' }))
    await w.find('.rep-preview .retry').trigger('click')
    await flushPromises()
    expect(w.find('.rep-pdf').attributes('data')).toBe('blob:test/1')
    w.unmount()
  })

  it('searches the list and shows the no-match state', async () => {
    const { wrapper: w } = await mountView(api(LIST))
    await w.find('#reports-search').setValue('gas')
    expect(items(w)).toHaveLength(1)
    expect(w.find('.mod-count').text()).toBe('1 von 3 Berichten')
    await w.find('#reports-search').setValue('nichts davon')
    expect(items(w)).toHaveLength(0)
    expect(w.text()).toContain('Kein Bericht passt')
    w.unmount()
  })

  it('shows load errors with retry and the empty list', async () => {
    let calls = 0
    const client = api(() => (++calls === 1 ? new HttpError(500, '') : LIST))
    let { wrapper: w } = await mountView(client)
    expect(w.find('.alert-error').exists()).toBe(true)
    await w.find('.alert-error .retry').trigger('click')
    await flushPromises()
    expect(items(w)).toHaveLength(3)
    w.unmount()
    ;({ wrapper: w } = await mountView(api({ reports: [] })))
    expect(w.text()).toContain('keine Berichte')
    w.unmount()
  })

  it('on phone width shows the list first and the report with a back button', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    const { wrapper: w, router } = await mountView(api(LIST))
    expect(w.find('.rep-side').exists()).toBe(true)
    expect(w.find('.rep-main').exists()).toBe(false)
    await items(w)[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.id).toBe('23802')
    expect(w.find('.rep-side').exists()).toBe(false)
    expect(w.find('.rep-detail h2').text()).toBe('Wochenbericht Gas')
    expect(document.activeElement?.id).toBe('report-detail-title')
    await w.find('.rep-back').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.id).toBeUndefined()
    expect(w.find('.rep-side').exists()).toBe(true)
    expect(document.activeElement?.id).toBe('report-item-23802')
    expect(revoked).toEqual(['blob:test/1'])
    w.unmount()
  })
})
