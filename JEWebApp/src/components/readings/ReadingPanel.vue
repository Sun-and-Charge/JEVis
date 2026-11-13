<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { matchedRouteKey, onBeforeRouteLeave } from 'vue-router'
import { MAX_PHOTO_BYTES, NOTE_MAX, readingWarnings, saveReading, uploadReadingPhoto, type ReadingMeter, type SavedReading } from '../../api/readings'
import { browserTimeZone } from '../../analysis/settings'
import { useBusy, writeErrorText } from '../../edit/edit'
import { outsideExpected, parseNumber } from '../../readings/expected'
import {
  canRead,
  defaultReadingInput,
  expectedAt,
  formatRange,
  formatReading,
  formatReadingTime,
  noteProblem,
  readingInputMode,
  readingTimestamp,
  userNote,
  type ReadingSavedEvent,
} from '../../readings/readings'
import { mediumLabel } from '../../registers/format'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import MediumIcon from '../registers/MediumIcon.vue'
import DueBadge from './DueBadge.vue'
import ReadingPhoto from './ReadingPhoto.vue'
import '../../styles/edit.css'
import '../../styles/isoEdit.css'
import '../../styles/registersEdit.css'
import '../../styles/readings.css'

/*
 * Entry panel of a meter (E8.1): the last readings, the time (date or date-time by the row's mode, prefilled with the
 * suggestion or now), the reading (with "," or "."), the expected value and range at the chosen time, a note and a
 * photo (uploaded after the reading was saved). Plausibility warnings of the server (409) are confirmed with
 * "Trotzdem speichern" as in E1. "Speichern & weiter" asks the page to open the next due meter. Closing or leaving
 * the page with unsaved input asks first. Side panel on wide screens, full-width sheet on phones.
 */

const props = defineProps<{ meter: ReadingMeter; nextName?: string | null }>()
const emit = defineEmits<{ close: []; saved: [event: ReadingSavedEvent]; exchange: []; changed: [] }>()

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `rd-panel-${Math.random().toString(36).slice(2, 9)}`
const id = (suffix: string) => `${uid}-${suffix}`

const row = computed(() => props.meter.row)
const readable = computed(() => canRead(props.meter))
const unit = computed(() => row.value?.unit || '')
const inputMode = computed(() => readingInputMode(row.value?.mode))
const inputType = computed(() => (inputMode.value === 'DAY' ? 'date' : 'datetime-local'))

/* ---- form ---- */
const initialWhen = defaultReadingInput(props.meter, Date.now())
const when = ref(initialWhen)
const value = ref('')
const note = ref('')
const photo = ref<File | null>(null)
const photoUrl = ref<string | null>(null)
const warnings = ref<string[]>([])
const pendingNext = ref(false)
const error = ref<string | null>(null)
const problems = ref<{ when?: string; value?: string; note?: string; photo?: string }>({})
/* saved, but the photo upload failed: retry or go on without */
const photoFailed = ref<{ result: SavedReading; next: boolean } | null>(null)

const dirty = computed(() => !photoFailed.value && (value.value.trim() !== '' || note.value.trim() !== '' || photo.value !== null || when.value !== initialWhen))

/* ---- expected value at the chosen time ---- */
const expected = computed(() => expectedAt(props.meter, when.value))
const parsed = computed(() => parseNumber(value.value))
const outside = computed(() => parsed.value != null && outsideExpected(parsed.value, expected.value))
const last = computed(() => props.meter.readings[0] ?? null)
const belowLast = computed(() => !!row.value?.counter && parsed.value != null && typeof last.value?.value === 'number' && parsed.value < last.value.value)
const expectedNone = computed(() => {
  if (!row.value) return ''
  if (!row.value.counter && !row.value.period) return t('readings.expected.noPeriod')
  return t('readings.expected.none')
})
const timeHint = computed(() => (te('entry.modeHint.' + (row.value?.mode ?? '')) ? t('entry.modeHint.' + row.value!.mode) : t('entry.modeHint.SPECIFIC_DATETIME')))
const otherZone = computed(() => {
  const zone = row.value?.timeZone
  return zone && zone !== browserTimeZone() ? zone : null
})

const facts = computed(() =>
  [
    { key: 'location', label: t('readings.col.location'), value: props.meter.location },
    { key: 'serial', label: t('readings.col.serial'), value: props.meter.serialNumber },
    { key: 'point', label: t('readings.col.meterPoint'), value: props.meter.meterPoint },
    { key: 'row', label: t('entry.row'), value: row.value?.name ?? null },
  ].filter((f) => !!f.value),
)
const kind = computed(() => mediumLabel(props.meter, (k) => t(k), (k) => te(k)))
const problemText = computed(() => {
  if (row.value && !row.value.canEnter) return t('readings.problem.NO_RIGHT')
  const p = props.meter.problem ?? 'NO_TARGET'
  return te('readings.problem.' + p) ? t('readings.problem.' + p) : t('readings.problem.NO_TARGET')
})

