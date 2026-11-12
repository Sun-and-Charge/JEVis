<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { hasRow, newRow, setUnit, type Draft, type PickedRow, type Problem } from '../../../analysis/editor'
import { useRowUnits } from '../../../analysis/editFields'
import { useAuthStore } from '../../../stores/auth'
import EditChartsTab from './EditChartsTab.vue'
import EditGeneralTab from './EditGeneralTab.vue'
import EditRowsTab from './EditRowsTab.vue'
import RowPickerDialog from './RowPickerDialog.vue'
import '../../../styles/edit.css'
import '../../../styles/isoEdit.css'
import '../../../styles/analysisEdit.css'

/*
 * Side panel of the analysis edit mode (E3) with the tabs "Allgemein", "Diagramme", "Datenreihen" and the buttons
 * "Speichern", "Speichern unter", "Verwerfen", "Löschen". The draft is changed in place; the view previews it.
 * At phone width the panel is a full-width sheet that can be folded down to its buttons to look at the preview.
 */

const props = defineProps<{
  draft: Draft
  problems: readonly Problem[]
  canWrite: boolean
  canDelete: boolean
  canSaveAs: boolean
  dirty: boolean
  busy: boolean
  error: string | null
  messages: readonly string[]
  previewState: 'idle' | 'waiting' | 'loading' | 'error' | 'invalid'
  previewMessages: readonly string[]
}>()
const emit = defineEmits<{ save: []; saveAs: []; discard: []; remove: [] }>()
const { t } = useI18n()
const auth = useAuthStore()
const units = useRowUnits(auth.http)

const uid = `ae-${Math.random().toString(36).slice(2, 9)}`
type Tab = 'general' | 'charts' | 'rows'
const TABS: Tab[] = ['general', 'charts', 'rows']
const tab = ref<Tab>('general')
const rowsChart = ref<string | null>(props.draft.charts[0]?.uid ?? null)
const folded = ref(false)
const picking = ref(false)

const tabOf = (p: Problem): Tab => (p.path.startsWith('charts.') ? (p.path.includes('.rows.') ? 'rows' : 'charts') : 'general')
const problemCount = (tb: Tab) => props.problems.filter((p) => tabOf(p) === tb).length

function selectTab(next: Tab) {
  tab.value = next
}

function onTabKey(e: KeyboardEvent) {
  const i = TABS.indexOf(tab.value)
  let next = -1
  if (e.key === 'ArrowRight') next = (i + 1) % TABS.length
  else if (e.key === 'ArrowLeft') next = (i + TABS.length - 1) % TABS.length
  else if (e.key === 'Home') next = 0
  else if (e.key === 'End') next = TABS.length - 1
  if (next < 0) return
  e.preventDefault()
  tab.value = TABS[next]
  void nextTick(() => document.getElementById(`${uid}-tab-${TABS[next]}`)?.focus())
}

function showRows(chartUid: string) {
  rowsChart.value = chartUid
  tab.value = 'rows'
}

const pickChart = computed(() => props.draft.charts.find((c) => c.uid === rowsChart.value) ?? props.draft.charts[0] ?? null)
const present = computed(() => (pickChart.value ? pickChart.value.rows.map((r) => `${r.objectId}|${r.attribute}`) : []))

/** rows of the picker: added to the chart with its own unit (first of the units list) */
function addRows(picked: PickedRow[]) {
  const chart = pickChart.value
  picking.value = false
  if (!chart) return
  for (const { node, attribute } of picked) {
    if (hasRow(chart, node.id, attribute)) continue
    const row = newRow(node, attribute, chart.rows.length)
    chart.rows.push(row)
    void units.load(row).then((list) => {
      const own = list?.[0]
      const target = chart.rows.find((r) => r.uid === row.uid)
      if (own && target && target.unitLabel == null && target.unitFormula == null && target.unitPrefix == null) setUnit(target, own)
    })
  }
}

/** location of a problem for the summary: tab, chart, row */
function where(p: Problem): string {
  const m = /^charts\.(\d+)(?:\.rows\.(\d+))?/.exec(p.path)
  if (!m) return t('analysisEdit.tabs.general')
  const chart = props.draft.charts[Number(m[1])]
  const chartName = chart?.name.trim() || t('analysisEdit.charts.unnamed')
  if (m[2] === undefined) return `${t('analysisEdit.tabs.charts')} – ${chartName}`
  const row = chart?.rows[Number(m[2])]
  return `${t('analysisEdit.tabs.rows')} – ${chartName} – ${row?.name.trim() || row?.objectName || ''}`
}

