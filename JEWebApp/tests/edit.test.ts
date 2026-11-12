/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import { confirmAlarmConfiguration, confirmAllAlarms } from '../src/api/alarms'
import { deleteValue, loadEntryInfo, saveValue } from '../src/api/entry'
import { HttpClient, HttpError } from '../src/api/http'
import { completeAppointment, setMaintenanceStatus } from '../src/api/modules'
import { createNote, deleteNote, noteTargetId, updateNote } from '../src/api/notes'
import { buildChartViews } from '../src/analysis/model'
import { editableRowNote, nearestSampleTime, notePointFromClick, rowNotes } from '../src/analysis/notes'
import { xyOption } from '../src/analysis/options'
import { fromDateTimeInput, isoWithOffset, parseUserNumber, serverReason, toDateTimeInput, toMinute, useBusy, writeErrorKind, writeErrorText } from '../src/edit/edit'
import { entryInputType, entryMode, entryTimestamp, formatEntryTime, toEntryInput, wallTime } from '../src/edit/entry'
import { chosenTags, customTags, editableTags, EDITABLE_TAG_IDS, tagsToSend } from '../src/notes/notes'
import { at, chart, config, data, row } from './fixtures/analysis'
import { httpError } from './fixtures/edit'

const client = () => {
  const http = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
  http.get.mockResolvedValue({})
  http.post.mockResolvedValue({})
  http.put.mockResolvedValue({})
  http.delete.mockResolvedValue(undefined)
  return http
}

describe('HttpClient write methods', () => {
  const hooks = { accessToken: () => 'tok', refresh: async () => false, onUnauthorized: () => {} }

  it('PUT and DELETE send method, token and JSON body', async () => {
    const fetchFn = vi.fn(async () => new Response('{"ok":true}', { status: 200 }))
    const http = new HttpClient(hooks, fetchFn as unknown as typeof fetch)
    expect(await http.put('/x', { a: 1 })).toEqual({ ok: true })
    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit & { headers: Record<string, string> }]
    expect(url).toBe('/JEWebService/v1/x')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe('{"a":1}')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.headers.Authorization).toBe('Bearer tok')

    fetchFn.mockResolvedValueOnce(new Response(null, { status: 204 }))
    expect(await http.delete('/y')).toBeUndefined()
    const [, del] = fetchFn.mock.calls[1] as unknown as [string, RequestInit]
    expect(del.method).toBe('DELETE')
    expect(del.body).toBeUndefined()
  })

  it('a 409 keeps its JSON body for the caller', async () => {
    const fetchFn = vi.fn(async () => new Response('{"warnings":["zu hoch"]}', { status: 409 }))
    const http = new HttpClient(hooks, fetchFn as unknown as typeof fetch)
    const e = (await http.post('/z', {}).catch((err: unknown) => err)) as HttpError
    expect(e).toBeInstanceOf(HttpError)
    expect(e.status).toBe(409)
    expect(e.json()).toEqual({ warnings: ['zu hoch'] })
    expect(new HttpError(400, 'kein JSON').json()).toBeNull()
    expect(new HttpError(409, '').json()).toBeNull()
  })
})

