/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import {
  applyColorToAll,
  applyUnitToAll,
  conflictConfig,
  draftConfig,
  draftKey,
  emptyDefinition,
  formatDates,
  formatWeekdays,
  move,
  nameProblem,
  nameTakenId,
  newChart,
  newRow,
  normaliseTime,
  parseDates,
  parseWeekdays,
  previewKey,
  rowKey,
  toDefinition,
  toDraft,
  validateDraft,
} from '../src/analysis/editor'
import { buildChartViews } from '../src/analysis/model'
import { xyOption, type OptionContext } from '../src/analysis/options'
import { cellColors, cellMatches, filterTimestamps, gradientColor, rowTotalOf, textOn, timestampSum, buildTimeTable } from '../src/analysis/table'
import { at, data } from './fixtures/analysis'
import { editConfig, lockedRow, VERSION } from './fixtures/analysisEdit'
import { httpError } from './fixtures/edit'

const READ_ONLY = ['id', 'names', 'defaults', 'version', 'canWrite', 'canDelete', 'directoryId']
const ROW_READ_ONLY = ['readable', 'unit', 'key', 'rowClass', 'calculationId']

describe('analysis editor: definition <-> form', () => {
  it('maps the GET answer to the definition without the read-only keys', () => {
    const draft = toDraft(editConfig(), 'Analyse A')
    const def = toDefinition(draft)
    for (const key of READ_ONLY) expect(def).not.toHaveProperty(key)
    expect(def).toMatchObject({
      chartsPerScreen: 2,
      horizontalPies: 2,
      horizontalTables: 1,
      autoSize: true,
      forcedInterval: '',
      dayFilter: { weekdays: '6,7', holidays: true, dates: '2026-12-24' },
      limitLines: [{ value: 100, label: 'Grenze', color: '#ff0000', axis: 0, fill: 'NONE' }],
    })
    expect(def.charts.map((c) => c.id)).toEqual([0, 1])
    expect(def.charts[0]).toMatchObject({ name: 'Chart 0', type: 'LINE', xAxisTitle: null, yAxisTitle: null, dayStart: null, dayEnd: null, filterTexts: {} })
    const first = def.charts[0].rows[0]
    for (const key of ROW_READ_ONLY) expect(first).not.toHaveProperty(key)
    expect(first).toEqual({
      objectId: 2001,
      attribute: 'Value',
      name: 'Musterwerk Strom',
      color: '#00538a',
      axis: 0,
      chartType: 'DEFAULT',
      unitPrefix: 'NONE',
      unitFormula: 'kWh',
      unitLabel: 'kWh',
      decimalDigits: -1,
      calculation: false,
      bubbleType: 'NONE',
      interval: { enabled: false, start: null, end: null },
      css: '',
    })
    expect(def.charts[1].filterTexts).toEqual({ 'Musterwerk Strom': '12' })
  })

  it('sends rows the user may not read back with exactly the keys they came with', () => {
    const draft = toDraft(editConfig(), 'Analyse A')
    const locked = draft.charts[0].rows[1]
    expect(locked.readable).toBe(false)
    // an accidental change of the form state is not sent
    locked.name = 'x'
    locked.color = '#000000'
    const sent = toDefinition(draft).charts[0].rows[1]
    const { key: _k, readable: _r, unit: _u, ...rest } = lockedRow('0-2002-Value', 2002)
    expect(sent).toEqual(rest)
  })

  it('normalises texts: empty titles null, times with seconds, empty filter texts dropped', () => {
    const draft = toDraft(editConfig(), 'Analyse A')
    const chart = draft.charts[0]
    chart.xAxisTitle = '  Zeit '
    chart.dayStart = '06:00'
    chart.dayEnd = '22:00:30'
    chart.filterTexts = { a: '', b: 'x' }
    chart.rows[0].decimalDigits = ''
    const def = toDefinition(draft).charts[0]
    expect(def.xAxisTitle).toBe('Zeit')
    expect(def.dayStart).toBe('06:00:00')
    expect(def.dayEnd).toBe('22:00:30')
    expect(def.filterTexts).toEqual({ b: 'x' })
    expect(def.rows[0].decimalDigits).toBe(-1)
    expect(normaliseTime('')).toBeNull()
  })

  it('a draft without changes has the key of the loaded analysis; any change differs', () => {
    const draft = toDraft(editConfig(), 'Analyse A')
    const key = draftKey(draft)
    expect(draftKey(toDraft(editConfig(), 'Analyse A'))).toBe(key)
    draft.name = 'Analyse B'
    expect(draftKey(draft)).not.toBe(key)
    draft.name = 'Analyse A'
    draft.charts[0].rows[0].color = '#123456'
    expect(draftKey(draft)).not.toBe(key)
  })

  it('preview key: only changes of the data need a new preview', () => {
    const draft = toDraft(editConfig(), 'Analyse A')
    const key = previewKey(toDefinition(draft))
    draft.charts[0].name = 'Neu'
    draft.charts[0].rows[0].color = '#123456'
    draft.chartsPerScreen = 3
    draft.limitLines[0].value = 5
    expect(previewKey(toDefinition(draft))).toBe(key)
    draft.charts[0].rows[0].unitLabel = 'MWh'
    expect(previewKey(toDefinition(draft))).not.toBe(key)
  })

  it('preview config: row keys of the saved analysis first, then <chartId>-<objectId>-<attribute>', () => {
    const base = editConfig()
    base.charts[0].rows[0].key = 'saved-key'
    const draft = toDraft(base, 'Analyse A')
    draft.charts[0].rows.push(newRow({ id: 3001, name: 'Musterwerk Gas', unit: 'm³', class: 'Data' }, 'Value', 2))
    const saved = draftConfig(draft, base, true)
    expect(saved.charts[0].rows.map((r) => r.key)).toEqual(['saved-key', '0-2002-Value', '0-3001-Value'])
    const preview = draftConfig(draft, base)
    expect(preview.charts[0].rows.map((r) => r.key)).toEqual(['0-2001-Value', '0-2002-Value', '0-3001-Value'])
    expect(preview.charts[0].rows[1]).toMatchObject({ readable: false, name: null, unit: null })
    expect(preview.charts[0].rows[2]).toMatchObject({ name: 'Musterwerk Gas', unit: 'm³', color: '#2e7d32' })
    expect(rowKey(4, { objectId: 1, attribute: 'Value' })).toBe('4-1-Value')
  })

  it('an empty analysis has one line chart with id 0', () => {
    const def = emptyDefinition('Diagramm 1')
    expect(def.charts).toHaveLength(1)
    expect(def.charts[0]).toMatchObject({ id: 0, name: 'Diagramm 1', type: 'LINE', rows: [] })
    expect(def.chartsPerScreen).toBe(2)
  })
})

