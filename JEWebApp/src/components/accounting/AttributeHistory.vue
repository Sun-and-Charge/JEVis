<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { historyChartOption, historyLines, historyPoints } from '../../accounting/contracts'
import { echartsLocale } from '../../analysis/echarts'
import { loadContractHistory, type ContractAttribute, type ContractHistory } from '../../api/contracts'
import { HttpError } from '../../api/http'
import { useAuthStore } from '../../stores/auth'

/*
 * Price history of one attribute of a contract object: a step chart (each value holds until the next one) for
 * numbers and a table of the last values; ranging values only as table.
 */

const props = defineProps<{ objectId: number; attribute: ContractAttribute; domId: string }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

/** values in the table */
const TABLE_LIMIT = 12

const history = shallowRef<ContractHistory | null>(null)
const state = ref<'loading' | 'ready' | 'error' | 'notFound'>('loading')
let seq = 0

async function load() {
  const current = ++seq
  state.value = 'loading'
  try {
    const answer = await loadContractHistory(auth.http, props.objectId, props.attribute.name)
    if (current !== seq) return
    history.value = answer
    state.value = 'ready'
  } catch (e) {
    if (current === seq) state.value = e instanceof HttpError && e.status === 404 ? 'notFound' : 'error'
  }
}

onMounted(load)

const openEnd = (from: string) => t('contracts.openEnd', { from })
const unit = computed(() => history.value?.unit ?? props.attribute.unit ?? null)
const ranging = computed(() => (history.value?.type ?? props.attribute.type) === 'ranging')
const points = computed(() => (ranging.value ? [] : historyPoints(history.value)))
const option = computed(() => (points.value.length ? historyChartOption(points.value, props.attribute.label, unit.value, locale.value) : null))
const initOptions = computed(() => ({ locale: echartsLocale(locale.value) }))
const lines = computed(() => historyLines(history.value, locale.value, openEnd, TABLE_LIMIT))
const total = computed(() => history.value?.samples?.length ?? 0)
</script>

<template>
  <div :id="domId" class="ctr-history">
    <p v-if="state === 'loading'" class="muted" role="status">{{ t('contracts.history.loading') }}</p>
    <p v-else-if="state === 'notFound'" class="muted">{{ t('contracts.history.notFound') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('contracts.history.error') }}
      <button type="button" class="btn-link retry" @click="load">{{ t('modules.retry') }}</button>
    </div>
    <template v-else>
      <p v-if="!lines.length" class="muted">{{ t('contracts.history.empty') }}</p>
      <template v-else>
        <div v-if="option" class="ctr-chart" role="img" :aria-label="t('contracts.history.chartLabel', { name: attribute.label })">
          <VChart :option="option" :init-options="initOptions" :update-options="{ notMerge: true }" autoresize />
        </div>
        <div class="iso-mini-wrap">
          <table class="iso-mini ctr-history-table">
            <caption class="visually-hidden">{{ t('contracts.history.caption', { name: attribute.label }) }}</caption>
            <thead>
              <tr>
                <th scope="col">{{ t('contracts.history.date') }}</th>
                <th scope="col" :class="{ num: !ranging }">{{ t('contracts.history.value') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(l, i) in lines" :key="l.timestamp + i">
                <td class="nowrap">{{ l.date || l.timestamp }}</td>
                <td :class="{ num: !ranging }">{{ l.value || t('modules.none') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="total > lines.length" class="muted note-small">{{ t('contracts.history.more', { n: lines.length, total }) }}</p>
      </template>
    </template>
  </div>
</template>
