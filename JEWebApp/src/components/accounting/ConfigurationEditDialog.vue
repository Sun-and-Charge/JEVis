<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CONTRACT_DATE_FIELDS,
  CONTRACT_TEXT_FIELDS,
  CONTRACT_TEXT_MAX,
  carrySelections,
  checkConfiguration,
  cloneConfigurationForm,
  configurationChanges,
  configurationForm,
  conflictModelOf,
  editModelOf,
  hasConfigurationChanges,
  mergeConfigurationForm,
  unassignedKeys,
  warningsOfModel,
  type ConfigurationForm,
  type ContractField,
} from '../../accounting/configurationEdit'
import { NAME_MAX, isDay, type Problem } from '../../accounting/contractEdit'
import { contractWhere } from '../../accounting/contracts'
import {
  CONTRACT_TYPES,
  createConfiguration,
  deleteConfiguration,
  loadConfigurationEdit,
  loadTemplateInputs,
  loadTemplates,
  updateConfiguration,
  type ConfigurationEdit,
  type ConfigurationInput,
  type ContractCreateTarget,
  type TemplateOption,
  type TemplateRef,
} from '../../api/contracts'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLeaveGuard } from '../../edit/useLeaveGuard'
import { serverMessages } from '../../iso/edit'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import ConfigurationInputSelect from './ConfigurationInputSelect.vue'
import '../../styles/isoEdit.css'
import '../../styles/registersEdit.css'

/*
 * "Neu" / "Bearbeiten" of an accounting configuration (E6.3), as the Control Center's "Configuration" tab of the
 * Accounting Plugin: a side panel (full-width sheet on phones) with the contract fields, the calculation template and
 * one searchable select per input selector. Changing the template reloads the selectors (after a question) and keeps
 * the selections whose key exists. After saving the panel stays open: the server's warnings are shown and the cost
 * calculation of the configuration can be opened. 409: "Neu laden" / "Weiter bearbeiten" as E6.2.
 */

const props = defineProps<{
  /** the configuration to edit; null: create a new one in one of `targets` */
  configurationId: number | null
  /** directories for a new configuration */
  targets?: ContractCreateTarget[]
  /** name as listed (title while loading) */
  listedName?: string
  canWrite?: boolean
  canDelete?: boolean
}>()

const emit = defineEmits<{
  close: []
  /** saved or created: the page reloads the list; the panel stays open */
  saved: [model: ConfigurationEdit, name: string, created: boolean]
  /** the configuration changed elsewhere or vanished: reload the list */
  changed: []
  deleted: [name: string]
}>()

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `cfg-dlg-${Math.random().toString(36).slice(2, 9)}`
const id = (suffix: string) => `${uid}-${suffix}`

/* ---------- state ---------- */
const currentId = ref<number | null>(props.configurationId)
const creating = computed(() => currentId.value == null)
/** created in this panel: the creator may go on editing until the list tells the rights */
const createdHere = ref(false)
const writable = computed(() => creating.value || props.canWrite === true || (createdHere.value && props.canWrite !== false))
const deletable = computed(() => !creating.value && props.canDelete === true)

const loadState = ref<'loading' | 'ready' | 'error'>(props.configurationId == null ? 'ready' : 'loading')
const loadError = ref<string | null>(null)
const model = shallowRef<ConfigurationEdit | null>(null)
const title = ref(props.listedName ?? '')

const templates = shallowRef<TemplateOption[]>([])
const templatesState = ref<'loading' | 'ready' | 'error'>('loading')
const inputs = shallowRef<ConfigurationInput[]>([])
const inputsState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
/** the template of the model (also when it is not readable) */
const modelTemplate = computed<TemplateRef | null>(() => model.value?.template ?? null)

const initial = ref<ConfigurationForm>(configurationForm(null))
const form = ref<ConfigurationForm>(cloneConfigurationForm(initial.value))
const targets = computed(() => props.targets ?? [])
const parent = ref<number | ''>(targets.value.length === 1 ? targets.value[0].id : '')

