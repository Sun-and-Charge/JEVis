/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import { toIsoLocal } from '../src/analysis/settings'
import { costDefinitionPath, costResultPath } from '../src/api/costs'
import { HttpError } from '../src/api/http'
import {
  analysisRange,
  costCsv,
  costCsvName,
  costEntries,
  costErrorKind,
  costRequest,
  costStateFromQuery,
  costStateToQuery,
  defaultCostState,
  filterCostEntries,
  formatCostNumber,
  gridLayout,
  groupBySite,
  isCostStateKey,
  orderCells,
  parseCostId,
  periodText,
  separatorOrientation,
  serverMessage,
  singleColumnCells,
} from '../src/costs/costs'
import { COST_LIST, configurationDefinition, costResult, NOVEMBER, RESULT_CELLS, templateDefinition, YEAR } from './fixtures/costs'

describe('cost list', () => {
  it('builds entries, configurations first, with template state', () => {
    const entries = costEntries(COST_LIST)
    expect(entries.map((e) => [e.kind, e.id])).toEqual([
      ['configuration', 23950],
      ['configuration', 23951],
      ['template', 23960],
      ['template', 23961],
    ])
    expect(entries[0]).toMatchObject({ templateName: 'Stromkosten', templateReadable: true })
    expect(entries[1]).toMatchObject({ templateName: null, templateReadable: false })
    expect(costEntries(null)).toEqual([])
  })

  it('groups by site in the order of the answer and searches name, path, site and template', () => {
    const entries = costEntries(COST_LIST)
    const groups = groupBySite(entries.filter((e) => e.kind === 'template'), 'de')
    expect(groups.map((g) => [g.key, g.site, g.entries.length])).toEqual([
      ['23053', 'Musterwerk', 1],
      ['none', null, 1],
    ])
    expect(filterCostEntries(entries, 'gas', 'de').map((e) => e.id)).toEqual([23951])
    expect(filterCostEntries(entries, 'stromkosten', 'de').map((e) => e.id)).toEqual([23950, 23960])
    expect(filterCostEntries(entries, 'musterwerk vorlagen', 'de').map((e) => e.id)).toEqual([23960])
    expect(filterCostEntries(entries, '  ', 'de')).toHaveLength(4)
  })

  it('parses the id of the route', () => {
    expect(parseCostId('23950')).toBe(23950)
    expect(parseCostId(['23960'])).toBe(23960)
    expect(parseCostId('0')).toBeNull()
    expect(parseCostId('abc')).toBeNull()
    expect(parseCostId(undefined)).toBeNull()
  })

  it('builds the API paths', () => {
    expect(costDefinitionPath(23950, 'de')).toBe('/web/costs/23950?lang=de')
    expect(costResultPath(23950)).toBe('/web/costs/23950/result')
  })
})

