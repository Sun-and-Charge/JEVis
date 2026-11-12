/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import { HttpClient } from '../src/api/http'
import { createIsoItem, deleteAttachment, deleteIsoItem, MAX_ATTACHMENT_BYTES, updateIsoItem, uploadAttachment } from '../src/api/iso'
import {
  ACTION_FIELDS,
  anyWritable,
  changedFields,
  dateInput,
  dropItem,
  emptyValues,
  fieldOptions,
  isDoneStatus,
  itemLabel,
  itemValues,
  localToday,
  NONCONFORMITY_FIELDS,
  OBLIGATION_FIELDS,
  putItem,
  serverMessages,
  validateValues,
  withDoneDate,
} from '../src/iso/edit'
import { httpError } from './fixtures/edit'
import { action, actionPlan, legalPlan, nonconformity, nonconformityPlan, obligation } from './fixtures/iso'

const field = (defs: typeof ACTION_FIELDS, key: string) => defs.find((d) => d.key === key)!

describe('E2 API functions', () => {
  const http = () => {
    const h = { post: vi.fn(), put: vi.fn(), delete: vi.fn(), postForm: vi.fn() }
    h.post.mockResolvedValue({ id: 99 })
    h.put.mockResolvedValue({ id: 5 })
    h.delete.mockResolvedValue(undefined)
    h.postForm.mockResolvedValue({ id: 4711, name: 'angebot.pdf', size: 3 })
    return h
  }

  it('create posts the fields to the plan, update sends version and only the given fields', async () => {
    const h = http()
    await createIsoItem(h, 'actions', 7, { Title: 'Musterwerk Beleuchtung' })
    expect(h.post).toHaveBeenCalledWith('/web/actions/plans/7/items', { Title: 'Musterwerk Beleuchtung' })
    await updateIsoItem(h, 'nonconformities', 5, '2026-11-01T10:00:00+01:00', { Cause: 'x' })
    expect(h.put).toHaveBeenCalledWith('/web/nonconformities/items/5', { version: '2026-11-01T10:00:00+01:00', fields: { Cause: 'x' } })
  })

  it('delete sends the version as query, attachments go to their own endpoints', async () => {
    const h = http()
    await deleteIsoItem(h, 'obligations', 12, '2026-11-01T10:00:00+01:00')
    expect(h.delete).toHaveBeenCalledWith('/web/obligations/items/12?version=2026-11-01T10%3A00%3A00%2B01%3A00')
    const file = new File(['abc'], 'angebot.pdf', { type: 'application/pdf' })
    expect(await uploadAttachment(h, 5, file)).toEqual({ id: 4711, name: 'angebot.pdf', size: 3 })
    const [path, form] = h.postForm.mock.calls[0] as unknown as [string, FormData]
    expect(path).toBe('/web/items/5/attachments')
    expect((form.get('file') as File).name).toBe('angebot.pdf')
    await deleteAttachment(h, 4711)
    expect(h.delete).toHaveBeenLastCalledWith('/web/attachments/4711')
    expect(MAX_ATTACHMENT_BYTES).toBe(25 * 1024 * 1024)
  })

  it('postForm sends the form data with the Bearer token and without a JSON content type', async () => {
    const fetchFn = vi.fn(async () => new Response('{"id":1}', { status: 201 }))
    const client = new HttpClient({ accessToken: () => 'tok', refresh: async () => false, onUnauthorized: () => {} }, fetchFn as unknown as typeof fetch)
    const form = new FormData()
    form.append('file', new File(['x'], 'a.txt'))
    expect(await client.postForm('/web/items/1/attachments', form)).toEqual({ id: 1 })
    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit & { headers: Record<string, string> }]
    expect(url).toBe('/JEWebService/v1/web/items/1/attachments')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(form)
    expect(init.headers.Authorization).toBe('Bearer tok')
    expect(init.headers['Content-Type']).toBeUndefined()
  })
})

