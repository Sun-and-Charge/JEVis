/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import { startDashboardFor } from '../src/api/navigation'
import { HttpClient, HttpError } from '../src/api/http'
import { formatJoda, isoWeek } from '../src/dashboard/joda'
import { contentSize, nextZoomStep, normaliseZoom, scaleFactor, sortByLayer, widgetBox } from '../src/dashboard/layout'
import { gaugeStops, sankeyLinks } from '../src/dashboard/options'
import { alignment, backgroundStyle, cssColor, fontWeight, frameStyle, mixColor } from '../src/dashboard/style'
import {
  analysisQuery,
  dashboardDataRequest,
  defaultTimeBar,
  selectTimeFrame,
  stepTimeBar,
  timeBarFromQuery,
  timeBarToQuery,
  timeFrameOptions,
} from '../src/dashboard/timeframe'
import { limitColor, percentOf, percentText, valueDisplay, widgetDecimals, widgetRows, widgetValues } from '../src/dashboard/values'
import { chartWidgetView, linkTarget, pieWidgetView, timeFrameText, widgetKind } from '../src/dashboard/widgets'
import { at, dashboard, ddata, drow, iso, widget } from './fixtures/dashboard'

describe('layout', () => {
  it('normalises the zoom of the configuration', () => {
    expect(normaliseZoom('FIT_WIDTH')).toBe('FIT_WIDTH')
    expect(normaliseZoom(97)).toBe('FIT_HEIGHT')
    expect(normaliseZoom(98)).toBe('FIT_WIDTH')
    expect(normaliseZoom(99)).toBe('FIT_SCREEN')
    expect(normaliseZoom(1.5)).toBe(1.5)
    expect(normaliseZoom('0.75')).toBe(0.75)
    expect(normaliseZoom('nonsense')).toBe(1)
    expect(normaliseZoom(-3)).toBe(1)
  })

  it('computes the scale for each zoom mode', () => {
    const content = { width: 1000, height: 500 }
    const viewport = { width: 500, height: 500 }
    expect(scaleFactor('FIT_WIDTH', content, viewport)).toBe(0.5)
    expect(scaleFactor('FIT_HEIGHT', content, viewport)).toBe(1)
    expect(scaleFactor('FIT_SCREEN', content, viewport)).toBe(0.5)
    expect(scaleFactor(2, content, viewport)).toBe(2)
    expect(scaleFactor('FIT_WIDTH', content, { width: 0, height: 0 })).toBe(1)
  })

  it('enlarges the content size to widgets outside the configured size', () => {
    expect(contentSize({ width: 100, height: 100 }, [{ x: 50, y: 80, width: 100, height: 40 }])).toEqual({ width: 150, height: 120 })
    expect(contentSize({ width: 0, height: 0 }, [])).toEqual({ width: 1, height: 1 })
  })

  it('sorts by layer and keeps the file order within a layer', () => {
    const list = [
      { id: 'a', layer: 2 },
      { id: 'b', layer: 0 },
      { id: 'c', layer: 2 },
      { id: 'd', layer: 1 },
    ]
    expect(sortByLayer(list).map((w) => w.id)).toEqual(['b', 'd', 'a', 'c'])
  })

  it('scales widget boxes and zoom steps', () => {
    expect(widgetBox({ x: 10, y: 20, width: 200, height: 100 }, 0.5)).toEqual({ left: 5, top: 10, width: 100, height: 50 })
    expect(nextZoomStep(1, 1)).toBe(1.25)
    expect(nextZoomStep(1, -1)).toBe(0.9)
    expect(nextZoomStep(4, 1)).toBe(4)
  })
})

