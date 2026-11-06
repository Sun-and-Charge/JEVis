/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

// The ECharts parts of the analysis view (Chart and Pie widgets) plus those only dashboards need.
import { use } from 'echarts/core'
import { GaugeChart, RadarChart, SankeyChart } from 'echarts/charts'
import { RadarComponent } from 'echarts/components'
import '../analysis/echarts'

use([GaugeChart, RadarChart, SankeyChart, RadarComponent])
