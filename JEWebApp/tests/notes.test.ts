/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import { buildChartViews } from '../src/analysis/model'
import { noteMarkers, noteMarkPoint, noteTooltip, rowNotes, sampleAt } from '../src/analysis/notes'
import { chartOption, type OptionContext } from '../src/analysis/options'
import { moduleCounts } from '../src/api/navigation'
import { notesPath, rowSamplesPath } from '../src/api/notes'
import { i18n } from '../src/i18n'
import { sortRows } from '../src/iso/columns'
import { DAY_MS, noteChartOption, noteWindow, numericRowSamples } from '../src/notes/chart'
import {
  checkNoteRange,
  defaultNoteFilter,
  defaultNoteRange,
  filterNotes,
  i18nTagName,
  minusMonths,
  noteColumns,
  noteSites,
  noteTagIds,
  notesCsv,
  notesCsvName,
  rowFullName,
  tagNamer,
  tagOptions,
  toggleTag,
  visibleTags,
  type NoteFilter,
} from '../src/notes/notes'
import { at, chart, config, data, row } from './fixtures/analysis'
import { note, STANDARD_TAGS } from './fixtures/notes'

const tagName = tagNamer(STANDARD_TAGS)
const filter = (overrides: Partial<NoteFilter> = {}): NoteFilter => ({ ...defaultNoteFilter(), ...overrides })
const ids = (list: { id: string }[]) => list.map((n) => n.id)

describe('note tags', () => {
  it('names tags from the answer, then the app translations, else the id', () => {
    const t = (k: string) => i18n.global.t(k)
    const te = (k: string) => i18n.global.te(k)
    i18n.global.locale.value = 'en'
    const name = tagNamer([{ id: '4', name: 'Ereignis' }], i18nTagName(t, te))
    expect(name('4')).toBe('Ereignis')
    expect(name('6')).toBe('Error')
    expect(name('Custom tag')).toBe('Custom tag')
    expect(name('99')).toBe('99')
    i18n.global.locale.value = 'de'
    expect(i18nTagName(t, te)('6')).toBe('Fehler')
  })

  it('treats notes without tags as "none" and hides "none" as badge', () => {
    expect(noteTagIds({ tags: [] })).toEqual(['0'])
    expect(noteTagIds({ tags: ['', '4'] })).toEqual(['4'])
    expect(visibleTags({ tags: ['0', '4', 'x'] })).toEqual(['4', 'x'])
    expect(visibleTags({ tags: [] })).toEqual([])
  })

  it('offers the used tags of the answer with counts, keeps selected ones and appends unknown ones', () => {
    const notes = [note('a', { tags: ['4'] }), note('b', { tags: ['4', '6'] }), note('c', { tags: [] }), note('d', { tags: ['Old text'] })]
    const options = tagOptions(STANDARD_TAGS, notes, ['1'], tagName)
    expect(options).toEqual([
      { id: '0', name: 'Kein', count: 1 },
      { id: '1', name: 'Audit', count: 0 },
      { id: '4', name: 'Ereignis', count: 2 },
      { id: '6', name: 'Fehler', count: 1 },
      { id: 'Old text', name: 'Old text', count: 1 },
    ])
    expect(tagOptions(STANDARD_TAGS, [note('x', { tags: ['4', '4'] })]).map((o) => o.count)).toEqual([1])
  })

  it('toggles tags of the filter', () => {
    expect(toggleTag([], '4')).toEqual(['4'])
    expect(toggleTag(['4', '6'], '4')).toEqual(['6'])
  })
})

