<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadMaintenance, type MaintenanceEntry, type MaintenanceList } from '../api/modules'
import { localName } from '../api/navigation'
import MaintenanceDetails from '../components/modules/MaintenanceDetails.vue'
import { formatDuration, formatPeriod } from '../modules/dates'
import { DEFAULT_MAINTENANCE_FILTER, filterMaintenance, maintenanceState, sortMaintenance, type MaintenanceFilter } from '../modules/maintenance'
import { siteOptions, type SiteFilter } from '../modules/search'
import { useNarrow } from '../modules/useNarrow'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const narrow = useNarrow()

const list = shallowRef<MaintenanceList | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const filter = reactive<MaintenanceFilter>({ ...DEFAULT_MAINTENANCE_FILTER })
const expanded = ref(new Set<number>())
let seq = 0

async function fetchList() {
  const current = ++seq
  state.value = 'loading'
  try {
    const result = await loadMaintenance(auth.http)
    if (current !== seq) return
    list.value = result
    state.value = 'ready'
  } catch {
    if (current !== seq) return
    state.value = 'error'
  }
}

onMounted(fetchList)

const all = computed(() => list.value?.entries ?? [])
const sites = computed(() => siteOptions(all.value, locale.value))
const hasEntriesWithoutSite = computed(() => all.value.some((e) => !e.site))
const rows = computed(() => sortMaintenance(filterMaintenance(all.value, filter, locale.value)))

const siteValue = computed({
  get: () => String(filter.site),
  set: (v: string) => {
    filter.site = (v === 'all' || v === 'none' ? v : Number(v)) as SiteFilter
  },
})

function toggle(id: number) {
  const next = new Set(expanded.value)
  if (!next.delete(id)) next.add(id)
  expanded.value = next
}

function stateLabel(e: MaintenanceEntry): string {
  return t('maintenance.state.' + maintenanceState(e))
}

function typeLabel(e: MaintenanceEntry): string {
  if (!e.type) return t('modules.none')
  const key = 'maintenance.types.' + e.type
  return te(key) ? t(key) : e.type
}

function siteLabel(e: MaintenanceEntry): string {
  return e.site ? localName(e.site, locale.value) : t('modules.none')
}

const statusOptions = ['active', 'done', 'all'] as const
const typeOptions = ['MAINTENANCE', 'KNOWN_ISSUE'] as const
</script>

