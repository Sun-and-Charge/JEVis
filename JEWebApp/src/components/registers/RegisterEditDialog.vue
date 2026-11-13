<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { matchedRouteKey, onBeforeRouteLeave } from 'vue-router'
import { HttpError } from '../../api/http'
import {
  createRegisterObject,
  loadEquipmentCreateOptions,
  loadMeterCreateOptions,
  updateRegisterObject,
  type EditableRegisterKind,
  type EquipmentCreateOptions,
  type MeterCreateOptions,
  type RegisterGroup,
  type RegisterRow,
  type UploadedFile,
} from '../../api/registers'
import type { PickedRow } from '../../analysis/editor'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { serverMessages } from '../../iso/edit'
import {
  changedKeys,
  changedRegisterFields,
  cloneFormValues,
  fileOf,
  mergeAfterConflict,
  nameProblem,
  NAME_MAX,
  registerFieldDefs,
  rowFormValues,
  rowOf,
  validateRegisterValues,
  withUploadedFile,
  type RegisterFieldDef,
  type RegisterFormValues,
  type RegisterProblem,
  type TargetInput,
} from '../../registers/edit'
import { formatAttribute, mediumLabel } from '../../registers/format'
import { useAuthStore } from '../../stores/auth'
import RowPickerDialog from '../analysis/edit/RowPickerDialog.vue'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import MediumIcon from './MediumIcon.vue'
import RegisterFileField from './RegisterFileField.vue'
import '../../styles/isoEdit.css'
import '../../styles/registersEdit.css'

/*
 * "Neu" / "Bearbeiten" of a meter or an equipment object (E5), as the Control Center's NewMeterDialog,
 * MeterFormAttributeTab and EquipmentDialog:
 * - new meter: class (with medium), name, optional parent meter (create options of the group);
 * - new equipment: parent (create options of the class), name, then the attribute form;
 * - edit: name and the attribute form generated from the group's columns; only changed attributes are sent with
 *   the version they were loaded with; files are uploaded right away; read-only types are shown greyed.
 * 409: the current row is offered ("Neu laden") or merged ("Weiter bearbeiten": own changes kept). Closing or
 * leaving the page with unsaved changes asks first.
 */

const props = defineProps<{
  kind: EditableRegisterKind
  group: RegisterGroup
  /** the object to change; without it a new one is created in the group */
  row?: RegisterRow | null
}>()

const emit = defineEmits<{
  close: []
  /** saved: the row of the answer (null if none: reload), created or changed */
  saved: [row: RegisterRow | null, created: boolean, name: string]
  /** something was written (upload) or the object changed / vanished (409, 404): reload the list */
  changed: []
}>()

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const tr = (key: string) => t(key)

const editing = computed(() => !!props.row)
const uid = `reg-dlg-${Math.random().toString(36).slice(2, 9)}`

/* ---- the object as loaded (replaced after an upload or a 409) ---- */
const current = shallowRef<RegisterRow | null>(props.row ?? null)

const defs = computed(() => {
  const all = registerFieldDefs(props.group.columns, current.value)
  /* a new meter gets its attributes in the edit form afterwards (as NewMeterDialog); a new object has no files yet */
  if (!editing.value) return props.kind === 'meters' ? [] : all.filter((d) => d.kind !== 'file')
  return all
})

const initial = ref<RegisterFormValues>(rowFormValues(defs.value, current.value, locale.value))
const values = ref<RegisterFormValues>(cloneFormValues(initial.value))
const initialName = ref(props.row?.name ?? '')
const name = ref(initialName.value)

const problems = ref<Record<string, RegisterProblem>>({})
const nameError = ref<RegisterProblem | null>(null)
const parentError = ref<RegisterProblem | null>(null)
const error = ref<string | null>(null)
const messages = ref<string[]>([])
const info = ref<string | null>(null)
const conflict = shallowRef<RegisterRow | null>(null)
const conflictText = ref<string | null>(null)
const uploaded = ref(false)

/* ---- create options ---- */
const optionsState = ref<'idle' | 'loading' | 'ready' | 'error'>(editing.value ? 'ready' : 'loading')
const meterOptions = shallowRef<MeterCreateOptions | null>(null)
const equipmentOptions = shallowRef<EquipmentCreateOptions | null>(null)
const meterClass = ref('')
const parentId = ref<number | ''>('')

