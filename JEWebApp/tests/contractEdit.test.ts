/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import {
  addRangingRow,
  anyContractWritable,
  canCreateIn,
  canEditObject,
  checkForm,
  checkNewValue,
  checkRanging,
  cloneForm,
  contractForm,
  firstOfNextMonth,
  inputNumber,
  isDay,
  mergeForm,
  objectOf,
  RANGING_MAX,
  rangingFormRows,
  removeRangingRow,
  UNLIMITED,
  usedByOf,
  warningsOf,
  type RangingFormRow,
} from '../src/accounting/contractEdit'
import { contractTabs } from '../src/accounting/contracts'
import { contractObject } from './fixtures/accounting'
import { httpError } from './fixtures/edit'

/* Pure logic of the contract editor (E6.2); neutral placeholder data. */

const row = (from: string, to: string, value: string, unlimited = false): RangingFormRow => ({ from, to, value, unlimited })

describe('contract editing: numbers and dates', () => {
  it('the default valid-from day is the 1st of the next month', () => {
    expect(firstOfNextMonth(new Date(2026, 10, 16))).toBe('2026-12-01')
    expect(firstOfNextMonth(new Date(2026, 11, 31))).toBe('2027-01-01')
    expect(firstOfNextMonth(new Date(2026, 0, 31))).toBe('2026-02-01')
  })

  it('formats numbers for inputs without grouping, decimal comma in German', () => {
    expect(inputNumber(0.28, 'de')).toBe('0,28')
    expect(inputNumber(0.28, 'en')).toBe('0.28')
    expect(inputNumber(12345.5, 'de')).toBe('12345,5')
    expect(inputNumber(0.1 + 0.2, 'en')).toBe('0.3')
    expect(inputNumber(null, 'de')).toBe('')
    expect(inputNumber(Number.NaN, 'de')).toBe('')
  })

  it('checks days', () => {
    expect(isDay('2027-01-01')).toBe(true)
    expect(isDay('2027-02-30')).toBe(false)
    expect(isDay('01.01.2027')).toBe(false)
    expect(isDay('')).toBe(false)
  })

  it('checks a new value: "," and "." accepted, integers for LONG, a valid day', () => {
    expect(checkNewValue('0,2875', '2027-01-01', 'DOUBLE')).toEqual({ value: 0.2875, valueProblem: null, dateProblem: null })
    expect(checkNewValue('1.234,5', '2027-01-01', 'DOUBLE').value).toBe(1234.5)
    expect(checkNewValue('0.31', '2027-01-01', 'DOUBLE').value).toBe(0.31)
    expect(checkNewValue('', '2027-01-01', 'DOUBLE').valueProblem?.key).toBe('contractEdit.problem.valueRequired')
    expect(checkNewValue('abc', '2027-01-01', 'DOUBLE').valueProblem?.key).toBe('registerEdit.problem.number')
    expect(checkNewValue('1,5', '2027-01-01', 'LONG').valueProblem?.key).toBe('registerEdit.problem.integer')
    expect(checkNewValue('15', '2027-01-01', 'LONG').value).toBe(15)
    expect(checkNewValue('1', '', 'DOUBLE').dateProblem?.key).toBe('contractEdit.problem.date')
  })
})

