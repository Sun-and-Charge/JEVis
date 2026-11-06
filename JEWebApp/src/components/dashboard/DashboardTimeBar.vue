<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DashboardTimeFrameOption } from '../../api/dashboard'
import { formatDateTime } from '../../analysis/format'
import { fromDateTimeInput, toDateTimeInput } from '../../analysis/settings'
import type { TimeBarState } from '../../dashboard/timeframe'

/** Time frame bar of a dashboard: period, arrows, time frame select and (CUSTOM) from/to. */
const props = defineProps<{
  state: TimeBarState
  options: DashboardTimeFrameOption[]
  /** resolved period of the last answer */
  range: { from: number; to: number } | null
  canPrevious: boolean
  canNext: boolean
  busy: boolean
}>()
const emit = defineEmits<{ select: [timeFrame: string]; custom: [from: Date, to: Date]; step: [direction: -1 | 1] }>()
const { t, te, locale } = useI18n()

function label(option: DashboardTimeFrameOption): string {
  if (option.custom || /^\d+$/.test(option.id)) return option.name || option.id
  return te(`dashboard.timeFrames.${option.id}`) ? t(`dashboard.timeFrames.${option.id}`) : option.name || option.id
}

const rangeText = computed(() => {
  if (!props.range) return '…'
  return `${formatDateTime(props.range.from, locale.value)} – ${formatDateTime(props.range.to, locale.value)}`
})

function setDate(which: 'from' | 'to', value: string) {
  let date = fromDateTimeInput(value)
  if (!date) return
  if (which === 'to') date = new Date(date.getTime() + 59_999)
  const from = which === 'from' ? date : props.state.from
  const to = which === 'to' ? date : props.state.to
  if (!from || !to || from.getTime() > to.getTime()) return
  emit('custom', from, to)
}
</script>

<template>
  <div class="timebar" role="group" :aria-label="t('dashboard.timeBar')">
    <div class="period">
      <button type="button" class="step" :disabled="!canPrevious || busy" :aria-label="t('analysis.previous')" :title="t('analysis.previous')" @click="emit('step', -1)">
        <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M10 3 5 8l5 5" /></svg>
      </button>
      <p class="range" aria-live="polite">
        <span class="visually-hidden">{{ t('analysis.range') }}: </span>
        <span>{{ rangeText }}</span>
      </p>
      <button type="button" class="step" :disabled="!canNext || busy" :aria-label="t('analysis.next')" :title="t('analysis.next')" @click="emit('step', 1)">
        <svg viewBox="0 0 16 16" aria-hidden="true"><path d="m6 3 5 5-5 5" /></svg>
      </button>
    </div>
    <div class="controls">
      <div class="control">
        <label for="db-timeframe">{{ t('analysis.timeFrame') }}</label>
        <select id="db-timeframe" :value="state.timeFrame" @change="emit('select', ($event.target as HTMLSelectElement).value)">
          <option v-for="o in options" :key="o.id" :value="o.id">{{ label(o) }}</option>
        </select>
      </div>
      <template v-if="state.timeFrame === 'CUSTOM'">
        <div class="control">
          <label for="db-from">{{ t('analysis.from') }}</label>
          <input id="db-from" type="datetime-local" :value="toDateTimeInput(state.from)" @change="setDate('from', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="control">
          <label for="db-to">{{ t('analysis.to') }}</label>
          <input id="db-to" type="datetime-local" :value="toDateTimeInput(state.to)" @change="setDate('to', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.timebar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.75rem 1.5rem;
}

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
  align-items: flex-end;
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
    width: 100%;
    margin-left: 0;
  }

  .control {
    flex: 1 1 40%;
  }

  select,
  input[type='datetime-local'] {
    width: 100%;
  }
}
</style>
