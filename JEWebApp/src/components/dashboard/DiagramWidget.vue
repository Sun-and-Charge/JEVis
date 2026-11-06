<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import type { WidgetContext } from '../../dashboard/context'
import { netGraphOption, sankeyConfig, sankeyOption } from '../../dashboard/options'
import { cssColor, DEFAULT_FONT_SIZE } from '../../dashboard/style'
import { num, widgetDecimals, widgetRows } from '../../dashboard/values'
import { widgetKind } from '../../dashboard/widgets'
import WidgetChart from './WidgetChart.vue'

/** NetGraph (ECharts radar) and Sankey (ECharts sankey). */
const props = defineProps<{ widget: WidgetConfig; data: WidgetData | undefined; ctx: WidgetContext }>()
const { t } = useI18n()

const option = computed(() => {
  const rows = widgetRows(props.widget, props.data)
  const c = props.widget.config ?? {}
  const base = {
    locale: props.ctx.locale,
    decimals: widgetDecimals(props.widget, null),
    color: cssColor(props.widget.style?.fontColor) ?? '#1a2a45',
    noAccess: t('analysis.state.noAccess'),
    fontSize: (props.widget.style?.fontSize || DEFAULT_FONT_SIZE) * props.ctx.scale,
  }
  if (widgetKind(props.widget.type) === 'sankey') return sankeyOption(rows, sankeyConfig(c), base)
  return netGraphOption(rows, { minimum: num(c.minimum, 0), maximum: num(c.maximum, 100), inPercent: !!c.inPercent }, base)
})
</script>

<template>
  <WidgetChart :option="option" :locale="ctx.locale" :label="widget.title || undefined" />
</template>
