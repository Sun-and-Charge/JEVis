<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { backgroundPath, loadDashboard, loadDashboardData, type DashboardConfig, type DashboardData, type WidgetData } from '../api/dashboard'
import { HttpError } from '../api/http'
import type { OptionContext } from '../analysis/options'
import { browserTimeZone } from '../analysis/settings'
import { useAuthImage } from '../dashboard/authImage'
import type { WidgetContext } from '../dashboard/context'
import '../dashboard/echarts'
import { useDashboardEditing } from '../dashboard/edit/useDashboardEditing'
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
import AnalysisNameDialog from '../components/analysis/edit/AnalysisNameDialog.vue'
import DashboardTimeBar from '../components/dashboard/DashboardTimeBar.vue'
import DashboardWidget from '../components/dashboard/DashboardWidget.vue'
import DashboardEditCanvas from '../components/dashboard/edit/DashboardEditCanvas.vue'
import DashboardEditPanel from '../components/dashboard/edit/DashboardEditPanel.vue'
import DashboardEditToolbar from '../components/dashboard/edit/DashboardEditToolbar.vue'
import ConfirmDialog from '../components/edit/ConfirmDialog.vue'
import EditDialog from '../components/edit/EditDialog.vue'
import { useAuthStore } from '../stores/auth'
import { useNavigationStore } from '../stores/navigation'
import '../styles/edit.css'
import '../styles/dashboardEdit.css'

const props = defineProps<{ id: number }>()
const { t, locale } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()
const route = useRoute()
const router = useRouter()

/** shortest auto refresh (seconds) */
const MIN_UPDATE_RATE = 60
/** below this width the dashboard always fits the width (phones); pinch zoom stays possible */
const NARROW = 800
/** E4: editing is offered from tablet width on */
const EDIT_MIN_WIDTH = 900

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
const state = computed<TimeBarState>(() => (config.value ? timeBarFromQuery(route.query, config.value) : { timeFrame: 'P1D', date: null, from: null, to: null }))
const stateKey = computed(() => JSON.stringify(timeBarToQuery(state.value)))
/** time frame bar of the last data request (leaving edit mode reloads when it changed meanwhile) */
let fetchedKey = ''

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
  fetchedKey = stateKey.value
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
watch([config, stateKey], () => {
  // while editing, the preview of the source is loaded instead (the saved data stays for "Verwerfen")
  if (!ed.editing.value) void fetchData()
})

/* ---- E4: edit mode ---- */

const ed = useDashboardEditing({
  id: () => props.id,
  config,
  data,
  title,
  request: () => dashboardDataRequest(state.value, browserTimeZone()),
  settingsKey: stateKey,
  replaceConfig: (next) => {
    configSeq++
    config.value = next
    configState.value = 'ready'
  },
})
const { editing, canEdit, saveAs, overwrite, deleting, leaveAsk, conflictOpen } = ed
const editBusy = ed.busy

onMounted(() => void nav.loadDashboardDirectories())

watch(editing, (isEditing, was) => {
  if (was && !isEditing && fetchedKey !== stateKey.value) void fetchData()
})

/** wide enough to edit (tablet width and more) */
const wideEnough = ref(typeof window === 'undefined' || window.innerWidth >= EDIT_MIN_WIDTH)

/*
 * "Neues Dashboard" opens the new dashboard with ?edit=1. The route query changes before the id prop (the prop
 * follows with the next render), so edit mode starts only once the loaded dashboard is the one of the route;
 * otherwise the dashboard that was open before would be edited (and saved) instead of the new one.
 */
watch(
  () => [configState.value, config.value?.id, props.id, route.query.edit, nav.dashboardDirectories] as const,
  ([cs, loadedId, id, edit, directories]) => {
    if (cs !== 'ready' || loadedId !== id || edit !== '1' || directories === null || editing.value || ed.starting.value) return
    const query = { ...route.query }
    delete query.edit
    void router.replace({ query })
    if (wideEnough.value) void ed.start()
  },
  { immediate: true },
)

/** the dashboard drawn: while editing the source over the last preview */
const shown = computed(() => (editing.value ? ed.shownConfig.value : config.value))
const shownData = computed(() => (editing.value ? ed.shownData.value : data.value))
const options = computed(() => (shown.value ? timeFrameOptions(shown.value) : []))
/** data or preview request running */
const busy = computed(() => (editing.value ? ed.previewState.value === 'loading' : dataState.value === 'loading'))

/* input widgets: the answer of a write replaces the data of that widget */
function onWritten(uuid: string, answer: WidgetData) {
  const current = data.value
  if (!current) return
  data.value = { ...current, widgets: { ...current.widgets, [uuid]: answer } }
}

