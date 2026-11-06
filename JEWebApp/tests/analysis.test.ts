/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import { decimalsFor, escapeHtml, formatNumber } from '../src/analysis/format'
import { buildHeatMap, colorScale, typicalInterval } from '../src/analysis/heatmap'
import { buildChartViews, chartKind, rowTotal, rowType, type ChartView } from '../src/analysis/model'
import { bubblePoints, bubbleRoles, chartOption, stateText, type OptionContext } from '../src/analysis/options'
import {
  dataRequest,
  fromDateTimeInput,
  inferUnit,
  nextInFuture,
  settingsFromQuery,
  settingsToQuery,
  shiftRange,
  toDateTimeInput,
  toIsoLocal,
} from '../src/analysis/settings'
import { buildCsv, buildTimeTable, csvField, csvSeparators, fileName } from '../src/analysis/table'
import { at, chart, config, data, row } from './fixtures/analysis'

const ctx: OptionContext = { locale: 'de', labels: { noData: 'Keine Daten', noPermission: 'Keine Berechtigung', error: 'Fehler', count: 'Anzahl' } }
const NO_ACCESS = '(kein Zugriff)'

function views(charts: Parameters<typeof config>[0], answer: Parameters<typeof data>[0]): ChartView[] {
  return buildChartViews(config(charts), data(answer), NO_ACCESS)
}

type Any = any

describe('settings and URL query', () => {
  const defaults = { timeFrame: 'LAST_7_DAYS', aggregation: 'DAILY', manipulation: 'NONE' }

  it('uses the defaults of the analysis without query', () => {
    expect(settingsFromQuery({}, defaults)).toEqual({ timeFrame: 'LAST_7_DAYS', from: null, to: null, aggregation: 'DAILY', manipulation: 'NONE' })
  })

  it('round-trips presets and custom ranges', () => {
    const preset = settingsFromQuery({ tf: 'LAST_MONTH', agg: 'HOURLY', man: 'CUMULATE' }, defaults)
    expect(settingsFromQuery(settingsToQuery(preset), defaults)).toEqual(preset)
    expect(settingsToQuery(preset)).toEqual({ tf: 'LAST_MONTH', agg: 'HOURLY', man: 'CUMULATE' })

    const custom = { timeFrame: 'CUSTOM' as const, from: new Date(at(1)), to: new Date(at(2) - 1), aggregation: 'NONE', manipulation: 'NONE' }
    const query = settingsToQuery(custom)
    expect(query.from).toBe(toIsoLocal(new Date(at(1))))
    expect(settingsFromQuery(query, defaults)).toEqual(custom)
  })

  it('reads an offset whose "+" became a space', () => {
    const s = settingsFromQuery({ tf: 'CUSTOM', from: '2026-09-01T00:00:00.000 02:00', to: '2026-09-02T00:00:00.000 02:00' }, defaults)
    expect(s.from?.toISOString()).toBe('2026-08-31T22:00:00.000Z')
  })

  it('ignores invalid values', () => {
    const s = settingsFromQuery({ tf: 'NEXT_CENTURY', agg: 'EVERY_SECOND', man: ['SUM', 'MAX'] }, defaults)
    expect(s).toMatchObject({ timeFrame: 'LAST_7_DAYS', aggregation: 'DAILY', manipulation: 'SUM' })
    // CUSTOM without a valid range, or from after to
    expect(settingsFromQuery({ tf: 'CUSTOM', from: 'x' }, defaults).timeFrame).toBe('LAST_7_DAYS')
    expect(settingsFromQuery({ tf: 'CUSTOM', from: new Date(at(3)).toISOString(), to: new Date(at(2)).toISOString() }, defaults).timeFrame).toBe('LAST_7_DAYS')
  })

  it('accepts aggregations of the defaults that the toolbar does not list', () => {
    const d = { timeFrame: 'CUSTOM_START_END', aggregation: 'MINUTELY', manipulation: 'NONE' }
    const s = settingsFromQuery({ agg: 'MINUTELY' }, d)
    expect(s.aggregation).toBe('MINUTELY')
    expect(s.timeFrame).toBe('TODAY')
  })

  it('builds the data request with the time zone, from/to only for CUSTOM', () => {
    const preset = dataRequest(settingsFromQuery({ tf: 'TODAY' }, defaults), 'Europe/Berlin')
    expect(preset).toEqual({ timeFrame: 'TODAY', from: null, to: null, aggregation: 'DAILY', manipulation: 'NONE', timeZone: 'Europe/Berlin' })
    const custom = dataRequest({ timeFrame: 'CUSTOM', from: new Date(at(1)), to: new Date(at(2)), aggregation: 'NONE', manipulation: 'SUM' }, 'UTC')
    expect(Date.parse(custom.from!)).toBe(at(1))
    expect(custom.to).toMatch(/^2026-09-02T00:00:00\.000[+-]\d{2}:\d{2}$/)
  })

  it('converts datetime-local input values', () => {
    expect(toDateTimeInput(new Date(at(5, 14, 30)))).toBe('2026-09-05T14:30')
    expect(fromDateTimeInput('2026-09-05T14:30')?.getTime()).toBe(at(5, 14, 30))
    expect(fromDateTimeInput('')).toBeNull()
  })
})

