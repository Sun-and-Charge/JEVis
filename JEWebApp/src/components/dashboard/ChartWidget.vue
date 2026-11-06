<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import { chartOption } from '../../analysis/options'
import type { WidgetContext } from '../../dashboard/context'
import { parseTime } from '../../dashboard/values'
import { chartWidgetView, pieWidgetView, widgetKind } from '../../dashboard/widgets'
import DataTable from '../analysis/DataTable.vue'
import WidgetChart from './WidgetChart.vue'

/** Chart and Pie widgets: the chart options of the analysis view. */
const props = defineProps<{ widget: WidgetConfig; data: WidgetData | undefined; ctx: WidgetContext }>()
const { t } = useI18n()

const view = computed(() =>
  widgetKind(props.widget.type) === 'pie'
    ? pieWidgetView(props.widget, props.data, t('analysis.state.noAccess'))
    : chartWidgetView(props.widget, props.data, t('analysis.state.noAccess')),
)
const option = computed(() => {
  const v = view.value
  if (!v || v.kind === 'table') return null
  const from = parseTime(props.data?.from ?? props.ctx.data?.from)
  const to = parseTime(props.data?.to ?? props.ctx.data?.to)
  return chartOption(v, props.ctx.chart, from, to)
})
</script>

<template>
  <div class="chartw">
    <DataTable v-if="view && view.kind === 'table'" :chart="view" :ctx="ctx.chart" class="table" />
    <WidgetChart v-else :option="option" :locale="ctx.locale" :label="widget.title || view?.name" />
  </div>
</template>

<style scoped>
.chartw {
  display: flex;
  width: 100%;
  height: 100%;
  padding: 0.25em;
  font-size: 13px;
}

.table {
  width: 100%;
  overflow: auto;
}
</style>
