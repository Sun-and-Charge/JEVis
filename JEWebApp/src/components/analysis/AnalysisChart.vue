<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { echartsLocale } from '../../analysis/echarts'
import type { ChartView, RowView } from '../../analysis/model'
import { editableRowNote, notePointFromClick, type ChartClick, type NotePoint } from '../../analysis/notes'
import { chartOption, type OptionContext } from '../../analysis/options'
import { buildCsv, fileName } from '../../analysis/table'
import DataTable from './DataTable.vue'
import SummaryTable from './SummaryTable.vue'

const props = defineProps<{
  chart: ChartView
  ctx: OptionContext
  /** resolved time frame (epoch ms) */
  from: number | null
  to: number | null
  /** file name prefix for exports (analysis name) */
  exportName: string
}>()
const { t, locale } = useI18n()
/* E1: a click on a point / note marker (note dialog), actions of the summary table */
const emit = defineEmits<{ note: [point: NotePoint]; addNote: [row: RowView]; entry: [row: RowView] }>()

const noteClicks = computed(() => props.chart.kind === 'xy' && props.chart.rows.some((r) => r.canAddNote || r.notes?.some(editableRowNote)))

/** Time at the x position of a click (lines without symbols), null if it cannot be computed. */
function pixelTime(click: ChartClick & { event?: { offsetX?: number; offsetY?: number } }): number | null {
  const x = click.event?.offsetX
  const y = click.event?.offsetY
  if (typeof x !== 'number' || typeof y !== 'number' || !chartRef.value) return null
  try {
    const v = (chartRef.value as unknown as { convertFromPixel: (finder: unknown, px: number[]) => unknown }).convertFromPixel({ xAxisIndex: 0 }, [x, y])
    const n = Array.isArray(v) ? v[0] : v
    return typeof n === 'number' && Number.isFinite(n) ? n : null
  } catch {
    return null
  }
}

function onClick(click: ChartClick & { event?: { offsetX?: number; offsetY?: number } }) {
  if (props.chart.kind !== 'xy') return
  const point = notePointFromClick(props.chart.rows, click, pixelTime(click))
  if (point) emit('note', point)
}

const chartRef = ref<InstanceType<typeof VChart> | null>(null)
const isTable = computed(() => props.chart.kind === 'table')

/* new option object only when the inputs change; notMerge so removed series disappear */
const optionCtx = () => (noteClicks.value ? { ...props.ctx, noteClicks: true } : props.ctx)
const option = shallowRef(chartOption(props.chart, optionCtx(), props.from, props.to))
function update() {
  option.value = chartOption(props.chart, optionCtx(), props.from, props.to)
}
watch(() => [props.chart, props.ctx, props.from, props.to] as const, update)
// ECharts measures (and caches) text widths when drawing: start once Montserrat is loaded, or the legend overlaps
const fontsReady = ref(!document.fonts || document.fonts.status === 'loaded')
onMounted(() => {
  if (!fontsReady.value) document.fonts.ready.finally(() => (fontsReady.value = true))
})
const initOptions = computed(() => ({ locale: echartsLocale(locale.value) }))
const headingId = computed(() => `chart-${props.chart.id}-title`)

const baseName = computed(() => fileName(`${props.exportName} - ${props.chart.name}`))

function download(href: string, name: string) {
  const a = document.createElement('a')
  a.href = href
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
}

function exportPng() {
  const url = chartRef.value?.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#ffffff' })
  if (url) download(url, baseName.value + '.png')
}

function exportCsv() {
  const csv = buildCsv(
    props.chart.rows.filter((r) => r.readable),
    locale.value,
    t('analysis.table.time'),
  )
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  download(url, baseName.value + '.csv')
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<template>
  <section class="chart" :aria-labelledby="headingId">
    <header class="head">
      <h2 :id="headingId">{{ chart.name }}</h2>
      <div class="exports">
        <button v-if="!isTable" type="button" class="export" :aria-label="t('analysis.export.pngLabel', { name: chart.name })" :title="t('analysis.export.pngLabel', { name: chart.name })" @click="exportPng">
          <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 2v8m-3.5-3.5L8 10l3.5-3.5M3 13h10" /></svg>{{ t('analysis.export.png') }}
        </button>
        <button type="button" class="export" :aria-label="t('analysis.export.csvLabel', { name: chart.name })" :title="t('analysis.export.csvLabel', { name: chart.name })" @click="exportCsv">
          <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 2v8m-3.5-3.5L8 10l3.5-3.5M3 13h10" /></svg>{{ t('analysis.export.csv') }}
        </button>
      </div>
    </header>

    <DataTable v-if="isTable" :chart="chart" :ctx="ctx" />
    <template v-else>
      <div class="canvas">
        <VChart
          v-if="fontsReady"
          ref="chartRef"
          :option="option ?? {}"
          :init-options="initOptions"
          :update-options="{ notMerge: true }"
          autoresize
          @click="onClick"
        />
      </div>
      <p v-if="noteClicks" class="note-hint">{{ t('noteEdit.chartHint') }}</p>
      <SummaryTable :chart="chart" :ctx="ctx" @add-note="(r: RowView) => emit('addNote', r)" @entry="(r: RowView) => emit('entry', r)" />
    </template>
  </section>
</template>

<style scoped>
.chart {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 0.9rem 1rem 0.6rem;
  min-width: 0;
}

.head {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

h2 {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 1rem;
  overflow-wrap: anywhere;
}

.exports {
  display: flex;
  gap: 0.25rem;
  flex: none;
}

.export {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  min-height: 2rem;
  padding: 0.2rem 0.5rem;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--muted);
  background: none;
  border: 1px solid transparent;
  border-radius: var(--radius);
  cursor: pointer;
}

.export:hover {
  color: var(--blueberry);
  border-color: var(--line);
}

.export svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.canvas {
  height: var(--chart-h, 420px);
  margin-bottom: 0.5rem;
}

.note-hint {
  margin: 0 0 0.4rem;
  font-size: 0.78rem;
  color: var(--muted);
}
</style>
