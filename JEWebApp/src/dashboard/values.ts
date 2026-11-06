/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { DashboardData, DashboardRowConfig, WidgetConfig, WidgetData, WidgetRowData } from '../api/dashboard'
import { formatNumber, type Decimals } from '../analysis/format'
import type { RowState } from '../analysis/model'

/** Default decimals of the Control Center widgets. */
export const DEFAULT_DECIMALS = 2

const clamp = (n: number) => Math.min(20, Math.max(0, Math.round(n)))

/**
 * Decimals of a widget value like the Control Center (`Widget.getDecimals`): the decimals of the row when set,
 * with "auto" the decimals of the data row (the server puts them into `decimalDigits`, see report), otherwise those
 * of the widget (default 2).
 */
export function widgetDecimals(widget: Pick<WidgetConfig, 'decimals' | 'decimalsAuto'>, row?: Pick<DashboardRowConfig, 'decimalDigits'> | null): Decimals {
  if (row && typeof row.decimalDigits === 'number' && row.decimalDigits >= 0) {
    const d = clamp(row.decimalDigits)
    return { min: d, max: d }
  }
  if (widget.decimalsAuto) return { min: 0, max: DEFAULT_DECIMALS }
  const d = typeof widget.decimals === 'number' && widget.decimals >= 0 ? clamp(widget.decimals) : DEFAULT_DECIMALS
  return { min: d, max: d }
}

/** A row of a widget joined with its data. */
export interface WidgetRow {
  config: DashboardRowConfig
  data: WidgetRowData | null
  state: RowState
  value: number | null
  text: string | null
  unit: string
}

function state(config: DashboardRowConfig, data: WidgetRowData | null): RowState {
  if (config.readable === false) return 'NO_PERMISSION'
  if (!data) return 'NO_DATA'
  switch (data.status) {
    case 'OK':
      return (typeof data.value === 'number' && Number.isFinite(data.value)) || data.text != null || (data.samples?.length ?? 0) > 0 ? 'OK' : 'NO_DATA'
    case 'NO_PERMISSION':
    case 'ERROR':
      return data.status
    default:
      return 'NO_DATA'
  }
}

export function widgetRows(widget: Pick<WidgetConfig, 'rows'>, data: WidgetData | null | undefined): WidgetRow[] {
  const byKey = new Map((data?.rows ?? []).map((r) => [r.key, r]))
  return (widget.rows ?? []).map((config) => {
    const d = byKey.get(config.key) ?? null
    const s = state(config, d)
    const value = s === 'OK' && typeof d?.value === 'number' && Number.isFinite(d.value) ? d.value : null
    const text = s === 'OK' && d?.text != null ? String(d.text) : null
    return { config, data: d, state: s, value, text, unit: (d?.unit ?? config.unit ?? '') || '' }
  })
}

/** The value of each widget (its first row) by uuid, for percent and limit references. */
export function widgetValues(widgets: Pick<WidgetConfig, 'uuid' | 'rows'>[], data: DashboardData | null): Map<string, number> {
  const values = new Map<string, number>()
  if (!data) return values
  for (const w of widgets) {
    const first = widgetRows(w, data.widgets?.[String(w.uuid)])[0]
    if (first?.value != null) values.set(String(w.uuid), first.value)
  }
  return values
}

/* ---------- percent ---------- */

export interface PercentConfig {
  source: string | number | null
  diff: boolean
  minFractionDigits: number
  maxFractionDigits: number
}

/** value / reference * 100, or with `diff` (1 - value / reference) * 100; null if not computable. */
export function percentOf(value: number | null, reference: number | null | undefined, diff: boolean): number | null {
  if (value == null || reference == null || !Number.isFinite(value) || !Number.isFinite(reference) || reference === 0) return null
  const result = diff ? (1 - value / reference) * 100 : (value / reference) * 100
  return Number.isFinite(result) ? result : null
}

/** "12,5%"; below 0.01 % (not for diff) "< 0,01 %" like the Control Center. */
export function percentText(result: number | null, config: Pick<PercentConfig, 'diff' | 'minFractionDigits' | 'maxFractionDigits'>, locale: string): string {
  if (result == null) return ''
  if (result < 0.01 && !config.diff) return `< ${formatNumber(0.01, locale, { min: 2, max: 2 })} %`
  const max = clamp(config.maxFractionDigits ?? 0)
  const min = Math.min(clamp(config.minFractionDigits ?? 0), max)
  return formatNumber(result, locale, { min, max }) + '%'
}

/* ---------- limits ---------- */

