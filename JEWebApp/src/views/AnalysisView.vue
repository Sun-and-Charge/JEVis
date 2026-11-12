<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { loadAnalysis, loadAnalysisData, type AnalysisConfig, type AnalysisData } from '../api/analysis'
import { HttpError } from '../api/http'
import '../analysis/echarts'
import { buildChartViews, type ChartView, type RowView } from '../analysis/model'
import { editableRowNote, type NotePoint } from '../analysis/notes'
import { formatDateTime, formatNumber } from '../analysis/format'
import type { RowNote } from '../api/analysis'
import type { SavedValue, EntryInfo } from '../api/entry'
import EditDialog from '../components/edit/EditDialog.vue'
import EntryDialog from '../components/edit/EntryDialog.vue'
import NoteDialog from '../components/edit/NoteDialog.vue'
import { formatEntryTime } from '../edit/entry'
import type { OptionContext } from '../analysis/options'
import { browserTimeZone, dataRequest, settingsFromQuery, settingsToQuery, shiftRange, type AnalysisSettings } from '../analysis/settings'
import AnalysisChart from '../components/analysis/AnalysisChart.vue'
import AnalysisToolbar from '../components/analysis/AnalysisToolbar.vue'
import AnalysisEditPanel from '../components/analysis/edit/AnalysisEditPanel.vue'
import AnalysisNameDialog from '../components/analysis/edit/AnalysisNameDialog.vue'
import ConfirmDialog from '../components/edit/ConfirmDialog.vue'
import { useAnalysisEditing } from '../analysis/useAnalysisEditing'
import { i18nTagName, type EditableNote } from '../notes/notes'
import '../styles/edit.css'
import { useAuthStore } from '../stores/auth'
import { useNavigationStore } from '../stores/navigation'

const props = defineProps<{ id: number }>()
const { t, te, locale } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()
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

/** settings of the last data request (leaving edit mode reloads the data when the toolbar changed meanwhile) */
let fetchedKey = ''