describe('filterNotes', () => {
  const notes = [
    note('a', { text: 'Peak load at start', tags: ['4'], user: 'Alice', row: { id: 1, name: 'Power total', path: 'Data / Power', readable: true } }),
    note('b', { text: 'Check meter', tags: ['2', '6'], user: 'Bob', site: { id: 7001, name: 'Site B' } }),
    note('c', { text: 'Hidden row', tags: [], user: null, row: { id: 3, readable: false }, site: null }),
  ]

  it('keeps all notes without search and filters', () => {
    expect(ids(filterNotes(notes, filter(), tagName, 'de'))).toEqual(['a', 'b', 'c'])
  })

  it('filters by any of the selected tags, "none" matches notes without tags', () => {
    expect(ids(filterNotes(notes, filter({ tags: ['6'] }), tagName, 'de'))).toEqual(['b'])
    expect(ids(filterNotes(notes, filter({ tags: ['4', '2'] }), tagName, 'de'))).toEqual(['a', 'b'])
    expect(ids(filterNotes(notes, filter({ tags: ['0'] }), tagName, 'de'))).toEqual(['c'])
  })

  it('filters by site', () => {
    expect(ids(filterNotes(notes, filter({ site: 7001 }), tagName, 'de'))).toEqual(['b'])
    expect(ids(filterNotes(notes, filter({ site: 'none' }), tagName, 'de'))).toEqual(['c'])
  })

  it('searches only in the fields switched on (like the Control Center toggles)', () => {
    expect(ids(filterNotes(notes, filter({ text: 'peak' }), tagName, 'de'))).toEqual(['a'])
    expect(ids(filterNotes(notes, filter({ text: 'peak', inText: false }), tagName, 'de'))).toEqual([])
    expect(ids(filterNotes(notes, filter({ text: 'bob' }), tagName, 'de'))).toEqual(['b'])
    expect(ids(filterNotes(notes, filter({ text: 'bob', inUser: false }), tagName, 'de'))).toEqual([])
    expect(ids(filterNotes(notes, filter({ text: 'power total' }), tagName, 'de'))).toEqual(['a'])
    expect(ids(filterNotes(notes, filter({ text: 'power total', inRow: false }), tagName, 'de'))).toEqual([])
    expect(ids(filterNotes(notes, filter({ text: 'fehler' }), tagName, 'de'))).toEqual(['b'])
    expect(ids(filterNotes(notes, filter({ text: 'fehler', inTag: false }), tagName, 'de'))).toEqual([])
  })

  it('needs every word in one of the fields; no field switched on matches nothing', () => {
    expect(ids(filterNotes(notes, filter({ text: 'alice peak' }), tagName, 'de'))).toEqual(['a'])
    expect(ids(filterNotes(notes, filter({ text: 'alice check' }), tagName, 'de'))).toEqual([])
    const none = filter({ text: 'peak', inText: false, inUser: false, inRow: false, inTag: false })
    expect(filterNotes(notes, none, tagName, 'de')).toEqual([])
    expect(filterNotes(notes, { ...none, text: '  ' }, tagName, 'de')).toHaveLength(3)
  })

  it('does not search in rows without read right', () => {
    const hidden = [note('x', { text: '', user: null, row: { id: 3, name: 'secret', path: 'secret', readable: false } })]
    expect(filterNotes(hidden, filter({ text: 'secret' }), tagName, 'de')).toEqual([])
  })

  it('lists the sites of the notes', () => {
    expect(noteSites(notes, 'de')).toEqual([
      { id: 7000, name: 'Site A' },
      { id: 7001, name: 'Site B' },
    ])
  })
})

