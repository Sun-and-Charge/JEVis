<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadRegisterHistory, type RegisterColumn, type RegisterHistory, type RegisterKind, type RegisterRow } from '../../api/registers'
import { formatAttribute, formatByPeriod, formatLast, historyLines, isFileValue, isTargetValue, mediumLabel, rowPeriod, targetText, verificationState, verificationText } from '../../registers/format'
import { useAuthStore } from '../../stores/auth'
import MeterQr from './MeterQr.vue'
import RegisterFile from './RegisterFile.vue'

/*
 * Detail panel of a register row: all attribute values (files as thumbnail / download, multi-line texts kept), the
 * target and last value, a short history (base data: last values; meters: last readings, meter exchanges marked)
 * and the meter's QR code.
 */

const props = defineProps<{ kind: RegisterKind; row: RegisterRow; columns: readonly RegisterColumn[]; today: string }>()
const { t, te, locale } = useI18n()
const auth = useAuthStore()
const tr = (key: string) => t(key)

const meter = computed(() => props.kind === 'meters')
const hasHistory = computed(() => props.kind === 'basedata' || (meter.value && !!props.row.target?.readable))

const history = shallowRef<RegisterHistory | null>(null)
const historyState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')

async function loadHistory() {
  if (!hasHistory.value) return
  historyState.value = 'loading'
  try {
    history.value = await loadRegisterHistory(auth.http, props.row.id, 12)
    historyState.value = 'ready'
  } catch {
    historyState.value = 'error'
  }
}

onMounted(loadHistory)

const lines = computed(() =>
  historyLines(history.value, { meter: meter.value, period: rowPeriod(props.row), decimals: props.row.last?.decimals, unit: props.row.last?.unit }, locale.value),
)

const lastTime = computed(() => formatByPeriod(props.row.last?.timestamp, meter.value ? null : rowPeriod(props.row), locale.value))
const verification = computed(() => verificationText(props.row, props.today, locale.value, tr))
const verificationClass = computed(() => verificationState(props.row, props.today))

function text(column: RegisterColumn): string {
  return formatAttribute(props.row.values?.[column.key], column, locale.value, tr)
}

const fileValue = (column: RegisterColumn) => {
  const v = props.row.values?.[column.key]
  return isFileValue(v) ? v : null
}

const isTarget = (column: RegisterColumn) => isTargetValue(props.row.values?.[column.key])
const id = (suffix: string) => `reg-${props.row.id}-${suffix}`
</script>

<template>
  <div class="iso-sections reg-details">
    <section class="iso-section" :aria-labelledby="id('general')">
      <h3 :id="id('general')">{{ t('registers.detail.general') }}</h3>
      <dl class="mod-dl">
        <dt>{{ t('registers.col.name') }}</dt>
        <dd>{{ row.name }}</dd>
        <template v-if="row.classLabel || row.className">
          <dt>{{ t('registers.detail.class') }}</dt>
          <dd>{{ row.classLabel || row.className }}</dd>
        </template>
        <template v-if="meter">
          <dt>{{ t('registers.col.medium') }}</dt>
          <dd>{{ mediumLabel(row, tr, te) }}</dd>
        </template>
        <template v-if="row.path">
          <dt>{{ t('registers.col.path') }}</dt>
          <dd>{{ row.path }}</dd>
        </template>
        <template v-if="meter">
          <dt>{{ t('registers.detail.target') }}</dt>
          <dd>{{ row.target ? targetText(row.target, tr) : t('registers.detail.noTarget') }}</dd>
        </template>
        <template v-if="kind !== 'equipment'">
          <dt>{{ t('registers.col.last') }}</dt>
          <dd class="num-text">{{ formatLast(row.last, locale) || t('modules.none') }}</dd>
          <dt>{{ meter ? t('registers.col.lastTime') : t('registers.col.timestamp') }}</dt>
          <dd class="num-text">{{ lastTime || t('modules.none') }}</dd>
        </template>
        <template v-if="verification">
          <dt>{{ t('registers.col.verification') }}</dt>
          <dd>
            <span class="mod-badge" :class="'is-' + verificationClass">{{ verification }}</span>
          </dd>
        </template>
      </dl>
    </section>

    <section v-if="columns.length" class="iso-section" :aria-labelledby="id('values')">
      <h3 :id="id('values')">{{ t('registers.detail.values') }}</h3>
      <dl class="mod-dl reg-values">
        <template v-for="c in columns" :key="c.key">
          <dt>{{ c.name }}</dt>
          <dd v-if="fileValue(c)">
            <RegisterFile :object-id="row.id" :attribute="c.key" :label="c.name" :value="fileValue(c)!" />
          </dd>
          <dd v-else-if="c.type === 'TEXTAREA'" class="reg-multiline">{{ text(c) || t('modules.none') }}</dd>
          <dd v-else :class="{ 'num-text': c.type === 'NUMBER' || c.type === 'INTEGER', muted: isTarget(c) && !text(c) }">{{ text(c) || t('modules.none') }}</dd>
        </template>
      </dl>
    </section>

    <section v-if="hasHistory" class="iso-section" :aria-labelledby="id('history')">
      <h3 :id="id('history')">{{ meter ? t('registers.history.readings') : t('registers.history.values') }}</h3>
      <p v-if="historyState === 'loading'" class="muted" role="status">{{ t('registers.history.loading') }}</p>
      <p v-else-if="historyState === 'error'" class="alert alert-error" role="alert">
        {{ t('registers.history.error') }}
        <button type="button" class="btn-link" @click="loadHistory">{{ t('modules.retry') }}</button>
      </p>
      <p v-else-if="!lines.length" class="muted">{{ t('registers.history.empty') }}</p>
      <div v-else class="iso-mini-wrap">
        <table class="iso-mini reg-history">
          <caption class="visually-hidden">{{ meter ? t('registers.history.readings') : t('registers.history.values') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('registers.history.time') }}</th>
              <th scope="col" class="num">{{ t('registers.history.value') }}</th>
              <th scope="col">{{ t('registers.history.note') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(l, i) in lines" :key="l.timestamp + i" :class="{ 'reg-exchange': l.exchange }">
              <td class="nowrap num-text">{{ l.time }}</td>
              <td class="num num-text">{{ l.value }}</td>
              <td>
                <span v-if="l.exchange" class="mod-badge is-dueSoon">{{ t('registers.history.exchange') }}</span>
                {{ l.note }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="meter" class="iso-section" :aria-labelledby="id('qr')">
      <h3 :id="id('qr')">{{ t('registers.qr.title') }}</h3>
      <MeterQr :meter-id="row.id" :name="row.name" />
    </section>
  </div>
</template>
