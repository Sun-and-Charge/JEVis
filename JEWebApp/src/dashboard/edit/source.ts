/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { RowTreeNode, UnitOption } from '../../api/analysis'
import type { DashboardConfig, DashboardTimeFrameOption, WidgetConfig, WidgetStyle } from '../../api/dashboard'
import { FALLBACK_COLORS } from '../../analysis/model'
import { nameProblem } from '../../analysis/editor'
import { DASHBOARD_TIME_FRAMES } from '../timeframe'
import { cssToFx, fxAlpha, fxToCss, toHexInput, widgetRows, ZOOM_FIT_HEIGHT, ZOOM_FIT_SCREEN, ZOOM_FIT_WIDTH, type RawChartModel, type RawDashboard, type RawRow, type RawWidget } from './raw'

/*
 * The source of the dashboard editor (E4) and the side panel: what the canvas draws at once (positions, sizes and the
 * common style come from the source, the rest from the last preview), the fields of one or several widgets, data
 * rows, the client-side checks (the server checks again) and the compare keys.
 */

export const FONT_WEIGHTS = ['THIN', 'EXTRA_LIGHT', 'LIGHT', 'NORMAL', 'MEDIUM', 'SEMI_BOLD', 'BOLD', 'EXTRA_BOLD', 'BLACK'] as const
export const FONT_POSTURES = ['REGULAR', 'ITALIC'] as const
export const TITLE_POSITIONS = ['TOP_LEFT', 'TOP_CENTER', 'TOP_RIGHT', 'CENTER_LEFT', 'CENTER', 'CENTER_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_CENTER', 'BOTTOM_RIGHT'] as const
export const BACKGROUND_MODES = ['default', 'repeat', 'stretch'] as const
export const AGGREGATION_PERIODS = ['NONE', 'MINUTELY', 'QUARTER_HOURLY', 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'THREEYEARS', 'FIVEYEARS', 'TENYEARS'] as const
export const MANIPULATION_MODES = ['NONE', 'AVERAGE', 'SUM', 'MIN', 'MAX', 'MEDIAN', 'RUNNING_MEAN', 'CENTRIC_RUNNING_MEAN', 'SORTED_MIN', 'SORTED_MAX', 'CUMULATE', 'GEOMETRIC_MEAN'] as const
/** Zoom of the dashboard: the fit modes (stored 97 / 98 / 99) or a factor. */
export const ZOOM_MODES = [
  { value: ZOOM_FIT_WIDTH, key: 'FIT_WIDTH' },
  { value: ZOOM_FIT_HEIGHT, key: 'FIT_HEIGHT' },
  { value: ZOOM_FIT_SCREEN, key: 'FIT_SCREEN' },
] as const

/** Values the Control Center's ConfigManager.loadDashboard falls back to. */
export const SOURCE_DEFAULTS = {
  color: '0xf4f4f4ff',
  grid: 25,
  updateRate: 900,
  zoom: 1,
  width: 1863,
  height: 886,
  dataPeriod: 'P1D',
} as const

export interface Problem {
  /** "name", "dashboard.width", "widget.<uuid>.fontSize" */
  path: string
  key: string
  params?: Record<string, unknown>
}

const num = (v: unknown, fallback: number) => (typeof v === 'number' && Number.isFinite(v) ? v : typeof v === 'string' && v.trim() !== '' && Number.isFinite(Number(v)) ? Number(v) : fallback)
const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T

/* ---------------------------------------------------------------- keys */

/** Compare key of the whole source (unsaved changes, undo). */
export const sourceKey = (source: RawDashboard) => JSON.stringify(source)

const LAYOUT_KEYS = ['xPos', 'yPos', 'width', 'height', 'layer'] as const

/** Compare key for the preview: moving, resizing and layers are drawn from the source and need no request. */
export function previewKey(source: RawDashboard): string {
  return JSON.stringify({
    ...source,
    Widget: source.Widget.map((w) => {
      const copy: Record<string, unknown> = { ...w }
      for (const k of LAYOUT_KEYS) delete copy[k]
      return copy
    }),
  })
}

