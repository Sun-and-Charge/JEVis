<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { createGroup, deleteGroup, listUsers, loadMembers, renameGroup, setMembers, type AdminGroup, type AdminScope, type AdminUser, type Right } from '../../api/admin'
import { adminErrorKey, conflictState, groupDirectories, normalizeRights, refName, RIGHTS, sameRights, sortByName, userName } from '../../admin/admin'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLeaveGuard } from '../../edit/useLeaveGuard'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import RightsCheckboxes from './RightsCheckboxes.vue'
import '../../styles/isoEdit.css'
import '../../styles/admin.css'

/*
 * Group in the side panel (E10.1): "Neue Gruppe" (group directory of a site, name), rename, delete (only without
 * members; into the recycle bin) and the members with their rights (user picker + rights checkboxes).
 */

const props = defineProps<{
  /** null: create */
  group: AdminGroup | null
  scope: AdminScope
  /** directory preselected for a new group (site filter of the list) */
  directory?: number | null
}>()

const emit = defineEmits<{
  close: []
  saved: [group: AdminGroup, created: boolean]
  deleted: [name: string]
  changed: []
}>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `adm-group-${Math.random().toString(36).slice(2, 9)}`
const id = (s: string) => `${uid}-${s}`

interface Row {
  user: number
  account: string
  name: string
  rights: Right[]
}

const directories = computed(() => groupDirectories(props.scope))
const current = shallowRef<AdminGroup | null>(props.group)
const isNew = computed(() => current.value == null)
const canWrite = computed(() => isNew.value || current.value?.canWrite === true)
const directory = ref<number | null>(
  props.directory != null && directories.value.some((d) => d.directory === props.directory) ? props.directory : directories.value.length === 1 ? directories.value[0].directory : null,
)
const initialName = ref(current.value?.name ?? '')
const name = ref(initialName.value)

const membersState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const initialRows = ref<Row[]>([])
const rows = ref<Row[]>([])
const users = shallowRef<AdminUser[]>([])
const addChoice = ref<number | null>(null)

const nameDirty = computed(() => canWrite.value && name.value.trim() !== initialName.value.trim())
const membersDirty = computed(() => {
  if (isNew.value || membersState.value !== 'ready') return false
  const a = initialRows.value.filter((r) => r.rights.length)
  const b = rows.value.filter((r) => r.rights.length)
  if (a.length !== b.length) return true
  return b.some((r) => {
    const o = a.find((x) => x.user === r.user)
    return !o || !sameRights(o.rights, r.rights)
  })
})
const dirty = computed(() => nameDirty.value || membersDirty.value)
const title = computed(() => (isNew.value ? t('admin.groups.createTitle') : t('admin.groups.editTitle', { name: current.value!.name })))
const memberCount = computed(() => (membersState.value === 'ready' ? initialRows.value.filter((r) => r.rights.length).length : (current.value?.members ?? 0)))
const canDelete = computed(() => !isNew.value && current.value?.canDelete === true)

const addable = computed(() =>
  sortByName(
    users.value.filter((u) => !rows.value.some((r) => r.user === u.id)),
    (u) => u.account,
    locale.value,
  ),
)

const problem = ref<string | null>(null)
const error = ref<string | null>(null)
const info = ref<string | null>(null)
const deleting = ref(false)
const deleteError = ref<string | null>(null)

const { leaveAsk, confirmDrop } = useLeaveGuard(dirty)

async function fetchMembers() {
  if (!current.value) return
  membersState.value = 'loading'
  try {
    const [members, all] = await Promise.all([loadMembers(auth.http, current.value.id), canWrite.value ? listUsers(auth.http).catch(() => [] as AdminUser[]) : Promise.resolve([] as AdminUser[])])
    const list = (Array.isArray(members) ? members : []).map((m) => ({ user: m.user.id, account: m.user.account, name: m.user.name || m.user.account, rights: normalizeRights(m.rights) }))
    initialRows.value = list.map((r) => ({ ...r, rights: [...r.rights] }))
    rows.value = list
    users.value = Array.isArray(all) ? all : []
    membersState.value = 'ready'
  } catch {
    membersState.value = 'error'
  }
}

