/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { EChartsOption } from 'echarts'
import { escapeHtml, formatNumber } from '../analysis/format'
import { CSV_BOM, csvField, csvNumber, csvSeparators } from '../analysis/table'
import { INVOICE_MEDIA, type Invoice, type InvoiceItem, type InvoiceMedium } from '../api/invoices'
import { formatLocalDate, formatPeriod } from '../modules/dates'
import { matchesSite, matchesTerms, searchTerms, siteOptions, type SiteFilter, type SiteOption } from '../modules/search'
import { checkRange, isoDay, type RangeCheck } from '../notes/notes'

/*
 * Energy invoices (W4.7, view only): time frame, medium chips, search and site filter, the summary and monthly
 * chart per medium (credit notes reduce them), CSV and the line items of one invoice.
 */

/** Medium of an invoice; empty or unknown values count as OTHER. */
export function invoiceMedium(inv: Pick<Invoice, 'medium'>): InvoiceMedium {
  const m = (inv.medium ?? '').trim().toUpperCase()
  return (INVOICE_MEDIA as readonly string[]).includes(m) ? (m as InvoiceMedium) : 'OTHER'
}

/** Default time frame: the last 24 months (current month included) up to the end of the current month. */
export function defaultInvoiceRange(now: Date = new Date()): { from: string; to: string } {
  return {
    from: isoDay(new Date(now.getFullYear(), now.getMonth() - 23, 1)),
    to: isoDay(new Date(now.getFullYear(), now.getMonth() + 1, 0)),
  }
}

/** Time frame of the invoices: at most 10 years (server: 400). */
export function checkInvoiceRange(from: string, to: string): RangeCheck {
  return checkRange(from, to, 10)
}

export interface InvoiceFilter {
  /** search in number, seller and meter point */
  text: string
  /** selected media; none selected: all */
  media: InvoiceMedium[]
  site: SiteFilter
}

export function defaultInvoiceFilter(): InvoiceFilter {
  return { text: '', media: [], site: 'all' }
}

const siteOf = (inv: Pick<Invoice, 'site'>) => (inv.site ? { id: inv.site.id, name: inv.site.name, names: inv.site.names ?? {} } : null)

export function filterInvoices(invoices: readonly Invoice[], filter: InvoiceFilter, locale: string): Invoice[] {
  const terms = searchTerms(filter.text, locale)
  const media = new Set(filter.media)
  return invoices.filter(
    (inv) =>
      (!media.size || media.has(invoiceMedium(inv))) &&
      matchesSite(siteOf(inv), filter.site) &&
      matchesTerms(terms, [inv.number, inv.seller, inv.meterPoint], locale),
  )
}

export function invoiceSites(invoices: readonly Invoice[], locale: string): SiteOption[] {
  return siteOptions(invoices.map((inv) => ({ site: siteOf(inv) })), locale)
}

export interface MediumOption {
  id: InvoiceMedium
  count: number
}

/** Medium chips: the media present (fixed order) with their number; selected ones stay visible. */
export function mediumOptions(invoices: readonly Invoice[], selected: readonly InvoiceMedium[] = []): MediumOption[] {
  const counts = new Map<InvoiceMedium, number>()
  for (const inv of invoices) counts.set(invoiceMedium(inv), (counts.get(invoiceMedium(inv)) ?? 0) + 1)
  return INVOICE_MEDIA.filter((m) => counts.has(m) || selected.includes(m)).map((id) => ({ id, count: counts.get(id) ?? 0 }))
}

const isNumber = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v)

/** A value as it counts in sums: credit notes are stored negative; a positive value of a credit note is turned negative. */
export function signed(value: number | null | undefined, credit: boolean): number | null {
  if (!isNumber(value)) return null
  return credit ? -Math.abs(value) : value
}

export interface Amount {
  /** unit or currency */
  key: string
  value: number
}

export interface MediumSummary {
  medium: InvoiceMedium
  count: number
  credits: number
  /** per consumption unit, most used first */
  consumption: Amount[]
  /** per currency, most used first */
  net: Amount[]
  gross: Amount[]
}

function addAmount(map: Map<string, { value: number; count: number }>, key: string, value: number | null) {
  if (value == null) return
  const entry = map.get(key) ?? { value: 0, count: 0 }
  entry.value += value
  entry.count++
  map.set(key, entry)
}

const amounts = (map: Map<string, { value: number; count: number }>): Amount[] =>
  [...map.entries()].sort((a, b) => b[1].count - a[1].count || a[0].localeCompare(b[0])).map(([key, e]) => ({ key, value: e.value }))