describe('E1 API functions', () => {
  it('alarms: confirm one configuration and all shown ones', async () => {
    const http = client()
    await confirmAlarmConfiguration(http, 24202, true)
    expect(http.post).toHaveBeenLastCalledWith('/web/alarms/configurations/24202/confirm', { checked: true })
    await confirmAlarmConfiguration(http, 24202, false)
    expect(http.post).toHaveBeenLastCalledWith('/web/alarms/configurations/24202/confirm', { checked: false })
    await confirmAllAlarms(http, [1, 2])
    expect(http.post).toHaveBeenLastCalledWith('/web/alarms/confirm-all', { ids: [1, 2] })
  })

  it('notes: create on the row, change and delete with version', async () => {
    const http = client()
    await createNote(http, 8100, { timestamp: '2026-11-10T14:15:00+01:00', text: 'Prüfung', tags: ['4', '13'] }, 'Europe/Berlin')
    expect(http.post).toHaveBeenLastCalledWith('/web/rows/8100/notes?timeZone=Europe%2FBerlin', { timestamp: '2026-11-10T14:15:00+01:00', text: 'Prüfung', tags: ['4', '13'] })
    await updateNote(http, '9000-1700000000000', { text: 'neu', version: 'a1b2c3d4e5f60718' }, 'Europe/Berlin')
    expect(http.put).toHaveBeenLastCalledWith('/web/notes/9000-1700000000000?timeZone=Europe%2FBerlin', { text: 'neu', version: 'a1b2c3d4e5f60718' })
    await deleteNote(http, '9000-1700000000000', 'a1b2c3d4e5f60718', 'Europe/Berlin')
    expect(http.delete).toHaveBeenLastCalledWith('/web/notes/9000-1700000000000?version=a1b2c3d4e5f60718&timeZone=Europe%2FBerlin')
    await deleteNote(http, '9000-1', 'v')
    expect(http.delete).toHaveBeenLastCalledWith('/web/notes/9000-1?version=v')
  })

  it('notes: the target row is the number notesTarget; null or missing = no new notes', () => {
    expect(noteTargetId(null)).toBeNull()
    expect(noteTargetId({ id: 1, readable: true })).toBeNull()
    expect(noteTargetId({ id: 1, readable: true, notesTarget: null })).toBeNull()
    expect(noteTargetId({ id: 1, readable: true, notesTarget: 2 })).toBe(2)
  })

  it('manual values: entry info, save with confirm flag, delete by timestamp', async () => {
    const http = client()
    await loadEntryInfo(http, 8100)
    expect(http.get).toHaveBeenLastCalledWith('/web/rows/8100/entry')
    await saveValue(http, 8100, { timestamp: '2026-11-12T00:00:00', value: 12.5, confirm: false }, 'de')
    expect(http.post).toHaveBeenLastCalledWith('/web/rows/8100/values?lang=de', { timestamp: '2026-11-12T00:00:00', value: 12.5, confirm: false })
    await saveValue(http, 8100, { timestamp: '2026-11-12T00:00:00', value: 1, confirm: true })
    expect(http.post).toHaveBeenLastCalledWith('/web/rows/8100/values', { timestamp: '2026-11-12T00:00:00', value: 1, confirm: true })
    await deleteValue(http, 8100, '2026-11-12T00:00:00.000+01:00')
    expect(http.delete).toHaveBeenLastCalledWith('/web/rows/8100/values?timestamp=2026-11-12T00%3A00%3A00.000%2B01%3A00')
  })

  it('appointments and maintenance send the loaded version', async () => {
    const http = client()
    await completeAppointment(http, 5, '2026-11-01T08:00:00+01:00')
    expect(http.post).toHaveBeenLastCalledWith('/web/appointments/5/done', { version: '2026-11-01T08:00:00+01:00' })
    await setMaintenanceStatus(http, 6, 'IN_PROGRESS', 'v1')
    expect(http.post).toHaveBeenLastCalledWith('/web/maintenance/6/status', { status: 'IN_PROGRESS', version: 'v1' })
  })
})

