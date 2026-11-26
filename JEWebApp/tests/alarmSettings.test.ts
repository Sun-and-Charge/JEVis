/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import {
  checkConfig,
  checkRow,
  cloneConfigForm,
  cloneRowForm,
  configChanges,
  configForm,
  conflictConfigOf,
  conflictRowSettingsOf,
  defaultSteps,
  filterAlarmConfigs,
  invalidMails,
  mergeConfigForm,
  mergeRowForm,
  newRule,
  numberText,
  periodOptions,
  recipientCount,
  rowChanges,
  rowForm,
  scheduleBody,
  scheduleForm,
  scheduleProblem,
  splitMails,
  splitPeople,
} from '../src/alarms/settings'
import { deleteAlarmConfigurationPath, rowAlarmSettingsPath } from '../src/api/alarmSettings'
import { alarmConfig, rowSettings, settingsList } from './fixtures/alarmSettings'
import { httpError } from './fixtures/edit'

/* Alarm settings (E7): pure logic of the configuration and row editors. */

const people = settingsList([]).people!
const known = { users: [{ id: 41, name: 'Max Mustermann', kind: 'USER' }], groups: [{ id: 42, name: 'Energieteam', kind: 'GROUP' }] }

describe('mail addresses', () => {
  it('splits by comma, semicolon, blanks and lines and finds invalid ones', () => {
    expect(splitMails(' a@example.org, b@example.org;c@example.org\nd@example.org ')).toEqual(['a@example.org', 'b@example.org', 'c@example.org', 'd@example.org'])
    expect(invalidMails('a@example.org, kaputt, x@y')).toEqual(['kaputt', 'x@y'])
    expect(invalidMails('')).toEqual([])
  })
})

describe('configuration list', () => {
  it('counts recipients, offers periods and filters', () => {
    const c = alarmConfig(9100)
    expect(recipientCount(c)).toBe(3)
    expect(recipientCount({ notification: null })).toBe(0)
    expect(periodOptions('DAILY')).not.toContain('CUSTOM')
    expect(periodOptions('CUSTOM')).toContain('CUSTOM')
    const list = [c, alarmConfig(9200, { name: 'Gas', site: { id: 7101, name: 'Zweigwerk' } })]
    expect(filterAlarmConfigs(list, 'zweig', 'all', 'de').map((x) => x.id)).toEqual([9200])
    expect(filterAlarmConfigs(list, '', 7100, 'de').map((x) => x.id)).toEqual([9100])
  })

  it('paths', () => {
    expect(deleteAlarmConfigurationPath(9100, '2026-11-01T10:00:00.000+01:00')).toBe('/web/alarms/settings/9100?version=2026-11-01T10%3A00%3A00.000%2B01%3A00')
    expect(rowAlarmSettingsPath(8100, 'de')).toBe('/web/rows/8100/alarm-settings?lang=de')
  })
})

