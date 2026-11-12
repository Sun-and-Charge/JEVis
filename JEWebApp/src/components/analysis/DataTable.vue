<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime, formatNumber, formatValue } from '../../analysis/format'
import { colorScale } from '../../analysis/heatmap'
import type { ChartView } from '../../analysis/model'
import { stateText, type OptionContext } from '../../analysis/options'
import { buildTimeTable, cellColors, cellMatches, filterTimestamps, rowTotalOf, textOn, timestampSum } from '../../analysis/table'

/*
 * TABLE, TABLE_V (timestamps as lines) and TABLE_H (timestamps as columns). E3: the table options of the Control
 * Center – sums of the rows ("Reihensummen"), sums per timestamp ("Spaltensummen", column "Summe"), colouring of the
 * values by the colour mapping and filter fields per column (`filterTexts`, keyed by the row name).
 */
const props = defineProps<{ chart: ChartView; ctx: OptionContext }>()
/** E3: a filter text was changed (edit mode stores it in the analysis, as the Control Center) */
const emit = defineEmits<{ filter: [column: string, text: string] }>()
const { t, locale } = useI18n()

/** more lines would make the page slow; the CSV export has all values */
const LIMIT = 1000

const table = computed(() => buildTimeTable(props.chart.rows))
const header = (name: string, unit: string) => (unit ? `${name} [${unit}]` : name)
const cell = (r: number, i: number) => formatValue(table.value.values[r][i], locale.value, props.chart.rows[r].decimals)
const note = (r: number, i: number) => table.value.notes[r][i] ?? undefined
const time = (i: number) => formatDateTime(table.value.timestamps[i], locale.value)
const rowsWithState = computed(() => props.chart.rows.filter((r) => r.state !== 'OK'))

/* filter fields: per row column (vertical) or the name column (horizontal, local only) */
const filters = ref<Record<string, string>>({ ...(props.chart.filterTexts ?? {}) })
watch(
  () => props.chart.filterTexts,
  (texts) => (filters.value = { ...(texts ?? {}) }),
)
const nameFilter = ref('')
const filtering = computed(() => !!props.chart.filterEnabled)
function setFilter(column: string, text: string) {
  filters.value = { ...filters.value, [column]: text }
  emit('filter', column, text)
}

const vertical = computed(() => props.chart.orientation === 'vertical')
/** timestamps after the filter (vertical tables) */
const matching = computed(() => {
  const all = table.value.timestamps.map((_, i) => i)
  if (!filtering.value || !vertical.value) return all
  return filterTimestamps(
    table.value,
    props.chart.rows.map((r) => filters.value[r.name] ?? ''),
    cell,
  )
})
const indexes = computed(() => matching.value.slice(0, LIMIT))
/** rows shown (horizontal tables: name filter) */
const shownRows = computed(() =>
  props.chart.rows
    .map((row, r) => ({ row, r }))
    .filter(({ row }) => vertical.value || !filtering.value || cellMatches(row.name, row.name, nameFilter.value)),
)

const totals = computed(() => props.chart.rows.map((row, r) => rowTotalOf(row, table.value.values[r], matching.value)))
const total = (r: number) => formatValue(totals.value[r], locale.value, props.chart.rows[r].decimals)
const lineSum = (i: number) => formatNumber(timestampSum(table.value, i), locale.value, props.chart.decimals)
const totalOfTotals = computed(() => {
  const values = totals.value.filter((v): v is number => v != null)
  return values.length ? formatNumber(values.reduce((a, b) => a + b, 0), locale.value, props.chart.decimals) : ''
})
/** unit of the sum column: the first row's (as the Control Center) */
const sumUnit = computed(() => props.chart.rows.find((r) => r.unit)?.unit ?? '')

const colors = computed(() => (props.chart.coloringEnabled ? cellColors(table.value, matching.value, colorScale(props.chart.colorMapping)) : null))
function cellStyle(r: number, i: number) {
  const bg = colors.value?.[r]?.[i]
  return bg ? { background: bg, color: textOn(bg) } : undefined
}
const filterLabel = (name: string) => t('analysisEdit.table.filterLabel', { name })
</script>

