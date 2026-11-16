<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  checkForm,
  cloneForm,
  contractForm,
  formChanges,
  hasChanges,
  isFormAttribute,
  isPriceAttribute,
  mergeForm,
  NAME_MAX,
  objectOf,
  usedByOf,
  type ContractForm,
  type Problem,
  type RangingFormRow,
  type RangingProblem,
} from '../../accounting/contractEdit'
import { attributeText, contractWhere } from '../../accounting/contracts'
import { deleteContractObject, updateContractObject, type ContractAttribute, type ContractObject, type ContractUsage } from '../../api/contracts'
import { HttpError } from '../../api/http'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLeaveGuard } from '../../edit/useLeaveGuard'
import { serverMessages } from '../../iso/edit'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import ContractorSelect from './ContractorSelect.vue'
import ContractPriceField from './ContractPriceField.vue'
import ContractRangingEditor from './ContractRangingEditor.vue'
import '../../styles/isoEdit.css'
import '../../styles/registersEdit.css'

/*
 * "Bearbeiten" of a contract partner, contractor or governmental due (E6.2), as the Control Center's "Enter data"
 * tab: a side panel (full-width sheet on phones) with
 * - the form: name, contractor (searchable select), text attributes and ranging tables, saved with "Speichern"
 *   (only the changed parts with the version; 409: "Neu laden" / "Weiter bearbeiten" as E5);
 * - the prices / dues: per number attribute the current value, the history and "Neuer Wert" (written right away);
 * - "Löschen" with a question; when other contracts still use the object, their list and "Trotzdem löschen".
 * The page reloads after every write and passes the new object in; own unsaved changes are kept.
 */

const props = defineProps<{
  object: ContractObject
  /** label of the group (tab) */
  groupLabel: string
  /** the contractor with this id is in the contractor tab (link offered) */
  canShowContractor?: (id: number) => boolean
}>()

const emit = defineEmits<{
  close: []
  /** the form was saved */
  saved: [object: ContractObject | null, name: string]
  /** something was written (a value) or the object changed / vanished: reload the list */
  changed: []
  deleted: [name: string]
  openContractor: [id: number]
}>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `ctr-dlg-${Math.random().toString(36).slice(2, 9)}`
const id = (suffix: string) => `${uid}-${suffix}`

/* ---------- the object as loaded and the form ---------- */
const current = shallowRef<ContractObject>(props.object)
const initial = ref<ContractForm>(contractForm(current.value, locale.value))
const form = ref<ContractForm>(cloneForm(initial.value))
const title = ref(props.object.name)

const canWrite = computed(() => current.value.canWrite === true)
const canDelete = computed(() => current.value.canDelete === true)
const where = computed(() => contractWhere(current.value, locale.value))
const formAttributes = computed(() => (current.value.attributes ?? []).filter(isFormAttribute))
const priceAttributes = computed(() => (current.value.attributes ?? []).filter(isPriceAttribute))
const readOnlyAttributes = computed(() => (current.value.attributes ?? []).filter((a) => !isFormAttribute(a) && !isPriceAttribute(a)))
const hasContractor = computed(() => !!current.value.contractorClass)

const nameError = ref<Problem | null>(null)
const rangingProblems = ref<Record<string, RangingProblem[]>>({})
const error = ref<string | null>(null)
const messages = ref<string[]>([])
const info = ref<string | null>(null)
const conflict = shallowRef<ContractObject | null>(null)
const pendingPrices = reactive<Record<string, boolean>>({})
const written = ref(false)

const changes = computed(() => formChanges(initial.value, form.value))
const formDirty = computed(() => canWrite.value && hasChanges(changes.value))
const dirty = computed(() => formDirty.value || Object.values(pendingPrices).some(Boolean))

const { leaveAsk, confirmDrop } = useLeaveGuard(dirty)

async function requestClose() {
  if (busy.value) return
  if (await confirmDrop()) emit('close')
}

