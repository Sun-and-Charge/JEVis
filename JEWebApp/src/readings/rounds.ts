/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { ReadingMeter } from '../api/readings'
import {
  ROUND_LIMITS,
  ROUND_PERIODS,
  type ReadingRound,
  type RoundBody,
  type RoundOptionMeter,
  type RoundOptionPerson,
  type RoundState,
} from '../api/readingRounds'
import { matchesTerms, searchTerms } from '../modules/search'
import { canRead } from './readings'

/*
 * Logic of the reading rounds (E8.2): state badges and order of the list, the "Meine Runden" filter, the next meter
 * of a round ("Speichern & weiter" in round order), the editor's form with the server's validation rules mirrored,
 * the merge after a 409 and the reordering of the meters.
 */

/* ------------------------------------------------------------------ list */

/** Order of the states in the list (as the server sorts): urgent first. */
export const STATE_ORDER: readonly RoundState[] = ['ESCALATED', 'LATE', 'OPEN', 'NOT_STARTED', 'DONE', 'INACTIVE']

/** Badge class of a state: Erledigt green, Offen neutral, Verspätet amber, Eskaliert red, others grey. */
export function roundStateClass(state: string): string {
  switch (state) {
    case 'DONE':
      return 'is-done'
    case 'OPEN':
      return 'is-open'
    case 'LATE':
      return 'is-dueSoon'
    case 'ESCALATED':
      return 'is-overdue'
    default:
      return 'is-none'
  }
}

/** Row / card marker of a state (left edge as the meter list). */
export function roundRowClass(state: string): string | undefined {
  return state === 'ESCALATED' ? 'is-overdue' : state === 'LATE' ? 'is-dueSoon' : state === 'DONE' || state === 'INACTIVE' ? 'is-done' : undefined
}

const stateRank = (state: string) => {
  const i = STATE_ORDER.indexOf(state as RoundState)
  return i < 0 ? STATE_ORDER.length : i
}

/** Sorted as the server: own rounds first, then by state (urgent first), then by name. */
export function sortRounds<R extends Pick<ReadingRound, 'mine' | 'state' | 'name'>>(rounds: readonly R[], locale: string): R[] {
  return [...rounds].sort(
    (a, b) => Number(b.mine) - Number(a.mine) || stateRank(a.state) - stateRank(b.state) || a.name.localeCompare(b.name, locale, { sensitivity: 'base' }),
  )
}

export type RoundFilter = 'mine' | 'all'

/** "Meine Runden" if the user is a reader of any round, else "Alle". */
export function defaultRoundFilter(rounds: readonly Pick<ReadingRound, 'mine'>[]): RoundFilter {
  return rounds.some((r) => r.mine) ? 'mine' : 'all'
}

/** Rounds of the filter and site (null: all sites) whose name, site or readers contain all search words. */
export function filterRounds<R extends Pick<ReadingRound, 'mine' | 'name' | 'site' | 'readers'>>(
  rounds: readonly R[],
  filter: RoundFilter,
  site: number | null,
  text: string,
  locale: string,
): R[] {
  const terms = searchTerms(text, locale)
  return rounds.filter(
    (r) =>
      (filter === 'all' || r.mine) &&
      (site == null || r.site?.id === site) &&
      matchesTerms(terms, [r.name, r.site?.name, ...r.readers.map((p) => p.name)], locale),
  )
}

/** Meters of a round that are still open (readable, not automatic, not done). */
export function openRoundMeters(round: Pick<ReadingRound, 'meters'>): number {
  return round.meters.filter((m) => m.readable && !m.done && !m.automatic).length
}

/** Readers as text ("Max Mustermann, Energieteam"); people the user may not read as `unknown`. */
export function peopleText(people: readonly { name: string | null }[], unknown: string): string {
  return people.map((p) => p.name || unknown).join(', ')
}

/** The next meter of a round after `currentId` in round order (wrapping around) that is not done and can be read. */
export function nextRoundMeter<M extends Pick<ReadingMeter, 'id' | 'row' | 'problem' | 'doneInRound'>>(list: readonly M[], currentId: number): M | null {
  const start = list.findIndex((m) => m.id === currentId)
  for (let i = 1; i <= list.length; i++) {
    const m = list[(start + i + list.length) % list.length]
    if (m.id !== currentId && !m.doneInRound && canRead(m)) return m
  }
  return null
}

/* ------------------------------------------------------------------ editor form */

/** The editor's state; numbers as typed. */
export interface RoundForm {
  name: string
  site: number | null
  enabled: boolean
  startDate: string
  period: string
  leadDays: string
  reminderDays: string
  escalationDays: string
  readers: number[]
  supervisor: number[]
  meters: number[]
  remarks: string
}

