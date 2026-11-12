<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { UnitOption } from '../../../api/analysis'
import type { DashboardConfig } from '../../../api/dashboard'
import { CHART_TYPES, COLOR_MAPPINGS, isTableType, isXyType, ORIENTATIONS, ROW_TYPES, type PickedRow } from '../../../analysis/editor'
import { useRowUnits } from '../../../analysis/editFields'
import { cssToFx, fxAlpha, toHexInput, widgetRows, type RawChartModel, type RawRow } from '../../../dashboard/edit/raw'
import { addRows, AGGREGATION_PERIODS, ensureChartModel, MANIPULATION_MODES, moveRow, removeRow, rowId, sameRowUnit, setRowUnit } from '../../../dashboard/edit/source'
import { typeEditorFor } from '../../../dashboard/edit/typeEditors'
import type { DashboardEditing } from '../../../dashboard/edit/useDashboardEditing'
import { useAuthStore } from '../../../stores/auth'
import RowPickerDialog from '../../analysis/edit/RowPickerDialog.vue'
import DbeColor from './DbeColor.vue'
import { typeHasRows, useTypeLabel } from './typeLabel'

/*
 * Tab "Daten" of the dashboard editor (E4) for one selected widget: its data rows (E3 picker; name, colour, unit,
 * aggregation, manipulation, decimals, calculation; for charts also axis and chart type), the chart settings of a
 * `Chart` widget and the settings of its type (typeEditorFor). Rows the user may not read stay unchanged.
 */

const props = defineProps<{ ed: DashboardEditing; uid: string; shown: DashboardConfig | null; dashboardId: number | null }>()
const { t, te } = useI18n()
const auth = useAuthStore()
const units = useRowUnits(auth.http)
const typeLabel = useTypeLabel()

const widget = computed(() => (props.ed.selectedWidgets.value.length === 1 ? props.ed.selectedWidgets.value[0] : null))
const hasRows = computed(() => !!widget.value && typeHasRows(widget.value.WidgetType, !!widget.value.dataHandler))
const isChart = computed(() => widget.value?.WidgetType === 'Chart')
const rows = computed(() => (widget.value ? widgetRows(widget.value) : []))
const typeEditor = computed(() => (widget.value ? typeEditorFor(String(widget.value.WidgetType)) : null))
const id = (k: string) => `${props.uid}-d-${k}`
const label = (group: string, value: string) => (te(`${group}.${value}`) ? t(`${group}.${value}`) : value)

/* what the last preview knows of the rows: names, units, read right */
const viewRows = computed(() => props.shown?.widgets.find((w) => String(w.uuid) === String(widget.value?.uuid))?.rows ?? [])
const viewRow = (r: RawRow) => viewRows.value.find((v) => v.objectId === r.id && v.attribute === r.attributeString)
const readable = (r: RawRow) => viewRow(r)?.readable !== false
const rowLabel = (r: RawRow) => (readable(r) ? (typeof r.name === 'string' && r.name.trim()) || viewRow(r)?.name || `${r.id}` : t('analysisEdit.rows.noPermission'))
const unitRef = (r: RawRow) => ({ objectId: r.id, attribute: r.attributeString })

const open = ref<Set<string>>(new Set())
function toggle(r: RawRow) {
  const k = rowId(r)
  const next = new Set(open.value)
  if (next.has(k)) next.delete(k)
  else {
    next.add(k)
    void units.load(unitRef(r))
  }
  open.value = next
}

/* unit select: index of the offered unit, 'stored' or '' (none stored) */
const unitOptions = (r: RawRow): UnitOption[] => units.unitsOf(unitRef(r)) ?? []
function unitValue(r: RawRow): string {
  const i = unitOptions(r).findIndex((u) => sameRowUnit(r, u))
  if (i >= 0) return String(i)
  return r.unitLabel != null || r.unitFormula != null || r.unitPrefix != null ? 'stored' : ''
}
function setUnitValue(r: RawRow, value: string) {
  const u = unitOptions(r)[Number(value)]
  if (u && value !== '') setRowUnit(r, u)
}
const unitText = (u: UnitOption) => u.label || u.formula || '–'

function setColor(r: RawRow, hex: string) {
  const fx = cssToFx(hex, fxAlpha(r.color))
  if (fx) r.color = fx
}
const decimalsValue = (r: RawRow) => (typeof r.decimalDigits === 'number' && r.decimalDigits >= 0 ? r.decimalDigits : '')
function setDecimals(r: RawRow, value: string) {
  const v = value.trim()
  if (v === '') r.decimalDigits = -1
  else if (Number.isFinite(Number(v))) r.decimalDigits = Number(v)
}