describe('previous / next period', () => {
  const day = (d: Date) => d.getTime()

  it('shifts days', () => {
    const r = shiftRange('TODAY', new Date(at(10)), new Date(at(11) - 1), -1)
    expect([day(r.from), day(r.to)]).toEqual([at(9), at(10) - 1])
    const n = shiftRange('LAST_7_DAYS', new Date(at(1)), new Date(at(8) - 1), 1)
    expect([day(n.from), day(n.to)]).toEqual([at(8), at(15) - 1])
  })

  it('shifts whole months and years, also from a running period', () => {
    // THIS_MONTH up to now (5 Sep, 12:00): previous is the whole of August
    const r = shiftRange('THIS_MONTH', new Date(at(1)), new Date(at(5, 12)), -1)
    expect(day(r.from)).toBe(new Date(2026, 7, 1).getTime())
    expect(day(r.to)).toBe(at(1) - 1)
    // next of the running month: all of October
    const n = shiftRange('THIS_MONTH', new Date(at(1)), new Date(at(5, 12)), 1)
    expect(day(n.from)).toBe(new Date(2026, 9, 1).getTime())
    expect(day(n.to)).toBe(new Date(2026, 10, 1).getTime() - 1)
    const y = shiftRange('LAST_YEAR', new Date(2025, 0, 1), new Date(new Date(2026, 0, 1).getTime() - 1), -1)
    expect(day(y.from)).toBe(new Date(2024, 0, 1).getTime())
    expect(day(y.to)).toBe(new Date(2025, 0, 1).getTime() - 1)
  })

  it('keeps a workday start of 06:00', () => {
    const from = new Date(2026, 8, 1, 6)
    const to = new Date(new Date(2026, 9, 1, 6).getTime() - 1)
    const r = shiftRange('LAST_MONTH', from, to, -1)
    expect(day(r.from)).toBe(new Date(2026, 7, 1, 6).getTime())
    expect(day(r.to)).toBe(from.getTime() - 1)
  })

  it('infers the unit of custom ranges', () => {
    expect(inferUnit(new Date(2026, 1, 1), new Date(new Date(2026, 2, 1).getTime() - 1))).toEqual({ months: 1 })
    expect(inferUnit(new Date(at(1)), new Date(at(4) - 1))).toEqual({ days: 3 })
    expect(inferUnit(new Date(at(1, 8)), new Date(at(1, 10)))).toEqual({ ms: 2 * 3600_000 + 1 })
    // a custom month range moves month by month (30 and 31 days)
    const r = shiftRange('CUSTOM', new Date(2026, 8, 1), new Date(new Date(2026, 9, 1).getTime() - 1), 1)
    expect(r.to.getTime()).toBe(new Date(2026, 10, 1).getTime() - 1)
  })

  it('shifts calendar days across a DST change (25-hour day)', () => {
    const r = shiftRange('TODAY', new Date(2026, 9, 26), new Date(new Date(2026, 9, 27).getTime() - 1), -1)
    expect(r.from.getTime()).toBe(new Date(2026, 9, 25).getTime())
    expect(r.to.getTime()).toBe(new Date(2026, 9, 26).getTime() - 1)
  })

  it('knows when next would be in the future', () => {
    const now = new Date(at(5, 12))
    expect(nextInFuture('TODAY', new Date(at(5)), new Date(at(6) - 1), now)).toBe(true)
    expect(nextInFuture('YESTERDAY', new Date(at(4)), new Date(at(5) - 1), now)).toBe(false)
  })
})

