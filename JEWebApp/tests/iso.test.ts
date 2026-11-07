/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import { actionOptions, actionRows, actionStatistics, ACTION_COLUMNS, DEFAULT_ACTION_COLUMNS, DEFAULT_ACTION_FILTER, filterActions, type ActionFilter } from '../src/iso/actions'
import { columnStorageKey, compareValues, loadColumnPrefs, saveColumnPrefs, sortRows, toggleColumn, type ColumnContext } from '../src/iso/columns'
import { safeFileName } from '../src/iso/download'
import { dayOf, daysBetween, matchesDateRange, matchesTag, planRows, splitTags, tagOptions } from '../src/iso/filter'
import { formatMoney, formatPercent, formatSize, formatWithUnit, humanizeKey, npvInputs, npvResults, yearTotals } from '../src/iso/format'
import { DEFAULT_LEGAL_FILTER, filterLegal, legalOptions, legalRows, relevanceState, safeLink } from '../src/iso/legal'
import { DEFAULT_NONCONFORMITY_FILTER, filterNonconformities, nonconformityOptions, nonconformityRows, nonconformityState, warningText } from '../src/iso/nonconformities'
import { action, actionPlan, legalPlan, nonconformity, nonconformityPlan, npv, obligation } from './fixtures/iso'

/** Intl uses no-break spaces */
const sp = (s: string) => s.replace(/\s/g, ' ')

const ctx: ColumnContext = { locale: 'de', t: ((key: string) => key) as ColumnContext['t'] }

function memoryStorage() {
  const map = new Map<string, string>()
  return {
    map,
    getItem: (k: string) => map.get(k) ?? null,
    setItem: (k: string, v: string) => void map.set(k, v),
    removeItem: (k: string) => void map.delete(k),
  }
}

describe('iso filter helpers', () => {
  it('splits tags like the Control Center', () => {
    expect(splitTags('Power; Gas;;')).toEqual(['Power', 'Gas'])
    expect(splitTags(['Area 1', 'Area 2;Area 3', null])).toEqual(['Area 1', 'Area 2', 'Area 3'])
    expect(splitTags(null)).toEqual([])
  })

  it('matches tags ignoring case, in lists and ;-texts', () => {
    expect(matchesTag('Power', '')).toBe(true)
    expect(matchesTag('power', 'Power')).toBe(true)
    expect(matchesTag('Gas;Power', 'Power')).toBe(true)
    expect(matchesTag(['Area 1', 'Area 2'], 'area 2')).toBe(true)
    expect(matchesTag('Powerline', 'Power')).toBe(false)
    expect(matchesTag(null, 'Power')).toBe(false)
  })

  it('builds tag options: configured first, then used ones sorted, no duplicates', () => {
    expect(tagOptions([['Open', 'Done'], ['open', 'Waiting']], ['Done', 'Zeta', 'alpha', null, 'Gas;Zeta'], 'de')).toEqual(['Open', 'Done', 'Waiting', 'alpha', 'Gas', 'Zeta'])
  })

  it('compares the stored day regardless of offset', () => {
    expect(dayOf('2026-03-01T00:00:00.000+01:00')).toBe(Date.UTC(2026, 2, 1))
    expect(dayOf('2026-03-01')).toBe(Date.UTC(2026, 2, 1))
    expect(dayOf('nonsense')).toBeNull()
    expect(daysBetween('2026-01-01T00:00:00+01:00', '2026-11-06')).toBe(309)
    expect(daysBetween(null, '2026-11-06')).toBeNull()
  })

  it('filters by date range (inclusive, missing dates hidden only with a range)', () => {
    expect(matchesDateRange(null, '', '')).toBe(true)
    expect(matchesDateRange(null, '2026-01-01', '')).toBe(false)
    expect(matchesDateRange('2026-01-01T00:00:00+01:00', '2026-01-01', '2026-01-01')).toBe(true)
    expect(matchesDateRange('2025-12-31T23:00:00+01:00', '2026-01-01', '')).toBe(false)
    expect(matchesDateRange('2026-02-01', '', '2026-01-31')).toBe(false)
  })

  it('flattens plans to rows', () => {
    const rows = planRows([actionPlan(1, [action(1), action(2)]), actionPlan(2, [action(3)])], (p) => p.actions)
    expect(rows.map((r) => [r.plan.id, r.item.id])).toEqual([
      [1, 1],
      [1, 2],
      [2, 3],
    ])
  })
})

