<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { createApiKey, deleteApiKey, listApiKeys, listUsers, type AdminUser, type ApiKey } from '../api/admin'
import { apiKeyProblems, sortByName, userName } from '../admin/admin'
import ConfirmDialog from '../components/edit/ConfirmDialog.vue'
import EditDialog from '../components/edit/EditDialog.vue'
import { useBusy, writeErrorText } from '../edit/edit'
import { useAdminStore } from '../stores/admin'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/edit.css'
import '../styles/isoEdit.css'
import '../styles/admin.css'

/*
 * "API-Schlüssel" (E10.1, Sys Admins with an interactive login): the keys of the services and scripts, create (the
 * key is shown once, with a copy button) and delete (revoke).
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const admin = useAdminStore()
const { busy, run } = useBusy()

const keys = shallowRef<ApiKey[]>([])
const users = shallowRef<AdminUser[]>([])
const state = ref<'loading' | 'ready' | 'error' | 'forbidden'>('loading')
const message = ref<string | null>(null)

const creating = ref(false)
const form = ref<{ userId: number | null; name: string; expires: string }>({ userId: null, name: '', expires: '' })
const problems = ref<string[]>([])
const createError = ref<string | null>(null)
const newKey = shallowRef<ApiKey | null>(null)
const copied = ref(false)

const deleting = shallowRef<ApiKey | null>(null)
const deleteError = ref<string | null>(null)

const sortedUsers = computed(() => sortByName(users.value, (u) => u.account, locale.value))
const rows = computed(() => sortByName(keys.value, (k) => k.name, locale.value))

async function fetchList() {
  state.value = 'loading'
  try {
    const [k, u] = await Promise.all([listApiKeys(auth.http), listUsers(auth.http).catch(() => [] as AdminUser[])])
    keys.value = Array.isArray(k) ? k : []
    users.value = Array.isArray(u) ? u : []
    state.value = 'ready'
  } catch (e) {
    state.value = (e as { status?: number })?.status === 403 ? 'forbidden' : 'error'
  }
}

onMounted(async () => {
  await admin.load()
  if (admin.loaded && !admin.sysAdmin) {
    state.value = 'forbidden'
    return
  }
  await fetchList()
})

const date = (ms: number | null | undefined) => (ms ? new Date(ms).toLocaleString(locale.value, { dateStyle: 'medium', timeStyle: 'short' }) : '–')
const keyState = (k: ApiKey) => (k.revoked ? t('admin.apikeys.revoked') : k.expires != null && k.expires < Date.now() ? t('admin.apikeys.expired') : t('admin.apikeys.active'))

function openCreate() {
  form.value = { userId: null, name: '', expires: '' }
  problems.value = []
  createError.value = null
  newKey.value = null
  copied.value = false
  creating.value = true
}

async function create() {
  createError.value = null
  const found = apiKeyProblems(form.value)
  problems.value = found
  if (found.length) return
  const expires = form.value.expires ? new Date(form.value.expires + 'T23:59:59').getTime() : null
  await run(async () => {
    try {
      const k = await createApiKey(auth.http, { userId: form.value.userId!, name: form.value.name.trim(), expires })
      newKey.value = k
      void fetchList()
    } catch (e) {
      createError.value = writeErrorText(e, t)
    }
  })
}

async function copyKey() {
  if (!newKey.value?.key) return
  try {
    await navigator.clipboard.writeText(newKey.value.key)
    copied.value = true
  } catch {
    copied.value = false
  }
}

function closeCreate() {
  /* the key is shown only once: forget it when the dialog closes */
  newKey.value = null
  creating.value = false
}

async function confirmDelete() {
  const k = deleting.value
  if (!k) return
  await run(async () => {
    try {
      await deleteApiKey(auth.http, k.id)
      deleting.value = null
      message.value = t('admin.apikeys.deleted', { name: k.name })
      void fetchList()
    } catch (e) {
      deleteError.value = writeErrorText(e, t)
    }
  })
}
</script>

