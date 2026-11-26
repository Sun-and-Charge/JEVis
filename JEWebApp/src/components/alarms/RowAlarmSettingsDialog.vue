<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  OPERATOR_SYMBOLS,
  RULES_MAX,
  checkRow,
  cloneRowForm,
  conflictRowSettingsOf,
  defaultSteps,
  mergeRowForm,
  newRule,
  newOutageRule,
  noDataMinutes,
  rowChanges,
  rowForm,
  rowSettingsOf,
  type Problem,
  type RowForm,
  type RuleForm,
  type ScheduleForm,
  type StepForm,
} from '../../alarms/settings'
import { OPERATORS, SUBSTITUTES, loadRowAlarmSettings, saveRowAlarmSettings, type RowAlarmSettings } from '../../api/alarmSettings'
import type { PickedRow } from '../../analysis/editor'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLeaveGuard } from '../../edit/useLeaveGuard'
import { serverMessages } from '../../iso/edit'
import { useAuthStore } from '../../stores/auth'
import RowPickerDialog from '../analysis/edit/RowPickerDialog.vue'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import AlarmScheduleField from './AlarmScheduleField.vue'
import '../../styles/edit.css'
import '../../styles/isoEdit.css'
import '../../styles/registersEdit.css'
import '../../styles/alarmSettings.css'

/*
 * Alarm settings of a clean data row (E7.2), as the Control Center's LimitEditor and AlarmEditor: limit steps 1 and 2
 * (min / max, substitute, defaults for DEFAULT_VALUE) and the alarm rules (fixed value or comparison row, operator,
 * tolerance, silent and standby time as weekdays + time range). Lists the alarm configurations that report the row.
 * Side panel (full-width sheet on phones); opened from the alarm list, the configuration editor, the meter register
 * and the meter reading panel. 409: "Neu laden" / "Weiter bearbeiten" as E6.2; closing with changes asks first.
 */

const props = defineProps<{
  rowId: number
  /** name shown while loading */
  rowName?: string | null
}>()
const emit = defineEmits<{ close: []; saved: [settings: RowAlarmSettings] }>()

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `alm-row-${Math.random().toString(36).slice(2, 9)}`
const id = (suffix: string) => `${uid}-${suffix}`

const loadState = ref<'loading' | 'ready' | 'error'>('loading')
const loadError = ref<string | null>(null)
const model = shallowRef<RowAlarmSettings | null>(null)
const initial = ref<RowForm>(rowForm(null, locale.value))
const form = ref<RowForm>(cloneRowForm(initial.value))
const problems = ref<Record<string, Problem>>({})
const error = ref<string | null>(null)
const messages = ref<string[]>([])
const info = ref<string | null>(null)
const reprocess = ref(false)
const conflict = shallowRef<RowAlarmSettings | null>(null)

const writable = computed(() => model.value?.canWrite === true)
const changes = computed(() => rowChanges(initial.value, form.value))
const formDirty = computed(() => changes.value.limits || changes.value.alarms)
const dirty = computed(() => writable.value && loadState.value === 'ready' && formDirty.value)
const { leaveAsk, confirmDrop } = useLeaveGuard(dirty)

async function requestClose() {
  if (busy.value) return
  if (await confirmDrop()) emit('close')
}

function apply(s: RowAlarmSettings) {
  model.value = s
  initial.value = rowForm(s, locale.value)
  form.value = cloneRowForm(initial.value)
}

async function load() {
  loadState.value = 'loading'
  loadError.value = null
  try {
    const s = rowSettingsOf(await loadRowAlarmSettings(auth.http, props.rowId, locale.value))
    if (!s) throw new Error('no settings')
    apply(s)
    loadState.value = 'ready'
  } catch (e) {
    const kind = writeErrorKind(e)
    loadError.value = kind === 'notFound' || kind === 'forbidden' ? t('alarmSettings.row.notAvailable') : t('alarmSettings.row.loadError')
    loadState.value = 'error'
  }
}

