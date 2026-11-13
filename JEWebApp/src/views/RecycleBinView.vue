<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import type { AnalysisDirectory } from '../api/analysis'
import { deleteForever, isParentGone, loadRecycleBin, parseKind, RECYCLE_KINDS, restoreEntry, type RecycleEntry, type RecycleKind, type RestoredEntry } from '../api/recycle'
import ConfirmDialog from '../components/edit/ConfirmDialog.vue'
import RestoreTargetDialog from '../components/recycle/RestoreTargetDialog.vue'
import { useBusy, writeErrorKind, writeErrorText } from '../edit/edit'
import { useAuthStore } from '../stores/auth'
import { useNavigationStore } from '../stores/navigation'
import '../styles/overview.css'
import '../styles/edit.css'
import '../components/recycle/recycle.css'

/*
 * Recycle bin of dashboards and analyses (/recycle?kind=dashboards|analyses), as in the Control Center: name,
 * original place, deleted at / by; "Wiederherstellen" (to the original place, or into a chosen directory when it is
 * gone) and "Endgültig löschen" after a confirmation. After a change the navigation is reloaded, so restored items
 * show up in the menu and the overview pages.
 */

const { t, locale } = useI18n()
const route = useRoute()
const auth = useAuthStore()
const nav = useNavigationStore()
const { busy, run } = useBusy()

const kind = computed<RecycleKind>(() => parseKind(route.query.kind))
const entries = ref<RecycleEntry[] | null>(null)
const loading = ref(false)
const loadFailed = ref(false)
/** success message (live region) */
const done = ref<{ text: string; restored?: RestoredEntry } | null>(null)
/** error of a restore to the original place (no dialog open) */
const actionError = ref<string | null>(null)

/* dialogs */
const targetFor = ref<RecycleEntry | null>(null)
const targetError = ref<string | null>(null)
const deleteFor = ref<RecycleEntry | null>(null)
const deleteError = ref<string | null>(null)

let request = 0

async function load(): Promise<void> {
  const mine = ++request
  loading.value = true
  loadFailed.value = false
  try {
    const list = await loadRecycleBin(auth.http, kind.value, locale.value)
    if (mine !== request) return
    entries.value = Array.isArray(list) ? list.filter((e) => e && typeof e.id === 'number') : []
  } catch {
    if (mine !== request) return
    entries.value = null
    loadFailed.value = true
  } finally {
    if (mine === request) loading.value = false
  }
}

watch(
  kind,
  () => {
    entries.value = null
    done.value = null
    actionError.value = null
    void load()
  },
  { immediate: true },
)

const directories = computed<AnalysisDirectory[] | null>(() => {
  const all = kind.value === 'analyses' ? nav.directories : nav.dashboardDirectories
  if (all === null) return null
  return kind.value === 'analyses' ? nav.creatableDirectories : nav.creatableDashboardDirectories
})

function loadDirectories(): Promise<void> {
  return kind.value === 'analyses' ? nav.loadDirectories() : nav.loadDashboardDirectories()
}

/* ---------------------------------------------------------------- restore */

function startRestore(entry: RecycleEntry) {
  done.value = null
  actionError.value = null
  if (entry.parentGone) {
    openTarget(entry)
    return
  }
  void run(async () => {
    try {
      await restored(entry, await restoreEntry(auth.http, entry.id, locale.value))
    } catch (e) {
      if (isParentGone(e)) openTarget(entry)
      else await failed(e, (text) => (actionError.value = text))
    }
  })
}

function openTarget(entry: RecycleEntry) {
  targetError.value = null
  targetFor.value = entry
  void loadDirectories()
}

function restoreTo(directoryId: number) {
  const entry = targetFor.value
  if (!entry) return
  targetError.value = null
  void run(async () => {
    try {
      const result = await restoreEntry(auth.http, entry.id, locale.value, directoryId)
      targetFor.value = null
      await restored(entry, result)
    } catch (e) {
      await failed(e, (text) => (targetError.value = text))
    }
  })
}

async function restored(entry: RecycleEntry, result: RestoredEntry) {
  const name = result?.name || entry.name
  done.value = {
    text: result?.path ? t('recycle.restored', { name, path: result.path }) : t('recycle.restoredNoPath', { name }),
    restored: result && typeof result.id === 'number' ? result : undefined,
  }
  await refresh()
}

/* ---------------------------------------------------------------- delete forever */

function startDelete(entry: RecycleEntry) {
  done.value = null
  actionError.value = null
  deleteError.value = null
  deleteFor.value = entry
}

function confirmDelete() {
  const entry = deleteFor.value
  if (!entry) return
  deleteError.value = null
  void run(async () => {
    try {
      await deleteForever(auth.http, entry.id)
      deleteFor.value = null
      done.value = { text: t('recycle.deletedForever', { name: entry.name }) }
      await refresh()
    } catch (e) {
      await failed(e, (text) => (deleteError.value = text))
    }
  })
}

/* ---------------------------------------------------------------- helpers */

/** after a change: the list and the navigation (menu, overview pages) */
async function refresh() {
  await Promise.all([load(), nav.load()])
}

