/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { RawDashboard, RawRow, RawWidget } from '../../src/dashboard/edit/raw'

/** Dashboard sources as the Control Center stores them (neutral names, no real data). */

export const SOURCE_VERSION = '2026-11-12T10:00:00.000+01:00'

export function rawRow(id: number, overrides: Partial<RawRow> = {}): RawRow {
  return {
    id,
    attributeString: 'Value',
    name: 'Musterwerk ' + id,
    color: '0x00538aff',
    unitPrefix: 'NONE',
    unitFormula: 'kWh',
    unitLabel: 'kWh',
    axis: 0,
    calculation: false,
    calculationId: -1,
    chartType: 'DEFAULT',
    aggregationPeriod: 'NONE',
    manipulationMode: 'NONE',
    decimalDigits: -1,
    ...overrides,
  }
}

export function rawWidget(uuid: number, type: string, overrides: Partial<RawWidget> = {}): RawWidget {
  return {
    uuid,
    WidgetType: type,
    title: 'Widget ' + uuid,
    tooltip: '',
    'Background Color': '0xffffffff',
    'Font Color': '0x1a2a45ff',
    fontSize: 13,
    fontWeight: 'BOLD',
    fontPosture: 'REGULAR',
    fontUnderlined: false,
    'Title Position': 'TOP_LEFT',
    borderSize: 1,
    borderColor: '0xd0d4daff',
    cornerRadius: 4,
    shadow: false,
    layer: 0,
    xPos: 10,
    yPos: 20,
    width: 200,
    height: 100,
    decimals: 2,
    decimalsAuto: false,
    showValue: true,
    fixedTimeframe: false,
    forcedTimeFrame: 'PT0S',
    ...overrides,
  }
}

export function withRows(widget: RawWidget, rows: RawRow[], forcedInterval = ''): RawWidget {
  widget.dataHandler = { type: 'AnalysisHandler', forcedInterval, chartModels: [{ chartId: 0, chartName: '', chartType: 'LINE', chartData: rows }] }
  return widget
}

export function rawSource(widgets: RawWidget[], overrides: Partial<RawDashboard> = {}): RawDashboard {
  return {
    version: '1.2',
    'Dash Board Color': '0xf4f4f4ff',
    backgroundMode: 'default',
    'Snap to Grid': true,
    'Show Grid': false,
    'X Axis Grid Interval': 25,
    'Y Axis Grid Interval': 25,
    'Update Rate': 900,
    'Zoom Factor': 98,
    width: 1000,
    height: 500,
    'Data Period': 'P1D',
    'Unknown Key': { kept: true },
    Widget: widgets,
    ...overrides,
  }
}
