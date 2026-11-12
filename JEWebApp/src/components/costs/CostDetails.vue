<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { browserTimeZone, fromDateTimeInput, toDateTimeInput } from '../../analysis/settings'
import { calculateCost, loadCostDefinition, type CostDefinition, type CostInput, type CostResult } from '../../api/costs'
import {
  CUSTOM,
  costCsv,
  costCsvName,
  costErrorKind,
  costRequest,
  costStateFromQuery,
  costStateToQuery,
  isCostStateKey,
  parseTime,
  periodText,
  serverMessage,
  type CostEntry,
  type CostErrorKind,
  type CostState,
} from '../../costs/costs'
import { saveBlob } from '../../iso/download'
import { formatLocalDate } from '../../modules/dates'
import { useAuthStore } from '../../stores/auth'
import CostResultGrid from './CostResultGrid.vue'

/*
 * The selected configuration or template: contract fields, time frame (the template's time frames plus a custom
 * range), the input objects (selectable for templates) and the calculated result. The settings are kept in the URL
 * query so a result can be linked; every change recalculates (debounced, answers of older requests are ignored).
 */

const props = defineProps<{ entry: CostEntry; headingId?: string }>()
const { t, te, locale } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

/** Delay between a change of the settings and the calculation. */
const DEBOUNCE_MS = 300

type DefState = 'loading' | 'ready' | CostErrorKind
type ResultState = 'none' | 'loading' | 'ready' | CostErrorKind

const def = shallowRef<CostDefinition | null>(null)
const defState = ref<DefState>('loading')
const state = ref<CostState | null>(null)
const result = shallowRef<CostResult | null>(null)
const resultState = ref<ResultState>('none')
const resultMessage = ref('')
let defSeq = 0
let resultSeq = 0
let timer: ReturnType<typeof setTimeout> | null = null

const domId = (suffix: string) => `cost-${props.entry.id}-${suffix}`

/* ---------- definition ---------- */

async function loadDefinition() {
  const current = ++defSeq
  defState.value = 'loading'
  try {
    const answer = await loadCostDefinition(auth.http, props.entry.id, locale.value)
    if (current !== defSeq) return
    def.value = answer
    defState.value = 'ready'
    // keep the settings when only the language changed
    state.value = state.value ? costStateFromQuery(costStateToQuery(state.value, answer), answer) : costStateFromQuery(route.query, answer)
    await writeQuery()
    await calculate()
  } catch (e) {
    if (current === defSeq) defState.value = costErrorKind(e)
  }
}

onMounted(loadDefinition)
watch(locale, loadDefinition)

/* ---------- settings ---------- */

async function writeQuery() {
  if (!state.value || !def.value) return
  const query = { ...route.query }
  for (const key of Object.keys(query)) if (isCostStateKey(key)) delete query[key]
  await router.replace({ query: { ...query, ...costStateToQuery(state.value, def.value) } })
}

function update(next: CostState) {
  state.value = next
  void writeQuery()
  schedule()
}

function schedule() {
  if (timer) clearTimeout(timer)
  // an answer to the old settings is no longer wanted
  resultSeq++
  resultState.value = 'loading'
  timer = setTimeout(() => {
    timer = null
    void calculate()
  }, DEBOUNCE_MS)
}

async function calculate() {
  if (!def.value || !state.value) return
  const current = ++resultSeq
  resultState.value = 'loading'
  resultMessage.value = ''
  try {
    const answer = await calculateCost(auth.http, props.entry.id, costRequest(state.value, def.value, browserTimeZone()))
    if (current !== resultSeq) return
    result.value = answer
    resultState.value = 'ready'
  } catch (e) {
    if (current !== resultSeq) return
    result.value = null
    resultState.value = costErrorKind(e)
    resultMessage.value = serverMessage(e)
  }
}

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
  defSeq++
  resultSeq++
})