describe('contract editing: ranging tables', () => {
  it('builds sorted rows; an open or 1e9 end is "unbegrenzt"', () => {
    expect(
      rangingFormRows(
        [
          { from: 100, to: UNLIMITED, value: 10 },
          { from: 0, to: 100, value: 12.5 },
        ],
        'de',
      ),
    ).toEqual([row('0', '100', '12,5'), row('100', '', '10', true)])
    expect(rangingFormRows([{ from: 0, to: null, value: 1 }], 'en')).toEqual([row('0', '', '1', true)])
    expect(rangingFormRows(null, 'de')).toEqual([])
  })

  it('adds a row after the last one and moves "unbegrenzt" to it', () => {
    expect(addRangingRow([])).toEqual([row('0', '', '', true)])
    expect(addRangingRow([row('0', '100', '12,5')])).toEqual([row('0', '100', '12,5'), row('100', '', '')])
    expect(addRangingRow([row('0', '', '12,5', true)])).toEqual([row('0', '', '12,5'), row('', '', '', true)])
  })

  it('removes a row; "unbegrenzt" of the last row goes to the new last row', () => {
    const rows = [row('0', '100', '1'), row('100', '', '2', true)]
    expect(removeRangingRow(rows, 1)).toEqual([row('0', '', '1', true)])
    expect(removeRangingRow(rows, 0)).toEqual([row('100', '', '2', true)])
  })

  it('accepts a valid table with "unbegrenzt" as 1e9', () => {
    const check = checkRanging([row('0', '100', '12,5'), row('100', '', '10', true)])
    expect(check.problems).toEqual([])
    expect(check.steps).toEqual([
      { from: 0, to: 100, value: 12.5 },
      { from: 100, to: 1e9, value: 10 },
    ])
  })

  it('checks as the server: numbers, from < to, sorted, no overlap, 1–50 rows', () => {
    const keys = (rows: RangingFormRow[]) => checkRanging(rows).problems.map((p) => [p.row, p.field ?? null, p.key])
    expect(keys([])).toEqual([[null, null, 'contractEdit.ranging.problem.empty']])
    expect(keys([row('', '100', 'x')])).toEqual([
      [0, 'from', 'contractEdit.ranging.problem.required'],
      [0, 'value', 'registerEdit.problem.number'],
    ])
    expect(keys([row('100', '50', '1')])).toEqual([[0, 'to', 'contractEdit.ranging.problem.order']])
    expect(keys([row('0', '100', '1'), row('50', '200', '2')])).toEqual([[1, 'from', 'contractEdit.ranging.problem.overlap']])
    expect(keys([row('100', '200', '1'), row('0', '50', '2')])).toEqual([[1, 'from', 'contractEdit.ranging.problem.sorted']])
    const many = Array.from({ length: RANGING_MAX + 1 }, (_, i) => row(String(i), String(i + 1), '1'))
    expect(keys(many)).toEqual([[null, null, 'contractEdit.ranging.problem.tooMany']])
    expect(checkRanging(many).steps).toBeNull()
    // touching ranges are fine
    expect(keys([row('0', '100', '1'), row('100', '200', '2')])).toEqual([])
  })
})

