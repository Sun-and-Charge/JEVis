/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import {
  CONFIGURATIONS_TAB,
  attributeText,
  configurationValue,
  contractSites,
  contractTabs,
  contractWhere,
  filterConfigurations,
  filterContractObjects,
  hasHistory,
  historyChartOption,
  historyLines,
  historyPoints,
  rangingRows,
  rangingText,
} from '../src/accounting/contracts'
import {
  checkInvoiceRange,
  defaultInvoiceFilter,
  defaultInvoiceRange,
  filterInvoices,
  invoiceFileName,
  invoiceMedium,
  invoiceMonth,
  invoiceSummaries,
  invoicesCsv,
  isPdfFile,
  itemCell,
  itemColumns,
  mediumOptions,
  monthRange,
  monthlyChartOption,
  monthlySeries,
  signed,
  type InvoiceCsvHeaders,
  type InvoiceFilter,
} from '../src/accounting/invoices'
import { contractHistoryPath, contractsPath } from '../src/api/contracts'
import { invoiceFilePath, invoicesPath } from '../src/api/invoices'
import { attribute, configuration, contractList, contractObject, invoice, invoiceSet } from './fixtures/accounting'

const openEnd = (from: string) => `ab ${from}`

describe('contract tabs and filter', () => {
  it('orders the groups as fixed, unknown kinds after them, configurations last, empty ones left out', () => {
    const list = contractList()
    list.groups.unshift({ kind: 'dues', objects: [contractObject(1)] }, { kind: 'custom', objects: [contractObject(2)] }, { kind: 'meteringOperators', objects: [] })
    expect(contractTabs(list).map((t) => t.id)).toEqual(['suppliers', 'gridOperators', 'contractors', 'dues', 'custom', CONFIGURATIONS_TAB])
    expect(contractTabs({ groups: [], configurations: [] })).toEqual([])
    expect(contractTabs(null)).toEqual([])
  })

  it('searches name, class label, path, contractor and text values; filters by site', () => {
    const objects = contractList().groups.flatMap((g) => g.objects)
    const ids = (filter: { text: string; site: 'all' | 'none' | number }) => filterContractObjects(objects, filter, 'de').map((o) => o.id)
    expect(ids({ text: '', site: 'all' })).toEqual([23901, 23920, 23910])
    expect(ids({ text: 'netzbetreiber', site: 'all' })).toEqual([23920])
    expect(ids({ text: 'k-4711', site: 'all' })).toEqual([23901])
    expect(ids({ text: 'nord stromlieferant', site: 'all' })).toEqual([23901])
    expect(ids({ text: 'partner', site: 'all' })).toEqual([23910])
    expect(ids({ text: '', site: 7101 })).toEqual([23920])
    expect(filterConfigurations([configuration(1)], { text: 'v-1001', site: 'all' }, 'de')).toHaveLength(1)
    expect(filterConfigurations([configuration(1)], { text: 'gas', site: 'all' }, 'de')).toHaveLength(0)
    expect(contractSites(contractList(), 'de').map((s) => s.name)).toEqual(['Musterwerk', 'Zweigwerk'])
    expect(contractWhere({ site: { id: 1, name: 'Musterwerk' }, path: 'A / B' }, 'de')).toBe('Musterwerk / A / B')
    expect(contractWhere({ site: null, path: null }, 'de')).toBe('')
  })
})

