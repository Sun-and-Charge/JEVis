/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { RegisterColumn, RegisterGroup, RegisterList, RegisterRow } from '../../src/api/registers'

/* Neutral placeholder data (no customer data). */

export function column(key: string, overrides: Partial<RegisterColumn> = {}): RegisterColumn {
  return { key, name: key, type: 'TEXT', unit: null, position: 0, visible: true, ...overrides }
}

export function meter(id: number, overrides: Partial<RegisterRow> = {}): RegisterRow {
  return {
    id,
    name: `Meter ${id}`,
    className: 'Electricity Measurement Instrument',
    classLabel: 'Stromzähler',
    path: 'Site A / Meters',
    medium: 'ELECTRICITY',
    values: {},
    target: { id: 9000 + id, attribute: 'Value', name: `Data ${id}`, readable: true },
    last: { value: 1234.5, unit: 'kWh', timestamp: '2026-11-10T23:45:00.000+01:00', decimals: 1 },
    verification: null,
    ...overrides,
  }
}

export function baseData(id: number, overrides: Partial<RegisterRow> = {}): RegisterRow {
  return {
    id,
    name: `Base value ${id}`,
    className: 'Base Data',
    classLabel: 'Stammdaten',
    path: 'Site A / Base data',
    values: {},
    last: { value: 42, unit: 'm²', timestamp: '2026-10-01T00:00:00.000+02:00', period: 'P1M' },
    ...overrides,
  }
}

export function equipment(id: number, overrides: Partial<RegisterRow> = {}): RegisterRow {
  return {
    id,
    name: `Pump ${id}`,
    className: 'Pump',
    classLabel: 'Pumpe',
    path: 'Site A / Equipment',
    values: {},
    ...overrides,
  }
}

export function group(key: string, rows: RegisterRow[], overrides: Partial<RegisterGroup> = {}): RegisterGroup {
  return { key, name: `Group ${key}`, site: null, columns: [], rows, ...overrides }
}

export function registerList(kind: RegisterList['kind'], groups: RegisterGroup[], today = '2026-11-11'): RegisterList {
  return { kind, today, groups }
}
