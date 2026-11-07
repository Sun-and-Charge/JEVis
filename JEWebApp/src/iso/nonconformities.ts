/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { Nonconformity, NonconformityList, NonconformityPlan } from '../api/iso'
import { localName } from '../api/navigation'
import { matchesTerms, searchTerms } from '../modules/search'
import type { ColumnDef } from './columns'
import { formatAmount, formatIsoDate } from './format'
import { ALL, dayOf, matchesDateRange, matchesPlan, matchesTag, planRows, selectedPlans, splitTags, tagOptions, type DateRange, type PlanFilter, type PlanRow } from './filter'

export type NonconformityRow = PlanRow<NonconformityPlan, Nonconformity>

export type NonconformityDateField = 'createDate' | 'plannedDate' | 'doneDate'
export const NONCONFORMITY_DATE_FIELDS: readonly NonconformityDateField[] = ['createDate', 'plannedDate', 'doneDate']

export type NonconformityStatusFilter = 'open' | 'closed' | 'all'

export interface NonconformityFilter extends DateRange<NonconformityDateField> {
  plan: PlanFilter
  status: NonconformityStatusFilter
  medium: string
  field: string
  seu: string
  text: string
}

export const DEFAULT_NONCONFORMITY_FILTER: NonconformityFilter = {
  plan: 'all',
  status: 'all',
  medium: ALL,
  field: ALL,
  seu: ALL,
  dateField: 'createDate',
  from: '',
  until: '',
  text: '',
}

/** State for text and colour: overdue (open, planned date passed), open or closed. */
export type NonconformityState = 'overdue' | 'open' | 'closed'

export function nonconformityState(n: Pick<Nonconformity, 'open' | 'overdue'>): NonconformityState {
  if (!n.open) return 'closed'
  return n.overdue ? 'overdue' : 'open'
}

export function nonconformityRows(list: NonconformityList | null | undefined): NonconformityRow[] {
  return planRows(list?.plans ?? [], (p) => p.items)
}

export function filterNonconformities(rows: readonly NonconformityRow[], filter: NonconformityFilter, locale: string): NonconformityRow[] {
  const terms = searchTerms(filter.text, locale)
  return rows.filter((row) => {
    const n = row.item
    if (!matchesPlan(row.plan, filter.plan)) return false
    if (filter.status === 'open' && !n.open) return false
    if (filter.status === 'closed' && n.open) return false
    if (!matchesTag(n.medium, filter.medium)) return false
    if (!matchesTag(n.fields, filter.field)) return false
    if (!matchesTag(n.seu, filter.seu)) return false
    if (!matchesDateRange(n[filter.dateField], filter.from, filter.until)) return false
    return matchesTerms(
      terms,
      [
        n.number,
        n.title,
        n.creator,
        n.responsible,
        n.description,
        n.cause,
        n.immediateMeasures,
        n.correctiveActions,
        n.action,
        n.medium,
        n.seu,
        ...(n.fields ?? []),
        row.plan.name,
        row.plan.site ? localName(row.plan.site, locale) : null,
      ],
      locale,
    )
  })
}

export interface NonconformityOptions {
  media: string[]
  fields: string[]
  seus: string[]
}

export function nonconformityOptions(plans: readonly NonconformityPlan[], plan: PlanFilter, locale: string): NonconformityOptions {
  const selected = selectedPlans(plans, plan)
  const items = selected.flatMap((p) => p.items ?? [])
  return {
    media: tagOptions(
      selected.map((p) => p.media),
      items.map((n) => n.medium),
      locale,
    ),
    fields: tagOptions(
      selected.map((p) => p.fields),
      items.map((n) => n.fields),
      locale,
    ),
    seus: tagOptions(
      selected.map((p) => p.seus),
      items.map((n) => n.seu),
      locale,
    ),
  }
}

/** Badge / row class: overdue red, open yellow, closed green (with the state as text). */
export const NONCONFORMITY_BADGE: Record<NonconformityState, string> = { overdue: 'overdue', open: 'dueSoon', closed: 'done' }

const STATE_ORDER: Record<NonconformityState, number> = { overdue: 0, open: 1, closed: 2 }

const dateColumn = (id: NonconformityDateField, label: string): ColumnDef<NonconformityRow> => ({
  id,
  label,
  text: (r, c) => formatIsoDate(r.item[id], c.locale),
  sort: (r) => dayOf(r.item[id]),
  nowrap: true,
})

const textColumn = (id: string, label: string, get: (n: Nonconformity) => string | null | undefined): ColumnDef<NonconformityRow> => ({
  id,
  label,
  text: (r) => get(r.item) ?? '',
})

export const NONCONFORMITY_COLUMNS: ColumnDef<NonconformityRow>[] = [
  { id: 'number', label: 'iso.nc.col.number', text: (r) => r.item.number, sort: (r) => r.item.nr ?? r.item.number, nowrap: true },
  textColumn('title', 'iso.nc.col.title', (n) => n.title),
  textColumn('creator', 'iso.nc.col.creator', (n) => n.creator),
  textColumn('responsible', 'iso.nc.col.responsible', (n) => n.responsible),
  textColumn('medium', 'iso.nc.col.medium', (n) => splitTags(n.medium).join(', ')),
  textColumn('fields', 'iso.nc.col.fields', (n) => splitTags(n.fields).join(', ')),
  dateColumn('createDate', 'iso.nc.col.createDate'),
  dateColumn('plannedDate', 'iso.nc.col.plannedDate'),
  dateColumn('doneDate', 'iso.nc.col.doneDate'),
  {
    id: 'state',
    label: 'iso.nc.col.state',
    text: (r, c) => c.t('iso.nc.state.' + nonconformityState(r.item)),
    sort: (r) => STATE_ORDER[nonconformityState(r.item)],
    badge: (r) => NONCONFORMITY_BADGE[nonconformityState(r.item)],
    nowrap: true,
  },
  textColumn('seu', 'iso.nc.col.seu', (n) => n.seu),
  textColumn('description', 'iso.nc.col.description', (n) => n.description),
  textColumn('cause', 'iso.nc.col.cause', (n) => n.cause),
  textColumn('immediateMeasures', 'iso.nc.col.immediateMeasures', (n) => n.immediateMeasures),
  textColumn('correctiveActions', 'iso.nc.col.correctiveActions', (n) => n.correctiveActions),
  textColumn('action', 'iso.nc.col.action', (n) => n.action),
  {
    id: 'warnings',
    label: 'iso.nc.col.warnings',
    text: (r, c) => (r.item.warnings ?? []).map((w) => warningText(w, c.t)).join(', '),
    sort: (r) => r.item.warnings?.length ?? 0,
  },
  {
    id: 'attachments',
    label: 'iso.col.attachments',
    text: (r, c) => (r.item.attachments ? formatAmount(r.item.attachments, c.locale) : ''),
    sort: (r) => r.item.attachments,
    numeric: true,
  },
]

export const DEFAULT_NONCONFORMITY_COLUMNS = ['number', 'title', 'creator', 'responsible', 'medium', 'fields', 'createDate', 'plannedDate', 'doneDate', 'state']

const KNOWN_WARNINGS = new Set(['IMMEDIATE_MEASURES_MISSING', 'DONE_WITHOUT_ACTION', 'DONE_IN_FUTURE'])

/** Text of a warning code (unknown codes as they are). */
export function warningText(code: string, t: (key: string) => string): string {
  return KNOWN_WARNINGS.has(code) ? t('iso.nc.warning.' + code) : code
}