describe('contract values', () => {
  it('formats numbers with unit in the UI locale, texts as they are, empty as ""', () => {
    expect(attributeText(attribute('a', { value: 0.28, unit: '€/kWh' }), 'de', openEnd)).toBe('0,28 €/kWh')
    expect(attributeText(attribute('a', { value: 0.283456, unit: '€/kWh' }), 'de', openEnd)).toBe('0,28346 €/kWh')
    expect(attributeText(attribute('a', { value: 1234.5, unit: '€' }), 'en', openEnd)).toBe('1,234.5 €')
    expect(attributeText(attribute('a', { value: 19 }), 'de', openEnd)).toBe('19')
    expect(attributeText(attribute('a', { type: 'text', value: 'K-4711' }), 'de', openEnd)).toBe('K-4711')
    expect(attributeText(attribute('a', { value: null }), 'de', openEnd)).toBe('')
    expect(attributeText(attribute('a', { type: 'text', value: '' }), 'de', openEnd)).toBe('')
  })

  it('builds ranging tables sorted by start, open ends and compact texts', () => {
    const steps = [
      { from: 100, to: null, value: 10 },
      { from: 0, to: 100, value: 12.5 },
    ]
    expect(rangingRows(steps, '€/kW', 'de')).toEqual([
      { from: '0', to: '100', value: '12,5 €/kW' },
      { from: '100', to: '', value: '10 €/kW' },
    ])
    expect(rangingText(steps, '€/kW', 'de', openEnd)).toBe('0–100: 12,5 €/kW · ab 100: 10 €/kW')
    expect(rangingRows([{ from: 1000.5, to: 2500, value: null }], null, 'de')).toEqual([{ from: '1.000,5', to: '2.500', value: '' }])
    expect(rangingRows(null, null, 'de')).toEqual([])
    expect(attributeText(attribute('a', { type: 'ranging', value: steps, unit: null }), 'de', openEnd)).toBe('0–100: 12,5 · ab 100: 10')
    expect(attributeText(attribute('a', { type: 'ranging', value: [] }), 'de', openEnd)).toBe('')
  })

  it('offers a history only for numbers and ranging values with more than one sample', () => {
    expect(hasHistory({ type: 'number', history: true })).toBe(true)
    expect(hasHistory({ type: 'ranging', history: true })).toBe(true)
    expect(hasHistory({ type: 'number', history: false })).toBe(false)
    expect(hasHistory({ type: 'text', history: true })).toBe(false)
  })

  it('formats configuration fields: dates local (texts unchanged), one energy type translated', () => {
    const c = configuration(1, { energyType: null })
    const energy = (code: string) => ({ ELECTRICITY: 'Strom', COMMUNITY_HEATING: 'Fernwärme' })[code] ?? code
    expect(configurationValue(c, 'contractDate', 'de', energy)).toBe('01.12.2025')
    expect(configurationValue(c, 'end', 'de', energy)).toBe('31.12.2027')
    expect(configurationValue(c, 'periodOfNotice', 'de', energy)).toBe('30.09.2027')
    expect(configurationValue({ ...c, periodOfNotice: '3 Monate' }, 'periodOfNotice', 'de', energy)).toBe('3 Monate')
    expect(configurationValue(c, 'energyType', 'de', energy)).toBe('Strom')
    expect(configurationValue({ ...c, energyType: 'COMMUNITY_HEATING' }, 'energyType', 'de', energy)).toBe('Fernwärme')
    expect(configurationValue({ ...c, contractType: 'Wasser' }, 'energyType', 'de', energy)).toBe('Wasser')
    expect(configurationValue(c, 'contractNumber', 'de', energy)).toBe('V-1001')
    expect(configurationValue({ ...c, start: null }, 'start', 'de', energy)).toBe('')
  })
})