/* ---------------------------------------------------------------- source → what the canvas draws */

/** Title position of the source as a JavaFX `Pos` name (legacy left / right / center as the CC's WidgetPojo). */
export function titlePosition(value: unknown): string {
  const v = typeof value === 'string' ? value.trim() : ''
  if (v === 'left') return 'CENTER_LEFT'
  if (v === 'right') return 'CENTER_RIGHT'
  if (v === 'center' || !v) return 'CENTER'
  return v.toUpperCase()
}

/** The common style of a widget of the source in the view format of W3. */
export function viewStyle(w: RawWidget): WidgetStyle {
  return {
    background: fxToCss(w['Background Color']),
    fontColor: fxToCss(w['Font Color']),
    fontSize: typeof w.fontSize === 'number' ? w.fontSize : null,
    fontWeight: typeof w.fontWeight === 'string' ? w.fontWeight : null,
    italic: w.fontPosture === 'ITALIC',
    underline: w.fontUnderlined === true,
    titlePosition: titlePosition(w['Title Position']),
    borderSize: typeof w.borderSize === 'number' ? w.borderSize : null,
    borderColor: fxToCss(w.borderColor),
    cornerRadius: typeof w.cornerRadius === 'number' ? w.cornerRadius : null,
    shadow: w.shadow === true,
  }
}

/** A widget not in the last preview yet (just added): an empty frame of its type with the common settings. */
export function rawToViewWidget(w: RawWidget): WidgetConfig {
  return {
    uuid: String(w.uuid),
    type: String(w.WidgetType),
    x: num(w.xPos, 0),
    y: num(w.yPos, 0),
    width: num(w.width, 10),
    height: num(w.height, 10),
    layer: num(w.layer, 0),
    title: typeof w.title === 'string' ? w.title : null,
    tooltip: typeof w.tooltip === 'string' ? w.tooltip : null,
    style: viewStyle(w),
    decimals: typeof w.decimals === 'number' ? w.decimals : null,
    decimalsAuto: w.decimalsAuto === true,
    showValue: w.showValue !== false,
    timeFrame: null,
    rows: [],
    chart: null,
    config: {},
  }
}

/** Zoom of the source in the W3 format. */
export function viewZoom(value: unknown): DashboardConfig['zoom'] {
  const z = num(value, 1)
  if (z === ZOOM_FIT_WIDTH) return 'FIT_WIDTH'
  if (z === ZOOM_FIT_HEIGHT) return 'FIT_HEIGHT'
  if (z === ZOOM_FIT_SCREEN) return 'FIT_SCREEN'
  return z > 0 ? z : 1
}

/**
 * What the canvas draws while editing: the last preview (or the saved dashboard) with the positions, sizes, layers,
 * titles and common style of the source; widgets removed from the source are gone, new ones are empty frames until
 * the preview has them.
 */
export function overlaySource(base: DashboardConfig, source: RawDashboard): DashboardConfig {
  const byUuid = new Map(base.widgets.map((w) => [String(w.uuid), w]))
  const widgets = source.Widget.map((w) => {
    const known = byUuid.get(String(w.uuid))
    const fresh = rawToViewWidget(w)
    if (!known || known.type !== fresh.type) return fresh
    return {
      ...known,
      x: fresh.x,
      y: fresh.y,
      width: fresh.width,
      height: fresh.height,
      layer: fresh.layer,
      title: fresh.title,
      tooltip: fresh.tooltip,
      style: fresh.style,
      decimalsAuto: fresh.decimalsAuto,
      showValue: fresh.showValue,
    }
  })
  const mode = typeof source.backgroundMode === 'string' ? source.backgroundMode.toUpperCase() : 'DEFAULT'
  return {
    ...base,
    width: num(source.width, SOURCE_DEFAULTS.width),
    height: num(source.height, SOURCE_DEFAULTS.height),
    zoom: viewZoom(source['Zoom Factor']),
    background: { color: fxToCss(source['Dash Board Color']) ?? '#f4f4f4', image: !!base.background?.image, mode },
    updateRate: num(source['Update Rate'], SOURCE_DEFAULTS.updateRate),
    widgets,
  }
}

