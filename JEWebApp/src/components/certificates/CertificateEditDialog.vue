<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  createCertificate,
  deleteCertificate,
  deleteCertificateFile,
  ENERGY_CLASSES,
  loadCertificateFile,
  updateCertificate,
  uploadCertificateFile,
  type Certificate,
  type CertificateBuilding,
} from '../../api/certificates'
import { HttpError } from '../../api/http'
import { localName } from '../../api/navigation'
import type { RoundOptionPerson } from '../../api/readingRounds'
import {
  certificateForm,
  defaultValidUntil,
  emptyForm,
  formData,
  numberFieldsOf,
  sameForm,
  serverProblem,
  type CertificateForm,
  type FormProblem,
  type NumberField,
} from '../../certificates/certificates'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLeaveGuard } from '../../edit/useLeaveGuard'
import { formatLocal } from '../../modules/dates'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import PeoplePicker from '../edit/PeoplePicker.vue'
import '../../styles/isoEdit.css'

/*
 * Energy certificate (BA-07) in the side panel: create (building chosen here) or change, with the sections
 * Ausweis, Kennwerte, Gültigkeit und Erinnerung, Zuständig, Datei, Anmerkungen. "Gültig bis" is suggested from the
 * issue date (10 years) as long as the user has not typed one. Unsaved changes ask before closing; a 409 reloads.
 */

const props = defineProps<{
  /** null: create */
  certificate: Certificate | null
  buildings: readonly CertificateBuilding[]
  people: readonly RoundOptionPerson[]
  /** building preselected for a new certificate (site picker) */
  building?: number | null
}>()

const emit = defineEmits<{
  close: []
  saved: [certificate: Certificate, created: boolean]
  deleted: [name: string]
  /** reload the list (conflict, vanished, file changed) */
  changed: []
}>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `cert-${Math.random().toString(36).slice(2, 9)}`
const id = (s: string) => `${uid}-${s}`

const current = shallowRef<Certificate | null>(props.certificate)
const creatable = computed(() => props.buildings.filter((b) => b.canCreate))
const startBuilding = props.building != null && creatable.value.some((b) => b.id === props.building) ? props.building : creatable.value.length === 1 ? creatable.value[0].id : null
const initial = ref<CertificateForm>(current.value ? certificateForm(current.value, locale.value) : emptyForm(startBuilding))
const form = ref<CertificateForm>(JSON.parse(JSON.stringify(initial.value)))
const untilTouched = ref(!!form.value.validUntil)

const isNew = computed(() => current.value == null)
const canWrite = computed(() => isNew.value || current.value?.canWrite === true)
const canDelete = computed(() => !isNew.value && current.value?.canDelete === true)
const dirty = computed(() => canWrite.value && !sameForm(initial.value, form.value))
const title = computed(() => (isNew.value ? t('certificates.createTitle') : t('certificates.editTitle', { name: current.value!.name })))
const numberFields = computed(() => numberFieldsOf(form.value.type))

const problems = ref<FormProblem[]>([])
const error = ref<string | null>(null)
const info = ref<string | null>(null)
const deleting = ref(false)
const deleteError = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const { leaveAsk, confirmDrop } = useLeaveGuard(dirty)

const problemOf = (field: string) => problems.value.find((p) => p.field === field) ?? null
const problemText = (field: string) => {
  const p = problemOf(field)
  return p ? t('certificates.problem.' + p.key) : null
}

function unitOf(f: NumberField): string {
  return f === 'referenceArea' ? t('certificates.unit.area') : f === 'co2' ? t('certificates.unit.co2') : t('certificates.unit.energy')
}

function onIssued() {
  if (!untilTouched.value) form.value.validUntil = defaultValidUntil(form.value.issued)
  clear('issued')
}

function clear(field: string) {
  problems.value = problems.value.filter((p) => p.field !== field)
  info.value = null
}

async function requestClose() {
  if (busy.value) return
  if (await confirmDrop()) emit('close')
}

function take(c: Certificate) {
  current.value = c
  initial.value = certificateForm(c, locale.value)
  form.value = JSON.parse(JSON.stringify(initial.value))
  untilTouched.value = true
}

