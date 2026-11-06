/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { AnalysisChartConfig, AnalysisConfig, AnalysisData, AnalysisRowConfig } from '../../src/api/analysis'

/** Mocked answers of /web/analyses (neutral names, no real data). */
export function row(key: string, overrides: Partial<AnalysisRowConfig> = {}): AnalysisRowConfig {
  return {
    key,
    objectId: 1000,
    attribute: 'Value',
    name: 'Meter ' + key,
    color: '#00538a',
    axis: 0,
    chartType: 'DEFAULT',
    unit: 'kWh',
    decimalDigits: -1,
    calculation: false,
    readable: true,
    ...overrides,
  }
}

export function chart(id: number, type: string, rows: AnalysisRowConfig[], overrides: Partial<AnalysisChartConfig> = {}): AnalysisChartConfig {
  return {
    id,
    name: 'Chart ' + id,
    type,
    minFractionDigits: 2,
    maxFractionDigits: 2,
    height: -1,
    orientation: 'HORIZONTAL',
    fixYAxisToZero: false,
    xAxisTitle: null,
    yAxisTitle: null,
    groupingInterval: 10,
    colorMapping: 'GREEN_YELLOW_RED',
    rows,
    ...overrides,
  }
}

export function config(charts: AnalysisChartConfig[]): AnalysisConfig {
  return {
    id: 1,
    name: 'Analysis A',
    names: { de: 'Analyse A', en: 'Analysis A' },
    chartsPerScreen: 2,
    horizontalPies: 2,
    horizontalTables: 1,
    autoSize: true,
    defaults: { timeFrame: 'LAST_7_DAYS', aggregation: 'DAILY', manipulation: 'NONE' },
    charts,
  }
}

/** Local midnight of 1 Sep 2026 plus days/hours (independent of the time zone of the test machine). */
export const at = (day: number, hour = 0, minute = 0) => new Date(2026, 8, day, hour, minute).getTime()

export function data(charts: AnalysisData['charts']): AnalysisData {
  return { from: new Date(at(1)).toISOString(), to: new Date(at(8) - 1).toISOString(), charts }
}
