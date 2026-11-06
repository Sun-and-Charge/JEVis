<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { backgroundPath, loadDashboard, loadDashboardData, type DashboardConfig, type DashboardData } from '../api/dashboard'
import { HttpError } from '../api/http'
import type { OptionContext } from '../analysis/options'
import { browserTimeZone } from '../analysis/settings'
import { useAuthImage } from '../dashboard/authImage'
import type { WidgetContext } from '../dashboard/context'
import '../dashboard/echarts'
import { contentSize, nextZoomStep, normaliseZoom, scaleFactor, sortByLayer } from '../dashboard/layout'
import { backgroundStyle } from '../dashboard/style'
import {
  dashboardDataRequest,
  selectTimeFrame,
  stepTimeBar,
  timeBarFromQuery,
  timeBarToQuery,
  timeFrameOptions,
  type TimeBarState,
} from '../dashboard/timeframe'
import { parseTime, widgetValues } from '../dashboard/values'
import DashboardTimeBar from '../components/dashboard/DashboardTimeBar.vue'
import DashboardWidget from '../components/dashboard/DashboardWidget.vue'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{ id: number }>()
const { t, locale } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

/** shortest auto refresh (seconds) */
const MIN_UPDATE_RATE = 60
/** below this width the dashboard always fits the width (phones); pinch zoom stays possible */
const NARROW = 800

const config = shallowRef<DashboardConfig | null>(null)
const data = shallowRef<DashboardData | null>(null)
const configState = ref<'loading' | 'ready' | 'error' | 'notFound'>('loading')
const dataState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
let configSeq = 0
let dataSeq = 0
let lastFetch = 0
/** time of the last answer (incomplete periods, arrows) */
const now = ref(Date.now())

const title = computed(() => (config.value ? config.value.names?.[locale.value] || config.value.name : ''))
const options = computed(() => (config.value ? timeFrameOptions(config.value) : []))
const state = computed<TimeBarState>(() => (config.value ? timeBarFromQuery(route.query, config.value) : { timeFrame: 'P1D', date: null, from: null, to: null }))
const stateKey = computed(() => JSON.stringify(timeBarToQuery(state.value)))

async function fetchConfig() {
  const seq = ++configSeq
  configState.value = 'loading'
  config.value = null
  data.value = null
  dataState.value = 'idle'
  try {
    const result = await loadDashboard(auth.http, props.id)
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
  lastFetch = Date.now()
  try {
    const result = await loadDashboardData(auth.http, props.id, dashboardDataRequest(state.value, browserTimeZone()))
    if (seq !== dataSeq) return
    data.value = result
    now.value = Date.now()
    dataState.value = 'ready'
  } catch {
    if (seq !== dataSeq) return
    dataState.value = 'error'
  }
}

watch(() => props.id, fetchConfig, { immediate: true })
watch([config, stateKey], fetchData)

/* ---- auto refresh: every updateRate seconds (at least 60), not while the tab is hidden ---- */
const refreshMs = computed(() => Math.max(MIN_UPDATE_RATE, Number(config.value?.updateRate) || 0) * 1000)
let timer: ReturnType<typeof setInterval> | undefined
function refreshIfDue() {
  if (typeof document !== 'undefined' && document.hidden) return
  if (dataState.value === 'loading' || !config.value) return
  if (Date.now() - lastFetch >= refreshMs.value - 1000) fetchData()
}
function onVisibility() {
  if (!document.hidden) refreshIfDue()
}
watch(
  refreshMs,
  (ms) => {
    if (timer) clearInterval(timer)
    timer = setInterval(refreshIfDue, ms)
  },
  { immediate: true },
)

/* ---- time frame bar ---- */
function apply(next: TimeBarState) {
  router.replace({ query: timeBarToQuery(next) })
}
const range = computed(() => {
  const from = parseTime(data.value?.from)
  const to = parseTime(data.value?.to)
  return from != null && to != null ? { from, to } : null
})
const previousState = computed(() => stepTimeBar(state.value, data.value, -1, now.value))
const nextState = computed(() => stepTimeBar(state.value, data.value, 1, now.value))
function step(direction: -1 | 1) {
  const next = direction < 0 ? previousState.value : nextState.value
  if (next) apply(next)
}
function select(timeFrame: string) {
  apply(selectTimeFrame(timeFrame, range.value))
}
function custom(from: Date, to: Date) {
  apply({ timeFrame: 'CUSTOM', date: null, from, to })
}

/* ---- layout ---- */
const stage = ref<HTMLElement | null>(null)
const viewport = ref({ width: 0, height: 0 })
const narrow = ref(false)
const userZoom = ref(1)
let observer: ResizeObserver | null = null

function measure() {
  const el = stage.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const width = el.clientWidth || rect.width
  // the visible height below the top of the stage (the page scrolls inside <main>)
  const height = Math.max(120, window.innerHeight - Math.max(0, rect.top) - 16)
  viewport.value = { width, height }
  narrow.value = window.innerWidth <= NARROW
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('resize', measure)
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(measure)
    if (stage.value) observer.observe(stage.value)
  }
  measure()
})
watch(stage, (el, old) => {
  if (old) observer?.unobserve(old)
  if (el) {
    observer?.observe(el)
    measure()
  }
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('resize', measure)
  observer?.disconnect()
})

