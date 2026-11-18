<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatNumber } from '../../analysis/format'
import type { ChartView } from '../../analysis/model'
import { regression } from '../../analysis/regression'

/* Model of a REGRESSION chart: formula, quality figures and the coefficient table (ISO 50006 style baseline). */
const props = defineProps<{ chart: ChartView }>()
const { t, locale } = useI18n()

const result = computed(() => regression(props.chart.rows))
const fit = computed(() => (result.value.ok ? result.value.fit : null))
const captionId = computed(() => `regression-${props.chart.id}-caption`)

/** significant digits for coefficients of any size */
function num(value: number, digits = 4): string {
  if (!Number.isFinite(value)) return '∞'
  const abs = Math.abs(value)
  const decimals = abs === 0 ? 0 : Math.min(8, Math.max(0, digits - 1 - Math.floor(Math.log10(abs))))
  return formatNumber(value, locale.value, { min: 0, max: decimals })
}
const percent = (value: number) => formatNumber(value * 100, locale.value, { min: 1, max: 1 }) + ' %'
const pValue = (p: number) => (p < 0.001 ? '< ' + formatNumber(0.001, locale.value, { min: 3, max: 3 }) : formatNumber(p, locale.value, { min: 3, max: 3 }))

const formula = computed(() => {
  const f = fit.value
  if (!f) return ''
  let text = `${f.target.name} = ${num(f.terms[0].coefficient)}`
  for (const term of f.terms.slice(1)) {
    const sign = term.coefficient < 0 ? ' − ' : ' + '
    text += `${sign}${num(Math.abs(term.coefficient))} · ${term.row?.name ?? ''}`
  }
  return text
})

const problemText = computed(() => {
  const r = result.value
  if (r.ok) return ''
  const k = r.drivers.length
  return t('analysis.regression.problem.' + r.problem, { needed: k + 2 })
})
</script>

<template>
  <div class="regression">
    <p v-if="!fit" class="problem" role="status">{{ problemText }}</p>
    <template v-else>
      <p class="formula">
        <span class="label">{{ t('analysis.regression.formula') }}</span>
        <code>{{ formula }}</code>
      </p>
      <dl class="figures">
        <div>
          <dt>{{ t('analysis.regression.n') }}</dt>
          <dd>{{ fit.n }}</dd>
        </div>
        <div>
          <dt>R²</dt>
          <dd>{{ formatNumber(fit.rSquared, locale, { min: 3, max: 3 }) }}</dd>
        </div>
        <div>
          <dt>{{ t('analysis.regression.adjustedR2') }}</dt>
          <dd>{{ formatNumber(fit.adjustedRSquared, locale, { min: 3, max: 3 }) }}</dd>
        </div>
        <div>
          <dt>{{ t('analysis.regression.rmse') }}</dt>
          <dd>{{ num(fit.rmse) }} {{ fit.target.unit }}</dd>
        </div>
        <div v-if="fit.cvRmse !== null">
          <dt>CV(RMSE)</dt>
          <dd>{{ percent(fit.cvRmse) }}</dd>
        </div>
      </dl>
      <div class="scroll">
        <table class="terms" :aria-describedby="captionId">
          <caption :id="captionId" class="visually-hidden">{{ t('analysis.regression.caption', { name: chart.name }) }}</caption>
          <thead>
            <tr>
              <th scope="col" class="name">{{ t('analysis.regression.term') }}</th>
              <th scope="col">{{ t('analysis.regression.coefficient') }}</th>
              <th scope="col">{{ t('analysis.regression.standardError') }}</th>
              <th scope="col">t</th>
              <th scope="col">p</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(term, i) in fit.terms" :key="term.row?.key ?? 'intercept'" :class="{ weak: i > 0 && term.p >= 0.1 }">
              <th scope="row" class="name">
                <span v-if="term.row" class="swatch" :style="{ background: term.row.color }" aria-hidden="true" />
                {{ term.row ? (term.row.unit ? `${term.row.name} [${term.row.unit}]` : term.row.name) : t('analysis.regression.intercept') }}
              </th>
              <td>{{ num(term.coefficient) }}</td>
              <td>{{ num(term.standardError) }}</td>
              <td>{{ num(term.t, 3) }}</td>
              <td>{{ pValue(term.p) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="hint">{{ t('analysis.regression.hint') }}</p>
    </template>
  </div>
</template>

<style scoped>
.regression {
  margin: 0.25rem 0 0.75rem;
  font-size: 0.85rem;
}

.problem {
  margin: 0.25rem 0;
  color: var(--muted);
  font-style: italic;
}

.formula {
  margin: 0 0 0.5rem;
  overflow-wrap: anywhere;
}

.formula .label {
  display: block;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
}

.formula code {
  font-family: inherit;
  font-weight: 600;
  color: var(--blueberry);
}

.figures {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1.5rem;
  margin: 0 0 0.6rem;
}

.figures dt {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
}

.figures dd {
  margin: 0;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.scroll {
  overflow-x: auto;
}

.terms {
  width: 100%;
  border-collapse: collapse;
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

tr.weak td,
tr.weak th {
  color: var(--muted);
}

.swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: 0.45rem;
}

.hint {
  margin: 0.4rem 0 0;
  font-size: 0.78rem;
  color: var(--muted);
}
</style>