describe('analysis editor: validation', () => {
  it('a loaded analysis is valid', () => {
    expect(validateDraft(toDraft(editConfig(), 'Analyse A'), { checkName: true })).toEqual([])
  })

  it('reports every problem with its field path', () => {
    const draft = toDraft(editConfig(), '')
    draft.chartsPerScreen = 0
    draft.horizontalPies = ''
    draft.horizontalTables = 21
    draft.limitLines[0].color = 'red'
    draft.limitLines[0].value = ''
    draft.dayFilter.dates = '2026-12-24,24.12.2026'
    const chart = draft.charts[0]
    chart.minFractionDigits = 21
    chart.height = 10
    chart.groupingInterval = 0
    chart.dayStart = '25:00'
    chart.rows[0].color = '#12345'
    chart.rows[0].decimalDigits = 30
    chart.rows[0].interval = { enabled: true, start: '2026-11-02T00:00:00+01:00', end: '2026-11-01T00:00:00+01:00' }
    // the locked row is never checked
    chart.rows[1].color = 'nonsense'
    draft.charts[1].id = 0
    const paths = validateDraft(draft, { checkName: true }).map((p) => p.path)
    expect(paths).toEqual([
      'name',
      'chartsPerScreen',
      'horizontalPies',
      'horizontalTables',
      'limitLines.0.value',
      'limitLines.0.color',
      'dayFilter.dates',
      'charts.0.minFractionDigits',
      'charts.0.height',
      'charts.0.groupingInterval',
      'charts.0.dayStart',
      'charts.0.rows.0.color',
      'charts.0.rows.0.decimalDigits',
      'charts.0.rows.0.interval',
      'charts.1.id',
    ])
    expect(validateDraft(draft).map((p) => p.path)).not.toContain('name')
  })

  it('checks names as the server does', () => {
    expect(nameProblem('  ')).toBe('analysisEdit.problem.required')
    expect(nameProblem('x'.repeat(101))).toBe('analysisEdit.problem.nameLength')
    expect(nameProblem('a\u0007b')).toBe('analysisEdit.problem.nameChars')
    expect(nameProblem(' Musterwerk ')).toBeNull()
  })
})

