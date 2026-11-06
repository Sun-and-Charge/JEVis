<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadAppointments, type Appointment, type AppointmentList } from '../api/modules'
import { localName } from '../api/navigation'
import AppointmentDetails from '../components/modules/AppointmentDetails.vue'
import { DEFAULT_APPOINTMENT_FILTER, dueState, filterAppointments, sortAppointments, type AppointmentFilter } from '../modules/appointments'
import { formatLocal, formatLocalDate } from '../modules/dates'
import { siteOptions, type SiteFilter } from '../modules/search'
import { useNarrow } from '../modules/useNarrow'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const narrow = useNarrow()

const list = shallowRef<AppointmentList | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const filter = reactive<AppointmentFilter>({ ...DEFAULT_APPOINTMENT_FILTER })
const expanded = ref(new Set<number>())
let seq = 0

async function fetchList() {
  const current = ++seq
  state.value = 'loading'
  try {
    const result = await loadAppointments(auth.http)
    if (current !== seq) return
    list.value = result
    state.value = 'ready'
  } catch {
    if (current !== seq) return
    state.value = 'error'
  }
}

onMounted(fetchList)

const all = computed(() => list.value?.appointments ?? [])
const sites = computed(() => siteOptions(all.value, locale.value))
const hasEntriesWithoutSite = computed(() => all.value.some((a) => !a.site))
const rows = computed(() => sortAppointments(filterAppointments(all.value, filter, locale.value)))

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

function stateLabel(a: Appointment): string {
  return t('appointments.state.' + dueState(a))
}

function recurrenceLabel(a: Appointment): string {
  if (!a.recurrence) return t('modules.none')
  const key = 'appointments.recurrence.' + a.recurrence
  return te(key) ? t(key) : a.recurrence
}

function reminderLabel(a: Appointment): string {
  if (a.reminderDays == null && !a.reminderDay) return t('modules.none')
  const parts: string[] = []
  if (a.reminderDays != null) parts.push(t('appointments.reminderDays', Math.max(0, a.reminderDays)))
  if (a.reminderDay) parts.push(t('appointments.reminderOn', { date: formatLocalDate(a.reminderDay, locale.value) }))
  return parts.join(', ')
}

function linkedLabel(a: Appointment): string {
  const o = a.linkedObject
  if (!o) return t('modules.none')
  if (!o.readable) return t('appointments.noAccess', { id: o.id })
  return o.name || `#${o.id}`
}

function siteLabel(a: Appointment): string {
  return a.site ? localName(a.site, locale.value) : t('modules.none')
}

const statusOptions = ['open', 'done', 'all'] as const
</script>