async function save() {
  error.value = null
  info.value = null
  const { data, problems: found } = formData(form.value, locale.value)
  problems.value = found
  if (!data) return
  await run(async () => {
    try {
      if (isNew.value) {
        const created = await createCertificate(auth.http, form.value.building!, data)
        take(created)
        emit('saved', created, true)
      } else {
        const saved = await updateCertificate(auth.http, current.value!.id, current.value!.version ?? '', data)
        take(saved)
        emit('saved', saved, false)
        info.value = t('certificates.saved', { name: saved.name })
      }
    } catch (e) {
      const p = e instanceof HttpError && e.status === 400 ? serverProblem(e.body) : null
      if (p) {
        problems.value = [p]
        return
      }
      const kind = writeErrorKind(e)
      if (kind === 'conflict' && e instanceof HttpError) {
        const fresh = e.json<Certificate>()
        if (fresh && typeof fresh === 'object' && fresh.id) take(fresh)
        emit('changed')
      } else if (kind === 'notFound') {
        emit('changed')
      }
      error.value = writeErrorText(e, t)
    }
  })
}

async function openFile() {
  if (!current.value) return
  error.value = null
  try {
    const blob = await loadCertificateFile(auth.http, current.value.id)
    const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    error.value = writeErrorText(e, t)
  }
}

async function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !current.value) return
  error.value = null
  info.value = null
  if (file.size > 20 * 1024 * 1024) {
    error.value = t('certificates.file.tooLarge')
    return
  }
  if (!/\.pdf$/i.test(file.name) && file.type !== 'application/pdf') {
    error.value = t('certificates.file.notPdf')
    return
  }
  await run(async () => {
    try {
      const updated = await uploadCertificateFile(auth.http, current.value!.id, file)
      current.value = { ...current.value!, file: updated.file }
      info.value = t('certificates.file.uploaded')
      emit('changed')
    } catch (err) {
      error.value = err instanceof HttpError && err.status === 400 ? t('certificates.file.notPdf') : err instanceof HttpError && err.status === 413 ? t('certificates.file.tooLarge') : writeErrorText(err, t)
    }
  })
}

async function removeFile() {
  if (!current.value) return
  await run(async () => {
    try {
      await deleteCertificateFile(auth.http, current.value!.id)
      current.value = { ...current.value!, file: null }
      info.value = t('certificates.file.removed')
      emit('changed')
    } catch (e) {
      error.value = writeErrorText(e, t)
    }
  })
}

async function confirmDelete() {
  const c = current.value
  if (!c) return
  await run(async () => {
    try {
      await deleteCertificate(auth.http, c.id, c.version ?? '')
      deleting.value = false
      emit('deleted', c.name)
    } catch (e) {
      deleteError.value = writeErrorText(e, t)
      if (writeErrorKind(e) === 'conflict') emit('changed')
    }
  })
}

const createdText = computed(() => {
  const d = current.value?.data
  return d?.created ? t('certificates.createdBy', { date: formatLocal(d.created.slice(0, 16), locale.value), user: d.createdBy ?? '' }) : null
})
</script>