export type RoundField = 'name' | 'site' | 'startDate' | 'period' | 'leadDays' | 'reminderDays' | 'escalationDays' | 'readers' | 'supervisor' | 'meters' | 'remarks'

/** A problem: i18n key below `rounds.problem.` with parameters. */
export interface RoundProblem {
  key: string
  params?: Record<string, unknown>
}

export type RoundProblems = Partial<Record<RoundField, RoundProblem>>

/** Defaults of a new round (server defaults: lead 3, reminder 3, escalation 7 days; monthly from today). */
export function newRoundForm(today: string, site: number | null): RoundForm {
  return {
    name: '',
    site,
    enabled: true,
    startDate: today,
    period: 'P1M',
    leadDays: '3',
    reminderDays: '3',
    escalationDays: '7',
    readers: [],
    supervisor: [],
    meters: [],
    remarks: '',
  }
}

export function roundForm(round: ReadingRound): RoundForm {
  return {
    name: round.name,
    site: round.site?.id ?? null,
    enabled: round.enabled,
    startDate: round.startDate ?? '',
    period: round.period ?? '',
    leadDays: String(round.leadDays ?? ''),
    reminderDays: String(round.reminderDays ?? ''),
    escalationDays: String(round.escalationDays ?? ''),
    readers: round.readers.map((p) => p.id),
    supervisor: round.supervisor.map((p) => p.id),
    meters: round.meters.map((m) => m.id),
    remarks: round.remarks ?? '',
  }
}

export function cloneRoundForm(form: RoundForm): RoundForm {
  return { ...form, readers: [...form.readers], supervisor: [...form.supervisor], meters: [...form.meters] }
}

const FIELDS: readonly (keyof RoundForm)[] = [
  'name',
  'site',
  'enabled',
  'startDate',
  'period',
  'leadDays',
  'reminderDays',
  'escalationDays',
  'readers',
  'supervisor',
  'meters',
  'remarks',
]

function same(a: RoundForm[keyof RoundForm], b: RoundForm[keyof RoundForm]): boolean {
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((v, i) => v === b[i])
  return typeof a === 'string' && typeof b === 'string' ? a.trim() === b.trim() : a === b
}

/** The fields that differ (text compared trimmed). */
export function changedRoundFields(initial: RoundForm, form: RoundForm): (keyof RoundForm)[] {
  return FIELDS.filter((f) => !same(initial[f], form[f]))
}

/**
 * After a 409: fields the user changed keep the user's value, all others take the server's current value
 * ("Weiter bearbeiten").
 */
export function mergeRoundForm(initial: RoundForm, own: RoundForm, server: RoundForm): RoundForm {
  const changed = new Set(changedRoundFields(initial, own))
  const merged = cloneRoundForm(server)
  for (const f of changed) (merged as unknown as Record<string, unknown>)[f] = Array.isArray(own[f]) ? [...(own[f] as number[])] : own[f]
  return merged
}

/** Shortest length of a period in days (lead days must be shorter). */
export function periodMinDays(period: string): number | null {
  switch (period) {
    case 'P1W':
      return 7
    case 'P2W':
      return 14
    case 'P1M':
      return 28
    case 'P3M':
      return 89
    case 'P6M':
      return 181
    case 'P1Y':
      return 365
    default:
      return null
  }
}

/** A whole number of days 0–60 typed by the user; null if invalid. */
export function parseDays(text: string): number | null {
  const s = (text ?? '').trim()
  if (!/^\d{1,3}$/.test(s)) return null
  const n = Number(s)
  return n >= 0 && n <= ROUND_LIMITS.days ? n : null
}

function validDate(text: string): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text ?? '')
  if (!m) return false
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])))
  return d.getUTCFullYear() === Number(m[1]) && d.getUTCMonth() === Number(m[2]) - 1 && d.getUTCDate() === Number(m[3])
}

export interface RoundValidationContext {
  /** names of the other rounds of the same directory (site), for the uniqueness check */
  otherNames: readonly string[]
  /** ids of the meters that may be chosen (options.meters); null: not loaded, not checked */
  meterIds: ReadonlySet<number> | null
  /** ids of the people that may be chosen (options.people); null: not checked */
  peopleIds: ReadonlySet<number> | null
  /** a new round needs a site */
  creating: boolean
  locale: string
}

// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u001f\u007f]/
// eslint-disable-next-line no-control-regex
const CONTROL_EXCEPT_LINES = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/

