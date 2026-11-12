/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

import type { Component } from 'vue'
import AlarmCountSettings from './types/AlarmCountSettings.vue'
import ArrowSettings from './types/ArrowSettings.vue'
import BatterySettings from './types/BatterySettings.vue'
import GaugeSettings from './types/GaugeSettings.vue'
import ImageSettings from './types/ImageSettings.vue'
import LinearGaugeSettings from './types/LinearGaugeSettings.vue'
import LinkSettings from './types/LinkSettings.vue'
import MinMaxSettings from './types/MinMaxSettings.vue'
import NetGraphSettings from './types/NetGraphSettings.vue'
import SankeySettings from './types/SankeySettings.vue'
import ShapeSettings from './types/ShapeSettings.vue'
import TimeFrameSettings from './types/TimeFrameSettings.vue'
import ValueSettings from './types/ValueSettings.vue'
import { typeLabelKey } from './types/shared'
import { TYPE_IDS, typeHasRows } from './widgetDefaults'

/*
 * The widget types of the dashboard editor (E4) and their settings components. Each component takes
 * `{ widget: RawWidget; source: RawDashboard; dashboardId: number | null }` and changes `widget` in place.
 */

/**
 * The 23 widget types of the Control Center (widget/Widgets.java, registration order), exact ids. `labelKey`: i18n key
 * of the name (the CC's `dashboard.widget.*` / `plugin.dashboard.*`), `hasRows`: the type uses data rows (the CC's
 * DataModelDataHandler, `toNode` writes `dataHandler`).
 */
export const WIDGET_TYPES: readonly { id: string; labelKey: string; hasRows: boolean }[] = TYPE_IDS.map((id) => ({
  id,
  labelKey: typeLabelKey(id) as string,
  hasRows: typeHasRows(id),
}))

/* Title, Chart (settings by the editor core), Pie, Table, String Value, Value Editor and Toggle Switch have no type tab in the CC */
const EDITORS: Record<string, Component> = {
  Value: ValueSettings,
  Gauge: GaugeSettings,
  LinearGauge: LinearGaugeSettings,
  Battery: BatterySettings,
  Shape: ShapeSettings,
  NetGraph: NetGraphSettings,
  Sankey: SankeySettings,
  'Time Frame': TimeFrameSettings,
  Slider: MinMaxSettings,
  PlusMinus: MinMaxSettings,
  Arrow: ArrowSettings,
  Image: ImageSettings,
  'Analyse-Link': LinkSettings,
  'Dashboard-Link': LinkSettings,
  'Report-Link': LinkSettings,
  'Alarm Count': AlarmCountSettings,
}

/** Settings component of a widget type; null for types without own settings. */
export function typeEditorFor(type: string): Component | null {
  return EDITORS[type] ?? null
}
