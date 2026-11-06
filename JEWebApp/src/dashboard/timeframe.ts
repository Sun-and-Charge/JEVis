/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { DashboardConfig, DashboardData, DashboardDataRequest, DashboardTimeFrameOption, WidgetLink } from '../api/dashboard'
import { toIsoLocal } from '../analysis/settings'
import { parseTime } from './values'

/** Time frames of the Control Center dashboards (custom periods of the server come with their object id). */
export const DASHBOARD_TIME_FRAMES = ['P1D', 'P1W', 'P1M', 'P1Y', 'P3Y', 'P5Y', 'P10Y', 'PT24H', 'P7D', 'P30D', 'P365D', 'PALL', 'CUSTOM'] as const

/** State of the time frame bar. `date` = reference time (null = now); from/to only for CUSTOM. */
export interface TimeBarState {
  timeFrame: string
  date: Date | null
  from: Date | null
  to: Date | null
}

/** The selectable time frames; the one of the dashboard is always included. */
export function timeFrameOptions(config: Pick<DashboardConfig, 'timeFrame' | 'timeFrames'>): DashboardTimeFrameOption[] {
  const list = (config.timeFrames ?? []).filter((o) => o && o.id != null).map((o) => ({ ...o, id: String(o.id) }))
  if (config.timeFrame && !list.some((o) => o.id === String(config.timeFrame))) list.unshift({ id: String(config.timeFrame), custom: false })
  if (!list.length) list.push({ id: 'P1D', custom: false })
  return list
}

export function defaultTimeBar(config: Pick<DashboardConfig, 'timeFrame' | 'timeFrames'>): TimeBarState {
  const options = timeFrameOptions(config)
  const tf = config.timeFrame && config.timeFrame !== 'CUSTOM' ? String(config.timeFrame) : options.find((o) => o.id !== 'CUSTOM')?.id ?? 'P1D'
  return { timeFrame: tf, date: null, from: null, to: null }
}

type QueryValue = string | null | undefined | (string | null)[]

function single(value: QueryValue): string | undefined {
  if (Array.isArray(value)) return value[0] ?? undefined
  return value ?? undefined
}

function parseDate(value: string | undefined): Date | null {
  if (!value) return null
  const ms = Date.parse(value.trim().replace(/ (\d{2}:?\d{2})$/, '+$1'))
  return Number.isNaN(ms) ? null : new Date(ms)
}

/** State from the URL query (tf, date, from, to); invalid values fall back to the dashboard's time frame. */
export function timeBarFromQuery(query: Record<string, QueryValue>, config: Pick<DashboardConfig, 'timeFrame' | 'timeFrames'>): TimeBarState {
  const state = defaultTimeBar(config)
  const tf = single(query.tf)
  const options = timeFrameOptions(config)
  if (tf === 'CUSTOM') {
    const from = parseDate(single(query.from))
    const to = parseDate(single(query.to))
    if (from && to && from.getTime() <= to.getTime()) return { timeFrame: 'CUSTOM', date: null, from, to }
    return state
  }
  if (tf && options.some((o) => o.id === tf)) state.timeFrame = tf
  state.date = parseDate(single(query.date))
  return state
}

export function timeBarToQuery(state: TimeBarState): Record<string, string> {
  const query: Record<string, string> = { tf: state.timeFrame }
  if (state.timeFrame === 'CUSTOM') {
    if (state.from && state.to) {
      query.from = toIsoLocal(state.from)
      query.to = toIsoLocal(state.to)
    }
  } else if (state.date) {
    query.date = toIsoLocal(state.date)
  }
  return query
}

export function dashboardDataRequest(state: TimeBarState, timeZone: string): DashboardDataRequest {
  const custom = state.timeFrame === 'CUSTOM' && state.from && state.to
  return {
    timeFrame: state.timeFrame,
    date: !custom && state.date ? toIsoLocal(state.date) : null,
    from: custom ? toIsoLocal(state.from!) : null,
    to: custom ? toIsoLocal(state.to!) : null,
    timeZone,
  }
}

/**
 * State after an arrow of the time frame bar, or null if not possible. Uses previous/next of the answer;
 * CUSTOM periods are shifted by their own length (next only while it does not start in the future).
 */
export function stepTimeBar(state: TimeBarState, data: Pick<DashboardData, 'previous' | 'next'> | null, direction: -1 | 1, now: number = Date.now()): TimeBarState | null {
  if (state.timeFrame === 'CUSTOM') {
    if (!state.from || !state.to) return null
    const length = state.to.getTime() - state.from.getTime() + 1
    const from = state.from.getTime() + direction * length
    if (direction > 0 && from > now) return null
    return { ...state, from: new Date(from), to: new Date(state.to.getTime() + direction * length) }
  }
  if (!data) return null
  const ms = parseTime(direction < 0 ? data.previous : data.next)
  return ms == null ? null : { ...state, date: new Date(ms) }
}

/** Selecting a time frame: CUSTOM starts with the period shown now, the others with the current period. */
export function selectTimeFrame(timeFrame: string, shown: { from: number; to: number } | null, now: Date = new Date()): TimeBarState {
  if (timeFrame === 'CUSTOM') {
    const from = shown ? new Date(shown.from) : new Date(new Date(now).setHours(0, 0, 0, 0))
    const to = shown ? new Date(shown.to) : now
    return { timeFrame, date: null, from, to }
  }
  return { timeFrame, date: null, from: null, to: null }
}

/* ---------- links to analyses ---------- */

/** Dashboard time frames that have an exact counterpart in the analysis view (for the current period). */
const ANALYSIS_PRESETS: Record<string, string> = {
  P1D: 'TODAY',
  P1W: 'THIS_WEEK',
  P1M: 'THIS_MONTH',
  P1Y: 'THIS_YEAR',
  P7D: 'LAST_7_DAYS',
  P30D: 'LAST_30_DAYS',
}

/**
 * Query of the analysis view for an Analyse-Link: a preset when the period is the current one, otherwise the
 * resolved period of the dashboard as CUSTOM. A link with its own time frame only maps to presets.
 */
export function analysisQuery(link: Pick<WidgetLink, 'timeFrame'>, state: TimeBarState, shown: { from: number; to: number } | null): Record<string, string> {
  const own = link.timeFrame != null && link.timeFrame !== '' && String(link.timeFrame) !== state.timeFrame
  if (own) {
    const preset = ANALYSIS_PRESETS[String(link.timeFrame)]
    return preset ? { tf: preset } : {}
  }
  if (state.timeFrame !== 'CUSTOM' && !state.date && ANALYSIS_PRESETS[state.timeFrame]) return { tf: ANALYSIS_PRESETS[state.timeFrame] }
  if (shown) return { tf: 'CUSTOM', from: toIsoLocal(new Date(shown.from)), to: toIsoLocal(new Date(shown.to)) }
  return {}
}

/** Query of the dashboard view for a Dashboard-Link with its own time frame. */
export function dashboardQuery(link: Pick<WidgetLink, 'timeFrame'>): Record<string, string> {
  return link.timeFrame != null && link.timeFrame !== '' && link.timeFrame !== 'CUSTOM' ? { tf: String(link.timeFrame) } : {}
}
