/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import {
  ALARM_PERIODS,
  OPERATORS,
  WEEKDAYS,
  type AlarmConfigBody,
  type AlarmConfigSettings,
  type AlarmNotificationBody,
  type AlarmObjectRef,
  type AlarmRule,
  type AlarmRuleLimitData,
  type AlarmSchedule,
  type LimitStep,
  type PersonRef,
  type RowAlarmRuleBody,
  type RowAlarmSettings,
  type RowAlarmSettingsBody,
} from '../api/alarmSettings'
import { HttpError } from '../api/http'
import type { RoundOptionPerson } from '../api/readingRounds'
import { parseUserNumber } from '../edit/edit'
import { nameProblem } from '../registers/edit'

/*
 * Editing of alarm settings (E7), like the Control Center's alarm configuration and the LimitEditor / AlarmEditor of
 * a clean data row: the forms, the checks the server does, the bodies of a save (PUT: only what changed) and the merge
 * of own changes with a newer version (409 "Weiter bearbeiten").
 */

export interface Problem {
  key: string
  params?: Record<string, unknown>
}

export const SUBJECT_MAX = 200
export const MESSAGE_MAX = 5000
export const RULES_MAX = 10

/* ------------------------------------------------------------------ e-mail addresses */

const MAIL = /^[^\s@,;<>()"]+@[^\s@,;<>()"]+\.[^\s@,;<>()".]{2,}$/

/** Addresses of a free text (separated by comma, semicolon, blanks or lines). */
export function splitMails(text: string): string[] {
  return (text ?? '')
    .split(/[\s,;]+/)
    .map((x) => x.trim())
    .filter(Boolean)
}

export const isMail = (text: string) => MAIL.test(text)

/** Addresses of the text that are no valid e-mail address. */
export function invalidMails(text: string): string[] {
  return splitMails(text).filter((m) => !isMail(m))
}

export const mailsText = (list: readonly string[] | null | undefined) => (list ?? []).filter((x) => typeof x === 'string' && x).join(', ')

/* ------------------------------------------------------------------ configurations: rights, list */

export const canEditAlarmConfig = (c: Pick<AlarmConfigSettings, 'canWrite' | 'canDelete'>) => c.canWrite === true || c.canDelete === true

/** Number of mail recipients of a configuration (people and addresses, without cc/bcc). */
export function recipientCount(c: Pick<AlarmConfigSettings, 'notification'>): number {
  const n = c.notification
  if (!n) return 0
  return (n.recipientUsers?.length ?? 0) + (n.recipientGroups?.length ?? 0) + (n.recipients?.length ?? 0)
}

/** Scope needs a list of rows. */
export const scopeNeedsObjects = (scope: string) => scope === 'SELECTED' || scope === 'WITHOUT_SELECTED'

/** Periods of the select: the JEAlarm ones plus a stored other value (e.g. CUSTOM), kept. */
export function periodOptions(current: string | null | undefined): string[] {
  const list: string[] = [...ALARM_PERIODS]
  if (current && !list.includes(current)) list.push(current)
  return list
}

/** Configurations matching a search text (name, path, site) – case-insensitive. */
export function filterAlarmConfigs(list: readonly AlarmConfigSettings[], text: string, site: number | 'all' | 'none', locale: string): AlarmConfigSettings[] {
  const terms = (text ?? '').toLocaleLowerCase(locale).split(/\s+/).filter(Boolean)
  return list.filter((c) => {
    if (site === 'none' && c.site) return false
    if (typeof site === 'number' && c.site?.id !== site) return false
    const hay = [c.name, c.path, c.site?.name].filter(Boolean).join(' ').toLocaleLowerCase(locale)
    return terms.every((t) => hay.includes(t))
  })
}

/* ------------------------------------------------------------------ configuration form */

export interface NotificationForm {
  enabled: boolean
  /** users and groups (ids) */
  people: number[]
  recipients: string
  cc: string
  bcc: string
  subject: string
  message: string
  html: boolean
}

export interface ConfigForm {
  name: string
  enabled: boolean
  scope: string
  objects: AlarmObjectRef[]
  period: string
  bindToData: boolean
  includeLimits: boolean
  disableLink: boolean
  /** daily summary of alarms and outages (docs/ALARMS-OUTAGE.md) */
  dailySummary: boolean
  /** "HH:mm" */
  dailySummaryTime: string
  notification: NotificationForm
}

export function notificationForm(n: AlarmConfigSettings['notification'] | null | undefined): NotificationForm {
  return {
    enabled: !!n?.enabled,
    people: [...(n?.recipientUsers ?? []), ...(n?.recipientGroups ?? [])].map((p) => p.id).filter((id) => typeof id === 'number'),
    recipients: mailsText(n?.recipients),
    cc: mailsText(n?.cc),
    bcc: mailsText(n?.bcc),
    subject: n?.subject ?? '',
    message: n?.message ?? '',
    html: !!n?.html,
  }
}

/** The form of a configuration; a new one: active, all rows of the building, daily. */
export function configForm(c: AlarmConfigSettings | null | undefined): ConfigForm {
  return {
    name: c?.name ?? '',
    enabled: c ? !!c.enabled : true,
    scope: c?.scope ?? 'COMPLETE',
    objects: (c?.objects ?? []).filter((o) => o && typeof o.id === 'number').map((o) => ({ ...o, attribute: o.attribute || 'Value' })),
    period: c?.period ?? 'DAILY',
    bindToData: !!c?.bindToData,
    includeLimits: !!c?.includeLimits,
    disableLink: !!c?.disableLink,
    dailySummary: !!c?.dailySummary,
    dailySummaryTime: c?.dailySummaryTime || '07:00',
    notification: notificationForm(c?.notification),
  }
}

export function cloneConfigForm(f: ConfigForm): ConfigForm {
  return { ...f, objects: f.objects.map((o) => ({ ...o })), notification: { ...f.notification, people: [...f.notification.people] } }
}

export const objectKey = (o: Pick<AlarmObjectRef, 'id' | 'attribute'>) => `${o.id}|${o.attribute || 'Value'}`

const objectsKey = (list: readonly AlarmObjectRef[]) => list.map(objectKey).join(';')

function notificationKey(n: NotificationForm): string {
  return JSON.stringify([
    n.enabled,
    [...n.people].sort((a, b) => a - b),
    splitMails(n.recipients),
    splitMails(n.cc),
    splitMails(n.bcc),
    n.subject.trim(),
    n.message.replace(/\r\n/g, '\n').trim(),
    n.html,
  ])
}

export const CONFIG_FIELDS = ['name', 'enabled', 'scope', 'objects', 'period', 'bindToData', 'includeLimits', 'disableLink', 'dailySummary', 'notification'] as const
export type ConfigField = (typeof CONFIG_FIELDS)[number]

/** Fields that differ from the loaded form. */
export function configChanges(initial: ConfigForm, form: ConfigForm): Set<ConfigField> {
  const out = new Set<ConfigField>()
  if (initial.name.trim() !== form.name.trim()) out.add('name')
  for (const f of ['enabled', 'scope', 'period', 'bindToData', 'includeLimits', 'disableLink'] as const) if (initial[f] !== form[f]) out.add(f)
  if (initial.dailySummary !== form.dailySummary || (form.dailySummary && initial.dailySummaryTime.trim() !== form.dailySummaryTime.trim())) out.add('dailySummary')
  if (objectsKey(initial.objects) !== objectsKey(form.objects)) out.add('objects')
  if (notificationKey(initial.notification) !== notificationKey(form.notification)) out.add('notification')
  return out
}

/** Kind of a chosen person: options first, then the lists as loaded. */
export function splitPeople(
  ids: readonly number[],
  people: readonly RoundOptionPerson[],
  known: { users: readonly PersonRef[]; groups: readonly PersonRef[] },
): { users: number[]; groups: number[] } {
  const kind = new Map<number, string>()
  for (const p of known.users) kind.set(p.id, 'USER')
  for (const p of known.groups) kind.set(p.id, 'GROUP')
  for (const p of people) kind.set(p.id, p.kind)
  const users: number[] = []
  const groups: number[] = []
  for (const id of ids) (kind.get(id) === 'GROUP' ? groups : users).push(id)
  return { users, groups }
}

export interface ConfigCheckOptions {
  /** the notification may be edited (not with several notifications) */
  notificationEditable: boolean
  people: readonly RoundOptionPerson[]
  known: { users: readonly PersonRef[]; groups: readonly PersonRef[] }
  /** PUT: only the changed fields; null = everything (POST) */
  changes: Set<ConfigField> | null
}

/** Checks the form as the server does; `body` only without problems. */
export function checkConfig(form: ConfigForm, opts: ConfigCheckOptions): { problems: Record<string, Problem>; body: AlarmConfigBody | null } {
  const problems: Record<string, Problem> = {}
  const name = nameProblem(form.name)
  if (name) problems.name = name
  if (scopeNeedsObjects(form.scope) && !form.objects.length) problems.objects = { key: 'alarmSettings.problem.objects' }
  const summaryTime = normalizeTime(form.dailySummaryTime)
  if (form.dailySummary && !summaryTime) problems.dailySummaryTime = { key: 'alarmSettings.problem.summaryTime' }
  const n = form.notification
  const sendNotification = opts.notificationEditable && (opts.changes == null ? n.enabled || hasNotificationContent(n) : opts.changes.has('notification'))
  if (sendNotification) {
    for (const f of ['recipients', 'cc', 'bcc'] as const) {
      const bad = invalidMails(n[f])
      if (bad.length) problems[f] = { key: 'alarmSettings.problem.mail', params: { list: bad.join(', ') } }
    }
    if (n.enabled && !n.people.length && !splitMails(n.recipients).length && !problems.recipients) problems.recipients = { key: 'alarmSettings.problem.noRecipient' }
    if (n.subject.trim().length > SUBJECT_MAX) problems.subject = { key: 'alarmSettings.problem.length', params: { max: SUBJECT_MAX } }
    if (n.message.length > MESSAGE_MAX) problems.message = { key: 'alarmSettings.problem.length', params: { max: MESSAGE_MAX } }
  }
  if (Object.keys(problems).length) return { problems, body: null }

  const want = (f: ConfigField) => opts.changes == null || opts.changes.has(f)
  const body: AlarmConfigBody = {}
  if (want('name')) body.name = form.name.trim()
  if (want('enabled')) body.enabled = form.enabled
  if (want('scope')) body.scope = form.scope
  if (want('objects') || (opts.changes != null && opts.changes.has('scope') && scopeNeedsObjects(form.scope))) {
    body.objects = form.objects.map((o) => ({ id: o.id, attribute: o.attribute || 'Value' }))
  }
  if (want('period')) body.period = form.period
  if (want('bindToData')) body.bindToData = form.bindToData
  if (want('includeLimits')) body.includeLimits = form.includeLimits
  if (want('disableLink')) body.disableLink = form.disableLink
  /* new configuration: only when switched on (servers without the attribute ignore it anyway) */
  if (opts.changes == null ? form.dailySummary : opts.changes.has('dailySummary')) {
    body.dailySummary = form.dailySummary
    if (summaryTime) body.dailySummaryTime = summaryTime
  }
  if (sendNotification) body.notification = notificationBody(n, opts.people, opts.known)
  return { problems, body }
}

/** "7:30" → "07:30"; null if not a time of day */
export function normalizeTime(text: string): string | null {
  const m = /^\s*(\d{1,2}):(\d{2})\s*$/.exec(text ?? '')
  if (!m) return null
  const h = Number(m[1])
  const min = Number(m[2])
  return h < 24 && min < 60 ? `${String(h).padStart(2, '0')}:${m[2]}` : null
}

const hasNotificationContent = (n: NotificationForm) => !!(n.people.length || n.recipients.trim() || n.cc.trim() || n.bcc.trim() || n.subject.trim() || n.message.trim())

export function notificationBody(
  n: NotificationForm,
  people: readonly RoundOptionPerson[],
  known: { users: readonly PersonRef[]; groups: readonly PersonRef[] },
): AlarmNotificationBody {
  const split = splitPeople(n.people, people, known)
  return {
    enabled: n.enabled,
    recipientUsers: split.users,
    recipientGroups: split.groups,
    recipients: splitMails(n.recipients),
    cc: splitMails(n.cc),
    bcc: splitMails(n.bcc),
    subject: n.subject.trim(),
    message: n.message.replace(/\r\n/g, '\n'),
    html: n.html,
  }
}

/** 409 "Weiter bearbeiten": own changed fields stay, the others take the newer version. */
export function mergeConfigForm(initial: ConfigForm, own: ConfigForm, server: ConfigForm): ConfigForm {
  const changes = configChanges(initial, own)
  const out = cloneConfigForm(server)
  const src = cloneConfigForm(own)
  for (const f of changes) (out as unknown as Record<string, unknown>)[f] = (src as unknown as Record<string, unknown>)[f]
  return out
}

/** The configuration of an answer (save, 409), null if it is none. */
export function configOf(json: unknown, id: number | null): AlarmConfigSettings | null {
  if (!json || typeof json !== 'object' || Array.isArray(json)) return null
  const c = json as Partial<AlarmConfigSettings>
  if (typeof c.id !== 'number' || (id != null && c.id !== id)) return null
  return { ...c, objects: Array.isArray(c.objects) ? c.objects : [] } as AlarmConfigSettings
}

export function conflictConfigOf(e: unknown, id: number): AlarmConfigSettings | null {
  if (!(e instanceof HttpError) || e.status !== 409) return null
  return configOf(e.json<unknown>(), id)
}

/* ------------------------------------------------------------------ row alarm settings: form */

/** A number for an input: the locale's decimal separator, no grouping. */
export function numberText(n: number | null | undefined, locale: string): string {
  if (n == null || typeof n !== 'number' || !Number.isFinite(n)) return ''
  const s = String(n)
  return locale.startsWith('de') ? s.replace('.', ',') : s
}

export interface StepForm {
  name: string
  min: string
  max: string
  substitute: string
  defaultMin: string
  defaultMax: string
  /** keys of `JsonLimitsConfig` the web does not edit */
  extra: Record<string, unknown>
}

export type ScheduleMode = 'none' | 'simple' | 'fixed'

export interface ScheduleForm {
  mode: ScheduleMode
  days: string[]
  from: string
  to: string
  /** stored value of a schedule the web cannot show as weekdays + time range */
  text: string
}

export interface RuleForm {
  uid: string
  id: string | number | null
  name: string | null
  kind: string
  limit: string
  limitData: AlarmRuleLimitData | null
  operator: string
  tolerance: string
  silent: ScheduleForm
  standby: ScheduleForm
  /** validity time of the rule (day/night limits) */
  valid: ScheduleForm
  /** NO_DATA: allowed time without a new value, in {@link noDataUnit} */
  noData: string
  noDataUnit: 'MINUTES' | 'HOURS'
  /** moving mean in minutes, empty = single values */
  average: string
}

export const RULE_KINDS = ['STATIC', 'DYNAMIC', 'NO_DATA'] as const

export interface RowForm {
  limitsEnabled: boolean
  steps: StepForm[]
  alarmsEnabled: boolean
  rules: RuleForm[]
}

const STEP_KEYS = new Set(['name', 'min', 'max', 'substitute', 'defaultMin', 'defaultMax'])
let ruleSeq = 0
export const newRuleUid = () => `rule${++ruleSeq}`

export function stepForm(s: Partial<LimitStep> | null | undefined, locale: string): StepForm {
  const extra: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(s ?? {})) if (!STEP_KEYS.has(k)) extra[k] = v
  return {
    name: typeof s?.name === 'string' ? s.name : '',
    min: numberText(s?.min ?? null, locale),
    max: numberText(s?.max ?? null, locale),
    substitute: typeof s?.substitute === 'string' && s.substitute ? s.substitute : 'NONE',
    defaultMin: numberText(s?.defaultMin ?? null, locale),
    defaultMax: numberText(s?.defaultMax ?? null, locale),
    extra,
  }
}

