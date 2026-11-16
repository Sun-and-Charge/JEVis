/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import {
  canCreateConfiguration,
  canEditConfiguration,
  carrySelections,
  checkConfiguration,
  cloneConfigurationForm,
  configurationChanges,
  configurationForm,
  conflictModelOf,
  editModelOf,
  hasConfigurationChanges,
  inputOptionLabel,
  inputTitle,
  mergeConfigurationForm,
  unassignedKeys,
  warningsOfModel,
} from '../src/accounting/configurationEdit'
import { contractTabs, CONFIGURATIONS_TAB } from '../src/accounting/contracts'
import {
  deleteConfigurationPath,
  loadTemplateInputs,
  loadTemplates,
  templateInputsPath,
  type ConfigurationEdit,
  type ConfigurationInput,
} from '../src/api/contracts'
import { httpError } from './fixtures/edit'

/* Pure logic of the accounting configuration editor (E6.3); neutral placeholder data. */

function input(key: string, overrides: Partial<ConfigurationInput> = {}): ConfigurationInput {
  return {
    key,
    variableNames: [key],
    objectClass: 'Clean Data',
    options: [
      { id: 501, name: 'Bereinigte Daten A', path: 'Musterwerk / Strom' },
      { id: 502, name: 'Bereinigte Daten B', path: 'Musterwerk / Strom' },
    ],
    selected: null,
    ...overrides,
  }
}

function model(overrides: Partial<ConfigurationEdit> = {}): ConfigurationEdit {
  return {
    id: 23950,
    name: 'Strom Musterwerk',
    version: '2026-11-01T10:00:00.000+01:00',
    parentId: 600,
    contract: {
      contractNumber: 'V-1001',
      contractType: 'ELECTRICITY',
      marketLocation: 'DE0000000000000000000000000000001',
      contractDate: '2025-12-01',
      firstRate: '2026-01-01',
      periodOfNotice: '2027-09-30',
      start: '2026-01-01',
      end: '2027-12-31',
    },
    template: { id: 23960, name: 'Stromkosten', readable: true },
    inputs: [input('i1', { selected: 501 }), input('class:Energy Supply Contractor', { objectClass: 'Energy Supply Contractor', options: [], selected: null })],
    ...overrides,
  }
}

describe('configuration editing: rights and tabs', () => {
  it('offers editing with a write or delete right and "Neu" with a directory', () => {
    expect(canEditConfiguration({ canWrite: true })).toBe(true)
    expect(canEditConfiguration({ canDelete: true })).toBe(true)
    expect(canEditConfiguration({})).toBe(false)
    const target = { id: 610, name: 'Abrechnung', path: null, site: null }
    expect(canCreateConfiguration({ canCreateConfiguration: true, configurationTargets: [target] })).toBe(true)
    expect(canCreateConfiguration({ configurationTargets: [target] })).toBe(true)
    expect(canCreateConfiguration({ canCreateConfiguration: false, configurationTargets: [target] })).toBe(false)
    expect(canCreateConfiguration({ canCreateConfiguration: true, configurationTargets: [] })).toBe(false)
    expect(canCreateConfiguration(null)).toBe(false)
  })

  it('lists the configurations tab without configurations when they can be created', () => {
    const target = { id: 610, name: 'Abrechnung', path: null, site: null }
    expect(contractTabs({ groups: [], configurations: [] })).toEqual([])
    expect(contractTabs({ groups: [], configurations: [], canCreateConfiguration: true, configurationTargets: [target] }).map((t) => t.id)).toEqual([CONFIGURATIONS_TAB])
  })
})