describe('edit helpers', () => {
  const t = (key: string) => key

  it('maps HTTP errors to kinds and adds the plain-text reason of 400 / 403', () => {
    expect(writeErrorKind(httpError(400))).toBe('invalid')
    expect(writeErrorKind(httpError(403))).toBe('forbidden')
    expect(writeErrorKind(httpError(404))).toBe('notFound')
    expect(writeErrorKind(httpError(409))).toBe('conflict')
    expect(writeErrorKind(httpError(500))).toBe('failed')
    expect(writeErrorKind(new Error('network'))).toBe('failed')
    expect(writeErrorText(httpError(403, 'Kein Schreibrecht'), t)).toBe('edit.error.forbidden (Kein Schreibrecht)')
    expect(writeErrorText(httpError(400, '<html>'), t)).toBe('edit.error.invalid')
    expect(writeErrorText(httpError(409, { a: 1 }), t, 'noteEdit.exists')).toBe('noteEdit.exists')
    expect(writeErrorText(httpError(500, 'Stacktrace'), t)).toBe('edit.error.failed')
    expect(serverReason(httpError(400, 'x'.repeat(400))).length).toBe(301)
  })

  it('useBusy ignores a second run while one is running', async () => {
    const { busy, run } = useBusy()
    let release!: () => void
    const action = vi.fn(() => new Promise<void>((r) => (release = r)))
    const first = run(action)
    expect(busy.value).toBe(true)
    expect(await run(action)).toBeUndefined()
    expect(action).toHaveBeenCalledTimes(1)
    release()
    await first
    expect(busy.value).toBe(false)
  })

  it('ISO with offset and datetime-local to the minute', () => {
    const ms = new Date(2026, 10, 10, 14, 15, 42).getTime()
    expect(isoWithOffset(ms)).toMatch(/^2026-11-10T14:15:42[+-]\d{2}:\d{2}$/)
    expect(Date.parse(isoWithOffset(ms))).toBe(ms)
    expect(toDateTimeInput(ms)).toBe('2026-11-10T14:15')
    expect(fromDateTimeInput('2026-11-10T14:15')).toBe(new Date(2026, 10, 10, 14, 15).getTime())
    expect(fromDateTimeInput('')).toBeNull()
    expect(toMinute(ms)).toBe(new Date(2026, 10, 10, 14, 15).getTime())
  })

  it('parses numbers typed with comma or point', () => {
    expect(parseUserNumber('12,5', 'de')).toBe(12.5)
    expect(parseUserNumber('1.234,5', 'de')).toBe(1234.5)
    expect(parseUserNumber('1,234.5', 'en')).toBe(1234.5)
    expect(parseUserNumber('-3.25', 'en')).toBe(-3.25)
    expect(parseUserNumber(' 7 ', 'de')).toBe(7)
    expect(parseUserNumber('', 'de')).toBeNull()
    expect(parseUserNumber('abc', 'de')).toBeNull()
    expect(parseUserNumber('1,2,3', 'de')).toBeNull()
  })
})

