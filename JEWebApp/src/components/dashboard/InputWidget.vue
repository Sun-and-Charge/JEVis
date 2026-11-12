<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import { writeWidgetValue, type WidgetValue } from '../../api/dashboardEdit'
import { formatNumber } from '../../analysis/format'
import { browserTimeZone } from '../../analysis/settings'
import type { WidgetContext } from '../../dashboard/context'
import { num, widgetDecimals, widgetRows } from '../../dashboard/values'
import { writeErrorText } from '../../edit/edit'
import { useAuthStore } from '../../stores/auth'
import InputValueDialog from './InputValueDialog.vue'

/*
 * Value Editor, Toggle Switch, Slider and PlusMinus: the current value. With `canEnter` (E4) they can be used as in
 * the Control Center and write via POST /web/dashboards/{id}/widgets/{uuid}/value (the Value Editor opens the manual
 * entry); without, and while the dashboard is edited, they are read-only.
 */
const props = defineProps<{ widget: WidgetConfig; data: WidgetData | undefined; ctx: WidgetContext }>()
const emit = defineEmits<{ written: [data: WidgetData] }>()
const { t } = useI18n()
const auth = useAuthStore()

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
const step = computed(() => {
  const d = Math.max(0, Math.min(6, Math.round(num(props.widget.decimals, 0))))
  return d ? Math.pow(10, -d) : 1
})

/** usable: write right, a row, not in edit mode */
const usable = computed(() => props.widget.canEnter === true && !props.ctx.editing && !!props.widget.rows?.length && row.value?.state !== 'NO_PERMISSION')
const name = computed(() => props.widget.title || props.widget.type)
const label = computed(() => (usable.value ? `${name.value}: ${text.value}` : `${name.value}: ${text.value} (${t('dashboard.readOnly')})`))

const busy = ref(false)
const error = ref<string | null>(null)
const slider = ref<number>(value.value ?? min.value)
watch(value, (v) => {
  if (!busy.value) slider.value = v ?? min.value
})

async function write(body: WidgetValue): Promise<void> {
  if (!usable.value || busy.value) return
  busy.value = true
  error.value = null
  try {
    const key = props.widget.rows[0]?.key
    const answer = await writeWidgetValue(auth.http, props.ctx.dashboardId, String(props.widget.uuid), { ...body, ...(key ? { rowKey: key } : {}), timeZone: browserTimeZone() }, props.ctx.locale)
    if (answer && typeof answer === 'object') emit('written', answer)
  } catch (e) {
    error.value = writeErrorText(e, t)
    slider.value = value.value ?? min.value
  } finally {
    busy.value = false
  }
}

function toggle() {
  void write({ value: !on.value })
}
function onSlide(e: Event) {
  const n = Number((e.target as HTMLInputElement).value)
  if (Number.isFinite(n)) {
    slider.value = n
    void write({ value: n })
  }
}

/* Value Editor: manual entry dialog */
const entry = ref(false)
function entrySaved(answer: WidgetData) {
  entry.value = false
  emit('written', answer)
}
</script>

<template>
  <div class="input" :class="{ usable, busy }" :title="usable ? widget.tooltip || undefined : t('dashboard.readOnly')" :aria-busy="busy ? 'true' : undefined">
    <template v-if="widget.type === 'Toggle Switch'">
      <button v-if="usable" type="button" role="switch" class="switch-btn" :aria-checked="on" :aria-label="label" :disabled="busy" @click="toggle">
        <span class="track" :class="{ on }" aria-hidden="true"><span class="thumb" /></span>
      </button>
      <label v-else class="switch">
        <input type="checkbox" role="switch" :checked="on" disabled :aria-label="label" />
        <span class="track" aria-hidden="true"><span class="thumb" /></span>
      </label>
    </template>
    <template v-else-if="widget.type === 'Slider'">
      <input v-if="usable" type="range" :min="min" :max="max" :step="step" :value="slider" :disabled="busy" :aria-label="label" @change="onSlide" />
      <input v-else type="range" :min="min" :max="max" :value="value ?? min" disabled :aria-label="label" />
      <output>{{ text }}</output>
    </template>
    <div v-else-if="widget.type === 'PlusMinus'" class="plusminus">
      <template v-if="usable">
        <button type="button" class="pm-minus" :disabled="busy" :aria-label="t('dashboard.input.minus', { name })" @click="write({ value: 'minus' })">−</button>
        <output aria-live="polite">{{ text }}</output>
        <button type="button" class="pm-plus" :disabled="busy" :aria-label="t('dashboard.input.plus', { name })" @click="write({ value: 'plus' })">+</button>
      </template>
      <template v-else>
        <button type="button" disabled aria-hidden="true" tabindex="-1">−</button>
        <output :aria-label="label">{{ text }}</output>
        <button type="button" disabled aria-hidden="true" tabindex="-1">+</button>
      </template>
    </div>
    <button v-else-if="usable" type="button" class="field field-btn" :aria-label="t('dashboard.input.enter', { name, value: text })" @click="entry = true">{{ text }}</button>
    <input v-else type="text" class="field" :value="text" readonly disabled :aria-label="label" />
    <p v-if="error" class="err" role="alert">{{ error }}</p>
    <InputValueDialog
      v-if="entry"
      :dashboard-id="ctx.dashboardId"
      :widget="widget"
      :current="value"
      :unit="row?.unit ?? null"
      @close="entry = false"
      @saved="entrySaved"
    />
  </div>
</template>

<style scoped>
.input {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5em;
  width: 100%;
  height: 100%;
  padding: 0.25em 0.5em;
  font-variant-numeric: tabular-nums;
}

.input.busy {
  opacity: 0.7;
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

.field-btn {
  cursor: pointer;
  background: rgba(255, 255, 255, 0.6);
}

.field-btn:hover,
.field-btn:focus-visible {
  border-color: #1a2a45;
}

input[type='range'] {
  flex: 1;
  min-width: 0;
}

.usable input[type='range'] {
  accent-color: #1a2a45;
  cursor: pointer;
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

.usable .plusminus button {
  opacity: 1;
  cursor: pointer;
  background: #fff;
  color: #1a2a45;
  font-weight: 700;
}

.usable .plusminus button:hover:not(:disabled) {
  background: #ffde00;
}

.switch,
.switch-btn {
  position: relative;
  margin: 0;
}

.switch-btn {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 0.7em;
}

.switch-btn:focus-visible {
  outline: 3px solid #ffde00;
  outline-offset: 2px;
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

.switch-btn .track {
  opacity: 1;
}

.switch input:checked + .track,
.track.on {
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

.switch input:checked + .track .thumb,
.track.on .thumb {
  left: 1.35em;
}

.err {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  margin: 0;
  padding: 0.1em 0.3em;
  font-size: 0.7rem;
  line-height: 1.2;
  color: #fff;
  background: #b3261e;
}
</style>
