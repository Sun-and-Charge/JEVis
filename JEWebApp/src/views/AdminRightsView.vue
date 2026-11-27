<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { listGroups, listUsers, loadObjectRights, loadUserRights, type AdminGroup, type AdminUser, type ObjectRights, type UserRights } from '../api/admin'
import { flagRights, refName, rightReasons, RIGHTS, sortByName, userName } from '../admin/admin'
import { writeErrorText } from '../edit/edit'
import { useAdminStore } from '../stores/admin'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/edit.css'
import '../styles/isoEdit.css'
import '../styles/admin.css'

/*
 * "Wer darf was?" (E10.1, GET /web/admin/rights): for an object (a site, a group or any object id) the users with
 * their effective rights and the groups each right comes from; for a user his group memberships and roles.
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const admin = useAdminStore()

const mode = ref<'object' | 'user'>('object')
const objectChoice = ref<string>('')
const objectId = ref('')
const userChoice = ref<number | null>(null)
const users = shallowRef<AdminUser[]>([])
const groups = shallowRef<AdminGroup[]>([])
const objectResult = shallowRef<ObjectRights | null>(null)
const userResult = shallowRef<UserRights | null>(null)
const state = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const error = ref<string | null>(null)
const problem = ref<string | null>(null)
let seq = 0

const sites = computed(() => sortByName(admin.scope?.sites ?? [], (s) => s.name, locale.value))
const sortedGroups = computed(() => sortByName(groups.value, (g) => g.name, locale.value))
const sortedUsers = computed(() => sortByName(users.value, (u) => u.account, locale.value))

onMounted(async () => {
  await admin.load()
  const [u, g] = await Promise.all([listUsers(auth.http).catch(() => [] as AdminUser[]), listGroups(auth.http).catch(() => [] as AdminGroup[])])
  users.value = Array.isArray(u) ? u : []
  groups.value = Array.isArray(g) ? g : []
})

function chosenObject(): number | null {
  const typed = objectId.value.trim()
  if (typed) return /^\d+$/.test(typed) ? Number(typed) : NaN
  return objectChoice.value ? Number(objectChoice.value) : null
}

async function show() {
  problem.value = null
  error.value = null
  const current = ++seq
  let target: number | null
  if (mode.value === 'object') {
    target = chosenObject()
    if (target == null || Number.isNaN(target)) {
      problem.value = target == null ? 'admin.rightsView.chooseObject' : 'admin.rightsView.badId'
      return
    }
  } else {
    target = userChoice.value
    if (target == null) {
      problem.value = 'admin.rightsView.chooseUser'
      return
    }
  }
  state.value = 'loading'
  objectResult.value = null
  userResult.value = null
  try {
    if (mode.value === 'object') {
      const r = await loadObjectRights(auth.http, target)
      if (current !== seq) return
      objectResult.value = r
    } else {
      const r = await loadUserRights(auth.http, target)
      if (current !== seq) return
      userResult.value = r
    }
    state.value = 'ready'
  } catch (e) {
    if (current !== seq) return
    error.value = writeErrorText(e, t)
    state.value = 'error'
  }
}

function setMode(m: 'object' | 'user') {
  mode.value = m
  state.value = 'idle'
  problem.value = null
  error.value = null
}

const reasonText = (sources: { group: string; roles: string[] }[]) =>
  sources.map((s) => (s.roles.length ? t('admin.rightsView.viaRoles', { group: s.group, roles: s.roles.join(', ') }) : s.group)).join('; ')
const userLabel = (u: AdminUser) => u.account + (userName(u) !== u.account ? ' (' + userName(u) + ')' : '')
</script>

<template>
  <section class="admin-page" aria-labelledby="adm-rights-title">
    <div class="mod-head">
      <h1 id="adm-rights-title">{{ t('admin.nav.adminRights') }}</h1>
    </div>
    <p class="muted adm-hint">{{ t('admin.rightsView.hint') }}</p>

    <form class="mod-filters adm-rights-form" novalidate @submit.prevent="show">
      <div class="control">
        <span class="adm-seg-label" aria-hidden="true">{{ t('admin.rightsView.by') }}</span>
        <div class="mod-seg" role="radiogroup" :aria-label="t('admin.rightsView.by')">
          <label><input type="radio" name="adm-rights-mode" value="object" :checked="mode === 'object'" @change="setMode('object')" /> {{ t('admin.rightsView.object') }}</label>
          <label><input type="radio" name="adm-rights-mode" value="user" :checked="mode === 'user'" @change="setMode('user')" /> {{ t('admin.rightsView.user') }}</label>
        </div>
      </div>
      <template v-if="mode === 'object'">
        <div class="control">
          <label for="adm-rights-object">{{ t('admin.rightsView.siteOrGroup') }}</label>
          <select id="adm-rights-object" v-model="objectChoice">
            <option value="">–</option>
            <optgroup v-if="sites.length" :label="t('admin.col.site')">
              <option v-for="s in sites" :key="'s' + s.id" :value="String(s.id)">{{ s.name }}</option>
            </optgroup>
            <optgroup v-if="sortedGroups.length" :label="t('admin.col.group')">
              <option v-for="g in sortedGroups" :key="'g' + g.id" :value="String(g.id)">{{ g.name }}{{ refName(g.site) ? ' (' + refName(g.site) + ')' : '' }}</option>
            </optgroup>
          </select>
        </div>
        <div class="control">
          <label for="adm-rights-id">{{ t('admin.rightsView.objectId') }}</label>
          <input id="adm-rights-id" v-model="objectId" type="text" inputmode="numeric" autocomplete="off" />
        </div>
      </template>
      <div v-else class="control">
        <label for="adm-rights-user">{{ t('admin.rightsView.user') }}</label>
        <select id="adm-rights-user" v-model="userChoice">
          <option :value="null">–</option>
          <option v-for="u in sortedUsers" :key="u.id" :value="u.id">{{ userLabel(u) }}</option>
        </select>
      </div>
      <div class="control adm-submit">
        <button type="submit" class="btn btn-primary" :disabled="state === 'loading'">{{ t('admin.rightsView.show') }}</button>
      </div>
    </form>
    <p v-if="problem" class="iso-field-error" role="alert">{{ t(problem) }}</p>

    <p v-if="state === 'loading'" class="muted" role="status">{{ t('admin.loading') }}</p>
    <p v-else-if="state === 'error'" class="alert alert-error" role="alert">{{ error }}</p>

    <template v-if="state === 'ready' && objectResult">
      <h2 class="adm-subtitle">{{ objectResult.objectName ?? objectResult.objectId }} <span class="muted">({{ objectResult.jevisClass }}, {{ objectResult.objectId }})</span></h2>
      <p class="muted">
        {{ t('admin.rightsView.owners') }}: {{ objectResult.ownerGroups.map((g) => refName(g)).join(', ') || '–' }}
        <template v-if="objectResult.publicObject"> · {{ t('admin.rightsView.public') }}</template>
      </p>
      <p v-if="!objectResult.complete" class="muted">{{ t('admin.rightsView.incomplete') }}</p>
      <p v-if="!objectResult.users.length" class="muted">{{ t('admin.rightsView.nobody') }}</p>
      <div v-else class="mod-table-wrap">
        <table class="mod-table adm-rights-table">
          <caption class="visually-hidden">{{ t('admin.nav.adminRights') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('admin.col.account') }}</th>
              <th v-for="r in RIGHTS" :key="r" scope="col" class="adm-right-cell">{{ t('admin.rights.' + r) }}</th>
              <th scope="col">{{ t('admin.rightsView.reason') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in objectResult.users" :key="u.userId" :class="{ 'adm-disabled': !u.enabled }">
              <th scope="row">
                {{ u.userName ?? u.userId }}
                <span v-if="u.sysAdmin" class="mod-badge adm-badge">{{ t('admin.col.sysAdmin') }}</span>
                <span v-if="!u.enabled" class="muted adm-small">({{ t('admin.rightsView.disabled') }})</span>
              </th>
              <td v-for="r in rightReasons(u)" :key="r.right" class="adm-right-cell" :title="reasonText(r.sources) || undefined">
                <span v-if="r.granted" :aria-label="t('admin.rightsView.yes')">✓</span>
                <span v-else aria-hidden="true">–</span>
              </td>
              <td class="adm-reason">
                <template v-if="u.sysAdmin">{{ t('admin.rightsView.sysAdminReason') }}</template>
                <template v-else>
                  <span v-for="r in rightReasons(u).filter((x) => x.granted && x.sources.length)" :key="r.right" class="adm-reason-line">
                    {{ t('admin.rights.' + r.right) }}: {{ reasonText(r.sources) }}
                  </span>
                  <span v-if="u.ownObject" class="adm-reason-line">{{ t('admin.rightsView.own') }}</span>
                  <span v-if="u.publicObject" class="adm-reason-line">{{ t('admin.rightsView.public') }}</span>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-if="state === 'ready' && userResult">
      <h2 class="adm-subtitle">
        {{ userResult.userName ?? userResult.userId }}
        <span v-if="userResult.sysAdmin" class="mod-badge adm-badge">{{ t('admin.col.sysAdmin') }}</span>
        <span v-if="!userResult.enabled" class="muted adm-small">({{ t('admin.rightsView.disabled') }})</span>
      </h2>
      <p v-if="userResult.sysAdmin" class="muted">{{ t('admin.rightsView.sysAdminReason') }}</p>
      <p v-if="!userResult.groups.length" class="muted">{{ t('admin.users.noGroups') }}</p>
      <div v-else class="mod-table-wrap">
        <table class="mod-table adm-rights-table">
          <caption class="visually-hidden">{{ t('admin.users.section.groups') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('admin.col.group') }}</th>
              <th v-for="r in RIGHTS" :key="r" scope="col" class="adm-right-cell">{{ t('admin.rights.' + r) }}</th>
              <th scope="col">{{ t('admin.rightsView.roles') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="g in userResult.groups" :key="g.groupId">
              <th scope="row">{{ g.groupName ?? g.groupId }}</th>
              <td v-for="r in RIGHTS" :key="r" class="adm-right-cell">
                <span v-if="flagRights(g).includes(r)" :aria-label="t('admin.rightsView.yes')">✓</span>
                <span v-else aria-hidden="true">–</span>
              </td>
              <td>{{ g.roles.map((x) => refName(x)).join(', ') || '–' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
