<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import { formatNumber } from '../../analysis/format'
import type { WidgetContext } from '../../dashboard/context'
import { alignment } from '../../dashboard/style'
import { parseTime, valueDisplay, widgetDecimals, widgetRows } from '../../dashboard/values'
import { timeFrameText, widgetKind } from '../../dashboard/widgets'

/** Widgets that show one line of text: Title, Value, String Value, Time Frame, Alarm Count. */
const props = defineProps<{ widget: WidgetConfig; data: WidgetData | undefined; ctx: WidgetContext }>()
const { t } = useI18n()

const kind = computed(() => widgetKind(props.widget.type))
const align = computed(() => alignment(props.widget.style?.titlePosition))
const first = computed(() => widgetRows(props.widget, props.data)[0])

/** no value because of the row state: shown greyed like in the analysis view */
const stateText = computed(() => {
  if (kind.value === 'title' || kind.value === 'timeFrame' || kind.value === 'alarmCount') return null
  switch (first.value?.state) {
    case 'NO_PERMISSION':
      return t('analysis.state.noPermission')
    case 'ERROR':
      return t('analysis.state.error')
    default:
      return null
  }
})

const display = computed(() => {
  switch (kind.value) {
    case 'title':
      return { text: props.widget.title ?? '', color: null, dimmed: false }
    case 'value':
      return valueDisplay(props.widget, first.value, props.ctx.values, props.ctx.locale, parseTime(props.data?.to ?? props.ctx.data?.to), props.ctx.now)
    case 'string': {
      const row = first.value
      const text = row?.text ?? (row?.value != null ? formatNumber(row.value, props.ctx.locale, widgetDecimals(props.widget, row.config)) + (row.unit ? ' ' + row.unit : '') : '')
      return { text, color: null, dimmed: false }
    }
    case 'timeFrame':
      return { text: timeFrameText(props.widget, props.ctx.data, props.ctx.locale), color: null, dimmed: false }
    case 'alarmCount': {
      const n = props.data?.alarmCount
      return { text: typeof n === 'number' ? formatNumber(n, props.ctx.locale, { min: 0, max: 0 }) : '', color: null, dimmed: false }
    }
    default:
      return { text: '', color: null, dimmed: false }
  }
})
</script>

<template>
  <div class="text" :style="{ justifyContent: align.justifyContent, alignItems: align.alignItems, textAlign: align.textAlign }">
    <span v-if="stateText" class="state" :title="first?.data?.message ?? undefined">{{ stateText }}</span>
    <span v-else class="value" :class="{ dimmed: display.dimmed }" :style="display.color ? { color: display.color } : undefined">{{ display.text }}</span>
  </div>
</template>

<style scoped>
.text {
  display: flex;
  width: 100%;
  height: 100%;
  padding: 0.15em 0.35em;
  overflow: hidden;
}

.value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
}

.dimmed {
  opacity: 0.5;
}

.state {
  color: #8a93a3;
  font-weight: 400;
  font-size: 0.85em;
}
</style>