describe('note columns and CSV', () => {
  const env = { noAccess: 'No access', tagName, formatTime: (ms: number) => new Date(ms).toISOString() }
  const ctx = { locale: 'de', t: (k: string) => k }

  it('sorts by time and by tag names', () => {
    const notes = [
      note('old', { timestamp: '2026-10-01T08:00:00.000+02:00', tags: ['6'] }),
      note('new', { timestamp: '2026-11-05T08:00:00.000+01:00', tags: ['1'] }),
      note('mid', { timestamp: '2026-10-20T08:00:00.000+02:00', tags: ['4'] }),
    ]
    const columns = noteColumns(env)
    const date = columns.find((c) => c.id === 'date')
    expect(ids(sortRows(notes, date, 'desc', ctx))).toEqual(['new', 'mid', 'old'])
    expect(ids(sortRows(notes, date, 'asc', ctx))).toEqual(['old', 'mid', 'new'])
    expect(ids(sortRows(notes, columns.find((c) => c.id === 'tags'), 'asc', ctx))).toEqual(['new', 'mid', 'old'])
  })

  it('shows path and name of readable rows only', () => {
    expect(rowFullName(note('a'), 'No access')).toBe('Data / Power / Row A')
    expect(rowFullName(note('a', { row: { id: 5, readable: false } }), 'No access')).toBe('No access')
    expect(rowFullName(note('a', { row: null }), 'No access')).toBe('')
  })

  it('writes a CSV with BOM, the separators of the locale and escaped texts', () => {
    const notes = [
      note('a', { timestamp: new Date(2026, 10, 3, 9, 15).toISOString(), text: 'Line 1\nline; "two"', tags: ['4', '6'] }),
      note('b', { timestamp: new Date(2026, 10, 4, 10, 0).toISOString(), text: '=SUM(A1)', tags: [], user: null, row: { id: 2, readable: false }, site: null }),
    ]
    const headers = { date: 'Datum', site: 'Standort', path: 'Pfad', row: 'Datenpunkt', text: 'Notiz', tags: 'Tags', user: 'Benutzer' }
    const csv = notesCsv(notes, headers, env, 'de')
    expect(csv.startsWith('﻿')).toBe(true)
    const lines = csv.slice(1).split('\r\n')
    expect(lines[0]).toBe('Datum;Standort;Pfad;Datenpunkt;Notiz;Tags;Benutzer')
    expect(lines[1]).toBe('2026-11-03 09:15:00;Site A;Data / Power;Row A;"Line 1\nline; ""two""";Ereignis, Fehler;User A')
    expect(lines[2]).toBe("2026-11-04 10:00:00;;;No access;'=SUM(A1);;")
    expect(notesCsv(notes.slice(0, 1), headers, env, 'en').split('\r\n')[0]).toBe('﻿Datum,Standort,Pfad,Datenpunkt,Notiz,Tags,Benutzer')
    expect(csv.endsWith('\r\n')).toBe(true)
  })

  it('names the CSV after the time frame', () => {
    expect(notesCsvName('Notizen', '2026-08-11', '2026-11-11')).toBe('Notizen 2026-08-11 - 2026-11-11.csv')
  })
})

describe('time frame of the notes', () => {
  it('goes back 3 months, clamping the day', () => {
    const d = minusMonths(new Date(2026, 4, 31), 3)
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 1, 28])
    const e = minusMonths(new Date(2026, 1, 15), 3)
    expect([e.getFullYear(), e.getMonth(), e.getDate()]).toEqual([2025, 10, 15])
    expect(defaultNoteRange(new Date(2026, 10, 11, 15, 30))).toEqual({ from: '2026-08-11', to: '2026-11-11' })
  })

  it('checks the range before sending it', () => {
    expect(checkNoteRange('2026-08-11', '2026-11-11')).toBe('ok')
    expect(checkNoteRange('2026-11-11', '2026-11-11')).toBe('ok')
    expect(checkNoteRange('', '2026-11-11')).toBe('missing')
    expect(checkNoteRange('2026-11-12', '2026-11-11')).toBe('order')
    expect(checkNoteRange('2023-11-11', '2026-11-11')).toBe('ok')
    expect(checkNoteRange('2023-11-10', '2026-11-11')).toBe('tooLong')
  })

  it('builds the request paths', () => {
    expect(notesPath({ from: '2026-08-11', to: '2026-11-11', timeZone: 'Europe/Berlin', lang: 'de' })).toBe(
      '/web/notes?from=2026-08-11&to=2026-11-11&timeZone=Europe%2FBerlin&lang=de',
    )
    const ts = Date.UTC(2026, 10, 3, 8, 15)
    const { from, to } = noteWindow(ts)
    expect(to - from).toBe(2 * DAY_MS)
    expect(rowSamplesPath(42, from, to)).toBe('/web/rows/42/samples?from=2026-11-02T08%3A15%3A00.000Z&to=2026-11-04T08%3A15%3A00.000Z')
  })
})

