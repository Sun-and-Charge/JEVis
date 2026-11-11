/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadRegisterFile, loadRegisterHistory, loadRegisters, meterQrPath, registerFilePath } from '../src/api/registers'
import { i18n } from '../src/i18n'
import {
  emptyChoice,
  loadColumnChoice,
  registerColumnKey,
  registerColumns,
  saveColumnChoice,
  toggleColumnChoice,
  visibleColumnIds,
  type RegisterEnv,
} from '../src/registers/columns'
import { registerCsv, registerCsvName } from '../src/registers/csv'
import { ALL, defaultFilter, filterRows, meterOptions, meterSummary, plainText, yearOptions } from '../src/registers/filter'
import {
  dueUntilEndOf,
  formatAttribute,
  formatByPeriod,
  formatLast,
  historyLines,
  isMeterExchange,
  mediumLabel,
  targetText,
  verificationState,
  verificationText,
} from '../src/registers/format'
import { baseData, column, equipment, meter } from './fixtures/registers'

const t = (k: string) => i18n.global.t(k)
const te = (k: string) => i18n.global.te(k)

function env(locale = 'de', today = '2026-11-11'): RegisterEnv {
  return { locale, today, t, te }
}

function memoryStore() {
  const map = new Map<string, string>()
  return {
    map,
    getItem: (k: string) => map.get(k) ?? null,
    setItem: (k: string, v: string) => void map.set(k, v),
    removeItem: (k: string) => void map.delete(k),
  }
}

beforeEach(() => {
  i18n.global.locale.value = 'de'
})

describe('register API', () => {
  it('builds the request paths', async () => {
    const get = vi.fn(async () => ({}))
    const getBlob = vi.fn(async () => new Blob())
    await loadRegisters({ get } as never, 'meters', 'de')
    await loadRegisterHistory({ get } as never, 5, 12)
    await loadRegisterFile({ getBlob } as never, 5, 'Data Sheet')
    expect(get).toHaveBeenNthCalledWith(1, '/web/registers/meters?lang=de')
    expect(get).toHaveBeenNthCalledWith(2, '/web/registers/objects/5/history?limit=12')
    expect(getBlob).toHaveBeenNthCalledWith(1, '/web/registers/objects/5/files/Data%20Sheet')
    expect(registerFilePath(7, 'Picture', true)).toBe('/web/registers/objects/7/files/Picture?inline=true')
    expect(meterQrPath(7)).toBe('/web/registers/meters/7/qr.svg')
  })
})

