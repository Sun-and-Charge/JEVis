/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { AnalysisConfig, AnalysisDirectory, AnalysisRowConfig } from '../../src/api/analysis'
import { chart, config, row } from './analysis'

/* Mocked answers of the E3 endpoints (neutral names "Musterwerk", no real data). */

export const VERSION = '2026-11-10T09:00:00.000+01:00'

/** A readable row with the E3 keys. */
export function editRow(key: string, objectId: number, overrides: Partial<AnalysisRowConfig> = {}): AnalysisRowConfig {
  return row(key, {
    objectId,
    rowClass: 'Clean Data',
    unitPrefix: 'NONE',
    unitFormula: 'kWh',
    unitLabel: 'kWh',
    calculationId: null,
    interval: { enabled: false, start: null, end: null },
    css: '',
    bubbleType: 'NONE',
    ...overrides,
  })
}

/** A row the user may not read: only the key and what the server sends back. */
export function lockedRow(key: string, objectId: number): AnalysisRowConfig {
  return {
    key,
    objectId,
    attribute: 'Value',
    name: null,
    color: '#b3261e',
    axis: 1,
    chartType: 'DEFAULT',
    unit: null,
    decimalDigits: 3,
    calculation: false,
    bubbleType: 'NONE',
    readable: false,
  }
}

/** The analysis of the edit tests: a line chart (one readable, one locked row) and a table. */
export function editConfig(overrides: Partial<AnalysisConfig> = {}): AnalysisConfig {
  return {
    ...config([
      chart(0, 'LINE', [editRow('0-2001-Value', 2001, { name: 'Musterwerk Strom' }), lockedRow('0-2002-Value', 2002)], {
        fractionDigitsAuto: null,
        filterEnabled: false,
        coloringEnabled: false,
        showColumnSums: false,
        showRowSums: false,
        dayStart: null,
        dayEnd: null,
        filterTexts: {},
      }),
      chart(1, 'TABLE_V', [editRow('1-2001-Value', 2001, { name: 'Musterwerk Strom' })], { filterTexts: { 'Musterwerk Strom': '12' } }),
    ]),
    version: VERSION,
    canWrite: true,
    canDelete: true,
    directoryId: 50,
    forcedInterval: '',
    dayFilter: { weekdays: '6,7', holidays: true, dates: '2026-12-24' },
    limitLines: [{ value: 100, label: 'Grenze', color: '#ff0000', axis: 0, fill: 'NONE' }],
    ...overrides,
  }
}

export const directories: AnalysisDirectory[] = [
  { id: 50, name: 'Analysen', path: 'Musterwerk / Analysen', canCreate: true },
  { id: 51, name: 'Archiv', path: 'Musterwerk / Archiv', canCreate: false },
]
