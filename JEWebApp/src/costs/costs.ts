/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { formatNumber } from '../analysis/format'
import { CSV_BOM, csvField, csvNumber, csvSeparators, fileName } from '../analysis/table'
import { toIsoLocal } from '../analysis/settings'
import type { CostCell, CostConfigurationSummary, CostDefinition, CostList, CostResultRequest, CostSite, CostTemplateSummary } from '../api/costs'
import { HttpError } from '../api/http'
import { matchesTerms, searchTerms } from '../modules/search'

/*
 * Cost and result calculation (W4.8, view only) like the Control Center's Accounting Plugin tab "View" and the
 * "Template Result Calculation" plugin: accounting configurations and result calculation templates grouped by site,
 * a time frame (and for templates the input objects), and the calculated output grid of the template.
 */

/* ---------- list ---------- */

/** One entry of the list: a configuration ("Abrechnung") or a template ("Vorlage"). */
export interface CostEntry {
  kind: 'configuration' | 'template'
  id: number
  name: string
  path: string | null
  site: CostSite | null
  /** configurations: name of the template (null if unknown) */
  templateName: string | null
  /** configurations: false if the template is missing or not readable */
  templateReadable: boolean
}

export function configurationEntry(c: CostConfigurationSummary): CostEntry {
  return {
    kind: 'configuration',
    id: c.id,
    name: c.name,
    path: c.path ?? null,
    site: c.site ?? null,
    templateName: c.template?.name ?? null,
    templateReadable: !!c.template?.readable,
  }
}

export function templateEntry(t: CostTemplateSummary): CostEntry {
  return { kind: 'template', id: t.id, name: t.name, path: t.path ?? null, site: t.site ?? null, templateName: null, templateReadable: true }
}

/** All entries of the answer: configurations first, then templates (each in the order of the answer). */
export function costEntries(list: CostList | null | undefined): CostEntry[] {
  return [...(list?.configurations ?? []).map(configurationEntry), ...(list?.templates ?? []).map(templateEntry)]
}

const siteName = (site: CostSite | null, locale: string) => (site ? site.names?.[locale] || site.name : '')

/** Entries in which every search word occurs in the name, path, site or template name. */
export function filterCostEntries(entries: readonly CostEntry[], text: string, locale: string): CostEntry[] {
  const terms = searchTerms(text, locale)
  if (!terms.length) return [...entries]
  return entries.filter((e) => matchesTerms(terms, [e.name, e.path, siteName(e.site, locale), e.templateName], locale))
}

export interface CostSiteGroup {
  /** site id or "none" */
  key: string
  /** site name, null for entries without site */
  site: string | null
  entries: CostEntry[]
}

/** Groups the entries by site, keeping the order of the answer (the server sorts by site, path and name). */
export function groupBySite(entries: readonly CostEntry[], locale: string): CostSiteGroup[] {
  const groups = new Map<string, CostSiteGroup>()
  for (const e of entries) {
    const key = e.site ? String(e.site.id) : 'none'
    let group = groups.get(key)
    if (!group) {
      group = { key, site: e.site ? siteName(e.site, locale) : null, entries: [] }
      groups.set(key, group)
    }
    group.entries.push(e)
  }
  return [...groups.values()]
}

/** The ?id= of the route, null if missing or invalid. */
export function parseCostId(value: unknown): number | null {
  const text = Array.isArray(value) ? value[0] : value
  if (typeof text !== 'string' || !/^\d+$/.test(text)) return null
  const id = Number(text)
  return id > 0 && Number.isSafeInteger(id) ? id : null
}

/* ---------- time frame and selections (state <-> URL query) ---------- */

export const CUSTOM = 'CUSTOM'

/** Prefix of the query keys of the template inputs: in.<input id>=<object id>. */
export const SELECTION_PREFIX = 'in.'

/** Settings of the calculation: time frame (from/to only for CUSTOM) and, for templates, the selected objects. */
export interface CostState {
  timeFrame: string
  from: Date | null
  to: Date | null
  /** reference date of the period for the other time frames (null = now, i.e. the current period) */
  date?: Date | null
  /** selectable input id → object id */
  selections: Record<string, number>
}

type QueryValue = string | null | undefined | (string | null)[]
type Query = Record<string, QueryValue>

function single(value: QueryValue): string | undefined {
  if (Array.isArray(value)) return value[0] ?? undefined
  return value ?? undefined
}