const widgets = computed(() => sortByLayer(config.value?.widgets ?? []))
const size = computed(() => (config.value ? contentSize(config.value, config.value.widgets ?? []) : { width: 1, height: 1 }))
const baseScale = computed(() => {
  if (!config.value || !viewport.value.width) return 1
  const zoom = narrow.value ? 'FIT_WIDTH' : normaliseZoom(config.value.zoom)
  return scaleFactor(zoom, size.value, viewport.value)
})
const scale = computed(() => baseScale.value * userZoom.value)
function zoom(direction: -1 | 1 | 0) {
  userZoom.value = direction === 0 ? 1 : nextZoomStep(userZoom.value, direction)
}

const bgPath = computed(() => (config.value?.background?.image ? backgroundPath(props.id) : null))
const bgImage = useAuthImage(auth.http, bgPath)
const canvasStyle = computed(() => ({
  width: `${Math.round(size.value.width * scale.value)}px`,
  height: `${Math.round(size.value.height * scale.value)}px`,
  ...backgroundStyle(config.value?.background, bgImage.url.value, bgImage.size.value, scale.value),
}))

const chartCtx = computed<OptionContext>(() => ({
  locale: locale.value,
  labels: { noData: t('analysis.state.noData'), noPermission: t('analysis.state.noPermission'), error: t('analysis.state.error'), count: t('analysis.count') },
}))
const values = computed(() => widgetValues(config.value?.widgets ?? [], data.value))
const ctx = computed<WidgetContext>(() => ({
  dashboardId: props.id,
  locale: locale.value,
  scale: scale.value,
  values: values.value,
  data: data.value,
  state: state.value,
  shown: range.value,
  chart: chartCtx.value,
  now: now.value,
}))
</script>

<template>
  <section class="dashboard" aria-labelledby="db-title">
    <p v-if="configState === 'loading'" class="muted" role="status">{{ t('dashboard.loading') }}</p>
    <div v-else-if="configState !== 'ready'" class="alert alert-error" role="alert">
      {{ configState === 'notFound' ? t('dashboard.notFound') : t('dashboard.loadError') }}
      <button v-if="configState === 'error'" type="button" class="btn-link retry" @click="fetchConfig">{{ t('analysis.retry') }}</button>
    </div>

    <template v-else-if="config">
      <div class="top">
        <h1 id="db-title">{{ title }}</h1>
        <DashboardTimeBar
          :state="state"
          :options="options"
          :range="range"
          :can-previous="!!previousState"
          :can-next="!!nextState"
          :busy="dataState === 'loading'"
          @select="select"
          @custom="custom"
          @step="step"
        >
          <div class="zoom" role="group" :aria-label="t('dashboard.zoom.label')">
            <button type="button" :aria-label="t('dashboard.zoom.out')" :title="t('dashboard.zoom.out')" @click="zoom(-1)">−</button>
            <button type="button" class="pct" :aria-label="t('dashboard.zoom.reset')" :title="t('dashboard.zoom.reset')" @click="zoom(0)">{{ Math.round(userZoom * 100) }} %</button>
            <button type="button" :aria-label="t('dashboard.zoom.in')" :title="t('dashboard.zoom.in')" @click="zoom(1)">+</button>
          </div>
        </DashboardTimeBar>
      </div>

      <div class="status" role="status">
        <span v-if="dataState === 'loading'" class="loading"><span class="bar" aria-hidden="true" />{{ t('analysis.updating') }}</span>
      </div>
      <div v-if="dataState === 'error'" class="alert alert-error" role="alert">
        {{ t('analysis.dataError') }}
        <button type="button" class="btn-link retry" @click="fetchData">{{ t('analysis.retry') }}</button>
      </div>
      <p v-if="!widgets.length" class="muted">{{ t('dashboard.noWidgets') }}</p>

      <div ref="stage" class="stage">
        <div class="canvas" :class="{ stale: dataState === 'loading' && !data }" :style="canvasStyle">
          <DashboardWidget v-for="(w, i) in widgets" :key="w.uuid + ':' + i" :widget="w" :data="data?.widgets?.[String(w.uuid)]" :ctx="ctx" :z="i + 1" />
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.dashboard {
  position: relative;
  min-width: 0;
}

.top {
  position: sticky;
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

.zoom {
  display: flex;
  align-items: center;
  height: 2.25rem;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  overflow: hidden;
}

.zoom button {
  height: 100%;
  min-width: 2.25rem;
  padding: 0 0.5rem;
  font: inherit;
  font-weight: 600;
  color: var(--blueberry);
  background: none;
  border: none;
  cursor: pointer;
}

.zoom .pct {
  min-width: 4rem;
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
  border-left: 1px solid var(--line);
  border-right: 1px solid var(--line);
}

.zoom button:hover {
  background: var(--bg);
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

/* the dashboard keeps its absolute layout; wider than the screen it scrolls */
.stage {
  width: 100%;
  overflow: auto;
}

.canvas {
  position: relative;
  background: #fff;
  font-family: 'Montserrat', 'Segoe UI', Arial, sans-serif;
  transition: opacity 0.2s;
}

.canvas.stale {
  opacity: 0.6;
}

@media (max-width: 800px) {
  .top {
    top: -1rem;
    margin: -1rem -1rem 0;
    padding: 1rem 1rem 0.75rem;
  }
}

@media (max-width: 640px) {
  .top {
    position: static;
  }
}
</style>
