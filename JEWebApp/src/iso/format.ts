/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { formatNumber } from '../analysis/format'
import type { Npv, NpvYearRow } from '../api/iso'
import { formatLocalDate } from '../modules/dates'

const TWO = { min: 2, max: 2 }
const UP_TO_TWO = { min: 0, max: 2 }

const isNumber = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v)

/** 1.234,5 (up to two decimals); '' if missing. */
export function formatAmount(value: number | null | undefined, locale: string): string {
  return isNumber(value) ? formatNumber(value, locale, UP_TO_TWO) : ''
}

const money = new Map<string, Intl.NumberFormat>()

/** 1.234,50 € like the Control Center's currency format; '' if missing. */
export function formatMoney(value: number | null | undefined, locale: string): string {
  if (!isNumber(value)) return ''
  let f = money.get(locale)
  if (!f) {
    f = new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR', minimumFractionDigits: 2, maximumFractionDigits: 2 })
    money.set(locale, f)
  }
  return f.format(value)
}

/** A value with unit: "20 kWh"; '' if missing. */
export function formatWithUnit(value: number | null | undefined, unit: string | null | undefined, locale: string): string {
  if (!isNumber(value)) return ''
  const text = formatNumber(value, locale, UP_TO_TWO)
  return unit ? `${text} ${unit}` : text
}

/** Percent values as stored (10.0 → "10 %"). */
export function formatPercent(value: number | null | undefined, locale: string): string {
  if (!isNumber(value)) return ''
  return new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 2 }).format(value / 100)
}

/** Profitability index etc.: two decimals. */
export function formatFactor(value: number | null | undefined, locale: string): string {
  return isNumber(value) ? formatNumber(value, locale, TWO) : ''
}

/** Date of a stored date (the day as written, without time). */
export function formatIsoDate(text: string | null | undefined, locale: string): string {
  return text ? formatLocalDate(text.slice(0, 10), locale) : ''
}

/** One labelled figure of the capital section; `label` is an i18n key. */
export interface Figure {
  label: string
  text: string
}

/** NPV inputs for display (missing values left out). */
export function npvInputs(npv: Npv | null | undefined, locale: string): Figure[] {
  if (!npv) return []
  const figures: Figure[] = [
    { label: 'iso.npv.investment', text: formatMoney(npv.investment, locale) },
    { label: 'iso.npv.saving', text: formatMoney(npv.saving, locale) },
    { label: 'iso.npv.runningCost', text: formatMoney(npv.runningCost, locale) },
    { label: 'iso.npv.interestRate', text: formatPercent(npv.interestRate, locale) },
    { label: 'iso.npv.inflation', text: formatPercent(npv.inflation, locale) },
    { label: 'iso.npv.years', text: isNumber(npv.years) ? String(npv.years) : '' },
    { label: 'iso.npv.duration', text: isNumber(npv.duration) ? String(npv.duration) : '' },
  ]
  return figures.filter((f) => f.text)
}

/** NPV results as stored (missing values left out). */
export function npvResults(npv: Npv | null | undefined, locale: string): Figure[] {
  if (!npv) return []
  const figures: Figure[] = [
    { label: 'iso.npv.result', text: formatMoney(npv.result, locale) },
    { label: 'iso.npv.profitabilityIndex', text: formatFactor(npv.profitabilityIndex, locale) },
    { label: 'iso.npv.resultOverX', text: formatMoney(npv.resultOverX, locale) },
    { label: 'iso.npv.profitabilityIndexOverX', text: formatFactor(npv.profitabilityIndexOverX, locale) },
    { label: 'iso.npv.sumDeposits', text: formatMoney(npv.sumDeposits, locale) },
    { label: 'iso.npv.sumPayouts', text: formatMoney(npv.sumPayouts, locale) },
    { label: 'iso.npv.sumNet', text: formatMoney(npv.sumNet, locale) },
  ]
  return figures.filter((f) => f.text)
}

export interface YearTotals {
  deposit: number
  cost: number
  net: number
  discounted: number
}

/** Sums of the year table (missing values count as 0). */
export function yearTotals(rows: readonly NpvYearRow[] | null | undefined): YearTotals {
  const totals: YearTotals = { deposit: 0, cost: 0, net: 0, discounted: 0 }
  for (const row of rows ?? []) {
    for (const key of ['deposit', 'cost', 'net', 'discounted'] as const) {
      const v = row[key]
      if (isNumber(v)) totals[key] += v
    }
  }
  return totals
}

/** "Process Document" / "processDocument" / "new_enpi" → readable fallback label for unknown check list keys. */
export function humanizeKey(key: string): string {
  const spaced = key
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z\d])([A-Z])/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim()
  return spaced ? spaced.charAt(0).toUpperCase() + spaced.slice(1) : key
}

/** Bytes as "12,1 kB". */
export function formatSize(bytes: number | null | undefined, locale: string): string {
  if (!isNumber(bytes) || bytes < 0) return ''
  const units = ['byte', 'kilobyte', 'megabyte', 'gigabyte'] as const
  let value = bytes
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return new Intl.NumberFormat(locale, { style: 'unit', unit: units[i], unitDisplay: 'short', maximumFractionDigits: i ? 1 : 0 }).format(value)
}