describe('analysis editor: helpers', () => {
  it('new charts get the next free id; lists move within bounds', () => {
    expect(newChart([{ id: 0 }, { id: 4 }], 'X').id).toBe(5)
    expect(newChart([], 'X').id).toBe(0)
    const list = [1, 2, 3]
    move(list, 0, 1)
    expect(list).toEqual([2, 1, 3])
    move(list, 2, 1)
    expect(list).toEqual([2, 1, 3])
  })

  it('"für alle übernehmen": colour on all readable rows, unit where it is offered', () => {
    const draft = toDraft(editConfig(), 'Analyse A')
    const chart = draft.charts[0]
    chart.rows.push(newRow({ id: 3001, name: 'Musterwerk Gas', unit: 'm³', class: 'Data' }, 'Value', 2))
    applyColorToAll(chart, '#abcdef')
    expect(chart.rows.map((r) => r.color)).toEqual(['#abcdef', '#b3261e', '#abcdef'])
    const mwh = { prefix: 'MEGA', formula: 'Wh', label: 'MWh' }
    const skipped = applyUnitToAll(chart, mwh, (r) => (r.objectId === 2001 ? [{ prefix: 'NONE', formula: 'kWh', label: 'kWh' }, mwh] : [{ prefix: 'NONE', formula: 'm³', label: 'm³' }]))
    expect(skipped).toBe(1)
    expect(chart.rows[0]).toMatchObject({ unitPrefix: 'MEGA', unitFormula: 'Wh', unitLabel: 'MWh' })
    expect(chart.rows[2].unitLabel).toBeNull()
  })

  it('day filter strings', () => {
    expect(parseWeekdays('7, 6,x,9,6')).toEqual([6, 7])
    expect(formatWeekdays([7, 1, 7])).toBe('1,7')
    expect(parseDates('2026-12-31; 2026-12-24 bad,2026-12-24')).toEqual(['2026-12-24', '2026-12-31'])
    expect(formatDates(['2026-12-31', '2026-01-01'])).toBe('2026-01-01,2026-12-31')
  })

  it('409 answers: name taken vs. version conflict', () => {
    expect(nameTakenId(httpError(409, { nameTaken: true, id: 9 }))).toBe(9)
    expect(nameTakenId(httpError(409, editConfig()))).toBeNull()
    expect(nameTakenId(httpError(400, { nameTaken: true, id: 9 }))).toBeNull()
    expect(conflictConfig(httpError(409, editConfig({ version: 'v2' })), 1)?.version).toBe('v2')
    expect(conflictConfig(httpError(409, { nameTaken: true, id: 1 }), 1)).toBeNull()
    expect(conflictConfig(httpError(409, editConfig()), 2)).toBeNull()
    expect(VERSION).toBeTruthy()
  })
})

const ctx: OptionContext = { locale: 'de', labels: { noData: 'Keine Daten', noPermission: 'Keine Berechtigung', error: 'Fehler', count: 'Anzahl' } }