describe('cost state and query', () => {
  const now = new Date(2026, 10, 11, 10, 0)

  it('defaults to the default time frame and the template defaults', () => {
    expect(defaultCostState(configurationDefinition(), now)).toEqual({ timeFrame: 'P1M', from: null, to: null, selections: {} })
    expect(defaultCostState(templateDefinition(), now).selections).toEqual({ i1: 23684 })
    // unknown default: the first; none at all: custom, the current month
    expect(defaultCostState(configurationDefinition({ defaultTimeFrame: 'P1W' }), now).timeFrame).toBe('P1M')
    const custom = defaultCostState(configurationDefinition({ timeFrames: [], defaultTimeFrame: null }), now)
    expect(custom.timeFrame).toBe('CUSTOM')
    expect(custom.from).toEqual(new Date(2026, 10, 1))
    expect(custom.to).toEqual(now)
  })

  it('reads and writes time frame, custom range and selections', () => {
    const def = templateDefinition()
    const from = new Date(2026, 9, 1)
    const to = new Date(2026, 9, 31, 23, 59, 59, 999)
    const state = { timeFrame: 'CUSTOM', from, to, selections: { i1: 23685 } }
    const query = costStateToQuery(state, def)
    expect(query).toEqual({ tf: 'CUSTOM', from: toIsoLocal(from), to: toIsoLocal(to), 'in.i1': '23685' })
    expect(costStateFromQuery(query, def, now)).toEqual(state)
    expect(costStateToQuery({ timeFrame: 'P1Y', from: null, to: null, selections: {} }, configurationDefinition())).toEqual({ tf: 'P1Y' })
  })

  it('ignores invalid query values', () => {
    const def = templateDefinition()
    const defaults = defaultCostState(def, now)
    expect(costStateFromQuery({ tf: 'P5Y' }, def, now)).toEqual(defaults)
    expect(costStateFromQuery({ tf: 'CUSTOM', from: '2026-11-30T00:00:00+01:00', to: '2026-11-01T00:00:00+01:00' }, def, now)).toEqual(defaults)
    expect(costStateFromQuery({ tf: 'CUSTOM', from: 'x', to: '2026-11-01' }, def, now)).toEqual(defaults)
    // not an option of the input, unknown input, not a number
    expect(costStateFromQuery({ 'in.i1': '99999', 'in.i9': '23684', 'in.x': 'abc' }, def, now).selections).toEqual({ i1: 23684 })
    // configurations have no selectable inputs
    expect(costStateFromQuery({ 'in.i1': '23684' }, configurationDefinition(), now).selections).toEqual({})
    // "+" of the offset typed into a link arrives as space
    const linked = costStateFromQuery({ tf: 'CUSTOM', from: '2026-11-01T00:00:00.000 01:00', to: '2026-11-02T00:00:00.000 01:00' }, def, now)
    expect(linked.timeFrame).toBe('CUSTOM')
    expect(linked.from?.toISOString()).toBe('2026-10-31T23:00:00.000Z')
  })

  it('knows its query keys', () => {
    expect(['tf', 'from', 'to', 'in.i1'].every(isCostStateKey)).toBe(true)
    expect(isCostStateKey('id')).toBe(false)
  })

  it('builds the request, selections only for templates', () => {
    const state = { timeFrame: 'P1M', from: null, to: null, selections: { i1: 23685 } }
    expect(costRequest(state, templateDefinition(), 'Europe/Berlin')).toEqual({ timeFrame: 'P1M', from: null, to: null, timeZone: 'Europe/Berlin', selections: { i1: 23685 } })
    expect(costRequest(state, configurationDefinition(), 'Europe/Berlin')).toEqual({ timeFrame: 'P1M', from: null, to: null, timeZone: 'Europe/Berlin' })
    const from = new Date(2026, 10, 1)
    const to = new Date(2026, 10, 15)
    expect(costRequest({ timeFrame: 'CUSTOM', from, to, selections: {} }, configurationDefinition(), 'UTC')).toMatchObject({ timeFrame: 'CUSTOM', from: toIsoLocal(from), to: toIsoLocal(to) })
  })
})

describe('cost result grid', () => {
  it('orders the cells by row and column', () => {
    expect(orderCells(RESULT_CELLS).map((c) => `${c.row}/${c.column}/${c.kind}`)).toEqual(['0/0/label', '1/0/value', '1/2/separator', '2/0/separator', '3/0/value', '3/1/text'])
  })

  it('places the cells exactly by row/column/span', () => {
    const layout = gridLayout(costResult())
    expect(layout.rows).toBe(4)
    expect(layout.columns).toBe(3)
    expect(layout.cells.map((p) => [p.gridRow, p.gridColumn, p.orientation])).toEqual([
      ['1 / span 1', '1 / span 3', null],
      ['2 / span 1', '1 / span 2', null],
      ['2 / span 2', '3 / span 1', 'vertical'],
      ['3 / span 1', '1 / span 2', 'horizontal'],
      ['4 / span 1', '1 / span 1', null],
      ['4 / span 1', '2 / span 1', null],
    ])
    // cells outside the given size widen the grid; bad spans count as 1
    const wide = gridLayout({ rows: 1, columns: 1, cells: [{ row: 2, column: 3, rowSpan: 0, columnSpan: 2, kind: 'label', name: 'x' }] })
    expect([wide.rows, wide.columns, wide.cells[0].gridRow]).toEqual([3, 5, '3 / span 1'])
    expect(gridLayout({ rows: 0, columns: 0, cells: [] })).toEqual({ rows: 0, columns: 1, cells: [] })
  })

  it('derives the separator orientation from the spans', () => {
    expect(separatorOrientation({ orientation: null, rowSpan: 1, columnSpan: 3 })).toBe('horizontal')
    expect(separatorOrientation({ orientation: undefined, rowSpan: 2, columnSpan: 1 })).toBe('vertical')
    expect(separatorOrientation({ orientation: 'vertical', rowSpan: 1, columnSpan: 3 })).toBe('vertical')
  })

  it('collapses to one column in reading order without vertical separators', () => {
    const cells = singleColumnCells(gridLayout(costResult()))
    expect(cells.map((p) => p.cell.name ?? p.orientation)).toEqual(['Strom', 'Arbeitspreis', 'horizontal', 'Netz', 'Tarif'])
  })

  it('formats numbers with 2 decimals in the UI locale, missing values as dash', () => {
    expect(formatCostNumber(1234.56, 'de')).toBe('1.234,56')
    expect(formatCostNumber(1234.5, 'en')).toBe('1,234.50')
    expect(formatCostNumber(0, 'de')).toBe('0,00')
    expect(formatCostNumber(null, 'de')).toBe('–')
    expect(formatCostNumber(Number.NaN, 'de')).toBe('–')
  })

  it('exports name; value; unit of value and text cells in row/column order', () => {
    const csv = costCsv(RESULT_CELLS, 'de', { name: 'Name', value: 'Wert', unit: 'Einheit' })
    expect(csv.startsWith('﻿')).toBe(true)
    expect(csv.slice(1).split('\r\n')).toEqual(['Name;Wert;Einheit', 'Arbeitspreis;1234,56;€', 'Netz;;€', 'Tarif;HT/NT;', ''])
    const en = costCsv([{ row: 0, column: 0, rowSpan: 1, columnSpan: 1, kind: 'text', name: 'Hinweis', text: 'a, b' }], 'en', { name: 'Name', value: 'Value', unit: 'Unit' })
    expect(en.slice(1).split('\r\n')[1]).toBe('Hinweis,"a, b",')
    expect(costCsvName('Strom Musterwerk', NOVEMBER.from, NOVEMBER.to)).toBe('Strom Musterwerk 2026-11-01_2026-11-30.csv')
    expect(costCsvName('A/B', null, null)).toBe('A_B.csv')
  })
})

