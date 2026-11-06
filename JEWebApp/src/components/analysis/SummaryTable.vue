<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatNumber } from '../../analysis/format'
import type { ChartView } from '../../analysis/model'
import { stateText, type OptionContext } from '../../analysis/options'

const props = defineProps<{ chart: ChartView; ctx: OptionContext }>()
const { t, locale } = useI18n()

const hasSum = computed(() => props.chart.rows.some((r) => r.stats?.sum != null))
</script>

<template>
  <div class="scroll">
    <table class="summary">
      <caption class="visually-hidden">{{ t('analysis.summary.caption', { name: chart.name }) }}</caption>
      <thead>
        <tr>
          <th scope="col" class="name">{{ t('analysis.summary.name') }}</th>
          <th scope="col">{{ t('analysis.summary.min') }}</th>
          <th scope="col">{{ t('analysis.summary.max') }}</th>
          <th scope="col">{{ t('analysis.summary.avg') }}</th>
          <th v-if="hasSum" scope="col">{{ t('analysis.summary.sum') }}</th>
          <th scope="col" class="unit">{{ t('analysis.summary.unit') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in chart.rows" :key="row.key" :class="{ off: row.state !== 'OK' }">
          <th scope="row" class="name">
            <span class="swatch" :style="{ background: row.state === 'OK' ? row.color : 'var(--line)' }" aria-hidden="true" />
            {{ row.name }}
          </th>
          <template v-if="row.state === 'OK'">
            <td>{{ formatNumber(row.stats?.min, locale, row.decimals) }}</td>
            <td>{{ formatNumber(row.stats?.max, locale, row.decimals) }}</td>
            <td>{{ formatNumber(row.stats?.avg, locale, row.decimals) }}</td>
            <td v-if="hasSum">{{ formatNumber(row.stats?.sum, locale, row.decimals) }}</td>
          </template>
          <td v-else :colspan="hasSum ? 4 : 3" class="state" :class="{ error: row.state === 'ERROR' }">{{ stateText(row, ctx) }}</td>
          <td class="unit">{{ row.unit }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.scroll {
  overflow-x: auto;
}

.summary {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  font-variant-numeric: tabular-nums;
}

th,
td {
  padding: 0.4rem 0.6rem;
  text-align: right;
  white-space: nowrap;
  border-bottom: 1px solid var(--line);
}

thead th {
  font-weight: 600;
  font-size: 0.75rem;
  color: var(--muted);
  border-bottom-color: var(--blueberry);
}

th.name {
  text-align: left;
  font-weight: 600;
  white-space: normal;
  min-width: 9rem;
}

.unit {
  text-align: left;
  color: var(--muted);
}

.swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: 0.45rem;
  vertical-align: baseline;
}

tr.off th.name {
  color: var(--muted);
  font-weight: 400;
}

td.state {
  text-align: left;
  color: var(--muted);
  font-style: italic;
  white-space: normal;
}

td.state.error {
  color: var(--error);
  font-style: normal;
}

tbody tr:last-child th,
tbody tr:last-child td {
  border-bottom: none;
}
</style>