/* ---------------------------------------------------------------- several widgets at once */

export interface Common<T> {
  value: T | undefined
  /** the selected widgets differ */
  mixed: boolean
}

/** The value of a field of all selected widgets (mixed if they differ). */
export function commonValue<T>(widgets: readonly RawWidget[], get: (w: RawWidget) => T): Common<T> {
  if (!widgets.length) return { value: undefined, mixed: false }
  const first = get(widgets[0])
  const mixed = widgets.some((w) => JSON.stringify(get(w)) !== JSON.stringify(first))
  return { value: mixed ? undefined : first, mixed }
}

/** Sets a key of all selected widgets. */
export function setAll(widgets: readonly RawWidget[], key: string, value: unknown): void {
  for (const w of widgets) w[key] = value
}

/** Colour fields: `#rrggbb` of the colour picker → `0xRRGGBBAA`, the alpha of each widget is kept. */
export function setColorAll(widgets: readonly RawWidget[], key: string, hex: string): void {
  for (const w of widgets) {
    const fx = cssToFx(hex, fxAlpha(w[key]))
    if (fx) w[key] = fx
  }
}

/** Opacity (0–100 %) of a colour field of all selected widgets. */
export function setAlphaAll(widgets: readonly RawWidget[], key: string, percent: number): void {
  const alpha = Math.min(1, Math.max(0, percent / 100))
  for (const w of widgets) {
    const fx = cssToFx(toHexInput(w[key], '#ffffff'), alpha)
    if (fx) w[key] = fx
  }
}

export const alphaPercent = (value: unknown) => Math.round(fxAlpha(value) * 100)

/* ---------------------------------------------------------------- own time frame, data rows, chart */

/** Own time frame of a widget: `dataHandler.forcedInterval` ('' = the dashboard's). */
export const ownTimeFrame = (w: RawWidget): string => (typeof w.dataHandler?.forcedInterval === 'string' ? w.dataHandler.forcedInterval : '')

/** The time frames of the dashboard ("Data Period") and of widgets: standard ids (no CUSTOM), LASTVALUE, custom periods. */
export function timeFrameChoices(options: readonly DashboardTimeFrameOption[] | undefined): { id: string; name: string | null }[] {
  const list: { id: string; name: string | null }[] = DASHBOARD_TIME_FRAMES.filter((id) => id !== 'CUSTOM').map((id) => ({ id, name: null }))
  list.push({ id: 'LASTVALUE', name: null })
  for (const o of options ?? []) if (o.custom && !list.some((x) => x.id === String(o.id))) list.push({ id: String(o.id), name: o.name ?? String(o.id) })
  return list
}

/** The first chart model of a widget (created if missing: the data handler of the Control Center). */
export function ensureChartModel(w: RawWidget): RawChartModel {
  if (!w.dataHandler || typeof w.dataHandler !== 'object') w.dataHandler = { type: 'AnalysisHandler', forcedInterval: '', chartModels: [] }
  if (!w.dataHandler.type) w.dataHandler.type = 'AnalysisHandler'
  if (!Array.isArray(w.dataHandler.chartModels)) w.dataHandler.chartModels = []
  if (!w.dataHandler.chartModels.length) w.dataHandler.chartModels.push({ chartId: 0, chartName: '', chartType: 'LINE', chartData: [] })
  const model = w.dataHandler.chartModels[0]
  if (!Array.isArray(model.chartData)) model.chartData = []
  return model
}