describe('note markers in analyses', () => {
  const samples: [number, number, null][] = [
    [1000, 10, null],
    [2000, 20, null],
    [3000, 30, null],
  ]

  it('reads the notes of a data answer defensively', () => {
    expect(rowNotes(undefined)).toEqual([])
    expect(rowNotes('x')).toEqual([])
    expect(
      rowNotes([
        { timestamp: 2000, text: 'b', tags: ['4', 7], user: '' },
        { timestamp: 'x', text: 'bad' },
        null,
        { timestamp: 1000, text: null, tags: null, user: 'A' },
      ]),
    ).toEqual([
      { timestamp: 1000, text: '', tags: [], user: 'A' },
      { timestamp: 2000, text: 'b', tags: ['4'], user: null },
    ])
  })

  it('finds the sample a note belongs to', () => {
    expect(sampleAt([], 5)).toBeNull()
    expect(sampleAt(samples, 500)?.[0]).toBe(1000)
    expect(sampleAt(samples, 2000)?.[0]).toBe(2000)
    expect(sampleAt(samples, 2999)?.[0]).toBe(2000)
    expect(sampleAt(samples, 9000)?.[0]).toBe(3000)
  })

  it('groups notes by time and places them on the curve (plus stacked rows below)', () => {
    const notes = [
      { timestamp: 2500, text: 'one', tags: [], user: null },
      { timestamp: 2500, text: 'two', tags: [], user: null },
      { timestamp: 1000, text: 'first', tags: [], user: null },
    ]
    const markers = noteMarkers(samples, notes)
    expect(markers.map((m) => [m.timestamp, m.sampleTime, m.value, m.notes.length])).toEqual([
      [1000, 1000, 10, 1],
      [2500, 2000, 20, 2],
    ])
    expect(noteMarkers(samples, notes, (t) => t / 100).map((m) => m.value)).toEqual([20, 40])
    expect(noteMarkers([], notes)).toEqual([])
    expect(noteMarkers(samples, undefined)).toEqual([])
    expect(noteMarkPoint([], 'Row', '#000', { locale: 'de', title: 'Notiz', tagName })).toBeUndefined()
  })

  it('escapes the tooltip and shows text, tags and user', () => {
    const html = noteTooltip(
      { timestamp: at(2, 9), notes: [{ timestamp: at(2, 9), text: '<b>Peak</b>', tags: ['0', '4'], user: 'Alice' }] },
      'Row <A>',
      { locale: 'de', title: 'Notiz', tagName },
    )
    expect(html).toContain('&lt;b&gt;Peak&lt;/b&gt;')
    expect(html).toContain('Row &lt;A&gt;')
    expect(html).toContain('Ereignis · Alice')
    expect(html).not.toContain('Kein')
    expect(html).not.toContain('<b>Peak')
  })

  const ctx: OptionContext = { locale: 'de', labels: { noData: 'Keine Daten', noPermission: 'Kein Zugriff', error: 'Fehler', count: 'Anzahl', note: 'Notiz' }, tagName }

  function lineChart(type: string, rows: ReturnType<typeof row>[], answers: Record<string, unknown>[]) {
    return buildChartViews(config([chart(0, type, rows)]), data([{ id: 0, rows: answers as never }]), 'No access')[0]
  }

  it('adds a markPoint only to series of rows with notes; answers without notes still work', () => {
    const view = lineChart(
      'LINE',
      [row('a'), row('b'), row('c', { readable: false })],
      [
        { key: 'a', status: 'OK', samples: [[at(1), 1, null], [at(2), 2, null]], notes: [{ timestamp: at(2, 6), text: 'n', tags: ['4'], user: 'A' }] },
        { key: 'b', status: 'OK', samples: [[at(1), 3, null]] },
        { key: 'c', status: 'OK', samples: [[at(1), 3, null]], notes: [{ timestamp: at(1), text: 'hidden', tags: [], user: null }] },
      ],
    )
    expect(view.rows.map((r) => r.notes?.length)).toEqual([1, 0, 0])
    const series = (chartOption(view, ctx) as { series: Record<string, unknown>[] }).series
    const mp = series[0].markPoint as { data: { coord: number[] }[]; tooltip: { formatter: (p: unknown) => string } }
    expect(mp.data).toEqual([{ name: 'note', coord: [at(2, 6), 2], value: 2 }])
    expect(mp.tooltip.formatter({ dataIndex: 0 })).toContain('Ereignis · A')
    expect(series[1].markPoint).toBeUndefined()
    expect(series[2].markPoint).toBeUndefined()
  })

  it('puts markers of stacked rows on top of the rows below', () => {
    const view = lineChart(
      'STACKED_COLUMN',
      [row('a'), row('b')],
      [
        { key: 'a', status: 'OK', samples: [[at(1), 5, null]] },
        { key: 'b', status: 'OK', samples: [[at(1), 2, null]], notes: [{ timestamp: at(1, 3), text: 'n', tags: [], user: null }] },
      ],
    )
    const series = (chartOption(view, ctx) as { series: Record<string, unknown>[] }).series
    expect((series[1].markPoint as { data: { coord: number[] }[] }).data[0].coord).toEqual([at(1, 3), 7])
  })

  it('leaves pie, bar and table charts without markers', () => {
    const notes = [{ timestamp: at(1), text: 'n', tags: [], user: null }]
    for (const type of ['PIE', 'BAR']) {
      const view = lineChart(type, [row('a')], [{ key: 'a', status: 'OK', samples: [[at(1), 5, null]], pieValue: 5, notes }])
      const option = chartOption(view, ctx) as { series: Record<string, unknown>[] }
      expect(option.series.every((s) => s.markPoint === undefined)).toBe(true)
    }
    expect(chartOption(lineChart('TABLE', [row('a')], [{ key: 'a', status: 'OK', samples: [[at(1), 5, null]], notes }]), ctx)).toBeNull()
  })
})