describe('register formatting', () => {
  it('formats attribute values by type', () => {
    expect(formatAttribute(1234.5, column('P', { type: 'NUMBER', unit: 'kW' }), 'de', t)).toBe('1.234,5 kW')
    expect(formatAttribute(1234.5, column('P', { type: 'NUMBER', unit: 'kW' }), 'en', t)).toBe('1,234.5 kW')
    expect(formatAttribute(3, column('N', { type: 'INTEGER' }), 'de', t)).toBe('3')
    expect(formatAttribute(true, column('B', { type: 'BOOLEAN' }), 'de', t)).toBe('Ja')
    expect(formatAttribute(false, column('B', { type: 'BOOLEAN' }), 'de', t)).toBe('Nein')
    expect(formatAttribute('true', column('B', { type: 'BOOLEAN' }), 'de', t)).toBe('Ja')
    expect(formatAttribute('2015-10-01', column('D', { type: 'DATE' }), 'de', t)).toBe('01.10.2015')
    expect(formatAttribute('2015-10-01T08:30:00', column('D', { type: 'DATETIME' }), 'de', t)).toBe('01.10.2015, 08:30')
    expect(formatAttribute('a\nb', column('T', { type: 'TEXTAREA' }), 'de', t)).toBe('a\nb')
    expect(formatAttribute({ file: 'sheet.pdf', image: false }, column('F', { type: 'FILE' }), 'de', t)).toBe('sheet.pdf')
    expect(formatAttribute({ id: 1, attribute: 'Value', name: 'Data', readable: true }, column('X', { type: 'TARGET' }), 'de', t)).toBe('Data')
    expect(formatAttribute(undefined, column('X'), 'de', t)).toBe('')
    expect(formatAttribute(null, column('X'), 'de', t)).toBe('')
  })

  it('shows a target only if readable', () => {
    expect(targetText({ id: 1, attribute: null, name: 'Data', readable: true }, t)).toBe('Data')
    expect(targetText({ id: 1, attribute: null, name: null, readable: false }, t)).toBe('(ohne Leserecht)')
    expect(targetText(null, t)).toBe('')
  })

  it('formats timestamps by the period of the data (wall-clock time as stored)', () => {
    const ts = '2026-10-01T00:00:00.000+02:00'
    expect(formatByPeriod(ts, 'P1M', 'de')).toBe('Oktober 2026')
    expect(formatByPeriod(ts, 'P1Y', 'de')).toBe('2026')
    expect(formatByPeriod(ts, 'P1D', 'de')).toBe('01.10.2026')
    expect(formatByPeriod('2026-10-01T14:15:00+02:00', 'PT15M', 'de')).toBe('01.10.2026, 14:15')
    expect(formatByPeriod('2026-10-01T14:15:00+02:00', null, 'de')).toBe('01.10.2026, 14:15')
    expect(formatByPeriod(null, 'P1M', 'de')).toBe('')
  })

  it('formats the last value with decimals and unit', () => {
    expect(formatLast({ value: 1234.56, unit: 'kWh', timestamp: null, decimals: 1 }, 'de')).toBe('1.234,6 kWh')
    expect(formatLast({ value: 2.5, unit: null, timestamp: null }, 'en')).toBe('2.5')
    expect(formatLast({ value: null, unit: 'kWh', timestamp: null }, 'de')).toBe('')
    expect(formatLast(null, 'de')).toBe('')
  })

  it('recognises meter exchanges', () => {
    expect(isMeterExchange('Meter Tausch')).toBe(true)
    expect(isMeterExchange('meter tausch 2026')).toBe(true)
    expect(isMeterExchange('Ablesung')).toBe(false)
    expect(isMeterExchange(null)).toBe(false)
  })

  it('computes the verification state and the "due until end of year" check', () => {
    const due = (d: string, overdue = false) => ({ verification: { due: d, overdue } })
    expect(verificationState(due('2026-11-01'), '2026-11-11')).toBe('overdue')
    expect(verificationState(due('2027-06-01', true), '2026-11-11')).toBe('overdue')
    expect(verificationState(due('2026-12-31'), '2026-11-11')).toBe('dueSoon')
    expect(verificationState(due('2027-06-01'), '2026-11-11')).toBe('ok')
    expect(verificationState({ verification: null }, '2026-11-11')).toBeNull()
    expect(verificationText(due('2026-11-01'), '2026-11-11', 'de', t)).toBe('01.11.2026 – überfällig')
    expect(dueUntilEndOf(due('2026-12-31'), 2026)).toBe(true)
    expect(dueUntilEndOf(due('2027-01-01'), 2026)).toBe(false)
    expect(dueUntilEndOf(due('2027-01-01'), 2027)).toBe(true)
    expect(dueUntilEndOf({ verification: null }, 2030)).toBe(false)
  })

  it('names the medium (translation, else class label)', () => {
    expect(mediumLabel(meter(1), t, te)).toBe('Strom')
    expect(mediumLabel(meter(1, { medium: 'UNKNOWN_X', classLabel: 'Sonderzähler' }), t, te)).toBe('Sonderzähler')
    expect(mediumLabel(meter(1, { medium: null, classLabel: null }), t, te)).toBe('Electricity Measurement Instrument')
  })

  it('builds history lines', () => {
    const history = {
      unit: 'kWh',
      samples: [
        { timestamp: '2026-11-01T00:00:00+01:00', value: 120.25, note: 'Meter Tausch' },
        { timestamp: '2026-10-01T00:00:00+02:00', value: 100, note: null },
      ],
    }
    const meterLines = historyLines(history, { meter: true, decimals: 1 }, 'de')
    expect(meterLines.map((l) => [l.time, l.value, l.exchange])).toEqual([
      ['01.11.2026, 00:00', '120,3 kWh', true],
      ['01.10.2026, 00:00', '100,0 kWh', false],
    ])
    const baseLines = historyLines(history, { meter: false, period: 'P1M' }, 'de')
    expect(baseLines.map((l) => [l.time, l.exchange])).toEqual([
      ['November 2026', false],
      ['Oktober 2026', false],
    ])
    expect(historyLines(null, { meter: true }, 'de')).toEqual([])
  })
})

