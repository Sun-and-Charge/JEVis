/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { InvoiceMedium, ManualInvoiceBody } from '../api/invoices'
import { parseNumber } from '../readings/expected'

/*
 * "Rechnung erfassen" (R01, docs/ENERGY-INVOICES.md "Manual invoices"): an invoice without e-invoice file, typed from
 * a paper or PDF invoice. The server stores it like an imported e-invoice (format MANUAL, same duplicate check and
 * series); amounts of the lines follow from quantity × unit price, the totals from the lines, the consumption from
 * the lines with energy units (kWh, MWh, m³) unless the user enters it.
 */

/** Units of a line (UN/ECE codes as in e-invoices); the energy units count as consumption. */
export const LINE_UNITS = ['KWH', 'MWH', 'MTQ', 'KWT', 'MON', 'DAY', 'ANN', 'C62', 'LS'] as const
export type LineUnit = (typeof LINE_UNITS)[number] | ''
export const ENERGY_UNITS: readonly string[] = ['KWH', 'MWH', 'MTQ']
export const TAX_RATES = ['19', '7', '0'] as const

export interface LineForm {
  uid: string
  description: string
  quantity: string
  unit: LineUnit
  unitPrice: string
  /** empty: quantity × unit price */
  net: string
  taxRate: string
}

export interface ManualForm {
  directory: number | ''
  medium: InvoiceMedium | ''
  seller: string
  sellerVatId: string
  number: string
  date: string
  periodStart: string
  periodEnd: string
  meterPoint: string
  creditNote: boolean
  lines: LineForm[]
  /** own totals instead of the sums of the lines (e.g. rounding of the supplier) */
  ownTotals: boolean
  net: string
  tax: string
  gross: string
  /** own consumption instead of the energy lines */
  ownConsumption: boolean
  consumption: string
  consumptionUnit: 'KWH' | 'MWH' | 'MTQ'
}

export type ManualProblem =
  | 'directory'
  | 'medium'
  | 'seller'
  | 'number'
  | 'date'
  | 'period'
  | 'periodOrder'
  | 'lines'
  | 'number:quantity'
  | 'number:unitPrice'
  | 'number:net'
  | 'number:taxRate'
  | 'lineAmount'
  | 'lineDescription'
  | 'totals'
  | 'consumption'

let next = 0
const uid = () => `ml${++next}`

export function newLine(over: Partial<LineForm> = {}): LineForm {
  return { uid: uid(), description: '', quantity: '', unit: '', unitPrice: '', net: '', taxRate: '19', ...over }
}

export function newManualForm(directory: number | '' = ''): ManualForm {
  return {
    directory,
    medium: '',
    seller: '',
    sellerVatId: '',
    number: '',
    date: '',
    periodStart: '',
    periodEnd: '',
    meterPoint: '',
    creditNote: false,
    lines: [newLine({ unit: 'KWH' }), newLine({ unit: 'MON' })],
    ownTotals: false,
    net: '',
    tax: '',
    gross: '',
    ownConsumption: false,
    consumption: '',
    consumptionUnit: 'KWH',
  }
}

const round2 = (n: number) => Math.round(n * 100) / 100

/** the net amount of a line: entered, else quantity × unit price (2 decimals); null if neither */
export function lineNet(line: LineForm): number | null {
  const own = parseNumber(line.net)
  if (own != null) return own
  const q = parseNumber(line.quantity)
  const p = parseNumber(line.unitPrice)
  return q != null && p != null ? round2(q * p) : null
}

const filled = (line: LineForm) => [line.description, line.quantity, line.unitPrice, line.net].some((v) => v.trim() !== '')

export interface Totals {
  net: number | null
  tax: number | null
  gross: number | null
  /** kWh or m³ (MWh counted as kWh); null if no energy line */
  consumption: { value: number; unit: 'kWh' | 'm³' } | null
}

/**
 * Sums of the filled lines (tax per line rounded to cents, as the server does); the consumption is the sum of the
 * kWh lines (MWh × 1000), else of the m³ lines. Energy invoices often bill the same quantity several times (energy
 * price, grid fee, tax), so the sum counts only distinct quantities per unit — enter the consumption yourself if that
 * does not fit.
 */
export function lineTotals(form: ManualForm): Totals {
  let net = 0
  let tax = 0
  let any = false
  let taxKnown = true
  const kwh = new Set<number>()
  const m3 = new Set<number>()
  for (const line of form.lines.filter(filled)) {
    const n = lineNet(line)
    if (n == null) continue
    any = true
    net += n
    const rate = parseNumber(line.taxRate)
    if (rate == null) taxKnown = false
    else tax += round2((n * rate) / 100)
    const q = parseNumber(line.quantity)
    if (q != null && line.unit === 'KWH') kwh.add(q)
    if (q != null && line.unit === 'MWH') kwh.add(q * 1000)
    if (q != null && line.unit === 'MTQ') m3.add(q)
  }
  const sum = (s: Set<number>) => [...s].reduce((a, b) => a + b, 0)
  const consumption = kwh.size ? { value: sum(kwh), unit: 'kWh' as const } : m3.size ? { value: sum(m3), unit: 'm³' as const } : null
  if (!any) return { net: null, tax: null, gross: null, consumption }
  net = round2(net)
  tax = round2(tax)
  return { net, tax: taxKnown ? tax : null, gross: taxKnown ? round2(net + tax) : null, consumption }
}

