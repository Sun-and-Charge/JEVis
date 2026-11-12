/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { DashboardData } from '../api/dashboard'
import type { OptionContext } from '../analysis/options'
import type { TimeBarState } from './timeframe'

/** What the widget components need besides their own configuration and data. */
export interface WidgetContext {
  dashboardId: number
  locale: string
  /** scale factor of the dashboard (positions, sizes, fonts) */
  scale: number
  /** value of each widget by uuid (percent and limit references) */
  values: Map<string, number>
  data: DashboardData | null
  state: TimeBarState
  /** resolved period of the dashboard (epoch ms) */
  shown: { from: number; to: number } | null
  /** texts for the reused analysis chart options */
  chart: OptionContext
  now: number
  /** E4: the dashboard is being edited (input widgets stay read-only) */
  editing?: boolean
}
