/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type {
  AnalysisChartConfig,
  AnalysisConfig,
  AnalysisDefinition,
  AnalysisRowConfig,
  ChartDefinition,
  LimitLine,
  NameTaken,
  RowDefinition,
  RowTreeNode,
  UnitOption,
} from '../api/analysis'
import { HttpError } from '../api/http'
import { FALLBACK_COLORS } from './model'

/*
 * Editing of analyses (E3): the form state ("draft") of the side panel, its mapping from the GET answer and to the
 * definition sent to the server, the client-side validation (the server checks again) and small helpers.
 */

/** Chart types offered for a chart (as `ChartType` of the Control Center, without DEFAULT). */
export const CHART_TYPES = ['LINE', 'AREA', 'STACKED_AREA', 'COLUMN', 'STACKED_COLUMN', 'BAR', 'SCATTER', 'BUBBLE', 'LOGICAL', 'PIE', 'HEAT_MAP', 'TABLE', 'TABLE_V', 'TABLE_H'] as const
/** Types of a row: DEFAULT = the type of its chart. */
export const ROW_TYPES = ['DEFAULT', ...CHART_TYPES] as const
export const ORIENTATIONS = ['HORIZONTAL', 'VERTICAL'] as const
export const BUBBLE_TYPES = ['NONE', 'X', 'Y', 'SIZE'] as const
export const LIMIT_FILLS = ['NONE', 'ABOVE', 'BELOW'] as const
/** `ColorMapping` of the Control Center (order of its box). */
export const COLOR_MAPPINGS = [
  'LIME_YELLOW_RED',
  'BLUE_CYAN_GREEN_YELLOW_RED',
  'INFRARED_1',
  'INFRARED_2',
  'INFRARED_3',
  'INFRARED_4',
  'BLUE_GREEN_RED',
  'BLUE_BLACK_RED',
  'BLUE_YELLOW_RED',
  'BLUE_TRANSPARENT_RED',
  'GREEN_BLACK_RED',
  'GREEN_YELLOW_RED',
  'RAINBOW',
  'BLACK_WHITE',
  'WHITE_BLACK',
] as const
/** Forced intervals offered (ISO periods; '' = none). Other stored values stay selectable. */
export const FORCED_INTERVALS = ['', 'PT15M', 'PT1H', 'P1D', 'P1W', 'P1M', 'P3M', 'P1Y'] as const

const TABLE_TYPES = ['TABLE', 'TABLE_V', 'TABLE_H']
const XY_CHART_TYPES = ['LINE', 'AREA', 'STACKED_AREA', 'COLUMN', 'STACKED_COLUMN', 'SCATTER', 'LOGICAL']

/** The chart shows table options (sums, colouring, filter, orientation). */
export const isTableType = (type: string) => TABLE_TYPES.includes(type)
/** The chart has a time axis with y axes (axis titles, fix to zero, limit lines, row types). */
export const isXyType = (type: string) => XY_CHART_TYPES.includes(type)

/** Definition keys of a row; rows the user may not read are sent back with exactly the keys they came with. */
const ROW_KEYS = ['objectId', 'attribute', 'name', 'color', 'axis', 'chartType', 'unitPrefix', 'unitFormula', 'unitLabel', 'decimalDigits', 'calculation', 'bubbleType', 'interval', 'css'] as const

export interface DraftInterval {
  enabled: boolean
  start: string | null
  end: string | null
}

export interface DraftRow {
  /** local id (list keys, field ids) */
  uid: string
  objectId: number
  attribute: string
  name: string
  color: string
  axis: 0 | 1
  chartType: string
  unitPrefix: string | null
  unitFormula: string | null
  unitLabel: string | null
  /** -1: the decimals of the chart */
  decimalDigits: number | ''
  calculation: boolean
  bubbleType: string
  interval: DraftInterval
  css: string
  readable: boolean
  /** display unit (GET / picker); the unit fields win when set */
  unit: string | null
  /** name of the object (fallback when the legend name is empty) */
  objectName: string
  rowClass: string | null
  /** key of the row in the saved analysis (data answers of the saved analysis, before the first preview) */
  savedKey?: string
  /** rows the user may not read: the keys as received, sent back unchanged */
  locked?: RowDefinition
}

