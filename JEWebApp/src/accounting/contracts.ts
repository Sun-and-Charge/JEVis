/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { EChartsOption } from 'echarts'
import { escapeHtml, formatNumber, type Decimals } from '../analysis/format'
import { parseTime } from '../alarms/alarms'
import {
  CONTRACT_KINDS,
  type AccountingConfiguration,
  type ContractAttribute,
  type ContractHistory,
  type ContractList,
  type ContractObject,
  type ContractSite,
  type RangingStep,
} from '../api/contracts'
import { formatLocalDate } from '../modules/dates'
import { matchesSite, matchesTerms, searchTerms, siteOptions, type SiteFilter, type SiteOption } from '../modules/search'

/*
 * Contracts (W4.7, view only) like the Control Center's Accounting Plugin: the contract objects per group (tabs),
 * their attributes (numbers with unit, ranging values as from–to → value, texts), the price history of an attribute
 * and the contract fields of the accounting configurations.
 */

/** Tab of the configurations ("Abrechnung"), after the groups. */
export const CONFIGURATIONS_TAB = 'configurations'

export interface ContractTab {
  /** group kind or {@link CONFIGURATIONS_TAB} */
  id: string
  objects: ContractObject[]
  configurations: AccountingConfiguration[]
}

/** Tabs: the groups in the fixed order (unknown kinds after them), then the configurations; empty ones left out. */
export function contractTabs(list: ContractList | null | undefined): ContractTab[] {
  const groups = (list?.groups ?? []).filter((g) => g && Array.isArray(g.objects) && g.objects.length)
  const rank = (kind: string) => {
    const i = (CONTRACT_KINDS as readonly string[]).indexOf(kind)
    return i < 0 ? CONTRACT_KINDS.length : i
  }
  const tabs: ContractTab[] = [...groups]
    .map((g, i) => ({ g, i }))
    .sort((a, b) => rank(a.g.kind) - rank(b.g.kind) || a.i - b.i)
    .map(({ g }) => ({ id: g.kind, objects: g.objects, configurations: [] }))
  const configurations = list?.configurations ?? []
  if (configurations.length) tabs.push({ id: CONFIGURATIONS_TAB, objects: [], configurations })
  return tabs
}

const siteOf = (s: ContractSite | null | undefined) => (s ? { id: s.id, name: s.name, names: s.names ?? {} } : null)

/** Site name in the UI language. */
export const siteName = (s: ContractSite | null | undefined, locale: string) => (s ? s.names?.[locale] || s.name : '')

/** "Musterwerk / Energieverträge / Energielieferung"; '' if neither. */
export function contractWhere(entry: { site: ContractSite | null; path: string | null }, locale: string): string {
  return [siteName(entry.site, locale), entry.path].filter(Boolean).join(' / ')
}

/** Sites of all objects and configurations for the site filter. */
export function contractSites(list: ContractList | null | undefined, locale: string): SiteOption[] {
  const entries = [...(list?.groups ?? []).flatMap((g) => g.objects ?? []), ...(list?.configurations ?? [])]
  return siteOptions(entries.map((e) => ({ site: siteOf(e.site) })), locale)
}

/** True if some object or configuration has no site (then the filter offers "Ohne Standort"). */
export function hasEntriesWithoutSite(list: ContractList | null | undefined): boolean {
  return [...(list?.groups ?? []).flatMap((g) => g.objects ?? []), ...(list?.configurations ?? [])].some((e) => !e.site)
}

export interface ContractFilter {
  text: string
  site: SiteFilter
}

export function defaultContractFilter(): ContractFilter {
  return { text: '', site: 'all' }
}

/** Texts searched in an object: name, class label, path, site, contractor and the text values of its attributes. */
export function objectSearchTexts(o: ContractObject, locale: string): string[] {
  const texts = [o.name, o.classLabel, o.className, o.path, siteName(o.site, locale), o.contractor?.readable ? o.contractor.name ?? '' : '']
  for (const a of o.attributes ?? []) if (typeof a.value === 'string') texts.push(a.value)
  return texts.filter(Boolean) as string[]
}

