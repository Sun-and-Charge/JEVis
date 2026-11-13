/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import { conflictRound, roundSheetPath, type ReadingRound, type RoundOptionPerson } from '../src/api/readingRounds'
import type { ReadingMeter } from '../src/api/readings'
import {
  changedRoundFields,
  defaultRoundFilter,
  filterRounds,
  mergeRoundForm,
  moveItem,
  newRoundForm,
  nextRoundMeter,
  openRoundMeters,
  parseDays,
  peopleText,
  periodMinDays,
  roundBody,
  roundForm,
  roundRowClass,
  roundStateClass,
  searchMeters,
  searchPeople,
  sortRounds,
  usersWithoutEmail,
  validateRoundForm,
  type RoundValidationContext,
} from '../src/readings/rounds'
import { sheetFileName } from '../src/readings/sheet'
import { httpError } from './fixtures/edit'

/* E8.2 reading rounds: list logic, editor validation, reorder (neutral names). */

function round(id: number, name: string, overrides: Partial<ReadingRound> = {}): ReadingRound {
  return {
    id,
    name,
    site: { id: 1001, name: 'Musterwerk' },
    directoryId: 7000,
    enabled: true,
    period: 'P1M',
    startDate: '2026-01-01',
    leadDays: 3,
    reminderDays: 3,
    escalationDays: 7,
    remarks: null,
    readers: [{ id: 41, name: 'Max Mustermann', kind: 'USER' }],
    supervisor: [],
    due: '2026-11-01',
    nextDue: '2026-12-01',
    state: 'OPEN',
    done: 0,
    total: 2,
    meters: [
      { id: 5101, readable: true, name: 'Hauptzähler Strom', done: false, automatic: false },
      { id: 5102, readable: true, name: 'Unterzähler Halle 2', done: false, automatic: false },
    ],
    mine: false,
    canWrite: true,
    canDelete: true,
    version: '2026-11-10T09:00:00.000+01:00',
    ...overrides,
  }
}

const ctx = (overrides: Partial<RoundValidationContext> = {}): RoundValidationContext => ({
  otherNames: [],
  meterIds: new Set([5101, 5102, 5103]),
  peopleIds: new Set([41, 42]),
  creating: true,
  locale: 'de',
  ...overrides,
})

