<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { listGroups, type AdminGroup } from '../api/admin'
import { groupDirectories, refId, refName, sortByName } from '../admin/admin'
import GroupEditDialog from '../components/admin/GroupEditDialog.vue'
import { matchesTerms, searchTerms } from '../modules/search'
import { useAdminStore } from '../stores/admin'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/edit.css'
import '../styles/admin.css'

/*
 * "Gruppen" (E10.1): the readable groups with site and member count; "Neue Gruppe" (with a writable group directory)
 * and a click on a group open the side panel (rename, members, delete).
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const admin = useAdminStore()

const groups = shallowRef<AdminGroup[]>([])
const state = ref<'loading' | 'ready' | 'error' | 'forbidden'>('loading')
const text = ref('')
const site = ref<number | null>(null)
const message = ref<string | null>(null)
let seq = 0

const sites = computed(() => sortByName(admin.scope?.sites ?? [], (s) => s.name, locale.value))
const canCreate = computed(() => groupDirectories(admin.scope).length > 0)
const rows = computed(() => {
  const terms = searchTerms(text.value, locale.value)
  return sortByName(
    groups.value.filter((g) => matchesTerms(terms, [g.name, refName(g.site), g.path], locale.value)),
    (g) => g.name,
    locale.value,
  )
})

async function fetchList() {
  const current = ++seq
  state.value = 'loading'
  try {
    const list = await listGroups(auth.http, null, site.value)
    if (current !== seq) return
    groups.value = Array.isArray(list) ? list : []
    state.value = 'ready'
    if (editing.value && editing.value !== 'new') {
      const fresh = groups.value.find((g) => g.id === (editing.value as AdminGroup).id)
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

const editing = shallowRef<AdminGroup | 'new' | null>(null)
const newDirectory = computed(() => {
  if (site.value == null) return null
  const s = admin.scope?.sites.find((x) => x.id === site.value)
  return s ? refId(s.groupDirectory) : null
})

function onSaved(g: AdminGroup, created: boolean) {
  if (created) message.value = t('admin.groups.created', { name: g.name })
  editing.value = g
  void fetchList()
}

function onDeleted(name: string) {
  editing.value = null
  message.value = t('admin.groups.deleted', { name })
  void fetchList()
}
</script>

<template>
  <section class="admin-page" aria-labelledby="adm-groups-title">
    <div class="mod-head">
      <h1 id="adm-groups-title">{{ t('admin.nav.adminGroups') }}</h1>
      <span class="adm-actions">
        <button v-if="canCreate" type="button" class="btn btn-primary" @click="editing = 'new'">{{ t('admin.groups.new') }}</button>
      </span>
    </div>
    <p class="muted adm-hint">{{ t('admin.groups.hint') }}</p>
    <p v-if="message" class="alert alert-ok edit-result" role="status">{{ message }}</p>

    <fieldset class="mod-filters">
      <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
      <div v-if="sites.length > 1" class="control">
        <label for="adm-groups-site">{{ t('modules.site') }}</label>
        <select id="adm-groups-site" v-model="site">
          <option :value="null">{{ t('modules.allSites') }}</option>
          <option v-for="s in sites" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </div>
      <div class="control search">
        <label for="adm-groups-search">{{ t('modules.search') }}</label>
        <input id="adm-groups-search" v-model="text" type="search" :placeholder="t('modules.searchPlaceholder')" />
      </div>
    </fieldset>

    <p v-if="state === 'loading' && !groups.length" class="muted" role="status">{{ t('admin.loading') }}</p>
    <p v-else-if="state === 'forbidden'" class="alert alert-error" role="alert">{{ t('admin.forbidden') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link" @click="fetchList">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="state === 'ready' || groups.length">
      <p class="mod-count muted" role="status">{{ t('modules.count', { shown: rows.length, total: groups.length }) }}</p>
      <p v-if="!rows.length" class="muted">{{ t('admin.groups.empty') }}</p>
      <div v-else class="mod-table-wrap">
        <table class="mod-table">
          <caption class="visually-hidden">{{ t('admin.nav.adminGroups') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('admin.col.group') }}</th>
              <th scope="col">{{ t('admin.col.site') }}</th>
              <th scope="col">{{ t('admin.col.path') }}</th>
              <th scope="col" class="adm-num">{{ t('admin.col.members') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="g in rows" :key="g.id" class="mod-row">
              <th scope="row" class="adm-name">
                <button type="button" class="btn-link" @click="editing = g">{{ g.name }}</button>
              </th>
              <td>{{ refName(g.site) || '–' }}</td>
              <td class="muted">{{ g.path || '–' }}</td>
              <td class="adm-num">{{ g.members }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <GroupEditDialog
      v-if="editing && admin.scope"
      :key="editing === 'new' ? 'new' : 'g' + editing.id"
      :group="editing === 'new' ? null : editing"
      :scope="admin.scope"
      :directory="newDirectory"
      @close="editing = null"
      @saved="onSaved"
      @deleted="onDeleted"
      @changed="fetchList"
    />
  </section>
</template>