function changed(field?: keyof typeof problems.value) {
  /* a changed input needs a new plausibility check */
  warnings.value = []
  error.value = null
  if (field && problems.value[field]) {
    const next = { ...problems.value }
    delete next[field]
    problems.value = next
  }
}

/* ---- photo ---- */
const fileInput = ref<HTMLInputElement | null>(null)

function releasePhoto() {
  if (photoUrl.value && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(photoUrl.value)
  photoUrl.value = null
}

function onPhoto(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = ''
  changed('photo')
  if (!file) return
  if (file.type && !file.type.startsWith('image/')) {
    problems.value = { ...problems.value, photo: t('readings.photo.notImage') }
    return
  }
  if (file.size > MAX_PHOTO_BYTES) {
    problems.value = { ...problems.value, photo: t('readings.photo.tooLarge') }
    return
  }
  releasePhoto()
  photo.value = file
  photoUrl.value = typeof URL.createObjectURL === 'function' ? URL.createObjectURL(file) : null
}

function removePhoto() {
  releasePhoto()
  photo.value = null
  changed('photo')
}

/* ---- save ---- */
async function save(confirm: boolean, next: boolean) {
  error.value = null
  const r = row.value
  if (!r || !readable.value) return
  const found: typeof problems.value = {}
  const timestamp = readingTimestamp(when.value, r.mode)
  if (!timestamp) found.when = t('entry.timeRequired')
  const number = parsed.value
  if (number == null) found.value = t('entry.valueRequired')
  const np = noteProblem(note.value, NOTE_MAX)
  if (np) found.note = t('readings.note.' + np, { max: NOTE_MAX })
  problems.value = found
  if (Object.keys(found).length) {
    await nextTick()
    const first = (['when', 'value', 'note'] as const).find((f) => found[f])
    if (first) document.getElementById(id(first))?.focus()
    return
  }
  pendingNext.value = next
  await run(async () => {
    let result: SavedReading
    try {
      const text = note.value.trim()
      const answer = await saveReading(auth.http, props.meter.id, { timestamp: timestamp!, value: number!, note: text || null, confirm }, locale.value)
      result = answer ?? { timestamp: timestamp!, value: number!, warnings: [] }
      warnings.value = []
    } catch (e) {
      const list = readingWarnings(e)
      if (list) {
        warnings.value = list.length ? list : [t('entry.warningUnknown')]
        return
      }
      error.value = writeErrorText(e, t)
      return
    }
    if (photo.value) {
      try {
        await uploadReadingPhoto(auth.http, props.meter.id, result.timestamp, photo.value)
      } catch (e) {
        photoFailed.value = { result, next }
        error.value = t('readings.photo.uploadError', { reason: writeErrorText(e, t) })
        emit('changed')
        return
      }
    }
    emit('saved', { meter: props.meter, result, next, photoSkipped: false })
  })
}

async function retryPhoto() {
  const failed = photoFailed.value
  if (!failed || !photo.value) return
  await run(async () => {
    try {
      await uploadReadingPhoto(auth.http, props.meter.id, failed.result.timestamp, photo.value!)
      emit('saved', { meter: props.meter, result: failed.result, next: failed.next, photoSkipped: false })
    } catch (e) {
      error.value = t('readings.photo.uploadError', { reason: writeErrorText(e, t) })
    }
  })
}

function skipPhoto() {
  const failed = photoFailed.value
  if (failed) emit('saved', { meter: props.meter, result: failed.result, next: failed.next, photoSkipped: true })
}

/* ---- guard ---- */
const leaveAsk = ref<((drop: boolean) => void) | null>(null)
function confirmDrop(): Promise<boolean> {
  if (!dirty.value) return Promise.resolve(true)
  leaveAsk.value?.(false)
  return new Promise((resolve) => {
    leaveAsk.value = (drop: boolean) => {
      leaveAsk.value = null
      resolve(drop)
    }
  })
}

async function requestClose() {
  if (busy.value) return
  if (photoFailed.value) return skipPhoto()
  if (await confirmDrop()) emit('close')
}

defineExpose({ confirmDrop })

const routeRecord = inject(matchedRouteKey, null)
if (routeRecord?.value) onBeforeRouteLeave(() => confirmDrop())

function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  releasePhoto()
})