<template>
  <section class="maintenance" aria-labelledby="mt-title">
    <div class="mod-head">
      <h1 id="mt-title">{{ t('maintenance.title') }}</h1>
    </div>
    <p class="muted read-only">{{ t('modules.readOnly') }}</p>

    <p v-if="state === 'loading' && !list" class="muted" role="status">{{ t('maintenance.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="fetchList">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="list">
      <fieldset class="mod-filters">
        <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
        <div class="control">
          <span class="seg-label" aria-hidden="true">{{ t('modules.status') }}</span>
          <div class="mod-seg" role="radiogroup" :aria-label="t('modules.status')">
            <label v-for="s in statusOptions" :key="s">
              <input v-model="filter.status" type="radio" name="mt-status" :value="s" />
              {{ t('maintenance.filter.' + s) }}
            </label>
          </div>
        </div>
        <div class="control">
          <label for="mt-type">{{ t('maintenance.type') }}</label>
          <select id="mt-type" v-model="filter.type">
            <option value="all">{{ t('maintenance.allTypes') }}</option>
            <option v-for="ty in typeOptions" :key="ty" :value="ty">{{ t('maintenance.types.' + ty) }}</option>
          </select>
        </div>
        <div v-if="sites.length > 1 || (sites.length && hasEntriesWithoutSite)" class="control">
          <label for="mt-site">{{ t('modules.site') }}</label>
          <select id="mt-site" v-model="siteValue">
            <option value="all">{{ t('modules.allSites') }}</option>
            <option v-for="s in sites" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
            <option v-if="hasEntriesWithoutSite" value="none">{{ t('modules.noSite') }}</option>
          </select>
        </div>
        <div class="control search">
          <label for="mt-search">{{ t('modules.search') }}</label>
          <input id="mt-search" v-model="filter.text" type="search" :placeholder="t('modules.searchPlaceholder')" />
        </div>
      </fieldset>

      <p class="mod-count muted" role="status">{{ t('modules.count', { shown: rows.length, total: all.length }) }}</p>
      <p v-if="!rows.length" class="muted">{{ t('maintenance.empty') }}</p>

      <ul v-else-if="narrow" class="mod-cards">
        <li v-for="e in rows" :key="e.id" class="mod-card" :class="'is-' + maintenanceState(e)">
          <h2>{{ e.title }}</h2>
          <dl class="mod-dl">
            <dt>{{ t('maintenance.col.status') }}</dt>
            <dd><span class="mod-badge" :class="'is-' + maintenanceState(e)">{{ stateLabel(e) }}</span></dd>
            <dt>{{ t('maintenance.col.type') }}</dt>
            <dd>{{ typeLabel(e) }}</dd>
            <dt>{{ t('maintenance.col.period') }}</dt>
            <dd>{{ formatPeriod(e.start, e.end, locale) || t('modules.none') }}</dd>
            <dt>{{ t('maintenance.col.duration') }}</dt>
            <dd>{{ formatDuration(e.durationMinutes, locale) || t('modules.none') }}</dd>
            <dt>{{ t('maintenance.col.impact') }}</dt>
            <dd>{{ e.impact || t('modules.none') }}</dd>
            <dt>{{ t('maintenance.col.site') }}</dt>
            <dd>{{ siteLabel(e) }}</dd>
          </dl>
          <button
            type="button"
            class="mod-toggle"
            :aria-expanded="expanded.has(e.id)"
            :aria-controls="'mt-c-' + e.id"
            :aria-label="t('modules.showDetails', { title: e.title })"
            @click="toggle(e.id)"
          >
            <span class="chevron" aria-hidden="true">›</span>{{ t('modules.details') }}
          </button>
          <div v-if="expanded.has(e.id)" :id="'mt-c-' + e.id" class="mod-details">
            <MaintenanceDetails :entry="e" />
          </div>
        </li>
      </ul>

      <div v-else class="mod-table-wrap">
        <table class="mod-table">
          <caption class="visually-hidden">{{ t('maintenance.title') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('maintenance.col.type') }}</th>
              <th scope="col">{{ t('maintenance.col.title') }}</th>
              <th scope="col">{{ t('maintenance.col.period') }}</th>
              <th scope="col">{{ t('maintenance.col.duration') }}</th>
              <th scope="col">{{ t('maintenance.col.status') }}</th>
              <th scope="col">{{ t('maintenance.col.impact') }}</th>
              <th scope="col">{{ t('maintenance.col.site') }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="e in rows" :key="e.id">
              <tr class="mod-row" :class="'is-' + maintenanceState(e)">
                <td>{{ typeLabel(e) }}</td>
                <th scope="row" class="row-title">
                  <button
                    type="button"
                    class="mod-toggle"
                    :aria-expanded="expanded.has(e.id)"
                    :aria-controls="'mt-d-' + e.id"
                    :title="t('modules.showDetails', { title: e.title })"
                    @click="toggle(e.id)"
                  >
                    <span class="chevron" aria-hidden="true">›</span><span>{{ e.title }}</span>
                  </button>
                </th>
                <td class="nowrap">{{ formatPeriod(e.start, e.end, locale) || t('modules.none') }}</td>
                <td class="nowrap">{{ formatDuration(e.durationMinutes, locale) || t('modules.none') }}</td>
                <td><span class="mod-badge" :class="'is-' + maintenanceState(e)">{{ stateLabel(e) }}</span></td>
                <td class="impact">{{ e.impact || t('modules.none') }}</td>
                <td>{{ siteLabel(e) }}</td>
              </tr>
              <tr v-if="expanded.has(e.id)" :id="'mt-d-' + e.id" class="mod-detail">
                <td colspan="7"><MaintenanceDetails :entry="e" /></td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.read-only {
  font-size: 0.85rem;
  margin: 0 0 1rem;
}

.seg-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 0.15rem;
}

.row-title {
  font-weight: inherit;
  text-align: left;
  padding: 0.5rem 0.6rem;
  border-bottom: 1px solid var(--line);
  vertical-align: top;
  color: inherit;
  font-size: inherit;
  white-space: normal;
  min-width: 12rem;
}

.impact {
  white-space: pre-line;
  min-width: 10rem;
}

.retry {
  margin-left: 0.5rem;
}
</style>