async function loadOptions() {
  if (editing.value) return
  optionsState.value = 'loading'
  try {
    if (props.kind === 'meters') {
      const o = await loadMeterCreateOptions(auth.http, props.group.key, locale.value)
      meterOptions.value = { classes: Array.isArray(o?.classes) ? o.classes : [], parents: Array.isArray(o?.parents) ? o.parents : [] }
      meterClass.value = meterOptions.value.classes[0]?.class ?? ''
    } else {
      const o = await loadEquipmentCreateOptions(auth.http, props.group.key, locale.value)
      equipmentOptions.value = { parents: Array.isArray(o?.parents) ? o.parents : [] }
      if (equipmentOptions.value.parents.length === 1) parentId.value = equipmentOptions.value.parents[0].id
    }
    optionsState.value = 'ready'
  } catch {
    optionsState.value = 'error'
  }
}

const noCreateChoice = computed(() => {
  if (editing.value || optionsState.value !== 'ready') return false
  return props.kind === 'meters' ? !meterOptions.value?.classes.length : !equipmentOptions.value?.parents.length
})

/* ---- labels ---- */
const title = computed(() => (editing.value ? t('registerEdit.editTitle', { name: initialName.value }) : t('registerEdit.newTitle.' + props.kind)))
const fid = (def: RegisterFieldDef) => `${uid}-f${defs.value.indexOf(def)}`
const errId = (def: RegisterFieldDef) => fid(def) + '-err'
const hasReadOnly = computed(() => defs.value.some((d) => d.kind === 'readonly'))
const classLabel = (c: { class: string; label: string; medium: string | null }) =>
  c.label || mediumLabel({ medium: c.medium, classLabel: null, className: c.class }, tr, te)

/* ---- dirty state and the guard ---- */
const dirty = computed(() => {
  if (changedKeys(defs.value, initial.value, values.value, locale.value).length) return true
  if (name.value.trim() !== initialName.value.trim()) return true
  if (!editing.value && parentId.value !== '' && (equipmentOptions.value?.parents.length ?? 0) > 1) return true
  return false
})

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
  if (await confirmDrop()) emit('close')
}

/* only inside a routed page (tests may mount the view without one) */
const routeRecord = inject(matchedRouteKey, null)
if (routeRecord?.value) onBeforeRouteLeave(() => confirmDrop())

function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  void loadOptions()
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

/* ---- field helpers ---- */
function textOf(def: RegisterFieldDef): string {
  const v = values.value[def.key]
  return typeof v === 'string' ? v : ''
}

function setValue(def: RegisterFieldDef, value: RegisterFormValues[string]) {
  values.value = { ...values.value, [def.key]: value }
  info.value = null
  if (problems.value[def.key]) {
    const next = { ...problems.value }
    delete next[def.key]
    problems.value = next
  }
}

const onText = (def: RegisterFieldDef, e: Event) => setValue(def, (e.target as HTMLInputElement | HTMLTextAreaElement).value)
const onCheck = (def: RegisterFieldDef, e: Event) => setValue(def, (e.target as HTMLInputElement).checked)

function targetOf(def: RegisterFieldDef): TargetInput | null {
  const v = values.value[def.key]
  return v && typeof v === 'object' ? v : null
}

function targetText(def: RegisterFieldDef): string {
  const target = targetOf(def)
  if (!target) return t('registerEdit.target.none')
  const label = target.readable === false || !target.name ? t('registers.targetHidden') : target.name
  return target.attribute && target.attribute !== 'Value' ? `${label} (${target.attribute})` : label
}

const picking = ref<RegisterFieldDef | null>(null)
function picked(rows: PickedRow[]) {
  const def = picking.value
  picking.value = null
  const row = rows[0]
  if (!def || !row) return
  setValue(def, { id: row.node.id, attribute: row.attribute || 'Value', name: row.node.name, readable: true })
}

function readOnlyText(def: RegisterFieldDef): string {
  return formatAttribute(current.value?.values?.[def.key], def.column, locale.value, tr) || t('modules.none')
}

function describedBy(def: RegisterFieldDef, hint?: string): string | undefined {
  const ids = [problems.value[def.key] ? errId(def) : '', hint ?? ''].filter(Boolean)
  return ids.length ? ids.join(' ') : undefined
}

function onUploaded(def: RegisterFieldDef, file: UploadedFile) {
  if (current.value) current.value = withUploadedFile(current.value, def.key, { file: file.file, image: file.image }, file.version)
  uploaded.value = true
  emit('changed')
}

/* ---- save ---- */
function takeOver(row: RegisterRow) {
  current.value = row
  initial.value = rowFormValues(defs.value, row, locale.value)
  values.value = cloneFormValues(initial.value)
  initialName.value = row.name
  name.value = row.name
  problems.value = {}
}

function reloadFromConflict() {
  if (conflict.value) takeOver(conflict.value)
  conflict.value = null
  conflictText.value = null
  info.value = t('registerEdit.reloaded')
}