describe('E2 form helpers', () => {
  it('reads form values from a list item (dates as written, tags split, check list by stored key)', () => {
    const a = action(3, {
      title: 'Musterwerk Druckluft',
      plannedDate: '2026-06-30T00:00:00.000+02:00',
      field: 'Area 1;Area 2',
      seu: null,
      checkList: { processDocument: true, newEnPI: 'kWh je Stück', drawing: false },
    })
    const v = itemValues(ACTION_FIELDS, a)
    expect(v.Title).toBe('Musterwerk Druckluft')
    expect(v['Planned Date']).toBe('2026-06-30')
    expect(v['Done Date']).toBe('')
    expect(v['Field Tags']).toEqual(['Area 1', 'Area 2'])
    expect(v['SEU Tags']).toEqual([])
    expect(v['Medium Tags']).toBe('Power')
    expect(v['Check List']).toMatchObject({ 'Process Document': true, Drawing: false, isNeedWorkInstruction: false })
    expect(Object.keys(v['Check List'] as object)).not.toContain('New EnPI')
    expect(dateInput('kein Datum')).toBe('')
  })

  it('sends only changed fields: texts trimmed, empty dates null, lists ignoring order, the whole check list', () => {
    const initial = itemValues(ACTION_FIELDS, action(3, { field: 'Area 1;Area 2', checkList: { processDocument: true } }))
    const current = {
      ...initial,
      Title: '  Action 3  ',
      Description: ' Neu ',
      'Planned Date': '',
      'Field Tags': ['Area 2', 'Area 1'],
      'Check List': { ...(initial['Check List'] as object), Drawing: true },
    }
    const fields = changedFields(ACTION_FIELDS, initial, current)
    expect(Object.keys(fields).sort()).toEqual(['Check List', 'Description', 'Planned Date'])
    expect(fields.Description).toBe('Neu')
    expect(fields['Planned Date']).toBeNull()
    expect(fields['Check List']).toMatchObject({ 'Process Document': true, Drawing: true, Other: false })
    expect(changedFields(ACTION_FIELDS, initial, initial)).toEqual({})
  })

  it('a new item sends what was filled in', () => {
    const empty = emptyValues(NONCONFORMITY_FIELDS)
    const fields = changedFields(NONCONFORMITY_FIELDS, empty, { ...empty, Title: 'Musterwerk Leck', Medium: 'Gas', 'Field Tags': ['Area 1'] })
    expect(fields).toEqual({ Title: 'Musterwerk Leck', Medium: 'Gas', 'Field Tags': ['Area 1'] })
    const ob = emptyValues(OBLIGATION_FIELDS)
    expect(changedFields(OBLIGATION_FIELDS, ob, { ...ob, title: 'Verordnung', 'Relevance 50001': true })).toEqual({ title: 'Verordnung', 'Relevance 50001': true })
  })

  it('keeps old values that left the plan list as long as the field is unchanged', () => {
    const plan = actionPlan(1, [])
    const def = field(ACTION_FIELDS, 'Status Tags')
    const initial = itemValues(ACTION_FIELDS, action(1, { status: 'Alt', field: 'Area 1;Altbereich' }))
    expect(fieldOptions(def, plan, initial['Status Tags']).map((o) => [o.value, !!o.stale])).toEqual([
      ['Open', false],
      ['Done', false],
      ['Alt', true],
    ])
    // unchanged: no problem
    expect(validateValues(ACTION_FIELDS, plan, initial, initial)).toEqual({})
    // changed list field that still holds an old value: must be removed first
    const changed = { ...initial, 'Field Tags': ['Area 1', 'Altbereich', 'Area 2'] }
    expect(validateValues(ACTION_FIELDS, plan, initial, changed)).toEqual({ 'Field Tags': { key: 'isoEdit.notInList', params: { value: 'Altbereich' } } })
    // medium by id
    const medium = { ...initial, 'Medium Tags': 'Fernwärme' }
    expect(validateValues(ACTION_FIELDS, plan, initial, medium)['Medium Tags']?.key).toBe('isoEdit.notInList')
  })

  it('checks required fields and links', () => {
    const plan = legalPlan(1, [])
    const empty = emptyValues(OBLIGATION_FIELDS)
    expect(validateValues(OBLIGATION_FIELDS, plan, empty, empty)).toEqual({ title: { key: 'isoEdit.required' } })
    const bad = { ...empty, title: 'Verordnung', link: 'ftp://example.org' }
    expect(validateValues(OBLIGATION_FIELDS, plan, empty, bad)).toEqual({ link: { key: 'isoEdit.linkInvalid' } })
    expect(validateValues(OBLIGATION_FIELDS, plan, empty, { ...bad, link: 'https://example.org/vo' })).toEqual({})
    // unknown category / scope of the plan
    expect(validateValues(OBLIGATION_FIELDS, plan, empty, { ...empty, title: 'x', category: 'Andere' }).category?.key).toBe('isoEdit.notInList')
    expect(validateValues(NONCONFORMITY_FIELDS, nonconformityPlan(1, []), emptyValues(NONCONFORMITY_FIELDS), { ...emptyValues(NONCONFORMITY_FIELDS), Title: 'x', Medium: 'Power' })).toEqual({})
  })

  it('a done status sets the done date to today if it is empty', () => {
    expect(isDoneStatus('Erledigt')).toBe(true)
    expect(isDoneStatus(' done ')).toBe(true)
    expect(isDoneStatus('Offen')).toBe(false)
    const values = emptyValues(ACTION_FIELDS)
    expect(withDoneDate({ ...values, 'Status Tags': 'Done' }, '2026-11-12')?.['Done Date']).toBe('2026-11-12')
    expect(withDoneDate({ ...values, 'Status Tags': 'Done', 'Done Date': '2026-10-01' }, '2026-11-12')).toBeNull()
    expect(withDoneDate({ ...values, 'Status Tags': 'Open' }, '2026-11-12')).toBeNull()
    expect(localToday(new Date(2026, 0, 5))).toBe('2026-01-05')
  })

  it('reads the messages of a 400 answer (text lines, JSON list or object)', () => {
    expect(serverMessages(httpError(400, 'Sofortmaßnahmen fehlen\n- Abschlussdatum in der Zukunft\n'))).toEqual(['Sofortmaßnahmen fehlen', 'Abschlussdatum in der Zukunft'])
    expect(serverMessages(httpError(400, ['a', 'b']))).toEqual(['a', 'b'])
    expect(serverMessages(httpError(400, { messages: ['c'] }))).toEqual(['c'])
    expect(serverMessages(httpError(400, '<html>'))).toEqual([])
    expect(serverMessages(httpError(403, 'nein'))).toEqual([])
  })

  it('updates the lists in place: replace, add to the plan, remove; labels and rights', () => {
    const list = { today: '2026-11-12', plans: [actionPlan(1, [action(1), action(2)]), actionPlan(2, [action(3)])] }
    const replaced = putItem(list, 'actions', 1, action(2, { title: 'Neu' }))
    expect(replaced.plans[0].actions.map((a) => a.title)).toEqual(['Action 1', 'Neu'])
    expect(replaced.today).toBe('2026-11-12')
    const added = putItem(list, 'actions', 2, action(9))
    expect(added.plans[1].actions.map((a) => a.id)).toEqual([3, 9])
    expect(dropItem(list, 'actions', 1).plans[0].actions.map((a) => a.id)).toEqual([2])
    const nc = { plans: [nonconformityPlan(1, [nonconformity(4)])] }
    expect(dropItem(nc, 'nonconformities', 4).plans[0].items).toEqual([])
    expect(itemLabel('obligations', obligation(12, { title: 'Verordnung' }))).toBe('12 – Verordnung')
    expect(itemLabel('actions', action(4, { title: 'Pumpe' }))).toBe('AP 004 – Pumpe')
    expect(anyWritable('actions', list.plans)).toBe(false)
    expect(anyWritable('actions', [actionPlan(1, [action(1, { canAttach: true })])])).toBe(true)
    expect(anyWritable('obligations', [legalPlan(1, [], { canCreate: true })])).toBe(true)
  })
})