describe('contract editing: the form', () => {
  const object = () =>
    contractObject(23901, {
      version: '2026-01-01T00:00:00.000+01:00',
      contractorClass: 'Energy Supply Contractor',
      canWrite: true,
    })

  it('holds name, contractor, texts and ranging rows; numbers are left to "Neuer Wert"', () => {
    const form = contractForm(object(), 'de')
    expect(form.name).toBe('Strom - Energieversorger Nord')
    expect(form.contractor).toBe(23910)
    expect(form.texts).toEqual({ 'Customer Number': 'K-4711' })
    expect(form.ranging['Demand Charge']).toEqual([row('0', '100', '12,5'), row('100', '', '10', true)])
  })

  it('leaves attributes that are not editable out of the form', () => {
    const o = object()
    o.attributes[2] = { ...o.attributes[2], editable: false }
    expect(contractForm(o, 'de').texts).toEqual({})
  })

  it('sends only the changed parts; nothing changed: no body', () => {
    const initial = contractForm(object(), 'de')
    expect(checkForm(initial, cloneForm(initial))).toMatchObject({ body: null, changed: false })

    const form = cloneForm(initial)
    form.texts['Customer Number'] = 'K-0001'
    form.contractor = null
    expect(checkForm(initial, form).body).toEqual({ contractor: null, fields: { 'Customer Number': 'K-0001' } })

    const renamed = cloneForm(initial)
    renamed.name = '  Strom Nord  '
    renamed.ranging['Demand Charge'] = [row('0', '50', '13'), row('50', '', '11', true)]
    expect(checkForm(initial, renamed).body).toEqual({
      name: 'Strom Nord',
      fields: {
        'Demand Charge': [
          { from: 0, to: 50, value: 13 },
          { from: 50, to: 1e9, value: 11 },
        ],
      },
    })
  })

  it('the same numbers written differently are no change', () => {
    const initial = contractForm(object(), 'de')
    const form = cloneForm(initial)
    form.ranging['Demand Charge'] = [row('0', '100', '12,50'), row('100', '', '10.0', true)]
    expect(checkForm(initial, form).changed).toBe(false)
  })

  it('reports name and ranging problems without a body', () => {
    const initial = contractForm(object(), 'de')
    const form = cloneForm(initial)
    form.name = ' '
    form.ranging['Demand Charge'] = [row('0', '100', '')]
    const check = checkForm(initial, form)
    expect(check.body).toBeNull()
    expect(check.nameProblem?.key).toBe('registerEdit.problem.nameRequired')
    expect(check.ranging['Demand Charge'][0].key).toBe('contractEdit.ranging.problem.required')
  })

  it('merges own changes into the server form', () => {
    const initial = contractForm(object(), 'de')
    const mine = cloneForm(initial)
    mine.texts['Customer Number'] = 'K-0002'
    const server = cloneForm(initial)
    server.name = 'Strom Nord (neu)'
    server.texts['Customer Number'] = 'K-9999'
    server.contractor = 23911
    expect(mergeForm(initial, mine, server)).toMatchObject({ name: 'Strom Nord (neu)', contractor: 23911, texts: { 'Customer Number': 'K-0002' } })
  })
})

describe('contract editing: rights and answers', () => {
  it('knows the rights', () => {
    expect(canEditObject({ canWrite: false, canDelete: true })).toBe(true)
    expect(canEditObject({})).toBe(false)
    const group = { canCreate: true, classes: [{ class: 'Gas Supply Contractor', label: 'Gaslieferant' }], createTargets: [{ id: 1, name: 'Verträge', path: null, site: null }] }
    expect(canCreateIn(group)).toBe(true)
    expect(canCreateIn({ ...group, createTargets: [] })).toBe(false)
    expect(anyContractWritable([{ kind: 'suppliers', objects: [contractObject(1)] }])).toBe(false)
    expect(anyContractWritable([{ kind: 'suppliers', objects: [contractObject(1, { canWrite: true })] }])).toBe(true)
    expect(anyContractWritable([{ kind: 'dues', objects: [], ...group }])).toBe(true)
  })

  it('keeps an empty group as tab when objects can be created in it', () => {
    const tabs = contractTabs({ groups: [{ kind: 'dues', objects: [], canCreate: true }, { kind: 'contractors', objects: [] }], configurations: [] })
    expect(tabs.map((t) => t.id)).toEqual(['dues'])
    expect(tabs[0].group?.canCreate).toBe(true)
  })

  it('reads the 409 answers: usedBy, warnings, the current object', () => {
    expect(usedByOf(httpError(409, { usedBy: [{ id: 5, name: 'Strom Musterwerk', kind: 'configuration' }, null] }))).toEqual([{ id: 5, name: 'Strom Musterwerk', kind: 'configuration' }])
    expect(usedByOf(httpError(409, { id: 5 }))).toBeNull()
    expect(usedByOf(httpError(400, { usedBy: [] }))).toBeNull()
    expect(warningsOf(httpError(409, { warnings: ['schon vorhanden'] }))).toEqual(['schon vorhanden'])
    expect(warningsOf(httpError(409, { warnings: [] }))).toBeNull()
    expect(warningsOf(new Error('x'))).toBeNull()
    const o = contractObject(7)
    expect(objectOf(o, 7)).toBe(o)
    expect(objectOf(o, 8)).toBeNull()
    expect(objectOf('x', 7)).toBeNull()
  })
})
