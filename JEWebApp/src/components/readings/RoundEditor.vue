<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { matchedRouteKey, onBeforeRouteLeave } from 'vue-router'
import {
  ROUND_LIMITS,
  ROUND_PERIODS,
  conflictRound,
  createRound,
  updateRound,
  type ReadingRound,
  type RoundOptions,
} from '../../api/readingRounds'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { serverMessages } from '../../iso/edit'
import {
  changedRoundFields,
  cloneRoundForm,
  mergeRoundForm,
  newRoundForm,
  roundBody,
  roundForm,
  validateRoundForm,
  type RoundField,
  type RoundForm,
  type RoundProblems,
} from '../../readings/rounds'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import RoundMeterPicker from './RoundMeterPicker.vue'
import RoundPeoplePicker from './RoundPeoplePicker.vue'
import '../../styles/edit.css'
import '../../styles/isoEdit.css'
import '../../styles/readings.css'

/*
 * Editor of a reading round (E8.2), side panel on wide screens and full-width sheet on phones: name, site (new
 * rounds), active, start date, period, lead / reminder / escalation days, readers and supervisor, meters in reading
 * order, remarks. The server's rules are checked before sending; a 400 shows the server's messages, a 409 offers the
 * current round ("Neu laden") or keeps the own changes on top of it ("Weiter bearbeiten"). Closing or leaving the
 * page with unsaved changes asks first.
 */

const props = defineProps<{
  /** null: new round */
  round: ReadingRound | null
  sites: readonly { id: number; name: string }[]
  /** preselected site of a new round */
  site?: number | null
  /** all rounds (names must be unique per site) */
  rounds: readonly ReadingRound[]
  today: string
  /** options of a site (cached by the list) */
  loadOptions: (site: number) => Promise<RoundOptions>
}>()
const emit = defineEmits<{ close: []; saved: [round: ReadingRound | null, created: boolean, name: string]; changed: [] }>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `rd-round-${Math.random().toString(36).slice(2, 9)}`
const id = (suffix: string) => `${uid}-${suffix}`

const creating = computed(() => !props.round)
/* the round as loaded (replaced after a 409) */
const current = shallowRef<ReadingRound | null>(props.round)

const startSite = props.round?.site?.id ?? props.site ?? (props.sites.length === 1 ? props.sites[0].id : null)
const initial = ref<RoundForm>(props.round ? roundForm(props.round) : newRoundForm(props.today, startSite))
const form = ref<RoundForm>(cloneRoundForm(initial.value))

const problems = ref<RoundProblems>({})
const messages = ref<string[]>([])
const error = ref<string | null>(null)
const info = ref<string | null>(null)
const conflict = shallowRef<ReadingRound | null>(null)

const dirty = computed(() => changedRoundFields(initial.value, form.value).length > 0)
const title = computed(() => (props.round ? t('readings.rounds.editTitle', { name: props.round.name }) : t('readings.rounds.newTitle')))

/* ---- options of the site ---- */
const options = shallowRef<RoundOptions | null>(null)
const optionsState = ref<'none' | 'loading' | 'ready' | 'error'>('none')
let optionsSeq = 0

async function loadOptions(site: number | null) {
  const seq = ++optionsSeq
  options.value = null
  if (site == null) {
    optionsState.value = 'none'
    return
  }
  optionsState.value = 'loading'
  try {
    const o = await props.loadOptions(site)
    if (seq !== optionsSeq) return
    options.value = o
    optionsState.value = 'ready'
  } catch {
    if (seq === optionsSeq) optionsState.value = 'error'
  }
}

watch(
  () => form.value.site,
  (site, before) => {
    /* another site: its meters differ, the chosen ones are dropped */
    if (before !== undefined && site !== before) form.value.meters = []
    void loadOptions(site)
  },
  { immediate: true },
)

const noCreateRight = computed(() => creating.value && optionsState.value === 'ready' && !options.value?.canCreate)
const meterIds = computed(() => (options.value ? new Set(options.value.meters.map((m) => m.id)) : null))
const peopleIds = computed(() => {
  if (!options.value) return null
  /* people of the round the user may not read stay allowed (the server keeps them) */
  const ids = new Set(options.value.people.map((p) => p.id))
  for (const p of [...(current.value?.readers ?? []), ...(current.value?.supervisor ?? [])]) ids.add(p.id)
  return ids
})
const otherNames = computed(() =>
  props.rounds.filter((r) => r.id !== current.value?.id && r.site?.id != null && r.site.id === form.value.site).map((r) => r.name),
)

