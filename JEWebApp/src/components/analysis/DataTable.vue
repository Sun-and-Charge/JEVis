<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime, formatValue } from '../../analysis/format'
import type { ChartView } from '../../analysis/model'
import { stateText, type OptionContext } from '../../analysis/options'
import { buildTimeTable } from '../../analysis/table'

/** TABLE, TABLE_V (timestamps as lines) and TABLE_H (timestamps as columns). */
const props = defineProps<{ chart: ChartView; ctx: OptionContext }>()
const { t, locale } = useI18n()

/** more lines would make the page slow; the CSV export has all values */
const LIMIT = 1000

const table = computed(() => buildTimeTable(props.chart.rows))
const shown = computed(() => Math.min(table.value.timestamps.length, LIMIT))
const indexes = computed(() => Array.from({ length: shown.value }, (_, i) => i))
const header = (name: string, unit: string) => (unit ? `${name} [${unit}]` : name)
const cell = (r: number, i: number) => formatValue(table.value.values[r][i], locale.value, props.chart.rows[r].decimals)
const note = (r: number, i: number) => table.value.notes[r][i] ?? undefined
const time = (i: number) => formatDateTime(table.value.timestamps[i], locale.value)
const rowsWithState = computed(() => props.chart.rows.filter((r) => r.state !== 'OK'))
</script>

<template>
  <div>
    <p v-if="!table.timestamps.length" class="muted empty">{{ t('analysis.table.empty') }}</p>
    <div v-else class="scroll" tabindex="0" :aria-label="t('analysis.table.caption', { name: chart.name })">
      <table v-if="chart.orientation === 'vertical'" class="data">
        <caption class="visually-hidden">{{ t('analysis.table.caption', { name: chart.name }) }}</caption>
        <thead>
          <tr>
            <th scope="col" class="time">{{ t('analysis.table.time') }}</th>
            <th v-for="row in chart.rows" :key="row.key" scope="col">
              <span class="swatch" :style="{ background: row.color }" aria-hidden="true" />{{ header(row.name, row.unit) }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in indexes" :key="i">
            <th scope="row" class="time">{{ time(i) }}</th>
            <td v-for="(row, r) in chart.rows" :key="row.key" :title="note(r, i)" :class="{ noted: note(r, i) }">{{ cell(r, i) }}</td>
          </tr>
        </tbody>
      </table>
      <table v-else class="data">
        <caption class="visually-hidden">{{ t('analysis.table.caption', { name: chart.name }) }}</caption>
        <thead>
          <tr>
            <th scope="col" class="time">{{ t('analysis.summary.name') }}</th>
            <th v-for="i in indexes" :key="i" scope="col">{{ time(i) }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, r) in chart.rows" :key="row.key">
            <th scope="row" class="time"><span class="swatch" :style="{ background: row.color }" aria-hidden="true" />{{ header(row.name, row.unit) }}</th>
            <td v-for="i in indexes" :key="i" :title="note(r, i)" :class="{ noted: note(r, i) }">{{ cell(r, i) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="table.timestamps.length > LIMIT" class="muted hint">{{ t('analysis.table.limited', { shown: LIMIT, total: table.timestamps.length }) }}</p>
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