const timeOf = (timestamp: string) => formatReadingTime(timestamp, props.meter, locale.value)
const describedBy = (field: keyof typeof problems.value, hint?: string) => [problems.value[field] ? id(field) + '-err' : '', hint ?? ''].filter(Boolean).join(' ') || undefined
</script>

<template>
  <EditDialog :title="meter.name" :busy="busy" class="rd-panel-backdrop" @close="requestClose">
    <div class="rd-panel-meta">
      <span class="rd-kind"><MediumIcon :medium="meter.medium" />{{ kind }}</span>
      <DueBadge :due="meter.due" />
      <button v-if="meter.canExchange" type="button" class="edit-btn is-small rd-exchange" :disabled="busy" @click="emit('exchange')">
        {{ t('registerEdit.exchange.action') }}
      </button>
    </div>
    <dl v-if="facts.length" class="edit-facts rd-facts">
      <template v-for="f in facts" :key="f.key">
        <dt>{{ f.label }}</dt>
        <dd>{{ f.value }}</dd>
      </template>
    </dl>

    <p v-if="!readable" class="alert alert-error rd-problem" role="alert">{{ problemText }}</p>

    <form v-else :id="id('form')" class="rd-form" novalidate @submit.prevent="save(false, !!nextName)">
      <div class="edit-field">
        <label :for="id('value')">{{ t('readings.reading') }}<template v-if="unit"> [{{ unit }}]</template></label>
        <input
          :id="id('value')"
          v-model="value"
          class="rd-value"
          type="text"
          inputmode="decimal"
          autocomplete="off"
          required
          autofocus
          :disabled="busy || !!photoFailed"
          :aria-invalid="problems.value ? 'true' : undefined"
          :aria-describedby="describedBy('value', id('expected'))"
          @input="changed('value')"
        />
        <p v-if="problems.value" :id="id('value') + '-err'" class="iso-field-error">{{ problems.value }}</p>
        <div :id="id('expected')" class="rd-expected" :class="{ 'is-outside': outside || belowLast }" aria-live="polite">
          <template v-if="expected">
            <span class="rd-expected-label">{{ t('readings.expected.label') }}</span>
            <strong>{{ formatReading(expected.value, meter, locale) }}</strong>
            <span class="rd-expected-range">{{ t('readings.expected.range', { range: formatRange(expected, meter, locale) }) }}</span>
          </template>
          <span v-else class="muted">{{ expectedNone }}</span>
          <span v-if="belowLast" class="rd-expected-warn">{{ t('readings.expected.belowLast') }}</span>
          <span v-else-if="outside" class="rd-expected-warn">{{ t('readings.expected.outside') }}</span>
        </div>
      </div>

      <div class="edit-field">
        <label :for="id('when')">{{ inputType === 'date' ? t('readings.date') : t('readings.dateTime') }}</label>
        <input
          :id="id('when')"
          v-model="when"
          :type="inputType"
          :step="inputType === 'datetime-local' ? 60 : undefined"
          required
          :disabled="busy || !!photoFailed"
          :aria-invalid="problems.when ? 'true' : undefined"
          :aria-describedby="describedBy('when', id('when-hint'))"
          @input="changed('when')"
        />
        <p :id="id('when-hint')" class="edit-hint">
          {{ timeHint }}<template v-if="otherZone"> {{ t('entry.zoneHint', { zone: otherZone }) }}</template>
        </p>
        <p v-if="problems.when" :id="id('when') + '-err'" class="iso-field-error">{{ problems.when }}</p>
      </div>

      <div class="edit-field">
        <label :for="id('note')">{{ t('readings.note.label') }}</label>
        <input
          :id="id('note')"
          v-model="note"
          type="text"
          autocomplete="off"
          :maxlength="NOTE_MAX"
          :placeholder="t('readings.note.placeholder')"
          :disabled="busy || !!photoFailed"
          :aria-invalid="problems.note ? 'true' : undefined"
          :aria-describedby="describedBy('note')"
          @input="changed('note')"
        />
        <p v-if="problems.note" :id="id('note') + '-err'" class="iso-field-error">{{ problems.note }}</p>
      </div>

      <div v-if="meter.canPhoto" class="edit-field rd-photo-field">
        <span class="reg-edit-label" :id="id('photo-label')">{{ t('readings.photo.label') }}</span>
        <div class="rd-photo-take">
          <input
            :id="id('photo')"
            ref="fileInput"
            class="rd-file"
            type="file"
            accept="image/*"
            capture="environment"
            :disabled="busy || !!photoFailed"
            :aria-labelledby="id('photo-label') + ' ' + id('photo-btn')"
            @change="onPhoto"
          />
          <button :id="id('photo-btn')" type="button" class="edit-btn" :disabled="busy || !!photoFailed" @click="fileInput?.click()">
            {{ photo ? t('readings.photo.retake') : t('readings.photo.take') }}
          </button>
          <button v-if="photo && !photoFailed" type="button" class="edit-btn is-small is-danger" :disabled="busy" @click="removePhoto">
            {{ t('readings.photo.remove') }}
          </button>
        </div>
        <img v-if="photoUrl" :src="photoUrl" :alt="t('readings.photo.preview')" class="rd-photo-preview" />
        <p class="edit-hint">{{ t('readings.photo.hint') }}</p>
        <p v-if="problems.photo" class="iso-field-error" role="alert">{{ problems.photo }}</p>
      </div>

      <div v-if="warnings.length" class="alert alert-error entry-warnings" role="alert">
        <strong>{{ t('entry.warningsTitle') }}</strong>
        <ul class="edit-warnings">
          <li v-for="(w, i) in warnings" :key="i">{{ w }}</li>
        </ul>
      </div>
      <div v-if="photoFailed" class="alert alert-error rd-photo-failed" role="alert">
        <p>{{ t('readings.photo.savedWithout') }}</p>
        <p v-if="error" class="rd-photo-reason">{{ error }}</p>
        <div class="edit-actions">
          <button type="button" class="edit-btn rd-photo-retry" :disabled="busy" @click="retryPhoto">{{ t('readings.photo.retry') }}</button>
          <button type="button" class="edit-btn rd-photo-skip" :disabled="busy" @click="skipPhoto">{{ t('readings.photo.skip') }}</button>
        </div>
      </div>
      <p v-else-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>

    <section class="rd-history" :aria-labelledby="id('history')">
      <h3 :id="id('history')">{{ t('readings.history.title') }}</h3>
      <p v-if="!meter.readings.length" class="muted">{{ t('readings.history.none') }}</p>
      <table v-else class="rd-history-table">
        <thead>
          <tr>
            <th scope="col">{{ t('readings.col.date') }}</th>
            <th scope="col" class="num">{{ t('readings.reading') }}</th>
            <th scope="col">{{ t('readings.note.label') }}</th>
            <th scope="col"><span class="visually-hidden">{{ t('readings.photo.label') }}</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in meter.readings" :key="s.timestamp">
            <td class="nowrap">{{ timeOf(s.timestamp) }}</td>
            <td class="num nowrap">{{ formatReading(s.value, meter, locale) }}</td>
            <td class="rd-note">
              <span v-if="s.manual" class="rd-manual" :title="t('readings.history.manual')">{{ t('readings.history.manualShort') }}</span>
              {{ userNote(s) }}
            </td>
            <td class="rd-photo-col">
              <ReadingPhoto v-if="s.photo" :meter-id="meter.id" :timestamp="s.timestamp" :label="timeOf(s.timestamp)" />
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <template #footer>
      <span class="edit-left rd-state" :class="{ 'is-dirty': dirty }" role="status">
        {{ dirty ? t('registerEdit.unsaved') : '' }}
      </span>
      <button type="button" class="edit-btn" :disabled="busy" @click="requestClose">{{ readable ? t('edit.cancel') : t('edit.close') }}</button>
      <template v-if="readable && !photoFailed">
        <button v-if="warnings.length" type="button" class="btn btn-primary is-danger entry-force" :disabled="busy" @click="save(true, pendingNext)">
          {{ busy ? t('edit.saving') : t('entry.saveAnyway') }}
        </button>
        <template v-else>
          <button v-if="nextName" type="button" class="edit-btn rd-save-only" :disabled="busy" @click="save(false, false)">{{ t('edit.save') }}</button>
          <button type="submit" class="btn btn-primary edit-submit rd-save" :form="id('form')" :disabled="busy" :title="nextName ? t('readings.nextTitle', { name: nextName }) : undefined">
            {{ busy ? t('edit.saving') : nextName ? t('readings.saveNext') : t('edit.save') }}
          </button>
        </template>
      </template>
    </template>
  </EditDialog>

  <ConfirmDialog
    v-if="leaveAsk"
    :title="t('registerEdit.leave.title')"
    :message="t('readings.leave')"
    :confirm-label="t('registerEdit.leave.confirm')"
    danger
    @confirm="leaveAsk?.(true)"
    @cancel="leaveAsk?.(false)"
  />
</template>