describe('register columns', () => {
  const group = { columns: [column('Serial Number', { name: 'Seriennummer' }), column('Power', { name: 'Leistung', type: 'NUMBER', unit: 'kW', visible: false })] }

  it('puts the fixed columns of the kind before the attribute columns', () => {
    expect(registerColumns('meters', group, env()).map((c) => c.id)).toEqual(['name', 'medium', 'last', 'lastTime', 'verification', 'path', 'unit', 'a:Serial Number', 'a:Power'])
    expect(registerColumns('basedata', null, env()).map((c) => c.id)).toEqual(['name', 'path', 'last', 'lastTime', 'unit'])
    expect(registerColumns('equipment', group, env()).map((c) => c.id)).toEqual(['path', 'name', 'a:Serial Number', 'a:Power'])
    const power = registerColumns('equipment', group, env()).find((c) => c.id === 'a:Power')!
    expect(power.label).toBe('Leistung [kW]')
    expect(power.default).toBe(false)
    expect(power.text(equipment(1, { values: { Power: 7.5 } }), { locale: 'de', t })).toBe('7,5')
  })

  it('formats the fixed cells', () => {
    const cols = registerColumns('meters', group, env())
    const row = meter(1, { verification: { due: '2026-11-01', overdue: true } })
    const cell = (id: string) => cols.find((c) => c.id === id)!.text(row, { locale: 'de', t })
    expect(cell('medium')).toBe('Strom')
    expect(cell('last')).toBe('1.234,5 kWh')
    expect(cell('lastTime')).toBe('10.11.2026, 23:45')
    expect(cell('verification')).toBe('01.11.2026 – überfällig')
    expect(cols.find((c) => c.id === 'verification')!.badge!(row)).toBe('overdue')
    const base = registerColumns('basedata', null, env())
    expect(base.find((c) => c.id === 'lastTime')!.text(baseData(1), { locale: 'de', t })).toBe('Oktober 2026')
  })

  it('stores the column choice per kind as changes against the defaults', () => {
    const store = memoryStore()
    const cols = registerColumns('meters', group, env())
    expect(visibleColumnIds(cols, emptyChoice())).toEqual(['name', 'medium', 'last', 'lastTime', 'verification', 'a:Serial Number'])

    let choice = toggleColumnChoice(emptyChoice(), cols.find((c) => c.id === 'medium')!, true)
    choice = toggleColumnChoice(choice, cols.find((c) => c.id === 'a:Power')!, false)
    expect(choice).toEqual({ shown: ['a:Power'], hidden: ['medium'] })
    expect(visibleColumnIds(cols, choice)).toEqual(['name', 'last', 'lastTime', 'verification', 'a:Serial Number', 'a:Power'])

    saveColumnChoice('meters', choice, store)
    expect(loadColumnChoice('meters', store)).toEqual(choice)
    expect(loadColumnChoice('equipment', store)).toEqual(emptyChoice())

    choice = toggleColumnChoice(choice, cols.find((c) => c.id === 'medium')!, false)
    choice = toggleColumnChoice(choice, cols.find((c) => c.id === 'a:Power')!, true)
    expect(choice).toEqual(emptyChoice())
    saveColumnChoice('meters', choice, store)
    expect(store.map.has(registerColumnKey('meters'))).toBe(false)

    store.setItem(registerColumnKey('meters'), '{broken')
    expect(loadColumnChoice('meters', store)).toEqual(emptyChoice())
    expect(loadColumnChoice('meters', null)).toEqual(emptyChoice())
  })

  it('falls back to the defaults if the choice hides every column of a tab', () => {
    const cols = registerColumns('equipment', { columns: [] }, env())
    expect(visibleColumnIds(cols, { shown: [], hidden: ['path', 'name'] })).toEqual(['path', 'name'])
  })
})