onMounted(load)

/* ---------- fields ---------- */
const disabled = computed(() => busy.value || !writable.value)
const unit = computed(() => model.value?.row.unit || '')
const title = computed(() => t('alarmSettings.row.title', { name: model.value?.row.name || props.rowName || '#' + props.rowId }))
const problemText = (key: string) => {
  const p = problems.value[key]
  return p ? t(p.key, p.params ?? {}) : null
}
const describedBy = (key: string, hint?: string) => [problems.value[key] ? id(key + '-err') : '', hint ?? ''].filter(Boolean).join(' ') || undefined

function clearProblem(key: string) {
  info.value = null
  if (!problems.value[key]) return
  const p = { ...problems.value }
  delete p[key]
  problems.value = p
}

const substituteName = (s: string) => (te('alarmSettings.substitute.' + s) ? t('alarmSettings.substitute.' + s) : s)
const operatorName = (o: string) => (te('alarmSettings.operator.' + o) ? `${OPERATOR_SYMBOLS[o]} ${t('alarmSettings.operator.' + o)}` : o)
const extraSubstitute = (s: StepForm) => (s.substitute && !(SUBSTITUTES as readonly string[]).includes(s.substitute) ? s.substitute : null)
const extraOperator = (r: RuleForm) => (r.operator && !(OPERATORS as readonly string[]).includes(r.operator) ? r.operator : null)

function setLimitsEnabled(e: Event) {
  const on = (e.target as HTMLInputElement).checked
  form.value.limitsEnabled = on
  if (on && !form.value.steps.length) form.value.steps = defaultSteps([t('alarmSettings.limits.step', { n: 1 }), t('alarmSettings.limits.step', { n: 2 })])
  clearProblem('steps')
}

function setStep(i: number, key: keyof Omit<StepForm, 'extra'>, value: string) {
  const steps = [...form.value.steps]
  steps[i] = { ...steps[i], [key]: value }
  form.value.steps = steps
  clearProblem(`step${i}.${key}`)
  if (key === 'min') clearProblem(`step${i}.max`)
}

function addStep() {
  if (form.value.steps.length >= 2) return
  form.value.steps = [...form.value.steps, ...defaultSteps([t('alarmSettings.limits.step', { n: form.value.steps.length + 1 })])]
  clearProblem('steps')
}

function removeStep(i: number) {
  form.value.steps = form.value.steps.filter((_, j) => j !== i)
  problems.value = {}
}

const stepLabel = (s: StepForm, i: number) => t('alarmSettings.limits.step', { n: i + 1 }) + (s.name && s.name !== t('alarmSettings.limits.step', { n: i + 1 }) ? ` – ${s.name}` : '')

/* ---------- rules ---------- */
function updateRule(r: RuleForm, patch: Partial<RuleForm>) {
  form.value.rules = form.value.rules.map((x) => (x.uid === r.uid ? { ...x, ...patch } : x))
  for (const k of Object.keys(patch)) clearProblem(`${r.uid}.${k}`)
  if ('kind' in patch) {
    clearProblem(`${r.uid}.limit`)
    clearProblem(`${r.uid}.limitData`)
  }
}

async function addRule() {
  if (form.value.rules.length >= RULES_MAX) return
  const r = newRule()
  form.value.rules = [...form.value.rules, r]
  clearProblem('rules')
  await nextTick()
  document.getElementById(id(r.uid + '-limit'))?.focus()
}

function removeRule(r: RuleForm) {
  form.value.rules = form.value.rules.filter((x) => x.uid !== r.uid)
  clearProblem('rules')
}

const ruleLabel = (r: RuleForm, i: number) => (r.name ? t('alarmSettings.rules.ruleNamed', { n: i + 1, name: r.name }) : t('alarmSettings.rules.rule', { n: i + 1 }))

/** rule whose comparison row is being chosen */
const picking = ref<RuleForm | null>(null)