/** New steps (as the Control Center's `LimitEditor.createDefaultConfig`): two named steps, nothing set. */
export function defaultSteps(names: readonly string[]): StepForm[] {
  return names.map((name) => ({ name, min: '', max: '', substitute: 'NONE', defaultMin: '', defaultMax: '', extra: {} }))
}

export function scheduleForm(s: AlarmSchedule | null | undefined): ScheduleForm {
  if (!s) return { mode: 'none', days: [], from: '', to: '', text: '' }
  if (s.simple === true) {
    const days = Array.isArray(s.days) ? WEEKDAYS.filter((d) => s.days.includes(d)) : []
    return { mode: 'simple', days, from: s.from ?? '', to: s.to ?? '', text: '' }
  }
  return { mode: 'fixed', days: [], from: '', to: '', text: typeof s.text === 'string' ? s.text : '' }
}

export function ruleForm(r: AlarmRule, locale: string): RuleForm {
  const minutes = typeof r.noData === 'number' && r.noData > 0 ? r.noData : null
  const hours = minutes != null && minutes % 60 === 0
  return {
    uid: newRuleUid(),
    id: r.id ?? null,
    name: r.name ?? null,
    kind: r.kind === 'DYNAMIC' || r.kind === 'NO_DATA' ? r.kind : 'STATIC',
    limit: numberText(r.limit, locale),
    limitData: r.limitData ? { ...r.limitData } : null,
    operator: r.operator ?? '',
    tolerance: numberText(r.tolerance ?? 0, locale),
    silent: scheduleForm(r.silent),
    standby: scheduleForm(r.standby),
    valid: scheduleForm(r.valid ?? null),
    noData: minutes == null ? '' : String(hours ? minutes / 60 : minutes),
    noDataUnit: minutes == null || hours ? 'HOURS' : 'MINUTES',
    average: typeof r.average === 'number' && r.average > 0 ? String(r.average) : '',
  }
}

