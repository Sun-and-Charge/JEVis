<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { listGroups, listUsers, type AdminGroup, type AdminUser } from '../api/admin'
import { filterUsers, refId, sortByName, userDirectories, userName } from '../admin/admin'
import UserEditDialog from '../components/admin/UserEditDialog.vue'
import { useNarrow } from '../modules/useNarrow'
import { useAdminStore } from '../stores/admin'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/edit.css'
import '../styles/admin.css'

/*
 * "Benutzer" (E10.1): the users the caller can read, with search and site filter (the server filters by site: members
 * of a group of the site or users in its user directory). "Neuer Benutzer" (with a writable user directory) and a
 * click on a user open the side panel.
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const admin = useAdminStore()
const narrow = useNarrow()

const users = shallowRef<AdminUser[]>([])
const groups = shallowRef<AdminGroup[]>([])
const state = ref<'loading' | 'ready' | 'error' | 'forbidden'>('loading')
const text = ref('')
const site = ref<number | null>(null)
const message = ref<string | null>(null)
let seq = 0

const sites = computed(() => sortByName(admin.scope?.sites ?? [], (s) => s.name, locale.value))
const canCreate = computed(() => userDirectories(admin.scope).length > 0)
const rows = computed(() => sortByName(filterUsers(users.value, text.value, locale.value), (u) => u.account, locale.value))

async function fetchList() {
  const current = ++seq
  state.value = 'loading'
  try {
    const [u, g] = await Promise.all([listUsers(auth.http, null, site.value), listGroups(auth.http)])
    if (current !== seq) return
    users.value = Array.isArray(u) ? u : []
    groups.value = Array.isArray(g) ? g : []
    state.value = 'ready'
    if (editing.value && editing.value !== 'new') {
      const fresh = users.value.find((x) => x.id === (editing.value as AdminUser).id)
      if (fresh) editing.value = fresh
    }
  } catch (e) {
    if (current !== seq) return
    state.value = (e as { status?: number })?.status === 403 ? 'forbidden' : 'error'
  }
}

onMounted(async () => {
  await admin.load()
  await fetchList()
})
watch(site, fetchList)

/* ---------- editor ---------- */
const editing = shallowRef<AdminUser | 'new' | null>(null)
const newDirectory = computed(() => {
  if (site.value == null) return null
  const s = admin.scope?.sites.find((x) => x.id === site.value)
  return s ? refId(s.userDirectory) : null
})

function onSaved(u: AdminUser, created: boolean) {
  if (created) message.value = t('admin.users.created', { name: u.account })
  editing.value = u
  void fetchList()
}

const groupNames = (u: AdminUser) => u.groups.map((g) => g.name).join(', ')
const yes = (v: boolean) => (v ? t('admin.yes') : t('admin.no'))
</script>

<template>
  <section class="admin-page" aria-labelledby="adm-users-title">
    <div class="mod-head">
      <h1 id="adm-users-title">{{ t('admin.nav.adminUsers') }}</h1>
      <span class="adm-actions">
        <button v-if="canCreate" type="button" class="btn btn-primary" @click="editing = 'new'">{{ t('admin.users.new') }}</button>
      </span>
    </div>
    <p class="muted adm-hint">{{ t('admin.users.hint') }}</p>
    <p v-if="message" class="alert alert-ok edit-result" role="status">{{ message }}</p>

    <fieldset class="mod-filters">
      <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
      <div v-if="sites.length > 1" class="control">
        <label for="adm-users-site">{{ t('modules.site') }}</label>
        <select id="adm-users-site" v-model="site">
          <option :value="null">{{ t('modules.allSites') }}</option>
          <option v-for="s in sites" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </div>
      <div class="control search">
        <label for="adm-users-search">{{ t('modules.search') }}</label>
        <input id="adm-users-search" v-model="text" type="search" :placeholder="t('admin.users.searchPlaceholder')" />
      </div>
    </fieldset>

    <p v-if="state === 'loading' && !users.length" class="muted" role="status">{{ t('admin.loading') }}</p>
    <p v-else-if="state === 'forbidden'" class="alert alert-error" role="alert">{{ t('admin.forbidden') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link" @click="fetchList">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="state === 'ready' || users.length">
      <p class="mod-count muted" role="status">{{ t('modules.count', { shown: rows.length, total: users.length }) }}</p>
      <p v-if="!rows.length" class="muted">{{ t('admin.users.empty') }}</p>

      <ul v-else-if="narrow" class="mod-cards">
        <li v-for="u in rows" :key="u.id" class="mod-card">
          <h2>
            <button type="button" class="btn-link" @click="editing = u">{{ u.account }}</button>
          </h2>
          <dl class="mod-dl">
            <dt>{{ t('admin.col.name') }}</dt>
            <dd>{{ userName(u) === u.account ? '–' : userName(u) }}</dd>
            <dt>{{ t('admin.col.email') }}</dt>
            <dd>{{ u.email || '–' }}</dd>
            <dt>{{ t('admin.col.enabled') }}</dt>
            <dd>{{ yes(u.enabled) }}</dd>
            <dt>{{ t('admin.col.groups') }}</dt>
            <dd>{{ groupNames(u) || '–' }}</dd>
          </dl>
        </li>
      </ul>

      <div v-else class="mod-table-wrap">
        <table class="mod-table">
          <caption class="visually-hidden">{{ t('admin.nav.adminUsers') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('admin.col.account') }}</th>
              <th scope="col">{{ t('admin.col.name') }}</th>
              <th scope="col">{{ t('admin.col.email') }}</th>
              <th scope="col">{{ t('admin.col.enabled') }}</th>
              <th scope="col">{{ t('admin.col.sysAdmin') }}</th>
              <th scope="col">{{ t('admin.col.mfa') }}</th>
              <th scope="col">{{ t('admin.col.groups') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in rows" :key="u.id" class="mod-row" :class="{ 'adm-disabled': !u.enabled }">
              <th scope="row" class="adm-name">
                <button type="button" class="btn-link" @click="editing = u">{{ u.account }}</button>
              </th>
              <td>{{ userName(u) === u.account ? '–' : userName(u) }}</td>
              <td>{{ u.email || '–' }}</td>
              <td>{{ yes(u.enabled) }}</td>
              <td>{{ u.sysAdmin ? t('admin.yes') : '–' }}</td>
              <td>{{ u.mfa ? t('admin.yes') : '–' }}</td>
              <td class="adm-groups">{{ groupNames(u) || '–' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <UserEditDialog
      v-if="editing && admin.scope"
      :key="editing === 'new' ? 'new' : 'u' + editing.id"
      :user="editing === 'new' ? null : editing"
      :scope="admin.scope"
      :groups="groups"
      :directory="newDirectory"
      @close="editing = null"
      @saved="onSaved"
      @changed="fetchList"
    />
  </section>
</template>