/** The totals that will be stored: the user's own ones or the sums of the lines. */
export function effectiveTotals(form: ManualForm): Totals {
  const lines = lineTotals(form)
  const totals = form.ownTotals ? { net: parseNumber(form.net), tax: parseNumber(form.tax), gross: parseNumber(form.gross), consumption: lines.consumption } : lines
  if (form.ownTotals && totals.gross == null && totals.net != null && totals.tax != null) totals.gross = round2(totals.net + totals.tax)
  if (form.ownConsumption) {
    const v = parseNumber(form.consumption)
    const unit = form.consumptionUnit
    totals.consumption = v == null ? null : unit === 'MTQ' ? { value: v, unit: 'm³' } : { value: unit === 'MWH' ? v * 1000 : v, unit: 'kWh' }
  }
  return totals
}

/** Problems by field: `lines[i].<problem>` for a line, else the field name. */
export function checkManual(form: ManualForm): Map<string, ManualProblem> {
  const problems = new Map<string, ManualProblem>()
  if (form.directory === '') problems.set('directory', 'directory')
  if (!form.medium) problems.set('medium', 'medium')
  if (!form.seller.trim()) problems.set('seller', 'seller')
  if (!form.number.trim()) problems.set('number', 'number')
  if (!form.date) problems.set('date', 'date')
  if (!!form.periodStart !== !!form.periodEnd) problems.set('period', 'period')
  else if (form.periodStart && form.periodEnd < form.periodStart) problems.set('period', 'periodOrder')
  form.lines.forEach((line, i) => {
    if (!filled(line)) return
    const at = `lines[${i}].`
    for (const key of ['quantity', 'unitPrice', 'net', 'taxRate'] as const) {
      if (line[key].trim() && parseNumber(line[key]) == null) problems.set(at + key, `number:${key}` as ManualProblem)
    }
    if (!line.description.trim()) problems.set(at + 'description', 'lineDescription')
    if (lineNet(line) == null && !problems.has(at + 'net')) problems.set(at + 'net', 'lineAmount')
  })
  const totals = effectiveTotals(form)
  if (form.ownTotals) {
    if (totals.net == null || totals.gross == null) problems.set('totals', 'totals')
  } else if (!form.lines.some(filled)) {
    problems.set('lines', 'lines')
  } else if (totals.gross == null && !problems.size) {
    problems.set('totals', 'totals')
  }
  if (form.ownConsumption && form.consumption.trim() && parseNumber(form.consumption) == null) problems.set('consumption', 'consumption')
  return problems
}

const text = (s: string) => (s.trim() ? s.trim() : undefined)
const num = (s: string) => {
  const n = parseNumber(s)
  return n == null ? undefined : n
}

/** The body of POST /invoices/manual (only filled lines; amounts as numbers). */
export function manualBody(form: ManualForm): ManualInvoiceBody {
  const body: ManualInvoiceBody = {
    medium: form.medium as InvoiceMedium,
    seller: form.seller.trim(),
    number: form.number.trim(),
    date: form.date,
    sellerVatId: text(form.sellerVatId),
    meterPoint: text(form.meterPoint.replace(/\s+/g, '')),
    creditNote: form.creditNote || undefined,
    lines: form.lines.filter(filled).map((l) => ({
      description: l.description.trim(),
      quantity: num(l.quantity),
      unit: l.unit || undefined,
      unitPrice: num(l.unitPrice),
      net: num(l.net),
      taxRate: num(l.taxRate),
    })),
  }
  if (form.periodStart && form.periodEnd) {
    body.periodStart = form.periodStart
    body.periodEnd = form.periodEnd
  }
  if (form.ownTotals) {
    body.net = num(form.net)
    body.tax = num(form.tax)
    body.gross = num(form.gross)
  }
  /* the consumption shown in the form is the one stored (the server's analysis of the lines could differ) */
  const consumption = effectiveTotals(form).consumption
  if (consumption) body.consumption = { value: consumption.value, unit: consumption.unit === 'm³' ? 'MTQ' : 'KWH' }
  return body
}

/** Whether the form differs from a new one (for the unsaved-changes question). */
export function manualDirty(form: ManualForm): boolean {
  const fresh = newManualForm(form.directory)
  const strip = (f: ManualForm) => JSON.stringify({ ...f, directory: 0, lines: f.lines.map(({ uid: _u, ...rest }) => rest) })
  return strip(form) !== strip(fresh)
}