<template>
  <EditDialog :title="title" :busy="busy" wide @close="requestClose">
    <form :id="id('form')" class="cert-form" novalidate @submit.prevent="save">
      <section class="iso-edit-section" :aria-labelledby="id('s1')">
        <h3 :id="id('s1')">{{ t('certificates.section.certificate') }}</h3>
        <div class="iso-edit-grid">
          <div class="edit-field iso-edit-wide">
            <label :for="id('building')">{{ t('certificates.field.building') }}<span class="iso-req" aria-hidden="true"> *</span></label>
            <select v-if="isNew" :id="id('building')" v-model.number="form.building" :disabled="busy" :aria-invalid="problemOf('building') ? 'true' : undefined" @change="clear('building')">
              <option :value="null" disabled>–</option>
              <option v-for="b in creatable" :key="b.id" :value="b.id">{{ localName(b, locale) }}</option>
            </select>
            <input v-else :id="id('building')" type="text" :value="current?.building ? localName(current.building, locale) : '–'" readonly aria-readonly="true" />
            <p v-if="problemText('building')" class="iso-field-error">{{ problemText('building') }}</p>
          </div>
          <div class="edit-field">
            <label :for="id('type')">{{ t('certificates.field.type') }}<span class="iso-req" aria-hidden="true"> *</span></label>
            <select :id="id('type')" v-model="form.type" :disabled="busy || !canWrite" autofocus @change="clear('type')">
              <option value="CONSUMPTION">{{ t('certificates.type.CONSUMPTION') }}</option>
              <option value="DEMAND">{{ t('certificates.type.DEMAND') }}</option>
            </select>
            <p v-if="problemText('type')" class="iso-field-error">{{ problemText('type') }}</p>
          </div>
          <div class="edit-field">
            <label :for="id('use')">{{ t('certificates.field.use') }}</label>
            <select :id="id('use')" v-model="form.use" :disabled="busy || !canWrite">
              <option value="">–</option>
              <option value="RESIDENTIAL">{{ t('certificates.use.RESIDENTIAL') }}</option>
              <option value="NON_RESIDENTIAL">{{ t('certificates.use.NON_RESIDENTIAL') }}</option>
            </select>
          </div>
          <div class="edit-field">
            <label :for="id('reg')">{{ t('certificates.field.registrationNumber') }}</label>
            <input :id="id('reg')" v-model="form.registrationNumber" type="text" maxlength="100" autocomplete="off" :readonly="!canWrite" :disabled="busy" />
          </div>
          <div class="edit-field">
            <label :for="id('issuer')">{{ t('certificates.field.issuer') }}</label>
            <input :id="id('issuer')" v-model="form.issuer" type="text" maxlength="500" autocomplete="off" :readonly="!canWrite" :disabled="busy" />
          </div>
        </div>
      </section>

      <section class="iso-edit-section" :aria-labelledby="id('s2')">
        <h3 :id="id('s2')">{{ t('certificates.section.values') }}</h3>
        <div class="iso-edit-grid">
          <div class="edit-field">
            <label :for="id('class')">{{ t('certificates.field.energyClass') }}</label>
            <select :id="id('class')" v-model="form.energyClass" :disabled="busy || !canWrite" @change="clear('energyClass')">
              <option value="">–</option>
              <option v-for="c in ENERGY_CLASSES" :key="c" :value="c">{{ c }}</option>
            </select>
            <p v-if="problemText('energyClass')" class="iso-field-error">{{ problemText('energyClass') }}</p>
          </div>
          <div v-for="f in numberFields" :key="f" class="edit-field">
            <label :for="id(f)">{{ t('certificates.field.' + f) }} <span class="muted">({{ unitOf(f) }})</span></label>
            <input
              :id="id(f)"
              v-model="form.numbers[f]"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              :readonly="!canWrite"
              :disabled="busy"
              :aria-invalid="problemOf(f) ? 'true' : undefined"
              @input="clear(f)"
            />
            <p v-if="problemText(f)" class="iso-field-error">{{ problemText(f) }}</p>
          </div>
        </div>
      </section>

      <section class="iso-edit-section" :aria-labelledby="id('s3')">
        <h3 :id="id('s3')">{{ t('certificates.section.validity') }}</h3>
        <div class="iso-edit-grid">
          <div class="edit-field">
            <label :for="id('issued')">{{ t('certificates.field.issued') }}</label>
            <input :id="id('issued')" v-model="form.issued" type="date" :readonly="!canWrite" :disabled="busy" :aria-invalid="problemOf('issued') ? 'true' : undefined" @change="onIssued" />
            <p v-if="problemText('issued')" class="iso-field-error">{{ problemText('issued') }}</p>
          </div>
          <div class="edit-field">
            <label :for="id('until')">{{ t('certificates.field.validUntil') }}<span class="iso-req" aria-hidden="true"> *</span></label>
            <input
              :id="id('until')"
              v-model="form.validUntil"
              type="date"
              required
              aria-required="true"
              :readonly="!canWrite"
              :disabled="busy"
              :aria-invalid="problemOf('validUntil') ? 'true' : undefined"
              :aria-describedby="id('until-hint')"
              @input="untilTouched = true; clear('validUntil')"
            />
            <p :id="id('until-hint')" class="edit-hint">{{ t('certificates.field.validUntilHint') }}</p>
            <p v-if="problemText('validUntil')" class="iso-field-error">{{ problemText('validUntil') }}</p>
          </div>
          <div class="edit-field iso-edit-wide">
            <label :for="id('rem')">{{ t('certificates.field.reminderMonths') }}</label>
            <input :id="id('rem')" v-model="form.reminderMonths" type="text" autocomplete="off" :readonly="!canWrite" :disabled="busy" :aria-describedby="id('rem-hint')" @input="clear('reminderMonths')" />
            <p :id="id('rem-hint')" class="edit-hint">{{ t('certificates.field.reminderHint') }}</p>
            <p v-if="problemText('reminderMonths')" class="iso-field-error">{{ problemText('reminderMonths') }}</p>
          </div>
          <div class="edit-field iso-edit-wide">
            <PeoplePicker
              v-model="form.responsible"
              :people="people"
              :input-id="id('people')"
              :label="t('certificates.field.responsible')"
              :error="problemText('responsible')"
              :disabled="busy || !canWrite"
            />
          </div>
        </div>
        <p v-if="current?.lastReminder" class="muted">{{ t('certificates.lastReminder') }}: {{ formatLocal(current.lastReminder.slice(0, 16), locale) }}</p>
        <p v-if="current?.reminderError" class="alert alert-error">{{ t('certificates.reminderError', { error: current.reminderError }) }}</p>
      </section>

      <section class="iso-edit-section" :aria-labelledby="id('s4')">
        <h3 :id="id('s4')">{{ t('certificates.section.file') }}</h3>
        <p v-if="isNew" class="muted">{{ t('certificates.file.afterSave') }}</p>
        <template v-else>
          <p v-if="!current?.file" class="muted">{{ t('certificates.file.none') }}</p>
          <div class="edit-actions cert-file">
            <button v-if="current?.file" type="button" class="edit-btn" :disabled="busy" @click="openFile">{{ t('certificates.file.open') }} ({{ current.file.name }})</button>
            <template v-if="canWrite">
              <input ref="fileInput" type="file" accept="application/pdf,.pdf" class="visually-hidden" tabindex="-1" @change="onFile" />
              <button type="button" class="edit-btn" :disabled="busy" @click="fileInput?.click()">{{ current?.file ? t('certificates.file.replace') : t('certificates.file.upload') }}</button>
              <button v-if="current?.file" type="button" class="edit-btn is-danger" :disabled="busy" @click="removeFile">{{ t('certificates.file.remove') }}</button>
            </template>
          </div>
        </template>
      </section>

      <section class="iso-edit-section" :aria-labelledby="id('s5')">
        <h3 :id="id('s5')">{{ t('certificates.section.note') }}</h3>
        <div class="edit-field">
          <label :for="id('note')" class="visually-hidden">{{ t('certificates.field.note') }}</label>
          <textarea :id="id('note')" v-model="form.note" maxlength="20000" :readonly="!canWrite" :disabled="busy" />
        </div>
        <p v-if="createdText" class="muted">{{ createdText }}</p>
      </section>

      <p v-if="info" class="muted iso-edit-info" role="status">{{ info }}</p>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>

    <template #footer>
      <button v-if="canDelete" type="button" class="edit-btn is-danger edit-left" :disabled="busy" @click="deleting = true; deleteError = null">{{ t('certificates.delete') }}</button>
      <button type="button" class="edit-btn" :class="{ 'edit-left': !canDelete }" :disabled="busy" @click="requestClose">{{ dirty ? t('edit.cancel') : t('edit.close') }}</button>
      <button v-if="canWrite" type="submit" class="btn btn-primary edit-submit" :form="id('form')" :disabled="busy || (!dirty && !isNew)">
        {{ busy ? t('edit.saving') : t('edit.save') }}
      </button>
    </template>
  </EditDialog>

  <ConfirmDialog
    v-if="deleting && current"
    :title="t('certificates.delete')"
    :message="t('certificates.deleteQuestion', { name: current.name })"
    :confirm-label="t('certificates.delete')"
    :busy="busy"
    :error="deleteError"
    danger
    @confirm="confirmDelete"
    @cancel="deleting = false"
  />
  <ConfirmDialog
    v-if="leaveAsk"
    :title="t('registerEdit.leave.title')"
    :message="t('registerEdit.leave.message')"
    :confirm-label="t('registerEdit.leave.confirm')"
    danger
    @confirm="leaveAsk?.(true)"
    @cancel="leaveAsk?.(false)"
  />
</template>

<style scoped>
.cert-form textarea {
  min-height: 5rem;
}

.cert-file {
  justify-content: flex-start;
  flex-wrap: wrap;
}
</style>
