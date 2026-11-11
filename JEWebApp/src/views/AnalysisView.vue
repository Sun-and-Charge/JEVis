<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { loadAnalysis, loadAnalysisData, type AnalysisConfig, type AnalysisData } from '../api/analysis'
import { HttpError } from '../api/http'
import '../analysis/echarts'
import { buildChartViews, type ChartView } from '../analysis/model'
import type { OptionContext } from '../analysis/options'
import { browserTimeZone, dataRequest, settingsFromQuery, settingsToQuery, shiftRange, type AnalysisSettings } from '../analysis/settings'
import AnalysisChart from '../components/analysis/AnalysisChart.vue'
import AnalysisToolbar from '../components/analysis/AnalysisToolbar.vue'
import { i18nTagName } from '../notes/notes'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{ id: number }>()
const { t, te, locale } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const config = shallowRef<AnalysisConfig | null>(null)
const data = shallowRef<AnalysisData | null>(null)
const configState = ref<'loading' | 'ready' | 'error' | 'notFound'>('loading')
const dataState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
let configSeq = 0
let dataSeq = 0

const title = computed(() => (config.value ? config.value.names?.[locale.value] || config.value.name : ''))
const settings = computed<AnalysisSettings>(() => settingsFromQuery(route.query, config.value?.defaults))
/** compares settings without object identity (the query object changes on every navigation) */
const settingsKey = computed(() => JSON.stringify(settingsToQuery(settings.value)))

async function fetchConfig() {
  const seq = ++configSeq
  configState.value = 'loading'
  config.value = null
  data.value = null
  dataState.value = 'idle'
  try {
    const result = await loadAnalysis(auth.http, props.id)
    if (seq !== configSeq) return
    config.value = result
    configState.value = 'ready'
  } catch (e) {
    if (seq !== configSeq) return
    configState.value = e instanceof HttpError && (e.status === 404 || e.status === 403) ? 'notFound' : 'error'
  }
}

async function fetchData() {
  if (!config.value) return
  const seq = ++dataSeq
  dataState.value = 'loading'
  try {
    const result = await loadAnalysisData(auth.http, props.id, dataRequest(settings.value, browserTimeZone()))
    if (seq !== dataSeq) return
    data.value = result
    dataState.value = 'ready'
  } catch {
    if (seq !== dataSeq) return
    dataState.value = 'error'
  }
}

watch(() => props.id, fetchConfig, { immediate: true })
watch([config, settingsKey], fetchData)

function applySettings(next: AnalysisSettings) {
  router.replace({ query: settingsToQuery(next) })
}

const range = computed(() => {
  if (!data.value) return null
  const from = Date.parse(data.value.from)
  const to = Date.parse(data.value.to)
  return Number.isNaN(from) || Number.isNaN(to) ? null : { from: new Date(from), to: new Date(to) }
})

function shift(direction: -1 | 1) {
  if (!range.value) return
  const next = shiftRange(settings.value.timeFrame, range.value.from, range.value.to, direction)
  applySettings({ ...settings.value, timeFrame: 'CUSTOM', from: next.from, to: next.to })
}

const ctx = computed<OptionContext>(() => ({
  locale: locale.value,
  labels: {
    noData: t('analysis.state.noData'),
    noPermission: t('analysis.state.noPermission'),
    error: t('analysis.state.error'),
    count: t('analysis.count'),
    note: t('notes.note'),
  },
  tagName: i18nTagName(t, te),
}))

const charts = computed<ChartView[]>(() => (config.value ? buildChartViews(config.value, data.value, t('analysis.state.noAccess')) : []))

/** consecutive pies / tables side by side (horizontalPies / horizontalTables of the analysis) */
const blocks = computed(() => {
  const result: { key: string; columns: number; charts: ChartView[] }[] = []
  const pies = Math.max(1, config.value?.horizontalPies ?? 1)
  const tables = Math.max(1, config.value?.horizontalTables ?? 1)
  for (const chart of charts.value) {
    const columns = chart.kind === 'pie' ? pies : chart.kind === 'table' ? tables : 1
    const last = result[result.length - 1]
    if (columns > 1 && last && last.columns === columns && last.charts[0].kind === chart.kind && last.charts.length < columns) {
      last.charts.push(chart)
    } else {
      result.push({ key: 'c' + chart.id, columns, charts: [chart] })
    }
  }
  return result.map((b) => ({ ...b, columns: Math.min(b.columns, b.charts.length) }))
})