describe('column preferences and sorting', () => {
  const all = ['a', 'b', 'c', 'd']

  it('loads defaults, stored columns in canonical order and ignores junk', () => {
    const store = memoryStorage()
    expect(loadColumnPrefs('m', all, ['c', 'a'], store)).toEqual(['a', 'c'])
    store.setItem(columnStorageKey('m'), JSON.stringify(['d', 'x', 'b']))
    expect(loadColumnPrefs('m', all, ['a'], store)).toEqual(['b', 'd'])
    store.setItem(columnStorageKey('m'), '{broken')
    expect(loadColumnPrefs('m', all, ['a'], store)).toEqual(['a'])
    store.setItem(columnStorageKey('m'), JSON.stringify(['x']))
    expect(loadColumnPrefs('m', all, ['a'], store)).toEqual(['a'])
  })

  it('saves per module, removes the entry for the defaults, survives a throwing storage', () => {
    const store = memoryStorage()
    saveColumnPrefs('m', ['a', 'b'], ['a'], store)
    expect(store.map.get('jevis.iso.columns.m')).toBe('["a","b"]')
    saveColumnPrefs('m', ['a'], ['a'], store)
    expect(store.map.has('jevis.iso.columns.m')).toBe(false)
    const throwing = {
      getItem: () => {
        throw new Error('blocked')
      },
      setItem: () => {
        throw new Error('blocked')
      },
      removeItem: () => {
        throw new Error('blocked')
      },
    }
    expect(() => saveColumnPrefs('m', ['b'], ['a'], throwing)).not.toThrow()
    expect(loadColumnPrefs('m', all, ['a'], throwing)).toEqual(['a'])
  })

  it('toggles columns and keeps the last one', () => {
    expect(toggleColumn(['a', 'c'], 'b', all)).toEqual(['a', 'b', 'c'])
    expect(toggleColumn(['a', 'c'], 'a', all)).toEqual(['c'])
    expect(toggleColumn(['c'], 'c', all)).toEqual(['c'])
  })

  it('sorts naturally with empty values last', () => {
    expect(compareValues('AP 2', 'AP 10', 'asc', 'de')).toBeLessThan(0)
    expect(compareValues(null, 1, 'asc', 'de')).toBeGreaterThan(0)
    expect(compareValues(null, 1, 'desc', 'de')).toBeGreaterThan(0)
    const rows = actionRows({ today: '2026-11-06', plans: [actionPlan(1, [action(1, { npv: npv({ investment: 500 }) }), action(2, { npv: null }), action(3, { npv: npv({ investment: 2000 }) })])] })
    const col = ACTION_COLUMNS.find((c) => c.id === 'investment')
    expect(sortRows(rows, col, 'desc', ctx).map((r) => r.item.id)).toEqual([3, 1, 2])
    expect(sortRows(rows, col, 'asc', ctx).map((r) => r.item.id)).toEqual([1, 3, 2])
    expect(sortRows(rows, undefined, 'asc', ctx).map((r) => r.item.id)).toEqual([1, 2, 3])
  })

  it('has the Control Center default columns', () => {
    expect(DEFAULT_ACTION_COLUMNS.every((id) => ACTION_COLUMNS.some((c) => c.id === id))).toBe(true)
  })
})