async function fetchData() {
  if (!config.value) return
  fetchedKey = settingsKey.value
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
watch([config, settingsKey], () => {
  // while editing, the preview of the draft is loaded instead (the saved data stays for "Verwerfen")
  if (!ed.editing.value) void fetchData()
})

/* ---- E3: edit mode ---- */

const ed = useAnalysisEditing({
  id: () => props.id,
  config,
  data,
  title,
  request: () => dataRequest(settings.value, browserTimeZone()),
  settingsKey,
  replaceConfig: (next) => {
    configSeq++
    config.value = next
    configState.value = 'ready'
  },
})
const {
  editing,
  draft,
  dirty,
  problems,
  busy: editBusy,
  canEdit,
  canWrite,
  canDelete,
  canSaveAs,
  previewState,
  result,
  leaveAsk,
  saveAs,
  saveAsError,
  overwrite,
  deleting,
  deleteError,
  conflictOpen,
} = ed

onMounted(() => void nav.loadDirectories())

watch(editing, (now, was) => {
  if (was && !now && fetchedKey !== settingsKey.value) void fetchData()
})

/* "Neue Analyse" of the navigation opens the new analysis with ?edit=1 */
watch(
  () => [configState.value, route.query.edit, nav.directories] as const,
  ([state, edit, directories]) => {
    if (state !== 'ready' || edit !== '1' || directories === null || editing.value) return
    const query = { ...route.query }
    delete query.edit
    void router.replace({ query })
    ed.start()
  },
  { immediate: true },
)

function onFilter(chartId: number, column: string, text: string) {
  if (editing.value) ed.setFilterText(chartId, column, text)
}

function applySettings(next: AnalysisSettings) {
  router.replace({ query: settingsToQuery(next) })
}

const range = computed(() => {
  const answer = ed.shownData.value
  if (!answer) return null
  const from = Date.parse(answer.from)
  const to = Date.parse(answer.to)
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

/* while editing: the draft and its preview data */
const shown = computed(() => ed.shownConfig.value)
const charts = computed<ChartView[]>(() => (shown.value ? buildChartViews(shown.value, ed.shownData.value, t('analysis.state.noAccess')) : []))

/** consecutive pies / tables side by side (horizontalPies / horizontalTables of the analysis) */
const blocks = computed(() => {
  const result: { key: string; columns: number; charts: ChartView[] }[] = []
  const pies = Math.max(1, shown.value?.horizontalPies ?? 1)
  const tables = Math.max(1, shown.value?.horizontalTables ?? 1)
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

const perScreen = computed(() => Math.min(6, Math.max(1, Math.round(shown.value?.chartsPerScreen || 1))))

/** fixed height of a chart in the configuration (autoSize off) */
function chartStyle(chart: ChartView) {
  return shown.value && !shown.value.autoSize && chart.height > 0 ? { '--chart-h': `${chart.height}px` } : undefined
}

/** data or preview request running */
const busy = computed(() => (editing.value ? previewState.value === 'loading' : dataState.value === 'loading'))

const from = computed(() => range.value?.from.getTime() ?? null)
const to = computed(() => range.value?.to.getTime() ?? null)

/* ---- E1: notes from the chart, manual values ---- */

type NoteDialogState =
  | { kind: 'new'; rowId: number; rowName: string; timestamp: number }
  | { kind: 'edit'; note: EditableNote; rowName: string }
  | { kind: 'pick'; point: NotePoint }
const noteDialog = ref<NoteDialogState | null>(null)
const entryDialog = ref<{ rowId: number; rowName: string } | null>(null)
const message = ref<string | null>(null)
const tagName = computed(() => i18nTagName(t, te))

const toEditable = (n: RowNote): EditableNote => ({
  id: n.id!,
  timestamp: n.timestamp,
  text: n.text,
  tags: n.tags,
  version: n.version ?? null,
  canEdit: n.canEdit === true,
  canDelete: n.canDelete === true,
})

function onNote(point: NotePoint) {
  message.value = null
  const editable = point.notes.filter(editableRowNote)
  if (!point.notes.length) {
    if (point.canAddNote) noteDialog.value = { kind: 'new', rowId: point.objectId, rowName: point.rowName, timestamp: point.timestamp }
  } else if (point.notes.length === 1 && editable.length === 1) {
    noteDialog.value = { kind: 'edit', note: toEditable(editable[0]), rowName: point.rowName }
  } else {
    noteDialog.value = { kind: 'pick', point }
  }
}

function pickEdit(point: NotePoint, n: RowNote) {
  noteDialog.value = { kind: 'edit', note: toEditable(n), rowName: point.rowName }
}

function pickNew(point: NotePoint) {
  noteDialog.value = { kind: 'new', rowId: point.objectId, rowName: point.rowName, timestamp: point.timestamp }
}

function onAddNote(row: RowView) {
  if (typeof row.objectId !== 'number') return
  message.value = null
  noteDialog.value = { kind: 'new', rowId: row.objectId, rowName: row.name, timestamp: Date.now() }
}

function onEntry(row: RowView) {
  if (typeof row.objectId !== 'number') return
  message.value = null
  entryDialog.value = { rowId: row.objectId, rowName: row.name }
}

function noteDone(text: string) {
  noteDialog.value = null
  message.value = text
  void fetchData()
}

function entrySaved(result: SavedValue, info: EntryInfo) {
  entryDialog.value = null
  const value = formatNumber(result.value, locale.value, { min: 0, max: 6 }) + (info.unit ? ' ' + info.unit : '')
  message.value = t('entry.saved', { value, time: formatEntryTime(result.timestamp, info.mode, locale.value, info.timeZone) })
  void fetchData()
}

const pickTime = (ms: number) => formatDateTime(ms, locale.value)
</script>

<template>
  <section class="analysis" :class="{ 'is-editing': editing }" :style="{ '--per-screen': perScreen }" aria-labelledby="an-title">
    <p v-if="configState === 'loading'" class="muted" role="status">{{ t('analysis.loading') }}</p>
    <div v-else-if="configState !== 'ready'" class="alert alert-error" role="alert">
      {{ configState === 'notFound' ? t('analysis.notFound') : t('analysis.loadError') }}
      <button v-if="configState === 'error'" type="button" class="btn-link retry" @click="fetchConfig">{{ t('analysis.retry') }}</button>
    </div>

    <template v-else-if="config">
      <div class="top">
        <div class="title-line">
          <h1 id="an-title">{{ title }}</h1>
          <button
            v-if="canEdit && !editing"
            type="button"
            class="edit-btn an-edit"
            :aria-label="t('analysisEdit.editLabel', { name: title })"
            @click="ed.start()"
          >
            <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M3 13l1-3.5L10.5 3 13 5.5 6.5 12zM9.5 4l2.5 2.5" /></svg>
            {{ t('analysisEdit.edit') }}
          </button>
        </div>
        <AnalysisToolbar :settings="settings" :range="range" :busy="busy" @change="applySettings" @shift="shift" />
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

          <p v-if="message" class="alert alert-ok edit-result analysis-message" role="status">{{ message }}</p>
          <p v-if="result" class="alert edit-result analysis-edit-result" :class="result.ok ? 'alert-ok' : 'alert-error'" role="status">{{ result.text }}</p>
          <p v-if="!charts.length" class="muted">{{ t('analysis.noCharts') }}</p>
          <div class="charts" :class="{ stale: busy }">
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
                @note="onNote"
                @add-note="onAddNote"
                @entry="onEntry"
                @filter="(column: string, text: string) => onFilter(chart.id, column, text)"
              />
            </div>
          </div>
        </div>
        <AnalysisEditPanel
          v-if="editing && draft"
          :draft="draft"
          :problems="problems"
          :can-write="canWrite"
          :can-delete="canDelete"
          :can-save-as="canSaveAs"
          :dirty="dirty"
          :busy="editBusy"
          :error="ed.error.value"
          :messages="ed.messages.value"
          :preview-state="previewState"
          :preview-messages="ed.previewMessages.value"
          @save="ed.save()"
          @save-as="ed.openSaveAs()"
          @discard="ed.discard()"
          @remove="ed.askDelete()"
        />
      </div>
    </template>

    <!-- E3 dialogs -->
    <ConfirmDialog
      v-if="leaveAsk"
      :title="t('analysisEdit.leave.title')"
      :message="t('analysisEdit.leave.message')"
      :confirm-label="t('analysisEdit.leave.confirm')"
      danger
      @confirm="leaveAsk(true)"
      @cancel="leaveAsk(false)"
    />
    <AnalysisNameDialog
      v-if="saveAs && !overwrite"
      :title="t('analysisEdit.nameDialog.saveAsTitle')"
      :confirm-label="t('analysisEdit.save')"
      :directories="nav.creatableDirectories"
      :name="saveAs.name"
      :directory-id="saveAs.directoryId"
      :busy="editBusy"
      :error="saveAsError"
      @close="saveAs = null"
      @submit="ed.submitSaveAs"
    />
    <ConfirmDialog
      v-if="overwrite"
      :title="t('analysisEdit.overwrite.title')"
      :message="t('analysisEdit.overwrite.message', { name: overwrite.name })"
      :confirm-label="t('analysisEdit.overwrite.confirm')"
      :busy="editBusy"
      danger
      @confirm="ed.confirmOverwrite()"
      @cancel="ed.cancelOverwrite()"
    />
    <ConfirmDialog
      v-if="deleting"
      :title="t('analysisEdit.deleteDialog.title')"
      :message="t('analysisEdit.deleteDialog.message', { name: title })"
      :confirm-label="t('analysisEdit.deleteDialog.confirm')"
      :busy="editBusy"
      :error="deleteError"
      danger
      @confirm="ed.confirmDelete()"
      @cancel="ed.cancelDelete()"
    />
    <EditDialog v-if="conflictOpen" :title="t('analysisEdit.conflictDialog.title')" :busy="editBusy" @close="conflictOpen = false">
      <p class="edit-message">{{ t('analysisEdit.conflictDialog.message') }}</p>
      <template #footer>
        <button type="button" class="edit-btn" :disabled="editBusy" @click="conflictOpen = false">{{ t('edit.cancel') }}</button>
        <button v-if="canSaveAs" type="button" class="edit-btn an-conflict-save-as" :disabled="editBusy" @click="ed.conflictSaveAs()">{{ t('analysisEdit.saveAs') }}</button>
        <button type="button" class="btn btn-primary an-conflict-reload" :disabled="editBusy" @click="ed.conflictReload()">{{ t('analysisEdit.conflictDialog.reload') }}</button>
      </template>
    </EditDialog>

    <NoteDialog
      v-if="noteDialog?.kind === 'new'"
      :row-id="noteDialog.rowId"
      :row-name="noteDialog.rowName"
      :timestamp="noteDialog.timestamp"
      :tag-name="tagName"
      @close="noteDialog = null"
      @saved="noteDone(t('noteEdit.saved'))"
      @stale="fetchData"
    />
    <NoteDialog
      v-else-if="noteDialog?.kind === 'edit'"
      :note="noteDialog.note"
      :row-name="noteDialog.rowName"
      :tag-name="tagName"
      @close="noteDialog = null"
      @saved="noteDone(t('noteEdit.saved'))"
      @deleted="noteDone(t('noteEdit.deleted'))"
      @stale="fetchData"
    />
    <EditDialog v-else-if="noteDialog?.kind === 'pick'" :title="t('noteEdit.pickTitle', { time: pickTime(noteDialog.point.timestamp) })" @close="noteDialog = null">
      <p class="muted edit-message">{{ noteDialog.point.rowName }}</p>
      <h3 class="pick-head">{{ t('noteEdit.pickExisting') }}</h3>
      <ul class="pick-list">
        <li v-for="(n, i) in noteDialog.point.notes" :key="n.id ?? i">
          <span class="pick-text">{{ n.text || t('modules.none') }}</span>
          <span v-if="n.user" class="muted"> · {{ n.user }}</span>
          <button v-if="editableRowNote(n)" type="button" class="edit-btn is-small pick-edit" @click="pickEdit(noteDialog.point, n)">{{ t('noteEdit.edit') }}</button>
        </li>
      </ul>
      <template #footer>
        <button type="button" class="edit-btn" @click="noteDialog = null">{{ t('edit.cancel') }}</button>
        <button v-if="noteDialog.point.canAddNote" type="button" class="btn btn-primary pick-new" @click="pickNew(noteDialog.point)">{{ t('noteEdit.addHere') }}</button>
      </template>
    </EditDialog>
    <EntryDialog v-if="entryDialog" :row-id="entryDialog.rowId" :row-name="entryDialog.rowName" @close="entryDialog = null" @saved="entrySaved" />
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

.pick-head {
  font-size: 0.9rem;
  margin: 0 0 0.4rem;
}

.pick-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.5rem;
}

.pick-list li {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.4rem;
}

.pick-text {
  white-space: pre-wrap;
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

.an-edit svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
  stroke-linejoin: round;
}

.layout {
  min-width: 0;
}

/* while editing the panel stays in view instead of the toolbar */
.analysis.is-editing .top {
  position: static;
}

.layout.is-editing {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(20rem, 26rem);
  gap: 1rem;
  align-items: start;
}

.main-col {
  min-width: 0;
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

@media (max-width: 800px) {
  .layout.is-editing {
    display: block;
    /* room for the folded panel */
    padding-bottom: 6rem;
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
