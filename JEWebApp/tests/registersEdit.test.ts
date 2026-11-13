/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import {
  createRegisterObject,
  deleteRegisterObject,
  loadEquipmentCreateOptions,
  loadMeterCreateOptions,
  saveMeterExchange,
  updateRegisterObject,
  uploadRegisterFile,
} from '../src/api/registers'
import type { HttpClient } from '../src/api/http'
import { fromDateTimeInput } from '../src/edit/edit'
import {
  anyRegisterWritable,
  changedKeys,
  changedRegisterFields,
  checkExchange,
  cloneFormValues,
  fieldKind,
  formValue,
  latestVersion,
  mergeAfterConflict,
  nameProblem,
  numberInput,
  registerFieldDefs,
  requestValue,
  rowFormValues,
  rowOf,
  sameFormValue,
  validateRegisterValues,
  warningsOf,
  withUploadedFile,
} from '../src/registers/edit'
import { column, group, meter } from './fixtures/registers'

/* Field model and conversions of the meter / equipment editor (E5); neutral placeholder data. */

const V1 = '2026-11-01T10:00:00.000+01:00'
const V2 = '2026-11-05T08:30:00.000+01:00'

const columns = [
  column('Serial Number', { name: 'Seriennummer', primitiveType: 'STRING', guiType: 'BASIC_TEXT', editable: true }),
  column('Remark', { name: 'Bemerkung', type: 'TEXTAREA', primitiveType: 'STRING', guiType: 'BASIC_TEXT_MULTI' }),
  column('Factor', { name: 'Faktor', type: 'NUMBER', primitiveType: 'DOUBLE', inputUnit: 'kWh/Imp' }),
  column('Decimal Places', { name: 'Nachkommastellen', type: 'INTEGER', primitiveType: 'LONG' }),
  column('Calibrated', { name: 'Geeicht', type: 'BOOLEAN', primitiveType: 'BOOLEAN' }),
  column('Installation Date', { name: 'Einbaudatum', type: 'DATE', primitiveType: 'STRING', guiType: 'BASIC_TEXT_DATE_FULL' }),
  column('Online ID', { name: 'Datenpunkt', type: 'TARGET', primitiveType: 'TARGET', guiType: 'TARGET_OBJECT' }),
  column('Picture', { name: 'Bild', type: 'FILE', primitiveType: 'FILE' }),
  column('Password', { name: 'Passwort', primitiveType: 'PASSWORD_PBKDF2', editable: false }),
  column('Location GPS', { name: 'GPS', primitiveType: 'STRING', guiType: 'GPS', editable: false }),
]

const row = meter(5101, {
  values: {
    'Serial Number': 'SN-0001',
    Remark: 'Zeile 1\nZeile 2',
    Factor: 1.5,
    'Decimal Places': 2,
    Calibrated: true,
    'Installation Date': '2015-10-01',
    'Online ID': { id: 2001, attribute: 'Value', name: 'Musterwerk Strom', readable: true },
    Picture: { file: 'meter.jpg', image: true },
    Password: null,
    'Location GPS': '52.1,8.2',
  },
  versions: { 'Serial Number': V1, Remark: V1, Factor: V2, 'Decimal Places': null, Calibrated: V1, 'Installation Date': V1, 'Online ID': V1, Picture: V1 },
  inputUnits: { Factor: 'Wh/Imp' },
})

