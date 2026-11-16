<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { checkNewValue, firstOfNextMonth, warningsOf, type Problem } from '../../accounting/contractEdit'
import { contractNumber, sampleDate } from '../../accounting/contracts'
import { addContractValue, deleteContractValue, loadContractHistory, type ContractAttribute, type ContractHistory, type ContractHistorySample } from '../../api/contracts'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { serverMessages } from '../../iso/edit'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'

/*
 * A price or due of a contract object (E6.2), as the Control Center's EnterDataDialog: the current value with unit
 * and "gültig ab", the history (newest first; delete per value with a question) and "Neuer Wert" (value in the input
 * unit, valid-from day, default the 1st of next month). A value that exists at that day is only overwritten after
 * "Trotzdem speichern". Values are written right away (not with the dialog's "Speichern").
 */

const props = defineProps<{
  objectId: number
  attribute: ContractAttribute
  /** write right on the object */
  canWrite: boolean
  idBase: string
}>()
const emit = defineEmits<{
  /** a value was written or deleted: reload the list */
  written: []
  /** a new value is typed but not saved (unsaved-changes guard) */
  pending: [pending: boolean]
}>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()

const a = computed(() => props.attribute)
const unit = computed(() => a.value.unit ?? null)
const inputUnit = computed(() => a.value.inputUnit ?? a.value.unit ?? null)
const writable = computed(() => props.canWrite && a.value.editable !== false)
const current = computed(() => (typeof a.value.value === 'number' ? contractNumber(a.value.value, unit.value, locale.value) : ''))
const since = computed(() => (current.value ? sampleDate(a.value.timestamp, locale.value) : ''))

/* ---------- history ---------- */
const open = ref(false)
const history = shallowRef<ContractHistory | null>(null)
const historyState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
let seq = 0

async function loadHistory() {
  const mine = ++seq
  historyState.value = 'loading'
  try {
    const answer = await loadContractHistory(auth.http, props.objectId, a.value.name)
    if (mine !== seq) return
    history.value = answer
    historyState.value = 'ready'
  } catch {
    if (mine === seq) historyState.value = 'error'
  }
}

function toggle() {
  open.value = !open.value
  if (open.value) void loadHistory()
}

const samples = computed(() => (history.value?.samples ?? []).filter((s) => s && typeof s.timestamp === 'string'))
const historyUnit = computed(() => history.value?.unit ?? unit.value)
const sampleValue = (s: ContractHistorySample) => (typeof s.value === 'number' ? contractNumber(s.value, historyUnit.value, locale.value) : t('modules.none'))

/* ---------- new value ---------- */
const valueText = ref('')
const validFrom = ref(firstOfNextMonth())
const valueProblem = ref<Problem | null>(null)
const dateProblem = ref<Problem | null>(null)
const error = ref<string | null>(null)
const done = ref<string | null>(null)
const warnings = ref<string[] | null>(null)
const pendingValue = ref<number | null>(null)

watch(
  () => valueText.value.trim() !== '',
  (pending) => emit('pending', pending),
)

async function save(confirm = false) {
  error.value = null
  done.value = null
  const check = checkNewValue(valueText.value, validFrom.value, a.value.primitive)
  valueProblem.value = check.valueProblem
  dateProblem.value = check.dateProblem
  if (check.value == null || check.dateProblem) {
    document.getElementById(props.idBase + (check.valueProblem ? '-value' : '-from'))?.focus()
    return
  }
  pendingValue.value = check.value
  await send(check.value, confirm)
}

async function send(value: number, confirm: boolean) {
  await run(async () => {
    try {
      await addContractValue(auth.http, props.objectId, { attribute: a.value.name, validFrom: validFrom.value, value, confirm }, locale.value)
      warnings.value = null
      done.value = t('contractEdit.price.saved', { value: contractNumber(value, inputUnit.value, locale.value), date: sampleDate(validFrom.value, locale.value) })
      valueText.value = ''
      emit('written')
      if (open.value) void loadHistory()
    } catch (e) {
      const list = warningsOf(e)
      if (list && !confirm) {
        warnings.value = list
        return
      }
      warnings.value = null
      if (writeErrorKind(e) === 'invalid') {
        const messages = serverMessages(e)
        if (messages.length) {
          error.value = messages.join(' ')
          return
        }
      }
      error.value = writeErrorText(e, t)
    }
  })
}

function overwrite() {
  if (pendingValue.value != null) void send(pendingValue.value, true)
}

function cancelOverwrite() {
  if (!busy.value) warnings.value = null
}

/* ---------- delete a value ---------- */
const deleting = shallowRef<ContractHistorySample | null>(null)
const deleteError = ref<string | null>(null)

function askDelete(s: ContractHistorySample) {
  deleteError.value = null
  done.value = null
  deleting.value = s
}

async function confirmDelete() {
  const s = deleting.value
  if (!s) return
  await run(async () => {
    try {
      await deleteContractValue(auth.http, props.objectId, a.value.name, s.timestamp)
      deleting.value = null
      done.value = t('contractEdit.price.deleted', { date: sampleDate(s.timestamp, locale.value) })
      emit('written')
      void loadHistory()
    } catch (e) {
      if (writeErrorKind(e) === 'notFound') {
        deleting.value = null
        error.value = t('contractEdit.price.deleteGone')
        emit('written')
        void loadHistory()
        return
      }
      deleteError.value = writeErrorText(e, t)
    }
  })
}