function parseDate(value: string | undefined): Date | null {
  if (!value) return null
  // a "+" of the offset typed into a link arrives as space
  const ms = Date.parse(value.trim().replace(/ (\d{2}:?\d{2})$/, '+$1'))
  return Number.isNaN(ms) ? null : new Date(ms)
}

/** The current month up to now (start of a custom range when the template enables no time frame). */
function currentMonth(now: Date): { from: Date; to: Date } {
  return { from: new Date(now.getFullYear(), now.getMonth(), 1), to: now }
}

/** Default time frame of the definition: defaultTimeFrame if offered, else the first, else CUSTOM. */
export function defaultTimeFrame(def: Pick<CostDefinition, 'timeFrames' | 'defaultTimeFrame'>): string {
  const ids = (def.timeFrames ?? []).map((tf) => tf.id)
  if (def.defaultTimeFrame && ids.includes(def.defaultTimeFrame)) return def.defaultTimeFrame
  return ids[0] ?? CUSTOM
}

/** Default selections: the template's default object of each selectable input. */
export function defaultSelections(def: Pick<CostDefinition, 'inputs'>): Record<string, number> {
  const selections: Record<string, number> = {}
  for (const input of def.inputs ?? []) if (input.selectable && input.selected) selections[input.id] = input.selected.id
  return selections
}

export function defaultCostState(def: Pick<CostDefinition, 'timeFrames' | 'defaultTimeFrame' | 'inputs'>, now = new Date()): CostState {
  const timeFrame = defaultTimeFrame(def)
  const range = timeFrame === CUSTOM ? currentMonth(now) : { from: null, to: null }
  return { timeFrame, ...range, selections: defaultSelections(def) }
}

/** Object id of an input if it is one of the input's options (or its default object). */
function validSelection(def: Pick<CostDefinition, 'inputs'>, inputId: string, objectId: number): boolean {
  const input = (def.inputs ?? []).find((i) => i.id === inputId)
  if (!input?.selectable) return false
  return input.selected?.id === objectId || (input.options ?? []).some((o) => o.id === objectId)
}

/**
 * Reads the state from the URL query (tf, from, to, in.<input>): unknown time frames, invalid ranges and objects
 * that are no option of the input are replaced by the defaults.
 */
export function costStateFromQuery(query: Query, def: Pick<CostDefinition, 'timeFrames' | 'defaultTimeFrame' | 'inputs'>, now = new Date()): CostState {
  const state = defaultCostState(def, now)
  const tf = single(query.tf)
  if (tf === CUSTOM) {
    const from = parseDate(single(query.from))
    const to = parseDate(single(query.to))
    if (from && to && from.getTime() <= to.getTime()) Object.assign(state, { timeFrame: CUSTOM, from, to })
  } else if (tf && (def.timeFrames ?? []).some((f) => f.id === tf)) {
    const date = parseDate(single(query.d))
    Object.assign(state, { timeFrame: tf, from: null, to: null, date: date && date.getTime() < now.getTime() ? date : null })
  }
  for (const [key, value] of Object.entries(query)) {
    if (!key.startsWith(SELECTION_PREFIX)) continue
    const inputId = key.slice(SELECTION_PREFIX.length)
    const text = single(value)
    if (!text || !/^\d+$/.test(text)) continue
    const objectId = Number(text)
    if (validSelection(def, inputId, objectId)) state.selections[inputId] = objectId
  }
  return state
}

/** URL query of the state: tf, from/to with CUSTOM, in.<input> per selection of a selectable input. */
export function costStateToQuery(state: CostState, def: Pick<CostDefinition, 'inputs'>): Record<string, string> {
  const query: Record<string, string> = { tf: state.timeFrame }
  if (state.timeFrame === CUSTOM && state.from && state.to) {
    query.from = toIsoLocal(state.from)
    query.to = toIsoLocal(state.to)
  } else if (state.date) {
    query.d = toIsoLocal(state.date)
  }
  for (const input of def.inputs ?? []) {
    const id = state.selections[input.id]
    if (input.selectable && id != null) query[SELECTION_PREFIX + input.id] = String(id)
  }
  return query
}

/** True for the query keys written by {@link costStateToQuery} (removed when another entry is selected). */
export function isCostStateKey(key: string): boolean {
  return key === 'tf' || key === 'from' || key === 'to' || key === 'd' || key.startsWith(SELECTION_PREFIX)
}