export function newRule(): RuleForm {
  return {
    uid: newRuleUid(),
    id: null,
    name: null,
    kind: 'STATIC',
    limit: '',
    limitData: null,
    operator: 'BIGGER',
    tolerance: '0',
    silent: scheduleForm(null),
    standby: scheduleForm(null),
    valid: scheduleForm(null),
    noData: '',
    noDataUnit: 'HOURS',
    average: '',
  }
}

/** A new data outage rule ("Datenausfall"), default 2 hours. */
export function newOutageRule(): RuleForm {
  return { ...newRule(), kind: 'NO_DATA', operator: '', noData: '2', noDataUnit: 'HOURS' }
}

/** Minutes of the outage rule, null if empty, NaN if not a whole positive number. */
export function noDataMinutes(r: Pick<RuleForm, 'noData' | 'noDataUnit'>, locale: string): number | null {
  if (!r.noData.trim()) return null
  const n = parseUserNumber(r.noData, locale)
  if (n == null || !(n > 0)) return NaN
  const minutes = r.noDataUnit === 'HOURS' ? n * 60 : n
  return Number.isInteger(minutes) && minutes <= 525600 ? minutes : NaN
}

export function rowForm(s: RowAlarmSettings | null | undefined, locale: string): RowForm {
  return {
    limitsEnabled: !!s?.limits?.enabled,
    steps: (s?.limits?.steps ?? []).slice(0, 2).map((x) => stepForm(x, locale)),
    alarmsEnabled: !!s?.alarms?.enabled,
    rules: (s?.alarms?.rules ?? []).map((r) => ruleForm(r, locale)),
  }
}