function keepEditing() {
  const row = conflict.value
  if (row) {
    const server = rowFormValues(defs.value, row, locale.value)
    const merged = mergeAfterConflict(defs.value, initial.value, values.value, server, locale.value)
    const ownName = name.value.trim() !== initialName.value.trim() ? name.value : row.name
    current.value = row
    initial.value = server
    values.value = merged
    initialName.value = row.name
    name.value = ownName
  }
  conflict.value = null
  conflictText.value = null
}

async function focusFirstProblem() {
  await nextTick()
  if (nameError.value) {
    document.getElementById(uid + '-name')?.focus()
    return
  }
  if (parentError.value) {
    document.getElementById(uid + '-parent')?.focus()
    return
  }
  const def = defs.value.find((d) => problems.value[d.key])
  if (def) document.getElementById(fid(def))?.focus()
}

async function save() {
  error.value = null
  messages.value = []
  info.value = null
  nameError.value = nameProblem(name.value)
  parentError.value = !editing.value && props.kind === 'equipment' && parentId.value === '' ? { key: 'registerEdit.problem.parentRequired' } : null
  problems.value = validateRegisterValues(defs.value, initial.value, values.value, locale.value)
  if (nameError.value || parentError.value || Object.keys(problems.value).length) {
    await focusFirstProblem()
    return
  }
  const fields = changedRegisterFields(defs.value, initial.value, values.value, editing.value ? current.value?.versions : null, locale.value)
  const newName = name.value.trim()
  const renamed = editing.value && newName !== initialName.value.trim()

  if (editing.value && !renamed && !Object.keys(fields).length) {
    info.value = t('registerEdit.nothingChanged')
    return
  }

  await run(async () => {
    try {
      let answer: RegisterRow
      if (editing.value) {
        answer = await updateRegisterObject(auth.http, current.value!.id, renamed ? { fields, name: newName } : { fields }, locale.value)
      } else if (props.kind === 'meters') {
        answer = await createRegisterObject(
          auth.http,
          'meters',
          { groupKey: props.group.key, class: meterClass.value, name: newName, parentId: parentId.value === '' ? null : parentId.value, fields },
          locale.value,
        )
      } else {
        answer = await createRegisterObject(auth.http, 'equipment', { class: props.group.key, parentId: parentId.value as number, name: newName, fields }, locale.value)
      }
      const row = answer && typeof answer === 'object' && typeof answer.id === 'number' ? answer : null
      emit('saved', row, !editing.value, row?.name || newName)
    } catch (e) {
      handle(e)
    }
  })
}

