<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadMaintenance, type MaintenanceEntry } from '../api/modules'
import { BANNER_REFRESH_MS, bannerEntries, bannerKey, loadDismissed, pruneDismissed, saveDismissed } from '../modules/banner'
import { formatPeriod } from '../modules/dates'
import { useAuthStore } from '../stores/auth'
import { useNavigationStore } from '../stores/navigation'

/*
 * Active maintenance messages (planned or in progress) as notice above the content. Loaded after login and every
 * 15 minutes, only if the user can read maintenance entries at all. Each notice can be hidden until it changes.
 */
const { t, te, locale } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()

const entries = shallowRef<MaintenanceEntry[]>([])
const dismissed = ref<Set<string>>(loadDismissed())
const visible = computed(() => bannerEntries(entries.value, dismissed.value))
let timer: ReturnType<typeof setInterval> | null = null

async function refresh() {
  if (nav.modules.maintenance <= 0 || !auth.isLoggedIn) return
  try {
    const result = await loadMaintenance(auth.http)
    entries.value = result.entries ?? []
    const pruned = pruneDismissed(dismissed.value, entries.value)
    if (pruned.size !== dismissed.value.size) {
      dismissed.value = pruned
      saveDismissed(pruned)
    }
  } catch {
    /* keep the last notices; the next refresh tries again */
  }
}

function stop() {
  if (timer) clearInterval(timer)
  timer = null
}

watch(
  () => nav.modules.maintenance,
  (count) => {
    stop()
    if (count > 0) {
      refresh()
      timer = setInterval(refresh, BANNER_REFRESH_MS)
    } else {
      entries.value = []
    }
  },
  { immediate: true },
)

onBeforeUnmount(stop)

function dismiss(entry: MaintenanceEntry) {
  const next = new Set(dismissed.value)
  next.add(bannerKey(entry))
  dismissed.value = next
  saveDismissed(next)
}

function typeLabel(e: MaintenanceEntry): string {
  const key = 'maintenance.types.' + e.type
  return e.type && te(key) ? t(key) : t('maintenance.types.MAINTENANCE')
}
</script>

<template>
  <section v-if="visible.length" class="banner" :aria-label="t('banner.region')">
    <article v-for="e in visible" :key="bannerKey(e)" class="notice" :aria-labelledby="'mb-' + e.id">
      <span class="icon" aria-hidden="true">!</span>
      <div class="body">
        <p :id="'mb-' + e.id" class="title">
          <span class="type">{{ typeLabel(e) }}:</span> {{ e.title }}
          <span v-if="e.status === 'IN_PROGRESS'" class="state">({{ t('maintenance.state.inProgress') }})</span>
        </p>
        <p v-if="e.start" class="line">
          <span class="visually-hidden">{{ t('banner.period') }}: </span>{{ formatPeriod(e.start, e.end, locale) }}
        </p>
        <p v-if="e.impact" class="line impact"><span class="label">{{ t('banner.impact') }}:</span> {{ e.impact }}</p>
        <RouterLink :to="{ name: 'maintenance' }" class="more">{{ t('banner.details') }}</RouterLink>
      </div>
      <button type="button" class="close" :aria-label="t('banner.dismiss', { title: e.title })" :title="t('banner.dismiss', { title: e.title })" @click="dismiss(e)">
        ×
      </button>
    </article>
  </section>
</template>

<style scoped>
.banner {
  display: grid;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.notice {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.65rem 0.75rem 0.65rem 0.9rem;
  background: #fff8c4;
  border: 1px solid var(--sun);
  border-left: 6px solid var(--sun);
  border-radius: var(--radius);
  color: var(--blueberry);
}

.icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  margin-top: 0.05rem;
  border-radius: 50%;
  background: var(--blueberry);
  color: var(--sun);
  font-weight: 600;
}

.body {
  flex: 1;
  min-width: 0;
}

.body p {
  margin: 0 0 0.2rem;
  overflow-wrap: anywhere;
}

.title {
  font-weight: 600;
}

.state {
  font-weight: 400;
}

.line {
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
}

.impact {
  white-space: pre-line;
}

.label {
  font-weight: 600;
}

.more {
  font-size: 0.9rem;
  font-weight: 600;
}

.close {
  flex: none;
  width: 2rem;
  height: 2rem;
  padding: 0;
  font-size: 1.3rem;
  line-height: 1;
  color: var(--blueberry);
  background: none;
  border: 1px solid transparent;
  border-radius: 50%;
  cursor: pointer;
}

.close:hover {
  border-color: var(--blueberry);
}
</style>