describe('field model', () => {
  it('maps column type, primitive type and GUI type to a field kind', () => {
    const kinds = registerFieldDefs(columns).map((d) => [d.key, d.kind])
    expect(kinds).toEqual([
      ['Serial Number', 'text'],
      ['Remark', 'textarea'],
      ['Factor', 'number'],
      ['Decimal Places', 'integer'],
      ['Calibrated', 'boolean'],
      ['Installation Date', 'date'],
      ['Online ID', 'target'],
      ['Picture', 'file'],
      ['Password', 'readonly'],
      ['Location GPS', 'readonly'],
    ])
  })

  it('recognises kinds without the E5 fields (W4.3 columns) and the read-only types', () => {
    expect(fieldKind(column('a', { type: 'NUMBER' }))).toBe('number')
    expect(fieldKind(column('a', { type: 'INTEGER' }))).toBe('integer')
    expect(fieldKind(column('a', { type: 'DATETIME' }))).toBe('date')
    expect(fieldKind(column('a', { type: 'TEXTAREA' }))).toBe('textarea')
    expect(fieldKind(column('a', { type: 'TEXT' }))).toBe('text')
    expect(fieldKind(column('a', { type: 'TEXT', primitiveType: 'STRING', guiType: 'BASIC_TEXT_MULTI' }))).toBe('textarea')
    expect(fieldKind(column('a', { type: 'TEXT', primitiveType: 'TARGET', guiType: 'TARGET_ATTRIBUTE' }))).toBe('target')
    expect(fieldKind(column('a', { type: 'TEXT', primitiveType: 'SELECTION' }))).toBe('readonly')
    expect(fieldKind(column('a', { type: 'TEXT', primitiveType: 'MULTI_SELECTION' }))).toBe('readonly')
    expect(fieldKind(column('a', { type: 'TEXT', primitiveType: 'STRING', editable: false }))).toBe('readonly')
    expect(fieldKind(column('a', { type: 'TEXT', primitiveType: 'SOMETHING_NEW' }))).toBe('readonly')
    /* files stay files (shown with download / upload) even when not editable as a value */
    // GPS (a FILE attribute with a map editor in the CC) and other non-editable files are read-only, no upload
    expect(fieldKind(column('a', { type: 'FILE', editable: false }))).toBe('readonly')
    expect(fieldKind(column('a', { type: 'FILE', primitiveType: 'FILE', guiType: 'GPS', editable: false }))).toBe('readonly')
  })

  it('uses the row input unit over the column one', () => {
    const defs = registerFieldDefs(columns, row)
    expect(defs.find((d) => d.key === 'Factor')!.inputUnit).toBe('Wh/Imp')
    expect(registerFieldDefs(columns).find((d) => d.key === 'Factor')!.inputUnit).toBe('kWh/Imp')
    expect(defs.find((d) => d.key === 'Serial Number')!.inputUnit).toBeNull()
  })
})

describe('value conversion', () => {
  const defs = registerFieldDefs(columns, row)
  const def = (key: string) => defs.find((d) => d.key === key)!

  it('reads form values from a row (German decimal comma, dates, targets)', () => {
    const values = rowFormValues(defs, row, 'de')
    expect(values).toEqual({
      'Serial Number': 'SN-0001',
      Remark: 'Zeile 1\nZeile 2',
      Factor: '1,5',
      'Decimal Places': '2',
      Calibrated: true,
      'Installation Date': '2015-10-01',
      'Online ID': { id: 2001, attribute: 'Value', name: 'Musterwerk Strom', readable: true },
      Picture: null,
      Password: null,
      'Location GPS': null,
    })
    expect(rowFormValues(defs, row, 'en').Factor).toBe('1.5')
    expect(formValue(def('Installation Date'), '2015-10-01T00:00:00.000Z', 'de')).toBe('2015-10-01')
    expect(formValue(def('Calibrated'), 'true', 'de')).toBe(true)
    expect(formValue(def('Calibrated'), null, 'de')).toBe(false)
    expect(formValue(def('Online ID'), null, 'de')).toBeNull()
    expect(numberInput(1234567.25, 'de')).toBe('1234567,25')
  })

  it('converts form values to request values', () => {
    expect(requestValue(def('Serial Number'), '  SN-2 ', 'de')).toBe('SN-2')
    expect(requestValue(def('Serial Number'), '', 'de')).toBe('')
    expect(requestValue(def('Remark'), 'a\n b ', 'de')).toBe('a\n b ')
    expect(requestValue(def('Factor'), '1.234,5', 'de')).toBe(1234.5)
    expect(requestValue(def('Factor'), '0,25', 'de')).toBe(0.25)
    expect(requestValue(def('Factor'), '1,234.5', 'en')).toBe(1234.5)
    expect(requestValue(def('Factor'), '', 'de')).toBeNull()
    expect(requestValue(def('Factor'), 'abc', 'de')).toBeUndefined()
    expect(requestValue(def('Decimal Places'), '3', 'de')).toBe(3)
    expect(requestValue(def('Decimal Places'), '3,5', 'de')).toBeUndefined()
    expect(requestValue(def('Calibrated'), false, 'de')).toBe(false)
    expect(requestValue(def('Installation Date'), '2026-11-13', 'de')).toBe('2026-11-13')
    expect(requestValue(def('Installation Date'), '', 'de')).toBeNull()
    expect(requestValue(def('Online ID'), { id: 3001, attribute: 'Value', name: 'x' }, 'de')).toEqual({ id: 3001, attribute: 'Value' })
    expect(requestValue(def('Online ID'), { id: 3001, attribute: null, name: 'x' }, 'de')).toEqual({ id: 3001, attribute: 'Value' })
    expect(requestValue(def('Online ID'), null, 'de')).toBeNull()
  })

  it('compares numbers by value and targets by id and attribute', () => {
    expect(sameFormValue(def('Factor'), '1,5', '1,50', 'de')).toBe(true)
    expect(sameFormValue(def('Factor'), '1,5', '1,6', 'de')).toBe(false)
    expect(sameFormValue(def('Online ID'), { id: 1, attribute: null, name: 'a' }, { id: 1, attribute: 'Value', name: 'b' }, 'de')).toBe(true)
    expect(sameFormValue(def('Online ID'), { id: 1, attribute: 'Value', name: 'a' }, null, 'de')).toBe(false)
    expect(sameFormValue(def('Serial Number'), 'a ', 'a', 'de')).toBe(true)
    expect(sameFormValue(def('Remark'), 'a ', 'a', 'de')).toBe(false)
  })
})

