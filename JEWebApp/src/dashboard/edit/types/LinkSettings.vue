<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { DASHBOARD_TIME_FRAMES } from '../../timeframe'
import { objectId, useBlock, type TypeEditorProps } from './shared'
import TargetSelect from './TargetSelect.vue'
import { useNavTargets, useReportTargets } from './targets'
import './types.css'

/*
 * The link widgets: Analyse-Link (widget/LinkerWidget.java, block `analysisLinker`: `graphAnalysisObject` and the
 * optional `forcedInterval`, a TimeFrameFactory id written only if not empty — GraphAnalysisLinkerNode NON_EMPTY),
 * Dashboard-Link (DashboadLinkWidget.java, `dashboardLinker.dashboardObject`) and Report-Link (ReportLinkWidget.java,
 * `reportLinker.reportObject`). 0 = no target.
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string) => t('dashboardEdit.types.' + k)
const uid = useId()

const kind = computed(() => (props.widget.WidgetType === 'Dashboard-Link' ? 'dashboard' : props.widget.WidgetType === 'Report-Link' ? 'report' : 'analysis'))
const KEYS = {
  analysis: { block: 'analysisLinker', field: 'graphAnalysisObject' },
  dashboard: { block: 'dashboardLinker', field: 'dashboardObject' },
  report: { block: 'reportLinker', field: 'reportObject' },
} as const
const keys = computed(() => KEYS[kind.value])
const b = useBlock(props, () => keys.value.block)
const target = computed(() => objectId(b.cfg.value[keys.value.field]))

const nav = useNavTargets()
const reports = useReportTargets(() => kind.value === 'report')
const targets = computed(() => (kind.value === 'report' ? reports.targets.value : kind.value === 'dashboard' ? nav.dashboards.value : nav.analyses.value))
const loading = computed(() => (kind.value === 'report' ? reports.state.value === 'loading' : nav.loading.value))

/* time frame of the analysis: the CC offers TimeFrameFactory.getAll (standard ids and LASTVALUE) */
const FRAMES = [...DASHBOARD_TIME_FRAMES.filter((f) => f !== 'CUSTOM'), 'LASTVALUE']
const interval = computed(() => b.str('forcedInterval', ''))
const frameLabel = (f: string) => (f === 'LASTVALUE' ? tt('link.lastValue') : t('dashboard.timeFrames.' + f))
function setInterval(value: string) {
  const block = b.block()
  if (value) block.forcedInterval = value
  else delete block.forcedInterval
}
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('link.tab') }}</legend>
    <div class="ae-grid">
      <TargetSelect
        :id="uid + '-target'"
        :label="tt('link.target.' + kind)"
        :targets="targets"
        :value="target"
        :loading="loading && !targets.length"
        @change="b.set(keys.field, $event)"
      />
      <p v-if="kind === 'report' && reports.state.value === 'error'" class="ae-error ae-wide">{{ tt('link.reportsError') }}</p>
      <div v-if="kind === 'analysis'" class="edit-field ae-wide">
        <label :for="uid + '-interval'">{{ tt('link.timeFrame') }}</label>
        <select :id="uid + '-interval'" :value="interval" @change="setInterval(($event.target as HTMLSelectElement).value)">
          <option value="">{{ tt('link.dashboardInterval') }}</option>
          <option v-for="f in FRAMES" :key="f" :value="f">{{ frameLabel(f) }}</option>
          <option v-if="interval && !FRAMES.includes(interval)" :value="interval">{{ tt('link.otherInterval') }} ({{ interval }})</option>
        </select>
      </div>
    </div>
  </fieldset>
</template>
