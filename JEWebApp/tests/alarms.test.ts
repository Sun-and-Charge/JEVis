/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import {
  alarmColumns,
  alarmOperator,
  alarmSites,
  alarmsCsv,
  alarmTypeOptions,
  checkAlarmRange,
  checkedTitle,
  defaultAlarmFilter,
  defaultAlarmRange,
  filterAlarms,
  formatAlarmNumber,
  valueWithUnit,
  type AlarmCsvHeaders,
  type AlarmFilter,
} from '../src/alarms/alarms'
import { localTimestamp } from '../src/analysis/format'
import { alarmsPath } from '../src/api/alarms'
import { sortRows } from '../src/iso/columns'
import { DAY_MS, noteChartOption } from '../src/notes/chart'
import { checkNoteRange, notesCsvName } from '../src/notes/notes'
import { alarm } from './fixtures/alarms'

const filter = (overrides: Partial<AlarmFilter> = {}): AlarmFilter => ({ ...defaultAlarmFilter(), ...overrides })
const ids = (list: { id: string }[]) => list.map((a) => a.id)
const stateName = (checked: boolean) => (checked ? 'Quittiert' : 'Offen')
const env = { noAccess: 'Kein Leserecht', formatTime: (ms: number) => String(ms), locale: 'de', stateName }

describe('alarm filters', () => {
  const alarms = [
    alarm('a'),
    alarm('b', { type: 'GAP', typeName: 'Datenlücke', configuration: { id: 9101, name: 'Gas - Lücken', checked: true, checkedBy: 'admin', checkedAt: '2026-11-10T23:50:00.000+01:00' } }),
    alarm('c', { type: 'L1', row: { id: 8102, readable: false }, site: { id: 7101, name: 'Zweigwerk' } }),
    alarm('d', { type: 'CUSTOM', typeName: 'Eigen', site: null }),
  ]

  it('offers the present types in the JEAlarm order with counts, unknown ones last, selected ones kept', () => {
    expect(alarmTypeOptions(alarms)).toEqual([
      { id: 'L1', name: 'Grenzwert Stufe 1', count: 2 },
      { id: 'GAP', name: 'Datenlücke', count: 1 },
      { id: 'CUSTOM', name: 'Eigen', count: 1 },
    ])
    expect(alarmTypeOptions(alarms.slice(0, 1), ['D1']).map((o) => [o.id, o.count])).toEqual([
      ['L1', 1],
      ['D1', 0],
    ])
  })

  it('filters by type and site', () => {
    expect(ids(filterAlarms(alarms, filter(), 'de'))).toEqual(['a', 'b', 'c', 'd'])
    expect(ids(filterAlarms(alarms, filter({ types: ['GAP', 'CUSTOM'] }), 'de'))).toEqual(['b', 'd'])
    expect(ids(filterAlarms(alarms, filter({ site: 7101 }), 'de'))).toEqual(['c'])
    expect(ids(filterAlarms(alarms, filter({ site: 'none' }), 'de'))).toEqual(['d'])
  })

  it('searches configuration, data point name and path and type name; not in unreadable rows', () => {
    expect(ids(filterAlarms(alarms, filter({ text: 'lücken' }), 'de'))).toEqual(['b'])
    expect(ids(filterAlarms(alarms, filter({ text: 'daten / strom' }), 'de'))).toEqual(['a', 'b', 'd'])
    expect(ids(filterAlarms(alarms, filter({ text: 'datenlücke' }), 'de'))).toEqual(['b'])
    expect(ids(filterAlarms(alarms, filter({ text: 'gas gesamt' }), 'de'))).toEqual(['b'])
    expect(ids(filterAlarms(alarms, filter({ text: 'nichts' }), 'de'))).toEqual([])
  })

  it('lists the sites of the alarms', () => {
    expect(alarmSites(alarms, 'de')).toEqual([
      { id: 7100, name: 'Musterwerk' },
      { id: 7101, name: 'Zweigwerk' },
    ])
  })
})