describe('register filters and summary', () => {
  const rows = [
    meter(1, { name: 'Main meter', values: { Type: 'Main', Location: 'Hall 1', 'Serial Number': 'A-123' }, verification: { due: '2026-12-31', overdue: false } }),
    meter(2, { name: 'Sub meter', values: { Type: 'Sub', Location: 'Hall 2' }, verification: { due: '2027-03-01', overdue: false } }),
    meter(3, { name: 'Gas meter', medium: 'GAS', classLabel: 'Gaszähler', values: { Type: 'Main' }, verification: { due: '2025-01-01', overdue: true } }),
  ]
  const cols = registerColumns('meters', { columns: [column('Serial Number'), column('Type'), column('Location')] }, env())
  const names = (list: { name: string }[]) => list.map((r) => r.name)

  it('searches all words, case-insensitive, in every column', () => {
    const filter = { ...defaultFilter('2026-11-11'), text: 'MAIN a-123' }
    expect(names(filterRows(rows, filter, cols, env(), true))).toEqual(['Main meter'])
    expect(names(filterRows(rows, { ...filter, text: 'hall' }, cols, env(), true))).toEqual(['Main meter', 'Sub meter'])
    expect(names(filterRows(rows, { ...filter, text: 'strom' }, cols, env(), true))).toEqual(['Main meter', 'Sub meter'])
  })

  it('filters meters by medium, type, location and verification due until the end of a year', () => {
    const base = defaultFilter('2026-11-11')
    expect(base.year).toBe(2026)
    expect(names(filterRows(rows, { ...base, medium: 'Gas' }, cols, env(), true))).toEqual(['Gas meter'])
    expect(names(filterRows(rows, { ...base, type: 'Main' }, cols, env(), true))).toEqual(['Main meter', 'Gas meter'])
    expect(names(filterRows(rows, { ...base, location: 'Hall 2' }, cols, env(), true))).toEqual(['Sub meter'])
    expect(names(filterRows(rows, { ...base, overdueOnly: true }, cols, env(), true))).toEqual(['Main meter', 'Gas meter'])
    expect(names(filterRows(rows, { ...base, overdueOnly: true, year: 2027 }, cols, env(), true))).toEqual(['Main meter', 'Sub meter', 'Gas meter'])
    /* other kinds ignore the meter filters */
    expect(filterRows(rows, { ...base, medium: 'Gas' }, cols, env(), false)).toHaveLength(3)
  })

  it('offers the filter choices and the key figures', () => {
    expect(meterOptions(rows, env())).toEqual({ media: ['Gas', 'Strom'], types: ['Main', 'Sub'], locations: ['Hall 1', 'Hall 2'] })
    expect(meterSummary(rows, 2026, env())).toEqual({
      total: 3,
      perMedium: [
        { name: 'Strom', count: 2 },
        { name: 'Gas', count: 1 },
      ],
      perType: [
        { name: 'Main', count: 2 },
        { name: 'Sub', count: 1 },
      ],
      overdue: 2,
    })
    expect(meterSummary([meter(9)], 2026, env()).perType).toEqual([{ name: 'Ohne Typ', count: 1 }])
    expect(yearOptions(2026)).toHaveLength(20)
    expect(yearOptions(2026)[0]).toBe(2016)
    expect(plainText({ file: 'x', image: false })).toBe('')
    expect(ALL).toBe('*')
  })
})

describe('register CSV', () => {
  it('exports the given columns with raw numbers and the separators of the locale', () => {
    const cols = registerColumns('meters', { columns: [column('Note'), column('Power', { type: 'NUMBER', unit: 'kW' })] }, env()).filter((c) =>
      ['name', 'last', 'verification', 'a:Note', 'a:Power'].includes(c.id),
    )
    const rows = [
      meter(1, { name: 'Main; meter', values: { Note: '=SUM(A1)', Power: 1234.5 }, verification: { due: '2026-12-31', overdue: false } }),
      meter(2, { last: null, values: {} }),
    ]
    const de = registerCsv(cols, rows, 'de')
    expect(de.startsWith('﻿')).toBe(true)
    expect(de.slice(1).split('\r\n')).toEqual([
      'Name;Letzter Wert;Nächste Eichung;Note;Power [kW]',
      '"Main; meter";1234,5;2026-12-31;\'=SUM(A1);1234,5',
      'Meter 2;;;;',
      '',
    ])
    const en = registerCsv(cols.slice(0, 2), rows.slice(0, 1), 'en')
    expect(en.slice(1).split('\r\n')[1]).toBe('Main; meter,1234.5')
    expect(registerCsvName('Messstellen', 'Site/A')).toBe('Messstellen - Site_A.csv')
    expect(registerCsvName('Anlagen', null)).toBe('Anlagen.csv')
  })
})