describe('round list', () => {
  it('maps states to badge and row classes', () => {
    expect(roundStateClass('DONE')).toBe('is-done')
    expect(roundStateClass('OPEN')).toBe('is-open')
    expect(roundStateClass('LATE')).toBe('is-dueSoon')
    expect(roundStateClass('ESCALATED')).toBe('is-overdue')
    expect(roundStateClass('NOT_STARTED')).toBe('is-none')
    expect(roundStateClass('INACTIVE')).toBe('is-none')
    expect(roundStateClass('SOMETHING')).toBe('is-none')
    expect(roundRowClass('ESCALATED')).toBe('is-overdue')
    expect(roundRowClass('LATE')).toBe('is-dueSoon')
    expect(roundRowClass('OPEN')).toBeUndefined()
    expect(roundRowClass('INACTIVE')).toBe('is-done')
  })

  it('sorts own rounds first, then urgent states, then by name', () => {
    const list = [
      round(1, 'Zeta', { state: 'DONE' }),
      round(2, 'Alpha', { state: 'INACTIVE' }),
      round(3, 'Beta', { state: 'ESCALATED' }),
      round(4, 'Gamma', { state: 'OPEN', mine: true }),
      round(5, 'Delta', { state: 'LATE' }),
      round(6, 'Epsilon', { state: 'NOT_STARTED' }),
      round(7, 'Äpfel', { state: 'LATE' }),
    ]
    expect(sortRounds(list, 'de').map((r) => r.name)).toEqual(['Gamma', 'Beta', 'Äpfel', 'Delta', 'Epsilon', 'Zeta', 'Alpha'])
  })

  it('filters by "Meine Runden", site and search', () => {
    const list = [
      round(1, 'Monatsablesung', { mine: true }),
      round(2, 'Lager jährlich', { site: { id: 1002, name: 'Lager Nord' }, readers: [{ id: 42, name: 'Energieteam', kind: 'GROUP' }] }),
      round(3, 'Ohne Leser', { readers: [{ id: 99, name: null, kind: 'USER' }] }),
    ]
    expect(defaultRoundFilter(list)).toBe('mine')
    expect(defaultRoundFilter(list.slice(1))).toBe('all')
    expect(filterRounds(list, 'mine', null, '', 'de').map((r) => r.id)).toEqual([1])
    expect(filterRounds(list, 'all', 1002, '', 'de').map((r) => r.id)).toEqual([2])
    expect(filterRounds(list, 'all', null, 'energie', 'de').map((r) => r.id)).toEqual([2])
    expect(filterRounds(list, 'all', null, 'nord', 'de').map((r) => r.id)).toEqual([2])
    expect(peopleText(list[2].readers, '(nicht sichtbar)')).toBe('(nicht sichtbar)')
    expect(peopleText([...list[0].readers, ...list[1].readers], '?')).toBe('Max Mustermann, Energieteam')
    expect(openRoundMeters(round(4, 'x', { meters: [{ id: 1, readable: true, done: true }, { id: 2, readable: true }, { id: 3, readable: false }, { id: 4, readable: true, automatic: true }] }))).toBe(1)
  })

  it('follows the round order to the next meter that is not done and can be read', () => {
    const row = { canEnter: true } as ReadingMeter['row']
    const m = (id: number, doneInRound: boolean, extra: Partial<ReadingMeter> = {}) => ({ id, row, problem: null, doneInRound, ...extra }) as Pick<ReadingMeter, 'id' | 'row' | 'problem' | 'doneInRound'>
    const list = [m(1, false), m(2, true), m(3, false, { problem: 'AUTOMATIC' }), m(4, false, { row: null }), m(5, false)]
    expect(nextRoundMeter(list, 1)?.id).toBe(5)
    expect(nextRoundMeter(list, 5)?.id).toBe(1)
    expect(nextRoundMeter(list, 2)?.id).toBe(5)
    expect(nextRoundMeter([m(1, false), m(2, true)], 1)).toBeNull()
  })

  it('builds the sheet path and file name', () => {
    expect(roundSheetPath(7001, 'de')).toBe('/web/reading-rounds/7001/sheet.pdf?lang=de')
    expect(roundSheetPath(7001, 'en', '2026-11-01')).toBe('/web/reading-rounds/7001/sheet.pdf?lang=en&due=2026-11-01')
    expect(sheetFileName('Monatsablesung Musterwerk', '2026-11-01')).toBe('Laufzettel_Monatsablesung_Musterwerk_2026-11-01.pdf')
    expect(sheetFileName('a/b', null)).toBe('Laufzettel_a_b.pdf')
  })

  it('reads the current round of a 409 answer', () => {
    const r = round(1, 'Monatsablesung', { version: 'v2' })
    expect(conflictRound(httpError(409, r))?.version).toBe('v2')
    expect(conflictRound(httpError(409, { round: r }))?.id).toBe(1)
    expect(conflictRound(httpError(409, 'kaputt'))).toBeNull()
    expect(conflictRound(httpError(400, r))).toBeNull()
  })
})