describe('style', () => {
  it('accepts only plain colors', () => {
    expect(cssColor('#1a2a45')).toBe('#1a2a45')
    expect(cssColor('rgba(10, 20, 30, 0.5)')).toBe('rgba(10, 20, 30, 0.5)')
    expect(cssColor('transparent')).toBe('transparent')
    expect(cssColor('url(http://example.invalid/x.png)')).toBeNull()
    expect(cssColor('red; position: fixed')).toBeNull()
    expect(cssColor(null)).toBeNull()
  })

  it('maps title positions to flex alignment', () => {
    expect(alignment('TOP_LEFT')).toEqual({ justifyContent: 'flex-start', alignItems: 'flex-start', textAlign: 'left' })
    expect(alignment('CENTER')).toEqual({ justifyContent: 'center', alignItems: 'center', textAlign: 'center' })
    expect(alignment('BOTTOM_RIGHT')).toEqual({ justifyContent: 'flex-end', alignItems: 'flex-end', textAlign: 'right' })
    expect(alignment('CENTER_LEFT')).toMatchObject({ justifyContent: 'flex-start', alignItems: 'center' })
    expect(alignment('right')).toMatchObject({ justifyContent: 'flex-end', alignItems: 'flex-start' })
  })

  it('maps the widget style to CSS, scaled', () => {
    const css = frameStyle(widget('1', 'Value').style, 2)
    expect(css).toMatchObject({ background: '#ffffff', color: '#1a2a45', fontSize: '26px', fontWeight: '700', border: '2px solid #d0d4da', borderRadius: '8px' })
    expect(frameStyle({ italic: true, underline: true, shadow: true, fontWeight: 'SEMI_BOLD' }, 1)).toMatchObject({
      fontStyle: 'italic',
      textDecoration: 'underline',
      fontWeight: '600',
      fontSize: '13px',
    })
    expect(fontWeight('unknown')).toBe(400)
  })

  it('maps the background modes', () => {
    const bg = { color: '#f4f4f4', image: true, mode: 'DEFAULT' }
    expect(backgroundStyle(bg, 'blob:x', { width: 100, height: 50 }, 2)).toMatchObject({ backgroundColor: '#f4f4f4', backgroundRepeat: 'no-repeat', backgroundSize: '200px 100px' })
    expect(backgroundStyle({ ...bg, mode: 'REPEAT' }, 'blob:x', { width: 100, height: 50 }, 1)).toMatchObject({ backgroundRepeat: 'repeat' })
    expect(backgroundStyle({ ...bg, mode: 'STRETCH' }, 'blob:x', null, 1)).toMatchObject({ backgroundSize: '100% 100%' })
    expect(backgroundStyle(bg, null, null, 1).backgroundImage).toBeUndefined()
  })

  it('mixes colors', () => {
    expect(mixColor('#000000', '#ffffff', 0.5)).toBe('#808080')
    expect(mixColor('#ff0000', '#0000ff', 0)).toBe('#ff0000')
    expect(mixColor('#ff0000', '#0000ff', 2)).toBe('#0000ff')
  })
})

describe('values', () => {
  it('chooses decimals like the Control Center', () => {
    expect(widgetDecimals({ decimals: 1, decimalsAuto: false }, drow('a'))).toEqual({ min: 1, max: 1 })
    expect(widgetDecimals({ decimals: 1, decimalsAuto: false }, drow('a', { decimalDigits: 3 }))).toEqual({ min: 3, max: 3 })
    expect(widgetDecimals({ decimals: null, decimalsAuto: false }, null)).toEqual({ min: 2, max: 2 })
    expect(widgetDecimals({ decimals: 1, decimalsAuto: true }, drow('a'))).toEqual({ min: 0, max: 2 })
  })

  it('computes percent and its text', () => {
    expect(percentOf(25, 200, false)).toBe(12.5)
    expect(percentOf(150, 200, true)).toBe(25)
    expect(percentOf(1, 0, false)).toBeNull()
    expect(percentOf(null, 10, false)).toBeNull()
    const cfg = { diff: false, minFractionDigits: 0, maxFractionDigits: 1 }
    expect(percentText(12.5, cfg, 'de')).toBe('12,5%')
    expect(percentText(0.001, cfg, 'de')).toBe('< 0,01 %')
    expect(percentText(-3, { ...cfg, diff: true }, 'en')).toBe('-3%')
  })

  it('colors values outside the limits (lower first, offset, dynamic source)', () => {
    const limit = {
      source: null,
      upper: { enabled: true, value: 100, color: '#ff0000', offset: 1 },
      lower: { enabled: true, value: 10, color: '#00ff00', offset: 1 },
    }
    expect(limitColor(limit, 120)).toBe('#ff0000')
    expect(limitColor(limit, 100)).toBe('#ff0000')
    expect(limitColor(limit, 5)).toBe('#00ff00')
    expect(limitColor(limit, 50)).toBeNull()
    expect(limitColor({ ...limit, upper: { ...limit.upper, offset: 1.5 } }, 120)).toBeNull()
    const dynamic = { ...limit, source: '9' }
    expect(limitColor(dynamic, 120, 200)).toBe('#00ff00')
    expect(limitColor(dynamic, 250, 200)).toBe('#ff0000')
    expect(limitColor(dynamic, 250, null)).toBeNull()
    expect(limitColor(null, 1)).toBeNull()
  })

  it('builds the Value widget text with percent of another widget and limit colors', () => {
    const ref = widget('9', 'Value', { rows: [drow('r')] })
    const w = widget('1', 'Value', {
      rows: [drow('a')],
      config: {
        percent: { source: '9', diff: false, minFractionDigits: 0, maxFractionDigits: 0 },
        limit: { source: null, upper: { enabled: true, value: 40, color: '#ff0000', offset: 1 }, lower: null },
        dimIncompletePeriod: true,
      },
    })
    const data = ddata({
      '1': { rows: [{ key: 'a', status: 'OK', unit: 'kWh', value: 50 }] },
      '9': { rows: [{ key: 'r', status: 'OK', unit: 'kWh', value: 200 }] },
    })
    const values = widgetValues([w, ref], data)
    expect(values.get('9')).toBe(200)
    const row = widgetRows(w, data.widgets['1'])[0]
    const d = valueDisplay(w, row, values, 'de', at(2), at(1, 12))
    expect(d).toEqual({ text: '50,00 kWh (25%)', color: '#ff0000', dimmed: true })
    expect(valueDisplay({ ...w, showValue: false }, row, values, 'de', null).text).toBe('25%')
    expect(valueDisplay({ ...w, showValue: false, config: {} }, row, values, 'de', null).text).toBe('- %')
    // diff percent: the limit is compared with the percent value
    const diff = { ...w, config: { ...w.config, percent: { source: '9', diff: true, minFractionDigits: 0, maxFractionDigits: 0 } } }
    expect(valueDisplay(diff, row, values, 'de', null)).toMatchObject({ text: '50,00 kWh (75%)', color: '#ff0000' })
  })

  it('marks rows without permission or data', () => {
    const w = widget('1', 'Value', { rows: [drow('a', { readable: false, name: null }), drow('b'), drow('c')] })
    const rows = widgetRows(w, { rows: [{ key: 'b', status: 'ERROR', message: 'x' }, { key: 'c', status: 'OK', text: 'open' }] })
    expect(rows.map((r) => r.state)).toEqual(['NO_PERMISSION', 'ERROR', 'OK'])
    expect(rows[2].text).toBe('open')
  })
})