/** Totals per medium (fixed order): consumption per unit, net and gross per currency; credit notes reduce them. */
export function invoiceSummaries(invoices: readonly Invoice[]): MediumSummary[] {
  type Sums = Map<string, { value: number; count: number }>
  const groups = new Map<InvoiceMedium, { count: number; credits: number; c: Sums; n: Sums; g: Sums }>()
  for (const inv of invoices) {
    const m = invoiceMedium(inv)
    let g = groups.get(m)
    if (!g) {
      g = { count: 0, credits: 0, c: new Map(), n: new Map(), g: new Map() }
      groups.set(m, g)
    }
    g.count++
    if (inv.credit) g.credits++
    addAmount(g.c, inv.consumptionUnit ?? '', signed(inv.consumption, inv.credit))
    addAmount(g.n, inv.currency ?? '', signed(inv.net, inv.credit))
    addAmount(g.g, inv.currency ?? '', signed(inv.gross, inv.credit))
  }
  return INVOICE_MEDIA.filter((m) => groups.has(m)).map((medium) => {
    const g = groups.get(medium)!
    return { medium, count: g.count, credits: g.credits, consumption: amounts(g.c), net: amounts(g.n), gross: amounts(g.g) }
  })
}

/** Month of an invoice (yyyy-MM): of the period start, else of the invoice date; null if neither. */
export function invoiceMonth(inv: Pick<Invoice, 'periodStart' | 'date'>): string | null {
  const text = inv.periodStart || inv.date
  return text && /^\d{4}-\d{2}/.test(text) ? text.slice(0, 7) : null
}

/** Months from `first` to `last` (yyyy-MM), both included. */
export function monthRange(first: string, last: string): string[] {
  const out: string[] = []
  let [y, m] = first.split('-').map(Number)
  const [ly, lm] = last.split('-').map(Number)
  while (y < ly || (y === ly && m <= lm)) {
    out.push(`${y}-${String(m).padStart(2, '0')}`)
    if (++m > 12) {
      m = 1
      y++
    }
    if (out.length > 1200) break
  }
  return out
}

export interface MonthlySeries {
  medium: InvoiceMedium
  /** consumption unit used (the most frequent); invoices in other units are not in the consumption bars */
  unit: string
  /** currency used (the most frequent); invoices in other currencies are not in the cost bars */
  currency: string
  months: string[]
  consumption: (number | null)[]
  gross: (number | null)[]
}

const mostFrequent = (values: (string | null | undefined)[]) => {
  const counts = new Map<string, number>()
  for (const v of values) if (v != null) counts.set(v, (counts.get(v) ?? 0) + 1)
  return [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0]?.[0] ?? ''
}

/**
 * Monthly consumption and gross cost per medium (by the month of the period start), from the first to the last
 * month with invoices; months without invoices are null. Credit notes reduce the month.
 */
export function monthlySeries(invoices: readonly Invoice[]): MonthlySeries[] {
  const result: MonthlySeries[] = []
  for (const medium of INVOICE_MEDIA) {
    const list = invoices.filter((inv) => invoiceMedium(inv) === medium && invoiceMonth(inv) != null)
    if (!list.length) continue
    const unit = mostFrequent(list.filter((i) => isNumber(i.consumption)).map((i) => i.consumptionUnit ?? ''))
    const currency = mostFrequent(list.filter((i) => isNumber(i.gross)).map((i) => i.currency ?? ''))
    const keys = list.map((i) => invoiceMonth(i)!).sort()
    const months = monthRange(keys[0], keys[keys.length - 1])
    const index = new Map(months.map((m, i) => [m, i]))
    const consumption: (number | null)[] = months.map(() => null)
    const gross: (number | null)[] = months.map(() => null)
    for (const inv of list) {
      const i = index.get(invoiceMonth(inv)!)!
      const c = signed(inv.consumption, inv.credit)
      if (c != null && (inv.consumptionUnit ?? '') === unit) consumption[i] = (consumption[i] ?? 0) + c
      const g = signed(inv.gross, inv.credit)
      if (g != null && (inv.currency ?? '') === currency) gross[i] = (gross[i] ?? 0) + g
    }
    result.push({ medium, unit, currency, months, consumption, gross })
  }
  return result
}

const INK = '#1a2a45'
const MUTED = '#5b6576'
const LINE = '#d9dee6'
const ACCENT = '#00538a'
const COST = '#e0b800'
const FONT = 'Montserrat, Segoe UI, Arial, sans-serif'

export interface MonthlyChartLabels {
  consumption: string
  cost: string
  /** "Okt. 2026" of a yyyy-MM month */
  month: (key: string) => string
}

