/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

// Only the parts of Apache ECharts that the analysis view needs (keeps the bundle small).
import { registerLocale, use } from 'echarts/core'
import { BarChart, HeatmapChart, LineChart, PieChart, ScatterChart } from 'echarts/charts'
import { DataZoomInsideComponent, DataZoomSliderComponent, GridComponent, LegendScrollComponent, TooltipComponent, VisualMapContinuousComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import langDE from 'echarts/i18n/langDE-obj.js'

use([
  LineChart,
  BarChart,
  ScatterChart,
  PieChart,
  HeatmapChart,
  GridComponent,
  TooltipComponent,
  LegendScrollComponent,
  DataZoomInsideComponent,
  DataZoomSliderComponent,
  VisualMapContinuousComponent,
  CanvasRenderer,
])
registerLocale('DE', langDE as Parameters<typeof registerLocale>[1])

/** ECharts locale for the app language (month and day names of time axes). */
export function echartsLocale(locale: string): string {
  return locale.startsWith('de') ? 'DE' : 'EN'
}
