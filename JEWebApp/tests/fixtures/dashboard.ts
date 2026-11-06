/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { DashboardConfig, DashboardData, DashboardRowConfig, WidgetConfig } from '../../src/api/dashboard'

/** Mocked answers of /web/dashboards (neutral names, no real data). */
export function drow(key: string, overrides: Partial<DashboardRowConfig> = {}): DashboardRowConfig {
  return {
    key,
    objectId: 1000,
    attribute: 'Value',
    name: 'Meter ' + key,
    color: '#00538a',
    unit: 'kWh',
    decimalDigits: -1,
    calculation: false,
    readable: true,
    ...overrides,
  }
}

export function widget(uuid: string, type: string, overrides: Partial<WidgetConfig> = {}): WidgetConfig {
  return {
    uuid,
    type,
    x: 10,
    y: 20,
    width: 200,
    height: 100,
    layer: 0,
    title: 'Widget ' + uuid,
    tooltip: '',
    style: {
      background: '#ffffff',
      fontColor: '#1a2a45',
      fontSize: 13,
      fontWeight: 'BOLD',
      italic: false,
      underline: false,
      titlePosition: 'TOP_LEFT',
      borderSize: 1,
      borderColor: '#d0d4da',
      cornerRadius: 4,
      shadow: false,
    },
    decimals: 2,
    decimalsAuto: false,
    showValue: true,
    timeFrame: null,
    rows: [],
    chart: null,
    config: {},
    ...overrides,
  }
}

export function dashboard(widgets: WidgetConfig[], overrides: Partial<DashboardConfig> = {}): DashboardConfig {
  return {
    id: 7,
    name: 'Dashboard A',
    names: { de: 'Dashboard A (de)' },
    width: 1000,
    height: 500,
    zoom: 'FIT_WIDTH',
    background: { color: '#f4f4f4', image: false, mode: 'DEFAULT' },
    updateRate: 900,
    timeFrame: 'P1D',
    timeFrames: [
      { id: 'P1D', custom: false },
      { id: 'P1W', custom: false },
      { id: 'CUSTOM', custom: false },
      { id: '2005', name: 'Period X', custom: true },
    ],
    widgets,
    ...overrides,
  }
}

/** Local midnight of 1 Sep 2026 plus days/hours (independent of the time zone of the test machine). */
export const at = (day: number, hour = 0, minute = 0) => new Date(2026, 8, day, hour, minute).getTime()
const iso = (ms: number) => new Date(ms).toISOString()

export function ddata(widgets: DashboardData['widgets'], overrides: Partial<DashboardData> = {}): DashboardData {
  return { from: iso(at(1)), to: iso(at(2) - 1), previous: iso(at(0, 12)), next: null, widgets, ...overrides }
}

export { iso }
