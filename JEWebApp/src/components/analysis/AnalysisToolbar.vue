<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '../../analysis/format'
import {
  AGGREGATIONS,
  MANIPULATIONS,
  TIME_FRAMES,
  fromDateTimeInput,
  nextInFuture,
  toDateTimeInput,
  type AnalysisSettings,
  type TimeFrame,
} from '../../analysis/settings'

const props = defineProps<{
  settings: AnalysisSettings
  /** resolved time frame of the last data answer */
  range: { from: Date; to: Date } | null
  busy: boolean
}>()
const emit = defineEmits<{ change: [settings: AnalysisSettings]; shift: [direction: -1 | 1] }>()
const { t, te, locale } = useI18n()

const label = (group: string, value: string) => (te(`analysis.${group}.${value}`) ? t(`analysis.${group}.${value}`) : value)

/* values from the defaults that are not in the lists (e.g. MINUTELY) are still selectable */
const aggregations = computed(() => withCurrent(AGGREGATIONS, props.settings.aggregation))
const manipulations = computed(() => withCurrent(MANIPULATIONS, props.settings.manipulation))
function withCurrent(list: readonly string[], current: string): string[] {
  return list.includes(current) ? [...list] : [...list, current]
}

const rangeText = computed(() => {
  if (!props.range) return '…'
  return `${formatDateTime(props.range.from.getTime(), locale.value)} – ${formatDateTime(props.range.to.getTime(), locale.value)}`
})

const canShift = computed(() => !!props.range && !props.busy)
const canNext = computed(() => canShift.value && !nextInFuture(props.settings.timeFrame, props.range!.from, props.range!.to))

function setTimeFrame(value: string) {
  const timeFrame = value as TimeFrame
  if (timeFrame === 'CUSTOM') {
    // start with the period that is shown now
    const from = props.range?.from ?? new Date(new Date().setHours(0, 0, 0, 0))
    const to = props.range?.to ?? new Date()
    emit('change', { ...props.settings, timeFrame, from, to })
  } else {
    emit('change', { ...props.settings, timeFrame, from: null, to: null })
  }
}

function setDate(which: 'from' | 'to', value: string) {
  let date = fromDateTimeInput(value)
  if (!date) return
  // the input has minutes; "to" means the end of that minute
  if (which === 'to') date = new Date(date.getTime() + 59_999)
  const next = { ...props.settings, timeFrame: 'CUSTOM' as const, [which]: date }
  if (next.from && next.to && next.from.getTime() > next.to.getTime()) return
  emit('change', next)
}

function setValue(key: 'aggregation' | 'manipulation', value: string) {
  emit('change', { ...props.settings, [key]: value })
}
</script>

<template>
  <div class="toolbar" role="group" :aria-label="t('analysis.settings')">
    <div class="period">
      <button type="button" class="step" :disabled="!canShift" :aria-label="t('analysis.previous')" :title="t('analysis.previous')" @click="emit('shift', -1)">
        <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M10 3 5 8l5 5" /></svg>
      </button>
      <p class="range" aria-live="polite">
        <span class="visually-hidden">{{ t('analysis.range') }}: </span>
        <span>{{ rangeText }}</span>
      </p>
      <button type="button" class="step" :disabled="!canNext" :aria-label="t('analysis.next')" :title="t('analysis.next')" @click="emit('shift', 1)">
        <svg viewBox="0 0 16 16" aria-hidden="true"><path d="m6 3 5 5-5 5" /></svg>
      </button>
    </div>

    <div class="controls">
      <div class="control">
        <label for="tb-timeframe">{{ t('analysis.timeFrame') }}</label>
        <select id="tb-timeframe" :value="settings.timeFrame" @change="setTimeFrame(($event.target as HTMLSelectElement).value)">
          <option v-for="tf in TIME_FRAMES" :key="tf" :value="tf">{{ label('timeFrames', tf) }}</option>
        </select>
      </div>
      <template v-if="settings.timeFrame === 'CUSTOM'">
        <div class="control">
          <label for="tb-from">{{ t('analysis.from') }}</label>
          <input id="tb-from" type="datetime-local" :value="toDateTimeInput(settings.from)" @change="setDate('from', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="control">
          <label for="tb-to">{{ t('analysis.to') }}</label>
          <input id="tb-to" type="datetime-local" :value="toDateTimeInput(settings.to)" @change="setDate('to', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>
      <div class="control">
        <label for="tb-agg">{{ t('analysis.aggregation') }}</label>
        <select id="tb-agg" :value="settings.aggregation" @change="setValue('aggregation', ($event.target as HTMLSelectElement).value)">
          <option v-for="a in aggregations" :key="a" :value="a">{{ label('aggregations', a) }}</option>
        </select>
      </div>
      <div class="control">
        <label for="tb-man">{{ t('analysis.manipulation') }}</label>
        <select id="tb-man" :value="settings.manipulation" @change="setValue('manipulation', ($event.target as HTMLSelectElement).value)">
          <option v-for="m in manipulations" :key="m" :value="m">{{ label('manipulations', m) }}</option>
        </select>
      </div>
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.75rem 1.5rem;
}

/* the period is the one emphasised element: the yellow bar marks what the charts show */
.period {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  min-width: 0;
}

.range {
  margin: 0;
  padding: 0.2rem 0.4rem 0.3rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  box-shadow: inset 0 -4px 0 var(--sun);
}

.step {
  flex: none;
  width: 2.25rem;
  height: 2.25rem;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: var(--surface);
  color: var(--blueberry);
  cursor: pointer;
}

.step svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.step:hover:not(:disabled) {
  border-color: var(--blueberry);
}

.step:disabled {
  opacity: 0.35;
  cursor: default;
}

.controls {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
  margin-left: auto;
  min-width: 0;
}

.control {
  min-width: 0;
}

.control label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 0.15rem;
}

select,
input[type='datetime-local'] {
  font: inherit;
  font-size: 0.9rem;
  color: var(--ink);
  height: 2.25rem;
  padding: 0 0.5rem;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  max-width: 100%;
}

@media (max-width: 640px) {
  .period {
    width: 100%;
  }

  .range {
    flex: 1;
    text-align: center;
    white-space: normal;
    font-size: 0.9rem;
  }

  .controls {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: 100%;
    margin-left: 0;
  }

  select,
  input[type='datetime-local'] {
    width: 100%;
  }
}
</style>