export function filterContractObjects(objects: readonly ContractObject[], filter: ContractFilter, locale: string): ContractObject[] {
  const terms = searchTerms(filter.text, locale)
  return objects.filter((o) => matchesSite(siteOf(o.site), filter.site) && matchesTerms(terms, objectSearchTexts(o, locale), locale))
}

export function filterConfigurations(configurations: readonly AccountingConfiguration[], filter: ContractFilter, locale: string): AccountingConfiguration[] {
  const terms = searchTerms(filter.text, locale)
  return configurations.filter(
    (c) =>
      matchesSite(siteOf(c.site), filter.site) &&
      matchesTerms(terms, [c.name, c.path, siteName(c.site, locale), c.contractNumber, c.contractType, c.marketLocation, c.energyType], locale),
  )
}

const isNumber = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v)

/** True if the value is a list of ranging steps. */
export function isRanging(value: unknown): value is RangingStep[] {
  return Array.isArray(value)
}

/** Decimals of prices: up to 5 below 1 (0,28345 €/kWh), else up to 4. */
export function priceDecimals(value: number): Decimals {
  const abs = Math.abs(value)
  return { min: 0, max: abs > 0 && abs < 1 ? 5 : 4 }
}

/** "0,28 €/kWh"; '' if not a number. */
export function contractNumber(value: number | null | undefined, unit: string | null | undefined, locale: string): string {
  if (!isNumber(value)) return ''
  const text = formatNumber(value, locale, priceDecimals(value))
  return unit ? `${text} ${unit}` : text
}

export interface RangingRow {
  from: string
  to: string
  value: string
}

/** Rows of a ranging value sorted by their start: bounds as numbers, the value with unit; missing parts ''. */
export function rangingRows(steps: readonly RangingStep[] | null | undefined, unit: string | null | undefined, locale: string): RangingRow[] {
  const bound = (v: number | null | undefined) => (isNumber(v) ? formatNumber(v, locale, { min: 0, max: 4 }) : '')
  return [...(steps ?? [])]
    .filter((s) => s && typeof s === 'object')
    .map((s, i) => ({ s, i }))
    .sort((a, b) => (a.s.from ?? -Infinity) - (b.s.from ?? -Infinity) || a.i - b.i)
    .map(({ s }) => ({ from: bound(s.from), to: bound(s.to), value: contractNumber(s.value, unit, locale) }))
}

/** Compact text of a ranging value: "0–100: 12,5 €/kW · ab 100: 10 €/kW" (`openEnd` builds "ab 100"). */
export function rangingText(steps: readonly RangingStep[] | null | undefined, unit: string | null | undefined, locale: string, openEnd: (from: string) => string): string {
  return rangingRows(steps, unit, locale)
    .map((r) => {
      const range = r.to ? `${r.from || '0'}–${r.to}` : r.from ? openEnd(r.from) : ''
      return [range, r.value].filter(Boolean).join(': ')
    })
    .filter(Boolean)
    .join(' · ')
}

/** Value of an attribute as text: numbers with unit in the UI locale, texts as they are, ranging values compact; '' if empty. */
export function attributeText(a: Pick<ContractAttribute, 'type' | 'value' | 'unit'>, locale: string, openEnd: (from: string) => string): string {
  const v = a.value
  if (v == null || v === '') return ''
  if (isRanging(v)) return rangingText(v, a.unit, locale, openEnd)
  if (isNumber(v)) return contractNumber(v, a.unit, locale)
  return String(v)
}

/** The attribute can show a history (more than one sample of a number or ranging value). */
export function hasHistory(a: Pick<ContractAttribute, 'type' | 'history'>): boolean {
  return !!a.history && (a.type === 'number' || a.type === 'ranging')
}

/** Date of a sample (valid from) as in the UI: 01.01.2026; '' if missing. */
export function sampleDate(timestamp: string | null | undefined, locale: string): string {
  return timestamp ? formatLocalDate(timestamp, locale) : ''
}