const problems = ref<Record<string, Problem>>({})
const error = ref<string | null>(null)
const messages = ref<string[]>([])
const warnings = ref<string[]>([])
const info = ref<string | null>(null)
const conflict = shallowRef<ConfigurationEdit | null>(null)
const savedOnce = ref(false)

const changes = computed(() => configurationChanges(initial.value, form.value))
const dirty = computed(() => writable.value && loadState.value === 'ready' && hasConfigurationChanges(changes.value))
const { leaveAsk, confirmDrop } = useLeaveGuard(dirty)

async function requestClose() {
  if (busy.value) return
  if (await confirmDrop()) emit('close')
}

/* ---------- loading ---------- */
function apply(m: ConfigurationEdit) {
  model.value = m
  initial.value = configurationForm(m)
  form.value = cloneConfigurationForm(initial.value)
  inputs.value = m.inputs ?? []
  inputsState.value = m.template ? 'ready' : 'idle'
  title.value = m.name
}

async function loadModel() {
  if (currentId.value == null) return
  loadState.value = 'loading'
  loadError.value = null
  try {
    const m = editModelOf(await loadConfigurationEdit(auth.http, currentId.value, locale.value), currentId.value)
    if (!m) throw new Error('no model')
    apply(m)
    loadState.value = 'ready'
  } catch (e) {
    loadError.value = writeErrorKind(e) === 'notFound' ? writeErrorText(e, t) : t('configEdit.loadError')
    loadState.value = 'error'
    if (writeErrorKind(e) === 'notFound') emit('changed')
  }
}

async function loadTemplateList() {
  templatesState.value = 'loading'
  try {
    templates.value = (await loadTemplates(auth.http, locale.value)).filter((x) => x && typeof x.id === 'number')
    templatesState.value = 'ready'
  } catch {
    templatesState.value = 'error'
  }
}

onMounted(() => {
  void loadTemplateList()
  void loadModel()
})

/* ---------- template ---------- */
const templateLabel = (x: TemplateOption) => {
  const w = contractWhere(x, locale.value)
  return w ? `${x.name} – ${w}` : x.name
}
/** the chosen template is not among the readable ones */
const hiddenTemplate = computed(() => form.value.templateId != null && templatesState.value === 'ready' && !templates.value.some((x) => x.id === form.value.templateId))
const hiddenTemplateLabel = computed(() => {
  const m = modelTemplate.value
  const name = m && m.id === form.value.templateId && m.readable !== false ? m.name : null
  return name || t('configEdit.hidden')
})
const templateReadable = computed(() => {
  const chosen = form.value.templateId
  if (chosen == null) return false
  if (templates.value.some((x) => x.id === chosen)) return true
  const m = modelTemplate.value
  /* list not loaded: trust the model */
  return templatesState.value !== 'ready' && !!m && m.id === chosen && m.readable !== false
})
const templateUnreadable = computed(() => form.value.templateId != null && modelTemplate.value?.id === form.value.templateId && modelTemplate.value.readable === false)

const templateAsk = shallowRef<{ name: string; resolve: (ok: boolean) => void } | null>(null)
const templateError = ref<string | null>(null)

function askTemplate(name: string): Promise<boolean> {
  return new Promise((resolve) => {
    templateAsk.value = {
      name,
      resolve: (ok) => {
        templateAsk.value = null
        resolve(ok)
      },
    }
  })
}

async function onTemplateChange(e: Event) {
  const el = e.target as HTMLSelectElement
  const next = el.value === '' ? null : Number(el.value)
  /* the select shows the applied template until the new one is loaded */
  el.value = form.value.templateId == null ? '' : String(form.value.templateId)
  if (next == null || next === form.value.templateId) return
  const name = templates.value.find((x) => x.id === next)?.name ?? ''
  const hasChoice = Object.values(form.value.selections).some((v) => v != null)
  if (form.value.templateId != null && hasChoice && !(await askTemplate(name))) return
  await switchTemplate(next)
}