export interface DraftChart {
  uid: string
  id: number
  name: string
  type: string
  minFractionDigits: number | ''
  maxFractionDigits: number | ''
  fractionDigitsAuto: boolean | null
  /** -1: automatic */
  height: number | ''
  orientation: string
  fixYAxisToZero: boolean
  xAxisTitle: string
  yAxisTitle: string
  groupingInterval: number | ''
  colorMapping: string
  filterEnabled: boolean
  coloringEnabled: boolean
  showColumnSums: boolean
  showRowSums: boolean
  /** HH:mm:ss or '' */
  dayStart: string
  dayEnd: string
  filterTexts: Record<string, string>
  rows: DraftRow[]
}

export interface DraftLimitLine {
  uid: string
  value: number | ''
  label: string
  color: string
  axis: 0 | 1
  fill: string
}

export interface Draft {
  /** local name of the analysis (rename when changed) */
  name: string
  chartsPerScreen: number | ''
  horizontalPies: number | ''
  horizontalTables: number | ''
  autoSize: boolean
  forcedInterval: string
  /** stored strings (kept unchanged unless edited) */
  dayFilter: { weekdays: string; holidays: boolean; dates: string }
  limitLines: DraftLimitLine[]
  charts: DraftChart[]
}

let uidSeq = 0
export const newUid = (prefix = 'u') => `${prefix}${++uidSeq}`

const num = (v: unknown, fallback: number): number => (typeof v === 'number' && Number.isFinite(v) ? v : fallback)

/* ------------------------------------------------------------------ GET answer -> draft */

export function rowToDraft(row: AnalysisRowConfig): DraftRow {
  const readable = row.readable !== false
  const draft: DraftRow = {
    uid: newUid('r'),
    objectId: row.objectId,
    attribute: row.attribute || 'Value',
    name: row.name ?? '',
    color: row.color ?? '',
    axis: row.axis === 1 ? 1 : 0,
    chartType: row.chartType || 'DEFAULT',
    unitPrefix: row.unitPrefix ?? null,
    unitFormula: row.unitFormula ?? null,
    unitLabel: row.unitLabel ?? null,
    decimalDigits: num(row.decimalDigits, -1),
    calculation: !!row.calculation,
    bubbleType: row.bubbleType || 'NONE',
    interval: { enabled: !!row.interval?.enabled, start: row.interval?.start ?? null, end: row.interval?.end ?? null },
    css: row.css ?? '',
    readable,
    unit: row.unit ?? null,
    objectName: row.name ?? '',
    rowClass: row.rowClass ?? null,
    savedKey: row.key,
  }
  if (!readable) {
    const locked: Record<string, unknown> = {}
    for (const key of ROW_KEYS) if (key in row) locked[key] = (row as unknown as Record<string, unknown>)[key]
    locked.objectId = row.objectId
    locked.attribute = row.attribute
    draft.locked = locked as unknown as RowDefinition
  }
  return draft
}

