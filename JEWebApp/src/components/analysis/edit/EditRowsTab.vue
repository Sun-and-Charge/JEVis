<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { UnitOption } from '../../../api/analysis'
import {
  BUBBLE_TYPES,
  ROW_TYPES,
  applyColorToAll,
  applyUnitToAll,
  isXyType,
  move,
  sameUnit,
  setUnit,
  toLocalInput,
  validColor,
  type Draft,
  type DraftRow,
  type Problem,
} from '../../../analysis/editor'
import { fieldHelpers, type RowUnits } from '../../../analysis/editFields'
import { isoWithOffset, fromDateTimeInput } from '../../../edit/edit'

/*
 * Tab "Datenreihen" of the analysis editor (E3): the rows of one chart – name, colour, unit, axis, chart type,
 * bubble type, decimals, own time frame, calculation, CSS; colour and unit "für alle übernehmen". Rows the user may
 * not read are listed as "Keine Berechtigung" and stay unchanged.
 */

const props = defineProps<{ draft: Draft; problems: readonly Problem[]; uid: string; units: RowUnits; chartUid: string | null }>()
const emit = defineEmits<{ 'update:chartUid': [uid: string]; pick: [] }>()
const { t, te } = useI18n()
const f = fieldHelpers(props.uid, () => props.problems)
const msg = (path: string) => {
  const p = f.problem(path)
  return p ? t(p.key, p.params ?? {}) : ''
}
const label = (group: string, value: string) => (te(`analysisEdit.${group}.${value}`) ? t(`analysisEdit.${group}.${value}`) : value)

const chartIndex = computed(() => {
  const i = props.draft.charts.findIndex((c) => c.uid === props.chartUid)
  return i >= 0 ? i : props.draft.charts.length ? 0 : -1
})
const chart = computed(() => (chartIndex.value >= 0 ? props.draft.charts[chartIndex.value] : null))
const chartLabel = (i: number) => props.draft.charts[i].name.trim() || t('analysisEdit.charts.unnamed')
const info = ref<string | null>(null)
watch(chart, () => (info.value = null))

const open = ref<Set<string>>(new Set())
function toggle(row: DraftRow) {
  const next = new Set(open.value)
  if (next.has(row.uid)) next.delete(row.uid)
  else {
    next.add(row.uid)
    void props.units.load(row)
  }
  open.value = next
}
const rowLabel = (row: DraftRow) => (row.readable ? row.name.trim() || row.objectName || String(row.objectId) : t('analysisEdit.rows.noPermission'))
const rowUnitText = (row: DraftRow) => row.unitLabel ?? row.unit ?? ''
const path = (ri: number, key: string) => `charts.${chartIndex.value}.rows.${ri}.${key}`
const hasProblem = (ri: number) => props.problems.some((p) => p.path.startsWith(`charts.${chartIndex.value}.rows.${ri}.`))

/* unit select: index of the offered unit, 'stored' (a stored unit not offered), '' (no unit stored) */
function unitOptions(row: DraftRow): UnitOption[] {
  return props.units.unitsOf(row) ?? []
}
function unitValue(row: DraftRow): string {
  const i = unitOptions(row).findIndex((u) => sameUnit(row, u))
  if (i >= 0) return String(i)
  return row.unitLabel != null || row.unitFormula != null || row.unitPrefix != null ? 'stored' : ''
}
function setUnitValue(row: DraftRow, value: string) {
  const unit = unitOptions(row)[Number(value)]
  if (unit && value !== '') setUnit(row, unit)
}
const unitText = (u: UnitOption) => u.label || u.formula || '–'

async function unitForAll(row: DraftRow) {
  const c = chart.value
  if (!c) return
  info.value = null
  await Promise.all(c.rows.filter((r) => r.readable).map((r) => props.units.load(r)))
  const unit: UnitOption = { prefix: row.unitPrefix, formula: row.unitFormula, label: row.unitLabel }
  const skipped = applyUnitToAll(c, unit, (r) => props.units.unitsOf(r))
  if (skipped) info.value = t('analysisEdit.rows.unitSkipped', skipped)
}

function colorForAll(row: DraftRow) {
  if (chart.value && validColor(row.color)) applyColorToAll(chart.value, row.color)
}