onMounted(fetchMembers)

async function requestClose() {
  if (busy.value) return
  if (await confirmDrop()) emit('close')
}

function setRights(user: number, rights: Right[]) {
  rows.value = rows.value.map((r) => (r.user === user ? { ...r, rights } : r))
}

function addUser() {
  const u = users.value.find((x) => x.id === addChoice.value)
  if (!u) return
  rows.value = [...rows.value, { user: u.id, account: u.account, name: userName(u), rights: ['READ'] }]
  addChoice.value = null
}

function removeUser(user: number) {
  rows.value = rows.value.filter((r) => r.user !== user)
}

function take(g: AdminGroup) {
  current.value = g
  initialName.value = g.name
  name.value = g.name
}

function failed(e: unknown) {
  if (writeErrorKind(e) === 'conflict') {
    const fresh = conflictState<AdminGroup>(e)
    if (fresh) take(fresh)
    emit('changed')
    void fetchMembers()
    error.value = adminErrorKey(e) ? t(adminErrorKey(e)!) : t('admin.error.conflict')
    return
  }
  if (writeErrorKind(e) === 'notFound') emit('changed')
  const key = adminErrorKey(e)
  error.value = key ? t(key) : writeErrorText(e, t)
}

async function save() {
  error.value = null
  info.value = null
  problem.value = null
  if (isNew.value && directory.value == null) {
    problem.value = 'admin.problem.directory'
    return
  }
  if (!name.value.trim()) {
    problem.value = 'admin.problem.name'
    return
  }
  await run(async () => {
    try {
      if (isNew.value) {
        const created = await createGroup(auth.http, directory.value!, name.value.trim())
        take(created)
        emit('saved', created, true)
        return
      }
      let group = current.value!
      if (nameDirty.value) {
        group = await renameGroup(auth.http, group.id, group.version, name.value.trim())
        take(group)
      }
      if (membersDirty.value) {
        await setMembers(
          auth.http,
          group.id,
          group.version,
          rows.value.filter((r) => r.rights.length).map((r) => ({ user: r.user, rights: r.rights })),
        )
        await fetchMembers()
      }
      info.value = t('admin.groups.saved', { name: group.name })
      emit('saved', group, false)
    } catch (e) {
      failed(e)
    }
  })
}

async function confirmDelete() {
  const g = current.value
  if (!g) return
  await run(async () => {
    try {
      await deleteGroup(auth.http, g.id)
      deleting.value = false
      emit('deleted', g.name)
    } catch (e) {
      const key = adminErrorKey(e)
      deleteError.value = key ? t(key) : writeErrorText(e, t)
      if (writeErrorKind(e) === 'conflict') emit('changed')
    }
  })
}
</script>

