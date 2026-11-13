<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteValue, type EntryInfo, type SavedValue } from '../../api/entry'
import { loadRegisterHistory, type RegisterColumn, type RegisterHistory, type RegisterKind, type RegisterRow } from '../../api/registers'
import { useBusy, writeErrorText } from '../../edit/edit'
import { formatEntryTime } from '../../edit/entry'
import { formatNumber } from '../../analysis/format'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EntryDialog from '../edit/EntryDialog.vue'
import MeterExchangeDialog from './MeterExchangeDialog.vue'
import '../../styles/edit.css'
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
/* E1: a value was entered or deleted (the view reloads the last values); E5: edit / delete the object */
const emit = defineEmits<{ changed: []; edit: []; remove: [] }>()
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

/* ---- E1: manual values (meters: the target data row, base data: the object itself) ---- */

const entryRowId = computed<number | null>(() => {
  if (props.kind === 'basedata') return props.row.id
  if (meter.value && typeof props.row.target?.id === 'number') return props.row.target.id
  return null
})
/* the list says whether values may be entered (meters: for the target row) */
const canEnter = computed(() => props.row.canEnter === true && entryRowId.value != null)
const entryOpen = ref(false)
const entryMessage = ref<{ ok: boolean; text: string } | null>(null)

function entrySaved(result: SavedValue, info: EntryInfo) {
  entryOpen.value = false
  const value = formatNumber(result.value, locale.value, { min: 0, max: 6 }) + (info.unit ? ' ' + info.unit : '')
  entryMessage.value = { ok: true, text: t('entry.saved', { value, time: formatEntryTime(result.timestamp, info.mode, locale.value, info.timeZone) }) }
  void loadHistory()
  emit('changed')
}

const toDelete = ref<{ timestamp: string; time: string } | null>(null)
const deleteError = ref<string | null>(null)
const { busy: deleting, run: runDelete } = useBusy()
/* history samples carry `manual` / `canDelete`; the lines are in the order of the samples */
const canDeleteAt = (index: number) => history.value?.samples?.[index]?.canDelete === true
const anyDeletable = computed(() => (history.value?.samples ?? []).some((s) => s?.canDelete === true))
/** row of the history samples (meters: the target row) */
const historyRowId = computed(() => (typeof history.value?.rowId === 'number' ? history.value.rowId : entryRowId.value))

function askDelete(timestamp: string, time: string) {
  deleteError.value = null
  entryMessage.value = null
  toDelete.value = { timestamp, time }
}

async function confirmDelete() {
  const target = toDelete.value
  const rowId = historyRowId.value
  if (!target || rowId == null) return
  await runDelete(async () => {
    try {
      await deleteValue(auth.http, rowId, target.timestamp)
      toDelete.value = null
      entryMessage.value = { ok: true, text: t('entry.deleted', { time: target.time }) }
      void loadHistory()
      emit('changed')
    } catch (e) {
      deleteError.value = writeErrorText(e, t)
    }
  })
}

/* ---- E5: edit, delete, meter exchange (meters and equipment only; the view opens edit / delete) ---- */

const editableKind = computed(() => props.kind === 'meters' || props.kind === 'equipment')
const canWrite = computed(() => editableKind.value && props.row.canWrite === true)
const canRemove = computed(() => editableKind.value && props.row.canDelete === true)
/* the exchange writes into the meter's data row: the same right as entering a value there */
const canExchange = computed(() => meter.value && canEnter.value)
const exchangeOpen = ref(false)

function exchangeSaved(timestamp: string) {
  exchangeOpen.value = false
  entryMessage.value = { ok: true, text: t('registerEdit.exchange.saved', { time: formatEntryTime(timestamp, 'SPECIFIC_DATETIME', locale.value) }) }
  void loadHistory()
  emit('changed')
}

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
      <div v-if="canEnter || canWrite || canRemove" class="edit-actions">
        <button v-if="canWrite" type="button" class="edit-btn reg-edit" :aria-label="t('registerEdit.editLabel', { name: row.name })" @click="emit('edit')">
          {{ t('registerEdit.edit') }}
        </button>
        <button v-if="canEnter" type="button" class="edit-btn reg-entry" :aria-label="t('entry.actionLabel', { name: row.name })" @click="entryOpen = true">{{ t('entry.action') }}</button>
        <button v-if="canExchange" type="button" class="edit-btn reg-exchange-btn" :aria-label="t('registerEdit.exchange.actionLabel', { name: row.name })" @click="exchangeOpen = true">
          {{ t('registerEdit.exchange.action') }}
        </button>
        <button v-if="canRemove" type="button" class="edit-btn is-danger reg-delete" :aria-label="t('registerEdit.deleteLabel', { name: row.name })" @click="emit('remove')">
          {{ t('registerEdit.delete') }}
        </button>
      </div>
      <p v-if="entryMessage" class="alert edit-result reg-entry-message" :class="entryMessage.ok ? 'alert-ok' : 'alert-error'" role="status">{{ entryMessage.text }}</p>
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
              <th v-if="anyDeletable" scope="col"><span class="visually-hidden">{{ t('alarmEdit.actions') }}</span></th>
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
              <td v-if="anyDeletable">
                <button
                  v-if="canDeleteAt(i)"
                  type="button"
                  class="edit-btn is-small is-danger reg-value-delete"
                  :aria-label="t('entry.deleteLabel', { time: l.time })"
                  @click="askDelete(l.timestamp, l.time)"
                >
                  {{ t('entry.deleteAction') }}
                </button>
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

    <MeterExchangeDialog v-if="exchangeOpen" :row="row" @close="exchangeOpen = false" @saved="exchangeSaved" />
    <EntryDialog v-if="entryOpen && entryRowId != null" :row-id="entryRowId" :row-name="row.name" @close="entryOpen = false" @saved="entrySaved" />
    <ConfirmDialog
      v-if="toDelete"
      :title="t('entry.deleteTitle')"
      :message="t('entry.deleteQuestion', { time: toDelete.time })"
      :confirm-label="t('entry.deleteAction')"
      :busy="deleting"
      :error="deleteError"
      danger
      @confirm="confirmDelete"
      @cancel="toDelete = null"
    />
  </div>
</template>
