<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  MESSAGE_MAX,
  SUBJECT_MAX,
  checkConfig,
  cloneConfigForm,
  configChanges,
  configForm,
  configOf,
  conflictConfigOf,
  mergeConfigForm,
  objectKey,
  periodOptions,
  scopeNeedsObjects,
  type ConfigForm,
  type Problem,
} from '../../alarms/settings'
import {
  ALARM_SCOPES,
  createAlarmConfiguration,
  deleteAlarmConfiguration,
  updateAlarmConfiguration,
  type AlarmConfigSettings,
  type AlarmObjectRef,
  type AlarmSettingsTarget,
} from '../../api/alarmSettings'
import type { RoundOptionPerson, RoundPerson } from '../../api/readingRounds'
import type { PickedRow } from '../../analysis/editor'
import { formatDateTime } from '../../analysis/format'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLeaveGuard } from '../../edit/useLeaveGuard'
import { serverMessages } from '../../iso/edit'
import { NAME_MAX } from '../../registers/edit'
import { useAuthStore } from '../../stores/auth'
import RowPickerDialog from '../analysis/edit/RowPickerDialog.vue'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import PeoplePicker from '../edit/PeoplePicker.vue'
import RowAlarmSettingsDialog from './RowAlarmSettingsDialog.vue'
import '../../styles/edit.css'
import '../../styles/isoEdit.css'
import '../../styles/registersEdit.css'
import '../../styles/alarmSettings.css'

/*
 * "Neu" / "Bearbeiten" of an alarm configuration (E7.1), as the Control Center's "Alarm Configuration" with its
 * "E-Mail Notification": side panel (full-width sheet on phones) with name, active, scope (radios with explanation),
 * the rows (E3 row picker, multi-select; each row's own alarm settings can be opened), period, the three options and
 * the notification (people as E8.2, free addresses, cc / bcc collapsed, subject, message). Several notifications: a
 * warning, the notification is not edited. PUT sends only what changed; 409: "Neu laden" / "Weiter bearbeiten".
 */

const props = defineProps<{
  /** the configuration to edit; null: create a new one in one of `targets` */
  config: AlarmConfigSettings | null
  targets?: AlarmSettingsTarget[]
  people?: RoundOptionPerson[]
}>()

const emit = defineEmits<{
  close: []
  /** saved or created: the page reloads the list; the panel stays open */
  saved: [config: AlarmConfigSettings, created: boolean]
  /** changed elsewhere or vanished: reload the list */
  changed: []
  deleted: [name: string]
}>()

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `alm-cfg-${Math.random().toString(36).slice(2, 9)}`
const id = (suffix: string) => `${uid}-${suffix}`

/* ---------- state ---------- */
const model = shallowRef<AlarmConfigSettings | null>(props.config)
const creating = computed(() => model.value == null)
const writable = computed(() => creating.value || model.value?.canWrite === true)
const deletable = computed(() => !creating.value && model.value?.canDelete === true)
const multiple = computed(() => model.value?.multipleNotifications === true)
const notificationEditable = computed(() => writable.value && !multiple.value)

const initial = ref<ConfigForm>(configForm(props.config))
const form = ref<ConfigForm>(cloneConfigForm(initial.value))
const targets = computed(() => props.targets ?? [])
const parent = ref<number | ''>(targets.value.length === 1 ? targets.value[0].id : '')
const people = computed(() => props.people ?? [])
const known = computed(() => ({ users: model.value?.notification?.recipientUsers ?? [], groups: model.value?.notification?.recipientGroups ?? [] }))
const knownPeople = computed<RoundPerson[]>(() => [...known.value.users, ...known.value.groups])

const problems = ref<Record<string, Problem>>({})
const error = ref<string | null>(null)
const messages = ref<string[]>([])
const info = ref<string | null>(null)
const conflict = shallowRef<AlarmConfigSettings | null>(null)
const savedOnce = ref(false)

const changes = computed(() => configChanges(initial.value, form.value))
const formDirty = computed(() => changes.value.size > 0)
const dirty = computed(() => writable.value && formDirty.value)
const { leaveAsk, confirmDrop } = useLeaveGuard(dirty)

async function requestClose() {
  if (busy.value) return
  if (await confirmDrop()) emit('close')
}

function apply(c: AlarmConfigSettings) {
  model.value = c
  initial.value = configForm(c)
  form.value = cloneConfigForm(initial.value)
}

/* ---------- fields ---------- */
const disabled = computed(() => busy.value || !writable.value)
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