/** Body of POST /web/costs/{id}/result; selections only for templates. */
export function costRequest(state: CostState, def: Pick<CostDefinition, 'kind' | 'inputs'>, timeZone: string): CostResultRequest {
  const custom = state.timeFrame === CUSTOM && state.from && state.to
  const request: CostResultRequest = {
    timeFrame: state.timeFrame,
    from: custom ? toIsoLocal(state.from!) : null,
    to: custom ? toIsoLocal(state.to!) : null,
    timeZone,
  }
  if (!custom && state.date) request.date = toIsoLocal(state.date)
  if (def.kind === 'template') {
    const selections: Record<string, number> = {}
    for (const input of def.inputs ?? []) {
      const id = state.selections[input.id]
      if (input.selectable && id != null) selections[input.id] = id
    }
    request.selections = selections
  }
  return request
}

/* ---------- result grid ---------- */

const span = (n: number | null | undefined) => (typeof n === 'number' && n >= 1 ? Math.floor(n) : 1)
const index = (n: number | null | undefined) => (typeof n === 'number' && n >= 0 ? Math.floor(n) : 0)

/** Cells in reading order: row, then column. */
export function orderCells(cells: readonly CostCell[] | null | undefined): CostCell[] {
  return (cells ?? [])
    .map((cell, i) => ({ cell, i }))
    .sort((a, b) => index(a.cell.row) - index(b.cell.row) || index(a.cell.column) - index(b.cell.column) || a.i - b.i)
    .map((x) => x.cell)
}

export type SeparatorOrientation = 'horizontal' | 'vertical'

/** Orientation of a separator: as given, else horizontal if it spans columns, vertical if it spans rows. */
export function separatorOrientation(cell: Pick<CostCell, 'orientation' | 'rowSpan' | 'columnSpan'>): SeparatorOrientation {
  if (cell.orientation === 'horizontal' || cell.orientation === 'vertical') return cell.orientation
  if (span(cell.columnSpan) > 1) return 'horizontal'
  if (span(cell.rowSpan) > 1) return 'vertical'
  return 'horizontal'
}

export interface PlacedCell {
  key: string
  cell: CostCell
  /** CSS grid placement, 1-based */
  gridRow: string
  gridColumn: string
  /** separators only */
  orientation: SeparatorOrientation | null
}

export interface CostGridLayout {
  rows: number
  columns: number
  cells: PlacedCell[]
}

/** Placement of the cells in a CSS grid exactly by row/column/rowSpan/columnSpan, in reading order. */
export function gridLayout(result: { rows?: number | null; columns?: number | null; cells?: CostCell[] | null }): CostGridLayout {
  const cells = orderCells(result.cells)
  let rows = Math.max(0, index(result.rows))
  let columns = Math.max(0, index(result.columns))
  const placed = cells.map((cell, i) => {
    const r = index(cell.row)
    const c = index(cell.column)
    const rs = span(cell.rowSpan)
    const cs = span(cell.columnSpan)
    rows = Math.max(rows, r + rs)
    columns = Math.max(columns, c + cs)
    return {
      key: `${r}-${c}-${i}`,
      cell,
      gridRow: `${r + 1} / span ${rs}`,
      gridColumn: `${c + 1} / span ${cs}`,
      orientation: cell.kind === 'separator' ? separatorOrientation(cell) : null,
    }
  })
  return { rows, columns: Math.max(1, columns), cells: placed }
}

/** Phone width: one column in reading order; vertical separators are left out (they only divide columns). */
export function singleColumnCells(layout: CostGridLayout): PlacedCell[] {
  return layout.cells.filter((p) => p.orientation !== 'vertical')
}

/** 2 decimals like the Control Center; "–" if the value could not be calculated. */
export function formatCostNumber(value: number | null | undefined, locale: string): string {
  if (value == null || !Number.isFinite(value)) return '–'
  return formatNumber(value, locale, { min: 2, max: 2 })
}

/** Shown result of a value or text cell (without unit). */
export function cellResultText(cell: CostCell, locale: string): string {
  if (cell.kind === 'text') return cell.text != null && cell.text !== '' ? cell.text : '–'
  return formatCostNumber(cell.value, locale)
}

/** Cells that carry a result (value and text); they go into the CSV. */
export const hasResult = (cell: Pick<CostCell, 'kind'>) => cell.kind === 'value' || cell.kind === 'text'