/** Sets the own time frame ('' = none). */
export function setOwnTimeFrame(w: RawWidget, id: string): void {
  if (!w.dataHandler && !id) return
  ensureChartModel(w)
  w.dataHandler!.forcedInterval = id
}

export const rowId = (r: Pick<RawRow, 'id' | 'attributeString'>) => `${r.id}|${r.attributeString}`

/** A new row from the picker (CC defaults of ChartData, aggregation / manipulation none, colour by position). */
export function newRawRow(node: Pick<RowTreeNode, 'id' | 'name'>, attribute: string, index: number): RawRow {
  return {
    id: node.id,
    attributeString: attribute,
    name: node.name,
    color: cssToFx(FALLBACK_COLORS[index % FALLBACK_COLORS.length]) ?? '0xadd8e6ff',
    axis: 0,
    calculation: false,
    bubbleType: 'NONE',
    chartType: 'DEFAULT',
    aggregationPeriod: 'NONE',
    manipulationMode: 'NONE',
    decimalDigits: -1,
    css: '',
    intervalEnabled: false,
  }
}

/** Adds rows to the first chart model; rows already in the widget are skipped. Returns the added rows. */
export function addRows(w: RawWidget, picked: readonly { node: Pick<RowTreeNode, 'id' | 'name'>; attribute: string }[]): RawRow[] {
  const model = ensureChartModel(w)
  const present = new Set(widgetRows(w).map(rowId))
  const added: RawRow[] = []
  for (const { node, attribute } of picked) {
    if (present.has(`${node.id}|${attribute}`)) continue
    const row = newRawRow(node, attribute, widgetRows(w).length)
    model.chartData!.push(row)
    present.add(rowId(row))
    added.push(row)
  }
  return added
}

/** Removes a row from whichever chart model holds it. */
export function removeRow(w: RawWidget, row: RawRow): void {
  for (const m of w.dataHandler?.chartModels ?? []) {
    const i = (m.chartData ?? []).indexOf(row)
    if (i >= 0) m.chartData!.splice(i, 1)
  }
}

/** Moves a row within its chart model. */
export function moveRow(w: RawWidget, row: RawRow, delta: -1 | 1): void {
  for (const m of w.dataHandler?.chartModels ?? []) {
    const list = m.chartData ?? []
    const i = list.indexOf(row)
    const j = i + delta
    if (i >= 0 && j >= 0 && j < list.length) {
      list.splice(i, 1)
      list.splice(j, 0, row)
    }
  }
}

export const sameRowUnit = (row: RawRow, u: UnitOption) =>
  (row.unitPrefix ?? null) === (u.prefix ?? null) && (row.unitFormula ?? null) === (u.formula ?? null) && (row.unitLabel ?? null) === (u.label ?? null)

export function setRowUnit(row: RawRow, u: UnitOption): void {
  row.unitPrefix = u.prefix ?? undefined
  row.unitFormula = u.formula ?? undefined
  row.unitLabel = u.label ?? undefined
}

/* ---------------------------------------------------------------- checks */

type Check = { min: number; max: number; integer?: boolean }

function check(problems: Problem[], path: string, value: unknown, c: Check, required = true, original?: unknown): void {
  // a value as loaded is accepted (the Control Center may store e.g. widgets narrower than 10 px); the server does the same
  if (original !== undefined && value === original) return
  if (value === undefined || value === null || value === '') {
    if (required) problems.push({ path, key: 'dashboardEdit.problem.required' })
    return
  }
  const n = typeof value === 'number' ? value : Number.NaN
  if (!Number.isFinite(n) || n < c.min || n > c.max || (c.integer && !Number.isInteger(n))) {
    problems.push({ path, key: c.integer ? 'dashboardEdit.problem.integerRange' : 'dashboardEdit.problem.range', params: { min: c.min, max: c.max } })
  }
}

