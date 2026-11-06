<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import type { WidgetContext } from '../../dashboard/context'
import { cssColor, mixColor } from '../../dashboard/style'
import { fraction, num, widgetRows } from '../../dashboard/values'

/** Shape widget: rectangle or ellipse, colored between minColor and maxColor by the value. */
const props = defineProps<{ widget: WidgetConfig; data: WidgetData | undefined; ctx: WidgetContext }>()

const c = computed(() => props.widget.config ?? {})
const ellipse = computed(() => String(c.value.shape ?? '').toUpperCase() === 'ELLIPSE')
const fill = computed(() => {
  const value = widgetRows(props.widget, props.data)[0]?.value ?? null
  const min = cssColor(c.value.minColor) ?? '#d9dee6'
  if (value == null) return min
  return mixColor(min, cssColor(c.value.maxColor) ?? min, fraction(value, num(c.value.minValue, 0), num(c.value.maxValue, 100))) ?? min
})
</script>

<template>
  <svg class="svg" viewBox="0 0 100 100" preserveAspectRatio="none" role="img" :aria-label="widget.title || undefined">
    <ellipse v-if="ellipse" cx="50" cy="50" rx="50" ry="50" :fill="fill" />
    <rect v-else x="0" y="0" width="100" height="100" :fill="fill" />
  </svg>
</template>

<style scoped>
.svg {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