describe('time frame bar', () => {
  const config = dashboard([])

  it('lists the time frames and starts with the dashboard one', () => {
    expect(timeFrameOptions({ timeFrame: 'P1M', timeFrames: [{ id: 'P1D', custom: false }] }).map((o) => o.id)).toEqual(['P1M', 'P1D'])
    expect(defaultTimeBar(config)).toEqual({ timeFrame: 'P1D', date: null, from: null, to: null })
  })

  it('reads and writes the URL query', () => {
    const date = new Date(at(3, 12))
    const q = timeBarToQuery({ timeFrame: 'P1W', date, from: null, to: null })
    expect(q.tf).toBe('P1W')
    expect(timeBarFromQuery(q, config)).toEqual({ timeFrame: 'P1W', date, from: null, to: null })
    expect(timeBarFromQuery({ tf: 'P99X' }, config).timeFrame).toBe('P1D')
    expect(timeBarFromQuery({ tf: '2005' }, config).timeFrame).toBe('2005')
    const custom = timeBarToQuery({ timeFrame: 'CUSTOM', date: null, from: new Date(at(1)), to: new Date(at(2)) })
    expect(timeBarFromQuery(custom, config)).toMatchObject({ timeFrame: 'CUSTOM', from: new Date(at(1)), to: new Date(at(2)) })
    // to before from is ignored
    expect(timeBarFromQuery({ tf: 'CUSTOM', from: custom.to, to: custom.from }, config).timeFrame).toBe('P1D')
  })

  it('builds the data request', () => {
    expect(dashboardDataRequest({ timeFrame: 'P1D', date: null, from: null, to: null }, 'Europe/Berlin')).toEqual({ timeFrame: 'P1D', date: null, from: null, to: null, timeZone: 'Europe/Berlin' })
    const r = dashboardDataRequest({ timeFrame: 'CUSTOM', date: null, from: new Date(at(1)), to: new Date(at(2)) }, 'UTC')
    expect(Date.parse(r.from!)).toBe(at(1))
    expect(r.date).toBeNull()
  })

  it('steps with previous/next of the answer', () => {
    const state = { timeFrame: 'P1D', date: null, from: null, to: null }
    const data = { previous: iso(at(0, 12)), next: null }
    expect(stepTimeBar(state, data, -1)?.date?.getTime()).toBe(at(0, 12))
    expect(stepTimeBar(state, data, 1)).toBeNull()
    expect(stepTimeBar(state, null, -1)).toBeNull()
  })

  it('shifts CUSTOM periods by their length, not into the future', () => {
    const state = { timeFrame: 'CUSTOM', date: null, from: new Date(at(1)), to: new Date(at(2) - 1) }
    expect(stepTimeBar(state, null, -1, at(5))).toMatchObject({ from: new Date(at(0)), to: new Date(at(1) - 1) })
    expect(stepTimeBar(state, null, 1, at(5))).toMatchObject({ from: new Date(at(2)) })
    expect(stepTimeBar(state, null, 1, at(1, 12))).toBeNull()
  })

  it('selects a time frame (CUSTOM starts with the shown period)', () => {
    expect(selectTimeFrame('P1W', null)).toEqual({ timeFrame: 'P1W', date: null, from: null, to: null })
    expect(selectTimeFrame('CUSTOM', { from: at(1), to: at(2) })).toEqual({ timeFrame: 'CUSTOM', date: null, from: new Date(at(1)), to: new Date(at(2)) })
  })

  it('maps the time frame for Analyse-Links', () => {
    const shown = { from: at(1), to: at(2) }
    expect(analysisQuery({ timeFrame: null }, { timeFrame: 'P1W', date: null, from: null, to: null }, shown)).toEqual({ tf: 'THIS_WEEK' })
    const past = analysisQuery({ timeFrame: null }, { timeFrame: 'P1W', date: new Date(at(1)), from: null, to: null }, shown)
    expect(past.tf).toBe('CUSTOM')
    expect(Date.parse(past.from)).toBe(at(1))
    expect(analysisQuery({ timeFrame: 'P1M' }, { timeFrame: 'P1D', date: null, from: null, to: null }, shown)).toEqual({ tf: 'THIS_MONTH' })
    expect(analysisQuery({ timeFrame: 'P5Y' }, { timeFrame: 'P1D', date: null, from: null, to: null }, shown)).toEqual({})
  })
})

