<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { applySharing, listUsers, loadSharing, previewSharing, type AdminUser, type SharingPlan, type SharingResult, type SiteSharing } from '../api/admin'
import { changedAssignments, planActionText, PRESETS, sharingRows, sortByName, userName, type PresetChoice, type SharingRow } from '../admin/admin'
import { useBusy, writeErrorText } from '../edit/edit'
import { useAdminStore } from '../stores/admin'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/edit.css'
import '../styles/admin.css'

/*
 * "Freigaben" (E10.1, wizard "Zugriff einrichten" of the Control Center): per site one group per preset (Ansehen,
 * Bearbeiten, Verwalten); every user is a member of the group of his preset. The table sets the wanted preset per
 * user; "Vorschau" shows the planned changes (objects to create, relationships to add or remove, warnings), and only
 * an unchanged preview can be applied. "Übernehmen" executes the plan with the caller's rights and lists what failed.
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const admin = useAdminStore()
const { busy, run } = useBusy()

const site = ref<number | null>(null)
const sharing = shallowRef<SiteSharing | null>(null)
const rows = ref<SharingRow[]>([])
const users = shallowRef<AdminUser[]>([])
const includeSubtree = ref(true)
const state = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const plan = shallowRef<SharingPlan | null>(null)
const result = shallowRef<SharingResult | null>(null)
const error = ref<string | null>(null)
const addChoice = ref<number | null>(null)
let seq = 0

const sites = computed(() =>
  sortByName(
    (admin.scope?.sites ?? []).filter((s) => s.canManage || admin.sysAdmin),
    (s) => s.name,
    locale.value,
  ),
)
const assignments = computed(() => changedAssignments(rows.value))
const changed = computed(() => Object.keys(assignments.value).length > 0)
const addable = computed(() =>
  sortByName(
    users.value.filter((u) => !rows.value.some((r) => r.id === u.id)),
    (u) => u.account,
    locale.value,
  ),
)

onMounted(async () => {
  await admin.load()
  if (sites.value.length === 1) site.value = sites.value[0].id
})

async function fetchSharing() {
  const current = ++seq
  plan.value = null
  error.value = null
  if (site.value == null) {
    state.value = 'idle'
    return
  }
  state.value = 'loading'
  try {
    const [s, u] = await Promise.all([loadSharing(auth.http, site.value), listUsers(auth.http).catch(() => [] as AdminUser[])])
    if (current !== seq) return
    sharing.value = s
    rows.value = sharingRows(s)
    users.value = Array.isArray(u) ? u : []
    state.value = 'ready'
  } catch (e) {
    if (current !== seq) return
    error.value = writeErrorText(e, t)
    state.value = 'error'
  }
}

watch(site, () => {
  result.value = null
  void fetchSharing()
})
/* any change makes the preview stale */
watch([rows, includeSubtree], () => (plan.value = null), { deep: true })

function setWanted(row: SharingRow, value: string) {
  const wanted = (value === '' ? null : value) as PresetChoice
  rows.value = rows.value.map((r) => (r.id === row.id ? { ...r, wanted } : r))
}

function addUser() {
  const u = users.value.find((x) => x.id === addChoice.value)
  if (!u) return
  rows.value = [...rows.value, { id: u.id, account: u.account, name: userName(u), current: null, wanted: 'VIEW' }]
  addChoice.value = null
}

const request = () => ({ assignments: assignments.value, includeSubtree: includeSubtree.value })

async function preview() {
  if (site.value == null) return
  error.value = null
  result.value = null
  await run(async () => {
    try {
      const p = await previewSharing(auth.http, site.value!, request())
      plan.value = { actions: Array.isArray(p?.actions) ? p.actions : [], warnings: Array.isArray(p?.warnings) ? p.warnings : [] }
    } catch (e) {
      error.value = writeErrorText(e, t)
    }
  })
}

async function apply() {
  if (site.value == null || !plan.value) return
  error.value = null
  await run(async () => {
    try {
      result.value = await applySharing(auth.http, site.value!, request())
      plan.value = null
    } catch (e) {
      error.value = writeErrorText(e, t)
    }
  })
  if (result.value) await fetchSharing()
}

const doneCount = computed(() => {
  const d = result.value?.done
  return typeof d === 'number' ? d : Array.isArray(d) ? d.length : 0
})
const failedList = computed(() => result.value?.failed ?? [])
const failedText = (f: SharingResult['failed'][number]) => [f.action ? planActionText(f.action, t) : f.text, f.error].filter(Boolean).join(': ')
const presetLabel = (p: PresetChoice) => (p ? t('admin.preset.' + p) : t('admin.preset.none'))
const groupName = (p: (typeof PRESETS)[number]) => sharing.value?.groups?.[p]?.name ?? null
</script>

