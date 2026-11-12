<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatNumber } from '../../analysis/format'
import { loadEntryInfo, saveValue, type EntryInfo, type SavedValue } from '../../api/entry'
import { HttpError } from '../../api/http'
import { parseUserNumber, useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { entryInputType, entryMode, entryTimestamp, formatEntryTime, toEntryInput } from '../../edit/entry'
import { browserTimeZone } from '../../analysis/settings'
import { useAuthStore } from '../../stores/auth'
import EditDialog from './EditDialog.vue'

/*
 * "Wert erfassen" (E1) like the Control Center's EnterDataDialog: loads what the row needs (mode, unit, last value,
 * suggested time), the timestamp input matches the mode (date-time, day, month, year). Plausibility warnings of
 * the server (409) are shown and can be overridden with "Trotzdem speichern".
 */

const props = defineProps<{ rowId: number; rowName?: string }>()
const emit = defineEmits<{ close: []; saved: [result: SavedValue, info: EntryInfo] }>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()

const info = shallowRef<EntryInfo | null>(null)
const state = ref<'loading' | 'ready' | 'notFound' | 'error'>('loading')
const when = ref('')
const value = ref('')
const error = ref<string | null>(null)
const warnings = ref<string[]>([])
const uid = `entry-dlg-${Math.random().toString(36).slice(2, 9)}`

async function load() {
  state.value = 'loading'
  try {
    const answer = await loadEntryInfo(auth.http, props.rowId)
    info.value = answer
    // without samples there is no suggestion: now in the site's zone
    when.value = toEntryInput(answer.suggestedTimestamp ?? Date.now(), answer.mode, answer.timeZone)
    state.value = 'ready'
  } catch (e) {
    state.value = e instanceof HttpError && (e.status === 404 || e.status === 403) ? 'notFound' : 'error'
  }
}

onMounted(load)

const mode = computed(() => entryMode(info.value?.mode))
const inputType = computed(() => entryInputType(mode.value))
const title = computed(() => t('entry.title'))
const unit = computed(() => info.value?.unit || '')
const lastText = computed(() => {
  const last = info.value?.last
  if (!last) return ''
  const v = typeof last.value === 'number' ? formatNumber(last.value, locale.value, { min: 0, max: 6 }) : (last.value ?? '')
  return `${v}${unit.value ? ' ' + unit.value : ''} · ${formatEntryTime(last.timestamp, mode.value, locale.value, info.value?.timeZone)}`
})
const limitsText = computed(() => {
  const l = info.value?.limits
  if (!l || (l.min == null && l.max == null)) return ''
  const f = (n: number | null) => (n == null ? '–' : formatNumber(n, locale.value, { min: 0, max: 6 }))
  return `${f(l.min)} … ${f(l.max)}${unit.value ? ' ' + unit.value : ''}`
})
const timeHint = computed(() => t('entry.modeHint.' + mode.value))
/** the site's zone, shown when it differs from the browser's */
const otherZone = computed(() => {
  const zone = info.value?.timeZone
  return zone && zone !== browserTimeZone() ? zone : null
})

function changed() {
  // a changed input needs a new plausibility check
  warnings.value = []
}

async function save(confirm: boolean) {
  error.value = null
  if (!info.value) return
  const timestamp = entryTimestamp(when.value, mode.value)
  if (!timestamp) {
    error.value = t('entry.timeRequired')
    return
  }
  const number = parseUserNumber(value.value, locale.value)
  if (number == null) {
    error.value = t('entry.valueRequired')
    return
  }
  await run(async () => {
    try {
      const result = await saveValue(auth.http, props.rowId, { timestamp, value: number, confirm }, locale.value)
      warnings.value = []
      emit('saved', result ?? { timestamp, value: number, warnings: [] }, info.value!)
    } catch (e) {
      if (writeErrorKind(e) === 'conflict') {
        const body = e instanceof HttpError ? e.json<{ warnings?: unknown }>() : null
        const list = Array.isArray(body?.warnings) ? body!.warnings.filter((w): w is string => typeof w === 'string') : []
        warnings.value = list.length ? list : [t('entry.warningUnknown')]
        return
      }
      error.value = writeErrorText(e, t)
    }
  })
}
</script>

<template>
  <EditDialog :title="title" :busy="busy" @close="emit('close')">
    <p v-if="state === 'loading'" class="muted" role="status">{{ t('entry.loading') }}</p>
    <p v-else-if="state === 'notFound'" class="alert alert-error" role="alert">{{ t('entry.notFound') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('entry.loadError') }}
      <button type="button" class="btn-link" @click="load">{{ t('modules.retry') }}</button>
    </div>
    <template v-else-if="info">
      <p v-if="!info.canEnter" class="alert alert-error" role="alert">{{ t('entry.noRight') }}</p>
      <form v-else :id="uid + '-form'" novalidate @submit.prevent="save(false)">
        <dl class="edit-facts">
          <dt>{{ t('entry.row') }}</dt>
          <dd>{{ info.name || rowName }}</dd>
          <template v-if="unit">
            <dt>{{ t('entry.unit') }}</dt>
            <dd>{{ unit }}</dd>
          </template>
          <dt>{{ t('entry.last') }}</dt>
          <dd class="entry-last">{{ lastText || t('entry.noLast') }}</dd>
          <template v-if="info.counter">
            <dt>{{ t('entry.kind') }}</dt>
            <dd>{{ t('entry.counter') }}</dd>
          </template>
          <template v-if="limitsText">
            <dt>{{ t('entry.limits') }}</dt>
            <dd>{{ limitsText }}</dd>
          </template>
        </dl>

        <div class="edit-field">
          <label :for="uid + '-time'">{{ t('entry.time') }}</label>
          <input
            :id="uid + '-time'"
            v-model="when"
            class="entry-time"
            :type="inputType"
            :step="inputType === 'datetime-local' ? 60 : undefined"
            :min="inputType === 'number' ? 1900 : undefined"
            :max="inputType === 'number' ? 2999 : undefined"
            :inputmode="inputType === 'number' ? 'numeric' : undefined"
            required
            :disabled="busy"
            :aria-describedby="uid + '-time-hint'"
            @input="changed"
          />
          <p :id="uid + '-time-hint'" class="edit-hint">
            {{ timeHint }}<template v-if="otherZone"> {{ t('entry.zoneHint', { zone: otherZone }) }}</template>
          </p>
        </div>

        <div class="edit-field">
          <label :for="uid + '-value'">{{ t('entry.value') }}<template v-if="unit"> [{{ unit }}]</template></label>
          <input :id="uid + '-value'" v-model="value" class="entry-value" type="text" inputmode="decimal" autocomplete="off" required :disabled="busy" autofocus @input="changed" />
          <p v-if="info.counter" class="edit-hint">{{ t('entry.counterHint') }}</p>
        </div>

        <div v-if="warnings.length" class="alert alert-error entry-warnings" role="alert">
          <strong>{{ t('entry.warningsTitle') }}</strong>
          <ul class="edit-warnings">
            <li v-for="(w, i) in warnings" :key="i">{{ w }}</li>
          </ul>
        </div>
        <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
      </form>
    </template>
    <template #footer>
      <button type="button" class="edit-btn" :disabled="busy" @click="emit('close')">{{ t('edit.cancel') }}</button>
      <template v-if="state === 'ready' && info?.canEnter">
        <button v-if="warnings.length" type="button" class="btn btn-primary is-danger entry-force" :disabled="busy" @click="save(true)">
          {{ busy ? t('edit.saving') : t('entry.saveAnyway') }}
        </button>
        <button v-else type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="busy">{{ busy ? t('edit.saving') : t('edit.save') }}</button>
      </template>
    </template>
  </EditDialog>
</template>