describe('limit lines', () => {
  it('are drawn as dashed horizontal lines on their axis, filled areas and a widened axis', () => {
    const config = editConfig({
      limitLines: [
        { value: 500, label: 'Max', color: '#ff0000', axis: 0, fill: 'BELOW' },
        { value: 2, label: '', color: '#00ff00', axis: 1, fill: 'NONE' },
      ],
    })
    const answer = data([
      {
        id: 0,
        rows: [{ key: '0-2001-Value', status: 'OK', samples: [[at(1), 10, null], [at(2), 30, null]], stats: { min: 10, max: 30, avg: 20, sum: 40, count: 2 } }],
      },
    ])
    const [line, table] = buildChartViews(config, answer, '(kein Zugriff)')
    expect(table.limitLines).toEqual([])
    expect(line.limitLines).toHaveLength(2)
    const option = xyOption(line, ctx) as unknown as { series: Record<string, unknown>[]; yAxis: { max?: unknown }[] }
    const left = option.series.find((s) => s.id === 'limits-0') as { yAxisIndex: number; markLine: { data: { yAxis: number; label: { formatter: string } }[] }; markArea: { data: { yAxis: number }[][] } }
    const right = option.series.find((s) => s.id === 'limits-1') as { markLine: { data: { yAxis: number; label: { formatter: string } }[] } }
    expect(left.yAxisIndex).toBe(0)
    expect(left.markLine.data[0]).toMatchObject({ yAxis: 500, label: { formatter: 'Max' } })
    // fill below: from the lowest value up to the line
    expect(left.markArea.data[0].map((p) => p.yAxis)).toEqual([10, 500])
    // a line without label shows its value
    expect(right.markLine.data[0].label.formatter).toBe('2')
    // the line lies above the values: the axis includes it
    expect(typeof option.yAxis[0].max).toBe('function')
    expect((option.yAxis[0].max as (v: { max: number }) => number)({ max: 30 })).toBe(500)
  })

  it('lines of the right axis go to the left one in a chart without right axis', () => {
    const config = editConfig({ limitLines: [{ value: 1, label: 'L', color: '#ff0000', axis: 1, fill: 'NONE' }] })
    config.charts[0].rows = [config.charts[0].rows[0]]
    const [line] = buildChartViews(config, null, '-')
    const option = xyOption(line, ctx) as unknown as { series: Record<string, unknown>[] }
    expect(option.series.filter((s) => String(s.id).startsWith('limits'))).toEqual([expect.objectContaining({ id: 'limits-0', yAxisIndex: 0 })])
  })
})

describe('table options', () => {
  const rows = [
    { samples: [[1, 10, null], [2, 20, null], [3, 'aus', null]] as [number, number | string, null][], stats: { min: 10, max: 20, avg: 15, sum: 30, count: 2 } },
    { samples: [[1, 1.5, null], [3, 2.5, null]] as [number, number, null][], stats: { min: 1.5, max: 2.5, avg: 2, sum: null, count: 2 } },
  ]
  const table = buildTimeTable(rows)

  it('filters timestamps by the text of their cells', () => {
    const shown = (r: number, i: number) => String(table.values[r][i] ?? '').replace('.', ',')
    expect(filterTimestamps(table, ['2', ''], shown)).toEqual([1])
    expect(filterTimestamps(table, ['', '2,5'], shown)).toEqual([2])
    expect(filterTimestamps(table, ['AUS', ''], shown)).toEqual([2])
    expect(filterTimestamps(table, ['', ''], shown)).toEqual([0, 1, 2])
    expect(cellMatches(2.5, '2,50', '2.5')).toBe(true)
  })

  it('row sums: quantities summed, others averaged; sums per timestamp', () => {
    expect(rowTotalOf(rows[0], table.values[0], [0, 1, 2])).toBe(30)
    expect(rowTotalOf(rows[1], table.values[1], [0, 1, 2])).toBe(2)
    expect(rowTotalOf(rows[0], table.values[0], [2])).toBeNull()
    expect(timestampSum(table, 0)).toBe(11.5)
    expect(timestampSum(table, 1)).toBe(20)
  })

  it('colours the values on the colour mapping', () => {
    expect(gradientColor(['#000000', '#ffffff'], 0.5)).toBe('#808080')
    expect(gradientColor(['#00ff00', '#ffff00', '#ff0000'], 1)).toBe('#ff0000')
    const colors = cellColors(table, [0, 1], ['#000000', '#ffffff'])
    expect(colors[0][1]).toBe('#ffffff')
    expect(colors[1][0]).toBe('#000000')
    expect(colors[0][2]).toBeNull()
    expect(textOn('#ffffff')).toBe('#1a2a45')
    expect(textOn('#000000')).toBe('#ffffff')
  })
})
