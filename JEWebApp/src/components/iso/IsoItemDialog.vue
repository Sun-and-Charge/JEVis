<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { HttpError } from '../../api/http'
import { createIsoItem, updateIsoItem, type IsoItem, type IsoKind } from '../../api/iso'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import {
  ACTION_STATUS_KEY,
  changedFields,
  cloneValues,
  emptyValues,
  fieldOptions,
  fieldsOf,
  itemLabel,
  itemValues,
  localToday,
  serverMessages,
  validateValues,
  withDoneDate,
  type FieldDef,
  type FieldProblem,
  type FormSection,
  type FormValues,
  type IsoPlan,
} from '../../iso/edit'
import { useAuthStore } from '../../stores/auth'
import EditDialog from '../edit/EditDialog.vue'
import '../../styles/isoEdit.css'

/*
 * "Neu" / "Bearbeiten" of an action, nonconformity or obligation (E2). Sends only the changed fields (with the
 * loaded version); values no longer in the plan's lists are shown and kept unless changed. 409: the current item of
 * the answer is taken over and the caller updates its list; 400: the server's messages (e.g. the requirement check
 * of nonconformities) are listed next to the form.
 */

const props = defineProps<{
  kind: IsoKind
  /** plan of the item, or the preselected plan of a new item */
  plan: IsoPlan
  /** new item: the plans the user may create in (a choice if more than one) */
  plans?: readonly IsoPlan[]
  /** the item to change; without it a new item is created */
  item?: IsoItem | null
  /** today (yyyy-MM-dd) for the done date of actions; default: the browser's day */
  today?: string
}>()

const emit = defineEmits<{
  close: []
  /** saved: the item of the answer (null if the answer had none: reload) */
  saved: [item: IsoItem | null, planId: number, created: boolean]
  /** 409 / 404: the current item (409 body) or null (reload the list) */
  stale: [item: IsoItem | null, planId: number]
}>()

const { t } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()

const defs = computed(() => fieldsOf(props.kind))
const editing = computed(() => !!props.item)
const planId = ref<number>(props.plan.id)
const choices = computed(() => (!props.item && props.plans && props.plans.length > 1 ? props.plans : []))
const currentPlan = computed<IsoPlan>(() => choices.value.find((p) => p.id === planId.value) ?? props.plan)

const initial = ref<FormValues>(props.item ? itemValues(defs.value, props.item) : emptyValues(defs.value))
const values = ref<FormValues>(cloneValues(initial.value))
const version = ref<string | null>(props.item?.version ?? null)
const label = ref(props.item ? itemLabel(props.kind, props.item) : '')

const problems = ref<Record<string, FieldProblem>>({})
const error = ref<string | null>(null)
const messages = ref<string[]>([])
const info = ref<string | null>(null)

const uid = `iso-dlg-${Math.random().toString(36).slice(2, 9)}`
const fid = (def: FieldDef) => `${uid}-f${defs.value.indexOf(def)}`
const errId = (def: FieldDef) => fid(def) + '-err'

const title = computed(() => (editing.value ? t('isoEdit.editTitle', { title: label.value }) : t('isoEdit.newTitle.' + props.kind)))
const sections = computed(() => {
  const order: FormSection[] = ['general', 'notes', 'checkList']
  return order
    .map((section) => ({ section, fields: defs.value.filter((d) => d.section === section) }))
    .filter((s) => s.fields.length)
})
const sectionTitle = (s: FormSection) => (s === 'general' ? t('iso.section.general') : s === 'notes' ? t('isoEdit.section.' + props.kind) : t('iso.section.checkList'))

function options(def: FieldDef) {
  return fieldOptions(def, currentPlan.value, initial.value[def.key])
}

function optionLabel(o: { label: string; stale?: boolean }) {
  return o.stale ? t('isoEdit.staleOption', { value: o.label }) : o.label
}

function multi(def: FieldDef): string[] {
  const v = values.value[def.key]
  return Array.isArray(v) ? v : []
}

function toggleChip(def: FieldDef, value: string) {
  const list = multi(def)
  values.value = { ...values.value, [def.key]: list.includes(value) ? list.filter((v) => v !== value) : [...list, value] }
  clearProblem(def)
}

function textOf(def: FieldDef): string {
  const v = values.value[def.key]
  return typeof v === 'string' ? v : ''
}

function setText(def: FieldDef, e: Event) {
  values.value = { ...values.value, [def.key]: (e.target as HTMLInputElement | HTMLTextAreaElement).value }
  clearProblem(def)
}