describe('changed fields and versions', () => {
  const defs = registerFieldDefs(columns, row)

  it('sends only the changed attributes, each with the version it was loaded with', () => {
    const initial = rowFormValues(defs, row, 'de')
    const current = cloneFormValues(initial)
    expect(changedRegisterFields(defs, initial, current, row.versions, 'de')).toEqual({})
    current.Factor = '2,75'
    current['Decimal Places'] = '3'
    current['Online ID'] = { id: 3001, attribute: 'Value', name: 'Musterwerk Gas' }
    current['Installation Date'] = ''
    current['Serial Number'] = 'SN-0001 '
    expect(changedKeys(defs, initial, current, 'de')).toEqual(['Factor', 'Decimal Places', 'Installation Date', 'Online ID'])
    expect(changedRegisterFields(defs, initial, current, row.versions, 'de')).toEqual({
      Factor: { version: V2, value: 2.75 },
      'Decimal Places': { version: null, value: 3 },
      'Installation Date': { version: V1, value: null },
      'Online ID': { version: V1, value: { id: 3001, attribute: 'Value' } },
    })
    /* the original values are not shared */
    expect(initial['Online ID']).toEqual({ id: 2001, attribute: 'Value', name: 'Musterwerk Strom', readable: true })
  })

  it('a new object sends the filled fields without versions; files and read-only fields never', () => {
    const initial = rowFormValues(defs, null, 'de')
    expect(initial.Factor).toBe('')
    expect(initial.Calibrated).toBe(false)
    const current = { ...cloneFormValues(initial), 'Serial Number': 'SN-9', Calibrated: true, Password: 'secret' }
    expect(changedRegisterFields(defs, initial, current, null, 'de')).toEqual({
      'Serial Number': { version: null, value: 'SN-9' },
      Calibrated: { version: null, value: true },
    })
  })

  it('checks changed numbers only', () => {
    const initial = rowFormValues(defs, row, 'de')
    const current = { ...cloneFormValues(initial), Factor: '1,2,3', 'Decimal Places': '2,5' }
    expect(validateRegisterValues(defs, initial, current, 'de')).toEqual({
      Factor: { key: 'registerEdit.problem.number' },
      'Decimal Places': { key: 'registerEdit.problem.integer' },
    })
    expect(validateRegisterValues(defs, initial, { ...current, Factor: '', 'Decimal Places': '4' }, 'de')).toEqual({})
  })

  it('after a conflict keeps own changes and takes the other fields from the server', () => {
    const initial = rowFormValues(defs, row, 'de')
    const current = { ...cloneFormValues(initial), Factor: '9' }
    const server = { ...cloneFormValues(initial), 'Serial Number': 'SN-NEU', Factor: '1,7' }
    const merged = mergeAfterConflict(defs, initial, current, server, 'de')
    expect(merged.Factor).toBe('9')
    expect(merged['Serial Number']).toBe('SN-NEU')
    expect(changedKeys(defs, server, merged, 'de')).toEqual(['Factor'])
  })

  it('latest version, name rule, rights, uploads, answers', () => {
    expect(latestVersion(row)).toBe(V2)
    expect(latestVersion({ versions: { a: null } })).toBeNull()
    expect(latestVersion({})).toBeNull()
    expect(nameProblem('  ')).toEqual({ key: 'registerEdit.problem.nameRequired' })
    expect(nameProblem('x'.repeat(101))).toEqual({ key: 'registerEdit.problem.nameLength', params: { max: 100 } })
    expect(nameProblem('a\tb')).toEqual({ key: 'registerEdit.problem.nameChars' })
    expect(nameProblem(' Zähler 1 ')).toBeNull()
    expect(anyRegisterWritable([group('1', [meter(1)])])).toBe(false)
    expect(anyRegisterWritable([group('1', [meter(1, { canWrite: true })])])).toBe(true)
    expect(anyRegisterWritable([group('1', [], { canCreate: true })])).toBe(true)
    const up = withUploadedFile(row, 'Picture', { file: 'neu.png', image: true }, V2)
    expect(up.values.Picture).toEqual({ file: 'neu.png', image: true })
    expect(up.versions!.Picture).toBe(V2)
    expect(row.values.Picture).toEqual({ file: 'meter.jpg', image: true })
    expect(rowOf(row, 5101)).toBe(row)
    expect(rowOf(row, 1)).toBeNull()
    expect(rowOf('x', 1)).toBeNull()
    expect(warningsOf({ warnings: ['a', 3, ''] })).toEqual(['a'])
    expect(warningsOf(null)).toEqual([])
  })
})