function picked(rows: PickedRow[]) {
  const p = rows[0]
  const r = picking.value
  picking.value = null
  if (!p || !r) return
  updateRule(r, { limitData: { id: p.node.id, attribute: p.attribute || 'Value', name: p.node.name, readable: true } })
}

const limitDataText = (r: RuleForm) => {
  const d = r.limitData
  if (!d) return t('alarmSettings.rules.noLimitData')
  const name = d.readable ? d.name || '#' + d.id : t('alarms.noAccess')
  return d.attribute && d.attribute !== 'Value' ? `${name} (${d.attribute})` : name
}

/* ---------- save ---------- */
async function focusFirstProblem() {
  await nextTick()
  document.querySelector<HTMLElement>(`#${uid}-form [aria-invalid="true"]`)?.focus()
}

async function save() {
  error.value = null
  messages.value = []
  info.value = null
  reprocess.value = false
  const c = changes.value
  if (!c.limits && !c.alarms) {
    info.value = t('registerEdit.nothingChanged')
    return
  }
  const check = checkRow(form.value, c, locale.value)
  problems.value = check.problems
  if (!check.body) {
    await focusFirstProblem()
    return
  }
  const body = { version: model.value?.version ?? 'none', ...check.body }
  await run(async () => {
    try {
      const answer = rowSettingsOf(await saveRowAlarmSettings(auth.http, props.rowId, body, locale.value))
      if (answer) apply(answer)
      else await load()
      reprocess.value = c.limits && (answer?.reprocessHint ?? true)
      info.value = t('alarmSettings.row.saved')
      if (model.value) emit('saved', model.value)
    } catch (e) {
      handle(e)
    }
  })
}

