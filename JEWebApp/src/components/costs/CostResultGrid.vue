<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CostCell, CostResult } from '../../api/costs'
import { analysisRange, cellResultText, gridLayout, type PlacedCell } from '../../costs/costs'
import CostRowChart from './CostRowChart.vue'

/*
 * The output grid of the template exactly as placed in the Control Center (row/column/rowSpan/columnSpan): name
 * left, result right with unit, bold flags, tooltip, separators and labels. The cells are in reading order in the
 * DOM, so on phone width the grid simply becomes one column. Cells with an analysis link open a small chart of
 * their input row for the result's period (at most 31 days).
 */

const props = defineProps<{ result: CostResult; idPrefix: string }>()
const { t, locale } = useI18n()

const layout = computed(() => gridLayout(props.result))
const range = computed(() => analysisRange(props.result.from, props.result.to))

const open = ref<string | null>(null)
const openCell = computed(() => layout.value.cells.find((p) => p.key === open.value) ?? null)
watch(
  () => props.result,
  () => {
    // keep the chart only if the same cell still exists with an analysis link and the period allows it
    if (!openCell.value?.cell.analysis || !range.value) open.value = null
  },
)

function toggle(p: PlacedCell) {
  if (!range.value) return
  open.value = open.value === p.key ? null : p.key
}

const cellStyle = (p: PlacedCell) => ({ gridRow: p.gridRow, gridColumn: p.gridColumn })
const resultText = (cell: CostCell) => cellResultText(cell, locale.value)
/** the unit after a no-break space, so it stays with the number */
const unitText = (cell: CostCell) => ' ' +(cell.unit ?? '')
const showUnit = (cell: CostCell) => !!cell.unit && !(cell.kind === 'value' && cell.value == null)
const hintId = computed(() => props.idPrefix + '-toolong')
const hasAnalysis = computed(() => layout.value.cells.some((p) => p.cell.analysis))
</script>

<template>
  <div class="cost-grid-wrap">
    <p v-if="!layout.cells.length" class="muted">{{ t('costs.result.empty') }}</p>
    <div v-else class="cost-grid" :style="{ gridTemplateColumns: `repeat(${layout.columns}, minmax(0, 1fr))` }">
      <template v-for="p in layout.cells" :key="p.key">
        <div
          v-if="p.cell.kind === 'separator'"
          class="cost-sep"
          :class="'is-' + p.orientation"
          role="separator"
          :aria-orientation="p.orientation ?? undefined"
          :style="cellStyle(p)"
        />
        <div
          v-else-if="p.cell.kind === 'label'"
          class="cost-label"
          :class="{ 'is-bold': p.cell.nameBold }"
          role="heading"
          aria-level="4"
          :title="p.cell.tooltip || undefined"
          :style="cellStyle(p)"
        >
          {{ p.cell.name }}
        </div>
        <div
          v-else
          class="cost-cell"
          :class="['is-' + p.cell.kind, { 'is-open': open === p.key }]"
          :title="p.cell.tooltip || undefined"
          :style="cellStyle(p)"
        >
          <span class="cost-name" :class="{ 'is-bold': p.cell.nameBold }">{{ p.cell.name }}</span>
          <span class="cost-amount" :class="{ 'is-bold': p.cell.resultBold, 'is-missing': resultText(p.cell) === '–', 'is-text': p.cell.kind === 'text' }">
            <span class="cost-number">{{ resultText(p.cell) }}</span>
            <span v-if="showUnit(p.cell)" class="cost-unit">{{ unitText(p.cell) }}</span>
          </span>
          <button
            v-if="p.cell.analysis"
            type="button"
            class="cost-chart-btn"
            :disabled="!range"
            :aria-expanded="range ? (open === p.key ? 'true' : 'false') : undefined"
            :aria-controls="range && open === p.key ? idPrefix + '-chart' : undefined"
            :aria-describedby="range ? undefined : hintId"
            :aria-label="t('costs.chart.show', { name: p.cell.name || '' })"
            :title="range ? t('costs.chart.show', { name: p.cell.name || '' }) : t('costs.chart.tooLong')"
            @click="toggle(p)"
          >
            <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M2 13h12M3 10l3-3 3 2 4-5" /></svg>
          </button>
        </div>
      </template>
    </div>
    <p v-if="hasAnalysis && !range" :id="hintId" class="muted rep-hint cost-chart-hint">{{ t('costs.chart.tooLong') }}</p>

    <CostRowChart
      v-if="openCell?.cell.analysis && range"
      :id="idPrefix + '-chart'"
      :key="openCell.key + '|' + result.from + '|' + result.to"
      :object-id="openCell.cell.analysis.objectId"
      :name="openCell.cell.name || ''"
      :from="range.from"
      :to="range.to"
      @close="open = null"
    />
  </div>
</template>