describe('chart model', () => {
  it('resolves row types and chart kinds', () => {
    expect(rowType('AREA', 'DEFAULT')).toBe('AREA')
    expect(rowType('LINE', 'COLUMN')).toBe('COLUMN')
    expect(rowType('LINE', 'PIE')).toBe('LINE')
    expect(rowType('LINE', 'BAR')).toBe('COLUMN')
    expect(chartKind('TABLE_H')).toBe('table')
    expect(chartKind('HEAT_MAP')).toBe('heatmap')
    expect(chartKind('UNKNOWN')).toBe('xy')
  })

  it('joins configuration and data with row states', () => {
    const [c] = views(
      [chart(0, 'LINE', [row('a'), row('b'), row('c', { readable: false, name: null }), row('d'), row('e', { name: 'Meter a' })])],
      [
        {
          id: 0,
          rows: [
            { key: 'a', status: 'OK', unit: 'MWh', samples: [[at(1), 1.5, null]], stats: { min: 1.5, max: 1.5, avg: 1.5, sum: 1.5, count: 1 } },
            { key: 'b', status: 'ERROR', message: 'timeout' },
            { key: 'c', status: 'NO_PERMISSION' },
            { key: 'd', status: 'OK', samples: [] },
          ],
        },
      ],
    )
    expect(c.rows.map((r) => [r.name, r.state])).toEqual([
      ['Meter a', 'OK'],
      ['Meter b', 'ERROR'],
      [NO_ACCESS, 'NO_PERMISSION'],
      ['Meter d', 'NO_DATA'],
      ['Meter a (2)', 'NO_DATA'],
    ])
    expect(c.rows[0].unit).toBe('MWh')
    expect(stateText(c.rows[1], ctx)).toBe('Fehler: timeout')
    expect(stateText(c.rows[2], ctx)).toBe('Keine Berechtigung')
  })

  it('takes the decimals of the row when set', () => {
    expect(decimalsFor({ minFractionDigits: 0, maxFractionDigits: 3 }, -1)).toEqual({ min: 0, max: 3 })
    expect(decimalsFor({ minFractionDigits: 2, maxFractionDigits: 2 }, 0)).toEqual({ min: 0, max: 0 })
    expect(decimalsFor({ minFractionDigits: 4, maxFractionDigits: 1 }, -1)).toEqual({ min: 1, max: 1 })
    expect(formatNumber(1234.5, 'de', { min: 2, max: 2 })).toBe('1.234,50')
    expect(formatNumber(1234.5, 'en', { min: 0, max: 0 })).toBe('1,235')
  })
})