const previewText = computed(() => {
  switch (props.previewState) {
    case 'waiting':
      return t('analysisEdit.preview.waiting')
    case 'loading':
      return t('analysisEdit.preview.loading')
    case 'error':
      return t('analysisEdit.preview.error')
    case 'invalid':
      return t('analysisEdit.preview.invalid')
    default:
      return t('analysisEdit.preview.hint')
  }
})
</script>

<template>
  <aside class="ae-panel" :class="{ 'is-folded': folded }" :aria-labelledby="uid + '-title'">
    <header class="ae-head">
      <h2 :id="uid + '-title'">{{ t('analysisEdit.panelTitle') }}</h2>
      <span class="ae-state" :class="{ 'is-dirty': dirty }" role="status">{{ dirty ? t('analysisEdit.unsaved') : t('analysisEdit.noChanges') }}</span>
      <button type="button" class="edit-btn is-small ae-toggle" :aria-expanded="!folded" @click="folded = !folded">
        {{ folded ? t('analysisEdit.showSettings') : t('analysisEdit.showPreview') }}
      </button>
    </header>

    <div class="ae-tabs" role="tablist" :aria-label="t('analysisEdit.panelTitle')" @keydown="onTabKey">
      <button
        v-for="tb in TABS"
        :id="`${uid}-tab-${tb}`"
        :key="tb"
        type="button"
        role="tab"
        class="ae-tab"
        :class="'ae-tab-' + tb"
        :aria-selected="tab === tb"
        :aria-controls="`${uid}-panel`"
        :tabindex="tab === tb ? 0 : -1"
        @click="selectTab(tb)"
      >
        {{ t('analysisEdit.tabs.' + tb) }}<span v-if="problemCount(tb)" class="ae-count" :aria-label="t('analysisEdit.problemsTitle') + ' ' + problemCount(tb)">●</span>
      </button>
    </div>

    <div :id="`${uid}-panel`" class="ae-body" role="tabpanel" :aria-labelledby="`${uid}-tab-${tab}`">
      <p v-if="!canWrite" class="ae-hint ae-readonly">{{ t('analysisEdit.readOnlyHint') }}</p>
      <EditGeneralTab v-if="tab === 'general'" :draft="draft" :problems="problems" :uid="uid" :can-rename="canWrite" />
      <EditChartsTab v-else-if="tab === 'charts'" :draft="draft" :problems="problems" :uid="uid" @rows="showRows" />
      <EditRowsTab v-else v-model:chart-uid="rowsChart" :draft="draft" :problems="problems" :uid="uid" :units="units" @pick="picking = true" />
    </div>

    <div class="ae-notes">
      <p class="ae-preview" :class="{ 'is-error': previewState === 'error' || previewState === 'invalid' }" role="status">{{ previewText }}</p>
      <div v-if="previewMessages.length" class="alert alert-error ae-preview-messages">
        <ul>
          <li v-for="(m, i) in previewMessages" :key="i">{{ m }}</li>
        </ul>
      </div>
      <div v-if="messages.length" class="alert alert-error ae-messages" role="alert">
        <strong>{{ t('analysisEdit.invalidTitle') }}</strong>
        <ul>
          <li v-for="(m, i) in messages" :key="i">{{ m }}</li>
        </ul>
      </div>
      <div v-if="error" class="alert alert-error ae-error-box" role="alert">
        {{ error }}
        <ul v-if="problems.length">
          <li v-for="(p, i) in problems.slice(0, 8)" :key="i">{{ where(p) }}: {{ t(p.key, p.params ?? {}) }}</li>
        </ul>
      </div>
    </div>

    <footer class="ae-foot">
      <button v-if="canWrite" type="button" class="btn btn-primary ae-save" :disabled="busy || !dirty" @click="emit('save')">
        {{ busy ? t('edit.saving') : t('analysisEdit.save') }}
      </button>
      <button v-if="canSaveAs" type="button" class="edit-btn ae-save-as" :class="{ 'btn btn-primary': !canWrite }" :disabled="busy" @click="emit('saveAs')">
        {{ t('analysisEdit.saveAs') }}
      </button>
      <button type="button" class="edit-btn ae-discard" :disabled="busy" @click="emit('discard')">{{ t('analysisEdit.discard') }}</button>
      <button v-if="canDelete" type="button" class="edit-btn is-danger ae-delete" :disabled="busy" @click="emit('remove')">{{ t('analysisEdit.delete') }}</button>
    </footer>

    <RowPickerDialog v-if="picking" :present="present" @close="picking = false" @add="addRows" />
  </aside>
</template>