function handle(e: unknown) {
  const kind = writeErrorKind(e)
  if (kind === 'conflict') {
    const s = conflictRowSettingsOf(e)
    if (s) {
      conflict.value = s
      return
    }
    error.value = t('registerEdit.conflict.noRow')
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
  const s = conflict.value
  conflict.value = null
  problems.value = {}
  if (s) apply(s)
  info.value = t('registerEdit.reloaded')
}

function keepEditing() {
  const s = conflict.value
  conflict.value = null
  if (!s) return
  const server = rowForm(s, locale.value)
  const merged = mergeRowForm(initial.value, form.value, server)
  model.value = s
  initial.value = server
  form.value = merged
}

const stateText = computed(() => (formDirty.value ? t('registerEdit.unsaved') : t('registerEdit.noChanges')))
const scheduleUpdate = (r: RuleForm, key: 'silent' | 'standby' | 'valid') => (v: ScheduleForm) => updateRule(r, { [key]: v } as Partial<RuleForm>)

/* ---------- data outage (docs/ALARMS-OUTAGE.md) ---------- */
async function addOutageRule() {
  if (form.value.rules.length >= RULES_MAX) return
  const r = newOutageRule()
  form.value.rules = [...form.value.rules, r]
  clearProblem('rules')
  await nextTick()
  document.getElementById(id(r.uid + '-nodata'))?.focus()
}

function setKind(r: RuleForm, kind: string) {
  const patch: Partial<RuleForm> = { kind }
  if (kind === 'NO_DATA' && !r.noData) Object.assign(patch, { noData: '2', noDataUnit: 'HOURS' })
  if (kind !== 'NO_DATA' && !r.operator) patch.operator = 'BIGGER'
  updateRule(r, patch)
}

/** newest raw value and, with an outage rule, whether it is older than allowed */
const lastValue = computed(() => {
  const iso = model.value?.row.lastValue
  if (!iso) return null
  const ms = Date.parse(iso)
  if (Number.isNaN(ms)) return null
  let allowed: number | null = null
  for (const r of form.value.rules) {
    if (r.kind !== 'NO_DATA') continue
    const m = noDataMinutes(r, locale.value)
    if (m != null && !Number.isNaN(m) && (allowed == null || m < allowed)) allowed = m
  }
  const ageMinutes = Math.floor((Date.now() - ms) / 60000)
  /* an instant (the server writes UTC): shown in local time, not cut to its clock time */
  const text = new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(ms)
  return { text, overdue: allowed != null && ageMinutes > allowed, hours: Math.floor(ageMinutes / 60) }
})
</script>

<template>
  <EditDialog :title="title" :busy="busy" wide class="alm-edit-backdrop alm-row-backdrop" @close="requestClose">
    <p v-if="loadState === 'loading'" class="muted" role="status">{{ t('alarmSettings.row.loading') }}</p>
    <div v-else-if="loadState === 'error'" class="alert alert-error" role="alert">
      {{ loadError }}
      <button type="button" class="btn-link retry" @click="load">{{ t('modules.retry') }}</button>
    </div>

    <template v-else-if="model">
      <dl class="edit-facts">
        <dt>{{ t('alarmSettings.row.row') }}</dt>
        <dd>{{ model.row.name }}<template v-if="unit"> [{{ unit }}]</template></dd>
        <template v-if="model.row.rawName">
          <dt>{{ t('alarmSettings.row.raw') }}</dt>
          <dd>{{ model.row.rawName }}</dd>
        </template>
        <template v-if="lastValue">
          <dt>{{ t('alarmSettings.row.lastValue') }}</dt>
          <dd>
            {{ lastValue.text }}
            <span v-if="lastValue.overdue" class="mod-badge is-overdue alm-nodata-badge">{{ t('alarmSettings.row.noDataSince', { n: lastValue.hours }) }}</span>
          </dd>
        </template>
      </dl>
      <p v-if="!writable" class="muted read-only alm-row-readonly">{{ t('alarmSettings.row.readOnly') }}</p>

      <section class="iso-edit-section alm-row-configs" :aria-labelledby="id('configs')">
        <h3 :id="id('configs')">{{ t('alarmSettings.row.configurations') }}</h3>
        <ul v-if="model.configurations.length" class="alm-configs">
          <li v-for="c in model.configurations" :key="c.id">{{ c.name }}</li>
        </ul>
        <p v-else class="alm-warning alm-no-config">{{ t('alarmSettings.row.noConfiguration') }}</p>
      </section>

      <form :id="uid + '-form'" class="alm-row-form" novalidate :aria-describedby="messages.length ? id('messages') : undefined" @submit.prevent="save">
        <section class="iso-edit-section alm-limits" :aria-labelledby="id('limits')">
          <h3 :id="id('limits')">{{ t('alarmSettings.limits.title') }}</h3>
          <div class="edit-field">
            <label class="iso-edit-check">
              <input type="checkbox" class="alm-limits-on" :checked="form.limitsEnabled" :disabled="disabled" @change="setLimitsEnabled" />
              <span>{{ t('alarmSettings.limits.enabled') }}</span>
            </label>
            <p class="edit-hint">{{ t('alarmSettings.limits.hint') }}</p>
          </div>
          <p v-if="problemText('steps')" class="iso-field-error">{{ problemText('steps') }}</p>
          <template v-if="form.limitsEnabled || form.steps.length">
            <fieldset v-for="(s, i) in form.steps" :key="i" class="alm-step">
              <legend>{{ stepLabel(s, i) }}</legend>
              <div class="iso-edit-grid">
                <div class="edit-field">
                  <label :for="id(`step${i}-min`)">{{ t('alarmSettings.limits.min') }}<template v-if="unit"> [{{ unit }}]</template></label>
                  <input
                    :id="id(`step${i}-min`)"
                    :value="s.min"
                    type="text"
                    inputmode="decimal"
                    autocomplete="off"
                    :disabled="disabled"
                    :aria-invalid="problemText(`step${i}.min`) ? 'true' : undefined"
                    :aria-describedby="describedBy(`step${i}.min`)"
                    @input="setStep(i, 'min', ($event.target as HTMLInputElement).value)"
                  />
                  <p v-if="problemText(`step${i}.min`)" :id="id(`step${i}.min-err`)" class="iso-field-error">{{ problemText(`step${i}.min`) }}</p>
                </div>
                <div class="edit-field">
                  <label :for="id(`step${i}-max`)">{{ t('alarmSettings.limits.max') }}<template v-if="unit"> [{{ unit }}]</template></label>
                  <input
                    :id="id(`step${i}-max`)"
                    :value="s.max"
                    type="text"
                    inputmode="decimal"
                    autocomplete="off"
                    :disabled="disabled"
                    :aria-invalid="problemText(`step${i}.max`) ? 'true' : undefined"
                    :aria-describedby="describedBy(`step${i}.max`)"
                    @input="setStep(i, 'max', ($event.target as HTMLInputElement).value)"
                  />
                  <p v-if="problemText(`step${i}.max`)" :id="id(`step${i}.max-err`)" class="iso-field-error">{{ problemText(`step${i}.max`) }}</p>
                </div>
                <div class="edit-field iso-edit-wide">
                  <label :for="id(`step${i}-sub`)">{{ t('alarmSettings.limits.substitute') }}</label>
                  <select :id="id(`step${i}-sub`)" :value="s.substitute" :disabled="disabled" @change="setStep(i, 'substitute', ($event.target as HTMLSelectElement).value)">
                    <option v-for="x in SUBSTITUTES" :key="x" :value="x">{{ substituteName(x) }}</option>
                    <option v-if="extraSubstitute(s)" :value="extraSubstitute(s)!">{{ extraSubstitute(s) }}</option>
                  </select>
                </div>
                <template v-if="s.substitute === 'DEFAULT_VALUE'">
                  <div class="edit-field">
                    <label :for="id(`step${i}-dmin`)">{{ t('alarmSettings.limits.defaultMin') }}<span class="iso-req" aria-hidden="true"> *</span></label>
                    <input
                      :id="id(`step${i}-dmin`)"
                      :value="s.defaultMin"
                      type="text"
                      inputmode="decimal"
                      autocomplete="off"
                      aria-required="true"
                      :disabled="disabled"
                      :aria-invalid="problemText(`step${i}.defaultMin`) ? 'true' : undefined"
                      :aria-describedby="describedBy(`step${i}.defaultMin`)"
                      @input="setStep(i, 'defaultMin', ($event.target as HTMLInputElement).value)"
                    />
                    <p v-if="problemText(`step${i}.defaultMin`)" :id="id(`step${i}.defaultMin-err`)" class="iso-field-error">{{ problemText(`step${i}.defaultMin`) }}</p>
                  </div>
                  <div class="edit-field">
                    <label :for="id(`step${i}-dmax`)">{{ t('alarmSettings.limits.defaultMax') }}<span class="iso-req" aria-hidden="true"> *</span></label>
                    <input
                      :id="id(`step${i}-dmax`)"
                      :value="s.defaultMax"
                      type="text"
                      inputmode="decimal"
                      autocomplete="off"
                      aria-required="true"
                      :disabled="disabled"
                      :aria-invalid="problemText(`step${i}.defaultMax`) ? 'true' : undefined"
                      :aria-describedby="describedBy(`step${i}.defaultMax`)"
                      @input="setStep(i, 'defaultMax', ($event.target as HTMLInputElement).value)"
                    />
                    <p v-if="problemText(`step${i}.defaultMax`)" :id="id(`step${i}.defaultMax-err`)" class="iso-field-error">{{ problemText(`step${i}.defaultMax`) }}</p>
                  </div>
                </template>
              </div>
              <div v-if="writable && i === 1" class="edit-actions">
                <button type="button" class="edit-btn is-small is-danger alm-step-remove" :disabled="busy" @click="removeStep(i)">{{ t('alarmSettings.limits.removeStep') }}</button>
              </div>
            </fieldset>
            <div v-if="writable && form.limitsEnabled && form.steps.length < 2" class="edit-actions">
              <button type="button" class="edit-btn alm-step-add" :disabled="busy" @click="addStep">{{ t('alarmSettings.limits.addStep', { n: form.steps.length + 1 }) }}</button>
            </div>
          </template>
          <p v-if="reprocess" class="alert alm-warning alm-reprocess" role="status">{{ t('alarmSettings.row.reprocess') }}</p>
        </section>

        <section class="iso-edit-section alm-rules" :aria-labelledby="id('rules')">
          <h3 :id="id('rules')">{{ t('alarmSettings.rules.title') }}</h3>
          <div class="edit-field">
            <label class="iso-edit-check">
              <input v-model="form.alarmsEnabled" type="checkbox" class="alm-alarms-on" :disabled="disabled" />
              <span>{{ t('alarmSettings.rules.enabled') }}</span>
            </label>
          </div>
          <p v-if="!form.rules.length" class="muted">{{ t('alarmSettings.rules.none') }}</p>
          <fieldset v-for="(r, i) in form.rules" :key="r.uid" class="alm-rule">
            <legend>{{ ruleLabel(r, i) }}</legend>
            <div class="alm-rule-head">
              <fieldset class="alm-kind">
                <legend class="visually-hidden">{{ t('alarmSettings.rules.kind') }}</legend>
                <label>
                  <input type="radio" :name="id(r.uid + '-kind')" value="STATIC" :checked="r.kind === 'STATIC'" :disabled="disabled" @change="setKind(r, 'STATIC')" />
                  {{ t('alarmSettings.rules.static') }}
                </label>
                <label>
                  <input type="radio" :name="id(r.uid + '-kind')" value="DYNAMIC" :checked="r.kind === 'DYNAMIC'" :disabled="disabled" @change="setKind(r, 'DYNAMIC')" />
                  {{ t('alarmSettings.rules.dynamic') }}
                </label>
                <label>
                  <input type="radio" :name="id(r.uid + '-kind')" value="NO_DATA" :checked="r.kind === 'NO_DATA'" :disabled="disabled" @change="setKind(r, 'NO_DATA')" />
                  {{ t('alarmSettings.rules.noDataKind') }}
                </label>
              </fieldset>
              <button
                v-if="writable"
                type="button"
                class="edit-btn is-small is-danger alm-rule-remove"
                :disabled="busy"
                :aria-label="t('alarmSettings.rules.removeLabel', { name: ruleLabel(r, i) })"
                @click="removeRule(r)"
              >
                {{ t('alarmSettings.rules.remove') }}
              </button>
            </div>
            <div v-if="r.kind === 'NO_DATA'" class="iso-edit-grid alm-nodata">
              <div class="edit-field">
                <label :for="id(r.uid + '-nodata')">{{ t('alarmSettings.rules.noData') }}<span class="iso-req" aria-hidden="true"> *</span></label>
                <div class="alm-nodata-input">
                  <input
                    :id="id(r.uid + '-nodata')"
                    :value="r.noData"
                    type="text"
                    inputmode="numeric"
                    autocomplete="off"
                    aria-required="true"
                    :disabled="disabled"
                    :aria-invalid="problemText(r.uid + '.noData') ? 'true' : undefined"
                    :aria-describedby="describedBy(r.uid + '.noData', id(r.uid + '-nodata-hint'))"
                    @input="updateRule(r, { noData: ($event.target as HTMLInputElement).value })"
                  />
                  <select :aria-label="t('alarmSettings.rules.noDataUnit')" :value="r.noDataUnit" :disabled="disabled" @change="updateRule(r, { noDataUnit: ($event.target as HTMLSelectElement).value as 'MINUTES' | 'HOURS' })">
                    <option value="MINUTES">{{ t('alarmSettings.rules.minutes') }}</option>
                    <option value="HOURS">{{ t('alarmSettings.rules.hours') }}</option>
                  </select>
                </div>
                <p :id="id(r.uid + '-nodata-hint')" class="edit-hint">{{ t('alarmSettings.rules.noDataHint') }}</p>
                <p v-if="problemText(r.uid + '.noData')" :id="id(r.uid + '.noData-err')" class="iso-field-error">{{ problemText(r.uid + '.noData') }}</p>
              </div>
            </div>
            <div v-else class="iso-edit-grid">
              <div class="edit-field">
                <label :for="id(r.uid + '-op')">{{ t('alarmSettings.rules.operator') }}</label>
                <select
                  :id="id(r.uid + '-op')"
                  :value="r.operator"
                  :disabled="disabled"
                  :aria-invalid="problemText(r.uid + '.operator') ? 'true' : undefined"
                  :aria-describedby="describedBy(r.uid + '.operator')"
                  @change="updateRule(r, { operator: ($event.target as HTMLSelectElement).value })"
                >
                  <option v-if="!r.operator" value="" disabled>{{ t('registerEdit.choose') }}</option>
                  <option v-for="o in OPERATORS" :key="o" :value="o">{{ operatorName(o) }}</option>
                  <option v-if="extraOperator(r)" :value="extraOperator(r)!">{{ extraOperator(r) }}</option>
                </select>
                <p v-if="problemText(r.uid + '.operator')" :id="id(r.uid + '.operator-err')" class="iso-field-error">{{ problemText(r.uid + '.operator') }}</p>
              </div>
              <div v-if="r.kind === 'STATIC'" class="edit-field">
                <label :for="id(r.uid + '-limit')">{{ t('alarmSettings.rules.limit') }}<template v-if="unit"> [{{ unit }}]</template><span class="iso-req" aria-hidden="true"> *</span></label>
                <input
                  :id="id(r.uid + '-limit')"
                  :value="r.limit"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  aria-required="true"
                  :disabled="disabled"
                  :aria-invalid="problemText(r.uid + '.limit') ? 'true' : undefined"
                  :aria-describedby="describedBy(r.uid + '.limit')"
                  @input="updateRule(r, { limit: ($event.target as HTMLInputElement).value })"
                />
                <p v-if="problemText(r.uid + '.limit')" :id="id(r.uid + '.limit-err')" class="iso-field-error">{{ problemText(r.uid + '.limit') }}</p>
              </div>
              <div v-else class="edit-field">
                <span class="reg-edit-label" :id="id(r.uid + '-ld-label')">{{ t('alarmSettings.rules.limitData') }}<span class="iso-req" aria-hidden="true"> *</span></span>
                <div class="alm-picked">
                  <span :class="{ muted: !r.limitData }">{{ limitDataText(r) }}</span>
                  <button
                    v-if="writable"
                    :id="id(r.uid + '-limit')"
                    type="button"
                    class="edit-btn is-small alm-pick-row"
                    :disabled="busy"
                    :aria-describedby="[id(r.uid + '-ld-label'), problemText(r.uid + '.limitData') ? id(r.uid + '.limitData-err') : ''].filter(Boolean).join(' ')"
                    :aria-invalid="problemText(r.uid + '.limitData') ? 'true' : undefined"
                    @click="picking = r"
                  >
                    {{ r.limitData ? t('alarmSettings.rules.changeRow') : t('alarmSettings.rules.chooseRow') }}
                  </button>
                </div>
                <p v-if="problemText(r.uid + '.limitData')" :id="id(r.uid + '.limitData-err')" class="iso-field-error">{{ problemText(r.uid + '.limitData') }}</p>
              </div>
              <div class="edit-field">
                <label :for="id(r.uid + '-tol')">{{ t('alarmSettings.rules.tolerance') }}</label>
                <input
                  :id="id(r.uid + '-tol')"
                  :value="r.tolerance"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  :disabled="disabled"
                  :aria-invalid="problemText(r.uid + '.tolerance') ? 'true' : undefined"
                  :aria-describedby="describedBy(r.uid + '.tolerance', id(r.uid + '-tol-hint'))"
                  @input="updateRule(r, { tolerance: ($event.target as HTMLInputElement).value })"
                />
                <p :id="id(r.uid + '-tol-hint')" class="edit-hint">{{ t('alarmSettings.rules.toleranceHint') }}</p>
                <p v-if="problemText(r.uid + '.tolerance')" :id="id(r.uid + '.tolerance-err')" class="iso-field-error">{{ problemText(r.uid + '.tolerance') }}</p>
              </div>
              <div class="edit-field">
                <label :for="id(r.uid + '-avg')">{{ t('alarmSettings.rules.average') }}</label>
                <input
                  :id="id(r.uid + '-avg')"
                  :value="r.average"
                  type="text"
                  inputmode="numeric"
                  autocomplete="off"
                  :placeholder="t('alarmSettings.rules.averageNone')"
                  :disabled="disabled"
                  :aria-invalid="problemText(r.uid + '.average') ? 'true' : undefined"
                  :aria-describedby="describedBy(r.uid + '.average', id(r.uid + '-avg-hint'))"
                  @input="updateRule(r, { average: ($event.target as HTMLInputElement).value })"
                />
                <p :id="id(r.uid + '-avg-hint')" class="edit-hint">{{ t('alarmSettings.rules.averageHint') }}</p>
                <p v-if="problemText(r.uid + '.average')" :id="id(r.uid + '.average-err')" class="iso-field-error">{{ problemText(r.uid + '.average') }}</p>
              </div>
            </div>
            <AlarmScheduleField
              v-if="r.kind !== 'NO_DATA'"
              :model-value="r.valid"
              :label="t('alarmSettings.rules.valid')"
              :hint="t('alarmSettings.rules.validHint')"
              :id-base="id(r.uid + '-valid')"
              :disabled="disabled"
              :error="problemText(r.uid + '.valid')"
              @update:model-value="scheduleUpdate(r, 'valid')($event)"
            />
            <AlarmScheduleField
              :model-value="r.silent"
              :label="t('alarmSettings.rules.silent')"
              :hint="t('alarmSettings.rules.silentHint')"
              :id-base="id(r.uid + '-silent')"
              :disabled="disabled"
              :error="problemText(r.uid + '.silent')"
              @update:model-value="scheduleUpdate(r, 'silent')($event)"
            />
            <AlarmScheduleField
              :model-value="r.standby"
              :label="t('alarmSettings.rules.standby')"
              :hint="t('alarmSettings.rules.standbyHint')"
              :id-base="id(r.uid + '-standby')"
              :disabled="disabled"
              :error="problemText(r.uid + '.standby')"
              @update:model-value="scheduleUpdate(r, 'standby')($event)"
            />
          </fieldset>
          <p v-if="problemText('rules')" class="iso-field-error">{{ problemText('rules') }}</p>
          <div v-if="writable" class="edit-actions">
            <button type="button" class="edit-btn alm-rule-add" :disabled="busy || form.rules.length >= RULES_MAX" @click="addRule">{{ t('alarmSettings.rules.add') }}</button>
            <button type="button" class="edit-btn alm-outage-add" :disabled="busy || form.rules.length >= RULES_MAX" @click="addOutageRule">{{ t('alarmSettings.rules.addOutage') }}</button>
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
          <p>{{ t('alarmSettings.conflict') }}</p>
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
      <span v-if="writable && loadState === 'ready'" class="reg-edit-state edit-left" :class="{ 'is-dirty': formDirty }" role="status">{{ stateText }}</span>
      <button type="button" class="edit-btn alm-row-close" :disabled="busy" @click="requestClose">{{ formDirty ? t('edit.cancel') : t('edit.close') }}</button>
      <button v-if="writable" type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="busy || !!conflict || loadState !== 'ready'">
        {{ busy ? t('edit.saving') : t('edit.save') }}
      </button>
    </template>
  </EditDialog>

  <RowPickerDialog v-if="picking" single :present="[]" :title="t('alarmSettings.rules.pickTitle')" @close="picking = null" @add="picked" />
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