<template>
  <section class="admin-page" aria-labelledby="adm-sharing-title">
    <div class="mod-head">
      <h1 id="adm-sharing-title">{{ t('admin.nav.adminSharing') }}</h1>
    </div>
    <p class="muted adm-hint">{{ t('admin.sharing.intro') }}</p>
    <p class="muted adm-hint">{{ t('admin.sharing.presets') }}</p>

    <fieldset class="mod-filters">
      <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
      <div class="control">
        <label for="adm-sharing-site">{{ t('modules.site') }}</label>
        <select id="adm-sharing-site" v-model="site" :disabled="busy">
          <option :value="null" disabled>{{ t('admin.sharing.chooseSite') }}</option>
          <option v-for="s in sites" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </div>
    </fieldset>

    <p v-if="admin.loaded && !sites.length" class="muted">{{ t('admin.sharing.noSites') }}</p>
    <p v-if="state === 'loading'" class="muted" role="status">{{ t('admin.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ error }}
      <button type="button" class="btn-link" @click="fetchSharing">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="state === 'ready' && sharing">
      <ul class="adm-preset-groups">
        <li v-for="p in PRESETS" :key="p">
          <strong>{{ t('admin.preset.' + p) }}:</strong>
          {{ groupName(p) ?? t('admin.sharing.groupNew') }}
        </li>
      </ul>

      <p v-if="!rows.length" class="muted">{{ t('admin.sharing.noUsers') }}</p>
      <div v-else class="mod-table-wrap">
        <table class="mod-table">
          <caption class="visually-hidden">{{ t('admin.nav.adminSharing') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('admin.col.account') }}</th>
              <th scope="col">{{ t('admin.col.name') }}</th>
              <th scope="col">{{ t('admin.sharing.current') }}</th>
              <th scope="col">{{ t('admin.sharing.wanted') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.id" class="mod-row" :class="{ 'adm-changed': r.wanted !== r.current }">
              <th scope="row">{{ r.account }}</th>
              <td>{{ r.name === r.account ? '–' : r.name }}</td>
              <td>{{ presetLabel(r.current) }}</td>
              <td>
                <select :value="r.wanted ?? ''" :disabled="busy" :aria-label="t('admin.sharing.wantedOf', { name: r.account })" @change="setWanted(r, ($event.target as HTMLSelectElement).value)">
                  <option value="">{{ t('admin.preset.none') }}</option>
                  <option v-for="p in PRESETS" :key="p" :value="p">{{ t('admin.preset.' + p) }}</option>
                </select>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="addable.length" class="edit-actions adm-left adm-add">
        <label for="adm-sharing-add" class="visually-hidden">{{ t('admin.sharing.addUser') }}</label>
        <select id="adm-sharing-add" v-model.number="addChoice" :disabled="busy">
          <option :value="null">{{ t('admin.sharing.addUserChoose') }}</option>
          <option v-for="u in addable" :key="u.id" :value="u.id">{{ u.account }}{{ userName(u) !== u.account ? ' (' + userName(u) + ')' : '' }}</option>
        </select>
        <button type="button" class="edit-btn" :disabled="busy || addChoice == null" @click="addUser">{{ t('admin.sharing.addUser') }}</button>
      </div>

      <label class="adm-check adm-subtree">
        <input v-model="includeSubtree" type="checkbox" :disabled="busy" />
        {{ t('admin.sharing.subtree') }}
      </label>

      <div class="edit-actions adm-left">
        <button type="button" class="edit-btn" :disabled="busy || !changed" @click="preview">{{ t('admin.sharing.preview') }}</button>
        <button type="button" class="btn btn-primary" :disabled="busy || !plan || !plan.actions.length" @click="apply">{{ busy ? t('edit.saving') : t('admin.sharing.apply') }}</button>
      </div>
      <p v-if="!changed && !result" class="muted">{{ t('admin.sharing.noChanges') }}</p>
    </template>

    <p v-if="error && state !== 'error'" class="alert alert-error edit-error" role="alert">{{ error }}</p>

    <section v-if="plan" class="adm-plan" aria-labelledby="adm-plan-title">
      <h2 id="adm-plan-title">{{ t('admin.sharing.planTitle') }}</h2>
      <p v-if="!plan.actions.length" class="muted">{{ t('admin.sharing.nothing') }}</p>
      <ol v-else class="adm-plan-list">
        <li v-for="(a, i) in plan.actions" :key="i" :class="'adm-plan-' + a.type">{{ planActionText(a, t) }}</li>
      </ol>
      <ul v-if="plan.warnings.length" class="edit-warnings adm-warnings">
        <li v-for="(w, i) in plan.warnings" :key="i">{{ w }}</li>
      </ul>
    </section>

    <section v-if="result" class="adm-plan" aria-labelledby="adm-result-title">
      <h2 id="adm-result-title">{{ t('admin.sharing.resultTitle') }}</h2>
      <p class="alert" :class="failedList.length ? 'alert-error' : 'alert-ok'" role="status">
        {{ t('admin.sharing.done', doneCount) }}<template v-if="failedList.length"> · {{ t('admin.sharing.failed', failedList.length) }}</template>
      </p>
      <ul v-if="failedList.length" class="adm-plan-list">
        <li v-for="(f, i) in failedList" :key="i">{{ failedText(f) }}</li>
      </ul>
      <ul v-if="result.warnings?.length" class="edit-warnings adm-warnings">
        <li v-for="(w, i) in result.warnings" :key="i">{{ w }}</li>
      </ul>
    </section>
  </section>
</template>