export function chartToDraft(chart: AnalysisChartConfig): DraftChart {
  return {
    uid: newUid('c'),
    id: chart.id,
    name: chart.name ?? '',
    type: chart.type && chart.type !== 'DEFAULT' ? chart.type : 'LINE',
    minFractionDigits: num(chart.minFractionDigits, 2),
    maxFractionDigits: num(chart.maxFractionDigits, 2),
    fractionDigitsAuto: chart.fractionDigitsAuto ?? null,
    height: num(chart.height, -1),
    orientation: chart.orientation || 'HORIZONTAL',
    fixYAxisToZero: !!chart.fixYAxisToZero,
    xAxisTitle: chart.xAxisTitle ?? '',
    yAxisTitle: chart.yAxisTitle ?? '',
    groupingInterval: num(chart.groupingInterval, 30),
    colorMapping: chart.colorMapping || 'GREEN_YELLOW_RED',
    filterEnabled: !!chart.filterEnabled,
    coloringEnabled: !!chart.coloringEnabled,
    showColumnSums: !!chart.showColumnSums,
    showRowSums: !!chart.showRowSums,
    dayStart: chart.dayStart ?? '',
    dayEnd: chart.dayEnd ?? '',
    filterTexts: { ...(chart.filterTexts ?? {}) },
    rows: (chart.rows ?? []).map(rowToDraft),
  }
}

export function limitToDraft(line: LimitLine): DraftLimitLine {
  return {
    uid: newUid('l'),
    value: num(line.value, 0),
    label: line.label ?? '',
    color: line.color || '#FF0000',
    axis: line.axis === 1 ? 1 : 0,
    fill: line.fill || 'NONE',
  }
}

/** The form state of an analysis (GET answer); `name` is the local name shown to the user. */
export function toDraft(config: AnalysisConfig, name: string): Draft {
  return {
    name,
    chartsPerScreen: num(config.chartsPerScreen, 2),
    horizontalPies: num(config.horizontalPies, 3),
    horizontalTables: num(config.horizontalTables, 3),
    autoSize: config.autoSize !== false,
    forcedInterval: config.forcedInterval ?? '',
    dayFilter: {
      weekdays: config.dayFilter?.weekdays ?? '',
      holidays: !!config.dayFilter?.holidays,
      dates: config.dayFilter?.dates ?? '',
    },
    limitLines: (config.limitLines ?? []).map(limitToDraft),
    charts: (config.charts ?? []).map(chartToDraft),
  }
}

/** Deep copy of a draft (new object identities, same uids). */
export function cloneDraft(draft: Draft): Draft {
  return JSON.parse(JSON.stringify(draft)) as Draft
}

/* ------------------------------------------------------------------ draft -> definition */

/** "08:00" -> "08:00:00"; '' -> null; anything else as typed (validation reports it). */
export function normaliseTime(value: string): string | null {
  const v = (value ?? '').trim()
  if (!v) return null
  return /^\d{2}:\d{2}$/.test(v) ? v + ':00' : v
}

export function rowDefinition(row: DraftRow): RowDefinition {
  if (row.locked) return { ...row.locked }
  return {
    objectId: row.objectId,
    attribute: row.attribute,
    name: row.name,
    color: row.color.trim() || null,
    axis: row.axis,
    chartType: row.chartType,
    unitPrefix: row.unitPrefix,
    unitFormula: row.unitFormula,
    unitLabel: row.unitLabel,
    decimalDigits: row.decimalDigits === '' ? -1 : row.decimalDigits,
    calculation: row.calculation,
    bubbleType: row.bubbleType,
    interval: { enabled: row.interval.enabled, start: row.interval.start || null, end: row.interval.end || null },
    css: row.css,
  }
}

export function chartDefinition(chart: DraftChart): ChartDefinition {
  const filterTexts: Record<string, string> = {}
  for (const [k, v] of Object.entries(chart.filterTexts ?? {})) if (k && v) filterTexts[k] = v
  return {
    id: chart.id,
    name: chart.name,
    type: chart.type,
    minFractionDigits: num(chart.minFractionDigits, 2),
    maxFractionDigits: num(chart.maxFractionDigits, 2),
    fractionDigitsAuto: chart.fractionDigitsAuto,
    height: num(chart.height, -1),
    orientation: chart.orientation,
    fixYAxisToZero: chart.fixYAxisToZero,
    xAxisTitle: chart.xAxisTitle.trim() || null,
    yAxisTitle: chart.yAxisTitle.trim() || null,
    groupingInterval: num(chart.groupingInterval, 30),
    colorMapping: chart.colorMapping,
    filterEnabled: chart.filterEnabled,
    coloringEnabled: chart.coloringEnabled,
    showColumnSums: chart.showColumnSums,
    showRowSums: chart.showRowSums,
    dayStart: normaliseTime(chart.dayStart),
    dayEnd: normaliseTime(chart.dayEnd),
    filterTexts,
    rows: chart.rows.map(rowDefinition),
  }
}