/* ---- problems ---- */
function problemText(field: RoundField): string | null {
  const p = problems.value[field]
  return p ? t('readings.rounds.problem.' + p.key, p.params ?? {}) : null
}

function touched(field: RoundField) {
  info.value = null
  if (problems.value[field]) {
    const next = { ...problems.value }
    delete next[field]
    problems.value = next
  }
}

watch(
  () => form.value.readers,
  () => touched('readers'),
)
watch(
  () => form.value.supervisor,
  () => touched('supervisor'),
)
watch(
  () => form.value.meters,
  () => touched('meters'),
)

const ORDER: readonly RoundField[] = ['name', 'site', 'startDate', 'period', 'leadDays', 'reminderDays', 'escalationDays', 'readers', 'supervisor', 'meters', 'remarks']
const FOCUS: Partial<Record<RoundField, string>> = { meters: 'meters-search' }

async function focusFirstProblem() {
  await nextTick()
  const first = ORDER.find((f) => problems.value[f])
  if (first) document.getElementById(id(FOCUS[first] ?? first))?.focus()
}

/* ---- save ---- */
async function save() {
  error.value = null
  messages.value = []
  info.value = null
  if (conflict.value || noCreateRight.value) return
  problems.value = validateRoundForm(form.value, {
    otherNames: otherNames.value,
    meterIds: meterIds.value,
    peopleIds: peopleIds.value,
    creating: creating.value,
    locale: locale.value,
  })
  if (Object.keys(problems.value).length) {
    await focusFirstProblem()
    return
  }
  if (!creating.value && !dirty.value) {
    info.value = t('registerEdit.nothingChanged')
    return
  }
  const body = roundBody(form.value)
  await run(async () => {
    try {
      const answer = current.value
        ? await updateRound(auth.http, current.value.id, { ...body, version: current.value.version }, locale.value)
        : await createRound(auth.http, { ...body, site: form.value.site! }, locale.value)
      const round = answer && typeof answer === 'object' && typeof answer.id === 'number' ? answer : null
      initial.value = cloneRoundForm(form.value)
      emit('saved', round, creating.value, round?.name || body.name)
    } catch (e) {
      handle(e)
    }
  })
}