const periodName = (p: string) => (te('alarmSettings.period.' + p) ? t('alarmSettings.period.' + p) : p)
const periods = computed(() => periodOptions(initial.value.period))
const targetLabel = (d: AlarmSettingsTarget) => [d.site?.name, d.path, d.name].filter(Boolean).join(' / ')
const objectsShown = computed(() => scopeNeedsObjects(form.value.scope))
const objectName = (o: AlarmObjectRef) => (o.readable ? o.name || '#' + o.id : t('alarms.noAccess'))

/* ---------- rows ---------- */
const picking = ref(false)
const present = computed(() => form.value.objects.map(objectKey))

function addObjects(rows: PickedRow[]) {
  picking.value = false
  const have = new Set(present.value)
  const added = rows
    .map((p) => ({ id: p.node.id, attribute: p.attribute || 'Value', name: p.node.name, path: p.node.path ?? null, readable: true }))
    .filter((o) => !have.has(objectKey(o)))
  if (added.length) form.value.objects = [...form.value.objects, ...added]
  clearProblem('objects')
}

function removeObject(o: AlarmObjectRef) {
  form.value.objects = form.value.objects.filter((x) => objectKey(x) !== objectKey(o))
}

/** the row whose alarm settings are open */
const rowSettings = ref<AlarmObjectRef | null>(null)

/* ---------- save ---------- */
async function focusFirstProblem() {
  await nextTick()
  document.querySelector<HTMLElement>(`#${uid}-form [aria-invalid="true"]`)?.focus()
}

async function save() {
  error.value = null
  messages.value = []
  info.value = null
  const wasNew = creating.value
  if (!wasNew && !changes.value.size) {
    info.value = t('registerEdit.nothingChanged')
    return
  }
  const check = checkConfig(form.value, {
    notificationEditable: notificationEditable.value,
    people: people.value,
    known: known.value,
    changes: wasNew ? null : changes.value,
  })
  const found = { ...check.problems }
  if (wasNew && parent.value === '') found.parent = { key: 'alarmSettings.problem.directory' }
  problems.value = found
  if (Object.keys(found).length || !check.body) {
    if (found.cc || found.bcc) moreOpen.value = true
    await focusFirstProblem()
    return
  }
  const body = check.body
  await run(async () => {
    try {
      const answer = wasNew
        ? await createAlarmConfiguration(auth.http, { ...body, parentId: parent.value as number, name: form.value.name.trim() }, locale.value)
        : await updateAlarmConfiguration(auth.http, model.value!.id, { ...body, version: model.value?.version ?? 'none' }, locale.value)
      const c = configOf(answer, wasNew ? null : model.value!.id)
      const name = c?.name || form.value.name.trim()
      savedOnce.value = true
      if (c) {
        apply(c)
        info.value = t(wasNew ? 'registerEdit.created' : 'registerEdit.saved', { name })
        emit('saved', c, wasNew)
      } else {
        /* no configuration in the answer: the list is reloaded and the panel closed */
        emit('changed')
        emit('close')
      }
    } catch (e) {
      handle(e)
    }
  })
}