const id = (suffix: string) => `${props.idBase}-${suffix}`
const label = computed(() => a.value.label || a.value.name)
</script>

<template>
  <section class="ctr-price" :aria-labelledby="id('title')">
    <div class="ctr-price-head">
      <h4 :id="id('title')">{{ label }}</h4>
      <p class="ctr-price-current">
        <strong>{{ current || t('modules.none') }}</strong>
        <span v-if="since" class="muted">{{ t('contracts.validFrom', { date: since }) }}</span>
      </p>
      <button
        type="button"
        class="notes-btn ctr-price-history-btn"
        :aria-expanded="open"
        :aria-controls="id('history')"
        :aria-label="t('contracts.history.toggleLabel', { name: label })"
        @click="toggle"
      >
        <span class="chevron" aria-hidden="true">›</span> {{ t('contracts.history.toggle') }}
      </button>
    </div>

    <div v-if="open" :id="id('history')" class="ctr-price-history">
      <p v-if="historyState === 'loading'" class="muted" role="status">{{ t('contracts.history.loading') }}</p>
      <div v-else-if="historyState === 'error'" class="alert alert-error" role="alert">
        {{ t('contracts.history.error') }}
        <button type="button" class="btn-link retry" @click="loadHistory">{{ t('modules.retry') }}</button>
      </div>
      <p v-else-if="!samples.length" class="muted">{{ t('contracts.history.empty') }}</p>
      <div v-else class="iso-mini-wrap ctr-price-history-wrap">
        <table class="iso-mini">
          <caption class="visually-hidden">{{ t('contracts.history.caption', { name: label }) }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('contracts.history.date') }}</th>
              <th scope="col" class="num">{{ t('contracts.history.value') }}</th>
              <th scope="col"><span class="visually-hidden">{{ t('contractEdit.ranging.actions') }}</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(s, i) in samples" :key="s.timestamp + i">
              <td class="nowrap">{{ sampleDate(s.timestamp, locale) || s.timestamp }}</td>
              <td class="num">{{ sampleValue(s) }}</td>
              <td class="ctr-rng-actions">
                <button
                  v-if="s.canDelete"
                  type="button"
                  class="edit-btn is-small is-danger ctr-price-delete"
                  :disabled="busy"
                  :aria-label="t('contractEdit.price.deleteLabel', { date: sampleDate(s.timestamp, locale) })"
                  @click="askDelete(s)"
                >
                  {{ t('contractEdit.delete') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <form v-if="writable" class="ctr-price-new" novalidate @submit.prevent="save()">
      <div class="edit-field">
        <label :for="id('value')">{{ t('contractEdit.price.newValue') }}{{ inputUnit ? ` [${inputUnit}]` : '' }}</label>
        <input
          :id="id('value')"
          v-model="valueText"
          type="text"
          :inputmode="attribute.primitive === 'LONG' ? 'numeric' : 'decimal'"
          autocomplete="off"
          :disabled="busy"
          :aria-invalid="valueProblem ? 'true' : undefined"
          :aria-describedby="valueProblem ? id('value-err') : undefined"
          @input="valueProblem = null"
        />
        <p v-if="valueProblem" :id="id('value-err')" class="iso-field-error">{{ t(valueProblem.key, valueProblem.params ?? {}) }}</p>
      </div>
      <div class="edit-field">
        <label :for="id('from')">{{ t('contractEdit.price.validFrom') }}</label>
        <input
          :id="id('from')"
          v-model="validFrom"
          type="date"
          :disabled="busy"
          :aria-invalid="dateProblem ? 'true' : undefined"
          :aria-describedby="dateProblem ? id('from-err') : undefined"
          @input="dateProblem = null"
        />
        <p v-if="dateProblem" :id="id('from-err')" class="iso-field-error">{{ t(dateProblem.key) }}</p>
      </div>
      <button type="submit" class="edit-btn ctr-price-save" :disabled="busy">{{ busy ? t('edit.saving') : t('contractEdit.price.save') }}</button>
    </form>
    <p v-if="done" class="muted ctr-price-done" role="status">{{ done }}</p>
    <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>

    <Teleport to="body">
      <ConfirmDialog
        v-if="warnings"
        :title="t('contractEdit.price.overwriteTitle')"
        :message="warnings.join(' ')"
        :confirm-label="t('contractEdit.price.overwrite')"
        :busy="busy"
        :error="error"
        @confirm="overwrite"
        @cancel="cancelOverwrite"
      />
      <ConfirmDialog
        v-if="deleting"
        :title="t('contractEdit.price.deleteTitle')"
        :message="t('contractEdit.price.deleteQuestion', { name: label, date: sampleDate(deleting.timestamp, locale), value: sampleValue(deleting) })"
        :confirm-label="t('contractEdit.delete')"
        :busy="busy"
        :error="deleteError"
        danger
        @confirm="confirmDelete"
        @cancel="deleting = null"
      />
    </Teleport>
  </section>
</template>