const COLOR = /^(0x[0-9a-f]{6}([0-9a-f]{2})?|#[0-9a-f]{6}([0-9a-f]{2})?)$/i

/** Client-side checks of the source (as far as the panel edits it); the server checks everything again. */
export function validateSource(source: RawDashboard, name: string | null, original?: RawDashboard | null): Problem[] {
  const problems: Problem[] = []
  const loaded = new Map<number, RawWidget>((original?.Widget ?? []).map((w) => [Number(w.uuid), w]))
  if (name !== null) {
    const p = nameProblem(name)
    if (p) problems.push({ path: 'name', key: p })
  }
  check(problems, 'dashboard.width', source.width, { min: 100, max: 20000 }, false)
  check(problems, 'dashboard.height', source.height, { min: 100, max: 20000 }, false)
  check(problems, 'dashboard.gridX', source['X Axis Grid Interval'], { min: 1, max: 1000 }, false)
  check(problems, 'dashboard.gridY', source['Y Axis Grid Interval'], { min: 1, max: 1000 }, false)
  check(problems, 'dashboard.updateRate', source['Update Rate'], { min: 1, max: 86400, integer: true }, false)
  const zoom = source['Zoom Factor']
  if (zoom !== undefined && ![ZOOM_FIT_HEIGHT, ZOOM_FIT_WIDTH, ZOOM_FIT_SCREEN].includes(zoom as number)) check(problems, 'dashboard.zoom', zoom, { min: 0.01, max: 20 })
  if (source['Dash Board Color'] !== undefined && !COLOR.test(String(source['Dash Board Color']))) problems.push({ path: 'dashboard.color', key: 'dashboardEdit.problem.color' })
  if (source.Widget.length > 1000) problems.push({ path: 'dashboard.widgets', key: 'dashboardEdit.problem.tooMany' })
  for (const w of source.Widget) {
    const p = (k: string) => `widget.${w.uuid}.${k}`
    const o = loaded.get(Number(w.uuid))
    check(problems, p('xPos'), w.xPos, { min: -1e6, max: 1e6 }, true, o?.xPos)
    check(problems, p('yPos'), w.yPos, { min: -1e6, max: 1e6 }, true, o?.yPos)
    check(problems, p('width'), w.width, { min: 10, max: 20000 }, true, o?.width)
    check(problems, p('height'), w.height, { min: 10, max: 20000 }, true, o?.height)
    check(problems, p('fontSize'), w.fontSize, { min: 1, max: 400 }, false, o?.fontSize)
    check(problems, p('decimals'), w.decimals, { min: 0, max: 20, integer: true }, false, o?.decimals)
    check(problems, p('borderSize'), w.borderSize, { min: 0, max: 100 }, false, o?.borderSize)
    check(problems, p('cornerRadius'), w.cornerRadius, { min: 0, max: 10000 }, false, o?.cornerRadius)
    check(problems, p('layer'), w.layer, { min: -1e6, max: 1e6, integer: true }, false, o?.layer)
    for (const key of ['Background Color', 'Font Color', 'borderColor']) {
      if (w[key] !== undefined && w[key] !== null && !COLOR.test(String(w[key]))) problems.push({ path: p(key), key: 'dashboardEdit.problem.color' })
    }
    for (const row of widgetRows(w)) {
      if (row.decimalDigits !== undefined) check(problems, p(`row.${rowId(row)}.decimalDigits`), row.decimalDigits, { min: -1, max: 20, integer: true })
      if (row.color !== undefined && !COLOR.test(String(row.color))) problems.push({ path: p(`row.${rowId(row)}.color`), key: 'dashboardEdit.problem.color' })
    }
  }
  return problems
}

/** A copy of the source for the editor (the loaded object stays untouched). */
export function editableSource(source: RawDashboard | null | undefined): RawDashboard {
  const s = source && typeof source === 'object' ? clone(source) : ({ Widget: [] } as RawDashboard)
  if (!Array.isArray(s.Widget)) s.Widget = []
  // uuids as numbers (the server sends ints; older files may have strings)
  for (const w of s.Widget) w.uuid = Number(w.uuid)
  return s
}