describe('configuration form', () => {
  it('reads a configuration and a new one', () => {
    const f = configForm(alarmConfig(9100))
    expect(f.notification.people).toEqual([41, 42])
    expect(f.notification.recipients).toBe('alarm@example.org')
    const n = configForm(null)
    expect(n).toMatchObject({ enabled: true, scope: 'COMPLETE', period: 'DAILY', objects: [] })
  })

  it('PUT sends only changed fields, the notification as a whole', () => {
    const initial = configForm(alarmConfig(9100))
    const form = cloneConfigForm(initial)
    form.period = 'HOURLY'
    form.notification.people = [41, 42, 43]
    const changes = configChanges(initial, form)
    expect([...changes].sort()).toEqual(['notification', 'period'])
    const r = checkConfig(form, { notificationEditable: true, people, known, changes })
    expect(r.problems).toEqual({})
    expect(r.body).toEqual({
      period: 'HOURLY',
      notification: {
        enabled: true,
        recipientUsers: [41, 43],
        recipientGroups: [42],
        recipients: ['alarm@example.org'],
        cc: [],
        bcc: [],
        subject: 'Alarm',
        message: 'Neue Alarme',
        html: false,
      },
    })
  })

  it('checks objects, mails, recipients and lengths', () => {
    const form = configForm(alarmConfig(9100))
    form.objects = []
    form.notification.recipients = 'kaputt'
    form.notification.cc = 'x@'
    form.notification.subject = 'x'.repeat(201)
    const r = checkConfig(form, { notificationEditable: true, people, known, changes: null })
    expect(r.body).toBeNull()
    expect(Object.keys(r.problems).sort()).toEqual(['cc', 'objects', 'recipients', 'subject'])
    expect(r.problems.recipients.params).toEqual({ list: 'kaputt' })

    const empty = configForm(alarmConfig(9100))
    empty.notification.people = []
    empty.notification.recipients = ''
    expect(checkConfig(empty, { notificationEditable: true, people, known, changes: null }).problems.recipients.key).toBe('alarmSettings.problem.noRecipient')
  })

  it('a new configuration without notification content sends none; several notifications are never sent', () => {
    const f = configForm(null)
    f.name = 'Neu'
    expect(checkConfig(f, { notificationEditable: true, people, known, changes: null }).body).toEqual({
      name: 'Neu',
      enabled: true,
      scope: 'COMPLETE',
      objects: [],
      period: 'DAILY',
      bindToData: false,
      includeLimits: false,
      disableLink: false,
    })
    const g = configForm(alarmConfig(9100, { multipleNotifications: true }))
    g.notification.recipients = 'kaputt'
    g.name = 'Anders'
    const r = checkConfig(g, { notificationEditable: false, people, known, changes: configChanges(configForm(alarmConfig(9100)), g) })
    expect(r.body).toEqual({ name: 'Anders' })
  })

  it('splits people by kind (options, then the lists as loaded)', () => {
    expect(splitPeople([41, 42, 99], [], { users: [], groups: [{ id: 99, name: 'X', kind: 'GROUP' }] })).toEqual({ users: [41, 42], groups: [99] })
    expect(splitPeople([42], people, known)).toEqual({ users: [], groups: [42] })
  })

  it('merges own changes onto a newer version', () => {
    const initial = configForm(alarmConfig(9100))
    const own = cloneConfigForm(initial)
    own.name = 'Eigener Name'
    const server = configForm(alarmConfig(9100, { period: 'WEEKLY', name: 'Fremd' }))
    const merged = mergeConfigForm(initial, own, server)
    expect(merged.name).toBe('Eigener Name')
    expect(merged.period).toBe('WEEKLY')
  })

  it('reads the configuration of a 409', () => {
    expect(conflictConfigOf(httpError(409, alarmConfig(9100, { version: 'x' })), 9100)?.version).toBe('x')
    expect(conflictConfigOf(httpError(409, alarmConfig(9200)), 9100)).toBeNull()
    expect(conflictConfigOf(httpError(400, 'x'), 9100)).toBeNull()
  })
})

