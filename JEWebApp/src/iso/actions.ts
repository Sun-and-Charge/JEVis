/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { Action, ActionPlan, ActionPlanList } from '../api/iso'
import { localName } from '../api/navigation'
import { matchesTerms, searchTerms } from '../modules/search'
import type { ColumnDef } from './columns'
import { formatIsoDate, formatMoney, formatWithUnit, formatFactor, formatAmount } from './format'
import { ALL, dayOf, daysBetween, matchesDateRange, matchesPlan, matchesTag, planRows, selectedPlans, splitTags, tagOptions, type DateRange, type PlanFilter, type PlanRow } from './filter'

export type ActionRow = PlanRow<ActionPlan, Action>

export type ActionDateField = 'createDate' | 'plannedDate' | 'doneDate'
export const ACTION_DATE_FIELDS: readonly ActionDateField[] = ['createDate', 'plannedDate', 'doneDate']

export interface ActionFilter extends DateRange<ActionDateField> {
  plan: PlanFilter
  status: string
  medium: string
  field: string
  seu: string
  text: string
}

export const DEFAULT_ACTION_FILTER: ActionFilter = {
  plan: 'all',
  status: ALL,
  medium: ALL,
  field: ALL,
  seu: ALL,
  dateField: 'createDate',
  from: '',
  until: '',
  text: '',
}

export function actionRows(list: ActionPlanList | null | undefined): ActionRow[] {
  return planRows(list?.plans ?? [], (p) => p.actions)
}

/** Texts the search looks in (those of the Control Center plus number, field and medium). */
function searchTexts(row: ActionRow, locale: string): (string | null | undefined)[] {
  const a = row.item
  return [
    a.number,
    a.title,
    a.responsible,
    a.note,
    a.description,
    a.alternativeMeasures,
    a.correction,
    a.affectedProcessNote,
    a.followAction,
    a.evaluationNote,
    a.energyFlowNote,
    a.fromUser,
    a.distributor,
    a.field,
    a.seu,
    a.mediumName,
    row.plan.name,
    row.plan.site ? localName(row.plan.site, locale) : null,
  ]
}

export function filterActions(rows: readonly ActionRow[], filter: ActionFilter, locale: string): ActionRow[] {
  const terms = searchTerms(filter.text, locale)
  return rows.filter((row) => {
    const a = row.item
    if (!matchesPlan(row.plan, filter.plan)) return false
    if (!matchesTag(a.status, filter.status)) return false
    if (!matchesTag(a.medium, filter.medium)) return false
    if (!matchesTag(a.field, filter.field)) return false
    if (!matchesTag(a.seu, filter.seu)) return false
    if (!matchesDateRange(a[filter.dateField], filter.from, filter.until)) return false
    return matchesTerms(terms, searchTexts(row, locale), locale)
  })
}

export interface MediumOption {
  id: string
  name: string
}

export interface ActionOptions {
  statuses: string[]
  media: MediumOption[]
  fields: string[]
  seus: string[]
}

/** Filter options of the selected plan(s): configured tags plus those used by the actions. */
export function actionOptions(plans: readonly ActionPlan[], plan: PlanFilter, locale: string): ActionOptions {
  const selected = selectedPlans(plans, plan)
  const actions = selected.flatMap((p) => p.actions ?? [])
  const media: MediumOption[] = []
  const seen = new Set<string>()
  for (const p of selected) {
    for (const m of p.media ?? []) {
      if (!m.id || seen.has(m.id.toLowerCase())) continue
      seen.add(m.id.toLowerCase())
      media.push({ id: m.id, name: m.name || m.id })
    }
  }
  for (const a of actions) {
    for (const id of splitTags(a.medium)) {
      if (seen.has(id.toLowerCase())) continue
      seen.add(id.toLowerCase())
      media.push({ id, name: a.mediumName || id })
    }
  }
  return {
    statuses: tagOptions(
      selected.map((p) => p.statuses),
      actions.map((a) => a.status),
      locale,
    ),
    media,
    fields: tagOptions(
      selected.map((p) => p.fields),
      actions.map((a) => a.field),
      locale,
    ),
    seus: tagOptions(
      selected.map((p) => p.seus),
      actions.map((a) => a.seu),
      locale,
    ),
  }
}

const num = (v: number | null | undefined): number => (typeof v === 'number' && Number.isFinite(v) ? v : 0)
const diffOf = (a: Action) => num(a.consumption?.diff)
const savingOf = (a: Action) => num(a.npv?.saving)