export function cloneRowForm(f: RowForm): RowForm {
  return {
    limitsEnabled: f.limitsEnabled,
    steps: f.steps.map((s) => ({ ...s, extra: { ...s.extra } })),
    alarmsEnabled: f.alarmsEnabled,
    rules: f.rules.map((r) => ({
      ...r,
      limitData: r.limitData ? { ...r.limitData } : null,
      silent: { ...r.silent, days: [...r.silent.days] },
      standby: { ...r.standby, days: [...r.standby.days] },
      valid: { ...r.valid, days: [...r.valid.days] },
    })),
  }
}

const limitsKey = (f: RowForm) => JSON.stringify([f.limitsEnabled, f.steps])
const alarmsKey = (f: RowForm) => JSON.stringify([f.alarmsEnabled, f.rules.map(({ uid: _uid, ...rest }) => rest)])

export function rowChanges(initial: RowForm, form: RowForm): { limits: boolean; alarms: boolean } {
  return { limits: limitsKey(initial) !== limitsKey(form), alarms: alarmsKey(initial) !== alarmsKey(form) }
}

/** 409 "Weiter bearbeiten": an own changed section stays, the other one takes the newer version. */
export function mergeRowForm(initial: RowForm, own: RowForm, server: RowForm): RowForm {
  const c = rowChanges(initial, own)
  const mine = cloneRowForm(own)
  const out = cloneRowForm(server)
  if (c.limits) {
    out.limitsEnabled = mine.limitsEnabled
    out.steps = mine.steps
  }
  if (c.alarms) {
    out.alarmsEnabled = mine.alarmsEnabled
    out.rules = mine.rules
  }
  return out
}