describe('widgets', () => {
  it('maps types to components, unknown types to the empty frame', () => {
    expect(widgetKind('Value')).toBe('value')
    expect(widgetKind('String Value')).toBe('string')
    expect(widgetKind('Toggle Switch')).toBe('input')
    expect(widgetKind('Report-Link')).toBe('link')
    expect(widgetKind('Something new')).toBe('empty')
  })

  it('resolves link targets', () => {
    const state = { timeFrame: 'P1D', date: null, from: null, to: null }
    expect(linkTarget({ kind: 'ANALYSIS', id: 5, timeFrame: null, readable: true }, state, null)).toEqual({
      kind: 'route',
      to: { name: 'analysis', params: { id: 5 }, query: { tf: 'TODAY' } },
    })
    expect(linkTarget({ kind: 'DASHBOARD', id: 6, timeFrame: 'P1W', readable: true }, state, null)).toEqual({
      kind: 'route',
      to: { name: 'dashboard', params: { id: 6 }, query: { tf: 'P1W' } },
    })
    expect(linkTarget({ kind: 'REPORT', id: 7, timeFrame: null, readable: true }, state, null)).toEqual({ kind: 'disabled', reason: 'report' })
    expect(linkTarget({ kind: 'ANALYSIS', id: 5, timeFrame: null, readable: false }, state, null)).toEqual({ kind: 'disabled', reason: 'noAccess' })
  })

  it('builds the Time Frame widget text', () => {
    const data = ddata({
      '3': { from: iso(at(1)), to: iso(at(2) - 60_000), rows: [{ key: 'a', status: 'OK', value: 1, lastTimestamp: iso(at(1, 14, 30)) }] },
    })
    const tf = (config: Record<string, unknown>, rows = [] as ReturnType<typeof drow>[]) => timeFrameText(widget('4', 'Time Frame', { config, rows }), data, 'de')
    expect(tf({ source: '3', start: 'PERIOD_FROM', end: 'PERIOD_UNTIL', format: 'dd.MM.yyyy HH:mm', count: false })).toBe('01.09.2026 00:00 - 01.09.2026 23:59')
    expect(tf({ source: '3', start: 'NONE', end: 'LAST_TS', format: 'HH:mm', count: false })).toBe('14:30')
    expect(tf({ source: '3', start: 'PERIOD_FROM', end: 'NONE', format: 'MMMM yyyy', count: false })).toBe('September 2026')
    expect(tf({ source: null, start: 'PERIOD_FROM', end: 'NONE', format: "'Day' D", count: false })).toBe('Day 244')
    expect(tf({ source: '3', count: true })).toBe('0')
    // own rows: the newest timestamp of the widget
    const own = ddata({ '4': { rows: [{ key: 'x', status: 'OK', lastTimestamp: iso(at(5, 8)) }] } })
    expect(timeFrameText(widget('4', 'Time Frame', { rows: [drow('x')], config: { format: 'dd.MM. HH:mm' } }), own, 'de')).toBe('05.09. 08:00')
  })

  it('reuses the analysis model for Chart and Pie widgets', () => {
    const pie = pieWidgetView(widget('5', 'Pie', { rows: [drow('a'), drow('b', { readable: false, name: null })] }), { rows: [{ key: 'a', status: 'OK', value: 4 }] }, '(no access)')
    expect(pie?.kind).toBe('pie')
    expect(pie?.rows.map((r) => [r.name, r.state, r.pieValue])).toEqual([
      ['Meter a', 'OK', 4],
      ['(no access)', 'NO_PERMISSION', null],
    ])
    const chart = chartWidgetView(widget('6', 'Chart', { rows: [drow('a')] }), { from: iso(at(1)), to: iso(at(2)), rows: [{ key: 'a', status: 'OK', samples: [[at(1), 3, null]] }] }, '-')
    expect(chart?.kind).toBe('xy')
    expect(chart?.rows[0].samples).toHaveLength(1)
  })

  it('builds gauge stops and sankey links', () => {
    expect(gaugeStops([{ start: 50, end: 100, color: '#ff0000' }], 0, 100)).toEqual([
      [0.5, '#e3e7ed'],
      [1, '#ff0000'],
    ])
    const links = sankeyLinks(
      [
        { key: 'a', children: ['b', 'c'] },
        { key: 'b', children: ['a'] },
      ],
      new Map([
        ['a', 10],
        ['b', 6],
        ['c', 4],
      ]),
    )
    expect(links).toEqual([
      { source: 'a', target: 'b', value: 6 },
      { source: 'a', target: 'c', value: 4 },
    ])
  })
})