describe('round editor form', () => {
  const valid = () => ({ ...newRoundForm('2026-11-13', 1001), name: 'Wochenrunde', readers: [41], meters: [5101, 5102] })

  it('starts with the server defaults', () => {
    expect(newRoundForm('2026-11-13', 1001)).toMatchObject({ startDate: '2026-11-13', period: 'P1M', leadDays: '3', reminderDays: '3', escalationDays: '7', enabled: true })
    expect(validateRoundForm(valid(), ctx())).toEqual({})
  })

  it('mirrors the server rules', () => {
    const p = validateRoundForm({ ...newRoundForm('', null), startDate: '2026-02-30', period: 'P2D' }, ctx())
    expect(Object.keys(p).sort()).toEqual(['meters', 'name', 'period', 'readers', 'site', 'startDate'])
    expect(validateRoundForm({ ...valid(), name: 'x'.repeat(101) }, ctx()).name?.key).toBe('nameTooLong')
    expect(validateRoundForm({ ...valid(), name: 'a\tb' }, ctx()).name?.key).toBe('invalidChars')
    expect(validateRoundForm({ ...valid(), name: ' wochenrunde ' }, ctx({ otherNames: ['Wochenrunde'] })).name?.key).toBe('nameTaken')
    expect(validateRoundForm({ ...valid(), leadDays: '61' }, ctx()).leadDays).toEqual({ key: 'days', params: { max: 60 } })
    expect(validateRoundForm({ ...valid(), reminderDays: '-1', escalationDays: '2,5' }, ctx())).toMatchObject({ reminderDays: { key: 'days' }, escalationDays: { key: 'days' } })
    expect(validateRoundForm({ ...valid(), period: 'P1W', leadDays: '7' }, ctx()).leadDays).toEqual({ key: 'leadTooLong', params: { max: 6 } })
    expect(validateRoundForm({ ...valid(), period: 'P1W', leadDays: '6' }, ctx()).leadDays).toBeUndefined()
    expect(validateRoundForm({ ...valid(), meters: [5101, 5101] }, ctx()).meters?.key).toBe('metersDuplicate')
    expect(validateRoundForm({ ...valid(), meters: [5101, 9999] }, ctx()).meters?.key).toBe('metersUnavailable')
    expect(validateRoundForm({ ...valid(), meters: [5101, 9999] }, ctx({ meterIds: null })).meters).toBeUndefined()
    expect(validateRoundForm({ ...valid(), meters: Array.from({ length: 501 }, (_, i) => i) }, ctx({ meterIds: null })).meters?.key).toBe('metersTooMany')
    expect(validateRoundForm({ ...valid(), supervisor: [77] }, ctx()).supervisor?.key).toBe('peopleUnknown')
    expect(validateRoundForm({ ...valid(), remarks: 'x'.repeat(1001) }, ctx()).remarks?.key).toBe('remarksTooLong')
    expect(validateRoundForm({ ...valid(), remarks: 'Zeile 1\nZeile 2' }, ctx()).remarks).toBeUndefined()
    expect(validateRoundForm({ ...valid(), site: null }, ctx({ creating: false })).site).toBeUndefined()
  })

  it('parses days and knows the shortest period lengths', () => {
    expect(parseDays(' 7 ')).toBe(7)
    expect(parseDays('0')).toBe(0)
    expect(parseDays('')).toBeNull()
    expect(parseDays('1e1')).toBeNull()
    expect(periodMinDays('P1M')).toBe(28)
    expect(periodMinDays('P1Y')).toBe(365)
    expect(periodMinDays('P5D')).toBeNull()
  })

  it('builds the request body', () => {
    expect(roundBody({ ...valid(), name: '  Wochenrunde ', remarks: '  ', leadDays: '2' })).toEqual({
      name: 'Wochenrunde',
      enabled: true,
      meters: [5101, 5102],
      readers: [41],
      supervisor: [],
      startDate: '2026-11-13',
      period: 'P1M',
      leadDays: 2,
      reminderDays: 3,
      escalationDays: 7,
      remarks: null,
    })
  })

  it('finds changes and merges after a conflict', () => {
    const initial = roundForm(round(1, 'Monatsablesung'))
    const own = { ...initial, name: 'Monatsablesung neu', meters: [5102, 5101] }
    expect(changedRoundFields(initial, { ...initial, name: ' Monatsablesung ' })).toEqual([])
    expect(changedRoundFields(initial, own)).toEqual(['name', 'meters'])
    const server = { ...initial, remarks: 'Schlüssel beim Pförtner', meters: [5101, 5102, 5103] }
    const merged = mergeRoundForm(initial, own, server)
    expect(merged).toMatchObject({ name: 'Monatsablesung neu', meters: [5102, 5101], remarks: 'Schlüssel beim Pförtner' })
    merged.meters.push(1)
    expect(own.meters).toEqual([5102, 5101])
  })
})

describe('round pickers', () => {
  it('moves items', () => {
    expect(moveItem([1, 2, 3], 0, 2)).toEqual([2, 3, 1])
    expect(moveItem([1, 2, 3], 2, 0)).toEqual([3, 1, 2])
    expect(moveItem([1, 2, 3], 1, 1)).toEqual([1, 2, 3])
    expect(moveItem([1, 2, 3], 1, 9)).toEqual([1, 3, 2])
    expect(moveItem([1, 2, 3], 5, 0)).toEqual([1, 2, 3])
  })

  const people: RoundOptionPerson[] = [
    { id: 41, name: 'Max Mustermann', kind: 'USER', email: true },
    { id: 43, name: 'Erika Beispiel', kind: 'USER', email: false },
    { id: 42, name: 'Energieteam', kind: 'GROUP', email: false },
  ]

  it('searches people and warns about users without e-mail', () => {
    expect(searchPeople(people, [41], '', 'de').map((p) => p.id)).toEqual([42, 43])
    expect(searchPeople(people, [], 'e', 'de', 1).map((p) => p.id)).toEqual([42])
    expect(searchPeople(people, [], 'muster', 'de').map((p) => p.id)).toEqual([41])
    expect(usersWithoutEmail(people, [41, 42, 43]).map((p) => p.id)).toEqual([43])
  })

  it('searches the meters of the site not in the round', () => {
    const meters = [
      { id: 5101, name: 'Hauptzähler Strom', location: 'Keller', medium: 'ELECTRICITY', automatic: false },
      { id: 5102, name: 'Unterzähler Halle 2', location: 'Halle 2', medium: 'ELECTRICITY', automatic: false },
      { id: 5104, name: 'Gas Heizung', location: 'Keller', medium: 'GAS', automatic: true },
    ]
    expect(searchMeters(meters, [5101], 'keller', 'de').map((m) => m.id)).toEqual([5104])
    expect(searchMeters(meters, [], '', 'de').map((m) => m.id)).toEqual([5101, 5102, 5104])
  })
})