/** The definition sent to the server (PUT / POST / copy / preview). Charts in the order of the draft. */
export function toDefinition(draft: Draft): AnalysisDefinition {
  return {
    chartsPerScreen: num(draft.chartsPerScreen, 2),
    horizontalPies: num(draft.horizontalPies, 3),
    horizontalTables: num(draft.horizontalTables, 3),
    autoSize: draft.autoSize,
    forcedInterval: draft.forcedInterval,
    dayFilter: { ...draft.dayFilter },
    limitLines: draft.limitLines.map((l) => ({ value: num(l.value, 0), label: l.label, color: l.color, axis: l.axis, fill: l.fill })),
    charts: draft.charts.map(chartDefinition),
  }
}

/** Compares two states (unsaved changes): definition plus name. */
export function draftKey(draft: Draft): string {
  return JSON.stringify([draft.name.trim(), toDefinition(draft)])
}

/**
 * The parts of a definition that change the data (rows, units, time settings); a change of only names, colours or
 * display options needs no new preview request.
 */
export function previewKey(definition: AnalysisDefinition): string {
  return JSON.stringify([
    definition.forcedInterval,
    definition.dayFilter,
    definition.charts.map((c) => [
      c.id,
      c.type,
      c.dayStart,
      c.dayEnd,
      c.rows.map((r) => [r.objectId, r.attribute, r.chartType, r.unitPrefix, r.unitFormula, r.unitLabel, r.calculation, r.interval]),
    ]),
  ])
}

/** An empty analysis (new analysis from the navigation): one line chart without rows, as the Control Center. */
export function emptyDefinition(chartName: string): AnalysisDefinition {
  const draft: Draft = {
    name: '',
    chartsPerScreen: 2,
    horizontalPies: 3,
    horizontalTables: 3,
    autoSize: true,
    forcedInterval: '',
    dayFilter: { weekdays: '', holidays: false, dates: '' },
    limitLines: [],
    charts: [newChart([], chartName)],
  }
  return toDefinition(draft)
}

/* ------------------------------------------------------------------ preview config */

export const rowKey = (chartId: number, row: Pick<DraftRow, 'objectId' | 'attribute'>) => `${chartId}-${row.objectId}-${row.attribute}`

const COLOR = /^#([0-9a-f]{6}|[0-9a-f]{8})$/i
export const validColor = (c: string | null | undefined): boolean => !!c && COLOR.test(c.trim())

/**
 * The configuration the view draws while editing: the draft in the shape of the GET answer, row keys as the preview
 * answer has them (`<chartId>-<objectId>-<attribute>`; with `savedKeys` the keys of the saved analysis, as long as
 * the data shown is still that of the saved analysis). Invalid numbers fall back to the saved values.
 */