describe('ECharts options', () => {
  const samples: [number, number, string | null][] = [
    [at(1), 10, null],
    [at(2), 20, 'checked'],
  ]
  const ok = (key: string, sum: number | null = 30) => ({
    key,
    status: 'OK',
    unit: 'kWh',
    samples,
    stats: { min: 10, max: 20, avg: 15, sum, count: 2 },
  })

  it('LINE with two y axes, units as axis names, colours and zoom', () => {
    const [c] = views([chart(0, 'LINE', [row('a'), row('b', { axis: 1, unit: '°C', color: '#ff0000' }), row('c', { chartType: 'COLUMN' })])], [
      { id: 0, rows: [ok('a'), { ...ok('b'), unit: '°C' }, ok('c')] },
    ])
    const o = chartOption(c, ctx) as Any
    expect(o.xAxis.type).toBe('time')
    expect(o.yAxis).toHaveLength(2)
    expect(o.yAxis[0].name).toBe('kWh')
    expect(o.yAxis[1].name).toBe('°C')
    expect(o.series.map((s: Any) => [s.type, s.yAxisIndex, s.color])).toEqual([
      ['line', 0, '#00538a'],
      ['line', 1, '#ff0000'],
      ['bar', 0, '#00538a'],
    ])
    expect(o.series[0].data).toEqual(samples)
    expect(o.dataZoom.map((z: Any) => z.type)).toEqual(['inside', 'slider'])
  })

  it('tooltip shows time, value with unit and note, HTML escaped', () => {
    const [c] = views([chart(0, 'LINE', [row('a', { name: 'Meter <A>' })])], [{ id: 0, rows: [ok('a')] }])
    const o = chartOption(c, ctx) as Any
    const html: string = o.tooltip.formatter([{ seriesIndex: 0, axisValue: at(2), value: samples[1], marker: '' }])
    expect(html).toContain('Meter &lt;A&gt;')
    expect(html).toContain('<b>20,00</b> kWh')
    expect(html).toContain('checked')
    expect(escapeHtml('"x" & <y>')).toBe('&quot;x&quot; &amp; &lt;y&gt;')
  })

  it('AREA, STACKED_AREA, STACKED_COLUMN, SCATTER, LOGICAL', () => {
    const types = ['AREA', 'STACKED_AREA', 'STACKED_COLUMN', 'SCATTER', 'LOGICAL']
    const [c] = views([chart(0, 'LINE', types.map((t) => row(t, { chartType: t })))], [{ id: 0, rows: types.map((t) => ok(t)) }])
    const s = (chartOption(c, ctx) as Any).series
    expect(s[0]).toMatchObject({ type: 'line', areaStyle: { opacity: 0.25 } })
    expect(s[0].stack).toBeUndefined()
    expect(s[1]).toMatchObject({ type: 'line', stack: 'area-0' })
    expect(s[2]).toMatchObject({ type: 'bar', stack: 'column-0' })
    expect(s[3].type).toBe('scatter')
    expect(s[4]).toMatchObject({ type: 'line', step: 'end' })
  })

  it('legend greys out rows without values', () => {
    const [c] = views([chart(0, 'LINE', [row('a'), row('b')])], [{ id: 0, rows: [ok('a'), { key: 'b', status: 'NO_DATA' }] }])
    const legend = (chartOption(c, ctx) as Any).legend
    expect(legend.data[1].itemStyle.opacity).toBeLessThan(1)
    expect(legend.formatter('Meter b')).toBe('{off|Meter b (Keine Daten)}')
    expect(legend.formatter('Meter a')).toBe('Meter a')
  })

  it('BAR: horizontal, last value per row', () => {
    const [c] = views([chart(0, 'BAR', [row('a'), row('b')])], [{ id: 0, rows: [ok('a'), { key: 'b', status: 'NO_DATA' }] }])
    const o = chartOption(c, ctx) as Any
    expect(o.yAxis.type).toBe('category')
    expect(o.yAxis.data).toEqual(['Meter a'])
    expect(o.series[0].data[0].value).toBe(20)
  })

  it('PIE: uses the value computed by the server, one sample at the start for CSV and table', () => {
    const [c] = views([chart(0, 'PIE', [row('a'), row('b')])], [
      { id: 0, rows: [{ key: 'a', status: 'OK', samples: [], stats: { min: 1, max: 2, avg: 1.5, sum: 3, count: 2 }, pieValue: 42 }, { key: 'b', status: 'NO_DATA' }] },
    ])
    expect(c.rows[0].pieValue).toBe(42)
    expect(c.rows[0].samples).toEqual([[at(1), 42, null]])
    expect(c.rows[1].pieValue).toBeNull()
    const o = chartOption(c, ctx) as Any
    expect(o.series[0].data.map((d: Any) => d.value)).toEqual([42, '-'])
  })

  it('PIE: sum for quantities, average otherwise, empty rows stay in the legend', () => {
    const [c] = views([chart(0, 'PIE', [row('a'), row('b'), row('c')])], [
      { id: 0, rows: [ok('a', 30), ok('b', null), { key: 'c', status: 'NO_PERMISSION' }] },
    ])
    expect(rowTotal(c.rows[0])).toBe(30)
    expect(rowTotal(c.rows[1])).toBe(15)
    const data = (chartOption(c, ctx) as Any).series[0].data
    expect(data.map((d: Any) => [d.name, d.value])).toEqual([
      ['Meter a', 30],
      ['Meter b', 15],
      ['Meter c', '-'],
    ])
  })

  it('HEAT_MAP: categories of days and time slots with a colour scale', () => {
    const hourly: [number, number, null][] = []
    for (let h = 0; h < 48; h++) hourly.push([at(1, h), h, null])
    const [c] = views([chart(0, 'HEAT_MAP', [row('a')], { colorMapping: 'BLUE_YELLOW_RED' })], [
      { id: 0, rows: [{ key: 'a', status: 'OK', samples: hourly, stats: { min: 0, max: 47, avg: 23.5, sum: null, count: 48 } }] },
    ])
    const o = chartOption(c, ctx, at(1), at(3) - 1) as Any
    expect(o.xAxis.data).toHaveLength(24)
    expect(o.xAxis.data[1]).toBe('01:00')
    expect(o.yAxis.data).toHaveLength(2)
    expect(o.series[0].type).toBe('heatmap')
    expect(o.visualMap.inRange.color).toEqual(colorScale('BLUE_YELLOW_RED'))
    expect(o.visualMap).toMatchObject({ min: 0, max: 47 })
  })

  it('BUBBLE: uses the rows marked X and Y, else the order', () => {
    const [c] = views([chart(0, 'BUBBLE', [row('s', { bubbleType: 'SIZE' }), row('y', { bubbleType: 'Y' }), row('x', { bubbleType: 'X' })])], [])
    const roles = bubbleRoles(c.rows)
    expect(roles.xRow?.key).toBe('x')
    expect(roles.yRows.map((r) => r.key)).toEqual(['y'])
    const [plain] = views([chart(0, 'BUBBLE', [row('a'), row('b'), row('c')])], [])
    expect(bubbleRoles(plain.rows).xRow?.key).toBe('a')
    expect(bubbleRoles(plain.rows).yRows.map((r) => r.key)).toEqual(['b', 'c'])
  })

  it('BUBBLE: groups the x values by the grouping interval', () => {
    const [c] = views([chart(0, 'BUBBLE', [row('x'), row('y')], { groupingInterval: 10 })], [
      {
        id: 0,
        rows: [
          { key: 'x', status: 'OK', samples: [[1, 3, null], [2, 7, null], [3, 15, null]] },
          { key: 'y', status: 'OK', samples: [[1, 100, null], [2, 200, null], [3, 50, null]] },
        ],
      },
    ])
    expect(bubblePoints(c.rows[0], c.rows[1], 10)).toEqual([
      { x: 0, y: 150, count: 2 },
      { x: 10, y: 50, count: 1 },
    ])
    expect(bubblePoints(c.rows[0], null, 10).map((p) => p.y)).toEqual([2, 1])
    const o = chartOption(c, ctx) as Any
    expect(o.series[0].data).toEqual([
      [0, 150, 2],
      [10, 50, 1],
    ])
  })

  it('table charts have no ECharts option', () => {
    const [c] = views([chart(0, 'TABLE_V', [row('a')])], [{ id: 0, rows: [ok('a')] }])
    expect(chartOption(c, ctx)).toBeNull()
    expect(c.orientation).toBe('vertical')
    expect(views([chart(0, 'TABLE_H', [row('a')])], [])[0].orientation).toBe('horizontal')
  })
})