describe('detail chart of a note', () => {
  it('keeps numeric samples sorted', () => {
    expect(numericRowSamples([[3, 1], [1, 'text'], [2, null], [0, 4]])).toEqual([
      [0, 4],
      [3, 1],
    ])
    expect(numericRowSamples(undefined)).toEqual([])
  })

  it('shows ±1 day with a line and a pin at the note time', () => {
    const ts = Date.UTC(2026, 10, 3, 8, 15)
    const option = noteChartOption(
      [
        [ts - 3600_000, 10],
        [ts + 3600_000, 12],
      ],
      ts,
      'Row A',
      'kWh',
      { locale: 'de', noteLabel: 'Notiz' },
    ) as { xAxis: { min: number; max: number }; series: { markLine: { data: unknown[] }; markPoint: { data: { coord: number[] }[] } }[] }
    expect(option.xAxis.min).toBe(ts - DAY_MS)
    expect(option.xAxis.max).toBe(ts + DAY_MS)
    expect(option.series[0].markLine.data).toEqual([{ xAxis: ts }])
    expect(option.series[0].markPoint.data[0].coord).toEqual([ts, 10])
  })
})

describe('navigation', () => {
  it('counts readable notes objects', () => {
    expect(moduleCounts({ modules: { notes: 4 } }).notes).toBe(4)
    expect(moduleCounts({ modules: {} }).notes).toBe(0)
  })
})
