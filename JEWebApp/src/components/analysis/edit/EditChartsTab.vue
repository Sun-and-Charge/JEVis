<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CHART_TYPES, COLOR_MAPPINGS, ORIENTATIONS, isTableType, isXyType, move, newChart, type Draft, type DraftChart, type Problem } from '../../../analysis/editor'
import { fieldHelpers } from '../../../analysis/editFields'

/* Tab "Diagramme" of the analysis editor (E3): add / remove / rename / order the charts and their settings. */

const props = defineProps<{ draft: Draft; problems: readonly Problem[]; uid: string }>()
/** "Datenreihen (n)" of a chart: the rows tab with that chart */
const emit = defineEmits<{ rows: [chartUid: string] }>()
const { t, te } = useI18n()
const f = fieldHelpers(props.uid, () => props.problems)
const msg = (path: string) => {
  const p = f.problem(path)
  return p ? t(p.key, p.params ?? {}) : ''
}

const open = ref<Set<string>>(new Set(props.draft.charts.length === 1 ? [props.draft.charts[0].uid] : []))
function toggle(chart: DraftChart) {
  const next = new Set(open.value)
  if (next.has(chart.uid)) next.delete(chart.uid)
  else next.add(chart.uid)
  open.value = next
}

const label = (group: string, value: string) => (te(`analysisEdit.${group}.${value}`) ? t(`analysisEdit.${group}.${value}`) : value)
const chartLabel = (chart: DraftChart) => chart.name.trim() || t('analysisEdit.charts.unnamed')
const hasProblem = (index: number) => props.problems.some((p) => p.path.startsWith(`charts.${index}.`) && !p.path.startsWith(`charts.${index}.rows.`))

function add() {
  const chart = newChart(props.draft.charts, t('analysisEdit.chartName', { n: props.draft.charts.length + 1 }))
  props.draft.charts.push(chart)
  open.value = new Set([...open.value, chart.uid])
}

function remove(index: number) {
  props.draft.charts.splice(index, 1)
}

/* height: empty = automatic (-1) */
const heightValue = (chart: DraftChart) => (chart.height === -1 ? '' : chart.height)
function setHeight(chart: DraftChart, value: string) {
  const v = value.trim()
  chart.height = v === '' ? -1 : Number.isFinite(Number(v)) ? Number(v) : ''
}
const showAxes = (chart: DraftChart) => isXyType(chart.type) || chart.type === 'BAR' || chart.type === 'BUBBLE'
const showColorMapping = (chart: DraftChart) => chart.type === 'HEAT_MAP' || isTableType(chart.type)
</script>