function checks(def: FieldDef): Record<string, boolean> {
  return (values.value[def.key] as Record<string, boolean>) ?? {}
}

function toggleCheck(def: FieldDef, key: string, checked: boolean) {
  values.value = { ...values.value, [def.key]: { ...checks(def), [key]: checked } }
}

function clearProblem(def: FieldDef) {
  if (problems.value[def.key]) {
    const next = { ...problems.value }
    delete next[def.key]
    problems.value = next
  }
}

function changed(def: FieldDef) {
  clearProblem(def)
  if (props.kind === 'actions' && def.key === ACTION_STATUS_KEY) {
    const next = withDoneDate(values.value, props.today || localToday())
    if (next) {
      values.value = next
      info.value = t('isoEdit.doneDateSet')
    }
  }
}

function describedBy(def: FieldDef, hint?: string): string | undefined {
  const ids = [problems.value[def.key] ? errId(def) : '', hint ?? ''].filter(Boolean)
  return ids.length ? ids.join(' ') : undefined
}

function takeOver(item: IsoItem) {
  initial.value = itemValues(defs.value, item)
  values.value = cloneValues(initial.value)
  version.value = item.version ?? null
  label.value = itemLabel(props.kind, item)
  problems.value = {}
}

async function save() {
  error.value = null
  messages.value = []
  info.value = null
  problems.value = validateValues(defs.value, currentPlan.value, initial.value, values.value)
  const invalid = defs.value.find((d) => problems.value[d.key])
  if (invalid) {
    await nextTick()
    document.getElementById(fid(invalid))?.focus()
    return
  }
  const fields = changedFields(defs.value, initial.value, values.value)
  if (editing.value && !Object.keys(fields).length) {
    info.value = t('isoEdit.nothingChanged')
    return
  }
  if (editing.value && !version.value) {
    error.value = t('isoEdit.noVersion')
    return
  }
  await run(async () => {
    try {
      const saved = editing.value
        ? await updateIsoItem<IsoItem>(auth.http, props.kind, props.item!.id, version.value!, fields)
        : await createIsoItem<IsoItem>(auth.http, props.kind, currentPlan.value.id, fields)
      emit('saved', saved && typeof saved === 'object' ? saved : null, currentPlan.value.id, !editing.value)
    } catch (e) {
      handle(e)
    }
  })
}