/** a newer object from the page (after a reload): taken over, own unsaved changes kept */
function rebase(o: ContractObject) {
  const server = contractForm(o, locale.value)
  form.value = mergeForm(initial.value, form.value, server)
  initial.value = server
  current.value = o
  title.value = o.name
}

watch(
  () => props.object,
  (o) => {
    if (o && o !== current.value && !conflict.value) rebase(o)
  },
)

/* ---------- field helpers ---------- */
function setText(a: ContractAttribute, e: Event) {
  form.value.texts = { ...form.value.texts, [a.name]: (e.target as HTMLInputElement | HTMLTextAreaElement).value }
  info.value = null
}

function setRanging(a: ContractAttribute, rows: RangingFormRow[]) {
  form.value.ranging = { ...form.value.ranging, [a.name]: rows }
  info.value = null
  if (rangingProblems.value[a.name]) {
    const next = { ...rangingProblems.value }
    delete next[a.name]
    rangingProblems.value = next
  }
}

const isTextArea = (a: ContractAttribute) => /TEXT_?AREA/i.test(a.guiType ?? '')
const fid = (a: ContractAttribute) => id('a' + (current.value.attributes ?? []).indexOf(a))
const readOnlyText = (a: ContractAttribute) => attributeText(a, locale.value, (from) => t('contracts.openEnd', { from })) || t('modules.none')

async function showContractor(contractorId: number) {
  if (await confirmDrop()) emit('openContractor', contractorId)
}

/* ---------- save ---------- */
function reloadFromConflict() {
  const o = conflict.value
  if (o) {
    initial.value = contractForm(o, locale.value)
    form.value = cloneForm(initial.value)
    current.value = o
    title.value = o.name
  }
  conflict.value = null
  rangingProblems.value = {}
  nameError.value = null
  info.value = t('registerEdit.reloaded')
}

function keepEditing() {
  const o = conflict.value
  conflict.value = null
  if (o) rebase(o)
}

async function focusFirstProblem() {
  await nextTick()
  if (nameError.value) {
    document.getElementById(id('name'))?.focus()
    return
  }
  const first = document.querySelector<HTMLElement>(`#${uid}-form [aria-invalid="true"]`)
  first?.focus()
}

async function save() {
  error.value = null
  messages.value = []
  info.value = null
  const check = checkForm(initial.value, form.value)
  nameError.value = check.nameProblem
  rangingProblems.value = check.ranging
  if (check.nameProblem || Object.keys(check.ranging).length) {
    await focusFirstProblem()
    return
  }
  if (!check.body) {
    info.value = t('registerEdit.nothingChanged')
    return
  }
  const body = { version: current.value.version ?? '', ...check.body }
  await run(async () => {
    try {
      const answer = await updateContractObject(auth.http, current.value.id, body, locale.value)
      const o = objectOf(answer, current.value.id)
      emit('saved', o, o?.name || form.value.name.trim())
    } catch (e) {
      handle(e)
    }
  })
}

function handle(e: unknown) {
  const kind = writeErrorKind(e)
  if (kind === 'conflict') {
    const o = objectOf(e instanceof HttpError ? e.json() : null, current.value.id)
    emit('changed')
    if (o) {
      conflict.value = o
      return
    }
    error.value = t('registerEdit.conflict.noRow')
    return
  }
  if (kind === 'notFound') {
    error.value = writeErrorText(e, t)
    emit('changed')
    return
  }
  if (kind === 'invalid') {
    const list = serverMessages(e)
    if (list.length) {
      messages.value = list
      return
    }
  }
  error.value = writeErrorText(e, t)
}

function onWritten() {
  written.value = true
  emit('changed')
}

/* ---------- delete ---------- */
const deleting = ref(false)
const usedBy = shallowRef<ContractUsage[] | null>(null)
const deleteError = ref<string | null>(null)

function askDelete() {
  if (busy.value) return
  usedBy.value = null
  deleteError.value = null
  deleting.value = true
}