describe('heat map matrix', () => {
  it('detects the sample interval', () => {
    expect(typicalInterval([0, 900_000, 1_800_000, 3_600_000])).toBe(900_000)
    expect(typicalInterval([5])).toBeNull()
  })

  it('15-minute data: 96 columns, one line per day, sums for quantities', () => {
    const samples: [number, number][] = []
    for (let i = 0; i < 96 * 2; i++) samples.push([at(1) + i * 900_000, 1])
    const m = buildHeatMap(samples, true, at(1), at(3) - 1)
    expect(m.mode).toBe('intraday')
    expect(m.columns).toHaveLength(96)
    expect(m.lines).toEqual([at(1), at(2)])
    expect(m.cells).toHaveLength(192)
    expect(m.cells[96]).toEqual([0, 1, 1])
  })

  it('finer data is combined into quarter hours (sum or average)', () => {
    const samples: [number, number][] = [
      [at(1, 0, 0), 2],
      [at(1, 0, 5), 4],
      [at(1, 0, 10), 6],
      [at(1, 0, 15), 1],
    ]
    expect(buildHeatMap(samples, true).cells[0]).toEqual([0, 0, 12])
    const avg = buildHeatMap(samples, false)
    expect(avg.slotMinutes).toBe(15)
    expect(avg.cells.slice(0, 2)).toEqual([
      [0, 0, 4],
      [1, 0, 1],
    ])
    expect([avg.min, avg.max]).toEqual([1, 4])
  })

  it('daily data: lines per month, columns per day of month', () => {
    const samples: [number, number][] = [
      [new Date(2026, 7, 30).getTime(), 1],
      [new Date(2026, 7, 31).getTime(), 2],
      [new Date(2026, 8, 1).getTime(), 3],
    ]
    const m = buildHeatMap(samples, true)
    expect(m.mode).toBe('monthDays')
    expect(m.columns).toHaveLength(31)
    expect(m.lines).toEqual([new Date(2026, 7, 1).getTime(), new Date(2026, 8, 1).getTime()])
    expect(m.cells).toEqual([
      [29, 0, 1],
      [30, 0, 2],
      [0, 1, 3],
    ])
  })

  it('maps ColorMapping names to colour scales', () => {
    expect(colorScale('GREEN_YELLOW_RED')).toHaveLength(3)
    expect(colorScale('BLUE_CYAN_GREEN_YELLOW_RED')).toHaveLength(5)
    expect(colorScale('SOMETHING')).toEqual(colorScale('GREEN_YELLOW_RED'))
  })
})