function setTimeFrame(value: string) {
  if (!state.value) return
  if (value === CUSTOM) {
    // start with the period that is shown now
    const from = parseTime(result.value?.from)
    const to = parseTime(result.value?.to)
    const now = new Date()
    update({
      ...state.value,
      timeFrame: CUSTOM,
      from: from != null ? new Date(from) : new Date(now.getFullYear(), now.getMonth(), 1),
      to: to != null ? new Date(to) : now,
    })
  } else {
    // keep the reference date: e.g. September as month, then 2026 as year
    update({ ...state.value, timeFrame: value, from: null, to: null })
  }
}

/** Moves to the previous / next period (reference dates from the last result), or back to the current one. */
function step(which: 'previous' | 'next') {
  const target = which === 'previous' ? result.value?.previous : result.value?.next
  const ms = parseTime(target)
  if (!state.value || ms == null) return
  update({ ...state.value, date: new Date(ms) })
}

function toCurrent() {
  if (state.value) update({ ...state.value, date: null })
}

const canStep = computed(() => !!state.value && state.value.timeFrame !== CUSTOM)

const rangeError = ref(false)

function setDate(which: 'from' | 'to', value: string) {
  if (!state.value) return
  let date = fromDateTimeInput(value)
  if (!date) return
  // the input has minutes; "to" means the end of that minute
  if (which === 'to') date = new Date(date.getTime() + 59_999)
  const next = { ...state.value, timeFrame: CUSTOM, [which]: date }
  rangeError.value = !!(next.from && next.to && next.from.getTime() > next.to.getTime())
  if (rangeError.value) return
  update(next)
}

function setSelection(input: CostInput, value: string) {
  if (!state.value) return
  const selections = { ...state.value.selections }
  if (/^\d+$/.test(value)) selections[input.id] = Number(value)
  else delete selections[input.id]
  update({ ...state.value, selections })
}

/* ---------- display ---------- */

const isTemplate = computed(() => def.value?.kind === 'template' || (!def.value && props.entry.kind === 'template'))
const where = computed(() => [props.entry.site?.name, props.entry.path].filter(Boolean).join(' / '))
const templateName = computed(() => def.value?.templateName ?? props.entry.templateName)

const energyName = (code: string) => (te('contracts.energy.' + code) ? t('contracts.energy.' + code) : code)
const contract = computed(() => def.value?.contract ?? null)
const contractPeriod = computed(() => {
  const c = contract.value
  if (!c) return ''
  const start = formatLocalDate(c.start, locale.value)
  const end = formatLocalDate(c.end, locale.value)
  return start || end ? `${start || t('modules.none')} – ${end || t('modules.none')}` : ''
})
const energyType = computed(() => {
  const code = contract.value?.energyType || contract.value?.contractType
  return code ? energyName(code) : ''
})

const inputLabel = (input: CostInput) => input.label || input.variableName
const selectableInputs = computed(() => (def.value?.inputs ?? []).filter((i) => i.selectable))
const boundInputs = computed(() => (def.value?.inputs ?? []).filter((i) => !i.selectable))
/** Options of a selectable input; the default object is offered even if the list was cut. */
function optionsOf(input: CostInput) {
  const options = input.options ?? []
  return input.selected && !options.some((o) => o.id === input.selected!.id) ? [input.selected, ...options] : options
}
const selectionValue = (input: CostInput) => {
  const id = state.value?.selections[input.id]
  return id == null ? '' : String(id)
}

const period = computed(() => (result.value ? periodText(result.value.from, result.value.to, locale.value) : ''))

/* ---------- export ---------- */

function exportCsv() {
  const r = result.value
  if (!r) return
  const csv = costCsv(r.cells ?? [], locale.value, { name: t('costs.csv.name'), value: t('costs.csv.value'), unit: t('costs.csv.unit') })
  saveBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), costCsvName(props.entry.name, r.from, r.to))
}

function print() {
  if (typeof window.print === 'function') window.print()
}
</script>