describe('configuration editing: form and checks', () => {
  it('builds the form from the editor model', () => {
    const f = configurationForm(model())
    expect(f.name).toBe('Strom Musterwerk')
    expect(f.contract.contractType).toBe('ELECTRICITY')
    expect(f.contract.end).toBe('2027-12-31')
    expect(f.templateId).toBe(23960)
    expect(f.selections).toEqual({ i1: 501, 'class:Energy Supply Contractor': null })
    const empty = configurationForm(null)
    expect(empty.templateId).toBeNull()
    expect(Object.values(empty.contract).every((v) => v === '')).toBe(true)
  })

  it('detects changes of name, fields, template and selections', () => {
    const initial = configurationForm(model())
    const current = cloneConfigurationForm(initial)
    expect(hasConfigurationChanges(configurationChanges(initial, current))).toBe(false)
    current.contract.contractNumber = 'V-1002'
    current.selections.i1 = 502
    const c = configurationChanges(initial, current)
    expect(c.contract).toEqual(['contractNumber'])
    expect(c.selections).toEqual(['i1'])
    expect(c.name).toBe(false)
    expect(c.template).toBe(false)
  })

  it('builds the whole body: empty fields as null, a selection per selector, the name only when changed', () => {
    const m = model()
    const f = configurationForm(m)
    f.contract.marketLocation = '  '
    f.contract.periodOfNotice = ''
    const check = checkConfiguration(f, m.inputs, { nameChanged: false, templateReadable: true })
    expect(check.problems).toEqual({})
    expect(check.body).toEqual({
      contract: {
        contractNumber: 'V-1001',
        marketLocation: null,
        contractType: 'ELECTRICITY',
        contractDate: '2025-12-01',
        firstRate: '2026-01-01',
        periodOfNotice: null,
        start: '2026-01-01',
        end: '2027-12-31',
      },
      templateId: 23960,
      selections: { i1: 501, 'class:Energy Supply Contractor': null },
    })
    f.name = '  Strom Neu '
    expect(checkConfiguration(f, m.inputs, { nameChanged: true, templateReadable: true }).body?.name).toBe('Strom Neu')
  })

  it('checks dates, end not before start, texts, energy type, name and template like the server', () => {
    const m = model()
    const f = configurationForm(m)
    f.contract.start = '2027-01-01'
    f.contract.end = '2026-12-31'
    f.contract.contractDate = '01.12.2025'
    f.contract.contractNumber = 'x'.repeat(201)
    f.contract.contractType = 'OTHER'
    f.name = ''
    const check = checkConfiguration(f, m.inputs, { nameChanged: true, templateReadable: true })
    expect(check.body).toBeNull()
    expect(check.problems.end.key).toBe('configEdit.problem.endBeforeStart')
    expect(check.problems.contractDate.key).toBe('configEdit.problem.date')
    expect(check.problems.contractNumber).toEqual({ key: 'configEdit.problem.textLength', params: { max: 200 } })
    expect(check.problems.contractType.key).toBe('configEdit.problem.contractType')
    expect(check.problems.name.key).toBe('registerEdit.problem.nameRequired')

    const same = configurationForm(m)
    same.contract.end = same.contract.start
    expect(checkConfiguration(same, m.inputs, { nameChanged: false, templateReadable: true }).problems).toEqual({})
    same.templateId = null
    expect(checkConfiguration(same, m.inputs, { nameChanged: false, templateReadable: true }).problems.template.key).toBe('configEdit.problem.template')
    same.templateId = 23960
    expect(checkConfiguration(same, m.inputs, { nameChanged: false, templateReadable: false }).problems.template.key).toBe('configEdit.problem.templateUnreadable')
  })

  it('lists the selectors without a selection', () => {
    const m = model()
    expect(unassignedKeys(m.inputs, configurationForm(m).selections)).toEqual(['class:Energy Supply Contractor'])
  })
})