const perScreen = computed(() => Math.min(6, Math.max(1, Math.round(config.value?.chartsPerScreen || 1))))

/** fixed height of a chart in the configuration (autoSize off) */
function chartStyle(chart: ChartView) {
  return config.value && !config.value.autoSize && chart.height > 0 ? { '--chart-h': `${chart.height}px` } : undefined
}

const from = computed(() => range.value?.from.getTime() ?? null)
const to = computed(() => range.value?.to.getTime() ?? null)
</script>

<template>
  <section class="analysis" :style="{ '--per-screen': perScreen }" aria-labelledby="an-title">
    <p v-if="configState === 'loading'" class="muted" role="status">{{ t('analysis.loading') }}</p>
    <div v-else-if="configState !== 'ready'" class="alert alert-error" role="alert">
      {{ configState === 'notFound' ? t('analysis.notFound') : t('analysis.loadError') }}
      <button v-if="configState === 'error'" type="button" class="btn-link retry" @click="fetchConfig">{{ t('analysis.retry') }}</button>
    </div>

    <template v-else-if="config">
      <div class="top">
        <h1 id="an-title">{{ title }}</h1>
        <AnalysisToolbar :settings="settings" :range="range" :busy="dataState === 'loading'" @change="applySettings" @shift="shift" />
      </div>

      <div class="status" role="status">
        <span v-if="dataState === 'loading'" class="loading"><span class="bar" aria-hidden="true" />{{ t('analysis.updating') }}</span>
      </div>
      <div v-if="dataState === 'error'" class="alert alert-error" role="alert">
        {{ t('analysis.dataError') }}
        <button type="button" class="btn-link retry" @click="fetchData">{{ t('analysis.retry') }}</button>
      </div>

      <p v-if="!charts.length" class="muted">{{ t('analysis.noCharts') }}</p>
      <div class="charts" :class="{ stale: dataState === 'loading' }">
        <div v-for="block in blocks" :key="block.key" class="block" :style="{ '--columns': block.columns }">
          <AnalysisChart
            v-for="chart in block.charts"
            :key="chart.id"
            :style="chartStyle(chart)"
            :chart="chart"
            :ctx="ctx"
            :from="from"
            :to="to"
            :export-name="title"
          />
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.analysis {
  /* height of one chart: the viewport below header and toolbar, divided by the charts per screen */
  --chart-h: max(240px, calc((100dvh - var(--header-h) - 12rem) / var(--per-screen) - 5.5rem));
  /* keeps visually hidden texts inside the scrolling area */
  position: relative;
  min-width: 0;
}

.top {
  position: sticky;
  /* the sticky offset counts from inside the padding of <main> */
  top: -1.5rem;
  z-index: 5;
  margin: -1.5rem -1.5rem 0;
  padding: 1.25rem 1.5rem 0.75rem;
  background: var(--bg);
  border-bottom: 1px solid var(--line);
}

h1 {
  margin: 0 0 0.6rem;
  overflow-wrap: anywhere;
}

.status {
  min-height: 1.6rem;
  padding: 0.3rem 0;
  font-size: 0.8rem;
  color: var(--muted);
}

.loading {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.bar {
  width: 2.5rem;
  height: 3px;
  background: linear-gradient(90deg, var(--sun) 0 40%, var(--line) 40%);
  background-size: 200% 100%;
  animation: slide 1s linear infinite;
}

@keyframes slide {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: -100% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bar {
    animation: none;
  }
}

.retry {
  margin-left: 0.5rem;
  color: inherit;
}

.charts {
  display: grid;
  gap: 1rem;
  transition: opacity 0.2s;
}

.charts.stale {
  opacity: 0.6;
}

.block {
  display: grid;
  grid-template-columns: repeat(var(--columns, 1), minmax(0, 1fr));
  gap: 1rem;
  min-width: 0;
}

@media (max-width: 800px) {
  .top {
    top: -1rem;
    margin: -1rem -1rem 0;
    padding: 1rem 1rem 0.75rem;
  }
}

@media (max-width: 640px) {
  .analysis {
    --chart-h: 320px;
  }

  .top {
    position: static;
  }

  .block {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
