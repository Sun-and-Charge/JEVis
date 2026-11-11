/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { Alarm, AlarmList } from '../../src/api/alarms'

/** Mocked answers of /web/alarms (neutral names, no real data). */
export function alarm(id: string, overrides: Partial<Alarm> = {}): Alarm {
  return {
    id,
    timestamp: '2026-11-10T14:15:00.000+01:00',
    logged: '2026-11-10T23:43:00.000+01:00',
    configuration: { id: 9100, name: 'Strom - Gesamt - Bereinigte Daten', checked: false, checkedBy: null, checkedAt: null },
    row: { id: 8100, name: 'Strom - Gesamt', className: 'Clean Data', path: 'Daten / Strom', readable: true },
    site: { id: 7100, name: 'Musterwerk' },
    attribute: 'Value',
    isValue: 812.5,
    operator: '>',
    shouldBeValue: 700,
    tolerance: 5,
    unit: 'kWh',
    type: 'L1',
    typeName: 'Grenzwert Stufe 1',
    logValue: 1,
    logValueName: 'Normaler Alarm',
    ...overrides,
  }
}

export function alarmList(alarms: Alarm[], overrides: Partial<AlarmList> = {}): AlarmList {
  return {
    from: '2026-11-04T00:00:00.000+01:00',
    to: '2026-11-11T23:59:59.999+01:00',
    state: 'open',
    alarms,
    truncated: false,
    ...overrides,
  }
}