function handle(e: unknown) {
  const kind = writeErrorKind(e)
  if (kind === 'conflict' && editing.value) {
    const row = rowOf(e instanceof HttpError ? e.json() : null, current.value!.id)
    emit('changed')
    if (row) {
      conflict.value = row
      conflictText.value = t('registerEdit.conflict.message')
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

const submitLabel = computed(() => (busy.value ? t('edit.saving') : editing.value ? t('edit.save') : t('registerEdit.create')))
const canSubmit = computed(() => !busy.value && !conflict.value && optionsState.value === 'ready' && !noCreateChoice.value)
const unitSuffix = (def: RegisterFieldDef) => (def.inputUnit ? ` [${def.inputUnit}]` : '')
</script>

<template>
  <EditDialog :title="title" :busy="busy" wide class="reg-edit-backdrop" @close="requestClose">
    <form :id="uid + '-form'" class="reg-edit-form" novalidate :aria-describedby="messages.length ? uid + '-messages' : undefined" @submit.prevent="save">
      <dl class="edit-facts">
        <dt>{{ t('registerEdit.group') }}</dt>
        <dd>{{ group.name }}</dd>
        <template v-if="editing && current && (current.classLabel || current.className)">
          <dt>{{ t('registers.detail.class') }}</dt>
          <dd>{{ current.classLabel || current.className }}</dd>
        </template>
      </dl>

      <!-- create options -->
      <p v-if="optionsState === 'loading'" class="muted" role="status">{{ t('registerEdit.options.loading') }}</p>
      <div v-else-if="optionsState === 'error'" class="alert alert-error" role="alert">
        {{ t('registerEdit.options.error') }}
        <button type="button" class="btn-link" @click="loadOptions">{{ t('modules.retry') }}</button>
      </div>
      <p v-else-if="noCreateChoice" class="alert alert-error" role="alert">{{ t('registerEdit.options.none.' + kind) }}</p>

      <template v-if="optionsState === 'ready' && !noCreateChoice">
        <fieldset v-if="!editing && kind === 'meters' && meterOptions" class="reg-classes">
          <legend>{{ t('registerEdit.meterClass') }}</legend>
          <label v-for="c in meterOptions.classes" :key="c.class" class="reg-class" :class="{ 'is-selected': meterClass === c.class }">
            <input v-model="meterClass" type="radio" :name="uid + '-class'" :value="c.class" :disabled="busy" />
            <MediumIcon :medium="c.medium" />
            <span>{{ classLabel(c) }}</span>
          </label>
        </fieldset>

        <div v-if="!editing && kind === 'equipment' && equipmentOptions" class="edit-field">
          <label :for="uid + '-parent'">{{ t('registerEdit.parent') }}<span class="iso-req" aria-hidden="true"> *</span></label>
          <select
            :id="uid + '-parent'"
            v-model="parentId"
            :disabled="busy"
            required
            aria-required="true"
            :aria-invalid="parentError ? 'true' : undefined"
            :aria-describedby="parentError ? uid + '-parent-err' : undefined"
            @change="parentError = null"
          >
            <option value="" disabled>{{ t('registerEdit.choose') }}</option>
            <option v-for="p in equipmentOptions.parents" :key="p.id" :value="p.id">{{ p.path ? `${p.name} – ${p.path}` : p.name }}</option>
          </select>
          <p v-if="parentError" :id="uid + '-parent-err'" class="iso-field-error">{{ t(parentError.key) }}</p>
        </div>

        <div class="edit-field">
          <label :for="uid + '-name'">{{ t('registers.col.name') }}<span class="iso-req" aria-hidden="true"> *</span></label>
          <input
            :id="uid + '-name'"
            v-model="name"
            type="text"
            :maxlength="NAME_MAX + 20"
            autocomplete="off"
            required
            aria-required="true"
            :autofocus="editing || kind === 'equipment' ? true : undefined"
            :disabled="busy"
            :aria-invalid="nameError ? 'true' : undefined"
            :aria-describedby="nameError ? uid + '-name-err' : undefined"
            @input="nameError = null"
          />
          <p v-if="nameError" :id="uid + '-name-err'" class="iso-field-error">{{ t(nameError.key, nameError.params ?? {}) }}</p>
        </div>

        <div v-if="!editing && kind === 'meters' && meterOptions && meterOptions.parents.length" class="edit-field">
          <label :for="uid + '-parent'">{{ t('registerEdit.parentMeter') }}</label>
          <select :id="uid + '-parent'" v-model="parentId" :disabled="busy" :aria-describedby="uid + '-parent-hint'">
            <option value="">{{ t('registerEdit.noParentMeter') }}</option>
            <option v-for="p in meterOptions.parents" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
          <p :id="uid + '-parent-hint'" class="edit-hint">{{ t('registerEdit.parentMeterHint') }}</p>
        </div>
        <p v-if="!editing && kind === 'meters'" class="edit-hint reg-edit-hint">{{ t('registerEdit.meterAttributesLater') }}</p>
      </template>

      <!-- attribute form -->
      <section v-if="defs.length && optionsState === 'ready' && !noCreateChoice" class="iso-edit-section" :aria-labelledby="uid + '-attrs'">
        <h3 :id="uid + '-attrs'">{{ t('registerEdit.attributes') }}</h3>
        <p v-if="hasReadOnly" class="edit-hint reg-edit-hint">{{ t('registerEdit.readOnlyHint') }}</p>
        <p v-if="!editing" class="edit-hint reg-edit-hint">{{ t('registerEdit.filesAfterCreate') }}</p>
        <div class="iso-edit-grid">
          <template v-for="def in defs" :key="def.key">
            <!-- yes / no -->
            <div v-if="def.kind === 'boolean'" class="edit-field iso-edit-bool">
              <label class="iso-edit-check" :for="fid(def)">
                <input :id="fid(def)" type="checkbox" :checked="values[def.key] === true" :disabled="busy" @change="onCheck(def, $event)" />
                {{ def.label }}
              </label>
            </div>

            <!-- file: shown, uploaded right away -->
            <div v-else-if="def.kind === 'file'" class="edit-field iso-edit-wide reg-edit-file">
              <span :id="fid(def) + '-label'" class="reg-edit-label">{{ def.label }}</span>
              <RegisterFileField
                v-if="current"
                :object-id="current.id"
                :attribute="def.key"
                :label="def.label"
                :value="fileOf(current, def.key)"
                :version="current.versions?.[def.key] ?? null"
                :can-upload="current.canUpload === true && !busy"
                :field-id="fid(def)"
                @uploaded="onUploaded(def, $event)"
              />
            </div>

            <!-- read-only -->
            <div v-else-if="def.kind === 'readonly'" class="edit-field reg-edit-readonly" :class="{ 'iso-edit-wide': def.column.type === 'TEXTAREA' }">
              <label :for="fid(def)">{{ def.label }}</label>
              <input :id="fid(def)" type="text" :value="readOnlyText(def)" readonly aria-readonly="true" :aria-describedby="fid(def) + '-ro'" />
              <p :id="fid(def) + '-ro'" class="visually-hidden">{{ t('registerEdit.readOnlyField') }}</p>
            </div>

            <!-- target: data row picker -->
            <div v-else-if="def.kind === 'target'" class="edit-field iso-edit-wide reg-edit-target">
              <span :id="fid(def) + '-label'" class="reg-edit-label">{{ def.label }}</span>
              <div class="reg-target-row">
                <output :id="fid(def) + '-value'" class="reg-target-value" :class="{ muted: !targetOf(def) }" :aria-labelledby="fid(def) + '-label'">{{ targetText(def) }}</output>
                <button
                  :id="fid(def)"
                  type="button"
                  class="edit-btn is-small"
                  :disabled="busy"
                  :aria-describedby="fid(def) + '-label ' + fid(def) + '-value'"
                  @click="picking = def"
                >
                  {{ targetOf(def) ? t('registerEdit.target.change') : t('registerEdit.target.choose') }}
                </button>
                <button
                  v-if="targetOf(def)"
                  type="button"
                  class="edit-btn is-small is-danger"
                  :disabled="busy"
                  :aria-label="t('registerEdit.target.clearLabel', { name: def.label })"
                  @click="setValue(def, null)"
                >
                  {{ t('registerEdit.target.clear') }}
                </button>
              </div>
            </div>

            <!-- text, multi-line text, number, integer, date -->
            <div v-else class="edit-field" :class="{ 'iso-edit-wide': def.kind === 'textarea' }">
              <label :for="fid(def)">{{ def.label }}{{ unitSuffix(def) }}</label>
              <textarea
                v-if="def.kind === 'textarea'"
                :id="fid(def)"
                :value="textOf(def)"
                maxlength="20000"
                :disabled="busy"
                @input="onText(def, $event)"
              />
              <input
                v-else
                :id="fid(def)"
                :value="textOf(def)"
                :type="def.kind === 'date' ? 'date' : 'text'"
                :inputmode="def.kind === 'number' ? 'decimal' : def.kind === 'integer' ? 'numeric' : undefined"
                :maxlength="def.kind === 'text' ? 2000 : undefined"
                autocomplete="off"
                :disabled="busy"
                :aria-invalid="problems[def.key] ? 'true' : undefined"
                :aria-describedby="describedBy(def)"
                @input="onText(def, $event)"
              />
              <p v-if="problems[def.key]" :id="errId(def)" class="iso-field-error">{{ t(problems[def.key].key, problems[def.key].params ?? {}) }}</p>
            </div>
          </template>
        </div>
      </section>

      <div v-if="messages.length" :id="uid + '-messages'" class="alert alert-error iso-messages" role="alert">
        <strong>{{ t('registerEdit.invalidTitle') }}</strong>
        <ul class="edit-warnings">
          <li v-for="(m, i) in messages" :key="i">{{ m }}</li>
        </ul>
      </div>
      <div v-if="conflict" class="alert alert-error reg-conflict" role="alert">
        <strong>{{ t('registerEdit.conflict.title') }}</strong>
        <p>{{ conflictText }}</p>
        <div class="edit-actions">
          <button type="button" class="edit-btn reg-conflict-reload" @click="reloadFromConflict">{{ t('registerEdit.conflict.reload') }}</button>
          <button type="button" class="edit-btn reg-conflict-keep" @click="keepEditing">{{ t('registerEdit.conflict.keep') }}</button>
        </div>
      </div>
      <p v-if="info" class="muted iso-edit-info" role="status">{{ info }}</p>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <span v-if="editing" class="edit-left reg-edit-state" :class="{ 'is-dirty': dirty }" role="status">
        {{ dirty ? t('registerEdit.unsaved') : uploaded ? t('registerEdit.uploadedState') : t('registerEdit.noChanges') }}
      </span>
      <button type="button" class="edit-btn" :disabled="busy" @click="requestClose">{{ t('edit.cancel') }}</button>
      <button type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="!canSubmit">{{ submitLabel }}</button>
    </template>
  </EditDialog>

  <RowPickerDialog
    v-if="picking"
    single
    :present="[]"
    :title="t('registerEdit.target.pickTitle', { name: picking.label })"
    @close="picking = null"
    @add="picked"
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