/** Numeric points of a history, oldest first (the answer is newest first); others left out. */
export function historyPoints(history: Pick<ContractHistory, 'samples'> | null | undefined): [number, number][] {
  const out: [number, number][] = []
  for (const s of history?.samples ?? []) {
    const ms = parseTime(s?.timestamp)
    if (ms != null && isNumber(s.value)) out.push([ms, s.value])
  }
  return out.sort((a, b) => a[0] - b[0])
}

export interface HistoryLine {
  timestamp: string
  date: string
  value: string
}

/** Lines of the history table, newest first as sent, at most `limit`. */
export function historyLines(history: ContractHistory | null | undefined, locale: string, openEnd: (from: string) => string, limit = 12): HistoryLine[] {
  const unit = history?.unit ?? null
  return (history?.samples ?? []).slice(0, limit).map((s) => ({
    timestamp: s.timestamp,
    date: sampleDate(s.timestamp, locale),
    value: isRanging(s.value) ? rangingText(s.value, unit, locale, openEnd) : contractNumber(s.value as number | null, unit, locale),
  }))
}

const INK = '#1a2a45'
const MUTED = '#5b6576'
const LINE = '#d9dee6'
const ACCENT = '#00538a'
const FONT = 'Montserrat, Segoe UI, Arial, sans-serif'

/**
 * Step chart of a price history: each value holds until the next one (step "end"); the last value is drawn on to
 * `now` (if later), so a single change is still visible as a line.
 */
export function historyChartOption(points: readonly [number, number][], name: string, unit: string | null, locale: string, now: number = Date.now()): EChartsOption {
  const data = points.map(([t, v]) => [t, v])
  const last = points.at(-1)
  if (last && now > last[0]) data.push([now, last[1]])
  const suffix = unit ? ' ' + unit : ''
  const fmt = (v: number) => formatNumber(v, locale, priceDecimals(v))
  return {
    animation: false,
    textStyle: { fontFamily: FONT, color: INK },
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    xAxis: { type: 'time', axisLine: { lineStyle: { color: LINE } }, axisLabel: { color: MUTED, hideOverlap: true }, splitLine: { show: false } },
    yAxis: {
      type: 'value',
      scale: true,
      name: unit ?? '',
      nameTextStyle: { color: MUTED, align: 'left' },
      splitLine: { lineStyle: { color: LINE } },
      axisLabel: { color: MUTED, formatter: fmt },
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      formatter: (raw: unknown) => {
        const p = (Array.isArray(raw) ? raw[0] : raw) as { value?: [number, number] } | undefined
        if (!p?.value) return ''
        const [t, v] = p.value
        const day = new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(t)
        return `<div style="font-weight:600">${escapeHtml(day)}</div><div>${escapeHtml(name)}: <b>${escapeHtml(fmt(v))}</b>${escapeHtml(suffix)}</div>`
      },
    },
    series: [{ type: 'line', name, step: 'end', data, color: ACCENT, showSymbol: true, symbolSize: 5, lineStyle: { width: 2 } }],
  } as EChartsOption
}

/**
 * Fields of a configuration in the order of the Control Center's dialog. `contractType` holds the energy type
 * (ELECTRICITY, GAS, COMMUNITY_HEATING) and `energyType` the same value, so only one "energy type" field is shown.
 */
export const CONFIGURATION_FIELDS = ['contractNumber', 'energyType', 'marketLocation', 'contractDate', 'firstRate', 'periodOfNotice', 'start', 'end'] as const
export type ConfigurationField = (typeof CONFIGURATION_FIELDS)[number]

/** Date fields (yyyy-MM-dd; texts that are no date are shown unchanged). */
const DATE_FIELDS = new Set<ConfigurationField>(['contractDate', 'firstRate', 'periodOfNotice', 'start', 'end'])

/** A field for the UI: dates local, the energy type (energyType, else contractType) translated by `energyName`, others as stored; '' if empty. */
export function configurationValue(c: AccountingConfiguration, field: ConfigurationField, locale: string, energyName: (code: string) => string): string {
  const v = field === 'energyType' ? (c.energyType || c.contractType) : c[field]
  if (v == null || v === '') return ''
  const text = String(v)
  if (DATE_FIELDS.has(field)) return formatLocalDate(text, locale)
  if (field === 'energyType') return energyName(text)
  return text
}
