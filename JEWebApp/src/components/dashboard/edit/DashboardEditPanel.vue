<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DashboardConfig, DashboardTimeFrameOption } from '../../../api/dashboard'
import type { DashboardEditing } from '../../../dashboard/edit/useDashboardEditing'
import EditDashboardTab from './EditDashboardTab.vue'
import EditDataTab from './EditDataTab.vue'
import EditWidgetTab from './EditWidgetTab.vue'
import '../../../styles/edit.css'
import '../../../styles/isoEdit.css'
import '../../../styles/analysisEdit.css'
import '../../../styles/dashboardEdit.css'

/*
 * Side panel of the dashboard edit mode (E4) with the tabs "Dashboard", "Widget", "Daten" and the buttons
 * "Speichern", "Speichern unter", "Verwerfen", "Löschen" (as the analysis editor of E3). Selecting a widget on the
 * canvas while "Dashboard" is open switches to "Widget".
 */

const props = defineProps<{ ed: DashboardEditing; timeFrames: readonly DashboardTimeFrameOption[]; shown: DashboardConfig | null; dashboardId: number | null }>()
const { t } = useI18n()
const ed = props.ed

const uid = `dbe-${Math.random().toString(36).slice(2, 9)}`
type Tab = 'dashboard' | 'widget' | 'data'
const TABS: Tab[] = ['dashboard', 'widget', 'data']
const tab = ref<Tab>('dashboard')

watch(
  () => ed.selection.value,
  (sel, old) => {
    if (sel.size && !old?.size && tab.value === 'dashboard') tab.value = 'widget'
  },
)

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

const tabOf = (path: string): Tab => (path.startsWith('widget.') ? (path.includes('.row.') ? 'data' : 'widget') : 'dashboard')
const problemCount = (tb: Tab) => ed.problems.value.filter((p) => tabOf(p.path) === tb).length

const previewText = computed(() => {
  switch (ed.previewState.value) {
    case 'waiting':
      return t('dashboardEdit.preview.waiting')
    case 'loading':
      return t('dashboardEdit.preview.loading')
    case 'error':
      return t('dashboardEdit.preview.error')
    case 'invalid':
      return t('dashboardEdit.preview.invalid')
    default:
      return t('dashboardEdit.preview.hint')
  }
})
</script>

<template>
  <aside class="ae-panel dbe-panel" :aria-labelledby="uid + '-title'">
    <header class="ae-head">
      <h2 :id="uid + '-title'">{{ t('dashboardEdit.panelTitle') }}</h2>
      <span class="ae-state" :class="{ 'is-dirty': ed.dirty.value }" role="status">{{ ed.dirty.value ? t('dashboardEdit.unsaved') : t('dashboardEdit.noChanges') }}</span>
    </header>

    <div class="ae-tabs" role="tablist" :aria-label="t('dashboardEdit.panelTitle')" @keydown="onTabKey">
      <button
        v-for="tb in TABS"
        :id="`${uid}-tab-${tb}`"
        :key="tb"
        type="button"
        role="tab"
        class="ae-tab"
        :class="'dbe-tab-' + tb"
        :aria-selected="tab === tb"
        :aria-controls="`${uid}-panel`"
        :tabindex="tab === tb ? 0 : -1"
        @click="tab = tb"
      >
        {{ t('dashboardEdit.tabs.' + tb) }}<span v-if="problemCount(tb)" class="ae-count" :aria-label="t('dashboardEdit.problemsTitle') + ' ' + problemCount(tb)">●</span>
      </button>
    </div>

    <div :id="`${uid}-panel`" class="ae-body" role="tabpanel" :aria-labelledby="`${uid}-tab-${tab}`">
      <p v-if="!ed.canWrite.value" class="ae-hint ae-readonly">{{ t('dashboardEdit.readOnlyHint') }}</p>
      <EditDashboardTab v-if="tab === 'dashboard'" :ed="ed" :uid="uid" :time-frames="timeFrames" />
      <EditWidgetTab v-else-if="tab === 'widget'" :ed="ed" :uid="uid" :time-frames="timeFrames" />
      <EditDataTab v-else :ed="ed" :uid="uid" :shown="shown" :dashboard-id="dashboardId" />
    </div>

    <div class="ae-notes">
      <p class="ae-preview" :class="{ 'is-error': ed.previewState.value === 'error' || ed.previewState.value === 'invalid' }" role="status">{{ previewText }}</p>
      <div v-if="ed.previewMessages.value.length" class="alert alert-error ae-preview-messages">
        <ul>
          <li v-for="(m, i) in ed.previewMessages.value" :key="i">{{ m }}</li>
        </ul>
      </div>
      <div v-if="ed.messages.value.length" class="alert alert-error ae-messages" role="alert">
        <strong>{{ t('dashboardEdit.invalidTitle') }}</strong>
        <ul>
          <li v-for="(m, i) in ed.messages.value" :key="i">{{ m }}</li>
        </ul>
      </div>
      <div v-if="ed.error.value" class="alert alert-error ae-error-box" role="alert">
        {{ ed.error.value }}
        <ul v-if="ed.problems.value.length">
          <li v-for="(p, i) in ed.problems.value.slice(0, 8)" :key="i">{{ t('dashboardEdit.tabs.' + tabOf(p.path)) }}: {{ t(p.key, p.params ?? {}) }}</li>
        </ul>
      </div>
    </div>

    <footer class="ae-foot">
      <button v-if="ed.canWrite.value" type="button" class="btn btn-primary dbe-save" :disabled="ed.busy.value || !ed.dirty.value" @click="ed.save()">
        {{ ed.busy.value ? t('edit.saving') : t('dashboardEdit.save') }}
      </button>
      <button v-if="ed.canSaveAs.value" type="button" class="edit-btn dbe-save-as" :class="{ 'btn btn-primary': !ed.canWrite.value }" :disabled="ed.busy.value" @click="ed.openSaveAs()">
        {{ t('dashboardEdit.saveAs') }}
      </button>
      <button type="button" class="edit-btn dbe-discard" :disabled="ed.busy.value" @click="ed.discard()">{{ t('dashboardEdit.discard') }}</button>
      <button v-if="ed.canDelete.value" type="button" class="edit-btn is-danger ae-delete dbe-delete" :disabled="ed.busy.value" @click="ed.askDelete()">{{ t('dashboardEdit.delete') }}</button>
    </footer>
  </aside>
</template>