/** Column chart of one medium: consumption on the left axis, gross cost on the right axis. */
export function monthlyChartOption(s: MonthlySeries, labels: MonthlyChartLabels, locale: string): EChartsOption {
  const consumptionName = s.unit ? `${labels.consumption} [${s.unit}]` : labels.consumption
  const costName = s.currency ? `${labels.cost} [${s.currency}]` : labels.cost
  const fmt = (v: number) => formatNumber(v, locale, { min: 0, max: Math.abs(v) >= 100 ? 0 : 2 })
  return {
    animation: false,
    textStyle: { fontFamily: FONT, color: INK },
    grid: { left: 8, right: 8, top: 40, bottom: 8, containLabel: true },
    legend: { top: 0, left: 0, textStyle: { color: INK, fontFamily: FONT }, itemWidth: 12, itemHeight: 10 },
    xAxis: {
      type: 'category',
      data: s.months.map(labels.month),
      axisLine: { lineStyle: { color: LINE } },
      axisLabel: { color: MUTED, hideOverlap: true },
    },
    yAxis: [
      { type: 'value', name: s.unit, nameTextStyle: { color: MUTED, align: 'left' }, splitLine: { lineStyle: { color: LINE } }, axisLabel: { color: MUTED, formatter: fmt } },
      { type: 'value', name: s.currency, nameTextStyle: { color: MUTED, align: 'right' }, splitLine: { show: false }, axisLabel: { color: MUTED, formatter: fmt } },
    ],
    tooltip: {
      trigger: 'axis',
      confine: true,
      formatter: (raw: unknown) => {
        const ps = (Array.isArray(raw) ? raw : [raw]) as { axisValueLabel?: string; seriesName?: string; value?: number | null; marker?: string }[]
        if (!ps.length) return ''
        const head = `<div style="font-weight:600">${escapeHtml(ps[0].axisValueLabel ?? '')}</div>`
        const lines = ps
          .filter((p) => p.value != null)
          .map((p) => `<div>${p.marker ?? ''}${escapeHtml(p.seriesName ?? '')}: <b>${escapeHtml(formatNumber(p.value as number, locale, { min: 0, max: 2 }))}</b></div>`)
        return head + lines.join('')
      },
    },
    series: [
      { type: 'bar', name: consumptionName, yAxisIndex: 0, data: s.consumption, color: ACCENT, barMaxWidth: 18 },
      { type: 'bar', name: costName, yAxisIndex: 1, data: s.gross, color: COST, barMaxWidth: 18 },
    ],
  } as EChartsOption
}

/** "Okt. 2026" of yyyy-MM. */
export function monthLabel(key: string, locale: string): string {
  const [y, m] = key.split('-').map(Number)
  if (!y || !m) return key
  return new Intl.DateTimeFormat(locale, { month: 'short', year: 'numeric', timeZone: 'UTC' }).format(Date.UTC(y, m - 1, 1))
}

/** Period of an invoice ("01.10.2026 – 31.10.2026"), else the invoice date. */
export function invoicePeriod(inv: Pick<Invoice, 'periodStart' | 'periodEnd'>, locale: string): string {
  return formatPeriod(inv.periodStart, inv.periodEnd, locale)
}

export const invoiceDate = (inv: Pick<Invoice, 'date'>, locale: string) => formatLocalDate(inv.date, locale)

/** An amount with 2 decimals (no currency sign; the currency has its own column). */
export function formatAmount(value: number | null | undefined, locale: string): string {
  return isNumber(value) ? formatNumber(value, locale, { min: 2, max: 2 }) : ''
}

/** A consumption with up to 3 decimals; with unit if given. */
export function formatConsumption(value: number | null | undefined, unit: string | null | undefined, locale: string): string {
  if (!isNumber(value)) return ''
  const text = formatNumber(value, locale, { min: 0, max: 3 })
  return unit ? `${text} ${unit}` : text
}

/** "13.744,50 EUR"; '' if not a number. */
export function amountWithCurrency(value: number | null | undefined, currency: string | null | undefined, locale: string): string {
  const text = formatAmount(value, locale)
  return text && currency ? `${text} ${currency}` : text
}

export interface InvoiceCsvHeaders {
  periodStart: string
  periodEnd: string
  number: string
  date: string
  seller: string
  medium: string
  meterPoint: string
  consumption: string
  unit: string
  net: string
  tax: string
  gross: string
  currency: string
  credit: string
  site: string
  path: string
}

/**
 * CSV of the shown invoices: days as stored (2026-10-01), numbers as stored (credit notes negative) without grouping
 * and with the decimal separator of the locale, UTF-8 with BOM and CRLF, separators of the locale, formulas escaped.
 */