describe('cost period and analysis range', () => {
  it('shows the resolved period as dates for whole days', () => {
    expect(periodText(NOVEMBER.from, NOVEMBER.to, 'de')).toBe('01.11.2026 – 30.11.2026')
    expect(periodText(toIsoLocal(new Date(2026, 10, 1)), toIsoLocal(new Date(2026, 11, 1)), 'de')).toBe('01.11.2026 – 30.11.2026')
    expect(periodText(toIsoLocal(new Date(2026, 10, 1, 6, 30)), toIsoLocal(new Date(2026, 10, 2, 18, 0)), 'de')).toBe('01.11.2026, 06:30 – 02.11.2026, 18:00')
    expect(periodText('x', null, 'de')).toBe('x')
  })

  it('allows the row chart for at most 31 days', () => {
    expect(analysisRange(NOVEMBER.from, NOVEMBER.to)).toEqual({ from: Date.parse(NOVEMBER.from), to: Date.parse(NOVEMBER.to) })
    expect(analysisRange(YEAR.from, YEAR.to)).toBeNull()
    expect(analysisRange('2026-01-01T00:00:00Z', '2026-02-01T00:00:00Z')).not.toBeNull()
    expect(analysisRange('2026-01-01T00:00:00Z', '2026-02-01T01:00:01Z')).toBeNull()
    // October with the change to winter time (31 days + 1 hour) is cut to 31 days
    const october = analysisRange('2026-10-01T00:00:00.000+02:00', '2026-10-31T23:59:59.999+01:00')
    expect(october).toEqual({ from: Date.parse('2026-10-01T00:00:00.000+02:00'), to: Date.parse('2026-10-01T00:00:00.000+02:00') + 31 * 86_400_000 })
    expect(analysisRange(null, NOVEMBER.to)).toBeNull()
    expect(analysisRange(NOVEMBER.to, NOVEMBER.from)).toBeNull()
  })
})

describe('cost errors', () => {
  it('maps the status codes', () => {
    expect(costErrorKind(new HttpError(404, ''))).toBe('notFound')
    expect(costErrorKind(new HttpError(409, ''))).toBe('template')
    expect(costErrorKind(new HttpError(400, 'x'))).toBe('invalid')
    expect(costErrorKind(new HttpError(500, ''))).toBe('error')
    expect(costErrorKind(new Error('offline'))).toBe('error')
  })

  it('reads the plain-text message of a 400 answer', () => {
    expect(serverMessage(new HttpError(400, ' At most 10 years \n'))).toBe('At most 10 years')
    expect(serverMessage(new HttpError(400, '{"message":"Unknown time frame"}'))).toBe('Unknown time frame')
    expect(serverMessage(new HttpError(400, '<html>…</html>'))).toBe('')
    expect(serverMessage(new Error('x'))).toBe('')
  })
})