describe('Joda patterns', () => {
  it('formats the common fields', () => {
    const ms = new Date(2026, 0, 4, 7, 5, 9, 45).getTime()
    expect(formatJoda(ms, 'yyyy-MM-dd HH:mm:ss.SSS', 'de')).toBe('2026-01-04 07:05:09.045')
    expect(formatJoda(ms, 'd.M.yy h:mm a', 'en')).toBe('4.1.26 7:05 AM')
    expect(formatJoda(ms, "EEEE, dd. MMMM yyyy 'KW' ww", 'de')).toBe('Sonntag, 04. Januar 2026 KW 01')
    expect(formatJoda(ms, "'it''s' MMM", 'en')).toBe("it's Jan")
    expect(isoWeek(new Date(2026, 11, 31))).toEqual({ week: 53, year: 2026 })
  })
})

describe('start dashboard and image download', () => {
  it('opens the start dashboard only on the home page', () => {
    expect(startDashboardFor({ startDashboard: 12 }, 'home')).toBe(12)
    expect(startDashboardFor({ startDashboard: 12 }, 'analysis')).toBeNull()
    expect(startDashboardFor({ startDashboard: null }, 'home')).toBeNull()
    expect(startDashboardFor(null, 'home')).toBeNull()
  })

  it('downloads blobs with the token and refreshes on 401', async () => {
    let token = 'old'
    const calls: string[] = []
    const fetchFn = (async (_url: string, init: RequestInit) => {
      const auth = (init.headers as Record<string, string>).Authorization
      calls.push(auth)
      return auth === 'Bearer new' ? new Response('png', { status: 200 }) : new Response('', { status: 401 })
    }) as unknown as typeof fetch
    const client = new HttpClient({ accessToken: () => token, refresh: async () => ((token = 'new'), true), onUnauthorized: () => {} }, fetchFn)
    const blob = await client.getBlob('/web/objects/1/image')
    expect(await blob.text()).toBe('png')
    expect(calls).toEqual(['Bearer old', 'Bearer new'])

    const notFound = new HttpClient({ accessToken: () => 't', refresh: async () => false, onUnauthorized: () => {} }, (async () => new Response('', { status: 404 })) as unknown as typeof fetch)
    await expect(notFound.getBlob('/x')).rejects.toBeInstanceOf(HttpError)
  })
})