describe('meter exchange checks', () => {
  const now = new Date(2026, 10, 13, 12, 0).getTime()

  it('accepts a past time and readings with decimal comma', () => {
    const r = checkExchange({ when: '2026-11-13T09:30', oldReading: '12.345,6', newReading: '0' }, 'de', now, fromDateTimeInput)
    expect(r.values).toEqual({ timestamp: new Date(2026, 10, 13, 9, 30).getTime(), oldReading: 12345.6, newReading: 0 })
  })

  it('rejects the future, missing or negative readings', () => {
    const r = checkExchange({ when: '2026-11-13T12:01', oldReading: '', newReading: '-1' }, 'de', now, fromDateTimeInput)
    expect(r.values).toBeNull()
    expect(r.problems).toEqual({
      when: { key: 'registerEdit.exchange.problem.future' },
      oldReading: { key: 'registerEdit.exchange.problem.reading' },
      newReading: { key: 'registerEdit.exchange.problem.reading' },
    })
    expect(checkExchange({ when: '', oldReading: '1', newReading: '2' }, 'de', now, fromDateTimeInput).problems.when).toEqual({ key: 'registerEdit.exchange.problem.time' })
  })
})

describe('E5 requests', () => {
  it('builds the paths and bodies of the contract', async () => {
    const mocks = {
      get: vi.fn(async (_path: string) => ({})),
      put: vi.fn(async (_path: string, _body: unknown) => ({})),
      post: vi.fn(async (_path: string, _body: unknown) => ({})),
      delete: vi.fn(async (_path: string) => undefined),
      postForm: vi.fn(async (_path: string, _form: FormData) => ({})),
    }
    const http = mocks as unknown as HttpClient
    await updateRegisterObject(http, 5101, { fields: { Factor: { version: V1, value: 2 } }, name: 'Neu' }, 'de')
    expect(mocks.put).toHaveBeenCalledWith('/web/registers/objects/5101?lang=de', { fields: { Factor: { version: V1, value: 2 } }, name: 'Neu' })
    await loadMeterCreateOptions(http, '5001', 'de')
    expect(mocks.get).toHaveBeenLastCalledWith('/web/registers/meters/create-options?groupKey=5001&lang=de')
    await loadEquipmentCreateOptions(http, 'Heat Pump', 'en')
    expect(mocks.get).toHaveBeenLastCalledWith('/web/registers/equipment/create-options?class=Heat%20Pump&lang=en')
    await createRegisterObject(http, 'meters', { groupKey: '5001', class: 'Gas Measurement Instrument', name: 'G1', parentId: null, fields: {} }, 'de')
    expect(mocks.post).toHaveBeenLastCalledWith('/web/registers/meters/objects?lang=de', { groupKey: '5001', class: 'Gas Measurement Instrument', name: 'G1', parentId: null, fields: {} })
    await deleteRegisterObject(http, 5101, V1)
    expect(mocks.delete).toHaveBeenCalledWith('/web/registers/objects/5101?version=' + encodeURIComponent(V1))
    const file = new File(['x'], 'bild.png', { type: 'image/png' })
    await uploadRegisterFile(http, 5101, 'Verification Documentation', file)
    expect(mocks.postForm.mock.calls[0][0]).toBe('/web/registers/objects/5101/files/Verification%20Documentation')
    expect(mocks.postForm.mock.calls[0][1]).toBeInstanceOf(FormData)
    await saveMeterExchange(http, 5101, { timestamp: '2026-11-13T09:30:00+01:00', oldReading: 1, newReading: 0, confirm: false }, 'de')
    expect(mocks.post).toHaveBeenLastCalledWith('/web/registers/meters/5101/exchange?lang=de', { timestamp: '2026-11-13T09:30:00+01:00', oldReading: 1, newReading: 0, confirm: false })
  })
})
