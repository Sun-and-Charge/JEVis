<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import { formatNumber } from '../../analysis/format'
import type { WidgetContext } from '../../dashboard/context'
import { num, widgetDecimals, widgetRows } from '../../dashboard/values'

/** Value Editor, Toggle Switch, Slider and PlusMinus: the current value, the controls disabled (view only). */
const props = defineProps<{ widget: WidgetConfig; data: WidgetData | undefined; ctx: WidgetContext }>()
const { t } = useI18n()

const row = computed(() => widgetRows(props.widget, props.data)[0])
const value = computed(() => row.value?.value ?? (row.value?.text != null && row.value.text.trim() !== '' && !Number.isNaN(Number(row.value.text)) ? Number(row.value.text) : null))
const text = computed(() => {
  if (row.value?.state === 'NO_PERMISSION') return t('analysis.state.noPermission')
  if (value.value != null) return formatNumber(value.value, props.ctx.locale, widgetDecimals(props.widget, row.value?.config)) + (row.value?.unit ? ' ' + row.value.unit : '')
  return row.value?.text ?? '–'
})
const on = computed(() => {
  if (value.value != null) return value.value !== 0
  return /^(true|on|1)$/i.test(row.value?.text ?? '')
})
const min = computed(() => num(props.widget.config?.minimum, 0))
const max = computed(() => num(props.widget.config?.maximum, 100))
const label = computed(() => `${props.widget.title || props.widget.type}: ${text.value} (${t('dashboard.readOnly')})`)
</script>

<template>
  <div class="input" :title="t('dashboard.readOnly')">
    <label v-if="widget.type === 'Toggle Switch'" class="switch">
      <input type="checkbox" role="switch" :checked="on" disabled :aria-label="label" />
      <span class="track" aria-hidden="true"><span class="thumb" /></span>
    </label>
    <template v-else-if="widget.type === 'Slider'">
      <input type="range" :min="min" :max="max" :value="value ?? min" disabled :aria-label="label" />
      <output>{{ text }}</output>
    </template>
    <div v-else-if="widget.type === 'PlusMinus'" class="plusminus">
      <button type="button" disabled aria-hidden="true" tabindex="-1">−</button>
      <output :aria-label="label">{{ text }}</output>
      <button type="button" disabled aria-hidden="true" tabindex="-1">+</button>
    </div>
    <input v-else type="text" class="field" :value="text" readonly disabled :aria-label="label" />
  </div>
</template>

<style scoped>
.input {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5em;
  width: 100%;
  height: 100%;
  padding: 0.25em 0.5em;
  font-variant-numeric: tabular-nums;
}

.field {
  width: 100%;
  font: inherit;
  color: inherit;
  padding: 0.2em 0.4em;
  border: 1px solid rgba(127, 127, 127, 0.4);
  border-radius: 4px;
  background: rgba(127, 127, 127, 0.08);
  text-align: right;
}

input[type='range'] {
  flex: 1;
  min-width: 0;
}

.plusminus {
  display: flex;
  align-items: center;
  gap: 0.5em;
}

.plusminus button {
  width: 1.8em;
  height: 1.8em;
  font: inherit;
  border: 1px solid rgba(127, 127, 127, 0.4);
  border-radius: 4px;
  background: rgba(127, 127, 127, 0.08);
  color: inherit;
  opacity: 0.5;
}

.switch {
  position: relative;
  margin: 0;
}

.switch input {
  position: absolute;
  opacity: 0;
  width: 1px;
  height: 1px;
}

.track {
  display: block;
  width: 2.6em;
  height: 1.4em;
  border-radius: 0.7em;
  background: #b9c0cc;
  position: relative;
  opacity: 0.8;
}

.switch input:checked + .track {
  background: #2e7d32;
}

.thumb {
  position: absolute;
  top: 0.15em;
  left: 0.15em;
  width: 1.1em;
  height: 1.1em;
  border-radius: 50%;
  background: #fff;
  transition: left 0.15s;
}

.switch input:checked + .track .thumb {
  left: 1.35em;
}
</style>