function handle(e: unknown) {
  const kind = writeErrorKind(e)
  if (kind === 'conflict' && editing.value) {
    const current = e instanceof HttpError ? e.json<IsoItem>() : null
    const usable = current && typeof current === 'object' && current.id === props.item!.id ? current : null
    if (usable) takeOver(usable)
    error.value = t('isoEdit.conflict')
    emit('stale', usable, currentPlan.value.id)
    return
  }
  if (kind === 'notFound') {
    error.value = writeErrorText(e, t)
    emit('stale', null, currentPlan.value.id)
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
</script>

<template>
  <EditDialog :title="title" :busy="busy" wide @close="emit('close')">
    <form :id="uid + '-form'" class="iso-edit-form" novalidate :aria-describedby="messages.length ? uid + '-messages' : undefined" @submit.prevent="save">
      <p class="edit-hint iso-req-hint">{{ t('isoEdit.requiredHint') }}</p>

      <div v-if="choices.length" class="edit-field">
        <label :for="uid + '-plan'">{{ t('iso.filter.plan') }}</label>
        <select :id="uid + '-plan'" v-model="planId" class="iso-plan-select" :disabled="busy">
          <option v-for="p in choices" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
      </div>
      <dl v-else class="edit-facts">
        <dt>{{ t('iso.col.plan') }}</dt>
        <dd>{{ currentPlan.name }}</dd>
        <template v-if="editing && label">
          <dt>{{ t('isoEdit.entry') }}</dt>
          <dd>{{ label }}</dd>
        </template>
      </dl>
      <p v-if="kind === 'actions'" class="edit-hint iso-calculated">{{ t('isoEdit.calculated') }}</p>

      <div
        v-if="messages.length"
        :id="uid + '-messages'"
        class="alert alert-error iso-messages"
        role="alert"
      >
        <strong>{{ t(kind === 'nonconformities' ? 'isoEdit.requirementsTitle' : 'isoEdit.invalidTitle') }}</strong>
        <ul class="edit-warnings">
          <li v-for="(m, i) in messages" :key="i">{{ m }}</li>
        </ul>
      </div>

      <section v-for="s in sections" :key="s.section" class="iso-edit-section" :aria-labelledby="uid + '-s-' + s.section">
        <h3 :id="uid + '-s-' + s.section">{{ sectionTitle(s.section) }}</h3>
        <div class="iso-edit-grid">
          <template v-for="def in s.fields" :key="def.key">
            <!-- multi select: chips -->
            <fieldset
              v-if="def.kind === 'multi'"
              :id="fid(def)"
              class="edit-tags iso-edit-wide"
              tabindex="-1"
              :aria-describedby="describedBy(def)"
              :aria-invalid="problems[def.key] ? 'true' : undefined"
            >
              <legend>{{ t(def.label) }}</legend>
              <p v-if="!options(def).length" class="muted edit-hint">{{ t('isoEdit.noOptions') }}</p>
              <button
                v-for="o in options(def)"
                :key="o.value"
                type="button"
                class="edit-chip"
                :class="{ 'is-stale': o.stale }"
                :aria-pressed="multi(def).includes(o.value)"
                :disabled="busy"
                @click="toggleChip(def, o.value)"
              >
                {{ optionLabel(o) }}
              </button>
              <p v-if="problems[def.key]" :id="errId(def)" class="iso-field-error">{{ t(problems[def.key].key, problems[def.key].params ?? {}) }}</p>
            </fieldset>

            <!-- check list -->
            <fieldset v-else-if="def.kind === 'checks'" :id="fid(def)" class="iso-edit-checks iso-edit-wide" tabindex="-1">
              <legend class="visually-hidden">{{ t(def.label) }}</legend>
              <label v-for="c in def.checks" :key="c.key" class="iso-edit-check">
                <input type="checkbox" :checked="checks(def)[c.key] === true" :disabled="busy" @change="toggleCheck(def, c.key, ($event.target as HTMLInputElement).checked)" />
                {{ t(def.checkLabels + '.' + c.webKey) }}
              </label>
            </fieldset>

            <!-- boolean -->
            <div v-else-if="def.kind === 'bool'" class="edit-field iso-edit-bool">
              <label class="iso-edit-check" :for="fid(def)">
                <input :id="fid(def)" v-model="values[def.key]" type="checkbox" :disabled="busy" />
                {{ t(def.label) }}
              </label>
            </div>

            <div v-else class="edit-field" :class="{ 'iso-edit-wide': def.kind === 'textarea' || def.required || def.kind === 'link' }">
              <label :for="fid(def)">
                {{ t(def.label) }}<span v-if="def.required" class="iso-req" aria-hidden="true"> *</span>
              </label>
              <select
                v-if="def.kind === 'select'"
                :id="fid(def)"
                v-model="values[def.key]"
                :disabled="busy"
                :aria-invalid="problems[def.key] ? 'true' : undefined"
                :aria-describedby="describedBy(def)"
                @change="changed(def)"
              >
                <option value="">{{ t('isoEdit.noSelection') }}</option>
                <option v-for="o in options(def)" :key="o.value" :value="o.value">{{ optionLabel(o) }}</option>
              </select>
              <textarea
                v-else-if="def.kind === 'textarea'"
                :id="fid(def)"
                :value="textOf(def)"
                maxlength="20000"
                :disabled="busy"
                :aria-invalid="problems[def.key] ? 'true' : undefined"
                :aria-describedby="describedBy(def)"
                @input="setText(def, $event)"
              />
              <input
                v-else
                :id="fid(def)"
                :value="textOf(def)"
                :type="def.kind === 'date' ? 'date' : def.kind === 'link' ? 'url' : 'text'"
                :inputmode="def.kind === 'link' ? 'url' : undefined"
                :maxlength="def.kind === 'date' ? undefined : 2000"
                :required="def.required || undefined"
                :aria-required="def.required ? 'true' : undefined"
                :autofocus="def.required || undefined"
                :disabled="busy"
                :aria-invalid="problems[def.key] ? 'true' : undefined"
                :aria-describedby="describedBy(def)"
                @input="setText(def, $event)"
              />
              <p v-if="problems[def.key]" :id="errId(def)" class="iso-field-error">{{ t(problems[def.key].key, problems[def.key].params ?? {}) }}</p>
            </div>
          </template>
        </div>
      </section>

      <p v-if="info" class="muted iso-edit-info" role="status">{{ info }}</p>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="edit-btn" :disabled="busy" @click="emit('close')">{{ t('edit.cancel') }}</button>
      <button type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="busy">
        {{ busy ? t('edit.saving') : editing ? t('edit.save') : t('isoEdit.create') }}
      </button>
    </template>
  </EditDialog>
</template>