/* picker: rows are added with their own unit (first of the units list) */
const picking = ref(false)
const present = computed(() => rows.value.map(rowId))
function onAdd(picked: PickedRow[]) {
  picking.value = false
  const w = widget.value
  if (!w) return
  for (const row of addRows(w, picked)) {
    void units.load(unitRef(row)).then((list) => {
      const own = list?.[0]
      const target = widgetRows(w).find((x) => rowId(x) === rowId(row))
      if (own && target && target.unitLabel == null && target.unitFormula == null && target.unitPrefix == null) setRowUnit(target, own)
    })
  }
}

/* chart settings of a Chart widget (first chart model) */
const model = computed<RawChartModel | null>(() => (isChart.value && widget.value ? widget.value.dataHandler?.chartModels?.[0] ?? {} : null))
const chartType = computed(() => String(model.value?.chartType ?? 'LINE'))
function setModel(key: string, value: unknown) {
  if (isChart.value && widget.value) ensureChartModel(widget.value)[key] = value
}
function setModelNumber(key: string, value: string) {
  const n = Number(value)
  if (value.trim() !== '' && Number.isFinite(n)) setModel(key, Math.round(n))
}
</script>

<template>
  <div>
    <p v-if="!widget" class="ae-hint dbe-none">{{ ed.selectedWidgets.value.length > 1 ? t('dashboardEdit.data.onlyOne') : t('dashboardEdit.widget.none') }}</p>
    <template v-else>
      <p class="ae-hint dbe-selected" role="status">{{ t('dashboardEdit.widget.one', { type: typeLabel(widget.WidgetType) }) }}</p>

      <section v-if="hasRows" class="ae-section dbe-rows">
        <h3>{{ t('dashboardEdit.data.rows') }}</h3>
        <p v-if="!rows.length" class="ae-hint">{{ t('dashboardEdit.data.noRows') }}</p>
        <ul class="ae-cards">
          <li v-for="(r, ri) in rows" :key="rowId(r)" class="ae-card ae-row dbe-row" :class="{ 'is-open': open.has(rowId(r)), 'is-locked': !readable(r) }">
            <template v-if="!readable(r)">
              <div class="ae-card-head">
                <span class="ae-swatch" style="background: var(--line)" aria-hidden="true" />
                <span class="ae-card-title ae-locked-title">{{ t('analysisEdit.rows.noPermission') }}</span>
              </div>
              <p class="ae-locked">{{ t('dashboardEdit.data.noPermissionHint', { id: r.id }) }}</p>
            </template>
            <template v-else>
              <div class="ae-card-head">
                <button
                  type="button"
                  class="ae-card-toggle"
                  :aria-expanded="open.has(rowId(r))"
                  :aria-controls="id('row-' + ri)"
                  :aria-label="t('analysisEdit.rows.toggle', { name: rowLabel(r) })"
                  @click="toggle(r)"
                >
                  <span class="ae-chevron" aria-hidden="true">›</span>
                  <span class="ae-swatch" :style="{ background: toHexInput(r.color, 'transparent') }" aria-hidden="true" />
                  <span class="ae-card-title">{{ rowLabel(r) }}</span>
                  <span class="ae-card-meta">{{ r.unitLabel ?? viewRow(r)?.unit ?? '' }}</span>
                </button>
                <button type="button" class="ae-icon-btn" :disabled="ri === 0" :aria-label="t('analysisEdit.rows.upLabel', { name: rowLabel(r) })" :title="t('analysisEdit.rows.up')" @click="moveRow(widget, r, -1)">↑</button>
                <button type="button" class="ae-icon-btn" :disabled="ri === rows.length - 1" :aria-label="t('analysisEdit.rows.downLabel', { name: rowLabel(r) })" :title="t('analysisEdit.rows.down')" @click="moveRow(widget, r, 1)">↓</button>
                <button type="button" class="ae-icon-btn is-danger dbe-row-remove" :aria-label="t('analysisEdit.rows.removeLabel', { name: rowLabel(r) })" :title="t('analysisEdit.rows.remove')" @click="removeRow(widget, r)">✕</button>
              </div>
              <div v-if="open.has(rowId(r))" :id="id('row-' + ri)" class="ae-card-body ae-grid">
                <div class="edit-field ae-wide">
                  <label :for="id('name-' + ri)">{{ t('analysisEdit.rows.name') }}</label>
                  <input :id="id('name-' + ri)" v-model="r.name" type="text" maxlength="200" :placeholder="viewRow(r)?.name ?? undefined" />
                </div>
                <DbeColor class="ae-wide" :id="id('color-' + ri)" :label="t('analysisEdit.rows.color')" :value="r.color" @color="setColor(r, $event)" />
                <div class="edit-field ae-wide">
                  <label :for="id('unit-' + ri)">{{ t('analysisEdit.rows.unit') }}</label>
                  <select :id="id('unit-' + ri)" :value="unitValue(r)" @change="setUnitValue(r, ($event.target as HTMLSelectElement).value)">
                    <option v-if="unitValue(r) === ''" value="">{{ viewRow(r)?.unit || t('analysisEdit.rows.unitDefault') }}</option>
                    <option v-if="unitValue(r) === 'stored'" value="stored">{{ t('analysisEdit.rows.unitStored', { unit: r.unitLabel ?? r.unitFormula ?? '' }) }}</option>
                    <option v-for="(u, ui) in unitOptions(r)" :key="ui" :value="String(ui)">{{ unitText(u) }}</option>
                  </select>
                  <p v-if="units.state(unitRef(r)) === 'loading'" class="ae-hint" role="status">{{ t('analysisEdit.rows.unitLoading') }}</p>
                  <p v-else-if="units.state(unitRef(r)) === 'error'" class="ae-error">{{ t('analysisEdit.rows.unitError') }}</p>
                </div>
                <div class="edit-field">
                  <label :for="id('aggregation-' + ri)">{{ t('dashboardEdit.data.aggregation') }}</label>
                  <select :id="id('aggregation-' + ri)" v-model="r.aggregationPeriod" class="dbe-aggregation">
                    <option v-if="r.aggregationPeriod && !(AGGREGATION_PERIODS as readonly string[]).includes(r.aggregationPeriod)" :value="r.aggregationPeriod">{{ r.aggregationPeriod }}</option>
                    <option v-for="a in AGGREGATION_PERIODS" :key="a" :value="a">{{ label('dashboardEdit.aggregations', a) }}</option>
                  </select>
                </div>
                <div class="edit-field">
                  <label :for="id('manipulation-' + ri)">{{ t('dashboardEdit.data.manipulation') }}</label>
                  <select :id="id('manipulation-' + ri)" v-model="r.manipulationMode" class="dbe-manipulation">
                    <option v-if="r.manipulationMode && !(MANIPULATION_MODES as readonly string[]).includes(r.manipulationMode)" :value="r.manipulationMode">{{ r.manipulationMode }}</option>
                    <option v-for="m in MANIPULATION_MODES" :key="m" :value="m">{{ label('dashboardEdit.manipulations', m) }}</option>
                  </select>
                </div>
                <div class="edit-field">
                  <label :for="id('decimals-' + ri)">{{ t('analysisEdit.rows.decimalDigits') }}</label>
                  <input
                    :id="id('decimals-' + ri)"
                    :value="decimalsValue(r)"
                    type="number"
                    min="0"
                    max="20"
                    step="1"
                    :placeholder="t('dashboardEdit.data.decimalsWidget')"
                    @input="setDecimals(r, ($event.target as HTMLInputElement).value)"
                  />
                </div>
                <template v-if="isChart">
                  <div class="edit-field">
                    <label :for="id('axis-' + ri)">{{ t('analysisEdit.rows.axis') }}</label>
                    <select :id="id('axis-' + ri)" v-model.number="r.axis">
                      <option :value="0">{{ t('analysisEdit.axes.0') }}</option>
                      <option :value="1">{{ t('analysisEdit.axes.1') }}</option>
                    </select>
                  </div>
                  <div v-if="isXyType(chartType)" class="edit-field">
                    <label :for="id('type-' + ri)">{{ t('analysisEdit.rows.chartType') }}</label>
                    <select :id="id('type-' + ri)" v-model="r.chartType">
                      <option v-for="type in ROW_TYPES" :key="type" :value="type">{{ label('analysisEdit.chartTypes', type) }}</option>
                    </select>
                  </div>
                </template>
                <label class="ae-check ae-wide">
                  <input v-model="r.calculation" type="checkbox" class="dbe-calculation" />
                  {{ t('analysisEdit.rows.calculation') }}
                </label>
              </div>
            </template>
          </li>
        </ul>
        <button type="button" class="edit-btn ae-add dbe-add-rows" @click="picking = true">+ {{ t('analysisEdit.rows.add') }}</button>
      </section>

      <section v-if="model" class="ae-section dbe-chart">
        <h3>{{ t('dashboardEdit.data.chart') }}</h3>
        <div class="ae-grid">
          <div class="edit-field ae-wide">
            <label :for="id('chartType')">{{ t('analysisEdit.charts.type') }}</label>
            <select :id="id('chartType')" :value="chartType" @change="setModel('chartType', ($event.target as HTMLSelectElement).value)">
              <option v-if="!(CHART_TYPES as readonly string[]).includes(chartType)" :value="chartType">{{ chartType }}</option>
              <option v-for="type in CHART_TYPES" :key="type" :value="type">{{ label('analysisEdit.chartTypes', type) }}</option>
            </select>
          </div>
          <div class="edit-field">
            <label :for="id('minFd')">{{ t('analysisEdit.charts.minFractionDigits') }}</label>
            <input :id="id('minFd')" :value="model.minFractionDigits ?? 2" type="number" min="0" max="20" step="1" @input="setModelNumber('minFractionDigits', ($event.target as HTMLInputElement).value)" />
          </div>
          <div class="edit-field">
            <label :for="id('maxFd')">{{ t('analysisEdit.charts.maxFractionDigits') }}</label>
            <input :id="id('maxFd')" :value="model.maxFractionDigits ?? 2" type="number" min="0" max="20" step="1" @input="setModelNumber('maxFractionDigits', ($event.target as HTMLInputElement).value)" />
          </div>
          <template v-if="isXyType(chartType)">
            <div class="edit-field">
              <label :for="id('xTitle')">{{ t('analysisEdit.charts.xAxisTitle') }}</label>
              <input :id="id('xTitle')" :value="model.xAxisTitle ?? ''" type="text" maxlength="200" @input="setModel('xAxisTitle', ($event.target as HTMLInputElement).value)" />
            </div>
            <div class="edit-field">
              <label :for="id('yTitle')">{{ t('analysisEdit.charts.yAxisTitle') }}</label>
              <input :id="id('yTitle')" :value="model.yAxisTitle ?? ''" type="text" maxlength="200" @input="setModel('yAxisTitle', ($event.target as HTMLInputElement).value)" />
            </div>
            <label class="ae-check ae-wide">
              <input type="checkbox" :checked="model.fixYAxisToZero === true" @change="setModel('fixYAxisToZero', ($event.target as HTMLInputElement).checked)" />
              {{ t('analysisEdit.charts.fixYAxisToZero') }}
            </label>
          </template>
          <template v-if="isTableType(chartType)">
            <div class="edit-field">
              <label :for="id('orientation')">{{ t('analysisEdit.charts.orientation') }}</label>
              <select :id="id('orientation')" :value="model.orientation ?? 'HORIZONTAL'" @change="setModel('orientation', ($event.target as HTMLSelectElement).value)">
                <option v-for="o in ORIENTATIONS" :key="o" :value="o">{{ label('analysisEdit.orientations', o) }}</option>
              </select>
            </div>
            <label class="ae-check ae-wide">
              <input type="checkbox" :checked="model.showColumnSums === true" @change="setModel('showColumnSums', ($event.target as HTMLInputElement).checked)" />
              {{ t('analysisEdit.charts.showColumnSums') }}
            </label>
            <label class="ae-check ae-wide">
              <input type="checkbox" :checked="model.showRowSums === true" @change="setModel('showRowSums', ($event.target as HTMLInputElement).checked)" />
              {{ t('analysisEdit.charts.showRowSums') }}
            </label>
          </template>
          <div v-if="chartType === 'HEAT_MAP'" class="edit-field ae-wide">
            <label :for="id('colorMapping')">{{ t('analysisEdit.charts.colorMapping') }}</label>
            <select :id="id('colorMapping')" :value="model.colorMapping ?? 'GREEN_YELLOW_RED'" @change="setModel('colorMapping', ($event.target as HTMLSelectElement).value)">
              <option v-for="m in COLOR_MAPPINGS" :key="m" :value="m">{{ label('analysisEdit.colorMappings', m) }}</option>
            </select>
          </div>
        </div>
      </section>

      <section v-if="typeEditor" class="ae-section dbe-type">
        <h3>{{ t('dashboardEdit.data.type', { type: typeLabel(widget.WidgetType) }) }}</h3>
        <component :is="typeEditor" :key="widget.uuid" :widget="widget" :source="ed.source.value!" :dashboard-id="dashboardId" />
      </section>
      <p v-else-if="!hasRows && !model" class="ae-hint">{{ t('dashboardEdit.data.noSettings') }}</p>
    </template>

    <RowPickerDialog v-if="picking" :present="present" @close="picking = false" @add="onAdd" />
  </div>
</template>