<template>
  <div>
    <p v-if="!table.timestamps.length" class="muted empty">{{ t('analysis.table.empty') }}</p>
    <div v-else class="scroll" tabindex="0" :aria-label="t('analysis.table.caption', { name: chart.name })">
      <table v-if="vertical" class="data">
        <caption class="visually-hidden">{{ t('analysis.table.caption', { name: chart.name }) }}</caption>
        <thead>
          <tr>
            <th scope="col" class="time">{{ t('analysis.table.time') }}</th>
            <th v-for="row in chart.rows" :key="row.key" scope="col">
              <span class="swatch" :style="{ background: row.color }" aria-hidden="true" />{{ header(row.name, row.unit) }}
              <input
                v-if="filtering"
                class="col-filter"
                type="search"
                :value="filters[row.name] ?? ''"
                :placeholder="t('analysisEdit.table.filter')"
                :aria-label="filterLabel(row.name)"
                @input="setFilter(row.name, ($event.target as HTMLInputElement).value)"
              />
            </th>
            <th v-if="chart.showColumnSums" scope="col" class="sum">{{ header(t('analysisEdit.table.sum'), sumUnit) }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in indexes" :key="i">
            <th scope="row" class="time">{{ time(i) }}</th>
            <td v-for="(row, r) in chart.rows" :key="row.key" :title="note(r, i)" :class="{ noted: note(r, i) }" :style="cellStyle(r, i)">{{ cell(r, i) }}</td>
            <td v-if="chart.showColumnSums" class="sum">{{ lineSum(i) }}</td>
          </tr>
        </tbody>
        <tfoot v-if="chart.showRowSums">
          <tr class="sum">
            <th scope="row" class="time">{{ t('analysisEdit.table.sum') }}</th>
            <td v-for="(row, r) in chart.rows" :key="row.key">{{ total(r) }}</td>
            <td v-if="chart.showColumnSums">{{ totalOfTotals }}</td>
          </tr>
        </tfoot>
      </table>
      <table v-else class="data">
        <caption class="visually-hidden">{{ t('analysis.table.caption', { name: chart.name }) }}</caption>
        <thead>
          <tr>
            <th scope="col" class="time">
              {{ t('analysis.summary.name') }}
              <input
                v-if="filtering"
                v-model="nameFilter"
                class="col-filter"
                type="search"
                :placeholder="t('analysisEdit.table.filter')"
                :aria-label="filterLabel(t('analysis.summary.name'))"
              />
            </th>
            <th v-for="i in indexes" :key="i" scope="col">{{ time(i) }}</th>
            <th v-if="chart.showRowSums" scope="col" class="sum">{{ t('analysisEdit.table.sum') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="{ row, r } in shownRows" :key="row.key">
            <th scope="row" class="time"><span class="swatch" :style="{ background: row.color }" aria-hidden="true" />{{ header(row.name, row.unit) }}</th>
            <td v-for="i in indexes" :key="i" :title="note(r, i)" :class="{ noted: note(r, i) }" :style="cellStyle(r, i)">{{ cell(r, i) }}</td>
            <td v-if="chart.showRowSums" class="sum">{{ total(r) }}</td>
          </tr>
        </tbody>
        <tfoot v-if="chart.showColumnSums">
          <tr class="sum">
            <th scope="row" class="time">{{ header(t('analysisEdit.table.sum'), sumUnit) }}</th>
            <td v-for="i in indexes" :key="i">{{ lineSum(i) }}</td>
            <td v-if="chart.showRowSums">{{ totalOfTotals }}</td>
          </tr>
        </tfoot>
      </table>
    </div>
    <p v-if="matching.length > LIMIT" class="muted hint">{{ t('analysis.table.limited', { shown: LIMIT, total: matching.length }) }}</p>
    <p v-if="filtering && vertical && matching.length < table.timestamps.length" class="muted hint" role="status">
      {{ t('analysisEdit.table.filtered', { shown: matching.length, total: table.timestamps.length }) }}
    </p>
    <ul v-if="rowsWithState.length" class="states">
      <li v-for="row in rowsWithState" :key="row.key" :class="{ error: row.state === 'ERROR' }">{{ row.name }}: {{ stateText(row, ctx) }}</li>
    </ul>
  </div>
</template>

<style scoped>
.scroll {
  overflow: auto;
  max-height: var(--chart-h, 420px);
  border: 1px solid var(--line);
  border-radius: 4px;
}

.data {
  border-collapse: separate;
  border-spacing: 0;
  font-size: 0.85rem;
  font-variant-numeric: tabular-nums;
  min-width: 100%;
}

th,
td {
  padding: 0.35rem 0.6rem;
  text-align: right;
  white-space: nowrap;
  border-bottom: 1px solid var(--line);
}

thead th {
  position: sticky;
  top: 0;
  background: var(--surface);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
  border-bottom-color: var(--blueberry);
  z-index: 1;
}

th.time {
  position: sticky;
  left: 0;
  text-align: left;
  background: var(--surface);
  font-weight: 600;
}

thead th.time {
  z-index: 2;
}

tbody th.time {
  color: var(--ink);
}

.sum,
tfoot th,
tfoot td {
  font-weight: 600;
}

tfoot td,
tfoot th {
  position: sticky;
  bottom: 0;
  background: var(--surface);
  border-top: 1px solid var(--blueberry);
}

.col-filter {
  display: block;
  width: 100%;
  min-width: 6rem;
  margin-top: 0.3rem;
  padding: 0.2rem 0.4rem;
  font: inherit;
  font-weight: 400;
  color: var(--ink);
  border: 1px solid var(--line);
  border-radius: 4px;
}

.col-filter:focus-visible {
  outline: 3px solid var(--sun);
  outline-offset: 1px;
}

td.noted {
  text-decoration: underline dotted var(--muted);
}

.swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: 0.4rem;
}

.empty,
.hint {
  font-size: 0.85rem;
  margin: 0.5rem 0 0;
}

.states {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
  font-size: 0.85rem;
  color: var(--muted);
}

.states .error {
  color: var(--error);
}
</style>