export interface MediumFigures {
  id: string
  name: string
  /** t CO₂ per MWh (0 if unknown, like the Control Center) */
  co2: number
  /** sum of the yearly consumption changes (kWh/a) */
  diff: number
  /** CO₂ of that change in t/a */
  co2PerYear: number
  /** since the date (see ActionStatistics.since) */
  kwhNet: number
  kwhGross: number
  co2Net: number
  co2Gross: number
}

export interface ActionStatistics {
  count: number
  /** sum of npv.investment */
  investment: number
  /** sum of npv.saving (€/a), net and only the positive ones */
  savings: number
  savingsGross: number
  /** sum of consumption.diff (kWh/a), net and only the positive ones */
  consumption: number
  consumptionGross: number
  /**
   * Savings of the actions done after `from` (all done ones without from date), each `days · x / 365` with the
   * days from its done date to today; CO₂ in t = co2 · kWh / 1000 with the factor of its medium.
   */
  since: {
    from: string | null
    count: number
    moneyNet: number
    moneyGross: number
    kwhNet: number
    kwhGross: number
    co2Net: number
    co2Gross: number
  }
  media: MediumFigures[]
  statuses: { status: string; count: number }[]
}

/**
 * Key figures of the (filtered) actions like the Control Center's Statistics. `statuses` are the status tags to
 * count (in their order; tags only used by actions are added). Done dates in the future count 0 days.
 */
export function actionStatistics(rows: readonly ActionRow[], today: string, from: string | null, statuses: readonly string[] = []): ActionStatistics {
  const stats: ActionStatistics = {
    count: rows.length,
    investment: 0,
    savings: 0,
    savingsGross: 0,
    consumption: 0,
    consumptionGross: 0,
    since: { from: from || null, count: 0, moneyNet: 0, moneyGross: 0, kwhNet: 0, kwhGross: 0, co2Net: 0, co2Gross: 0 },
    media: [],
    statuses: [],
  }
  const media = new Map<string, MediumFigures>()
  const fromDay = from ? dayOf(from) : null

  for (const { plan, item: a } of rows) {
    const diff = diffOf(a)
    const saving = savingOf(a)
    stats.investment += num(a.npv?.investment)
    stats.savings += saving
    if (saving > 0) stats.savingsGross += saving
    stats.consumption += diff
    if (diff > 0) stats.consumptionGross += diff

    const mediumId = (a.medium ?? '').trim()
    const medium = mediumId ? plan.media?.find((m) => m.id === mediumId) : undefined
    const co2 = num(medium?.co2)
    let figures: MediumFigures | undefined
    if (mediumId) {
      const key = mediumId.toLowerCase()
      figures = media.get(key)
      if (!figures) {
        figures = { id: mediumId, name: medium?.name || a.mediumName || mediumId, co2, diff: 0, co2PerYear: 0, kwhNet: 0, kwhGross: 0, co2Net: 0, co2Gross: 0 }
        media.set(key, figures)
      }
      figures.diff += diff
      figures.co2PerYear += (co2 * diff) / 1000
    }

    const doneDay = dayOf(a.doneDate)
    if (doneDay != null && (fromDay == null || doneDay >= fromDay)) {
      const days = Math.max(0, daysBetween(a.doneDate, today) ?? 0)
      const kwh = (days * diff) / 365
      const euro = (days * saving) / 365
      stats.since.count++
      stats.since.kwhNet += kwh
      stats.since.co2Net += (co2 * kwh) / 1000
      stats.since.moneyNet += euro
      if (euro > 0) stats.since.moneyGross += euro
      if (kwh > 0) {
        stats.since.kwhGross += kwh
        stats.since.co2Gross += (co2 * kwh) / 1000
      }
      if (figures) {
        figures.kwhNet += kwh
        figures.co2Net += (co2 * kwh) / 1000
        if (kwh > 0) {
          figures.kwhGross += kwh
          figures.co2Gross += (co2 * kwh) / 1000
        }
      }
    }
  }
  stats.media = [...media.values()]

  const counts = new Map<string, { status: string; count: number }>()
  for (const s of statuses) if (s && !counts.has(s.toLowerCase())) counts.set(s.toLowerCase(), { status: s, count: 0 })
  for (const { item } of rows) {
    const s = (item.status ?? '').trim()
    if (!s) continue
    const entry = counts.get(s.toLowerCase())
    if (entry) entry.count++
    else counts.set(s.toLowerCase(), { status: s, count: 1 })
  }
  stats.statuses = [...counts.values()]
  return stats
}