/* ------------------------------------------------------------------ row alarm settings: checks and body */

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/

export function scheduleProblem(s: ScheduleForm): Problem | null {
  if (s.mode !== 'simple') return null
  if (!s.days.length) return { key: 'alarmSettings.problem.days' }
  if (!TIME.test(s.from) || !TIME.test(s.to)) return { key: 'alarmSettings.problem.time' }
  if (s.from === s.to) return { key: 'alarmSettings.problem.sameTime' }
  return null
}

export function scheduleBody(s: ScheduleForm): AlarmSchedule | null {
  if (s.mode === 'simple') return { simple: true, days: WEEKDAYS.filter((d) => s.days.includes(d)), from: s.from, to: s.to }
  if (s.mode === 'fixed') return { simple: false, text: s.text }
  return null
}

/** A typed number: null if empty, NaN if not a number. */
function num(text: string, locale: string): number | null {
  if (!text.trim()) return null
  const n = parseUserNumber(text, locale)
  return n == null ? NaN : n
}

export interface RowCheck {
  problems: Record<string, Problem>
  body: Omit<RowAlarmSettingsBody, 'version'> | null
}

/** Checks the changed sections as the server does; `body` holds only those (null with problems). */
export function checkRow(form: RowForm, changes: { limits: boolean; alarms: boolean }, locale: string): RowCheck {
  const problems: Record<string, Problem> = {}
  const body: Omit<RowAlarmSettingsBody, 'version'> = {}
  const NUMBER: Problem = { key: 'alarmSettings.problem.number' }

  if (changes.limits) {
    const steps: LimitStep[] = []
    if (form.limitsEnabled && !form.steps.length) problems.steps = { key: 'alarmSettings.problem.steps' }
    form.steps.forEach((s, i) => {
      const p = `step${i}.`
      const min = num(s.min, locale)
      const max = num(s.max, locale)
      const dMin = num(s.defaultMin, locale)
      const dMax = num(s.defaultMax, locale)
      if (Number.isNaN(min)) problems[p + 'min'] = NUMBER
      if (Number.isNaN(max)) problems[p + 'max'] = NUMBER
      if (min != null && max != null && !Number.isNaN(min) && !Number.isNaN(max) && min >= max) problems[p + 'max'] = { key: 'alarmSettings.problem.minMax' }
      const defaults = s.substitute === 'DEFAULT_VALUE'
      if (defaults) {
        if (Number.isNaN(dMin)) problems[p + 'defaultMin'] = NUMBER
        else if (dMin == null) problems[p + 'defaultMin'] = { key: 'alarmSettings.problem.default' }
        if (Number.isNaN(dMax)) problems[p + 'defaultMax'] = NUMBER
        else if (dMax == null) problems[p + 'defaultMax'] = { key: 'alarmSettings.problem.default' }
      }
      const keep = (n: number | null) => (n == null || Number.isNaN(n) ? null : n)
      steps.push({
        ...s.extra,
        name: s.name,
        min: keep(min),
        max: keep(max),
        substitute: s.substitute,
        /* defaults are kept as typed (the processor reads them only for DEFAULT_VALUE) */
        defaultMin: keep(dMin),
        defaultMax: keep(dMax),
      })
    })
    body.limits = { enabled: form.limitsEnabled, steps }
  }

  if (changes.alarms) {
    if (form.rules.length > RULES_MAX) problems.rules = { key: 'alarmSettings.problem.rules', params: { max: RULES_MAX } }
    const rules: RowAlarmRuleBody[] = []
    for (const r of form.rules) {
      const p = `${r.uid}.`
      if (r.kind === 'NO_DATA') {
        const minutes = noDataMinutes(r, locale)
        if (minutes == null) problems[p + 'noData'] = { key: 'alarmSettings.problem.noData' }
        else if (Number.isNaN(minutes)) problems[p + 'noData'] = { key: 'alarmSettings.problem.noDataNumber' }
        const silent = scheduleProblem(r.silent)
        if (silent) problems[p + 'silent'] = silent
        const standby = scheduleProblem(r.standby)
        if (standby) problems[p + 'standby'] = standby
        rules.push({
          id: r.id,
          ...(r.name != null ? { name: r.name } : {}),
          kind: 'NO_DATA',
          limit: null,
          limitData: null,
          tolerance: 0,
          silent: scheduleBody(r.silent),
          standby: scheduleBody(r.standby),
          noData: minutes != null && !Number.isNaN(minutes) ? minutes : 0,
        })
        continue
      }
      let limit: number | null = null
      if (r.kind === 'STATIC') {
        const n = num(r.limit, locale)
        if (n == null) problems[p + 'limit'] = { key: 'alarmSettings.problem.limit' }
        else if (Number.isNaN(n)) problems[p + 'limit'] = NUMBER
        else limit = n
      } else if (!r.limitData) problems[p + 'limitData'] = { key: 'alarmSettings.problem.limitData' }
      if (!(OPERATORS as readonly string[]).includes(r.operator)) problems[p + 'operator'] = { key: 'alarmSettings.problem.operator' }
      const tol = num(r.tolerance, locale)
      if (tol != null && Number.isNaN(tol)) problems[p + 'tolerance'] = NUMBER
      else if (tol != null && tol < 0) problems[p + 'tolerance'] = { key: 'alarmSettings.problem.tolerance' }
      const silent = scheduleProblem(r.silent)
      if (silent) problems[p + 'silent'] = silent
      const standby = scheduleProblem(r.standby)
      if (standby) problems[p + 'standby'] = standby
      const valid = scheduleProblem(r.valid)
      if (valid) problems[p + 'valid'] = valid
      let average: number | null = null
      if (r.average.trim()) {
        const n = parseUserNumber(r.average, locale)
        if (n == null || !Number.isInteger(n) || n < 1 || n > 10080) problems[p + 'average'] = { key: 'alarmSettings.problem.average' }
        else average = n
      }
      rules.push({
        id: r.id,
        ...(r.name != null ? { name: r.name } : {}),
        valid: scheduleBody(r.valid),
        average,
        kind: r.kind,
        limit: r.kind === 'STATIC' ? limit : null,
        limitData: r.kind === 'DYNAMIC' && r.limitData ? { id: r.limitData.id, attribute: r.limitData.attribute || 'Value' } : null,
        operator: r.operator,
        tolerance: tol == null || Number.isNaN(tol) ? 0 : tol,
        silent: scheduleBody(r.silent),
        standby: scheduleBody(r.standby),
      })
    }
    body.alarms = { enabled: form.alarmsEnabled, rules }
  }

  return { problems, body: Object.keys(problems).length ? null : body }
}

