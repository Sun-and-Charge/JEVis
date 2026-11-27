<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  createUser,
  resetUserMfa,
  setUserGroups,
  setUserPassword,
  updateUser,
  type AdminGroup,
  type AdminScope,
  type AdminUser,
  type Membership,
  type Right,
} from '../../api/admin'
import {
  adminErrorKey,
  conflictState,
  emptyUserForm,
  errorCode,
  isEmptyDiff,
  membershipDiff,
  membershipsToSend,
  newPasswordProblem,
  RIGHTS,
  refName,
  sameUserForm,
  sortByName,
  userDirectories,
  userFormOf,
  userFormProblems,
  userName,
  type FormProblem,
  type UserForm,
} from '../../admin/admin'
import { serverReason, useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLeaveGuard } from '../../edit/useLeaveGuard'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import RightsCheckboxes from './RightsCheckboxes.vue'
import '../../styles/isoEdit.css'
import '../../styles/admin.css'

/*
 * User in the side panel (E10.1): "Neuer Benutzer" (directory, account, names, e-mail, enabled, initial password)
 * or change (data, enabled, Sys Admin – only Sys Admins, never for oneself), "Passwort setzen", "2FA zurücksetzen"
 * (Sys Admins) and the group memberships with their rights. Memberships in groups the caller may not write are
 * shown read-only; the server keeps them. A 409 takes the current state from the answer; passwords never leave the
 * form and are cleared after each submit.
 */

const props = defineProps<{
  /** null: create */
  user: AdminUser | null
  scope: AdminScope
  /** the readable groups (memberships can be added to the writable ones) */
  groups: readonly AdminGroup[]
  /** directory preselected for a new user (site filter of the list) */
  directory?: number | null
}>()

const emit = defineEmits<{
  close: []
  saved: [user: AdminUser, created: boolean]
  /** reload the list (conflict, vanished) */
  changed: []
}>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `adm-user-${Math.random().toString(36).slice(2, 9)}`
const id = (s: string) => `${uid}-${s}`

const directories = computed(() => userDirectories(props.scope))
const startDirectory =
  props.directory != null && directories.value.some((d) => d.directory === props.directory)
    ? props.directory
    : directories.value.length === 1
      ? directories.value[0].directory
      : null

const current = shallowRef<AdminUser | null>(props.user)
const initial = ref<UserForm>(current.value ? userFormOf(current.value) : emptyUserForm(startDirectory))
const form = ref<UserForm>({ ...initial.value })
const initialGroups = ref<Membership[]>(membershipsOf(current.value))
const memberships = ref<Membership[]>(membershipsOf(current.value))

const isNew = computed(() => current.value == null)
const isSelf = computed(() => current.value != null && current.value.id === auth.user?.id)
const canWrite = computed(() => isNew.value || current.value?.canWrite === true)
const canSysAdmin = computed(() => props.scope.sysAdmin && !isNew.value && !isSelf.value && canWrite.value)
const dataDirty = computed(() => canWrite.value && !sameUserForm(initial.value, form.value))
const groupsDirty = computed(() => !isNew.value && !isEmptyDiff(membershipDiff(initialGroups.value, memberships.value)))
const dirty = computed(() => dataDirty.value || groupsDirty.value || (isNew.value && (form.value.password !== '' || form.value.account.trim() !== '')))
const title = computed(() => (isNew.value ? t('admin.users.createTitle') : t('admin.users.editTitle', { name: userName(current.value!) })))

const groupsById = computed(() => new Map(props.groups.map((g) => [g.id, g])))
const groupWritable = (gid: number) => groupsById.value.get(gid)?.canWrite === true
const groupLabel = (gid: number) => {
  const g = groupsById.value.get(gid)
  const name = g?.name ?? current.value?.groups.find((x) => x.id === gid)?.name ?? String(gid)
  const site = g ? refName(g.site) : refName(current.value?.groups.find((x) => x.id === gid)?.site ?? null)
  return { name, site }
}
const addable = computed(() =>
  sortByName(
    props.groups.filter((g) => g.canWrite && !memberships.value.some((m) => m.id === g.id)),
    (g) => g.name,
    locale.value,
  ),
)
const addChoice = ref<number | null>(null)