export function draftConfig(draft: Draft, base: AnalysisConfig, savedKeys = false): AnalysisConfig {
  return {
    ...base,
    chartsPerScreen: num(draft.chartsPerScreen, base.chartsPerScreen),
    horizontalPies: num(draft.horizontalPies, base.horizontalPies),
    horizontalTables: num(draft.horizontalTables, base.horizontalTables),
    autoSize: draft.autoSize,
    limitLines: draft.limitLines
      .filter((l) => typeof l.value === 'number' && Number.isFinite(l.value))
      .map((l) => ({ value: l.value as number, label: l.label, color: validColor(l.color) ? l.color : '#FF0000', axis: l.axis, fill: l.fill })),
    charts: draft.charts.map((c) => ({
      id: c.id,
      name: c.name,
      type: c.type,
      minFractionDigits: num(c.minFractionDigits, 2),
      maxFractionDigits: num(c.maxFractionDigits, 2),
      fractionDigitsAuto: c.fractionDigitsAuto,
      height: num(c.height, -1),
      orientation: c.orientation,
      fixYAxisToZero: c.fixYAxisToZero,
      xAxisTitle: c.xAxisTitle.trim() || null,
      yAxisTitle: c.yAxisTitle.trim() || null,
      groupingInterval: num(c.groupingInterval, 30),
      colorMapping: c.colorMapping,
      filterEnabled: c.filterEnabled,
      coloringEnabled: c.coloringEnabled,
      showColumnSums: c.showColumnSums,
      showRowSums: c.showRowSums,
      filterTexts: c.filterTexts,
      rows: c.rows.map(
        (r): AnalysisRowConfig => ({
          key: savedKeys && r.savedKey ? r.savedKey : rowKey(c.id, r),
          objectId: r.objectId,
          attribute: r.attribute,
          name: r.readable ? r.name.trim() || r.objectName || String(r.objectId) : null,
          color: validColor(r.color) ? r.color : null,
          axis: r.axis,
          chartType: r.chartType,
          unit: r.readable ? (r.unitLabel ?? r.unit) : null,
          decimalDigits: r.decimalDigits === '' ? -1 : r.decimalDigits,
          calculation: r.calculation,
          bubbleType: r.bubbleType,
          readable: r.readable,
        }),
      ),
    })),
  }
}

/* ------------------------------------------------------------------ validation */

export interface Problem {
  /** field path, e.g. "charts.0.rows.1.color" (also the base of the field id) */
  path: string
  /** i18n key */
  key: string
  params?: Record<string, unknown>
}

const isInt = (v: unknown, min: number, max: number) => typeof v === 'number' && Number.isInteger(v) && v >= min && v <= max
const TIME = /^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/
// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u001f\u007f]/

/** Problem of an analysis name (create, rename, copy), or null. */
export function nameProblem(name: string): string | null {
  const v = (name ?? '').trim()
  if (!v) return 'analysisEdit.problem.required'
  if (v.length > 100) return 'analysisEdit.problem.nameLength'
  if (CONTROL.test(v)) return 'analysisEdit.problem.nameChars'
  return null
}

