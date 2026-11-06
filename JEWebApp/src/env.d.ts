/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

/** German texts of Apache ECharts (time axis month and day names). */
declare module 'echarts/i18n/langDE-obj.js' {
  const lang: Record<string, unknown>
  export default lang
}