describe('row alarm settings form', () => {
  it('reads numbers in the locale, keeps unknown step keys and schedules', () => {
    const f = rowForm(rowSettings(), 'de')
    expect(f.steps[1].max).toBe('1000,5')
    expect(f.steps[0].extra).toEqual({ durationOverUnderRun: '2' })
    expect(f.rules[0].limit).toBe('700')
    expect(f.rules[0].silent).toMatchObject({ mode: 'simple', days: ['SAT', 'SUN'], from: '22:00', to: '06:00' })
    expect(f.rules[0].standby).toMatchObject({ mode: 'fixed', text: '0 0 12 ? * MON-FRI' })
    expect(numberText(1.5, 'en')).toBe('1.5')
    expect(numberText(null, 'de')).toBe('')
  })

  it('sends only the changed section with numbers and the schedules', () => {
    const initial = rowForm(rowSettings(), 'de')
    const form = cloneRowForm(initial)
    form.steps[0].max = '450,5'
    const c = rowChanges(initial, form)
    expect(c).toEqual({ limits: true, alarms: false })
    const r = checkRow(form, c, 'de')
    expect(r.problems).toEqual({})
    expect(r.body?.alarms).toBeUndefined()
    expect(r.body?.limits?.steps[0]).toEqual({ durationOverUnderRun: '2', name: 'Stufe 1', min: 0, max: 450.5, substitute: 'NONE', defaultMin: null, defaultMax: null })

    const g = cloneRowForm(initial)
    g.rules[0].tolerance = ''
    const rr = checkRow(g, rowChanges(initial, g), 'de')
    expect(rr.body?.alarms?.rules[0]).toEqual({
      id: '1',
      kind: 'STATIC',
      limit: 700,
      limitData: null,
      operator: 'BIGGER',
      tolerance: 0,
      silent: { simple: true, days: ['SAT', 'SUN'], from: '22:00', to: '06:00' },
      standby: { simple: false, text: '0 0 12 ? * MON-FRI' },
      /* sent as null so that a removed validity time / mean is deleted on the server */
      valid: null,
      average: null,
    })
  })

  it('checks min < max, defaults for DEFAULT_VALUE, rules and schedules', () => {
    const form = rowForm(rowSettings(), 'de')
    form.steps[0].min = '600'
    form.steps[1].substitute = 'DEFAULT_VALUE'
    form.steps[1].defaultMin = 'abc'
    const dyn = newRule()
    dyn.kind = 'DYNAMIC'
    dyn.tolerance = '-1'
    dyn.silent = { mode: 'simple', days: [], from: '22:00', to: '06:00', text: '' }
    form.rules.push(dyn)
    const r = checkRow(form, { limits: true, alarms: true }, 'de')
    expect(r.body).toBeNull()
    expect(r.problems['step0.max'].key).toBe('alarmSettings.problem.minMax')
    expect(r.problems['step1.defaultMin'].key).toBe('alarmSettings.problem.number')
    expect(r.problems['step1.defaultMax'].key).toBe('alarmSettings.problem.default')
    expect(r.problems[dyn.uid + '.limitData'].key).toBe('alarmSettings.problem.limitData')
    expect(r.problems[dyn.uid + '.tolerance'].key).toBe('alarmSettings.problem.tolerance')
    expect(r.problems[dyn.uid + '.silent'].key).toBe('alarmSettings.problem.days')
  })

  it('more than 10 rules and enabled limits without steps are refused', () => {
    const form = rowForm(rowSettings({ limits: { enabled: true, steps: [] } }), 'de')
    form.rules = Array.from({ length: 11 }, () => ({ ...newRule(), limit: '1' }))
    const r = checkRow(form, { limits: true, alarms: true }, 'de')
    expect(r.problems.steps.key).toBe('alarmSettings.problem.steps')
    expect(r.problems.rules.params).toEqual({ max: 10 })
  })

  it('schedules: problems and bodies', () => {
    expect(scheduleProblem({ mode: 'simple', days: ['MON'], from: '22:00', to: '22:00', text: '' })?.key).toBe('alarmSettings.problem.sameTime')
    expect(scheduleProblem({ mode: 'simple', days: ['MON'], from: '', to: '06:00', text: '' })?.key).toBe('alarmSettings.problem.time')
    expect(scheduleProblem(scheduleForm({ simple: false, text: 'x' }))).toBeNull()
    expect(scheduleBody({ mode: 'simple', days: ['SUN', 'MON'], from: '22:00', to: '06:00', text: '' })).toEqual({ simple: true, days: ['MON', 'SUN'], from: '22:00', to: '06:00' })
    expect(scheduleBody(scheduleForm(null))).toBeNull()
  })

  it('default steps and the 409 merge per section', () => {
    expect(defaultSteps(['Stufe 1', 'Stufe 2']).map((s) => s.name)).toEqual(['Stufe 1', 'Stufe 2'])
    const initial = rowForm(rowSettings(), 'de')
    const own = cloneRowForm(initial)
    own.alarmsEnabled = false
    const server = rowForm(rowSettings({ limits: { enabled: false, steps: [] } }), 'de')
    const merged = mergeRowForm(initial, own, server)
    expect(merged.alarmsEnabled).toBe(false)
    expect(merged.limitsEnabled).toBe(false)
    expect(merged.steps).toEqual([])
    expect(conflictRowSettingsOf(httpError(409, rowSettings({ version: 'v9' })))?.version).toBe('v9')
  })
})