async function switchTemplate(templateId: number) {
  templateError.value = null
  inputsState.value = 'loading'
  try {
    const next = (await loadTemplateInputs(auth.http, templateId, currentId.value, locale.value)).filter((i) => i && typeof i.key === 'string')
    form.value = { ...form.value, templateId, selections: carrySelections(inputs.value, form.value.selections, next) }
    inputs.value = next
    inputsState.value = 'ready'
    info.value = null
    if (problems.value.template) {
      const p = { ...problems.value }
      delete p.template
      problems.value = p
    }
  } catch {
    inputsState.value = form.value.templateId == null ? 'idle' : 'ready'
    templateError.value = t('configEdit.inputsError')
  }
}

/* ---------- fields ---------- */
const textFields = CONTRACT_TEXT_FIELDS
const dateFields = CONTRACT_DATE_FIELDS
const energyName = (code: string) => (te('contracts.energy.' + code) ? t('contracts.energy.' + code) : code)
/** a stored energy type the web does not offer stays visible (and is flagged on save) */
const extraType = computed(() => {
  const v = form.value.contract.contractType
  return v && !(CONTRACT_TYPES as readonly string[]).includes(v) ? v : null
})

function setField(f: ContractField, e: Event) {
  form.value.contract = { ...form.value.contract, [f]: (e.target as HTMLInputElement | HTMLSelectElement).value }
  clearProblem(f)
  if (f === 'start' && problems.value.end?.key === 'configEdit.problem.endBeforeStart') clearProblem('end')
}

function clearProblem(key: string) {
  info.value = null
  if (!problems.value[key]) return
  const p = { ...problems.value }
  delete p[key]
  problems.value = p
}

function setSelection(key: string, value: number | null) {
  form.value.selections = { ...form.value.selections, [key]: value }
  info.value = null
}

/** a stored date that is no yyyy-MM-dd (old text); the date input cannot show it */
const notADate = (f: ContractField) => {
  const v = initial.value.contract[f]
  return v && v === form.value.contract[f] && !isDay(v) ? v : null
}

const unassigned = computed(() => unassignedKeys(inputs.value, form.value.selections))
const assignedText = computed(() => t('configEdit.assigned', { n: inputs.value.length - unassigned.value.length, total: inputs.value.length }))
const targetLabel = (d: ContractCreateTarget) => {
  const w = contractWhere(d, locale.value)
  return w ? `${d.name} – ${w}` : d.name
}
const fieldError = (key: string) => problems.value[key]
const describedBy = (key: string) => (problems.value[key] ? id(key + '-err') : undefined)

/* ---------- save ---------- */
async function focusFirstProblem() {
  await nextTick()
  document.querySelector<HTMLElement>(`#${uid}-form [aria-invalid="true"]`)?.focus()
}

async function save() {
  error.value = null
  messages.value = []
  info.value = null
  const check = checkConfiguration(form.value, inputs.value, { nameChanged: creating.value || changes.value.name, templateReadable: templateReadable.value })
  const found = { ...check.problems }
  if (creating.value && parent.value === '') found.parent = { key: 'contractEdit.problem.directory' }
  problems.value = found
  if (Object.keys(found).length || !check.body) {
    await focusFirstProblem()
    return
  }
  if (!creating.value && !hasConfigurationChanges(changes.value)) {
    info.value = t('registerEdit.nothingChanged')
    return
  }
  const body = check.body
  await run(async () => {
    try {
      const wasNew = creating.value
      const answer = wasNew
        ? await createConfiguration(auth.http, { ...body, parentId: parent.value as number, name: form.value.name.trim() }, locale.value)
        : await updateConfiguration(auth.http, currentId.value!, { ...body, version: model.value?.version ?? 'none' }, locale.value)
      const m = editModelOf(answer, wasNew ? null : currentId.value)
      const name = m?.name || form.value.name.trim()
      if (m) {
        if (wasNew) createdHere.value = true
        currentId.value = m.id
        apply(m)
        warnings.value = warningsOfModel(m)
      } else {
        warnings.value = []
        if (!wasNew) await loadModel()
      }
      savedOnce.value = true
      info.value = t(wasNew ? 'registerEdit.created' : 'registerEdit.saved', { name })
      if (m) emit('saved', m, name, wasNew)
      else emit('changed')
      if (wasNew && !m) emit('close')
    } catch (e) {
      handle(e)
    }
  })
}