<template>
  <div>
    <p v-if="!draft.charts.length" class="ae-hint">{{ t('analysisEdit.charts.none') }}</p>
    <ul class="ae-cards">
      <li v-for="(chart, i) in draft.charts" :key="chart.uid" class="ae-card ae-chart" :class="{ 'is-open': open.has(chart.uid) }">
        <div class="ae-card-head">
          <button
            type="button"
            class="ae-card-toggle"
            :aria-expanded="open.has(chart.uid)"
            :aria-controls="`${uid}-chart-${chart.uid}`"
            :aria-label="t('analysisEdit.charts.toggle', { name: chartLabel(chart) })"
            @click="toggle(chart)"
          >
            <span class="ae-chevron" aria-hidden="true">›</span>
            <span class="ae-card-title">{{ chartLabel(chart) }}</span>
            <span class="ae-card-meta">{{ label('chartTypes', chart.type) }}</span>
            <span v-if="hasProblem(i)" class="ae-badge" aria-hidden="true">!</span>
          </button>
          <button type="button" class="ae-icon-btn" :disabled="i === 0" :aria-label="t('analysisEdit.charts.upLabel', { name: chartLabel(chart) })" :title="t('analysisEdit.charts.up')" @click="move(draft.charts, i, -1)">↑</button>
          <button
            type="button"
            class="ae-icon-btn"
            :disabled="i === draft.charts.length - 1"
            :aria-label="t('analysisEdit.charts.downLabel', { name: chartLabel(chart) })"
            :title="t('analysisEdit.charts.down')"
            @click="move(draft.charts, i, 1)"
          >
            ↓
          </button>
          <button type="button" class="ae-icon-btn is-danger ae-chart-remove" :aria-label="t('analysisEdit.charts.removeLabel', { name: chartLabel(chart) })" :title="t('analysisEdit.charts.remove')" @click="remove(i)">✕</button>
        </div>
        <div v-if="open.has(chart.uid)" :id="`${uid}-chart-${chart.uid}`" class="ae-card-body">
          <div class="ae-grid">
            <div class="edit-field ae-wide">
              <label :for="f.fid(`charts.${i}.name`)">{{ t('analysisEdit.charts.name') }}</label>
              <input :id="f.fid(`charts.${i}.name`)" v-model="chart.name" type="text" maxlength="200" />
            </div>
            <div class="edit-field">
              <label :for="f.fid(`charts.${i}.type`)">{{ t('analysisEdit.charts.type') }}</label>
              <select :id="f.fid(`charts.${i}.type`)" v-model="chart.type">
                <option v-for="type in CHART_TYPES" :key="type" :value="type">{{ label('chartTypes', type) }}</option>
                <option v-if="!(CHART_TYPES as readonly string[]).includes(chart.type)" :value="chart.type">{{ chart.type }}</option>
              </select>
            </div>
            <div v-if="isTableType(chart.type)" class="edit-field">
              <label :for="f.fid(`charts.${i}.orientation`)">{{ t('analysisEdit.charts.orientation') }}</label>
              <select :id="f.fid(`charts.${i}.orientation`)" v-model="chart.orientation">
                <option v-for="o in ORIENTATIONS" :key="o" :value="o">{{ label('orientations', o) }}</option>
              </select>
            </div>
            <div v-for="key in ['minFractionDigits', 'maxFractionDigits'] as const" :key="key" class="edit-field">
              <label :for="f.fid(`charts.${i}.${key}`)">{{ t('analysisEdit.charts.' + key) }}</label>
              <input
                :id="f.fid(`charts.${i}.${key}`)"
                v-model.number="chart[key]"
                type="number"
                min="0"
                max="20"
                step="1"
                inputmode="numeric"
                :aria-invalid="f.problem(`charts.${i}.${key}`) ? 'true' : undefined"
                :aria-describedby="f.describedBy(`charts.${i}.${key}`)"
              />
              <p v-if="f.problem(`charts.${i}.${key}`)" :id="f.errId(`charts.${i}.${key}`)" class="ae-error">{{ msg(`charts.${i}.${key}`) }}</p>
            </div>
            <label class="ae-check ae-wide">
              <input :checked="!!chart.fractionDigitsAuto" type="checkbox" @change="chart.fractionDigitsAuto = ($event.target as HTMLInputElement).checked" />
              {{ t('analysisEdit.charts.fractionDigitsAuto') }}
            </label>
            <div class="edit-field">
              <label :for="f.fid(`charts.${i}.height`)">{{ t('analysisEdit.charts.height') }}</label>
              <input
                :id="f.fid(`charts.${i}.height`)"
                :value="heightValue(chart)"
                type="number"
                min="50"
                step="10"
                inputmode="numeric"
                :placeholder="t('analysisEdit.charts.heightAuto')"
                :aria-invalid="f.problem(`charts.${i}.height`) ? 'true' : undefined"
                :aria-describedby="f.describedBy(`charts.${i}.height`)"
                @input="setHeight(chart, ($event.target as HTMLInputElement).value)"
              />
              <p v-if="f.problem(`charts.${i}.height`)" :id="f.errId(`charts.${i}.height`)" class="ae-error">{{ msg(`charts.${i}.height`) }}</p>
            </div>
            <div v-if="chart.type === 'BUBBLE'" class="edit-field">
              <label :for="f.fid(`charts.${i}.groupingInterval`)">{{ t('analysisEdit.charts.groupingInterval') }}</label>
              <input
                :id="f.fid(`charts.${i}.groupingInterval`)"
                v-model.number="chart.groupingInterval"
                type="number"
                min="0"
                step="any"
                inputmode="decimal"
                :aria-invalid="f.problem(`charts.${i}.groupingInterval`) ? 'true' : undefined"
                :aria-describedby="f.describedBy(`charts.${i}.groupingInterval`)"
              />
              <p v-if="f.problem(`charts.${i}.groupingInterval`)" :id="f.errId(`charts.${i}.groupingInterval`)" class="ae-error">{{ msg(`charts.${i}.groupingInterval`) }}</p>
            </div>
            <template v-if="showAxes(chart)">
              <div class="edit-field">
                <label :for="f.fid(`charts.${i}.xAxisTitle`)">{{ t('analysisEdit.charts.xAxisTitle') }}</label>
                <input :id="f.fid(`charts.${i}.xAxisTitle`)" v-model="chart.xAxisTitle" type="text" maxlength="200" />
              </div>
              <div class="edit-field">
                <label :for="f.fid(`charts.${i}.yAxisTitle`)">{{ t('analysisEdit.charts.yAxisTitle') }}</label>
                <input :id="f.fid(`charts.${i}.yAxisTitle`)" v-model="chart.yAxisTitle" type="text" maxlength="200" />
              </div>
              <label class="ae-check ae-wide">
                <input v-model="chart.fixYAxisToZero" type="checkbox" />
                {{ t('analysisEdit.charts.fixYAxisToZero') }}
              </label>
            </template>
            <div v-if="showColorMapping(chart)" class="edit-field ae-wide">
              <label :for="f.fid(`charts.${i}.colorMapping`)">{{ t('analysisEdit.charts.colorMapping') }}</label>
              <select :id="f.fid(`charts.${i}.colorMapping`)" v-model="chart.colorMapping">
                <option v-for="m in COLOR_MAPPINGS" :key="m" :value="m">{{ label('colorMappings', m) }}</option>
                <option v-if="!(COLOR_MAPPINGS as readonly string[]).includes(chart.colorMapping)" :value="chart.colorMapping">{{ chart.colorMapping }}</option>
              </select>
            </div>
            <template v-if="isTableType(chart.type)">
              <label v-for="key in ['showRowSums', 'showColumnSums', 'coloringEnabled', 'filterEnabled'] as const" :key="key" class="ae-check ae-wide">
                <input v-model="chart[key]" type="checkbox" :class="'ae-' + key" />
                {{ t('analysisEdit.charts.' + key) }}
              </label>
            </template>
            <div v-for="key in ['dayStart', 'dayEnd'] as const" :key="key" class="edit-field">
              <label :for="f.fid(`charts.${i}.${key}`)">{{ t('analysisEdit.charts.' + key) }}</label>
              <input
                :id="f.fid(`charts.${i}.${key}`)"
                v-model="chart[key]"
                type="time"
                step="1"
                :aria-invalid="f.problem(`charts.${i}.${key}`) ? 'true' : undefined"
                :aria-describedby="f.describedBy(`charts.${i}.${key}`)"
              />
              <p v-if="f.problem(`charts.${i}.${key}`)" :id="f.errId(`charts.${i}.${key}`)" class="ae-error">{{ msg(`charts.${i}.${key}`) }}</p>
            </div>
          </div>
          <button type="button" class="edit-btn is-small ae-chart-rows" :aria-label="t('analysisEdit.charts.rowsLabel', { name: chartLabel(chart) })" @click="emit('rows', chart.uid)">
            {{ t('analysisEdit.charts.rows', { n: chart.rows.length }) }} →
          </button>
          <p v-if="f.problem(`charts.${i}.id`)" class="ae-error">{{ msg(`charts.${i}.id`) }}</p>
        </div>
      </li>
    </ul>
    <button type="button" class="edit-btn ae-add ae-add-chart" @click="add">+ {{ t('analysisEdit.charts.add') }}</button>
  </div>
</template>