describe('action plans', () => {
  const plans = [
    actionPlan(1, [
      action(1, { title: 'Replace lamps', status: 'Open', medium: 'Power', field: 'Area 1', seu: 'SEU 1', description: 'LED retrofit' }),
      action(2, { title: 'Insulate pipes', status: 'Done', medium: 'Gas', mediumName: 'Gas', field: 'Area 2', doneDate: '2026-05-10T00:00:00+02:00' }),
    ]),
    actionPlan(2, [action(3, { title: 'Pump control', status: 'done', medium: 'Power;Gas', field: 'Area 2;Area 3', createDate: '2025-06-01T00:00:00+02:00' })], { statuses: ['Done', 'Waiting'] }),
  ]
  const rows = actionRows({ today: '2026-11-06', plans })
  const run = (f: Partial<ActionFilter>) => filterActions(rows, { ...DEFAULT_ACTION_FILTER, ...f }, 'de').map((r) => r.item.id)

  it('filters by plan, status, medium, field, SEU, date and text', () => {
    expect(run({})).toEqual([1, 2, 3])
    expect(run({ plan: 2 })).toEqual([3])
    expect(run({ status: 'Done' })).toEqual([2, 3])
    expect(run({ medium: 'Gas' })).toEqual([2, 3])
    expect(run({ field: 'Area 3' })).toEqual([3])
    expect(run({ seu: 'SEU 1' })).toEqual([1])
    expect(run({ dateField: 'createDate', from: '2026-01-01' })).toEqual([1, 2])
    expect(run({ dateField: 'doneDate', from: '2026-01-01', until: '2026-12-31' })).toEqual([2])
    expect(run({ text: 'led' })).toEqual([1])
    expect(run({ text: 'pump area' })).toEqual([3])
  })

  it('offers options of the selected plans', () => {
    const one = actionOptions(plans, 1, 'de')
    expect(one.statuses).toEqual(['Open', 'Done'])
    expect(one.media.map((m) => m.id)).toEqual(['Power', 'Gas'])
    const all = actionOptions(plans, 'all', 'de')
    expect(all.statuses).toEqual(['Open', 'Done', 'Waiting'])
    expect(all.fields).toEqual(['Area 1', 'Area 2', 'Area 3'])
  })

  it('computes key figures like the Control Center statistics', () => {
    const list = [
      action(1, { medium: 'Power', npv: npv({ investment: 1000, saving: 365 }), consumption: { ...action(0).consumption!, diff: 3650 }, doneDate: '2026-10-07T00:00:00+02:00', status: 'Done' }),
      action(2, { medium: 'Gas', npv: npv({ investment: 500, saving: -73 }), consumption: { ...action(0).consumption!, diff: -730 }, doneDate: '2026-09-07T00:00:00+02:00', status: 'Done' }),
      action(3, { medium: 'Power', npv: null, consumption: null, status: 'Open' }),
      action(4, { medium: 'Power', npv: npv({ investment: 100, saving: 100 }), consumption: { ...action(0).consumption!, diff: 100 }, doneDate: '2025-01-01T00:00:00+01:00', status: 'Waiting' }),
    ]
    const r = actionRows({ today: '2026-11-06', plans: [actionPlan(1, list)] })
    const s = actionStatistics(r.slice(0, 3), '2026-11-06', null, ['Open', 'Done'])
    expect(s.count).toBe(3)
    expect(s.investment).toBe(1500)
    expect(s.savings).toBe(292)
    expect(s.savingsGross).toBe(365)
    expect(s.consumption).toBe(2920)
    expect(s.consumptionGross).toBe(3650)
    // 30 days and 60 days since done
    expect(s.since.count).toBe(2)
    expect(s.since.moneyNet).toBeCloseTo(30 - 12)
    expect(s.since.moneyGross).toBeCloseTo(30)
    expect(s.since.kwhNet).toBeCloseTo(300 - 120)
    expect(s.since.kwhGross).toBeCloseTo(300)
    expect(s.since.co2Net).toBeCloseTo((0.4 * 300) / 1000 + (0.2 * -120) / 1000)
    expect(s.since.co2Gross).toBeCloseTo((0.4 * 300) / 1000)
    const power = s.media.find((m) => m.id === 'Power')!
    expect(power.diff).toBe(3650)
    expect(power.co2PerYear).toBeCloseTo(1.46)
    expect(power.kwhNet).toBeCloseTo(300)
    const gas = s.media.find((m) => m.id === 'Gas')!
    expect(gas.kwhGross).toBe(0)
    expect(gas.co2Net).toBeCloseTo(-0.024)
    expect(s.statuses).toEqual([
      { status: 'Open', count: 1 },
      { status: 'Done', count: 2 },
    ])

    // with a from date only actions done after it count
    const since = actionStatistics(r, '2026-11-06', '2026-10-01', ['Open'])
    expect(since.since.count).toBe(1)
    expect(since.since.kwhNet).toBeCloseTo(300)
    expect(since.statuses.map((x) => x.status)).toEqual(['Open', 'Done', 'Waiting'])
    // without from date: since done (action 4: 674 days)
    const allSince = actionStatistics(r, '2026-11-06', null)
    expect(allSince.since.count).toBe(3)
    expect(allSince.since.kwhNet).toBeCloseTo(300 - 120 + (674 * 100) / 365)
  })

  it('counts future done dates as 0 days', () => {
    const r = actionRows({ today: '2026-11-06', plans: [actionPlan(1, [action(1, { doneDate: '2027-01-01T00:00:00+01:00' })])] })
    expect(actionStatistics(r, '2026-11-06', null).since.kwhNet).toBe(0)
  })
})