const dateColumn = (id: string, label: string, get: (a: Action) => string | null): ColumnDef<ActionRow> => ({
  id,
  label,
  text: (r, c) => formatIsoDate(get(r.item), c.locale),
  sort: (r) => dayOf(get(r.item)),
  nowrap: true,
})

const moneyColumn = (id: string, label: string, get: (a: Action) => number | null | undefined): ColumnDef<ActionRow> => ({
  id,
  label,
  text: (r, c) => formatMoney(get(r.item), c.locale),
  sort: (r) => get(r.item),
  numeric: true,
  nowrap: true,
})

const textColumn = (id: string, label: string, get: (a: Action) => string | null | undefined): ColumnDef<ActionRow> => ({
  id,
  label,
  text: (r) => get(r.item) ?? '',
})

/** All columns of the action list (Control Center order). */
export const ACTION_COLUMNS: ColumnDef<ActionRow>[] = [
  { id: 'number', label: 'iso.actions.col.number', text: (r) => r.item.number, sort: (r) => r.item.no ?? r.item.number, nowrap: true },
  textColumn('responsible', 'iso.actions.col.responsible', (a) => a.responsible),
  dateColumn('createDate', 'iso.actions.col.createDate', (a) => a.createDate),
  textColumn('title', 'iso.actions.col.title', (a) => a.title),
  textColumn('medium', 'iso.actions.col.medium', (a) => a.mediumName || a.medium),
  textColumn('status', 'iso.actions.col.status', (a) => a.status),
  dateColumn('doneDate', 'iso.actions.col.doneDate', (a) => a.doneDate),
  dateColumn('plannedDate', 'iso.actions.col.plannedDate', (a) => a.plannedDate),
  moneyColumn('investment', 'iso.actions.col.investment', (a) => a.npv?.investment),
  moneyColumn('saving', 'iso.actions.col.saving', (a) => a.npv?.saving),
  {
    id: 'consumptionDiff',
    label: 'iso.actions.col.consumptionDiff',
    text: (r, c) => formatWithUnit(r.item.consumption?.diff, r.item.consumption?.unit, c.locale),
    sort: (r) => r.item.consumption?.diff,
    numeric: true,
    nowrap: true,
  },
  textColumn('field', 'iso.actions.col.field', (a) => a.field),
  textColumn('seu', 'iso.actions.col.seu', (a) => a.seu),
  textColumn('fromUser', 'iso.actions.col.fromUser', (a) => a.fromUser),
  textColumn('distributor', 'iso.actions.col.distributor', (a) => a.distributor),
  textColumn('description', 'iso.actions.col.description', (a) => a.description),
  textColumn('note', 'iso.actions.col.note', (a) => a.note),
  {
    id: 'enpiDiff',
    label: 'iso.actions.col.enpiDiff',
    text: (r, c) => formatWithUnit(r.item.enpi?.diff, r.item.enpi?.unit, c.locale),
    sort: (r) => r.item.enpi?.diff,
    numeric: true,
    nowrap: true,
  },
  moneyColumn('npvResult', 'iso.actions.col.npvResult', (a) => a.npv?.result),
  {
    id: 'profitabilityIndex',
    label: 'iso.actions.col.profitabilityIndex',
    text: (r, c) => formatFactor(r.item.npv?.profitabilityIndex, c.locale),
    sort: (r) => r.item.npv?.profitabilityIndex,
    numeric: true,
    nowrap: true,
  },
  {
    id: 'savingsSinceDone',
    label: 'iso.actions.col.savingsSinceDone',
    text: (r, c) => formatWithUnit(r.item.savingsSinceDone, r.item.consumption?.unit, c.locale),
    sort: (r) => r.item.savingsSinceDone,
    numeric: true,
    nowrap: true,
  },
  {
    id: 'daysSinceDone',
    label: 'iso.actions.col.daysSinceDone',
    text: (r, c) => formatAmount(r.item.daysSinceDone, c.locale),
    sort: (r) => r.item.daysSinceDone,
    numeric: true,
    nowrap: true,
  },
  {
    id: 'attachments',
    label: 'iso.col.attachments',
    text: (r, c) => (r.item.attachments ? formatAmount(r.item.attachments, c.locale) : ''),
    sort: (r) => r.item.attachments,
    numeric: true,
  },
]

export const DEFAULT_ACTION_COLUMNS = ['number', 'responsible', 'createDate', 'title', 'medium', 'status', 'doneDate', 'plannedDate', 'investment', 'saving', 'consumptionDiff']