<template>
  <section class="appointments" aria-labelledby="ap-title">
    <div class="mod-head">
      <h1 id="ap-title">{{ t('appointments.title') }}</h1>
      <span v-if="list?.today" class="muted">{{ t('appointments.today', { date: formatLocalDate(list.today, locale) }) }}</span>
    </div>
    <p class="muted read-only">{{ t('modules.readOnly') }}</p>

    <p v-if="state === 'loading' && !list" class="muted" role="status">{{ t('appointments.loading') }}</p>
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
              <input v-model="filter.status" type="radio" name="ap-status" :value="s" />
              {{ t('appointments.filter.' + s) }}
            </label>
          </div>
        </div>
        <div v-if="sites.length > 1 || (sites.length && hasEntriesWithoutSite)" class="control">
          <label for="ap-site">{{ t('modules.site') }}</label>
          <select id="ap-site" v-model="siteValue">
            <option value="all">{{ t('modules.allSites') }}</option>
            <option v-for="s in sites" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
            <option v-if="hasEntriesWithoutSite" value="none">{{ t('modules.noSite') }}</option>
          </select>
        </div>
        <div class="control search">
          <label for="ap-search">{{ t('modules.search') }}</label>
          <input id="ap-search" v-model="filter.text" type="search" :placeholder="t('modules.searchPlaceholder')" />
        </div>
      </fieldset>

      <p class="mod-count muted" role="status">{{ t('modules.count', { shown: rows.length, total: all.length }) }}</p>
      <p v-if="!rows.length" class="muted">{{ t('appointments.empty') }}</p>

      <ul v-else-if="narrow" class="mod-cards">
        <li v-for="a in rows" :key="a.id" class="mod-card" :class="'is-' + dueState(a)">
          <h2>{{ a.title }}</h2>
          <dl class="mod-dl">
            <dt>{{ t('appointments.col.status') }}</dt>
            <dd><span class="mod-badge" :class="'is-' + dueState(a)">{{ stateLabel(a) }}</span></dd>
            <dt>{{ t('appointments.col.due') }}</dt>
            <dd>{{ formatLocal(a.due, locale) || t('modules.none') }}</dd>
            <dt>{{ t('appointments.col.recurrence') }}</dt>
            <dd>{{ recurrenceLabel(a) }}</dd>
            <dt>{{ t('appointments.col.reminder') }}</dt>
            <dd>{{ reminderLabel(a) }}</dd>
            <dt>{{ t('appointments.col.site') }}</dt>
            <dd>{{ siteLabel(a) }}</dd>
            <dt>{{ t('appointments.col.linkedObject') }}</dt>
            <dd>{{ linkedLabel(a) }}</dd>
            <dt>{{ t('appointments.col.lastMail') }}</dt>
            <dd>{{ formatLocal(a.lastMail, locale) || t('modules.none') }}</dd>
          </dl>
          <button
            type="button"
            class="mod-toggle"
            :aria-expanded="expanded.has(a.id)"
            :aria-controls="'ap-c-' + a.id"
            :aria-label="t('modules.showDetails', { title: a.title })"
            @click="toggle(a.id)"
          >
            <span class="chevron" aria-hidden="true">›</span>{{ t('modules.details') }}
          </button>
          <div v-if="expanded.has(a.id)" :id="'ap-c-' + a.id" class="mod-details">
            <AppointmentDetails :entry="a" />
          </div>
        </li>
      </ul>

      <div v-else class="mod-table-wrap">
        <table class="mod-table">
          <caption class="visually-hidden">{{ t('appointments.title') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('appointments.col.title') }}</th>
              <th scope="col">{{ t('appointments.col.due') }}</th>
              <th scope="col">{{ t('appointments.col.recurrence') }}</th>
              <th scope="col">{{ t('appointments.col.reminder') }}</th>
              <th scope="col">{{ t('appointments.col.status') }}</th>
              <th scope="col">{{ t('appointments.col.site') }}</th>
              <th scope="col">{{ t('appointments.col.linkedObject') }}</th>
              <th scope="col">{{ t('appointments.col.lastMail') }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="a in rows" :key="a.id">
              <tr class="mod-row" :class="'is-' + dueState(a)">
                <th scope="row" class="row-title">
                  <button
                    type="button"
                    class="mod-toggle"
                    :aria-expanded="expanded.has(a.id)"
                    :aria-controls="'ap-d-' + a.id"
                    :title="t('modules.showDetails', { title: a.title })"
                    @click="toggle(a.id)"
                  >
                    <span class="chevron" aria-hidden="true">›</span><span>{{ a.title }}</span>
                  </button>
                </th>
                <td class="nowrap">{{ formatLocal(a.due, locale) || t('modules.none') }}</td>
                <td>{{ recurrenceLabel(a) }}</td>
                <td>{{ reminderLabel(a) }}</td>
                <td><span class="mod-badge" :class="'is-' + dueState(a)">{{ stateLabel(a) }}</span></td>
                <td>{{ siteLabel(a) }}</td>
                <td :class="{ muted: a.linkedObject && !a.linkedObject.readable }">{{ linkedLabel(a) }}</td>
                <td class="nowrap">{{ formatLocal(a.lastMail, locale) || t('modules.none') }}</td>
              </tr>
              <tr v-if="expanded.has(a.id)" :id="'ap-d-' + a.id" class="mod-detail">
                <td colspan="8"><AppointmentDetails :entry="a" /></td>
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

.retry {
  margin-left: 0.5rem;
}
</style>