describe('nonconformities', () => {
  const plans = [
    nonconformityPlan(1, [
      nonconformity(1, { overdue: true, cause: 'Valve leak' }),
      nonconformity(2, { open: false, doneDate: '2026-04-01T00:00:00+02:00', medium: 'Gas', fields: ['Area 2'] }),
    ]),
    nonconformityPlan(2, [nonconformity(3, { seu: 'SEU 9', medium: 'Power;Gas', createDate: '2025-01-01T00:00:00+01:00' })], { media: ['Water'] }),
  ]
  const rows = nonconformityRows({ plans })
  const run = (f: Partial<typeof DEFAULT_NONCONFORMITY_FILTER>) => filterNonconformities(rows, { ...DEFAULT_NONCONFORMITY_FILTER, ...f }, 'de').map((r) => r.item.id)

  it('derives the state', () => {
    expect(nonconformityState({ open: true, overdue: true })).toBe('overdue')
    expect(nonconformityState({ open: true, overdue: false })).toBe('open')
    expect(nonconformityState({ open: false, overdue: true })).toBe('closed')
  })

  it('filters by plan, open/closed, medium, field, SEU, date and text', () => {
    expect(run({})).toEqual([1, 2, 3])
    expect(run({ status: 'open' })).toEqual([1, 3])
    expect(run({ status: 'closed' })).toEqual([2])
    expect(run({ plan: 1, medium: 'Gas' })).toEqual([2])
    expect(run({ medium: 'gas' })).toEqual([2, 3])
    expect(run({ field: 'Area 2' })).toEqual([2])
    expect(run({ seu: 'SEU 9' })).toEqual([3])
    expect(run({ from: '2026-01-01' })).toEqual([1, 2])
    expect(run({ text: 'valve' })).toEqual([1])
  })

  it('offers options and warning texts', () => {
    expect(nonconformityOptions(plans, 'all', 'de').media).toEqual(['Power', 'Gas', 'Water'])
    expect(nonconformityOptions(plans, 2, 'de').media).toEqual(['Water', 'Gas', 'Power'])
    const t = (k: string) => `[${k}]`
    expect(warningText('DONE_IN_FUTURE', t)).toBe('[iso.nc.warning.DONE_IN_FUTURE]')
    expect(warningText('SOMETHING_NEW', t)).toBe('SOMETHING_NEW')
  })
})

describe('legal register', () => {
  const plans = [
    legalPlan(1, [
      obligation(1, { title: 'Energy act', relevant: true }),
      obligation(2, { category: 'Category 2', relevant: false, examinationDate: null }),
      obligation(3, { scope: 'Scope 2', relevant: null, designation: 'Ordinance X' }),
    ]),
  ]
  const rows = legalRows({ plans })
  const run = (f: Partial<typeof DEFAULT_LEGAL_FILTER>) => filterLegal(rows, { ...DEFAULT_LEGAL_FILTER, ...f }, 'de').map((r) => r.item.id)

  it('filters by category, scope, relevance, date and text', () => {
    expect(run({})).toEqual([1, 2, 3])
    expect(run({ category: 'Category 2' })).toEqual([2])
    expect(run({ scope: 'Scope 2' })).toEqual([3])
    expect(run({ relevance: 'relevant' })).toEqual([1])
    expect(run({ relevance: 'notRelevant' })).toEqual([2, 3])
    expect(run({ dateField: 'examinationDate', from: '2026-01-01' })).toEqual([1, 3])
    expect(run({ dateField: 'issueDate', until: '2019-12-31' })).toEqual([])
    expect(run({ text: 'ordinance' })).toEqual([3])
  })

  it('offers options, relevance and only http(s) links', () => {
    expect(legalOptions(plans, 1, 'de')).toEqual({ categories: ['Category 1', 'Category 2'], scopes: ['Scope 1', 'Scope 2'] })
    expect(relevanceState({ relevant: null })).toBe('unknown')
    expect(safeLink('https://example.org/law')).toBe('https://example.org/law')
    expect(safeLink('javascript:alert(1)')).toBeNull()
    expect(safeLink('not a url')).toBeNull()
  })
})

describe('iso formatting', () => {
  it('formats money, percent, units and sizes with the locale', () => {
    expect(sp(formatMoney(1234.5, 'de'))).toBe('1.234,50 €')
    expect(formatMoney(null, 'de')).toBe('')
    expect(sp(formatPercent(10, 'de'))).toBe('10 %')
    expect(formatWithUnit(20, 'kWh', 'en')).toBe('20 kWh')
    expect(formatWithUnit(1234.567, null, 'de')).toBe('1.234,57')
    expect(sp(formatSize(12345, 'en'))).toBe('12.1 kB')
    expect(formatSize(null, 'en')).toBe('')
  })

  it('lists NPV inputs and results without missing values', () => {
    const inputs = npvInputs(npv({ runningCost: null }), 'de')
    expect(inputs.map((f) => f.label)).not.toContain('iso.npv.runningCost')
    expect(sp(inputs.find((f) => f.label === 'iso.npv.interestRate')!.text)).toBe('10 %')
    expect(npvResults(npv(), 'de').find((f) => f.label === 'iso.npv.profitabilityIndex')?.text).toBe('1,12')
    expect(npvInputs(null, 'de')).toEqual([])
    expect(yearTotals(npv().yearRows).net).toBeCloseTo(628.19)
    expect(yearTotals([{ year: 1, deposit: null, cost: 1, net: null, discounted: null }])).toEqual({ deposit: 0, cost: 1, net: 0, discounted: 0 })
  })

  it('makes readable labels and safe file names', () => {
    expect(humanizeKey('processDocument')).toBe('Process Document')
    expect(humanizeKey('new_enpi')).toBe('New enpi')
    expect(safeFileName('../dir/offer.pdf')).toBe('offer.pdf')
    expect(safeFileName('')).toBe('download')
  })
})