export interface LimitBound {
  enabled: boolean
  value: number | null
  color: string | null
  offset: number | null
}

export interface LimitConfig {
  /** uuid of the widget whose value is the limit (dynamic), null = fixed values */
  source: string | number | null
  upper: LimitBound | null
  lower: LimitBound | null
}

function exceeds(bound: LimitBound | null, value: number, dynamic: boolean, reference: number | null | undefined, below: boolean): boolean {
  if (!bound?.enabled) return false
  const base = dynamic ? reference : bound.value
  if (base == null || !Number.isFinite(base)) return false
  const limit = base * (typeof bound.offset === 'number' && Number.isFinite(bound.offset) ? bound.offset : 1)
  return below ? value <= limit : value >= limit
}

/**
 * Color of the value text when a limit is exceeded (`Limit.getExceedsLimitColor`: lower limit first), else null.
 * `reference`: the value of the source widget for dynamic limits.
 */
export function limitColor(limit: LimitConfig | null | undefined, value: number | null, reference?: number | null): string | null {
  if (!limit || value == null || !Number.isFinite(value)) return null
  const dynamic = limit.source != null && limit.source !== '' && limit.source !== -1
  if (exceeds(limit.lower, value, dynamic, reference, true)) return limit.lower?.color ?? null
  if (exceeds(limit.upper, value, dynamic, reference, false)) return limit.upper?.color ?? null
  return null
}

/* ---------- Value widget ---------- */

export interface ValueWidgetConfig {
  limit: LimitConfig | null
  percent: PercentConfig | null
  dimIncompletePeriod: boolean
}

export function valueConfig(config: Record<string, unknown> | null | undefined): ValueWidgetConfig {
  const c = (config ?? {}) as Partial<ValueWidgetConfig>
  const percent = c.percent && c.percent.source != null && c.percent.source !== '' ? c.percent : null
  return { limit: c.limit ?? null, percent, dimIncompletePeriod: !!c.dimIncompletePeriod }
}

export interface ValueDisplay {
  text: string
  /** limit color or null (the font color of the widget) */
  color: string | null
  /** the period is not over yet and the widget dims it */
  dimmed: boolean
}

/**
 * Text of a Value widget like the Control Center: "value unit (percent)", only the percent without showValue,
 * "- %" without both.
 */
export function valueDisplay(
  widget: Pick<WidgetConfig, 'decimals' | 'decimalsAuto' | 'showValue' | 'config'>,
  row: WidgetRow | undefined,
  values: Map<string, number>,
  locale: string,
  periodEnd: number | null,
  now: number = Date.now(),
): ValueDisplay {
  const cfg = valueConfig(widget.config)
  const value = row?.value ?? null
  const valueText = value != null ? `${formatNumber(value, locale, widgetDecimals(widget, row?.config))}${row?.unit ? ' ' + row.unit : ''}` : ''
  let percent: number | null = null
  let pText = ''
  if (cfg.percent) {
    percent = percentOf(value, values.get(String(cfg.percent.source)), cfg.percent.diff)
    pText = percentText(percent, cfg.percent, locale)
  }
  const showValue = widget.showValue !== false
  let text: string
  if (showValue) text = pText && valueText ? `${valueText} (${pText})` : valueText
  else text = pText || '- %'
  const compared = cfg.percent?.diff ? percent : value
  const reference = cfg.limit?.source != null ? values.get(String(cfg.limit.source)) : null
  return {
    text,
    color: limitColor(cfg.limit, compared, reference),
    dimmed: cfg.dimIncompletePeriod && periodEnd != null && periodEnd > now,
  }
}

/** Fraction of a value between minimum and maximum (0..1, clamped). */
export function fraction(value: number | null, minimum: number, maximum: number): number {
  if (value == null || !Number.isFinite(value) || !(maximum > minimum)) return 0
  return Math.min(1, Math.max(0, (value - minimum) / (maximum - minimum)))
}

/** `Helper.convertToPercent`: position of the value between minimum and maximum in percent. */
export function toPercent(value: number | null, minimum: number, maximum: number): number | null {
  if (value == null || !Number.isFinite(value) || maximum === minimum) return null
  return ((value - minimum) / (maximum - minimum)) * 100
}

export const num = (v: unknown, fallback: number): number => (typeof v === 'number' && Number.isFinite(v) ? v : fallback)

export function parseTime(value: string | null | undefined): number | null {
  if (!value) return null
  const ms = Date.parse(value)
  return Number.isNaN(ms) ? null : ms
}