describe('alarm values', () => {
  it('normalises operators like the Control Center', () => {
    expect(alarmOperator('>')).toBe('>')
    expect(alarmOperator('>=')).toBe('≥')
    expect(alarmOperator('')).toBe('≥')
    expect(alarmOperator('!=')).toBe('≠')
    expect(alarmOperator('<=')).toBe('≤')
    expect(alarmOperator('≤')).toBe('≤')
    expect(alarmOperator('between')).toBe('between')
    expect(alarmOperator(null)).toBe('')
  })

  it('formats numbers in the UI locale', () => {
    expect(formatAlarmNumber(1234.5, 'de')).toBe('1.234,5')
    expect(formatAlarmNumber(1234.5, 'en')).toBe('1,234.5')
    expect(formatAlarmNumber(0.0042, 'de')).toBe('0,0042')
    expect(formatAlarmNumber(2.123456, 'en')).toBe('2.123')
    expect(formatAlarmNumber(null, 'de')).toBe('')
    expect(formatAlarmNumber(Number.NaN, 'de')).toBe('')
    expect(valueWithUnit(812.5, 'kWh', 'de')).toBe('812,5 kWh')
    expect(valueWithUnit(812.5, '', 'de')).toBe('812,5')
    expect(valueWithUnit(null, 'kWh', 'de')).toBe('')
  })

  it('names who confirmed and when', () => {
    const c = { id: 1, name: 'X', checked: true, checkedBy: 'admin', checkedAt: '2026-11-10T23:50:00.000+01:00' }
    expect(checkedTitle({ configuration: c }, () => 'T')).toBe('admin · T')
    expect(checkedTitle({ configuration: { ...c, checkedBy: null } }, () => 'T')).toBe('T')
    expect(checkedTitle({ configuration: { ...c, checked: false } }, () => 'T')).toBe('')
  })
})

describe('alarm columns and CSV', () => {
  const columns = alarmColumns(env)
  const ctx = { locale: 'de', t: (k: string) => k }
  const col = (id: string) => columns.find((c) => c.id === id)

  it('sorts by time (newest first by default) and values numerically, empty values last', () => {
    const list = [
      alarm('old', { timestamp: '2026-11-05T10:00:00.000+01:00', isValue: 9 }),
      alarm('new', { timestamp: '2026-11-10T10:00:00.000+01:00', isValue: 100 }),
      alarm('none', { timestamp: '2026-11-07T10:00:00.000+01:00', isValue: null }),
    ]
    expect(ids(sortRows(list, col('date'), 'desc', ctx))).toEqual(['new', 'none', 'old'])
    expect(ids(sortRows(list, col('isValue'), 'asc', ctx))).toEqual(['old', 'new', 'none'])
    expect(ids(sortRows(list, col('isValue'), 'desc', ctx))).toEqual(['new', 'old', 'none'])
    expect(col('isValue')!.text(list[1], ctx)).toBe('100')
    expect(col('status')!.text(list[0], ctx)).toBe('Offen')
    expect(col('row')!.text(alarm('x', { row: { id: 1, readable: false } }), ctx)).toBe('Kein Leserecht')
  })

  const headers: AlarmCsvHeaders = {
    date: 'Datum',
    site: 'Standort',
    configuration: 'Konfiguration',
    path: 'Pfad',
    row: 'Datenpunkt',
    isValue: 'Ist-Wert',
    operator: 'Operator',
    shouldBeValue: 'Soll-Wert',
    tolerance: 'Toleranz',
    unit: 'Einheit',
    type: 'Art',
    alarm: 'Alarm',
    status: 'Status',
    checkedBy: 'Quittiert von',
    checkedAt: 'Quittiert am',
  }

  it('writes a CSV with BOM, the separators of the locale, plain numbers and escaped texts', () => {
    const checkedAt = '2026-11-10T23:50:00.000+01:00'
    const list = [
      alarm('a', { isValue: 1234.5, configuration: { id: 1, name: '=Formel; mit Trenner', checked: true, checkedBy: 'admin', checkedAt } }),
      alarm('b', { row: { id: 2, readable: false }, isValue: null, operator: '>=', site: null }),
    ]
    const csv = alarmsCsv(list, headers, env, 'de')
    expect(csv.startsWith('﻿')).toBe(true)
    expect(csv.endsWith('\r\n')).toBe(true)
    const lines = csv.slice(1).split('\r\n')
    expect(lines[0]).toBe('Datum;Standort;Konfiguration;Pfad;Datenpunkt;Ist-Wert;Operator;Soll-Wert;Toleranz;Einheit;Art;Alarm;Status;Quittiert von;Quittiert am')
    expect(lines[1]).toBe(
      `${localTimestamp(Date.parse('2026-11-10T14:15:00.000+01:00'))};Musterwerk;"'=Formel; mit Trenner";Daten / Strom;Strom - Gesamt;1234,5;>;700;5;kWh;Grenzwert Stufe 1;Normaler Alarm;Quittiert;admin;${localTimestamp(Date.parse(checkedAt))}`,
    )
    expect(lines[2].split(';').slice(1, 7)).toEqual(['', 'Strom - Gesamt - Bereinigte Daten', '', 'Kein Leserecht', '', '≥'])
    expect(lines[2].endsWith(';Offen;;')).toBe(true)
    const en = alarmsCsv(list.slice(0, 1), headers, env, 'en').slice(1).split('\r\n')
    expect(en[0].startsWith('Datum,Standort,')).toBe(true)
    expect(en[1]).toContain(',1234.5,>,700,5,kWh,')
  })

  it('names the CSV after the time frame', () => {
    expect(notesCsvName('Alarme', '2026-11-04', '2026-11-11')).toBe('Alarme 2026-11-04 - 2026-11-11.csv')
  })
})