describe('configuration editing: template change and merge', () => {
  it('keeps the selections whose key exists in the new template', () => {
    const before = [input('i1'), input('i2'), input('i3', { objectClass: 'Data' })]
    const selections = { i1: 501, i2: 502, i3: 777 }
    const after = [
      input('i1'),
      input('i3', { objectClass: 'Clean Data', options: [{ id: 501, name: 'A' }] }),
      input('i4', { selected: 502 }),
    ]
    expect(carrySelections(before, selections, after)).toEqual({ i1: 501, i3: null, i4: 502 })
  })

  it('"Weiter bearbeiten" keeps the own changes on the newer version', () => {
    const initial = configurationForm(model())
    const current = cloneConfigurationForm(initial)
    current.contract.contractNumber = 'V-2000'
    current.selections.i1 = 502
    const server = configurationForm(model({ name: 'Strom Musterwerk 2', contract: { ...model().contract, end: '2028-12-31' } }))
    const merged = mergeConfigurationForm(initial, current, server)
    expect(merged.name).toBe('Strom Musterwerk 2')
    expect(merged.contract.contractNumber).toBe('V-2000')
    expect(merged.contract.end).toBe('2028-12-31')
    expect(merged.selections.i1).toBe(502)

    const own = cloneConfigurationForm(initial)
    own.templateId = 23961
    own.selections = { x: 1 }
    const kept = mergeConfigurationForm(initial, own, server)
    expect(kept.templateId).toBe(23961)
    expect(kept.selections).toEqual({ x: 1 })
  })
})

describe('configuration editing: labels and answers', () => {
  it('labels selectors by their variable names and hides unreadable objects', () => {
    expect(inputTitle({ key: 'class:Clean Data', variableNames: ['verbrauch', 'leistung'], label: 'Bereinigte Daten' })).toBe('verbrauch, leistung')
    expect(inputTitle({ key: 'i1', variableNames: [], label: 'Verbrauch' })).toBe('Verbrauch')
    expect(inputTitle({ key: 'i1', variableNames: [] })).toBe('i1')
    expect(inputOptionLabel({ id: 1, name: 'A', path: 'P' }, 'Kein Leserecht')).toBe('A – P')
    expect(inputOptionLabel({ id: 1, name: null, readable: false }, 'Kein Leserecht')).toBe('Kein Leserecht')
  })

  it('reads the editor model of an answer and of a 409', () => {
    expect(editModelOf(model(), 23950)?.id).toBe(23950)
    expect(editModelOf(model(), 1)).toBeNull()
    expect(editModelOf({ id: 5, name: 'x' }, null)?.inputs).toEqual([])
    expect(editModelOf([], null)).toBeNull()
    expect(conflictModelOf(httpError(409, model()), 23950)?.name).toBe('Strom Musterwerk')
    expect(conflictModelOf(httpError(400, model()), 23950)).toBeNull()
    expect(warningsOfModel({ warnings: ['Eingang „leistung“ ist nicht zugeordnet.', '', 3 as unknown as string] })).toEqual(['Eingang „leistung“ ist nicht zugeordnet.'])
    expect(warningsOfModel(null)).toEqual([])
  })

  it('builds the paths and accepts bare or wrapped lists', async () => {
    expect(templateInputsPath(23960, 23950, 'de')).toBe('/web/contracts/templates/23960/inputs?lang=de&configuration=23950')
    expect(templateInputsPath(23960, null, 'en')).toBe('/web/contracts/templates/23960/inputs?lang=en')
    expect(deleteConfigurationPath(23950, '2026-11-01T10:00:00.000+01:00')).toBe('/web/contracts/configurations/23950?version=2026-11-01T10%3A00%3A00.000%2B01%3A00')
    const tpl = { id: 23960, name: 'Stromkosten', path: null, site: null }
    expect(await loadTemplates({ get: vi.fn(async () => [tpl]) } as never, 'de')).toEqual([tpl])
    expect(await loadTemplates({ get: vi.fn(async () => ({ templates: [tpl] })) } as never, 'de')).toEqual([tpl])
    expect(await loadTemplateInputs({ get: vi.fn(async () => ({ inputs: [input('i1')] })) } as never, 23960, null, 'de')).toHaveLength(1)
    expect(await loadTemplateInputs({ get: vi.fn(async () => null) } as never, 23960, null, 'de')).toEqual([])
  })
})