<template>
  <EditDialog :title="title" :busy="busy" wide @close="requestClose">
    <form :id="id('form')" class="adm-form" novalidate @submit.prevent="save">
      <section class="iso-edit-section" :aria-labelledby="id('s1')">
        <h3 :id="id('s1')">{{ t('admin.groups.section.group') }}</h3>
        <div class="iso-edit-grid">
          <div class="edit-field">
            <label :for="id('dir')">{{ t('admin.groups.field.site') }}<span v-if="isNew" class="iso-req" aria-hidden="true"> *</span></label>
            <select v-if="isNew" :id="id('dir')" v-model.number="directory" :disabled="busy" @change="problem = null">
              <option :value="null" disabled>–</option>
              <option v-for="d in directories" :key="d.directory" :value="d.directory">{{ d.site.name }}{{ d.name ? ' › ' + d.name : '' }}</option>
            </select>
            <input v-else :id="id('dir')" type="text" :value="current?.path || refName(current?.site ?? null) || '–'" readonly aria-readonly="true" />
          </div>
          <div class="edit-field">
            <label :for="id('name')">{{ t('admin.groups.field.name') }}<span class="iso-req" aria-hidden="true"> *</span></label>
            <input :id="id('name')" v-model="name" type="text" maxlength="200" autocomplete="off" :readonly="!canWrite" :disabled="busy" autofocus @input="problem = null" />
          </div>
        </div>
        <p v-if="problem" class="iso-field-error" role="alert">{{ t(problem) }}</p>
      </section>

      <section v-if="!isNew" class="iso-edit-section" :aria-labelledby="id('s2')">
        <h3 :id="id('s2')">{{ t('admin.groups.section.members') }}</h3>
        <p v-if="membersState === 'loading'" class="muted" role="status">{{ t('admin.loading') }}</p>
        <p v-else-if="membersState === 'error'" class="alert alert-error" role="alert">
          {{ t('modules.loadError') }} <button type="button" class="btn-link" @click="fetchMembers">{{ t('modules.retry') }}</button>
        </p>
        <template v-else-if="membersState === 'ready'">
          <p v-if="!rows.length" class="muted">{{ t('admin.groups.noMembers') }}</p>
          <div v-else class="mod-table-wrap">
            <table class="mod-table adm-rights-table">
              <caption class="visually-hidden">{{ t('admin.groups.section.members') }}</caption>
              <thead>
                <tr>
                  <th scope="col">{{ t('admin.col.account') }}</th>
                  <th scope="col">{{ t('admin.col.name') }}</th>
                  <th v-for="r in RIGHTS" :key="r" scope="col" class="adm-right-cell">{{ t('admin.rights.' + r) }}</th>
                  <th scope="col"><span class="visually-hidden">{{ t('admin.remove') }}</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.user">
                  <th scope="row">{{ r.account }}</th>
                  <td>{{ r.name === r.account ? '–' : r.name }}</td>
                  <RightsCheckboxes cells :model-value="r.rights" :of="r.account" :disabled="busy || !canWrite" @update:model-value="setRights(r.user, $event)" />
                  <td>
                    <button v-if="canWrite" type="button" class="edit-btn is-small" :disabled="busy" :aria-label="t('admin.groups.removeMember', { name: r.account })" @click="removeUser(r.user)">
                      {{ t('admin.remove') }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="canWrite && addable.length" class="edit-actions adm-left adm-add">
            <label :for="id('add')" class="visually-hidden">{{ t('admin.groups.addMember') }}</label>
            <select :id="id('add')" v-model.number="addChoice" :disabled="busy">
              <option :value="null">{{ t('admin.groups.addMemberChoose') }}</option>
              <option v-for="u in addable" :key="u.id" :value="u.id">{{ u.account }}{{ userName(u) !== u.account ? ' (' + userName(u) + ')' : '' }}</option>
            </select>
            <button type="button" class="edit-btn" :disabled="busy || addChoice == null" @click="addUser">{{ t('admin.groups.addMember') }}</button>
          </div>
          <p class="edit-hint">{{ t('admin.users.grantHint') }}</p>
        </template>
      </section>

      <p v-if="info" class="muted iso-edit-info" role="status">{{ info }}</p>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>

    <template #footer>
      <button
        v-if="canDelete"
        type="button"
        class="edit-btn is-danger edit-left"
        :disabled="busy || memberCount > 0"
        :title="memberCount > 0 ? t('admin.groups.deleteHasMembers') : undefined"
        @click="deleting = true; deleteError = null"
      >
        {{ t('admin.groups.delete') }}
      </button>
      <button type="button" class="edit-btn" :class="{ 'edit-left': !canDelete }" :disabled="busy" @click="requestClose">{{ dirty ? t('edit.cancel') : t('edit.close') }}</button>
      <button v-if="canWrite" type="submit" class="btn btn-primary edit-submit" :form="id('form')" :disabled="busy || (!isNew && !dirty)">
        {{ busy ? t('edit.saving') : isNew ? t('admin.groups.create') : t('edit.save') }}
      </button>
    </template>
    <p v-if="canDelete && memberCount > 0" class="edit-hint adm-delete-hint">{{ t('admin.groups.deleteHasMembers') }}</p>
  </EditDialog>

  <ConfirmDialog
    v-if="deleting && current"
    :title="t('admin.groups.delete')"
    :message="t('admin.groups.deleteQuestion', { name: current.name })"
    :confirm-label="t('admin.groups.delete')"
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