async function confirmDelete() {
  const force = usedBy.value != null
  await run(async () => {
    try {
      await deleteContractObject(auth.http, current.value.id, current.value.version ?? '', force)
      deleting.value = false
      emit('deleted', current.value.name)
    } catch (e) {
      const list = usedByOf(e)
      if (list && !force) {
        usedBy.value = list
        return
      }
      const kind = writeErrorKind(e)
      if (kind === 'conflict' || kind === 'notFound') {
        deleting.value = false
        error.value = kind === 'conflict' ? t('registerEdit.conflictDelete', { name: current.value.name }) : writeErrorText(e, t)
        emit('changed')
        return
      }
      deleteError.value = writeErrorText(e, t)
    }
  })
}

function cancelDelete() {
  if (!busy.value) deleting.value = false
}

const usageKind = (u: ContractUsage) => t(u.kind === 'configuration' ? 'contractEdit.usedBy.configuration' : 'contractEdit.usedBy.object')
const stateText = computed(() =>
  formDirty.value ? t('registerEdit.unsaved') : written.value ? t('contractEdit.valuesSaved') : t('registerEdit.noChanges'),
)
</script>

<template>
  <EditDialog :title="t('registerEdit.editTitle', { name: title })" :busy="busy" wide class="ctr-edit-backdrop" @close="requestClose">
    <dl class="edit-facts">
      <dt>{{ t('registerEdit.group') }}</dt>
      <dd>{{ groupLabel }}</dd>
      <dt>{{ t('registers.detail.class') }}</dt>
      <dd>{{ current.classLabel || current.className }}</dd>
      <template v-if="where">
        <dt>{{ t('contractEdit.where') }}</dt>
        <dd>{{ where }}</dd>
      </template>
    </dl>

    <form :id="uid + '-form'" class="ctr-edit-form" novalidate :aria-describedby="messages.length ? id('messages') : undefined" @submit.prevent="save">
      <div class="edit-field">
        <label :for="id('name')">{{ t('registers.col.name') }}<span class="iso-req" aria-hidden="true"> *</span></label>
        <input
          :id="id('name')"
          v-model="form.name"
          type="text"
          :maxlength="NAME_MAX + 20"
          autocomplete="off"
          required
          aria-required="true"
          autofocus
          :readonly="!canWrite"
          :disabled="busy"
          :aria-invalid="nameError ? 'true' : undefined"
          :aria-describedby="nameError ? id('name-err') : undefined"
          @input="nameError = null"
        />
        <p v-if="nameError" :id="id('name-err')" class="iso-field-error">{{ t(nameError.key, nameError.params ?? {}) }}</p>
      </div>

      <ContractorSelect
        v-if="hasContractor && canWrite"
        v-model="form.contractor"
        :contractor-class="current.contractorClass!"
        :loaded="current.contractor"
        :id-base="id('contractor')"
        :disabled="busy"
        :can-show="canShowContractor"
        @show="showContractor"
      />

      <section v-if="formAttributes.length || readOnlyAttributes.length" class="iso-edit-section" :aria-labelledby="id('attrs')">
        <h3 :id="id('attrs')">{{ t('registerEdit.attributes') }}</h3>
        <div class="iso-edit-grid">
          <template v-for="a in formAttributes" :key="a.name">
            <div v-if="a.type === 'ranging'" class="edit-field iso-edit-wide">
              <ContractRangingEditor
                :rows="form.ranging[a.name] ?? []"
                :problems="rangingProblems[a.name] ?? []"
                :label="a.label || a.name"
                :unit="a.inputUnit ?? a.unit"
                :id-base="fid(a)"
                :disabled="busy || !canWrite"
                @update:rows="setRanging(a, $event)"
              />
            </div>
            <div v-else class="edit-field" :class="{ 'iso-edit-wide': isTextArea(a) }">
              <label :for="fid(a)">{{ a.label || a.name }}</label>
              <textarea v-if="isTextArea(a)" :id="fid(a)" :value="form.texts[a.name]" maxlength="20000" :readonly="!canWrite" :disabled="busy" @input="setText(a, $event)" />
              <input
                v-else
                :id="fid(a)"
                :value="form.texts[a.name]"
                type="text"
                maxlength="2000"
                autocomplete="off"
                :readonly="!canWrite"
                :disabled="busy"
                @input="setText(a, $event)"
              />
            </div>
          </template>
          <div v-for="a in readOnlyAttributes" :key="a.name" class="edit-field reg-edit-readonly">
            <label :for="fid(a)">{{ a.label || a.name }}</label>
            <input :id="fid(a)" type="text" :value="readOnlyText(a)" readonly aria-readonly="true" />
          </div>
        </div>
      </section>

      <div v-if="messages.length" :id="id('messages')" class="alert alert-error iso-messages" role="alert">
        <strong>{{ t('registerEdit.invalidTitle') }}</strong>
        <ul class="edit-warnings">
          <li v-for="(m, i) in messages" :key="i">{{ m }}</li>
        </ul>
      </div>
      <div v-if="conflict" class="alert alert-error reg-conflict" role="alert">
        <strong>{{ t('registerEdit.conflict.title') }}</strong>
        <p>{{ t('registerEdit.conflict.message') }}</p>
        <div class="edit-actions">
          <button type="button" class="edit-btn reg-conflict-reload" @click="reloadFromConflict">{{ t('registerEdit.conflict.reload') }}</button>
          <button type="button" class="edit-btn reg-conflict-keep" @click="keepEditing">{{ t('registerEdit.conflict.keep') }}</button>
        </div>
      </div>
      <p v-if="info" class="muted iso-edit-info" role="status">{{ info }}</p>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>

    <section v-if="priceAttributes.length" class="iso-edit-section ctr-prices" :aria-labelledby="id('prices')">
      <h3 :id="id('prices')">{{ t('contractEdit.prices') }}</h3>
      <p v-if="canWrite" class="edit-hint reg-edit-hint">{{ t('contractEdit.pricesHint') }}</p>
      <ContractPriceField
        v-for="a in priceAttributes"
        :key="a.name"
        :object-id="current.id"
        :attribute="a"
        :can-write="canWrite"
        :id-base="fid(a)"
        @written="onWritten"
        @pending="pendingPrices[a.name] = $event"
      />
    </section>

    <template #footer>
      <button v-if="canDelete" type="button" class="edit-btn is-danger edit-left ctr-delete" :disabled="busy" @click="askDelete">
        {{ t('contractEdit.delete') }}
      </button>
      <span v-if="canWrite" class="reg-edit-state" :class="{ 'edit-left': !canDelete, 'is-dirty': formDirty }" role="status">{{ stateText }}</span>
      <button type="button" class="edit-btn ctr-close" :disabled="busy" @click="requestClose">{{ formDirty ? t('edit.cancel') : t('edit.close') }}</button>
      <button v-if="canWrite" type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="busy || !!conflict">
        {{ busy ? t('edit.saving') : t('edit.save') }}
      </button>
    </template>
  </EditDialog>

  <ConfirmDialog
    v-if="deleting"
    :title="t('contractEdit.deleteTitle')"
    :message="usedBy ? t('contractEdit.usedBy.message', { name: current.name }) : t('registerEdit.deleteQuestion', { name: current.name })"
    :confirm-label="usedBy ? t('contractEdit.usedBy.confirm') : t('contractEdit.delete')"
    :busy="busy"
    :error="deleteError"
    danger
    @confirm="confirmDelete"
    @cancel="cancelDelete"
  >
    <ul v-if="usedBy" class="edit-warnings ctr-used-by">
      <li v-for="u in usedBy" :key="u.kind + u.id">
        {{ u.name }} <span class="muted">({{ usageKind(u) }})</span>
      </li>
    </ul>
  </ConfirmDialog>
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