/* ---- auto refresh: every updateRate seconds (at least 60), not while the tab is hidden or the dashboard edited ---- */
const refreshMs = computed(() => Math.max(MIN_UPDATE_RATE, Number(config.value?.updateRate) || 0) * 1000)
let timer: ReturnType<typeof setInterval> | undefined
function refreshIfDue() {
  if (typeof document !== 'undefined' && document.hidden) return
  if (dataState.value === 'loading' || !config.value || editing.value) return
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
  const from = parseTime(shownData.value?.from)
  const to = parseTime(shownData.value?.to)
  return from != null && to != null ? { from, to } : null
})
const previousState = computed(() => stepTimeBar(state.value, shownData.value, -1, now.value))
const nextState = computed(() => stepTimeBar(state.value, shownData.value, 1, now.value))
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
  narrow.value = window.innerWidth <= NARROW
  wideEnough.value = window.innerWidth >= EDIT_MIN_WIDTH
  if (!el) return
  const rect = el.getBoundingClientRect()
  const width = el.clientWidth || rect.width
  // the visible height below the top of the stage (the page scrolls inside <main>)
  const height = Math.max(120, window.innerHeight - Math.max(0, rect.top) - 16)
  viewport.value = { width, height }
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

const widgets = computed(() => sortByLayer(shown.value?.widgets ?? []))
const size = computed(() => (shown.value ? contentSize(shown.value, shown.value.widgets ?? []) : { width: 1, height: 1 }))
const baseScale = computed(() => {
  if (!shown.value || !viewport.value.width) return 1
  const zoom = narrow.value ? 'FIT_WIDTH' : normaliseZoom(shown.value.zoom)
  return scaleFactor(zoom, size.value, viewport.value)
})
const scale = computed(() => baseScale.value * userZoom.value)
function zoom(direction: -1 | 1 | 0) {
  userZoom.value = direction === 0 ? 1 : nextZoomStep(userZoom.value, direction)
}

/* a new background (E4 upload) gets a new URL, so the browser cache is not used */
const bgPath = computed(() => (shown.value?.background?.image ? backgroundPath(props.id) + (ed.backgroundVersion.value ? `?v=${ed.backgroundVersion.value}` : '') : null))
const bgImage = useAuthImage(auth.http, bgPath)
const canvasStyle = computed(() => ({
  width: `${Math.round(size.value.width * scale.value)}px`,
  height: `${Math.round(size.value.height * scale.value)}px`,
  ...backgroundStyle(shown.value?.background, bgImage.url.value, bgImage.size.value, scale.value),
}))

const chartCtx = computed<OptionContext>(() => ({
  locale: locale.value,
  labels: { noData: t('analysis.state.noData'), noPermission: t('analysis.state.noPermission'), error: t('analysis.state.error'), count: t('analysis.count') },
}))
const values = computed(() => widgetValues(shown.value?.widgets ?? [], shownData.value))
const ctx = computed<WidgetContext>(() => ({
  dashboardId: props.id,
  locale: locale.value,
  scale: scale.value,
  values: values.value,
  data: shownData.value,
  state: state.value,
  shown: range.value,
  chart: chartCtx.value,
  now: now.value,
  editing: editing.value,
}))

/* ---- E4: "Widget hinzufügen" places the widget in the visible part of the canvas ---- */
const editCanvas = ref<InstanceType<typeof DashboardEditCanvas> | null>(null)
function addWidget(type: string) {
  const el = stage.value
  const rect = el?.getBoundingClientRect()
  const left = ((el?.scrollLeft ?? 0) + Math.max(0, -(rect?.left ?? 0))) / scale.value
  const top = ((el?.scrollTop ?? 0) + Math.max(0, -(rect?.top ?? 0))) / scale.value
  ed.addWidget(type, { left, top })
  editCanvas.value?.focus()
}
</script>