const problems = ref<FormProblem[]>([])
const error = ref<string | null>(null)
const info = ref<string | null>(null)

const pwOpen = ref(false)
const pw = ref({ password: '', repeat: '' })
const pwProblem = ref<string | null>(null)
const pwInfo = ref<string | null>(null)
const mfaAsk = ref(false)
const mfaError = ref<string | null>(null)

const { leaveAsk, confirmDrop } = useLeaveGuard(dirty)

function membershipsOf(u: AdminUser | null): Membership[] {
  return (u?.groups ?? []).map((g) => ({ id: g.id, rights: [...(g.rights ?? [])] }))
}

const problemText = (field: string) => {
  const p = problems.value.find((x) => x.field === field)
  return p ? t(p.key) : null
}

function clear(field: string) {
  problems.value = problems.value.filter((p) => p.field !== field)
  info.value = null
}

function take(u: AdminUser) {
  current.value = u
  initial.value = userFormOf(u)
  form.value = { ...initial.value }
  initialGroups.value = membershipsOf(u)
  memberships.value = membershipsOf(u)
}

async function requestClose() {
  if (busy.value) return
  if (await confirmDrop()) emit('close')
}

function setRights(gid: number, rights: Right[]) {
  memberships.value = memberships.value.map((m) => (m.id === gid ? { id: gid, rights } : m))
}

function addGroup() {
  if (addChoice.value == null) return
  memberships.value = [...memberships.value, { id: addChoice.value, rights: ['READ'] }]
  addChoice.value = null
}

function removeGroup(gid: number) {
  memberships.value = memberships.value.filter((m) => m.id !== gid)
}

/** Messages of a failed write; a conflict takes the current state of the answer. */
function failed(e: unknown) {
  const key = adminErrorKey(e)
  const code = errorCode(e)
  if (code === 'ACCOUNT_EXISTS') {
    problems.value = [{ field: 'account', key: 'admin.error.ACCOUNT_EXISTS' }]
    return
  }
  if (writeErrorKind(e) === 'conflict') {
    const fresh = conflictState<AdminUser>(e)
    if (fresh) take(fresh)
    emit('changed')
    error.value = key ? t(key) : t('admin.error.conflict')
    return
  }
  if (writeErrorKind(e) === 'notFound') emit('changed')
  if (key) {
    const reason = serverReason(e)
    /* a plain-text answer may explain more than the code ("GRANT: …") */
    error.value = reason && code && reason.length > code.length + 2 ? `${t(key)} (${reason})` : t(key)
    return
  }
  error.value = writeErrorText(e, t)
}

async function save() {
  error.value = null
  info.value = null
  problems.value = userFormProblems(form.value, isNew.value)
  if (problems.value.length) {
    if (isNew.value) form.value = { ...form.value, password: '', repeat: '' }
    return
  }
  const f = form.value
  await run(async () => {
    try {
      if (isNew.value) {
        const created = await createUser(auth.http, {
          directory: f.directory!,
          account: f.account.trim(),
          firstName: f.firstName.trim(),
          lastName: f.lastName.trim(),
          email: f.email.trim(),
          enabled: f.enabled,
          password: f.password,
        })
        take(created)
        info.value = t('admin.users.created', { name: created.account })
        emit('saved', created, true)
        return
      }
      let user = current.value!
      if (dataDirty.value) {
        user = await updateUser(auth.http, user.id, {
          version: user.version,
          firstName: f.firstName.trim(),
          lastName: f.lastName.trim(),
          email: f.email.trim(),
          enabled: f.enabled,
          ...(canSysAdmin.value ? { sysAdmin: f.sysAdmin } : {}),
        })
        const keepGroups = memberships.value
        take(user)
        memberships.value = keepGroups
      }
      if (groupsDirty.value) {
        const result = await setUserGroups(auth.http, user.id, user.version, membershipsToSend(memberships.value))
        if (result && typeof result.id === 'number' && Array.isArray(result.groups)) {
          user = { ...user, ...result } as AdminUser
          take(user)
        } else {
          initialGroups.value = memberships.value.map((m) => ({ ...m }))
        }
        if (result?.unchanged?.length) info.value = t('admin.users.unchanged', { names: result.unchanged.map((g) => refName(g)).join(', ') })
      }
      if (!info.value) info.value = t('admin.users.saved', { name: userName(user) })
      emit('saved', user, false)
    } catch (e) {
      failed(e)
    } finally {
      if (isNew.value) form.value = { ...form.value, password: '', repeat: '' }
    }
  })
}