/** All problems of the draft (the server checks the same and more; see WEB-API.md E3). */
export function validateDraft(draft: Draft, options: { checkName?: boolean } = {}): Problem[] {
  const problems: Problem[] = []
  const add = (path: string, key: string, params?: Record<string, unknown>) => problems.push({ path, key, params })

  if (options.checkName) {
    const p = nameProblem(draft.name)
    if (p) add('name', p)
  }
  for (const key of ['chartsPerScreen', 'horizontalPies', 'horizontalTables'] as const) {
    if (!isInt(draft[key], 1, 20)) add(key, 'analysisEdit.problem.range', { min: 1, max: 20 })
  }
  draft.limitLines.forEach((l, i) => {
    if (typeof l.value !== 'number' || !Number.isFinite(l.value)) add(`limitLines.${i}.value`, 'analysisEdit.problem.number')
    if (!validColor(l.color)) add(`limitLines.${i}.color`, 'analysisEdit.problem.color')
  })
  for (const d of (draft.dayFilter.dates ?? '').split(/[,;\s]+/).filter(Boolean)) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(d)) {
      add('dayFilter.dates', 'analysisEdit.problem.date', { value: d })
      break
    }
  }

  const ids = new Set<number>()
  draft.charts.forEach((c, ci) => {
    const base = `charts.${ci}`
    if (!Number.isInteger(c.id) || c.id < 0 || ids.has(c.id)) add(`${base}.id`, 'analysisEdit.problem.chartId')
    ids.add(c.id)
    if (!isInt(c.minFractionDigits, 0, 20)) add(`${base}.minFractionDigits`, 'analysisEdit.problem.range', { min: 0, max: 20 })
    if (!isInt(c.maxFractionDigits, 0, 20)) add(`${base}.maxFractionDigits`, 'analysisEdit.problem.range', { min: 0, max: 20 })
    if (typeof c.height !== 'number' || !Number.isFinite(c.height) || (c.height !== -1 && c.height < 50))
      add(`${base}.height`, 'analysisEdit.problem.height')
    if (typeof c.groupingInterval !== 'number' || !Number.isFinite(c.groupingInterval) || c.groupingInterval <= 0)
      add(`${base}.groupingInterval`, 'analysisEdit.problem.positive')
    for (const key of ['dayStart', 'dayEnd'] as const) {
      if (c[key].trim() && !TIME.test(c[key].trim())) add(`${base}.${key}`, 'analysisEdit.problem.time')
    }
    c.rows.forEach((r, ri) => {
      if (!r.readable) return
      const rb = `${base}.rows.${ri}`
      if (r.color.trim() && !validColor(r.color)) add(`${rb}.color`, 'analysisEdit.problem.color')
      if (r.decimalDigits !== '' && r.decimalDigits !== -1 && !isInt(r.decimalDigits, 0, 20)) add(`${rb}.decimalDigits`, 'analysisEdit.problem.decimals')
      if (r.interval.enabled) {
        const start = r.interval.start ? Date.parse(r.interval.start) : NaN
        const end = r.interval.end ? Date.parse(r.interval.end) : NaN
        if (Number.isNaN(start) || Number.isNaN(end)) add(`${rb}.interval`, 'analysisEdit.problem.interval')
        else if (end <= start) add(`${rb}.interval`, 'analysisEdit.problem.intervalOrder')
      }
    })
  })
  return problems
}

/* ------------------------------------------------------------------ editing helpers */

/** A new chart with the next free id (as the Control Center: line chart, 2 decimals). */
export function newChart(charts: readonly Pick<DraftChart, 'id'>[], name: string): DraftChart {
  const id = charts.reduce((max, c) => Math.max(max, c.id), -1) + 1
  return {
    uid: newUid('c'),
    id,
    name,
    type: 'LINE',
    minFractionDigits: 2,
    maxFractionDigits: 2,
    fractionDigitsAuto: null,
    height: -1,
    orientation: 'HORIZONTAL',
    fixYAxisToZero: false,
    xAxisTitle: '',
    yAxisTitle: '',
    groupingInterval: 30,
    colorMapping: 'GREEN_YELLOW_RED',
    filterEnabled: false,
    coloringEnabled: false,
    showColumnSums: false,
    showRowSums: false,
    dayStart: '',
    dayEnd: '',
    filterTexts: {},
    rows: [],
  }
}

/** A row chosen in the row picker. */
export interface PickedRow {
  node: RowTreeNode
  attribute: string
}

/** A new row from the row picker; the unit is the row's own one (filled in when the units are loaded). */
export function newRow(node: Pick<RowTreeNode, 'id' | 'name' | 'unit' | 'class'>, attribute: string, index: number): DraftRow {
  return {
    uid: newUid('r'),
    objectId: node.id,
    attribute,
    name: node.name,
    color: FALLBACK_COLORS[index % FALLBACK_COLORS.length],
    axis: 0,
    chartType: 'DEFAULT',
    unitPrefix: null,
    unitFormula: null,
    unitLabel: null,
    decimalDigits: -1,
    calculation: false,
    bubbleType: 'NONE',
    interval: { enabled: false, start: null, end: null },
    css: '',
    readable: true,
    unit: node.unit ?? null,
    objectName: node.name,
    rowClass: node.class ?? null,
  }
}

export function newLimitLine(): DraftLimitLine {
  return { uid: newUid('l'), value: 0, label: '', color: '#FF0000', axis: 0, fill: 'NONE' }
}

