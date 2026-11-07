/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { LegalList, LegalPlan, Obligation } from '../api/iso'
import { localName } from '../api/navigation'
import { matchesTerms, searchTerms } from '../modules/search'
import type { ColumnDef } from './columns'
import { formatAmount, formatIsoDate } from './format'
import { ALL, dayOf, matchesDateRange, matchesPlan, matchesTag, planRows, selectedPlans, tagOptions, type DateRange, type PlanFilter, type PlanRow } from './filter'

export type LegalRow = PlanRow<LegalPlan, Obligation>

export type LegalDateField = 'examinationDate' | 'issueDate' | 'currentVersionDate'
export const LEGAL_DATE_FIELDS: readonly LegalDateField[] = ['examinationDate', 'issueDate', 'currentVersionDate']

export type RelevanceFilter = 'all' | 'relevant' | 'notRelevant'

export interface LegalFilter extends DateRange<LegalDateField> {
  plan: PlanFilter
  category: string
  scope: string
  relevance: RelevanceFilter
  text: string
}

export const DEFAULT_LEGAL_FILTER: LegalFilter = {
  plan: 'all',
  category: ALL,
  scope: ALL,
  relevance: 'all',
  dateField: 'examinationDate',
  from: '',
  until: '',
  text: '',
}

export function legalRows(list: LegalList | null | undefined): LegalRow[] {
  return planRows(list?.plans ?? [], (p) => p.obligations)
}

export function filterLegal(rows: readonly LegalRow[], filter: LegalFilter, locale: string): LegalRow[] {
  const terms = searchTerms(filter.text, locale)
  return rows.filter((row) => {
    const o = row.item
    if (!matchesPlan(row.plan, filter.plan)) return false
    if (!matchesTag(o.category, filter.category)) return false
    if (!matchesTag(o.scope, filter.scope)) return false
    if (filter.relevance === 'relevant' && o.relevant !== true) return false
    if (filter.relevance === 'notRelevant' && o.relevant === true) return false
    if (!matchesDateRange(o[filter.dateField], filter.from, filter.until)) return false
    return matchesTerms(
      terms,
      [
        o.nr != null ? String(o.nr) : null,
        o.title,
        o.designation,
        o.description,
        o.importance,
        o.category,
        o.scope,
        row.plan.name,
        row.plan.site ? localName(row.plan.site, locale) : null,
      ],
      locale,
    )
  })
}

export interface LegalOptions {
  categories: string[]
  scopes: string[]
}

export function legalOptions(plans: readonly LegalPlan[], plan: PlanFilter, locale: string): LegalOptions {
  const selected = selectedPlans(plans, plan)
  const items = selected.flatMap((p) => p.obligations ?? [])
  return {
    categories: tagOptions(
      selected.map((p) => p.categories),
      items.map((o) => o.category),
      locale,
    ),
    scopes: tagOptions(
      selected.map((p) => p.scopes),
      items.map((o) => o.scope),
      locale,
    ),
  }
}

/** 'yes' / 'no' / 'unknown' (for text and badge). */
export function relevanceState(o: Pick<Obligation, 'relevant'>): 'yes' | 'no' | 'unknown' {
  return o.relevant == null ? 'unknown' : o.relevant ? 'yes' : 'no'
}

const dateColumn = (id: LegalDateField, label: string): ColumnDef<LegalRow> => ({
  id,
  label,
  text: (r, c) => formatIsoDate(r.item[id], c.locale),
  sort: (r) => dayOf(r.item[id]),
  nowrap: true,
})

const textColumn = (id: string, label: string, get: (o: Obligation) => string | null | undefined): ColumnDef<LegalRow> => ({
  id,
  label,
  text: (r) => get(r.item) ?? '',
})

export const LEGAL_COLUMNS: ColumnDef<LegalRow>[] = [
  { id: 'nr', label: 'iso.legal.col.nr', text: (r) => (r.item.nr != null ? String(r.item.nr) : ''), sort: (r) => r.item.nr, numeric: true, nowrap: true },
  textColumn('title', 'iso.legal.col.title', (o) => o.title),
  textColumn('designation', 'iso.legal.col.designation', (o) => o.designation),
  dateColumn('issueDate', 'iso.legal.col.issueDate'),
  dateColumn('currentVersionDate', 'iso.legal.col.currentVersionDate'),
  {
    id: 'relevant',
    label: 'iso.legal.col.relevant',
    text: (r, c) => c.t('iso.legal.relevance.' + relevanceState(r.item)),
    sort: (r) => r.item.relevant,
    badge: (r) => (r.item.relevant ? 'relevant' : null),
    nowrap: true,
  },
  dateColumn('examinationDate', 'iso.legal.col.examinationDate'),
  textColumn('category', 'iso.legal.col.category', (o) => o.category),
  textColumn('scope', 'iso.legal.col.scope', (o) => o.scope),
  { id: 'link', label: 'iso.legal.col.link', text: (r) => r.item.link ?? '', href: (r) => safeLink(r.item.link) },
  textColumn('description', 'iso.legal.col.description', (o) => o.description),
  textColumn('importance', 'iso.legal.col.importance', (o) => o.importance),
  {
    id: 'attachments',
    label: 'iso.col.attachments',
    text: (r, c) => (r.item.attachments ? formatAmount(r.item.attachments, c.locale) : ''),
    sort: (r) => r.item.attachments,
    numeric: true,
  },
]

export const DEFAULT_LEGAL_COLUMNS = ['nr', 'title', 'designation', 'issueDate', 'currentVersionDate', 'relevant', 'examinationDate', 'category', 'scope', 'link']

/** Only http(s) links are rendered (the server checks it as well). */
export function safeLink(link: string | null | undefined): string | null {
  if (!link) return null
  try {
    const url = new URL(link)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
}