async function savePassword() {
  pwInfo.value = null
  error.value = null
  pwProblem.value = newPasswordProblem(pw.value.password, pw.value.repeat)
  if (pwProblem.value || !current.value) {
    pw.value = { password: '', repeat: '' }
    return
  }
  const password = pw.value.password
  pw.value = { password: '', repeat: '' }
  await run(async () => {
    try {
      await setUserPassword(auth.http, current.value!.id, password)
      pwOpen.value = false
      pwInfo.value = t('admin.users.passwordSet')
    } catch (e) {
      failed(e)
    }
  })
}

async function resetMfa() {
  if (!current.value) return
  mfaError.value = null
  await run(async () => {
    try {
      await resetUserMfa(auth.http, current.value!.id)
      current.value = { ...current.value!, mfa: false }
      mfaAsk.value = false
      info.value = t('admin.users.mfaResetDone')
      emit('changed')
    } catch (e) {
      mfaError.value = writeErrorText(e, t)
    }
  })
}
</script>

<template>
  <EditDialog :title="title" :busy="busy" wide @close="requestClose">
    <form :id="id('form')" class="adm-form" novalidate @submit.prevent="save">
      <section class="iso-edit-section" :aria-labelledby="id('s1')">
        <h3 :id="id('s1')">{{ t('admin.users.section.data') }}</h3>
        <p v-if="!canWrite" class="muted">{{ t('admin.users.readOnly') }}</p>
        <div class="iso-edit-grid">
          <div class="edit-field iso-edit-wide">
            <label :for="id('dir')">{{ t('admin.users.field.directory') }}<span v-if="isNew" class="iso-req" aria-hidden="true"> *</span></label>
            <select v-if="isNew" :id="id('dir')" v-model.number="form.directory" :disabled="busy" :aria-invalid="problemText('directory') ? 'true' : undefined" @change="clear('directory')">
              <option :value="null" disabled>–</option>
              <option v-for="d in directories" :key="d.directory" :value="d.directory">{{ d.site.name }}{{ d.name ? ' › ' + d.name : '' }}</option>
            </select>
            <input v-else :id="id('dir')" type="text" :value="current?.directory ? [refName(current.directory.site), current.directory.name].filter(Boolean).join(' › ') : '–'" readonly aria-readonly="true" />
            <p v-if="problemText('directory')" class="iso-field-error">{{ problemText('directory') }}</p>
          </div>
          <div class="edit-field">
            <label :for="id('account')">{{ t('admin.users.field.account') }}<span v-if="isNew" class="iso-req" aria-hidden="true"> *</span></label>
            <input
              :id="id('account')"
              v-model="form.account"
              type="text"
              maxlength="100"
              autocomplete="off"
              :readonly="!isNew"
              :aria-readonly="!isNew ? 'true' : undefined"
              :disabled="busy"
              :autofocus="isNew"
              :aria-invalid="problemText('account') ? 'true' : undefined"
              @input="clear('account')"
            />
            <p v-if="problemText('account')" class="iso-field-error">{{ problemText('account') }}</p>
          </div>
          <div class="edit-field">
            <label :for="id('email')">{{ t('admin.users.field.email') }}</label>
            <input :id="id('email')" v-model="form.email" type="email" maxlength="200" autocomplete="off" :readonly="!canWrite" :disabled="busy" :aria-invalid="problemText('email') ? 'true' : undefined" @input="clear('email')" />
            <p v-if="problemText('email')" class="iso-field-error">{{ problemText('email') }}</p>
          </div>
          <div class="edit-field">
            <label :for="id('first')">{{ t('admin.users.field.firstName') }}</label>
            <input :id="id('first')" v-model="form.firstName" type="text" maxlength="100" autocomplete="off" :readonly="!canWrite" :disabled="busy" :autofocus="!isNew" />
          </div>
          <div class="edit-field">
            <label :for="id('last')">{{ t('admin.users.field.lastName') }}</label>
            <input :id="id('last')" v-model="form.lastName" type="text" maxlength="100" autocomplete="off" :readonly="!canWrite" :disabled="busy" />
          </div>
          <div class="edit-field iso-edit-wide adm-checks">
            <label class="adm-check">
              <input v-model="form.enabled" type="checkbox" :disabled="busy || !canWrite || isSelf" />
              {{ t('admin.users.field.enabled') }}
            </label>
            <label v-if="canSysAdmin" class="adm-check">
              <input v-model="form.sysAdmin" type="checkbox" :disabled="busy" />
              {{ t('admin.users.field.sysAdmin') }}
            </label>
            <span v-else-if="current?.sysAdmin" class="mod-badge adm-badge">{{ t('admin.users.field.sysAdmin') }}</span>
            <p v-if="isSelf" class="edit-hint">{{ t('admin.users.selfHint') }}</p>
          </div>
        </div>
      </section>

      <section v-if="isNew" class="iso-edit-section" :aria-labelledby="id('s2')">
        <h3 :id="id('s2')">{{ t('admin.users.section.initialPassword') }}</h3>
        <div class="iso-edit-grid">
          <div class="edit-field">
            <label :for="id('pw')">{{ t('admin.users.field.password') }}<span class="iso-req" aria-hidden="true"> *</span></label>
            <input :id="id('pw')" v-model="form.password" type="password" autocomplete="new-password" :disabled="busy" :aria-invalid="problemText('password') ? 'true' : undefined" @input="clear('password')" />
            <p v-if="problemText('password')" class="iso-field-error">{{ problemText('password') }}</p>
          </div>
          <div class="edit-field">
            <label :for="id('pw2')">{{ t('admin.users.field.repeat') }}<span class="iso-req" aria-hidden="true"> *</span></label>
            <input :id="id('pw2')" v-model="form.repeat" type="password" autocomplete="new-password" :disabled="busy" :aria-invalid="problemText('repeat') ? 'true' : undefined" @input="clear('repeat')" />
            <p v-if="problemText('repeat')" class="iso-field-error">{{ problemText('repeat') }}</p>
          </div>
        </div>
        <p class="edit-hint">{{ t('admin.users.passwordHint') }}</p>
      </section>

      <section v-if="!isNew" class="iso-edit-section" :aria-labelledby="id('s3')">
        <h3 :id="id('s3')">{{ t('admin.users.section.login') }}</h3>
        <div class="edit-actions adm-left">
          <button v-if="canWrite && !pwOpen" type="button" class="edit-btn" :disabled="busy" @click="pwOpen = true; pwInfo = null">{{ t('admin.users.setPassword') }}</button>
          <span class="muted">{{ current?.mfa ? t('admin.users.mfaOn') : t('admin.users.mfaOff') }}</span>
          <button v-if="scope.sysAdmin && current?.mfa" type="button" class="edit-btn is-danger" :disabled="busy" @click="mfaAsk = true; mfaError = null">{{ t('admin.users.mfaReset') }}</button>
        </div>
        <p v-if="pwInfo" class="muted" role="status">{{ pwInfo }}</p>
        <div v-if="pwOpen" class="adm-subform" role="group" :aria-label="t('admin.users.setPassword')">
          <div class="iso-edit-grid">
            <div class="edit-field">
              <label :for="id('npw')">{{ t('admin.users.field.newPassword') }}</label>
              <input :id="id('npw')" v-model="pw.password" type="password" autocomplete="new-password" :disabled="busy" @input="pwProblem = null" />
            </div>
            <div class="edit-field">
              <label :for="id('npw2')">{{ t('admin.users.field.repeat') }}</label>
              <input :id="id('npw2')" v-model="pw.repeat" type="password" autocomplete="new-password" :disabled="busy" @input="pwProblem = null" @keydown.enter.prevent="savePassword" />
            </div>
          </div>
          <p v-if="pwProblem" class="iso-field-error" role="alert">{{ t(pwProblem) }}</p>
          <p class="edit-hint">{{ t('admin.users.passwordSessions') }}</p>
          <div class="edit-actions adm-left">
            <button type="button" class="edit-btn" :disabled="busy" @click="pwOpen = false; pw = { password: '', repeat: '' }; pwProblem = null">{{ t('edit.cancel') }}</button>
            <button type="button" class="btn btn-primary" :disabled="busy" @click="savePassword">{{ t('admin.users.setPassword') }}</button>
          </div>
        </div>
      </section>

      <section v-if="!isNew" class="iso-edit-section" :aria-labelledby="id('s4')">
        <h3 :id="id('s4')">{{ t('admin.users.section.groups') }}</h3>
        <p v-if="!memberships.length" class="muted">{{ t('admin.users.noGroups') }}</p>
        <div v-else class="mod-table-wrap">
          <table class="mod-table adm-rights-table">
            <caption class="visually-hidden">{{ t('admin.users.section.groups') }}</caption>
            <thead>
              <tr>
                <th scope="col">{{ t('admin.col.group') }}</th>
                <th scope="col">{{ t('admin.col.site') }}</th>
                <th v-for="r in RIGHTS" :key="r" scope="col" class="adm-right-cell">{{ t('admin.rights.' + r) }}</th>
                <th scope="col"><span class="visually-hidden">{{ t('admin.users.removeGroup') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in memberships" :key="m.id" :class="{ 'adm-readonly': !groupWritable(m.id) }">
                <th scope="row">
                  {{ groupLabel(m.id).name }}
                  <span v-if="!groupWritable(m.id)" class="muted adm-small">({{ t('admin.readOnly') }})</span>
                </th>
                <td>{{ groupLabel(m.id).site || '–' }}</td>
                <RightsCheckboxes cells :model-value="m.rights" :of="groupLabel(m.id).name" :disabled="busy || !canWrite || !groupWritable(m.id)" @update:model-value="setRights(m.id, $event)" />
                <td>
                  <button v-if="canWrite && groupWritable(m.id)" type="button" class="edit-btn is-small" :disabled="busy" :aria-label="t('admin.users.removeGroupOf', { name: groupLabel(m.id).name })" @click="removeGroup(m.id)">
                    {{ t('admin.remove') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="canWrite && addable.length" class="edit-actions adm-left adm-add">
          <label :for="id('add')" class="visually-hidden">{{ t('admin.users.addGroup') }}</label>
          <select :id="id('add')" v-model.number="addChoice" :disabled="busy">
            <option :value="null">{{ t('admin.users.addGroupChoose') }}</option>
            <option v-for="g in addable" :key="g.id" :value="g.id">{{ g.name }}{{ refName(g.site) ? ' (' + refName(g.site) + ')' : '' }}</option>
          </select>
          <button type="button" class="edit-btn" :disabled="busy || addChoice == null" @click="addGroup">{{ t('admin.users.addGroup') }}</button>
        </div>
        <p class="edit-hint">{{ t('admin.users.grantHint') }}</p>
      </section>

      <p v-if="info" class="muted iso-edit-info" role="status">{{ info }}</p>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>

    <template #footer>
      <button type="button" class="edit-btn edit-left" :disabled="busy" @click="requestClose">{{ dirty ? t('edit.cancel') : t('edit.close') }}</button>
      <button v-if="canWrite" type="submit" class="btn btn-primary edit-submit" :form="id('form')" :disabled="busy || (!isNew && !dataDirty && !groupsDirty)">
        {{ busy ? t('edit.saving') : isNew ? t('admin.users.create') : t('edit.save') }}
      </button>
    </template>
  </EditDialog>

  <ConfirmDialog
    v-if="mfaAsk && current"
    :title="t('admin.users.mfaReset')"
    :message="t('admin.users.mfaResetQuestion', { name: userName(current) })"
    :confirm-label="t('admin.users.mfaReset')"
    :busy="busy"
    :error="mfaError"
    danger
    @confirm="resetMfa"
    @cancel="mfaAsk = false"
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