/** shows the error; a missing entry (restored or deleted elsewhere) also reloads the list */
async function failed(e: unknown, show: (text: string) => void) {
  const kindOfError = writeErrorKind(e)
  show(kindOfError === 'notFound' ? t('recycle.error.notFound') : kindOfError === 'conflict' ? t('recycle.error.parentGone') : writeErrorText(e, t))
  if (kindOfError === 'notFound') await load()
}

function deletedText(entry: RecycleEntry): string {
  const date = formatDate(entry.deletedAt)
  if (!date) return entry.deletedBy ? t('recycle.deletedByOnly', { user: entry.deletedBy }) : ''
  return entry.deletedBy ? t('recycle.deleted', { date, user: entry.deletedBy }) : t('recycle.deletedNoUser', { date })
}

function formatDate(value: string | null): string {
  if (!value) return ''
  const dayOnly = /^\d{4}-\d{2}-\d{2}$/.test(value)
  const d = dayOnly ? new Date(Number(value.slice(0, 4)), Number(value.slice(5, 7)) - 1, Number(value.slice(8, 10))) : new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return new Intl.DateTimeFormat(locale.value, dayOnly ? { dateStyle: 'medium' } : { dateStyle: 'medium', timeStyle: 'short' }).format(d)
}

function originText(entry: RecycleEntry): string {
  if (entry.parentGone) return t('recycle.originGone')
  return entry.path ? t('recycle.origin', { path: entry.path }) : t('recycle.originHidden')
}

const openRoute = (r: RestoredEntry) => ({ name: r.kind === 'analyses' ? 'analysis' : 'dashboard', params: { id: r.id } })
const backRoute = computed(() => ({ name: kind.value === 'analyses' ? 'analyses' : 'dashboards' }))
const uid = 'rb'
</script>

<template>
  <section class="overview recycle" :aria-labelledby="uid + '-title'">
    <div class="ov-head">
      <h1 :id="uid + '-title'">{{ t('recycle.title') }}</h1>
      <div class="ov-actions">
        <RouterLink :to="backRoute" class="edit-btn rb-back rb-link">{{ t('recycle.back.' + kind) }}</RouterLink>
      </div>
    </div>
    <p class="muted rb-intro">{{ t('recycle.intro') }}</p>

    <nav :aria-label="t('recycle.kindsLabel')">
      <ul class="rb-switch">
        <li v-for="k in RECYCLE_KINDS" :key="k">
          <RouterLink :to="{ name: 'recycle', query: { kind: k } }" :aria-current="k === kind ? 'page' : undefined">{{ t('recycle.kinds.' + k) }}</RouterLink>
        </li>
      </ul>
    </nav>

    <div role="status" aria-live="polite">
      <div v-if="done" class="alert alert-ok rb-done">
        <span>{{ done.text }}</span>
        <RouterLink v-if="done.restored" :to="openRoute(done.restored)" class="btn-link">{{ t('recycle.open') }}</RouterLink>
      </div>
    </div>
    <p v-if="actionError" class="alert alert-error" role="alert">{{ actionError }}</p>

    <p v-if="loading && !entries" class="muted" role="status">{{ t('recycle.loading') }}</p>
    <div v-else-if="loadFailed" class="alert alert-error" role="alert">
      {{ t('recycle.loadError') }}
      <button type="button" class="btn-link" @click="load">{{ t('recycle.retry') }}</button>
    </div>
    <p v-else-if="entries && !entries.length" class="muted rb-empty">{{ t('recycle.empty.' + kind) }}</p>

    <ul v-else-if="entries" class="rb-list" :aria-label="t('recycle.listLabel', { kind: t('recycle.kinds.' + kind) })">
      <li v-for="e in entries" :key="e.id" class="rb-item">
        <div class="rb-text">
          <h2 class="rb-name">{{ e.name }}</h2>
          <span class="rb-meta" :class="{ 'is-gone': e.parentGone }">{{ originText(e) }}</span>
          <span v-if="deletedText(e)" class="rb-meta">{{ deletedText(e) }}</span>
        </div>
        <div v-if="e.canRestore || e.canDeleteForever" class="rb-actions">
          <button v-if="e.canRestore" type="button" class="edit-btn" :disabled="busy" :aria-label="t('recycle.restoreOf', { name: e.name })" @click="startRestore(e)">
            {{ e.parentGone ? t('recycle.restoreTo') : t('recycle.restore') }}
          </button>
          <button v-if="e.canDeleteForever" type="button" class="edit-btn is-danger" :disabled="busy" :aria-label="t('recycle.deleteForeverOf', { name: e.name })" @click="startDelete(e)">
            {{ t('recycle.deleteForever') }}
          </button>
        </div>
        <span v-else class="rb-meta">{{ t('recycle.noRights') }}</span>
      </li>
    </ul>

    <Teleport to="body">
      <RestoreTargetDialog
        v-if="targetFor"
        :name="targetFor.name"
        :directories="directories"
        :busy="busy"
        :error="targetError"
        @close="targetFor = null"
        @submit="restoreTo"
      />
      <ConfirmDialog
        v-if="deleteFor"
        :title="t('recycle.confirm.title')"
        :message="t('recycle.confirm.message', { name: deleteFor.name })"
        :confirm-label="t('recycle.deleteForever')"
        :busy="busy"
        :error="deleteError"
        danger
        @confirm="confirmDelete"
        @cancel="deleteFor = null"
      />
    </Teleport>
  </section>
</template>