describe('price history', () => {
  const history = {
    unit: '€/kWh',
    type: 'number',
    samples: [
      { timestamp: '2026-01-01T00:00:00.000+01:00', value: 0.28 },
      { timestamp: '2025-01-01T00:00:00.000+01:00', value: 0.31 },
      { timestamp: 'kaputt', value: 0.5 },
      { timestamp: '2024-01-01T00:00:00.000+01:00', value: null },
    ],
  }

  it('sorts numeric points oldest first and builds lines newest first', () => {
    expect(historyPoints(history)).toEqual([
      [Date.parse('2025-01-01T00:00:00.000+01:00'), 0.31],
      [Date.parse('2026-01-01T00:00:00.000+01:00'), 0.28],
    ])
    const lines = historyLines(history, 'de', openEnd, 2)
    expect(lines.map((l) => [l.date, l.value])).toEqual([
      ['01.01.2026', '0,28 €/kWh'],
      ['01.01.2025', '0,31 €/kWh'],
    ])
    const ranging = historyLines({ unit: '€/kW', type: 'ranging', samples: [{ timestamp: '2026-01-01T00:00:00.000+01:00', value: [{ from: 0, to: null, value: 9 }] }] }, 'de', openEnd)
    expect(ranging[0].value).toBe('ab 0: 9 €/kW')
  })

  it('draws a step chart (step end) carried on to now', () => {
    const points = historyPoints(history)
    const now = Date.parse('2026-11-11T12:00:00Z')
    const option = historyChartOption(points, 'Arbeitspreis HT', '€/kWh', 'de', now) as { series: { step: string; data: number[][] }[] }
    expect(option.series[0].step).toBe('end')
    expect(option.series[0].data).toEqual([...points, [now, 0.28]])
    const past = historyChartOption(points, 'x', null, 'de', 0) as { series: { data: number[][] }[] }
    expect(past.series[0].data).toHaveLength(2)
  })

  it('builds the API paths', () => {
    expect(contractsPath('de')).toBe('/web/contracts?lang=de')
    expect(contractHistoryPath(23901, 'Energy Price HT')).toBe('/web/contracts/objects/23901/history?attribute=Energy+Price+HT&limit=100')
  })
})

describe('invoice filter and range', () => {
  const filter = (o: Partial<InvoiceFilter> = {}): InvoiceFilter => ({ ...defaultInvoiceFilter(), ...o })
  const ids = (list: { id: number }[]) => list.map((i) => i.id)

  it('defaults to the last 24 months up to the end of the current month', () => {
    expect(defaultInvoiceRange(new Date(2026, 10, 11))).toEqual({ from: '2024-12-01', to: '2026-11-30' })
    expect(defaultInvoiceRange(new Date(2026, 1, 3))).toEqual({ from: '2024-03-01', to: '2026-02-28' })
  })

  it('checks the time frame (at most 10 years)', () => {
    expect(checkInvoiceRange('2016-11-01', '2026-11-01')).toBe('ok')
    expect(checkInvoiceRange('2016-11-01', '2026-11-02')).toBe('tooLong')
    expect(checkInvoiceRange('2026-11-02', '2026-11-01')).toBe('order')
    expect(checkInvoiceRange('', '2026-11-01')).toBe('missing')
  })

  it('normalises media and offers the present ones in the fixed order', () => {
    expect(invoiceMedium({ medium: 'gas' })).toBe('GAS')
    expect(invoiceMedium({ medium: 'WATER' })).toBe('OTHER')
    expect(invoiceMedium({ medium: null })).toBe('OTHER')
    expect(mediumOptions(invoiceSet())).toEqual([
      { id: 'ELECTRICITY', count: 3 },
      { id: 'GAS', count: 1 },
    ])
    expect(mediumOptions([invoice(1)], ['DISTRICT_HEATING']).map((m) => m.id)).toEqual(['ELECTRICITY', 'DISTRICT_HEATING'])
  })

  it('filters by medium, site and search in number, seller and meter point', () => {
    const all = invoiceSet()
    expect(ids(filterInvoices(all, filter(), 'de'))).toEqual([24601, 24604, 24600, 24603])
    expect(ids(filterInvoices(all, filter({ media: ['GAS'] }), 'de'))).toEqual([24604])
    expect(ids(filterInvoices(all, filter({ site: 7101 }), 'de'))).toEqual([24604])
    expect(ids(filterInvoices(all, filter({ text: 'gu-2026' }), 'de'))).toEqual([24603])
    expect(ids(filterInvoices(all, filter({ text: 'gaswerk' }), 'de'))).toEqual([24604])
    expect(ids(filterInvoices(all, filter({ text: '0002' }), 'de'))).toEqual([24604])
  })
})