/** The settings of an answer (save, 409), null if they are none. */
export function rowSettingsOf(json: unknown): RowAlarmSettings | null {
  if (!json || typeof json !== 'object' || Array.isArray(json)) return null
  const s = json as Partial<RowAlarmSettings>
  if (!s.row || typeof s.row.id !== 'number') return null
  return {
    ...s,
    limits: { enabled: !!s.limits?.enabled, steps: Array.isArray(s.limits?.steps) ? s.limits!.steps : [] },
    alarms: { enabled: !!s.alarms?.enabled, rules: Array.isArray(s.alarms?.rules) ? s.alarms!.rules : [] },
    configurations: Array.isArray(s.configurations) ? s.configurations : [],
  } as RowAlarmSettings
}

export function conflictRowSettingsOf(e: unknown): RowAlarmSettings | null {
  if (!(e instanceof HttpError) || e.status !== 409) return null
  return rowSettingsOf(e.json<unknown>())
}

/** Operator symbol of `AlarmConstants.Operator`. */
export const OPERATOR_SYMBOLS: Record<string, string> = {
  BIGGER: '>',
  BIGGER_EQUALS: '≥',
  EQUALS: '=',
  NOT_EQUALS: '≠',
  SMALLER: '<',
  SMALLER_EQUALS: '≤',
}
