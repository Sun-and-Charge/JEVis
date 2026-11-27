/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import {
  checkConfig,
  checkRow,
  configChanges,
  configForm,
  newOutageRule,
  newRule,
  noDataMinutes,
  normalizeTime,
  rowChanges,
  rowForm,
  ruleForm,
} from '../src/alarms/settings'
import type { AlarmOutage } from '../src/api/alarms'
import AlarmOutages from '../src/components/alarms/AlarmOutages.vue'
import { i18n } from '../src/i18n'
import { alarmConfig, rowSettings, settingsList } from './fixtures/alarmSettings'

/* Data outage rules, validity time, moving mean, daily summary and the outage list (docs/ALARMS-OUTAGE.md). */

const people = settingsList([]).people!
const known = { users: [], groups: [] }

describe('outage rule and new rule keys', () => {
  it('reads minutes as hours when whole and back', () => {
    const r = ruleForm({ id: '2', kind: 'NO_DATA', limit: null, limitData: null, operator: '', tolerance: 0, silent: null, standby: null, noData: 180 }, 'de')
    expect(r.kind).toBe('NO_DATA')
    expect([r.noData, r.noDataUnit]).toEqual(['3', 'HOURS'])
    expect(noDataMinutes(r, 'de')).toBe(180)
    const m = ruleForm({ id: '3', kind: 'NO_DATA', limit: null, limitData: null, operator: '', tolerance: 0, silent: null, standby: null, noData: 45 }, 'de')
    expect([m.noData, m.noDataUnit]).toEqual(['45', 'MINUTES'])
    expect(noDataMinutes({ noData: '1,5', noDataUnit: 'HOURS' }, 'de')).toBe(90)
    expect(noDataMinutes({ noData: '0,5', noDataUnit: 'MINUTES' }, 'de')).toBeNaN()
    expect(noDataMinutes({ noData: '', noDataUnit: 'HOURS' }, 'de')).toBeNull()
  })

  it('sends an outage rule without operator and limits, others with valid time and mean', () => {
    const initial = rowForm(rowSettings(), 'de')
    const form = rowForm(rowSettings(), 'de')
    const outage = newOutageRule()
    form.rules.push(outage)
    const day = newRule()
    day.limit = '40'
    day.average = '60'
    day.valid = { mode: 'simple', days: ['MON', 'TUE', 'WED', 'THU', 'FRI'], from: '06:00', to: '20:00', text: '' }
    form.rules.push(day)
    const r = checkRow(form, rowChanges(initial, form), 'de')
    expect(r.problems).toEqual({})
    const rules = r.body!.alarms!.rules
    const sentOutage = rules[rules.length - 2]
    expect(sentOutage).toMatchObject({ kind: 'NO_DATA', noData: 120, limit: null, limitData: null })
    expect('operator' in sentOutage).toBe(false)
    expect(rules[rules.length - 1]).toMatchObject({ kind: 'STATIC', limit: 40, average: 60, valid: { simple: true, from: '06:00', to: '20:00' } })
  })

  it('reports invalid minutes and means', () => {
    const initial = rowForm(rowSettings(), 'de')
    const form = rowForm(rowSettings(), 'de')
    const outage = newOutageRule()
    outage.noData = ''
    const day = newRule()
    day.limit = '40'
    day.average = '0'
    form.rules.push(outage, day)
    const r = checkRow(form, rowChanges(initial, form), 'de')
    expect(r.body).toBeNull()
    expect(r.problems[`${outage.uid}.noData`].key).toBe('alarmSettings.problem.noData')
    expect(r.problems[`${day.uid}.average`].key).toBe('alarmSettings.problem.average')
  })
})

describe('daily summary of a configuration', () => {
  it('is sent for a new configuration only when switched on, and when changed', () => {
    const f = configForm(null)
    f.name = 'Neu'
    expect(checkConfig(f, { notificationEditable: true, people, known, changes: null }).body).not.toHaveProperty('dailySummary')
    f.dailySummary = true
    f.dailySummaryTime = '7:30'
    expect(checkConfig(f, { notificationEditable: true, people, known, changes: null }).body).toMatchObject({ dailySummary: true, dailySummaryTime: '07:30' })

    const stored = configForm(alarmConfig(50, { dailySummary: false, dailySummaryTime: null }))
    const edited = configForm(alarmConfig(50, { dailySummary: false, dailySummaryTime: null }))
    edited.dailySummary = true
    edited.dailySummaryTime = '25:00'
    const changes = configChanges(stored, edited)
    expect(changes.has('dailySummary')).toBe(true)
    expect(checkConfig(edited, { notificationEditable: true, people, known, changes }).problems.dailySummaryTime.key).toBe('alarmSettings.problem.summaryTime')
    expect(normalizeTime(' 6:05 ')).toBe('06:05')
    expect(normalizeTime('24:00')).toBeNull()
  })
})

describe('outage list', () => {
  const outage = (over: Partial<AlarmOutage>): AlarmOutage => ({
    id: 'x',
    kind: 'ROW',
    open: true,
    configuration: { id: 50, name: 'Werk Alarme' },
    object: { id: 41, name: 'Bereinigte Daten', path: 'Daten / Strom - Halle 3', readable: true },
    site: { id: 2, name: 'Musterwerk' },
    since: '2026-11-26T09:00:00.000+01:00',
    minutes: 120,
    cause: null,
    opened: '2026-11-26T11:05:00.000+01:00',
    resumed: null,
    backfilled: null,
    ended: null,
    ...over,
  })

  it('shows open outages first and the result of ended ones', () => {
    i18n.global.locale.value = 'de'
    const w = mount(AlarmOutages, {
      props: {
        outages: [
          outage({ id: 'a', open: false, resumed: '2026-11-25T13:00:00.000+01:00', backfilled: true, ended: '2026-11-25T13:05:00.000+01:00' }),
          outage({ id: 'b', kind: 'SOURCE', object: { id: 30, name: 'Gateway Halle 3', readable: true }, minutes: 1440 }),
          outage({ id: 'c', open: false, resumed: '2026-11-24T08:00:00.000+01:00', backfilled: false, ended: '2026-11-24T08:05:00.000+01:00', minutes: 45 }),
        ],
      },
      global: { plugins: [i18n] },
    })
    const items = w.findAll('.alm-outage')
    expect(items.map((x) => x.classes()).map((c) => c.includes('is-open'))).toEqual([true, false, false])
    expect(items[0].text()).toContain('Übertragungsstörung')
    expect(items[0].text()).toContain('1 Tag')
    expect(items[1].text()).toContain('Daten / Strom - Halle 3 / Bereinigte Daten')
    expect(items[1].text()).toContain('verspätet geliefert')
    expect(items[1].text()).toContain('2 Stunden')
    expect(items[2].text()).toContain('Lücke bleibt')
    expect(items[2].text()).toContain('45 Minuten')
  })

  it('lists an outage seen by two configurations once', () => {
    i18n.global.locale.value = 'de'
    const w = mount(AlarmOutages, {
      props: {
        outages: [
          outage({ id: 'a' }),
          outage({ id: 'b', configuration: { id: 51, name: 'Halle 3 Alarme' } }),
          outage({ id: 'c', object: { id: 42, name: 'Bereinigte Daten', path: 'Daten / Gas', readable: true } }),
        ],
      },
      global: { plugins: [i18n] },
    })
    const items = w.findAll('.alm-outage')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('Werk Alarme, Halle 3 Alarme')
    expect(items[1].text()).not.toContain('Halle 3 Alarme')
  })
})