describe('manual values: timestamp per mode', () => {
  const ms = new Date(2026, 10, 12, 14, 15).getTime()

  it('input type and value per mode, unknown modes are date-time', () => {
    expect(entryMode('MONTH')).toBe('MONTH')
    expect(entryMode('WEEK')).toBe('SPECIFIC_DATETIME')
    expect(entryInputType('SPECIFIC_DATETIME')).toBe('datetime-local')
    expect(entryInputType('DAY')).toBe('date')
    expect(entryInputType('MONTH')).toBe('month')
    expect(entryInputType('YEAR')).toBe('number')
    expect(toEntryInput(ms, 'SPECIFIC_DATETIME')).toBe('2026-11-12T14:15')
    expect(toEntryInput(ms, 'DAY')).toBe('2026-11-12')
    expect(toEntryInput(ms, 'MONTH')).toBe('2026-11')
    expect(toEntryInput(ms, 'YEAR')).toBe('2026')
    expect(toEntryInput(null, 'DAY')).toBe('')
    expect(toEntryInput('kaputt', 'DAY')).toBe('')
  })

  it('sends wall time without offset, aligned like the server (year, month, day start)', () => {
    expect(entryTimestamp('2026', 'YEAR')).toBe('2026-01-01T00:00:00')
    expect(entryTimestamp('2026-11', 'MONTH')).toBe('2026-11-01T00:00:00')
    expect(entryTimestamp('2026-11-12', 'DAY')).toBe('2026-11-12T00:00:00')
    expect(entryTimestamp('2026-11-12T14:15', 'SPECIFIC_DATETIME')).toBe('2026-11-12T14:15:00')
    expect(entryTimestamp('2026-02-30', 'DAY')).toBeNull()
    expect(entryTimestamp('2026-13', 'MONTH')).toBeNull()
    expect(entryTimestamp('26', 'YEAR')).toBeNull()
    expect(entryTimestamp('', 'DAY')).toBeNull()
  })

  it('inputs and display use the site time zone', () => {
    // 23:30 UTC on 30 Nov is already 1 Dec in Berlin and still 30 Nov in New York
    const utc = Date.UTC(2026, 10, 30, 23, 30)
    expect(wallTime(utc, 'Europe/Berlin')).toEqual({ year: 2026, month: 12, day: 1, hour: 0, minute: 30 })
    expect(toEntryInput(utc, 'MONTH', 'Europe/Berlin')).toBe('2026-12')
    expect(toEntryInput(utc, 'DAY', 'America/New_York')).toBe('2026-11-30')
    expect(toEntryInput(utc, 'SPECIFIC_DATETIME', 'Europe/Berlin')).toBe('2026-12-01T00:30')
    expect(formatEntryTime(utc, 'DAY', 'de', 'Europe/Berlin')).toBe('01.12.2026')
    expect(formatEntryTime(utc, 'DAY', 'de', 'America/New_York')).toBe('30.11.2026')
    // unknown zone: browser time
    expect(toEntryInput(ms, 'DAY', 'Kein/Ort')).toBe('2026-11-12')
  })

  it('shows a timestamp as precise as the mode', () => {
    expect(formatEntryTime(ms, 'YEAR', 'de')).toBe('2026')
    expect(formatEntryTime(ms, 'MONTH', 'de')).toBe('November 2026')
    expect(formatEntryTime(ms, 'MONTH', 'en')).toBe('November 2026')
    expect(formatEntryTime(ms, 'DAY', 'de')).toBe('12.11.2026')
    expect(formatEntryTime(ms, 'SPECIFIC_DATETIME', 'de')).toBe('12.11.2026, 14:15')
    expect(formatEntryTime(null, 'DAY', 'de')).toBe('')
  })
})

describe('note tags', () => {
  const name = (id: string) => 'Tag ' + id

  it('chips are the standard tags 1–13 with names', () => {
    expect(EDITABLE_TAG_IDS).toHaveLength(13)
    expect(editableTags(name)[0]).toEqual({ id: '1', name: 'Tag 1' })
    expect(editableTags(name)[12]).toEqual({ id: '13', name: 'Tag 13' })
  })

  it('chosen tags leave out none and custom texts; custom texts are listed', () => {
    expect(chosenTags(['13', '0', '4', '4', 'Altlast'])).toEqual(['4', '13'])
    expect(customTags(['4', 'Altlast'])).toEqual(['Altlast'])
  })

  it('unchanged chips send no tags (the server keeps them, also custom ones), changed chips the standard tags', () => {
    expect(tagsToSend(['4', 'Altlast'], ['4'])).toBeUndefined()
    expect(tagsToSend(['4', 'Altlast'], ['4', '13'])).toEqual(['4', '13'])
    expect(tagsToSend(['4'], [])).toEqual([])
    expect(tagsToSend([], [])).toBeUndefined()
    expect(tagsToSend(null, ['2'])).toEqual(['2'])
  })
})