describe('invoice totals and monthly series', () => {
  it('signs credit notes negative', () => {
    expect(signed(100, true)).toBe(-100)
    expect(signed(-100, true)).toBe(-100)
    expect(signed(100, false)).toBe(100)
    expect(signed(null, true)).toBeNull()
  })

  it('sums per medium; credit notes reduce the totals (also when stored positive)', () => {
    const list = [...invoiceSet(), invoice(24609, { periodStart: '2026-08-01', consumption: 500, net: 100, tax: 19, gross: 119, credit: true })]
    const [el, gas] = invoiceSummaries(list)
    expect(el.medium).toBe('ELECTRICITY')
    expect(el.count).toBe(4)
    expect(el.credits).toBe(2)
    expect(el.consumption).toEqual([{ key: 'kWh', value: 41250 + 40000 - 1000 - 500 }])
    expect(el.net[0].key).toBe('EUR')
    expect(el.net[0].value).toBeCloseTo(11550 + 11200 - 280 - 100)
    expect(el.gross[0].value).toBeCloseTo(13744.5 + 13328 - 333.2 - 119)
    expect(gas).toMatchObject({ medium: 'GAS', count: 1, consumption: [{ key: 'm³', value: 1200 }] })
  })

  it('aggregates by month of the period start (else the date), filling gaps', () => {
    expect(invoiceMonth({ periodStart: '2026-09-01', date: '2026-10-02' })).toBe('2026-09')
    expect(invoiceMonth({ periodStart: null, date: '2026-10-02' })).toBe('2026-10')
    expect(invoiceMonth({ periodStart: null, date: null })).toBeNull()
    expect(monthRange('2025-11', '2026-02')).toEqual(['2025-11', '2025-12', '2026-01', '2026-02'])
    const list = [...invoiceSet(), invoice(24610, { periodStart: '2026-06-01', periodEnd: '2026-06-30', consumption: 39000, gross: 13000 })]
    const [el, gas] = monthlySeries(list)
    expect(el.months).toEqual(['2026-06', '2026-07', '2026-08', '2026-09', '2026-10'])
    expect(el.unit).toBe('kWh')
    expect(el.currency).toBe('EUR')
    expect(el.consumption).toEqual([39000, null, null, 39000, 41250])
    expect(el.gross[3]).toBeCloseTo(13328 - 333.2)
    expect(gas.months).toEqual(['2026-10'])
  })

  it('leaves other units out of the bars of a medium', () => {
    const [el] = monthlySeries([invoice(1), invoice(2), invoice(3, { consumption: 40, consumptionUnit: 'MWh' })])
    expect(el.unit).toBe('kWh')
    expect(el.consumption).toEqual([82500])
  })

  it('builds a column chart with consumption and gross cost on two axes', () => {
    const [el] = monthlySeries(invoiceSet())
    const option = monthlyChartOption(el, { consumption: 'Verbrauch', cost: 'Kosten brutto', month: (k) => 'M' + k }, 'de') as {
      yAxis: unknown[]
      xAxis: { data: string[] }
      series: { type: string; name: string; yAxisIndex: number; data: (number | null)[] }[]
    }
    expect(option.yAxis).toHaveLength(2)
    expect(option.xAxis.data).toEqual(['M2026-09', 'M2026-10'])
    expect(option.series.map((s) => [s.type, s.name, s.yAxisIndex])).toEqual([
      ['bar', 'Verbrauch [kWh]', 0],
      ['bar', 'Kosten brutto [EUR]', 1],
    ])
    expect(option.series[0].data).toEqual([39000, 41250])
  })
})