/* decimals: empty = like the chart (-1) */
const decimalsValue = (row: DraftRow) => (row.decimalDigits === -1 ? '' : row.decimalDigits)
function setDecimals(row: DraftRow, value: string) {
  const v = value.trim()
  row.decimalDigits = v === '' ? -1 : Number.isFinite(Number(v)) ? Number(v) : ''
}

/* own time frame: datetime-local in the browser's zone, stored as ISO with offset */
function setInterval(row: DraftRow, which: 'start' | 'end', value: string) {
  const ms = fromDateTimeInput(value)
  row.interval[which] = ms == null ? null : isoWithOffset(ms)
}
function toggleInterval(row: DraftRow, enabled: boolean) {
  row.interval.enabled = enabled
  if (enabled && !row.interval.start && !row.interval.end) {
    const now = new Date()
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
    row.interval.start = isoWithOffset(start)
    row.interval.end = isoWithOffset(Math.floor(now.getTime() / 60000) * 60000)
  }
}

function remove(index: number) {
  chart.value?.rows.splice(index, 1)
}

const showBubble = computed(() => chart.value?.type === 'BUBBLE')
const showRowType = computed(() => !!chart.value && isXyType(chart.value.type))
</script>

<template>
  <div>
    <p v-if="!draft.charts.length" class="ae-hint">{{ t('analysisEdit.rows.noCharts') }}</p>
    <template v-else>
      <div class="edit-field">
        <label :for="uid + '-rows-chart'">{{ t('analysisEdit.rows.chart') }}</label>
        <select :id="uid + '-rows-chart'" :value="chart?.uid" class="ae-rows-chart" @change="emit('update:chartUid', ($event.target as HTMLSelectElement).value)">
          <option v-for="(c, i) in draft.charts" :key="c.uid" :value="c.uid">{{ chartLabel(i) }} ({{ c.rows.length }})</option>
        </select>
      </div>

      <template v-if="chart">
        <p v-if="!chart.rows.length" class="ae-hint">{{ t('analysisEdit.rows.none') }}</p>
        <p v-if="info" class="ae-hint" role="status">{{ info }}</p>
        <ul class="ae-cards">
          <li
            v-for="(row, ri) in chart.rows"
            :key="row.uid"
            class="ae-card ae-row"
            :class="{ 'is-open': open.has(row.uid), 'is-locked': !row.readable }"
          >
            <!-- not readable: kept as it is -->
            <template v-if="!row.readable">
              <div class="ae-card-head">
                <span class="ae-swatch" style="background: var(--line)" aria-hidden="true" />
                <span class="ae-card-title ae-locked-title">{{ t('analysisEdit.rows.noPermission') }}</span>
              </div>
              <p class="ae-locked">{{ t('analysisEdit.rows.noPermissionHint', { id: row.objectId }) }}</p>
            </template>
            <template v-else>
              <div class="ae-card-head">
                <button
                  type="button"
                  class="ae-card-toggle"
                  :aria-expanded="open.has(row.uid)"
                  :aria-controls="`${uid}-row-${row.uid}`"
                  :aria-label="t('analysisEdit.rows.toggle', { name: rowLabel(row) })"
                  @click="toggle(row)"
                >
                  <span class="ae-chevron" aria-hidden="true">›</span>
                  <span class="ae-swatch" :style="{ background: validColor(row.color) ? row.color : 'transparent' }" aria-hidden="true" />
                  <span class="ae-card-title">{{ rowLabel(row) }}</span>
                  <span class="ae-card-meta">{{ rowUnitText(row) }}</span>
                  <span v-if="hasProblem(ri)" class="ae-badge" aria-hidden="true">!</span>
                </button>
                <button type="button" class="ae-icon-btn" :disabled="ri === 0" :aria-label="t('analysisEdit.rows.upLabel', { name: rowLabel(row) })" :title="t('analysisEdit.rows.up')" @click="move(chart.rows, ri, -1)">↑</button>
                <button
                  type="button"
                  class="ae-icon-btn"
                  :disabled="ri === chart.rows.length - 1"
                  :aria-label="t('analysisEdit.rows.downLabel', { name: rowLabel(row) })"
                  :title="t('analysisEdit.rows.down')"
                  @click="move(chart.rows, ri, 1)"
                >
                  ↓
                </button>
                <button type="button" class="ae-icon-btn is-danger ae-row-remove" :aria-label="t('analysisEdit.rows.removeLabel', { name: rowLabel(row) })" :title="t('analysisEdit.rows.remove')" @click="remove(ri)">✕</button>
              </div>
              <div v-if="open.has(row.uid)" :id="`${uid}-row-${row.uid}`" class="ae-card-body ae-grid">
                <div class="edit-field ae-wide">
                  <label :for="f.fid(path(ri, 'name'))">{{ t('analysisEdit.rows.name') }}</label>
                  <input :id="f.fid(path(ri, 'name'))" v-model="row.name" type="text" maxlength="200" :placeholder="row.objectName" />
                </div>
                <div class="edit-field ae-wide">
                  <label :for="f.fid(path(ri, 'color'))">{{ t('analysisEdit.rows.color') }}</label>
                  <div class="ae-color">
                    <input
                      :value="validColor(row.color) ? row.color.slice(0, 7) : '#000000'"
                      type="color"
                      tabindex="-1"
                      :aria-label="t('analysisEdit.rows.color')"
                      @input="row.color = ($event.target as HTMLInputElement).value"
                    />
                    <input
                      :id="f.fid(path(ri, 'color'))"
                      v-model.trim="row.color"
                      type="text"
                      maxlength="9"
                      spellcheck="false"
                      :aria-invalid="f.problem(path(ri, 'color')) ? 'true' : undefined"
                      :aria-describedby="f.describedBy(path(ri, 'color'))"
                    />
                    <button
                      type="button"
                      class="edit-btn is-small ae-color-all"
                      :disabled="!validColor(row.color)"
                      :aria-label="t('analysisEdit.rows.colorForAll', { color: row.color })"
                      :title="t('analysisEdit.rows.forAll')"
                      @click="colorForAll(row)"
                    >
                      {{ t('analysisEdit.rows.forAll') }}
                    </button>
                  </div>
                  <p v-if="f.problem(path(ri, 'color'))" :id="f.errId(path(ri, 'color'))" class="ae-error">{{ msg(path(ri, 'color')) }}</p>
                </div>
                <div class="edit-field ae-wide">
                  <label :for="f.fid(path(ri, 'unit'))">{{ t('analysisEdit.rows.unit') }}</label>
                  <div class="ae-inline">
                    <select :id="f.fid(path(ri, 'unit'))" :value="unitValue(row)" class="ae-unit" @change="setUnitValue(row, ($event.target as HTMLSelectElement).value)">
                      <option v-if="unitValue(row) === ''" value="">{{ row.unit ? row.unit : t('analysisEdit.rows.unitDefault') }}</option>
                      <option v-if="unitValue(row) === 'stored'" value="stored">{{ t('analysisEdit.rows.unitStored', { unit: row.unitLabel ?? row.unitFormula ?? '' }) }}</option>
                      <option v-for="(u, ui) in unitOptions(row)" :key="ui" :value="String(ui)">{{ unitText(u) }}</option>
                    </select>
                    <button
                      type="button"
                      class="edit-btn is-small ae-unit-all"
                      :disabled="unitValue(row) === ''"
                      :aria-label="t('analysisEdit.rows.unitForAll', { unit: rowUnitText(row) })"
                      :title="t('analysisEdit.rows.forAll')"
                      @click="unitForAll(row)"
                    >
                      {{ t('analysisEdit.rows.forAll') }}
                    </button>
                  </div>
                  <p v-if="units.state(row) === 'loading'" class="ae-hint" role="status">{{ t('analysisEdit.rows.unitLoading') }}</p>
                  <p v-else-if="units.state(row) === 'error'" class="ae-error">{{ t('analysisEdit.rows.unitError') }}</p>
                </div>
                <div class="edit-field">
                  <label :for="f.fid(path(ri, 'axis'))">{{ t('analysisEdit.rows.axis') }}</label>
                  <select :id="f.fid(path(ri, 'axis'))" v-model.number="row.axis">
                    <option :value="0">{{ t('analysisEdit.axes.0') }}</option>
                    <option :value="1">{{ t('analysisEdit.axes.1') }}</option>
                  </select>
                </div>
                <div class="edit-field">
                  <label :for="f.fid(path(ri, 'decimalDigits'))">{{ t('analysisEdit.rows.decimalDigits') }}</label>
                  <input
                    :id="f.fid(path(ri, 'decimalDigits'))"
                    :value="decimalsValue(row)"
                    type="number"
                    min="0"
                    max="20"
                    step="1"
                    inputmode="numeric"
                    :placeholder="t('analysisEdit.rows.decimalAuto')"
                    :aria-invalid="f.problem(path(ri, 'decimalDigits')) ? 'true' : undefined"
                    :aria-describedby="f.describedBy(path(ri, 'decimalDigits'))"
                    @input="setDecimals(row, ($event.target as HTMLInputElement).value)"
                  />
                  <p v-if="f.problem(path(ri, 'decimalDigits'))" :id="f.errId(path(ri, 'decimalDigits'))" class="ae-error">{{ msg(path(ri, 'decimalDigits')) }}</p>
                </div>
                <div v-if="showRowType || row.chartType !== 'DEFAULT'" class="edit-field">
                  <label :for="f.fid(path(ri, 'chartType'))">{{ t('analysisEdit.rows.chartType') }}</label>
                  <select :id="f.fid(path(ri, 'chartType'))" v-model="row.chartType">
                    <option v-for="type in ROW_TYPES" :key="type" :value="type">{{ label('chartTypes', type) }}</option>
                    <option v-if="!(ROW_TYPES as readonly string[]).includes(row.chartType)" :value="row.chartType">{{ row.chartType }}</option>
                  </select>
                </div>
                <div v-if="showBubble || row.bubbleType !== 'NONE'" class="edit-field">
                  <label :for="f.fid(path(ri, 'bubbleType'))">{{ t('analysisEdit.rows.bubbleType') }}</label>
                  <select :id="f.fid(path(ri, 'bubbleType'))" v-model="row.bubbleType">
                    <option v-for="b in BUBBLE_TYPES" :key="b" :value="b">{{ label('bubbleTypes', b) }}</option>
                  </select>
                </div>
                <label class="ae-check ae-wide">
                  <input v-model="row.calculation" type="checkbox" class="ae-calculation" />
                  {{ t('analysisEdit.rows.calculation') }}
                </label>
                <label class="ae-check ae-wide">
                  <input :checked="row.interval.enabled" type="checkbox" class="ae-interval" @change="toggleInterval(row, ($event.target as HTMLInputElement).checked)" />
                  {{ t('analysisEdit.rows.interval') }}
                </label>
                <template v-if="row.interval.enabled">
                  <div class="edit-field">
                    <label :for="f.fid(path(ri, 'interval')) + '-start'">{{ t('analysisEdit.rows.start') }}</label>
                    <input
                      :id="f.fid(path(ri, 'interval')) + '-start'"
                      :value="toLocalInput(row.interval.start)"
                      type="datetime-local"
                      :aria-invalid="f.problem(path(ri, 'interval')) ? 'true' : undefined"
                      :aria-describedby="f.describedBy(path(ri, 'interval'))"
                      @change="setInterval(row, 'start', ($event.target as HTMLInputElement).value)"
                    />
                  </div>
                  <div class="edit-field">
                    <label :for="f.fid(path(ri, 'interval')) + '-end'">{{ t('analysisEdit.rows.end') }}</label>
                    <input
                      :id="f.fid(path(ri, 'interval')) + '-end'"
                      :value="toLocalInput(row.interval.end)"
                      type="datetime-local"
                      :aria-invalid="f.problem(path(ri, 'interval')) ? 'true' : undefined"
                      :aria-describedby="f.describedBy(path(ri, 'interval'))"
                      @change="setInterval(row, 'end', ($event.target as HTMLInputElement).value)"
                    />
                  </div>
                  <p v-if="f.problem(path(ri, 'interval'))" :id="f.errId(path(ri, 'interval'))" class="ae-error ae-wide">{{ msg(path(ri, 'interval')) }}</p>
                </template>
                <div class="edit-field ae-wide">
                  <label :for="f.fid(path(ri, 'css'))">{{ t('analysisEdit.rows.css') }}</label>
                  <input :id="f.fid(path(ri, 'css'))" v-model="row.css" type="text" maxlength="500" spellcheck="false" />
                </div>
              </div>
            </template>
          </li>
        </ul>
        <button type="button" class="edit-btn ae-add ae-add-rows" @click="emit('pick')">+ {{ t('analysisEdit.rows.add') }}</button>
      </template>
    </template>
  </div>
</template>