/** CSV of the result: name; value; unit per value/text cell in row/column order. UTF-8 with BOM and CRLF (Excel). */
export function costCsv(cells: readonly CostCell[], locale: string, header: { name: string; value: string; unit: string }): string {
  const { field, decimal } = csvSeparators(locale)
  const lines = [[header.name, header.value, header.unit].map((h) => csvField(h, field)).join(field)]
  for (const cell of orderCells(cells)) {
    if (!hasResult(cell)) continue
    let value = ''
    if (cell.kind === 'text') value = cell.text ? csvField(cell.text, field) : ''
    else if (cell.value != null && Number.isFinite(cell.value)) value = csvNumber(cell.value, decimal)
    lines.push([csvField(cell.name ?? '', field), value, csvField(cell.unit ?? '', field)].join(field))
  }
  return CSV_BOM + lines.join('\r\n') + '\r\n'
}

const pad = (n: number) => String(n).padStart(2, '0')
const day = (ms: number) => {
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** "Strom Musterwerk 2026-11-01_2026-11-30.csv" */
export function costCsvName(name: string, from: string | null | undefined, to: string | null | undefined): string {
  const f = parseTime(from)
  const t = parseTime(to)
  const range = f != null && t != null ? ` ${day(f)}_${day(t)}` : ''
  return fileName(`${name}${range}`) + '.csv'
}

/* ---------- period ---------- */

export function parseTime(text: string | null | undefined): number | null {
  if (!text) return null
  const ms = Date.parse(text)
  return Number.isNaN(ms) ? null : ms
}

const isDayStart = (d: Date) => d.getHours() === 0 && d.getMinutes() === 0 && d.getSeconds() === 0
const isDayEnd = (d: Date) => d.getHours() === 23 && d.getMinutes() === 59 && d.getSeconds() === 59

/**
 * Resolved period for the UI: "01.11.2026 – 30.11.2026" for whole days (in the browser's time zone), otherwise
 * with times; the stored texts if they cannot be read.
 */
export function periodText(from: string | null | undefined, to: string | null | undefined, locale: string): string {
  const f = parseTime(from)
  const t = parseTime(to)
  if (f == null || t == null) return [from, to].filter(Boolean).join(' – ')
  const df = new Date(f)
  const dt = new Date(t)
  const wholeDays = isDayStart(df) && (isDayEnd(dt) || isDayStart(dt))
  const options: Intl.DateTimeFormatOptions = wholeDays ? { dateStyle: 'medium' } : { dateStyle: 'medium', timeStyle: 'short' }
  const format = new Intl.DateTimeFormat(locale, options)
  // an end at 00:00 means the end of the day before
  const end = wholeDays && isDayStart(dt) && t > f ? t - 1 : t
  return `${format.format(f)} – ${format.format(end)}`
}

/* ---------- analysis link ---------- */

/** The samples endpoint answers at most 31 days. */
export const ANALYSIS_MAX_DAYS = 31
const DAY_MS = 86_400_000
/** a month with the change from summer to winter time is one hour longer */
const DST_SLACK_MS = 3_600_000

/**
 * Range of the row chart for the result's from/to: null if unreadable or longer than 31 days (then the chart button
 * is disabled). A 31-day month with the time change is cut to 31 × 24 h so the server accepts it.
 */
export function analysisRange(from: string | null | undefined, to: string | null | undefined): { from: number; to: number } | null {
  const f = parseTime(from)
  const t = parseTime(to)
  if (f == null || t == null || t < f) return null
  const max = ANALYSIS_MAX_DAYS * DAY_MS
  if (t - f > max + DST_SLACK_MS) return null
  return { from: f, to: Math.min(t, f + max) }
}

/* ---------- errors ---------- */

export type CostErrorKind = 'notFound' | 'template' | 'invalid' | 'error'

/** 404: not readable; 409: the configuration's template is missing or not readable; 400: invalid settings. */
export function costErrorKind(e: unknown): CostErrorKind {
  if (e instanceof HttpError) {
    if (e.status === 404 || e.status === 403) return 'notFound'
    if (e.status === 409) return 'template'
    if (e.status === 400) return 'invalid'
  }
  return 'error'
}

/** Plain-text message of a 400 answer (the server sends text/plain); '' for empty or HTML bodies. */
export function serverMessage(e: unknown): string {
  if (!(e instanceof HttpError)) return ''
  const body = (e.body ?? '').trim()
  if (!body || body.startsWith('<')) return ''
  if (body.startsWith('{')) {
    try {
      const json = JSON.parse(body) as { message?: unknown; error?: unknown }
      const text = json.message ?? json.error
      return typeof text === 'string' ? text : ''
    } catch {
      return body.slice(0, 300)
    }
  }
  return body.slice(0, 300)
}