function handle(e: unknown) {
  const kind = writeErrorKind(e)
  if (kind === 'conflict' && model.value) {
    emit('changed')
    const c = conflictConfigOf(e, model.value.id)
    if (c) {
      conflict.value = c
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
  const c = conflict.value
  conflict.value = null
  problems.value = {}
  if (c) apply(c)
  info.value = t('registerEdit.reloaded')
}

function keepEditing() {
  const c = conflict.value
  conflict.value = null
  if (!c) return
  const server = configForm(c)
  const merged = mergeConfigForm(initial.value, form.value, server)
  model.value = c
  initial.value = server
  form.value = merged
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
  const c = model.value
  if (!c) return
  await run(async () => {
    try {
      await deleteAlarmConfiguration(auth.http, c.id, c.version ?? 'none')
      deleting.value = false
      emit('deleted', c.name)
    } catch (e) {
      const kind = writeErrorKind(e)
      if (kind === 'conflict' || kind === 'notFound') {
        deleting.value = false
        error.value = kind === 'conflict' ? t('registerEdit.conflictDelete', { name: c.name }) : writeErrorText(e, t)
        emit('changed')
        return
      }
      deleteError.value = writeErrorText(e, t)
    }
  })
}

const lastRun = computed(() => {
  const ms = Date.parse(model.value?.timeStamp ?? '')
  return Number.isNaN(ms) ? '' : formatDateTime(ms, locale.value)
})
const moreOpen = ref(!!(initial.value.notification.cc || initial.value.notification.bcc))
const stateText = computed(() => (formDirty.value ? t('registerEdit.unsaved') : savedOnce.value ? t('alarmSettings.savedState') : t('registerEdit.noChanges')))
const dialogTitle = computed(() => (creating.value ? t('alarmSettings.newTitle') : t('registerEdit.editTitle', { name: model.value?.name ?? '' })))
const mailField = (key: 'recipients' | 'cc' | 'bcc') => ({
  value: form.value.notification[key],
  set: (v: string) => {
    form.value.notification = { ...form.value.notification, [key]: v }
    clearProblem(key)
  },
})
</script>

<template>
  <EditDialog :title="dialogTitle" :busy="busy" wide class="alm-edit-backdrop alm-cfg-backdrop" @close="requestClose">
    <form :id="uid + '-form'" class="alm-cfg-form" novalidate :aria-describedby="messages.length ? id('messages') : undefined" @submit.prevent="save">
      <p v-if="!writable" class="muted read-only">{{ t('alarmSettings.readOnlyConfig') }}</p>

      <div v-if="creating && targets.length > 1" class="edit-field">
        <label :for="id('parent')">{{ t('alarmSettings.directory') }}<span class="iso-req" aria-hidden="true"> *</span></label>
        <select
          :id="id('parent')"
          v-model="parent"
          :disabled="busy"
          aria-required="true"
          :aria-invalid="problemText('parent') ? 'true' : undefined"
          :aria-describedby="describedBy('parent')"
          @change="clearProblem('parent')"
        >
          <option value="" disabled>{{ t('registerEdit.choose') }}</option>
          <option v-for="d in targets" :key="d.id" :value="d.id">{{ targetLabel(d) }}</option>
        </select>
        <p v-if="problemText('parent')" :id="id('parent-err')" class="iso-field-error">{{ problemText('parent') }}</p>
      </div>
      <dl v-else-if="creating && targets.length === 1" class="edit-facts">
        <dt>{{ t('alarmSettings.directory') }}</dt>
        <dd>{{ targetLabel(targets[0]) }}</dd>
      </dl>
      <dl v-else-if="model" class="edit-facts">
        <template v-if="model.site">
          <dt>{{ t('modules.site') }}</dt>
          <dd>{{ model.site.name }}</dd>
        </template>
        <template v-if="model.path">
          <dt>{{ t('alarms.col.path') }}</dt>
          <dd>{{ model.path }}</dd>
        </template>
      </dl>

      <div class="edit-field">
        <label :for="id('name')">{{ t('registers.col.name') }}<span class="iso-req" aria-hidden="true"> *</span></label>
        <input
          :id="id('name')"
          v-model="form.name"
          type="text"
          :maxlength="NAME_MAX + 20"
          autocomplete="off"
          aria-required="true"
          autofocus
          :readonly="!writable"
          :disabled="busy"
          :aria-invalid="problemText('name') ? 'true' : undefined"
          :aria-describedby="describedBy('name')"
          @input="clearProblem('name')"
        />
        <p v-if="problemText('name')" :id="id('name-err')" class="iso-field-error">{{ problemText('name') }}</p>
      </div>

      <div class="edit-field">
        <label class="iso-edit-check">
          <input v-model="form.enabled" type="checkbox" class="alm-enabled" :disabled="disabled" />
          <span>{{ t('alarmSettings.field.enabled') }}</span>
        </label>
      </div>

      <section class="iso-edit-section" :aria-labelledby="id('rows')">
        <h3 :id="id('rows')">{{ t('alarmSettings.section.rows') }}</h3>
        <fieldset class="alm-scope">
          <legend>{{ t('alarmSettings.field.scope') }}</legend>
          <label v-for="s in ALARM_SCOPES" :key="s" class="alm-scope-option">
            <input v-model="form.scope" type="radio" :name="id('scope')" :value="s" :disabled="disabled" @change="clearProblem('objects')" />
            <span>
              <strong>{{ t('alarmSettings.scope.' + s) }}</strong>
              <span>{{ t('alarmSettings.scopeHint.' + s) }}</span>
            </span>
          </label>
        </fieldset>

        <div v-if="objectsShown" class="edit-field alm-objects-field">
          <span class="reg-edit-label" :id="id('objects-label')">
            {{ form.scope === 'SELECTED' ? t('alarmSettings.field.objectsSelected') : t('alarmSettings.field.objectsWithout') }}<span class="iso-req" aria-hidden="true"> *</span>
          </span>
          <ul v-if="form.objects.length" class="alm-objects" :aria-labelledby="id('objects-label')">
            <li v-for="o in form.objects" :key="objectKey(o)">
              <span class="alm-object-name" :class="{ muted: !o.readable }">
                {{ objectName(o) }}<template v-if="o.attribute && o.attribute !== 'Value'"> ({{ o.attribute }})</template>
                <small v-if="o.readable && o.path">{{ o.path }}</small>
              </span>
              <button
                v-if="o.readable"
                type="button"
                class="edit-btn is-small alm-object-settings"
                :disabled="busy"
                :aria-label="t('alarmSettings.rowSettingsLabel', { name: objectName(o) })"
                @click="rowSettings = o"
              >
                {{ t('alarmSettings.rowSettings') }}
              </button>
              <button
                v-if="writable"
                type="button"
                class="edit-btn is-small is-danger alm-object-remove"
                :disabled="busy"
                :aria-label="t('alarmSettings.removeObject', { name: objectName(o) })"
                @click="removeObject(o)"
              >
                ×
              </button>
            </li>
          </ul>
          <p v-else class="muted">{{ t('alarmSettings.noObjects') }}</p>
          <button
            v-if="writable"
            type="button"
            class="edit-btn alm-objects-add"
            :disabled="busy"
            :aria-invalid="problemText('objects') ? 'true' : undefined"
            :aria-describedby="describedBy('objects')"
            @click="picking = true"
          >
            {{ t('alarmSettings.addObjects') }}
          </button>
          <p v-if="problemText('objects')" :id="id('objects-err')" class="iso-field-error">{{ problemText('objects') }}</p>
        </div>
      </section>

      <section class="iso-edit-section" :aria-labelledby="id('check')">
        <h3 :id="id('check')">{{ t('alarmSettings.section.check') }}</h3>
        <div class="edit-field">
          <label :for="id('period')">{{ t('alarmSettings.field.period') }}</label>
          <select :id="id('period')" v-model="form.period" :disabled="disabled" :aria-describedby="id('period-hint')">
            <option v-for="p in periods" :key="p" :value="p">
              {{ periodName(p) }}
            </option>
          </select>
          <p :id="id('period-hint')" class="edit-hint">{{ t('alarmSettings.hint.period') }}</p>
        </div>
        <div class="iso-edit-checks">
          <label class="iso-edit-check">
            <input v-model="form.bindToData" type="checkbox" class="alm-bind" :disabled="disabled" />
            <span>{{ t('alarmSettings.field.bindToData') }}</span>
          </label>
          <label class="iso-edit-check">
            <input v-model="form.includeLimits" type="checkbox" class="alm-limits" :disabled="disabled" />
            <span>{{ t('alarmSettings.field.includeLimits') }}</span>
          </label>
          <label class="iso-edit-check">
            <input v-model="form.disableLink" type="checkbox" class="alm-link" :disabled="disabled" />
            <span>{{ t('alarmSettings.field.disableLink') }}</span>
          </label>
        </div>
        <p v-if="lastRun" class="edit-hint">{{ t('alarmSettings.lastRun', { time: lastRun }) }}</p>
      </section>

      <section class="iso-edit-section alm-notification" :aria-labelledby="id('mail')">
        <h3 :id="id('mail')">{{ t('alarmSettings.section.notification') }}</h3>
        <p v-if="multiple" class="alm-warning alm-multiple" role="note">{{ t('alarmSettings.multipleNotifications') }}</p>
        <template v-else>
          <div class="edit-field">
            <label class="iso-edit-check">
              <input v-model="form.notification.enabled" type="checkbox" class="alm-mail-on" :disabled="!notificationEditable || busy" />
              <span>{{ t('alarmSettings.field.notificationEnabled') }}</span>
            </label>
            <p v-if="!model?.notification && !creating" class="edit-hint">{{ t('alarmSettings.hint.newNotification') }}</p>
          </div>
          <PeoplePicker
            v-model="form.notification.people"
            :people="people"
            :known="knownPeople"
            :input-id="id('people')"
            :label="t('alarmSettings.field.people')"
            :hint="props.people ? undefined : t('alarmSettings.hint.noPeople')"
            :disabled="!notificationEditable || busy"
            no-email-key="alarmSettings.noEmail"
          />
          <div class="edit-field">
            <label :for="id('recipients')">{{ t('alarmSettings.field.recipients') }}</label>
            <textarea
              :id="id('recipients')"
              :value="mailField('recipients').value"
              rows="2"
              autocomplete="off"
              :readonly="!notificationEditable"
              :disabled="busy"
              :aria-invalid="problemText('recipients') ? 'true' : undefined"
              :aria-describedby="describedBy('recipients', id('recipients-hint'))"
              @input="mailField('recipients').set(($event.target as HTMLTextAreaElement).value)"
            />
            <p :id="id('recipients-hint')" class="edit-hint">{{ t('alarmSettings.hint.mails') }}</p>
            <p v-if="problemText('recipients')" :id="id('recipients-err')" class="iso-field-error">{{ problemText('recipients') }}</p>
          </div>
          <details class="alm-more" :open="moreOpen" @toggle="moreOpen = ($event.target as HTMLDetailsElement).open">
            <summary>{{ t('alarmSettings.field.ccBcc') }}</summary>
            <div v-for="k in ['cc', 'bcc'] as const" :key="k" class="edit-field">
              <label :for="id(k)">{{ t('alarmSettings.field.' + k) }}</label>
              <textarea
                :id="id(k)"
                :value="mailField(k).value"
                rows="2"
                autocomplete="off"
                :readonly="!notificationEditable"
                :disabled="busy"
                :aria-invalid="problemText(k) ? 'true' : undefined"
                :aria-describedby="describedBy(k)"
                @input="mailField(k).set(($event.target as HTMLTextAreaElement).value)"
              />
              <p v-if="problemText(k)" :id="id(k + '-err')" class="iso-field-error">{{ problemText(k) }}</p>
            </div>
          </details>
          <div class="edit-field">
            <label :for="id('subject')">{{ t('alarmSettings.field.subject') }}</label>
            <input
              :id="id('subject')"
              v-model="form.notification.subject"
              type="text"
              autocomplete="off"
              :maxlength="SUBJECT_MAX + 20"
              :readonly="!notificationEditable"
              :disabled="busy"
              :aria-invalid="problemText('subject') ? 'true' : undefined"
              :aria-describedby="describedBy('subject')"
              @input="clearProblem('subject')"
            />
            <p v-if="problemText('subject')" :id="id('subject-err')" class="iso-field-error">{{ problemText('subject') }}</p>
          </div>
          <div class="edit-field">
            <label :for="id('message')">{{ t('alarmSettings.field.message') }}</label>
            <textarea
              :id="id('message')"
              v-model="form.notification.message"
              rows="5"
              :maxlength="MESSAGE_MAX + 100"
              :readonly="!notificationEditable"
              :disabled="busy"
              :aria-invalid="problemText('message') ? 'true' : undefined"
              :aria-describedby="describedBy('message', id('message-hint'))"
              @input="clearProblem('message')"
            />
            <p :id="id('message-hint')" class="edit-hint">{{ t('alarmSettings.hint.message') }}</p>
            <p v-if="problemText('message')" :id="id('message-err')" class="iso-field-error">{{ problemText('message') }}</p>
          </div>
        </template>
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

    <template #footer>
      <button v-if="deletable" type="button" class="edit-btn is-danger edit-left alm-cfg-delete" :disabled="busy" @click="askDelete">
        {{ t('registerEdit.delete') }}
      </button>
      <span v-if="writable" class="reg-edit-state" :class="{ 'edit-left': !deletable, 'is-dirty': formDirty }" role="status">{{ stateText }}</span>
      <button type="button" class="edit-btn alm-cfg-close" :disabled="busy" @click="requestClose">{{ formDirty ? t('edit.cancel') : t('edit.close') }}</button>
      <button v-if="writable" type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="busy || !!conflict">
        {{ busy ? t('edit.saving') : creating ? t('registerEdit.create') : t('edit.save') }}
      </button>
    </template>
  </EditDialog>

  <RowPickerDialog v-if="picking" :present="present" :title="t('alarmSettings.pickTitle')" @close="picking = false" @add="addObjects" />
  <RowAlarmSettingsDialog v-if="rowSettings" :row-id="rowSettings.id" :row-name="rowSettings.name" @close="rowSettings = null" />
  <ConfirmDialog
    v-if="deleting"
    :title="t('alarmSettings.deleteTitle')"
    :message="t('registerEdit.deleteQuestion', { name: model?.name ?? '' })"
    :confirm-label="t('registerEdit.delete')"
    :busy="busy"
    :error="deleteError"
    danger
    @confirm="confirmDelete"
    @cancel="!busy && (deleting = false)"
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