/** Moves an item of a list by `delta` places (no-op at the ends). */
export function move<T>(list: T[], index: number, delta: number): void {
  const to = index + delta
  if (index < 0 || index >= list.length || to < 0 || to >= list.length) return
  const [item] = list.splice(index, 1)
  list.splice(to, 0, item)
}

/** The row is already in the chart (same object and attribute: the key would not be unique). */
export const hasRow = (chart: Pick<DraftChart, 'rows'>, objectId: number, attribute: string) =>
  chart.rows.some((r) => r.objectId === objectId && r.attribute === attribute)

export const sameUnit = (row: Pick<DraftRow, 'unitPrefix' | 'unitFormula' | 'unitLabel'>, unit: UnitOption) =>
  (row.unitPrefix ?? null) === (unit.prefix ?? null) && (row.unitFormula ?? null) === (unit.formula ?? null) && (row.unitLabel ?? null) === (unit.label ?? null)

export function setUnit(row: DraftRow, unit: UnitOption): void {
  row.unitPrefix = unit.prefix ?? null
  row.unitFormula = unit.formula ?? null
  row.unitLabel = unit.label ?? null
}

/**
 * "Für alle übernehmen" of the unit: sets the unit on every editable row of the chart that offers it (units per row
 * from `unitsOf`). Returns the number of rows that do not offer it (left unchanged).
 */
export function applyUnitToAll(chart: DraftChart, unit: UnitOption, unitsOf: (row: DraftRow) => UnitOption[] | undefined): number {
  let skipped = 0
  for (const row of chart.rows) {
    if (!row.readable) continue
    const offered = unitsOf(row)
    const match = offered?.find((u) => (u.label ?? null) === (unit.label ?? null) && (u.formula ?? null) === (unit.formula ?? null) && (u.prefix ?? null) === (unit.prefix ?? null))
    if (match) setUnit(row, match)
    else skipped++
  }
  return skipped
}

/** "Für alle übernehmen" of the colour: every editable row of the chart gets it. */
export function applyColorToAll(chart: DraftChart, color: string): void {
  for (const row of chart.rows) if (row.readable) row.color = color
}

/** ISO weekdays (1 = Monday) of the stored string "6,7". */
export function parseWeekdays(value: string): number[] {
  const days = new Set<number>()
  for (const part of (value ?? '').split(',')) {
    const n = Number(part.trim())
    if (Number.isInteger(n) && n >= 1 && n <= 7) days.add(n)
  }
  return [...days].sort((a, b) => a - b)
}

export const formatWeekdays = (days: number[]) => [...new Set(days)].sort((a, b) => a - b).join(',')

/** Dates (yyyy-MM-dd) of the stored string (comma, semicolon or space separated), sorted, unique. */
export function parseDates(value: string): string[] {
  return [...new Set((value ?? '').split(/[,;\s]+/).filter((d) => /^\d{4}-\d{2}-\d{2}$/.test(d)))].sort()
}

export const formatDates = (dates: string[]) => [...new Set(dates)].sort().join(',')

/** The id of the existing analysis of a 409 "name taken" answer, or null for any other error. */
export function nameTakenId(e: unknown): number | null {
  if (!(e instanceof HttpError) || e.status !== 409) return null
  const body = e.json<Partial<NameTaken>>()
  return body && body.nameTaken === true && typeof body.id === 'number' ? body.id : null
}

/** The current analysis of a 409 version conflict (the body), or null. */
export function conflictConfig(e: unknown, id: number): AnalysisConfig | null {
  if (!(e instanceof HttpError) || e.status !== 409) return null
  const body = e.json<AnalysisConfig>()
  return body && typeof body === 'object' && !('nameTaken' in body) && body.id === id && Array.isArray(body.charts) ? body : null
}

/** Value of an `<input type="datetime-local">` for a stored ISO date-time ('' if none). */
export function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const ms = Date.parse(iso)
  if (Number.isNaN(ms)) return ''
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
