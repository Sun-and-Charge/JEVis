<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { HttpError } from '../../api/http'
import { saveMeterExchange, type RegisterRow } from '../../api/registers'
import { fromDateTimeInput, isoWithOffset, toDateTimeInput, toMinute, useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { checkExchange, warningsOf, type ExchangeInput, type RegisterProblem } from '../../registers/edit'
import { targetText } from '../../registers/format'
import { useAuthStore } from '../../stores/auth'
import EditDialog from '../edit/EditDialog.vue'
import '../../styles/isoEdit.css'
import '../../styles/registersEdit.css'

/*
 * "Zählerwechsel" (E5) as the Control Center's MeterFormReadingsTab: the time of the exchange (not in the future),
 * the old meter's final reading (written one second before) and the new meter's first reading, both into the
 * meter's data row with the note "Meter Tausch". Plausibility warnings of the server (409) can be overridden with
 * "Trotzdem speichern" as in the entry dialog (E1).
 */

const props = defineProps<{ row: RegisterRow }>()
const emit = defineEmits<{ close: []; saved: [time: string] }>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `reg-xchg-${Math.random().toString(36).slice(2, 9)}`

const input = ref<ExchangeInput>({ when: toDateTimeInput(toMinute(Date.now())), oldReading: '', newReading: '' })
const problems = ref<Partial<Record<keyof ExchangeInput, RegisterProblem>>>({})
const warnings = ref<string[]>([])
const error = ref<string | null>(null)
const maxTime = computed(() => toDateTimeInput(Date.now()))
const unit = computed(() => props.row.last?.unit || '')
const rowName = computed(() => targetText(props.row.target, (k) => t(k)))

function changed(field: keyof ExchangeInput) {
  /* a changed input needs a new plausibility check */
  warnings.value = []
  if (problems.value[field]) {
    const next = { ...problems.value }
    delete next[field]
    problems.value = next
  }
}

function set(field: keyof ExchangeInput, e: Event) {
  input.value = { ...input.value, [field]: (e.target as HTMLInputElement).value }
  changed(field)
}

const fieldId = (field: keyof ExchangeInput) => `${uid}-${field}`

async function save(confirm: boolean) {
  error.value = null
  const check = checkExchange(input.value, locale.value, Date.now(), fromDateTimeInput)
  problems.value = Object.fromEntries(Object.entries(check.problems).filter(([, p]) => !!p)) as Partial<Record<keyof ExchangeInput, RegisterProblem>>
  if (!check.values) {
    const first = (['when', 'oldReading', 'newReading'] as const).find((f) => problems.value[f])
    await nextTick()
    if (first) document.getElementById(fieldId(first))?.focus()
    return
  }
  const values = check.values
  const timestamp = isoWithOffset(values.timestamp)
  await run(async () => {
    try {
      await saveMeterExchange(auth.http, props.row.id, { timestamp, oldReading: values.oldReading, newReading: values.newReading, confirm }, locale.value)
      warnings.value = []
      emit('saved', timestamp)
    } catch (e) {
      if (writeErrorKind(e) === 'conflict') {
        const list = warningsOf(e instanceof HttpError ? e.json() : null)
        warnings.value = list.length ? list : [t('entry.warningUnknown')]
        return
      }
      error.value = writeErrorText(e, t)
    }
  })
}

const problemText = (field: keyof ExchangeInput) => {
  const p = problems.value[field]
  return p ? t(p.key, p.params ?? {}) : ''
}
const describedBy = (field: keyof ExchangeInput, hint?: string) => [problems.value[field] ? fieldId(field) + '-err' : '', hint ?? ''].filter(Boolean).join(' ') || undefined
</script>

<template>
  <EditDialog :title="t('registerEdit.exchange.title')" :busy="busy" class="reg-edit-backdrop" :described-by="uid + '-intro'" @close="emit('close')">
    <form :id="uid + '-form'" novalidate @submit.prevent="save(false)">
      <p :id="uid + '-intro'" class="edit-message">{{ t('registerEdit.exchange.intro') }}</p>
      <dl class="edit-facts">
        <dt>{{ t('registers.col.name') }}</dt>
        <dd>{{ row.name }}</dd>
        <dt>{{ t('registers.detail.target') }}</dt>
        <dd>{{ rowName }}</dd>
      </dl>

      <div class="edit-field">
        <label :for="fieldId('when')">{{ t('registerEdit.exchange.time') }}</label>
        <input
          :id="fieldId('when')"
          :value="input.when"
          type="datetime-local"
          step="60"
          :max="maxTime"
          required
          :disabled="busy"
          :aria-invalid="problems.when ? 'true' : undefined"
          :aria-describedby="describedBy('when', uid + '-when-hint')"
          @input="set('when', $event)"
        />
        <p :id="uid + '-when-hint'" class="edit-hint">{{ t('registerEdit.exchange.timeHint') }}</p>
        <p v-if="problems.when" :id="fieldId('when') + '-err'" class="iso-field-error">{{ problemText('when') }}</p>
      </div>

      <div class="edit-field">
        <label :for="fieldId('oldReading')">{{ t('registerEdit.exchange.oldReading') }}<template v-if="unit"> [{{ unit }}]</template></label>
        <input
          :id="fieldId('oldReading')"
          :value="input.oldReading"
          type="text"
          inputmode="decimal"
          autocomplete="off"
          required
          autofocus
          :disabled="busy"
          :aria-invalid="problems.oldReading ? 'true' : undefined"
          :aria-describedby="describedBy('oldReading', uid + '-old-hint')"
          @input="set('oldReading', $event)"
        />
        <p :id="uid + '-old-hint'" class="edit-hint">{{ t('registerEdit.exchange.oldHint') }}</p>
        <p v-if="problems.oldReading" :id="fieldId('oldReading') + '-err'" class="iso-field-error">{{ problemText('oldReading') }}</p>
      </div>

      <div class="edit-field">
        <label :for="fieldId('newReading')">{{ t('registerEdit.exchange.newReading') }}<template v-if="unit"> [{{ unit }}]</template></label>
        <input
          :id="fieldId('newReading')"
          :value="input.newReading"
          type="text"
          inputmode="decimal"
          autocomplete="off"
          required
          :disabled="busy"
          :aria-invalid="problems.newReading ? 'true' : undefined"
          :aria-describedby="describedBy('newReading', uid + '-new-hint')"
          @input="set('newReading', $event)"
        />
        <p :id="uid + '-new-hint'" class="edit-hint">{{ t('registerEdit.exchange.newHint') }}</p>
        <p v-if="problems.newReading" :id="fieldId('newReading') + '-err'" class="iso-field-error">{{ problemText('newReading') }}</p>
      </div>

      <div v-if="warnings.length" class="alert alert-error entry-warnings" role="alert">
        <strong>{{ t('entry.warningsTitle') }}</strong>
        <ul class="edit-warnings">
          <li v-for="(w, i) in warnings" :key="i">{{ w }}</li>
        </ul>
      </div>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="edit-btn" :disabled="busy" @click="emit('close')">{{ t('edit.cancel') }}</button>
      <button v-if="warnings.length" type="button" class="btn btn-primary is-danger entry-force" :disabled="busy" @click="save(true)">
        {{ busy ? t('edit.saving') : t('entry.saveAnyway') }}
      </button>
      <button v-else type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="busy">{{ busy ? t('edit.saving') : t('edit.save') }}</button>
    </template>
  </EditDialog>
</template>