/** The server's rules (docs: "all problems in one 400"), per field. */
export function validateRoundForm(form: RoundForm, ctx: RoundValidationContext): RoundProblems {
  const p: RoundProblems = {}
  const name = form.name.trim()
  if (!name) p.name = { key: 'nameRequired' }
  else if (name.length > ROUND_LIMITS.name) p.name = { key: 'nameTooLong', params: { max: ROUND_LIMITS.name } }
  else if (CONTROL.test(form.name)) p.name = { key: 'invalidChars' }
  else if (ctx.otherNames.some((n) => n.trim().localeCompare(name, ctx.locale, { sensitivity: 'accent' }) === 0)) p.name = { key: 'nameTaken' }

  if (ctx.creating && form.site == null) p.site = { key: 'siteRequired' }
  if (!validDate(form.startDate)) p.startDate = { key: 'dateRequired' }
  if (!(ROUND_PERIODS as readonly string[]).includes(form.period)) p.period = { key: 'periodRequired' }

  const lead = parseDays(form.leadDays)
  const reminder = parseDays(form.reminderDays)
  const escalation = parseDays(form.escalationDays)
  const days = { max: ROUND_LIMITS.days }
  if (lead == null) p.leadDays = { key: 'days', params: days }
  else {
    const min = periodMinDays(form.period)
    if (min != null && lead >= min) p.leadDays = { key: 'leadTooLong', params: { max: min - 1 } }
  }
  if (reminder == null) p.reminderDays = { key: 'days', params: days }
  if (escalation == null) p.escalationDays = { key: 'days', params: days }

  if (!form.readers.length) p.readers = { key: 'readersRequired' }
  else if (ctx.peopleIds && form.readers.some((id) => !ctx.peopleIds!.has(id))) p.readers = { key: 'peopleUnknown' }
  if (ctx.peopleIds && form.supervisor.some((id) => !ctx.peopleIds!.has(id))) p.supervisor = { key: 'peopleUnknown' }

  if (!form.meters.length) p.meters = { key: 'metersRequired' }
  else if (form.meters.length > ROUND_LIMITS.meters) p.meters = { key: 'metersTooMany', params: { max: ROUND_LIMITS.meters } }
  else if (new Set(form.meters).size !== form.meters.length) p.meters = { key: 'metersDuplicate' }
  else if (ctx.meterIds && form.meters.some((id) => !ctx.meterIds!.has(id))) p.meters = { key: 'metersUnavailable' }

  if (form.remarks.trim().length > ROUND_LIMITS.remarks) p.remarks = { key: 'remarksTooLong', params: { max: ROUND_LIMITS.remarks } }
  else if (CONTROL_EXCEPT_LINES.test(form.remarks)) p.remarks = { key: 'invalidChars' }
  return p
}

/** The request body of a valid form. */
export function roundBody(form: RoundForm): RoundBody {
  const remarks = form.remarks.trim()
  return {
    name: form.name.trim(),
    enabled: form.enabled,
    meters: [...form.meters],
    readers: [...form.readers],
    supervisor: [...form.supervisor],
    startDate: form.startDate,
    period: form.period,
    leadDays: parseDays(form.leadDays) ?? 0,
    reminderDays: parseDays(form.reminderDays) ?? 0,
    escalationDays: parseDays(form.escalationDays) ?? 0,
    remarks: remarks || null,
  }
}

/* ------------------------------------------------------------------ pickers */

/** A copy of `list` with the item at `from` moved to `to` (indexes clamped); the same array if nothing moves. */
export function moveItem<T>(list: readonly T[], from: number, to: number): T[] {
  const target = Math.max(0, Math.min(list.length - 1, to))
  if (from < 0 || from >= list.length || from === target) return [...list]
  const next = [...list]
  const [item] = next.splice(from, 1)
  next.splice(target, 0, item)
  return next
}

/** People not chosen yet that match the search (groups and users), sorted by name; at most `limit`. */
export function searchPeople(people: readonly RoundOptionPerson[], chosen: readonly number[], text: string, locale: string, limit = 8): RoundOptionPerson[] {
  const terms = searchTerms(text, locale)
  const taken = new Set(chosen)
  return people
    .filter((p) => !taken.has(p.id) && matchesTerms(terms, [p.name], locale))
    .sort((a, b) => a.name.localeCompare(b.name, locale, { sensitivity: 'base' }))
    .slice(0, limit)
}

/** Chosen users without an e-mail address (they get no reminders, E8.3). */
export function usersWithoutEmail(people: readonly RoundOptionPerson[], chosen: readonly number[]): RoundOptionPerson[] {
  const byId = new Map(people.map((p) => [p.id, p]))
  return chosen.map((id) => byId.get(id)).filter((p): p is RoundOptionPerson => !!p && p.kind === 'USER' && !p.email)
}

/** Meters of the site not in the round yet that match the search (register order kept). */
export function searchMeters(meters: readonly RoundOptionMeter[], chosen: readonly number[], text: string, locale: string): RoundOptionMeter[] {
  const terms = searchTerms(text, locale)
  const taken = new Set(chosen)
  return meters.filter((m) => !taken.has(m.id) && matchesTerms(terms, [m.name, m.location], locale))
}
