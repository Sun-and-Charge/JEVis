/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/*
 * The dashboard "source" as the Control Center stores it (docs/WEB-API.md, "Editing, stage E4"). The editor changes
 * these objects in place and sends them back; keys the web does not know stay untouched. Shared by the editor core
 * (canvas, panels, saving) and the per-type settings editors (src/dashboard/edit/types).
 */

/** A widget of the source. Known keys are typed; every other key is kept as it is. */
export interface RawWidget {
  uuid: number
  WidgetType: string
  title?: string
  tooltip?: string
  'Background Color'?: string
  'Font Color'?: string
  fontSize?: number
  fontWeight?: string
  fontPosture?: string
  fontUnderlined?: boolean
  'Title Position'?: string
  borderSize?: number
  borderColor?: string
  cornerRadius?: number
  shadow?: boolean
  layer?: number
  xPos: number
  yPos: number
  width: number
  height: number
  decimals?: number
  decimalsAuto?: boolean
  showValue?: boolean
  fixedTimeframe?: unknown
  forcedTimeFrame?: unknown
  /** analysis JSON with row aggregation (AnalysisHandler.toJsonNode(model, true)) */
  dataHandler?: RawDataHandler
  [key: string]: unknown
}

export interface RawDataHandler {
  type?: string
  forcedInterval?: string
  chartModels?: RawChartModel[]
  [key: string]: unknown
}

export interface RawChartModel {
  chartId?: number
  chartName?: string
  chartType?: string
  chartData?: RawRow[]
  [key: string]: unknown
}

export interface RawRow {
  id: number
  attributeString: string
  name?: string
  color?: string
  unitPrefix?: string
  unitFormula?: string
  unitLabel?: string
  axis?: number
  chartType?: string
  aggregationPeriod?: string
  manipulationMode?: string
  decimalDigits?: number
  calculation?: boolean
  calculationId?: number
  [key: string]: unknown
}

/** The whole source (top-level keys of ConfigManager.toJson). */
export interface RawDashboard {
  version?: string
  'Dash Board Color'?: string
  backgroundMode?: string
  'Snap to Grid'?: boolean
  'Show Grid'?: boolean
  'X Axis Grid Interval'?: number
  'Y Axis Grid Interval'?: number
  'Update Rate'?: number
  'Zoom Factor'?: number
  width?: number
  height?: number
  'Data Period'?: string
  Widget: RawWidget[]
  [key: string]: unknown
}

/** Zoom codes of the Control Center. */
export const ZOOM_FIT_HEIGHT = 97
export const ZOOM_FIT_WIDTH = 98
export const ZOOM_FIT_SCREEN = 99

/**
 * JavaFX colour (`0xRRGGBBAA`) → CSS (`#rrggbb` or `rgba(r,g,b,a)`); CSS input is returned unchanged; invalid → null.
 */
export function fxToCss(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const v = value.trim()
  if (/^#[0-9a-f]{6}([0-9a-f]{2})?$/i.test(v) || v.startsWith('rgb')) return v
  const m = /^0x([0-9a-f]{6})([0-9a-f]{2})$/i.exec(v)
  if (!m) return null
  const alpha = parseInt(m[2], 16)
  if (alpha === 255) return '#' + m[1].toLowerCase()
  const r = parseInt(m[1].slice(0, 2), 16)
  const g = parseInt(m[1].slice(2, 4), 16)
  const b = parseInt(m[1].slice(4, 6), 16)
  return `rgba(${r},${g},${b},${Math.round((alpha / 255) * 1000) / 1000})`
}

/** `#rrggbb` (opaque) or `#rrggbbaa` → JavaFX `0xrrggbbaa`; invalid → null. */
export function cssToFx(value: string, alpha = 1): string | null {
  const m = /^#([0-9a-f]{6})([0-9a-f]{2})?$/i.exec(value.trim())
  if (!m) return null
  const a = m[2] ?? Math.round(Math.min(1, Math.max(0, alpha)) * 255).toString(16).padStart(2, '0')
  return ('0x' + m[1] + a).toLowerCase()
}

/** `#rrggbb` of a JavaFX/CSS colour for `<input type="color">` (alpha dropped); fallback for invalid values. */
export function toHexInput(value: unknown, fallback = '#000000'): string {
  if (typeof value !== 'string') return fallback
  const fx = /^0x([0-9a-f]{6})[0-9a-f]{2}$/i.exec(value.trim())
  if (fx) return '#' + fx[1].toLowerCase()
  const css = /^#([0-9a-f]{6})/i.exec(value.trim())
  return css ? '#' + css[1].toLowerCase() : fallback
}

/** Alpha (0–1) of a JavaFX colour; 1 for anything else. */
export function fxAlpha(value: unknown): number {
  if (typeof value !== 'string') return 1
  const m = /^0x[0-9a-f]{6}([0-9a-f]{2})$/i.exec(value.trim())
  return m ? parseInt(m[1], 16) / 255 : 1
}

/** Next free uuid (max + 1, as the CC's DashboardControl). */
export function nextUuid(source: RawDashboard): number {
  return source.Widget.reduce((max, w) => Math.max(max, Number(w.uuid) || 0), 0) + 1
}

/** All rows of a widget (flattened over its chart models). */
export function widgetRows(widget: RawWidget): RawRow[] {
  return (widget.dataHandler?.chartModels ?? []).flatMap((c) => c.chartData ?? [])
}