function handle(e: unknown) {
  const kind = writeErrorKind(e)
  if (kind === 'conflict' && current.value) {
    const round = conflictRound(e)
    emit('changed')
    if (round) {
      conflict.value = round
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
  const round = conflict.value
  if (round) {
    current.value = round
    initial.value = roundForm(round)
    form.value = cloneRoundForm(initial.value)
    problems.value = {}
  }
  conflict.value = null
  info.value = t('registerEdit.reloaded')
}

function keepEditing() {
  const round = conflict.value
  if (round) {
    const server = roundForm(round)
    form.value = mergeRoundForm(initial.value, form.value, server)
    initial.value = server
    current.value = round
  }
  conflict.value = null
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
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

/* ---- texts ---- */
const describedBy = (field: RoundField, hint?: string) => [problems.value[field] ? id(field) + '-err' : '', hint ? id(field) + '-hint' : ''].filter(Boolean).join(' ') || undefined
const canSubmit = computed(() => !busy.value && !conflict.value && !noCreateRight.value && (optionsState.value === 'ready' || !creating.value))
const submitLabel = computed(() => (busy.value ? t('edit.saving') : creating.value ? t('registerEdit.create') : t('edit.save')))
const siteName = computed(() => current.value?.site?.name ?? props.sites.find((s) => s.id === form.value.site)?.name ?? '')
</script>

<template>
  <EditDialog :title="title" :busy="busy" wide class="rd-panel-backdrop rd-round-backdrop" @close="requestClose">
    <form :id="id('form')" class="rd-round-form" novalidate :aria-describedby="messages.length ? id('messages') : undefined" @submit.prevent="save">
      <section class="iso-edit-section">
        <h3>{{ t('readings.rounds.section.general') }}</h3>
        <div class="iso-edit-grid">
          <div class="edit-field iso-edit-wide">
            <label :for="id('name')">{{ t('readings.rounds.field.name') }} <span class="iso-req" aria-hidden="true">*</span></label>
            <input
              :id="id('name')"
              v-model="form.name"
              type="text"
              autocomplete="off"
              required
              autofocus
              :maxlength="ROUND_LIMITS.name + 20"
              :disabled="busy"
              :aria-invalid="problems.name ? 'true' : undefined"
              :aria-describedby="describedBy('name')"
              @input="touched('name')"
            />
            <p v-if="problems.name" :id="id('name') + '-err'" class="iso-field-error">{{ problemText('name') }}</p>
          </div>

          <div v-if="creating" class="edit-field">
            <label :for="id('site')">{{ t('readings.rounds.field.site') }} <span class="iso-req" aria-hidden="true">*</span></label>
            <select
              :id="id('site')"
              v-model="form.site"
              :disabled="busy"
              :aria-invalid="problems.site ? 'true' : undefined"
              :aria-describedby="describedBy('site')"
              @change="touched('site')"
            >
              <option :value="null" disabled>{{ t('registerEdit.choose') }}</option>
              <option v-for="s in sites" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
            <p v-if="problems.site" :id="id('site') + '-err'" class="iso-field-error">{{ problemText('site') }}</p>
          </div>
          <div v-else class="edit-field">
            <span class="rd-field-label">{{ t('readings.rounds.field.site') }}</span>
            <p class="rd-field-static">{{ siteName }}</p>
          </div>

          <div class="edit-field iso-edit-bool">
            <label class="iso-edit-check rd-enabled">
              <input v-model="form.enabled" type="checkbox" :disabled="busy" />
              <span>{{ t('readings.rounds.field.enabled') }}</span>
            </label>
          </div>

          <div class="edit-field">
            <label :for="id('startDate')">{{ t('readings.rounds.field.startDate') }} <span class="iso-req" aria-hidden="true">*</span></label>
            <input
              :id="id('startDate')"
              v-model="form.startDate"
              type="date"
              required
              :disabled="busy"
              :aria-invalid="problems.startDate ? 'true' : undefined"
              :aria-describedby="describedBy('startDate', 'hint')"
              @input="touched('startDate')"
            />
            <p :id="id('startDate') + '-hint'" class="edit-hint">{{ t('readings.rounds.hint.startDate') }}</p>
            <p v-if="problems.startDate" :id="id('startDate') + '-err'" class="iso-field-error">{{ problemText('startDate') }}</p>
          </div>

          <div class="edit-field">
            <label :for="id('period')">{{ t('readings.rounds.field.period') }} <span class="iso-req" aria-hidden="true">*</span></label>
            <select
              :id="id('period')"
              v-model="form.period"
              :disabled="busy"
              :aria-invalid="problems.period ? 'true' : undefined"
              :aria-describedby="describedBy('period')"
              @change="touched('period'); touched('leadDays')"
            >
              <option v-if="!ROUND_PERIODS.includes(form.period as never)" :value="form.period" disabled>{{ form.period || t('registerEdit.choose') }}</option>
              <option v-for="p in ROUND_PERIODS" :key="p" :value="p">{{ t('readings.rounds.period.' + p) }}</option>
            </select>
            <p v-if="problems.period" :id="id('period') + '-err'" class="iso-field-error">{{ problemText('period') }}</p>
          </div>

          <div v-for="f in (['leadDays', 'reminderDays', 'escalationDays'] as const)" :key="f" class="edit-field">
            <label :for="id(f)">{{ t('readings.rounds.field.' + f) }}</label>
            <input
              :id="id(f)"
              v-model="form[f]"
              type="text"
              inputmode="numeric"
              autocomplete="off"
              :disabled="busy"
              :aria-invalid="problems[f] ? 'true' : undefined"
              :aria-describedby="describedBy(f, 'hint')"
              @input="touched(f)"
            />
            <p :id="id(f) + '-hint'" class="edit-hint">{{ t('readings.rounds.hint.' + f) }}</p>
            <p v-if="problems[f]" :id="id(f) + '-err'" class="iso-field-error">{{ problemText(f) }}</p>
          </div>
        </div>
      </section>

      <section class="iso-edit-section">
        <h3>{{ t('readings.rounds.section.people') }}</h3>
        <p v-if="optionsState === 'error'" class="alert alert-error" role="alert">{{ t('readings.rounds.optionsError') }}</p>
        <RoundPeoplePicker
          v-model="form.readers"
          :people="options?.people ?? []"
          :known="current?.readers"
          :input-id="id('readers')"
          :label="t('readings.rounds.field.readers') + ' *'"
          :hint="t('readings.rounds.hint.readers')"
          :error="problemText('readers')"
          :disabled="busy || optionsState !== 'ready'"
        />
        <RoundPeoplePicker
          v-model="form.supervisor"
          :people="options?.people ?? []"
          :known="current?.supervisor"
          :input-id="id('supervisor')"
          :label="t('readings.rounds.field.supervisor')"
          :hint="t('readings.rounds.hint.supervisor')"
          :error="problemText('supervisor')"
          :disabled="busy || optionsState !== 'ready'"
        />
      </section>

      <section class="iso-edit-section" :aria-labelledby="id('meters-title')">
        <h3 :id="id('meters-title')">
          {{ t('readings.rounds.section.meters') }} <span class="rd-count-inline">{{ t('readings.rounds.meters.count', form.meters.length) }}</span>
        </h3>
        <p class="edit-hint rd-meters-hint">{{ t('readings.rounds.hint.meters') }}</p>
        <p v-if="form.site == null" class="muted">{{ t('readings.rounds.meters.chooseSite') }}</p>
        <RoundMeterPicker
          v-else
          v-model="form.meters"
          :meters="options?.meters ?? []"
          :known="current?.meters"
          :id-prefix="id('meters')"
          :loading="optionsState === 'loading'"
          :error="problemText('meters')"
          :disabled="busy || optionsState !== 'ready'"
        />
      </section>

      <section class="iso-edit-section">
        <div class="edit-field">
          <label :for="id('remarks')">{{ t('readings.rounds.field.remarks') }}</label>
          <textarea
            :id="id('remarks')"
            v-model="form.remarks"
            rows="3"
            :maxlength="ROUND_LIMITS.remarks + 50"
            :disabled="busy"
            :aria-invalid="problems.remarks ? 'true' : undefined"
            :aria-describedby="describedBy('remarks', 'hint')"
            @input="touched('remarks')"
          ></textarea>
          <p :id="id('remarks') + '-hint'" class="edit-hint">{{ t('readings.rounds.hint.remarks') }}</p>
          <p v-if="problems.remarks" :id="id('remarks') + '-err'" class="iso-field-error">{{ problemText('remarks') }}</p>
        </div>
      </section>

      <p v-if="noCreateRight" class="alert alert-error rd-no-create" role="alert">{{ t('readings.rounds.noCreateRight') }}</p>
      <div v-if="messages.length" :id="id('messages')" class="alert alert-error iso-messages" role="alert">
        <strong>{{ t('registerEdit.invalidTitle') }}</strong>
        <ul class="edit-warnings">
          <li v-for="(m, i) in messages" :key="i">{{ m }}</li>
        </ul>
      </div>
      <div v-if="conflict" class="alert alert-error reg-conflict rd-conflict" role="alert">
        <strong>{{ t('registerEdit.conflict.title') }}</strong>
        <p>{{ t('readings.rounds.conflict') }}</p>
        <div class="edit-actions">
          <button type="button" class="edit-btn rd-conflict-reload" @click="reloadFromConflict">{{ t('registerEdit.conflict.reload') }}</button>
          <button type="button" class="edit-btn rd-conflict-keep" @click="keepEditing">{{ t('registerEdit.conflict.keep') }}</button>
        </div>
      </div>
      <p v-if="info" class="muted iso-edit-info" role="status">{{ info }}</p>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>

    <template #footer>
      <span class="edit-left rd-state" :class="{ 'is-dirty': dirty }" role="status">
        {{ dirty ? t('registerEdit.unsaved') : creating ? '' : t('registerEdit.noChanges') }}
      </span>
      <button type="button" class="edit-btn" :disabled="busy" @click="requestClose">{{ t('edit.cancel') }}</button>
      <button type="submit" class="btn btn-primary edit-submit rd-round-save" :form="id('form')" :disabled="!canSubmit">{{ submitLabel }}</button>
    </template>
  </EditDialog>

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