describe('notes from chart clicks', () => {
  const rows = [
    {
      key: 'a',
      name: 'Strom - Gesamt',
      type: 'LINE',
      readable: true,
      objectId: 8100,
      canAddNote: true,
      notes: [{ timestamp: at(2), text: 'Wartung', tags: ['13'], user: 'admin', id: '9000-1', canEdit: true, version: 'v' }],
      samples: [
        [at(1), 1, null],
        [at(2), 2, null],
        [at(3), 3, null],
      ] as [number, number, null][],
    },
    { key: 'b', name: 'Status', type: 'LOGICAL', readable: true, objectId: 8101, canAddNote: true, notes: [], samples: [] },
    { key: 'c', name: 'Gas', type: 'COLUMN', readable: true, objectId: 8102, canAddNote: false, notes: [], samples: [] },
  ]

  it('a point gives row and sample time, a marker its note time with the notes', () => {
    const point = notePointFromClick(rows, { componentType: 'series', seriesIndex: 0, value: [at(1), 1, null] })
    expect(point).toMatchObject({ rowKey: 'a', objectId: 8100, timestamp: at(1), notes: [], canAddNote: true })
    const marker = notePointFromClick(rows, { componentType: 'markPoint', seriesIndex: 0, data: { coord: [at(2), 2] } })
    expect(marker?.timestamp).toBe(at(2))
    expect(marker?.notes.map((n) => n.id)).toEqual(['9000-1'])
  })

  it('a click on the line snaps to the nearest sample', () => {
    expect(nearestSampleTime(rows[0].samples, at(2, 13))).toBe(at(3))
    expect(notePointFromClick(rows, { componentType: 'series', seriesIndex: 0 }, at(1, 2))?.timestamp).toBe(at(1))
    expect(notePointFromClick(rows, { componentType: 'series', seriesIndex: 0 })).toBeNull()
  })

  it('only line / area / column / scatter rows the user may write', () => {
    expect(notePointFromClick(rows, { componentType: 'series', seriesIndex: 1, value: [at(1), 1] })).toBeNull()
    expect(notePointFromClick(rows, { componentType: 'series', seriesIndex: 2, value: [at(1), 1] })).toBeNull()
    expect(notePointFromClick(rows, { componentType: 'xAxis', seriesIndex: 0 })).toBeNull()
    expect(notePointFromClick(rows, { componentType: 'series', seriesIndex: 9, value: [at(1), 1] })).toBeNull()
  })

  it('analysis notes keep id, canEdit and version; only those are editable', () => {
    const [n] = rowNotes([{ timestamp: at(1), text: 'x', tags: [], user: null, id: '9000-5', canEdit: true, version: 'v1' }])
    expect(n).toMatchObject({ id: '9000-5', canEdit: true, version: 'v1' })
    expect(editableRowNote(n)).toBe(true)
    expect(editableRowNote({ ...n, id: undefined })).toBe(false)
    expect(editableRowNote({ ...n, version: null })).toBe(false)
    expect(editableRowNote({ ...n, canEdit: false, canDelete: true })).toBe(true)
    const [d] = rowNotes([{ timestamp: at(1), text: 'x', tags: [], user: null, id: '9000-5', notesObject: 9000, canDelete: true, version: 'v1' }])
    expect(d).toMatchObject({ notesObject: 9000, canDelete: true })
    expect(d.canEdit).toBeUndefined()
  })

  it('rights of the rows come from configuration or data; lines then react to clicks', () => {
    const views = buildChartViews(
      config([chart(0, 'LINE', [row('a', { objectId: 8100, canAddNote: true }), row('b', { objectId: 8101 }), row('c', { readable: false, canEnter: true })])]),
      data([{ id: 0, rows: [{ key: 'a', status: 'OK', samples: [[at(1), 1, null]] }, { key: 'b', status: 'OK', samples: [[at(1), 1, null]], canEnter: true }] }]),
      'kein Zugriff',
    )
    const [a, b, c] = views[0].rows
    expect([a.canAddNote, a.canEnter, a.objectId]).toEqual([true, false, 8100])
    expect([b.canAddNote, b.canEnter]).toEqual([false, true])
    expect([c.canAddNote, c.canEnter]).toEqual([false, false])
    const ctx = { locale: 'de', labels: { noData: '', noPermission: '', error: '', count: '' } }
    const plain = xyOption(views[0], ctx) as unknown as { series: Record<string, unknown>[] }
    expect(plain.series[0].triggerLineEvent).toBeUndefined()
    const clickable = xyOption(views[0], { ...ctx, noteClicks: true }) as unknown as { series: Record<string, unknown>[] }
    expect(clickable.series.map((s) => s.triggerLineEvent)).toEqual([true, undefined, undefined])
  })
})