<template>
  <article class="rep-detail cost-detail" :aria-labelledby="headingId ?? domId('title')">
    <p v-if="where" class="rep-where muted">{{ where }}</p>
    <h2 :id="headingId ?? domId('title')" class="rep-title" tabindex="-1">{{ def?.name ?? entry.name }}</h2>

    <dl class="mod-dl cost-meta">
      <dt>{{ t('costs.detail.kind') }}</dt>
      <dd>{{ t(isTemplate ? 'costs.kind.template' : 'costs.kind.configuration') }}</dd>
      <template v-if="!isTemplate">
        <dt>{{ t('costs.detail.template') }}</dt>
        <dd :class="{ muted: !entry.templateReadable }">{{ entry.templateReadable ? templateName || t('modules.none') : t('costs.templateMissing') }}</dd>
      </template>
      <template v-if="contract">
        <dt>{{ t('contracts.config.contractNumber') }}</dt>
        <dd>{{ contract.contractNumber || t('modules.none') }}</dd>
        <dt>{{ t('contracts.config.marketLocation') }}</dt>
        <dd class="cost-wrap">{{ contract.marketLocation || t('modules.none') }}</dd>
        <dt>{{ t('contracts.config.energyType') }}</dt>
        <dd>{{ energyType || t('modules.none') }}</dd>
        <dt>{{ t('costs.detail.term') }}</dt>
        <dd>{{ contractPeriod || t('modules.none') }}</dd>
      </template>
    </dl>

    <p v-if="defState === 'loading'" class="muted" role="status">{{ t('costs.detail.loading') }}</p>
    <div v-else-if="defState === 'notFound'" class="alert alert-error" role="alert">{{ t('costs.error.notFound') }}</div>
    <div v-else-if="defState === 'template'" class="alert alert-error" role="alert">{{ t('costs.error.template') }}</div>
    <div v-else-if="defState !== 'ready'" class="alert alert-error" role="alert">
      {{ t('costs.error.definition') }}
      <button type="button" class="btn-link retry" @click="loadDefinition">{{ t('modules.retry') }}</button>
    </div>

    <template v-else-if="def && state">
      <div class="cost-settings" role="group" :aria-label="t('costs.detail.settings')">
        <div class="control cost-control">
          <label :for="domId('tf')">{{ t('costs.detail.timeFrame') }}</label>
          <select :id="domId('tf')" class="cost-timeframe" :value="state.timeFrame" @change="setTimeFrame(($event.target as HTMLSelectElement).value)">
            <option v-for="tf in def.timeFrames" :key="tf.id" :value="tf.id">{{ tf.name }}</option>
            <option :value="CUSTOM">{{ t('costs.detail.custom') }}</option>
          </select>
        </div>
        <template v-if="state.timeFrame === CUSTOM">
          <div class="control cost-control">
            <label :for="domId('from')">{{ t('costs.detail.from') }}</label>
            <input :id="domId('from')" class="cost-from" type="datetime-local" :value="toDateTimeInput(state.from)" @change="setDate('from', ($event.target as HTMLInputElement).value)" />
          </div>
          <div class="control cost-control">
            <label :for="domId('to')">{{ t('costs.detail.to') }}</label>
            <input :id="domId('to')" class="cost-to" type="datetime-local" :value="toDateTimeInput(state.to)" @change="setDate('to', ($event.target as HTMLInputElement).value)" />
          </div>
        </template>
        <div v-for="input in selectableInputs" :key="input.id" class="control cost-control cost-input">
          <label :for="domId('in-' + input.id)">{{ inputLabel(input) }}</label>
          <select :id="domId('in-' + input.id)" :data-input="input.id" :value="selectionValue(input)" @change="setSelection(input, ($event.target as HTMLSelectElement).value)">
            <option value="">{{ t('costs.detail.noObject') }}</option>
            <option v-for="o in optionsOf(input)" :key="o.id" :value="String(o.id)">{{ o.name || '#' + o.id }}</option>
          </select>
        </div>
      </div>
      <p v-if="rangeError" class="iso-error rep-hint" role="alert">{{ t('costs.detail.rangeOrder') }}</p>

      <details v-if="boundInputs.length" class="cost-inputs">
        <summary>{{ t('costs.detail.inputs', { n: boundInputs.length }) }}</summary>
        <dl class="mod-dl">
          <template v-for="input in boundInputs" :key="input.id">
            <dt>{{ inputLabel(input) }}</dt>
            <dd :class="{ muted: !input.selected || input.selected.readable === false }">
              {{ !input.selected ? t('costs.detail.noObject') : input.selected.readable === false ? t('costs.detail.noAccess') : input.selected.name }}
            </dd>
          </template>
        </dl>
      </details>

      <section class="cost-result" :aria-labelledby="domId('result')" :aria-busy="resultState === 'loading' ? 'true' : 'false'">
        <div class="cost-result-head">
          <div>
            <h3 :id="domId('result')" class="cost-result-title">{{ t('costs.result.title') }}</h3>
            <div v-if="period" class="cost-period-row">
              <button v-if="canStep" type="button" class="cost-step cost-prev" :aria-label="t('costs.result.previous')" :title="t('costs.result.previous')" :disabled="!result?.previous" @click="step('previous')">‹</button>
              <p class="cost-period" aria-live="polite">
                <span class="visually-hidden">{{ t('costs.result.period') }}: </span>{{ period }}
              </p>
              <button v-if="canStep" type="button" class="cost-step cost-next" :aria-label="t('costs.result.next')" :title="t('costs.result.next')" :disabled="!result?.next" @click="step('next')">›</button>
              <button v-if="canStep && state?.date" type="button" class="btn-link cost-current" @click="toCurrent">{{ t('costs.result.current') }}</button>
            </div>
            <p class="cost-print-only">{{ def.name }}<template v-if="templateName && !isTemplate"> · {{ templateName }}</template></p>
          </div>
          <div class="rep-actions cost-actions">
            <button type="button" class="notes-btn cost-csv" :disabled="!result || resultState !== 'ready'" @click="exportCsv">{{ t('costs.result.csv') }}</button>
            <button type="button" class="notes-btn cost-print" :disabled="!result || resultState !== 'ready'" @click="print">{{ t('costs.result.print') }}</button>
          </div>
        </div>

        <p v-if="resultState === 'loading'" class="muted" role="status">{{ t('costs.result.loading') }}</p>
        <div v-else-if="resultState === 'notFound'" class="alert alert-error cost-error" role="alert">{{ t('costs.error.notFound') }}</div>
        <div v-else-if="resultState === 'template'" class="alert alert-error cost-error" role="alert">{{ t('costs.error.template') }}</div>
        <div v-else-if="resultState === 'invalid'" class="alert alert-error cost-error" role="alert">
          {{ resultMessage ? t('costs.error.invalid') + ' ' + resultMessage : t('costs.error.invalid') }}
        </div>
        <div v-else-if="resultState === 'error'" class="alert alert-error cost-error" role="alert">
          {{ t('costs.error.result') }}
          <button type="button" class="btn-link retry" @click="calculate">{{ t('modules.retry') }}</button>
        </div>

        <div v-if="result && (resultState === 'ready' || resultState === 'loading')" class="cost-result-body" :class="{ 'is-stale': resultState === 'loading' }">
          <details v-if="result.warnings?.length" class="cost-warnings">
            <summary>{{ t('costs.result.warnings', result.warnings.length) }}</summary>
            <ul>
              <li v-for="(w, i) in result.warnings" :key="i">{{ w }}</li>
            </ul>
          </details>
          <CostResultGrid :result="result" :id-prefix="domId('grid')" />
        </div>
      </section>
    </template>
  </article>
</template>

<style scoped>
.retry {
  margin-left: 0.5rem;
}
</style>