function handle(e: unknown) {
  const kind = writeErrorKind(e)
  if (kind === 'conflict' && currentId.value != null) {
    emit('changed')
    const m = conflictModelOf(e, currentId.value)
    if (m) {
      conflict.value = m
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

function reloadFromConflict() {
  const m = conflict.value
  conflict.value = null
  problems.value = {}
  warnings.value = []
  if (m) apply(m)
  info.value = t('registerEdit.reloaded')
}

function keepEditing() {
  const m = conflict.value
  conflict.value = null
  if (!m) return
  const server = configurationForm(m)
  const ownTemplate = changes.value.template
  const merged = mergeConfigurationForm(initial.value, form.value, server)
  const ownInputs = inputs.value
  model.value = m
  initial.value = server
  form.value = merged
  if (!ownTemplate) inputs.value = m.inputs ?? []
  else inputs.value = ownInputs
  title.value = m.name
}

/* ---------- delete ---------- */
const deleting = ref(false)
const deleteError = ref<string | null>(null)

function askDelete() {
  if (busy.value) return
  deleteError.value = null
  deleting.value = true
}

async function confirmDelete() {
  if (currentId.value == null) return
  const name = model.value?.name ?? title.value
  await run(async () => {
    try {
      await deleteConfiguration(auth.http, currentId.value!, model.value?.version ?? 'none')
      deleting.value = false
      emit('deleted', name)
    } catch (e) {
      const kind = writeErrorKind(e)
      if (kind === 'conflict' || kind === 'notFound') {
        deleting.value = false
        error.value = kind === 'conflict' ? t('registerEdit.conflictDelete', { name }) : writeErrorText(e, t)
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

const formDirty = computed(() => hasConfigurationChanges(changes.value))
const stateText = computed(() => (formDirty.value ? t('registerEdit.unsaved') : savedOnce.value ? t('configEdit.savedState') : t('registerEdit.noChanges')))
const dialogTitle = computed(() => (creating.value ? t('configEdit.newTitle') : t('registerEdit.editTitle', { name: title.value })))
const costsLink = computed(() => (currentId.value == null ? null : { path: '/costs', query: { id: String(currentId.value) } }))
const fieldLabel = (f: ContractField) => t('configEdit.field.' + f)
</script>

<template>
  <EditDialog :title="dialogTitle" :busy="busy" wide class="ctr-edit-backdrop cfg-edit-backdrop" @close="requestClose">
    <p v-if="loadState === 'loading'" class="muted" role="status">{{ t('configEdit.loading') }}</p>
    <div v-else-if="loadState === 'error'" class="alert alert-error" role="alert">
      {{ loadError }}
      <button type="button" class="btn-link retry" @click="loadModel">{{ t('modules.retry') }}</button>
    </div>

    <template v-else>
      <p v-if="costsLink" class="cfg-costs">
        <RouterLink :to="costsLink" class="btn-link cfg-costs-link">{{ t('configEdit.openCosts') }}</RouterLink>
        <span v-if="formDirty" class="edit-hint">{{ t('configEdit.openCostsHint') }}</span>
      </p>

      <form :id="uid + '-form'" class="ctr-edit-form cfg-edit-form" novalidate :aria-describedby="messages.length ? id('messages') : undefined" @submit.prevent="save">
        <div v-if="creating && targets.length > 1" class="edit-field">
          <label :for="id('parent')">{{ t('contractEdit.directory') }}<span class="iso-req" aria-hidden="true"> *</span></label>
          <select
            :id="id('parent')"
            v-model="parent"
            :disabled="busy"
            required
            aria-required="true"
            :aria-invalid="fieldError('parent') ? 'true' : undefined"
            :aria-describedby="describedBy('parent')"
            @change="clearProblem('parent')"
          >
            <option value="" disabled>{{ t('registerEdit.choose') }}</option>
            <option v-for="d in targets" :key="d.id" :value="d.id">{{ targetLabel(d) }}</option>
          </select>
          <p v-if="fieldError('parent')" :id="id('parent-err')" class="iso-field-error">{{ t(fieldError('parent')!.key) }}</p>
        </div>
        <dl v-else-if="creating && targets.length === 1" class="edit-facts">
          <dt>{{ t('contractEdit.directory') }}</dt>
          <dd>{{ targetLabel(targets[0]) }}</dd>
        </dl>

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
            :readonly="!writable"
            :disabled="busy"
            :aria-invalid="fieldError('name') ? 'true' : undefined"
            :aria-describedby="describedBy('name')"
            @input="clearProblem('name')"
          />
          <p v-if="fieldError('name')" :id="id('name-err')" class="iso-field-error">{{ t(fieldError('name')!.key, fieldError('name')!.params ?? {}) }}</p>
        </div>

        <section class="iso-edit-section" :aria-labelledby="id('contract')">
          <h3 :id="id('contract')">{{ t('configEdit.contract') }}</h3>
          <div class="iso-edit-grid">
            <div v-for="f in textFields" :key="f" class="edit-field">
              <label :for="id(f)">{{ fieldLabel(f) }}</label>
              <input
                :id="id(f)"
                :value="form.contract[f]"
                type="text"
                :maxlength="CONTRACT_TEXT_MAX + 20"
                autocomplete="off"
                :readonly="!writable"
                :disabled="busy"
                :aria-invalid="fieldError(f) ? 'true' : undefined"
                :aria-describedby="describedBy(f)"
                @input="setField(f, $event)"
              />
              <p v-if="fieldError(f)" :id="id(f + '-err')" class="iso-field-error">{{ t(fieldError(f)!.key, fieldError(f)!.params ?? {}) }}</p>
            </div>
            <div class="edit-field">
              <label :for="id('contractType')">{{ fieldLabel('contractType') }}</label>
              <select
                :id="id('contractType')"
                :value="form.contract.contractType"
                :disabled="busy || !writable"
                :aria-invalid="fieldError('contractType') ? 'true' : undefined"
                :aria-describedby="describedBy('contractType')"
                @change="setField('contractType', $event)"
              >
                <option value="">{{ t('configEdit.noEnergy') }}</option>
                <option v-for="c in CONTRACT_TYPES" :key="c" :value="c">{{ energyName(c) }}</option>
                <option v-if="extraType" :value="extraType">{{ energyName(extraType) }}</option>
              </select>
              <p v-if="fieldError('contractType')" :id="id('contractType-err')" class="iso-field-error">{{ t(fieldError('contractType')!.key) }}</p>
            </div>
            <div v-for="f in dateFields" :key="f" class="edit-field">
              <label :for="id(f)">{{ fieldLabel(f) }}</label>
              <input
                :id="id(f)"
                :value="form.contract[f]"
                type="date"
                :readonly="!writable"
                :disabled="busy"
                :aria-invalid="fieldError(f) ? 'true' : undefined"
                :aria-describedby="fieldError(f) ? id(f + '-err') : notADate(f) ? id(f + '-old') : undefined"
                @input="setField(f, $event)"
                @change="setField(f, $event)"
              />
              <p v-if="notADate(f)" :id="id(f + '-old')" class="edit-hint">{{ t('configEdit.notADate', { text: notADate(f) }) }}</p>
              <p v-if="fieldError(f)" :id="id(f + '-err')" class="iso-field-error">{{ t(fieldError(f)!.key) }}</p>
            </div>
          </div>
        </section>

        <section class="iso-edit-section" :aria-labelledby="id('template-h')">
          <h3 :id="id('template-h')">{{ t('configEdit.template') }}</h3>
          <div class="edit-field">
            <label :for="id('template')">{{ t('configEdit.template') }}<span class="iso-req" aria-hidden="true"> *</span></label>
            <select
              :id="id('template')"
              :value="form.templateId == null ? '' : String(form.templateId)"
              :disabled="busy || !writable || templatesState === 'loading' || inputsState === 'loading'"
              aria-required="true"
              :aria-invalid="fieldError('template') ? 'true' : undefined"
              :aria-describedby="describedBy('template')"
              @change="onTemplateChange"
            >
              <option value="" disabled>{{ t('configEdit.templateChoose') }}</option>
              <option v-if="hiddenTemplate" :value="String(form.templateId)">{{ hiddenTemplateLabel }}</option>
              <option v-for="x in templates" :key="x.id" :value="String(x.id)">{{ templateLabel(x) }}</option>
            </select>
            <p v-if="fieldError('template')" :id="id('template-err')" class="iso-field-error">{{ t(fieldError('template')!.key) }}</p>
            <p v-if="templatesState === 'loading'" class="edit-hint" role="status">{{ t('configEdit.templatesLoading') }}</p>
            <div v-else-if="templatesState === 'error'" class="alert alert-error" role="alert">
              {{ t('configEdit.templatesError') }}
              <button type="button" class="btn-link retry" @click="loadTemplateList">{{ t('modules.retry') }}</button>
            </div>
            <p v-if="templateUnreadable" class="alert ctr-hint cfg-template-hidden" role="note">{{ t('configEdit.templateUnreadable') }}</p>
            <p v-if="templateError" class="alert alert-error" role="alert">{{ templateError }}</p>
          </div>
        </section>

        <section class="iso-edit-section cfg-inputs" :aria-labelledby="id('inputs')">
          <h3 :id="id('inputs')">{{ t('configEdit.inputs') }}</h3>
          <p v-if="inputsState === 'loading'" class="muted" role="status">{{ t('configEdit.inputsLoading') }}</p>
          <p v-else-if="form.templateId == null" class="muted">{{ t('configEdit.noTemplate') }}</p>
          <p v-else-if="!inputs.length" class="muted">{{ t('configEdit.noInputs') }}</p>
          <template v-else>
            <p class="edit-hint cfg-assigned" role="status">
              {{ assignedText }}
              <span v-if="writable"> – {{ t('configEdit.inputsHint') }}</span>
            </p>
            <ConfigurationInputSelect
              v-for="(input, i) in inputs"
              :key="input.key"
              :input="input"
              :model-value="form.selections[input.key] ?? null"
              :id-base="id('in' + i)"
              :disabled="busy || !writable"
              @update:model-value="setSelection(input.key, $event)"
            />
          </template>
          <p v-if="model?.unknownKeys?.length" class="edit-hint cfg-unknown">{{ t('configEdit.unknownKeys') }}</p>
        </section>

        <div v-if="warnings.length" class="alert ctr-hint cfg-warnings" role="status">
          <strong>{{ t('configEdit.warningsTitle') }}</strong>
          <ul class="edit-warnings">
            <li v-for="(w, i) in warnings" :key="i">{{ w }}</li>
          </ul>
        </div>
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
    </template>

    <template #footer>
      <button v-if="deletable && loadState === 'ready'" type="button" class="edit-btn is-danger edit-left cfg-delete" :disabled="busy" @click="askDelete">
        {{ t('contractEdit.delete') }}
      </button>
      <span v-if="writable && loadState === 'ready'" class="reg-edit-state" :class="{ 'edit-left': !deletable, 'is-dirty': formDirty }" role="status">{{ stateText }}</span>
      <button type="button" class="edit-btn cfg-close" :disabled="busy" @click="requestClose">{{ formDirty ? t('edit.cancel') : t('edit.close') }}</button>
      <button
        v-if="writable"
        type="submit"
        class="btn btn-primary edit-submit"
        :form="uid + '-form'"
        :disabled="busy || !!conflict || loadState !== 'ready' || inputsState === 'loading'"
      >
        {{ busy ? t('edit.saving') : creating ? t('registerEdit.create') : t('edit.save') }}
      </button>
    </template>
  </EditDialog>

  <ConfirmDialog
    v-if="templateAsk"
    :title="t('configEdit.switchTitle')"
    :message="t('configEdit.switchMessage', { name: templateAsk.name })"
    :confirm-label="t('configEdit.switchConfirm')"
    @confirm="templateAsk?.resolve(true)"
    @cancel="templateAsk?.resolve(false)"
  />
  <ConfirmDialog
    v-if="deleting"
    :title="t('configEdit.deleteTitle')"
    :message="t('registerEdit.deleteQuestion', { name: model?.name ?? title })"
    :confirm-label="t('contractEdit.delete')"
    :busy="busy"
    :error="deleteError"
    danger
    @confirm="confirmDelete"
    @cancel="cancelDelete"
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
