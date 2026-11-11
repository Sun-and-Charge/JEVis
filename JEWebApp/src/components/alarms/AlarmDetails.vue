<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { alarmOperator, alarmTime, checkedTitle, formatAlarmNumber, parseTime, valueWithUnit } from '../../alarms/alarms'
import { echartsLocale } from '../../analysis/echarts'
import { formatDateTime } from '../../analysis/format'
import type { Alarm } from '../../api/alarms'
import { HttpError } from '../../api/http'
import { loadRowSamples, type RowSamples } from '../../api/notes'
import { noteChartOption, noteWindow, numericRowSamples } from '../../notes/chart'
import { useAuthStore } from '../../stores/auth'

/*
 * Detail panel of an alarm: all logged fields, and the curve of the alarm's data row from one day before to one day
 * after the alarm with the alarm time marked and the limit (Soll-Wert) as dashed line. Unreadable rows get no chart.
 */

const props = defineProps<{ alarm: Alarm }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

type ChartState = 'noRow' | 'noAccess' | 'loading' | 'ready' | 'notFound' | 'error'

const result = shallowRef<RowSamples | null>(null)
const state = ref<ChartState>('loading')
let seq = 0

const ts = computed(() => alarmTime(props.alarm))
const readable = computed(() => !!props.alarm.row?.readable && ts.value != null)

async function load() {
  const current = ++seq
  result.value = null
  if (!props.alarm.row) {
    state.value = 'noRow'
    return
  }
  if (!readable.value || ts.value == null) {
    state.value = 'noAccess'
    return
  }
  state.value = 'loading'
  const { from, to } = noteWindow(ts.value)
  try {
    const answer = await loadRowSamples(auth.http, props.alarm.row.id, from, to)
    if (current !== seq) return
    result.value = answer
    state.value = 'ready'
  } catch (e) {
    if (current !== seq) return
    state.value = e instanceof HttpError && (e.status === 404 || e.status === 403) ? 'notFound' : 'error'
  }
}

onMounted(load)
watch(() => props.alarm.id, load)

const unit = computed(() => result.value?.unit || props.alarm.unit || null)
const samples = computed(() => numericRowSamples(result.value?.samples))
const hasText = computed(() => (result.value?.samples ?? []).some((s) => typeof s?.[1] === 'string'))
const rowLabel = computed(() => props.alarm.row?.name || '')
const option = computed(() =>
  ts.value != null && samples.value.length
    ? noteChartOption(samples.value, ts.value, rowLabel.value, unit.value, {
        locale: locale.value,
        noteLabel: t('alarms.alarm'),
        limit: props.alarm.shouldBeValue != null ? { value: props.alarm.shouldBeValue, label: t('alarms.detail.limit') } : null,
      })
    : null,
)
const initOptions = computed(() => ({ locale: echartsLocale(locale.value) }))

const fmtTime = (text: string | null | undefined) => {
  const ms = parseTime(text)
  return ms == null ? text || t('modules.none') : formatDateTime(ms, locale.value)
}
const num = (v: number | null) => valueWithUnit(v, props.alarm.unit, locale.value) || t('modules.none')
const checked = computed(() => !!props.alarm.configuration?.checked)
const checkedInfo = computed(() => checkedTitle(props.alarm, (ms) => formatDateTime(ms, locale.value)))
const id = (suffix: string) => `alarm-${props.alarm.id}-${suffix}`
</script>

<template>
  <div class="iso-sections note-details alarm-details">
    <section class="iso-section" :aria-labelledby="id('general')">
      <h3 :id="id('general')">{{ alarm.configuration?.name || t('alarms.alarm') }}</h3>
      <dl class="mod-dl">
        <dt>{{ t('alarms.col.date') }}</dt>
        <dd>{{ fmtTime(alarm.timestamp) }}</dd>
        <dt>{{ t('alarms.col.logged') }}</dt>
        <dd>{{ fmtTime(alarm.logged) }}</dd>
        <dt>{{ t('alarms.col.row') }}</dt>
        <dd :class="{ muted: alarm.row && !alarm.row.readable }">
          {{ alarm.row ? (alarm.row.readable ? alarm.row.name || '#' + alarm.row.id : t('alarms.noAccess')) : t('modules.none') }}
        </dd>
        <template v-if="alarm.row?.readable && alarm.row.path">
          <dt>{{ t('alarms.col.path') }}</dt>
          <dd>{{ alarm.row.path }}</dd>
        </template>
        <template v-if="alarm.attribute">
          <dt>{{ t('alarms.col.attribute') }}</dt>
          <dd>{{ alarm.attribute }}</dd>
        </template>
        <dt>{{ t('alarms.col.site') }}</dt>
        <dd>{{ alarm.site?.name || t('modules.none') }}</dd>
        <dt>{{ t('alarms.col.isValue') }}</dt>
        <dd>{{ num(alarm.isValue) }}</dd>
        <dt>{{ t('alarms.col.operator') }}</dt>
        <dd>{{ alarmOperator(alarm.operator) || t('modules.none') }}</dd>
        <dt>{{ t('alarms.col.shouldBeValue') }}</dt>
        <dd>{{ num(alarm.shouldBeValue) }}</dd>
        <dt>{{ t('alarms.col.tolerance') }}</dt>
        <dd>{{ formatAlarmNumber(alarm.tolerance, locale) || t('modules.none') }}</dd>
        <dt>{{ t('alarms.col.type') }}</dt>
        <dd>{{ alarm.typeName || alarm.type || t('modules.none') }}</dd>
        <dt>{{ t('alarms.col.alarm') }}</dt>
        <dd>{{ alarm.logValueName || t('modules.none') }}</dd>
        <dt>{{ t('alarms.col.status') }}</dt>
        <dd>
          <span class="mod-badge alarm-state" :class="checked ? 'is-checked' : 'is-open'">{{ t(checked ? 'alarms.state.checked' : 'alarms.state.open') }}</span>
        </dd>
        <template v-if="checkedInfo">
          <dt>{{ t('alarms.col.checkedBy') }}</dt>
          <dd>{{ checkedInfo }}</dd>
        </template>
      </dl>
    </section>

    <section class="iso-section note-chart-section" :aria-labelledby="id('chart')">
      <h3 :id="id('chart')">{{ t('alarms.detail.chart') }}<span v-if="unit" class="muted"> [{{ unit }}]</span></h3>
      <p v-if="state === 'noRow'" class="muted">{{ t('alarms.detail.noRow') }}</p>
      <p v-else-if="state === 'noAccess'" class="muted">{{ t('alarms.detail.noAccess') }}</p>
      <p v-else-if="state === 'loading'" class="muted" role="status">{{ t('alarms.detail.loading') }}</p>
      <p v-else-if="state === 'notFound'" class="muted">{{ t('alarms.detail.notFound') }}</p>
      <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
        {{ t('alarms.detail.error') }}
        <button type="button" class="btn-link retry" @click="load">{{ t('modules.retry') }}</button>
      </div>
      <template v-else>
        <p v-if="!option" class="muted">{{ hasText ? t('alarms.detail.noNumbers') : t('alarms.detail.noData') }}</p>
        <div v-else class="note-chart" role="img" :aria-label="t('alarms.detail.chartLabel', { name: rowLabel })">
          <VChart :option="option" :init-options="initOptions" :update-options="{ notMerge: true }" autoresize />
        </div>
        <p v-if="result?.truncated" class="muted note-small">{{ t('alarms.detail.truncated') }}</p>
      </template>
    </section>
  </div>
</template>

<style scoped>
.retry {
  margin-left: 0.5rem;
}
</style>