describe('tables and CSV', () => {
  const rows = [
    { name: 'Meter A', unit: 'kWh', samples: [[at(1), 1.5, null], [at(2), -2, 'n']] as [number, number | string | null, string | null][] },
    { name: 'Text; "quoted"', unit: '', samples: [[at(2), '=SUM(A1)', null]] as [number, number | string | null, string | null][] },
  ]

  it('joins the timestamps of all rows', () => {
    const t = buildTimeTable(rows)
    expect(t.timestamps).toEqual([at(1), at(2)])
    expect(t.values).toEqual([
      [1.5, -2],
      [null, '=SUM(A1)'],
    ])
    expect(t.notes[0][1]).toBe('n')
  })

  it('German CSV: semicolon, decimal comma, BOM, CRLF, escaping', () => {
    const csv = buildCsv(rows, 'de', 'Zeit')
    expect(csv.charCodeAt(0)).toBe(0xfeff)
    const lines = csv.slice(1).split('\r\n')
    expect(lines[0]).toBe('Zeit;Meter A [kWh];"Text; ""quoted"""')
    expect(lines[1]).toBe('2026-09-01 00:00:00;1,5;')
    expect(lines[2]).toBe(`2026-09-02 00:00:00;-2;'=SUM(A1)`)
    expect(lines[3]).toBe('')
  })

  it('English CSV: comma and decimal point', () => {
    expect(csvSeparators('en')).toEqual({ field: ',', decimal: '.' })
    const csv = buildCsv([{ name: 'Meter A', unit: 'kWh', samples: [[at(1), 1234.5678, null], [at(2), 1e-7, null]] }], 'en', 'Time')
    expect(csv).toContain('2026-09-01 00:00:00,1234.5678\r\n')
    expect(csv).toContain('2026-09-02 00:00:00,0.0000001\r\n')
    expect(csvField('a,b', ',')).toBe('"a,b"')
    expect(csvField('line\nbreak', ';')).toBe('"line\nbreak"')
    expect(csvField('-5 kW', ';')).toBe('-5 kW')
    expect(csvField('@cmd', ';')).toBe("'@cmd")
  })

  it('makes file names safe', () => {
    expect(fileName('Strom: A/B')).toBe('Strom_ A_B')
    expect(fileName('  ')).toBe('chart')
  })
})