describe('time frame of the alarms', () => {
  it('defaults to the last 7 days up to today', () => {
    expect(defaultAlarmRange(new Date(2026, 10, 11, 15, 30))).toEqual({ from: '2026-11-04', to: '2026-11-11' })
    expect(defaultAlarmRange(new Date(2026, 2, 3))).toEqual({ from: '2026-02-24', to: '2026-03-03' })
  })

  it('allows at most one year (notes keep three)', () => {
    expect(checkAlarmRange('2025-11-11', '2026-11-11')).toBe('ok')
    expect(checkAlarmRange('2025-11-10', '2026-11-11')).toBe('tooLong')
    expect(checkAlarmRange('2026-11-12', '2026-11-11')).toBe('order')
    expect(checkAlarmRange('2026-11-04', '')).toBe('missing')
    expect(checkNoteRange('2024-11-10', '2026-11-11')).toBe('ok')
  })

  it('builds the request path with state, time zone and language', () => {
    expect(alarmsPath({ from: '2026-11-04', to: '2026-11-11', state: 'checked', timeZone: 'Europe/Berlin', lang: 'de' })).toBe(
      '/web/alarms?from=2026-11-04&to=2026-11-11&state=checked&timeZone=Europe%2FBerlin&lang=de',
    )
  })
})

describe('detail chart of an alarm', () => {
  const ts = Date.UTC(2026, 10, 10, 13, 15)
  const samples: [number, number][] = [
    [ts - 3600_000, 650],
    [ts + 3600_000, 812.5],
  ]
  type Opt = {
    xAxis: { min: number; max: number }
    yAxis: { min?: (e: { min: number; max: number }) => number; max?: (e: { min: number; max: number }) => number }
    series: { markLine: { data: { xAxis?: number; yAxis?: number; label?: { formatter: string } }[] } }[]
  }

  it('marks the alarm time and draws the limit as dashed line, widening the axis to show it', () => {
    const option = noteChartOption(samples, ts, 'Strom - Gesamt', 'kWh', { locale: 'de', noteLabel: 'Alarm', limit: { value: 900, label: 'Soll-Wert' } }) as unknown as Opt
    expect(option.xAxis.max - option.xAxis.min).toBe(2 * DAY_MS)
    const data = option.series[0].markLine.data
    expect(data[0]).toEqual({ xAxis: ts })
    expect(data[1].yAxis).toBe(900)
    expect(data[1].label?.formatter).toBe('Soll-Wert')
    expect(option.yAxis.max!({ min: 650, max: 812.5 })).toBe(900)
    expect(option.yAxis.min!({ min: 650, max: 812.5 })).toBe(650)
  })

  it('has no limit line without a limit', () => {
    const option = noteChartOption(samples, ts, 'Strom - Gesamt', 'kWh', { locale: 'de', noteLabel: 'Alarm', limit: null }) as unknown as Opt
    expect(option.series[0].markLine.data).toEqual([{ xAxis: ts }])
    expect(option.yAxis.min).toBeUndefined()
  })
})