<template>
  <section class="admin-page" aria-labelledby="adm-keys-title">
    <div class="mod-head">
      <h1 id="adm-keys-title">{{ t('admin.nav.adminApiKeys') }}</h1>
      <span class="adm-actions">
        <button v-if="state === 'ready'" type="button" class="btn btn-primary" @click="openCreate">{{ t('admin.apikeys.new') }}</button>
      </span>
    </div>
    <p class="muted adm-hint">{{ t('admin.apikeys.hint') }}</p>
    <p v-if="message" class="alert alert-ok edit-result" role="status">{{ message }}</p>

    <p v-if="state === 'loading'" class="muted" role="status">{{ t('admin.loading') }}</p>
    <p v-else-if="state === 'forbidden'" class="alert alert-error" role="alert">{{ t('admin.apikeys.forbidden') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link" @click="fetchList">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="state === 'ready'">
      <p v-if="!rows.length" class="muted">{{ t('admin.apikeys.empty') }}</p>
      <div v-else class="mod-table-wrap">
        <table class="mod-table">
          <caption class="visually-hidden">{{ t('admin.nav.adminApiKeys') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('admin.apikeys.col.name') }}</th>
              <th scope="col">{{ t('admin.apikeys.col.user') }}</th>
              <th scope="col">{{ t('admin.apikeys.col.created') }}</th>
              <th scope="col">{{ t('admin.apikeys.col.expires') }}</th>
              <th scope="col">{{ t('admin.apikeys.col.lastUsed') }}</th>
              <th scope="col">{{ t('admin.apikeys.col.state') }}</th>
              <th scope="col"><span class="visually-hidden">{{ t('admin.apikeys.delete') }}</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="k in rows" :key="k.id" class="mod-row" :class="{ 'adm-disabled': k.revoked }">
              <th scope="row">{{ k.name }}</th>
              <td>{{ k.userName ?? k.userId }}</td>
              <td class="nowrap">{{ date(k.created) }}</td>
              <td class="nowrap">{{ k.expires ? date(k.expires) : t('admin.apikeys.never') }}</td>
              <td class="nowrap">{{ date(k.lastUsed) }}</td>
              <td>{{ keyState(k) }}</td>
              <td>
                <button
                  v-if="!k.revoked"
                  type="button"
                  class="edit-btn is-small is-danger"
                  :disabled="busy"
                  :aria-label="t('admin.apikeys.deleteOf', { name: k.name })"
                  @click="deleting = k; deleteError = null"
                >
                  {{ t('admin.apikeys.delete') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <EditDialog v-if="creating" :title="t('admin.apikeys.createTitle')" :busy="busy" @close="closeCreate">
      <template v-if="newKey">
        <p class="alert alert-ok" role="status">{{ t('admin.apikeys.created', { name: newKey.name }) }}</p>
        <p class="edit-message">{{ t('admin.apikeys.onlyOnce') }}</p>
        <div class="adm-key">
          <code id="adm-new-key" class="adm-key-text">{{ newKey.key }}</code>
          <button type="button" class="edit-btn" aria-describedby="adm-new-key" @click="copyKey">{{ copied ? t('admin.apikeys.copied') : t('admin.apikeys.copy') }}</button>
        </div>
      </template>
      <form v-else id="adm-key-form" class="adm-form" novalidate @submit.prevent="create">
        <div class="edit-field">
          <label for="adm-key-user">{{ t('admin.apikeys.col.user') }}<span class="iso-req" aria-hidden="true"> *</span></label>
          <select id="adm-key-user" v-model="form.userId" :disabled="busy" autofocus>
            <option :value="null" disabled>–</option>
            <option v-for="u in sortedUsers" :key="u.id" :value="u.id">{{ u.account }}{{ userName(u) !== u.account ? ' (' + userName(u) + ')' : '' }}</option>
          </select>
          <p v-if="problems.includes('user')" class="iso-field-error">{{ t('admin.apikeys.problem.user') }}</p>
        </div>
        <div class="edit-field">
          <label for="adm-key-name">{{ t('admin.apikeys.col.name') }}<span class="iso-req" aria-hidden="true"> *</span></label>
          <input id="adm-key-name" v-model="form.name" type="text" maxlength="200" autocomplete="off" :disabled="busy" />
          <p v-if="problems.includes('name')" class="iso-field-error">{{ t('admin.apikeys.problem.name') }}</p>
        </div>
        <div class="edit-field">
          <label for="adm-key-expires">{{ t('admin.apikeys.col.expires') }}</label>
          <input id="adm-key-expires" v-model="form.expires" type="date" :disabled="busy" aria-describedby="adm-key-expires-hint" />
          <p id="adm-key-expires-hint" class="edit-hint">{{ t('admin.apikeys.expiresHint') }}</p>
          <p v-if="problems.includes('expires')" class="iso-field-error">{{ t('admin.apikeys.problem.expires') }}</p>
        </div>
        <p v-if="createError" class="alert alert-error edit-error" role="alert">{{ createError }}</p>
      </form>
      <template #footer>
        <button type="button" class="edit-btn edit-left" :disabled="busy" @click="closeCreate">{{ newKey ? t('edit.close') : t('edit.cancel') }}</button>
        <button v-if="!newKey" type="submit" form="adm-key-form" class="btn btn-primary edit-submit" :disabled="busy">{{ busy ? t('edit.saving') : t('admin.apikeys.create') }}</button>
      </template>
    </EditDialog>

    <ConfirmDialog
      v-if="deleting"
      :title="t('admin.apikeys.delete')"
      :message="t('admin.apikeys.deleteQuestion', { name: deleting.name })"
      :confirm-label="t('admin.apikeys.delete')"
      :busy="busy"
      :error="deleteError"
      danger
      @confirm="confirmDelete"
      @cancel="deleting = null"
    />
  </section>
</template>