<template>
  <section class="dashboard" :class="{ 'is-editing': editing }" aria-labelledby="db-title">
    <p v-if="configState === 'loading'" class="muted" role="status">{{ t('dashboard.loading') }}</p>
    <div v-else-if="configState !== 'ready'" class="alert alert-error" role="alert">
      {{ configState === 'notFound' ? t('dashboard.notFound') : t('dashboard.loadError') }}
      <button v-if="configState === 'error'" type="button" class="btn-link retry" @click="fetchConfig">{{ t('analysis.retry') }}</button>
    </div>

    <template v-else-if="config">
      <div class="top">
        <div class="title-line">
          <h1 id="db-title">{{ title }}</h1>
          <button
            v-if="canEdit && !editing && wideEnough"
            type="button"
            class="edit-btn db-edit"
            :disabled="ed.starting.value"
            :aria-label="t('dashboardEdit.editLabel', { name: title })"
            @click="ed.start()"
          >
            <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M3 13l1-3.5L10.5 3 13 5.5 6.5 12zM9.5 4l2.5 2.5" /></svg>
            {{ ed.starting.value ? t('dashboardEdit.starting') : t('dashboardEdit.edit') }}
          </button>
          <p v-else-if="canEdit && !editing" class="muted db-edit-hint">{{ t('dashboardEdit.narrowHint') }}</p>
        </div>
        <DashboardTimeBar
          :state="state"
          :options="options"
          :range="range"
          :can-previous="!!previousState"
          :can-next="!!nextState"
          :busy="busy"
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

      <div class="layout" :class="{ 'is-editing': editing }">
        <div class="main-col">
          <div class="status" role="status">
            <span v-if="busy" class="loading"><span class="bar" aria-hidden="true" />{{ t('analysis.updating') }}</span>
          </div>
          <div v-if="!editing && dataState === 'error'" class="alert alert-error" role="alert">
            {{ t('analysis.dataError') }}
            <button type="button" class="btn-link retry" @click="fetchData">{{ t('analysis.retry') }}</button>
          </div>
          <p v-if="ed.result.value" class="alert edit-result db-edit-result" :class="ed.result.value.ok ? 'alert-ok' : 'alert-error'" role="status">{{ ed.result.value.text }}</p>
          <DashboardEditToolbar v-if="editing" :ed="ed" @add="addWidget" />
          <p v-if="!widgets.length" class="muted">{{ t(editing ? 'dashboardEdit.empty' : 'dashboard.noWidgets') }}</p>

          <div ref="stage" class="stage" :class="{ 'is-editing': editing }">
            <div class="canvas" :class="{ stale: dataState === 'loading' && !data && !editing }" :style="canvasStyle">
              <DashboardWidget
                v-for="(w, i) in widgets"
                :key="w.uuid + ':' + i"
                :widget="w"
                :data="shownData?.widgets?.[String(w.uuid)]"
                :ctx="ctx"
                :z="i + 1"
                @written="onWritten(String(w.uuid), $event)"
              />
              <DashboardEditCanvas v-if="editing && ed.source.value" ref="editCanvas" :ed="ed" :scale="scale" />
            </div>
          </div>
        </div>
        <DashboardEditPanel v-if="editing && ed.source.value" :ed="ed" :time-frames="shown?.timeFrames ?? []" :shown="shown" :dashboard-id="id" />
      </div>
    </template>

    <!-- E4 dialogs -->
    <ConfirmDialog
      v-if="leaveAsk"
      :title="t('dashboardEdit.leave.title')"
      :message="t('dashboardEdit.leave.message')"
      :confirm-label="t('dashboardEdit.leave.confirm')"
      danger
      @confirm="leaveAsk(true)"
      @cancel="leaveAsk(false)"
    />
    <AnalysisNameDialog
      v-if="saveAs && !overwrite"
      :title="t('dashboardEdit.nameDialog.saveAsTitle')"
      :confirm-label="t('dashboardEdit.save')"
      :directories="nav.creatableDashboardDirectories"
      :name="saveAs.name"
      :directory-id="saveAs.directoryId"
      :busy="editBusy"
      :error="ed.saveAsError.value"
      :texts="{ noDirectory: 'dashboardEdit.nameDialog.noDirectory', taken: 'dashboardEdit.nameDialog.taken', openExisting: 'dashboardEdit.nameDialog.openExisting' }"
      @close="saveAs = null"
      @submit="ed.submitSaveAs"
    />
    <ConfirmDialog
      v-if="overwrite"
      :title="t('dashboardEdit.overwrite.title')"
      :message="t('dashboardEdit.overwrite.message', { name: overwrite.name })"
      :confirm-label="t('dashboardEdit.overwrite.confirm')"
      :busy="editBusy"
      danger
      @confirm="ed.confirmOverwrite()"
      @cancel="ed.cancelOverwrite()"
    />
    <ConfirmDialog
      v-if="deleting"
      :title="t('dashboardEdit.deleteDialog.title')"
      :message="t('dashboardEdit.deleteDialog.message', { name: title })"
      :confirm-label="t('dashboardEdit.deleteDialog.confirm')"
      :busy="editBusy"
      :error="ed.deleteError.value"
      danger
      @confirm="ed.confirmDelete()"
      @cancel="ed.cancelDelete()"
    />
    <EditDialog v-if="conflictOpen" :title="t('dashboardEdit.conflictDialog.title')" :busy="editBusy" @close="conflictOpen = false">
      <p class="edit-message">{{ t('dashboardEdit.conflictDialog.message') }}</p>
      <template #footer>
        <button type="button" class="edit-btn" :disabled="editBusy" @click="conflictOpen = false">{{ t('edit.cancel') }}</button>
        <button v-if="ed.canSaveAs.value" type="button" class="edit-btn db-conflict-save-as" :disabled="editBusy" @click="ed.conflictSaveAs()">{{ t('dashboardEdit.saveAs') }}</button>
        <button type="button" class="btn btn-primary db-conflict-reload" :disabled="editBusy" @click="ed.conflictReload()">{{ t('dashboardEdit.conflictDialog.reload') }}</button>
      </template>
    </EditDialog>
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

/* while editing the panel stays in view instead of the time frame bar */
.dashboard.is-editing .top {
  position: static;
}

h1 {
  margin: 0 0 0.6rem;
  overflow-wrap: anywhere;
}

.title-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.4rem 0.75rem;
}

.title-line h1 {
  flex: 1 1 auto;
  min-width: 0;
}

.db-edit svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
  stroke-linejoin: round;
}

.db-edit-hint {
  margin: 0 0 0.6rem;
  font-size: 0.8rem;
}

.layout,
.main-col {
  min-width: 0;
}

.layout.is-editing {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(20rem, 24rem);
  gap: 1rem;
  align-items: start;
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