export function invoicesCsv(
  invoices: readonly Invoice[],
  headers: InvoiceCsvHeaders,
  env: { mediumName: (m: InvoiceMedium) => string; yes: string },
  locale: string,
): string {
  const { field, decimal } = csvSeparators(locale)
  const head = [
    headers.periodStart,
    headers.periodEnd,
    headers.number,
    headers.date,
    headers.seller,
    headers.medium,
    headers.meterPoint,
    headers.consumption,
    headers.unit,
    headers.net,
    headers.tax,
    headers.gross,
    headers.currency,
    headers.credit,
    headers.site,
    headers.path,
  ]
  const text = (v: string | null | undefined) => (v ? csvField(v, field) : '')
  const number = (v: number | null | undefined) => (isNumber(v) ? csvNumber(v, decimal) : '')
  const lines = [head.map((h) => csvField(h, field)).join(field)]
  for (const inv of invoices) {
    lines.push(
      [
        text(inv.periodStart),
        text(inv.periodEnd),
        text(inv.number),
        text(inv.date),
        text(inv.seller),
        text(env.mediumName(invoiceMedium(inv))),
        text(inv.meterPoint),
        number(inv.consumption),
        text(inv.consumptionUnit),
        number(inv.net),
        number(inv.tax),
        number(inv.gross),
        text(inv.currency),
        inv.credit ? text(env.yes) : '',
        text(inv.site?.name),
        text(inv.path),
      ].join(field),
    )
  }
  return CSV_BOM + lines.join('\r\n') + '\r\n'
}

/** The stored file is a PDF (by its name); others (XML) are only downloaded. */
export function isPdfFile(name: string | null | undefined): boolean {
  return !!name && /\.pdf$/i.test(name.trim())
}

/** Preferred order of the line item keys; other keys follow in the order they appear. */
export const ITEM_KEYS = ['description', 'quantity', 'unit', 'unitPrice', 'net', 'taxRate', 'tax', 'gross', 'period'] as const

/** Columns of the line items table: the keys with a value in at least one item; periodStart/periodEnd become "period". */
export function itemColumns(items: readonly InvoiceItem[]): string[] {
  const present: string[] = []
  const add = (key: string) => {
    if (!present.includes(key)) present.push(key)
  }
  for (const item of items) {
    if (!item || typeof item !== 'object') continue
    for (const [key, value] of Object.entries(item)) {
      if (value == null || value === '') continue
      add(key === 'periodStart' || key === 'periodEnd' ? 'period' : key)
    }
  }
  const rank = (key: string) => {
    const i = (ITEM_KEYS as readonly string[]).indexOf(key)
    return i < 0 ? ITEM_KEYS.length : i
  }
  return present.map((k, i) => ({ k, i })).sort((a, b) => rank(a.k) - rank(b.k) || a.i - b.i).map((x) => x.k)
}

/** Numeric columns of the line items (right aligned). */
export const NUMERIC_ITEM_KEYS = new Set(['quantity', 'unitPrice', 'net', 'taxRate', 'tax', 'gross'])

/** A cell of the line items table: numbers in the locale (tax rate with %), dates local, objects as JSON. */
export function itemCell(item: InvoiceItem, key: string, locale: string): string {
  if (!item || typeof item !== 'object') return ''
  if (key === 'period') {
    const start = typeof item.periodStart === 'string' ? item.periodStart : null
    const end = typeof item.periodEnd === 'string' ? item.periodEnd : null
    if (start || end) return formatPeriod(start, end, locale)
    return typeof item.period === 'string' ? item.period : ''
  }
  const v = item[key]
  if (v == null || v === '') return ''
  if (isNumber(v)) {
    if (key === 'taxRate') return formatNumber(v, locale, { min: 0, max: 2 }) + ' %'
    if (key === 'unitPrice') return formatNumber(v, locale, { min: 2, max: 6 })
    if (key === 'net' || key === 'tax' || key === 'gross') return formatAmount(v, locale)
    return formatNumber(v, locale, { min: 0, max: 4 })
  }
  if (typeof v === 'string') return /^\d{4}-\d{2}-\d{2}$/.test(v) ? formatLocalDate(v, locale) : v
  if (typeof v === 'boolean') return String(v)
  try {
    return JSON.stringify(v)
  } catch {
    return ''
  }
}

/** File name of the download: the stored name, else "<number>.pdf". */
export function invoiceFileName(inv: Pick<Invoice, 'file' | 'number' | 'id'>): string {
  return inv.file?.trim() || `${inv.number || 'invoice-' + inv.id}.pdf`
}