describe('invoice CSV, files and line items', () => {
  const headers: InvoiceCsvHeaders = {
    periodStart: 'Von',
    periodEnd: 'Bis',
    number: 'Nr',
    date: 'Datum',
    seller: 'Lieferant',
    medium: 'Medium',
    meterPoint: 'Zählpunkt',
    consumption: 'Verbrauch',
    unit: 'Einheit',
    net: 'Netto',
    tax: 'Steuer',
    gross: 'Brutto',
    currency: 'Währung',
    credit: 'Gutschrift',
    site: 'Standort',
    path: 'Pfad',
  }
  const env = { mediumName: (m: string) => (m === 'ELECTRICITY' ? 'Strom' : m), yes: 'ja' }

  it('writes the CSV with BOM, CRLF, locale separators, negative credit notes and escaped formulas', () => {
    const csv = invoicesCsv([invoice(1), invoice(2, { number: '=SUMME(A1)', seller: 'Nord; Süd', consumption: -1000, gross: -333.2, credit: true, site: null, path: null })], headers, env, 'de')
    expect(csv.startsWith('﻿')).toBe(true)
    const lines = csv.slice(1).split('\r\n')
    expect(lines[0]).toBe('Von;Bis;Nr;Datum;Lieferant;Medium;Zählpunkt;Verbrauch;Einheit;Netto;Steuer;Brutto;Währung;Gutschrift;Standort;Pfad')
    expect(lines[1]).toBe('2026-10-01;2026-10-31;EN-202610-S;2026-11-03;Energieversorger Nord;Strom;DE0000000000000000000000000000001;41250;kWh;11550;2194,5;13744,5;EUR;;Musterwerk;Energieverträge / Energierechnungen / Strom')
    expect(lines[2]).toContain(";'=SUMME(A1);")
    expect(lines[2]).toContain(';"Nord; Süd";')
    expect(lines[2]).toContain(';-1000;kWh;')
    expect(lines[2]).toContain(';-333,2;EUR;ja;;')
    expect(lines[3]).toBe('')
    const en = invoicesCsv([invoice(1)], headers, env, 'en')
    expect(en.split('\r\n')[1]).toContain(',2194.5,13744.5,EUR,')
  })

  it('detects PDF files and names downloads', () => {
    expect(isPdfFile('EN-202610-S.PDF')).toBe(true)
    expect(isPdfFile('GS-202610.xml')).toBe(false)
    expect(isPdfFile(null)).toBe(false)
    expect(invoiceFileName(invoice(1))).toBe('EN-202610-S.pdf')
    expect(invoiceFileName(invoice(1, { file: null }))).toBe('EN-202610-S.pdf')
    expect(invoiceFileName(invoice(7, { file: null, number: null }))).toBe('invoice-7.pdf')
  })

  it('shows the item keys that exist in the preferred order, period combined', () => {
    const items = [
      { description: 'Arbeitspreis', net: 10, quantity: 2, custom: 'x', periodStart: '2026-10-01', periodEnd: '2026-10-31', unitPrice: null },
      { description: 'Grundpreis', taxRate: 19 },
    ]
    expect(itemColumns(items)).toEqual(['description', 'quantity', 'net', 'taxRate', 'period', 'custom'])
    expect(itemColumns([])).toEqual([])
    expect(itemCell(items[0], 'period', 'de')).toBe('01.10.2026 – 31.10.2026')
    expect(itemCell(items[1], 'taxRate', 'de')).toBe('19 %')
    expect(itemCell(items[0], 'net', 'de')).toBe('10,00')
    expect(itemCell({ unitPrice: 0.25 }, 'unitPrice', 'de')).toBe('0,25')
    expect(itemCell(items[1], 'quantity', 'de')).toBe('')
    expect(itemCell({ nested: { a: 1 } }, 'nested', 'de')).toBe('{"a":1}')
  })

  it('builds the API paths', () => {
    expect(invoicesPath({ from: '2024-12-01', to: '2026-11-30', timeZone: 'Europe/Berlin' })).toBe('/web/invoices?from=2024-12-01&to=2026-11-30&timeZone=Europe%2FBerlin')
    expect(invoiceFilePath(24601)).toBe('/web/invoices/24601/file')
    expect(invoiceFilePath(24601, true)).toBe('/web/invoices/24601/file?inline=true')
  })
})
