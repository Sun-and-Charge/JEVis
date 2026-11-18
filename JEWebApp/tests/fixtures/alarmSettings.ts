/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { AlarmConfigSettings, AlarmSettingsList, RowAlarmSettings } from '../../src/api/alarmSettings'

/* Mocked answers of the alarm settings (E7); neutral placeholder names and example.org addresses only. */

export const V1 = '2026-11-01T10:00:00.000+01:00'
export const V2 = '2026-11-16T09:00:00.000+01:00'

export function alarmConfig(id: number, overrides: Partial<AlarmConfigSettings> = {}): AlarmConfigSettings {
  return {
    id,
    name: `Alarm ${id}`,
    path: 'Musterwerk / Alarme',
    site: { id: 7100, name: 'Musterwerk' },
    enabled: true,
    scope: 'SELECTED',
    objects: [{ id: 8100, attribute: 'Value', name: 'Strom - Gesamt', path: 'Daten / Strom', readable: true }],
    period: 'DAILY',
    bindToData: false,
    includeLimits: true,
    disableLink: false,
    timeStamp: '2026-11-15T00:00:00.000+01:00',
    notification: {
      id: id + 1,
      enabled: true,
      recipientUsers: [{ id: 41, name: 'Max Mustermann', kind: 'USER' }],
      recipientGroups: [{ id: 42, name: 'Energieteam', kind: 'GROUP' }],
      recipients: ['alarm@example.org'],
      cc: [],
      bcc: [],
      subject: 'Alarm',
      message: 'Neue Alarme',
      html: false,
    },
    canWrite: true,
    canDelete: true,
    version: V1,
    ...overrides,
  }
}

export function settingsList(configurations: AlarmConfigSettings[], overrides: Partial<AlarmSettingsList> = {}): AlarmSettingsList {
  return {
    configurations,
    targets: [{ id: 600, name: 'Alarme', path: 'Musterwerk', site: { id: 7100, name: 'Musterwerk' } }],
    people: [
      { id: 41, name: 'Max Mustermann', kind: 'USER', email: true },
      { id: 42, name: 'Energieteam', kind: 'GROUP', email: false },
      { id: 43, name: 'Erika Beispiel', kind: 'USER', email: false },
    ],
    ...overrides,
  }
}

export function rowSettings(overrides: Partial<RowAlarmSettings> = {}): RowAlarmSettings {
  return {
    row: { id: 8100, name: 'Strom - Gesamt', unit: 'kWh', rawId: 8099, rawName: 'Strom - Rohdaten' },
    canWrite: true,
    version: V1,
    limits: {
      enabled: true,
      steps: [
        { name: 'Stufe 1', min: 0, max: 500, substitute: 'NONE', defaultMin: null, defaultMax: null, durationOverUnderRun: '2' },
        { name: 'Stufe 2', min: -10, max: 1000.5, substitute: 'INTERPOLATION', defaultMin: null, defaultMax: null },
      ],
    },
    alarms: {
      enabled: true,
      rules: [
        {
          id: '1',
          name: null,
          kind: 'STATIC',
          limit: 700,
          limitData: null,
          operator: 'BIGGER',
          tolerance: 5,
          silent: { simple: true, days: ['SAT', 'SUN'], from: '22:00', to: '06:00' },
          standby: { simple: false, text: '0 0 12 ? * MON-FRI' },
        },
      ],
    },
    configurations: [{ id: 9100, name: 'Alarm 9100' }],
    ...overrides,
  }
}
