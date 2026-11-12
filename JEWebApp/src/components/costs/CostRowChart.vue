<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { echartsLocale } from '../../analysis/echarts'
import { HttpError } from '../../api/http'
import { loadRowSamples, type RowSamples } from '../../api/notes'
import { costRowChartOption } from '../../costs/chart'
import { numericRowSamples } from '../../notes/chart'
import { useAuthStore } from '../../stores/auth'

/* Inline chart of the input row of a result cell (raw samples of the result's period). */

const props = defineProps<{ id: string; objectId: number; name: string; from: number; to: number }>()
const emit = defineEmits<{ close: [] }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

type ChartState = 'loading' | 'ready' | 'notFound' | 'error'

const answer = shallowRef<RowSamples | null>(null)
const state = ref<ChartState>('loading')
let seq = 0

async function load() {
  const current = ++seq
  state.value = 'loading'
  try {
    const result = await loadRowSamples(auth.http, props.objectId, props.from, props.to)
    if (current !== seq) return
    answer.value = result
    state.value = 'ready'
  } catch (e) {
    if (current !== seq) return
    state.value = e instanceof HttpError && (e.status === 404 || e.status === 403) ? 'notFound' : 'error'
  }
}

onMounted(load)
onBeforeUnmount(() => seq++)

const samples = computed(() => numericRowSamples(answer.value?.samples))
const option = computed(() =>
  samples.value.length ? costRowChartOption(samples.value, props.from, props.to, props.name, answer.value?.unit ?? null, locale.value) : null,
)
const initOptions = computed(() => ({ locale: echartsLocale(locale.value) }))
</script>

<template>
  <section :id="id" class="cost-chart" :aria-labelledby="id + '-title'">
    <div class="cost-chart-head">
      <h4 :id="id + '-title'">{{ t('costs.chart.title', { name }) }}<span v-if="answer?.unit" class="muted"> [{{ answer.unit }}]</span></h4>
      <button type="button" class="notes-btn cost-chart-close" @click="emit('close')">{{ t('costs.chart.close') }}</button>
    </div>
    <p v-if="state === 'loading'" class="muted" role="status">{{ t('costs.chart.loading') }}</p>
    <p v-else-if="state === 'notFound'" class="muted">{{ t('costs.chart.notFound') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('costs.chart.error') }}
      <button type="button" class="btn-link retry" @click="load">{{ t('modules.retry') }}</button>
    </div>
    <template v-else>
      <p v-if="!option" class="muted">{{ t('costs.chart.noData') }}</p>
      <div v-else class="note-chart" role="img" :aria-label="t('costs.chart.label', { name })">
        <VChart :option="option" :init-options="initOptions" :update-options="{ notMerge: true }" autoresize />
      </div>
      <p v-if="answer?.truncated" class="muted note-small">{{ t('costs.chart.truncated') }}</p>
    </template>
  </section>
</template>

<style scoped>
.retry {
  margin-left: 0.5rem;
}
</style>
