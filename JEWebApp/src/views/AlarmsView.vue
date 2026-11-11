<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  DEFAULT_ALARM_SORT,
  alarmColumns,
  alarmOperator,
  alarmSites,
  alarmTypeOptions,
  alarmsCsv,
  checkAlarmRange,
  checkedTitle,
  defaultAlarmFilter,
  defaultAlarmRange,
  filterAlarms,
  valueWithUnit,
  type AlarmFilter,
} from '../alarms/alarms'
import { formatDateTime } from '../analysis/format'
import { browserTimeZone } from '../analysis/settings'
import { ALARM_STATES, loadAlarms, type Alarm, type AlarmState } from '../api/alarms'
import AlarmDetails from '../components/alarms/AlarmDetails.vue'
import { saveBlob } from '../iso/download'
import { sortRows, type ColumnContext, type SortDirection } from '../iso/columns'
import { useLoader } from '../iso/useLoader'
import type { SiteFilter } from '../modules/search'
import { useNarrow } from '../modules/useNarrow'
import { notesCsvName, rowFullName, toggleTag } from '../notes/notes'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'
import '../styles/notes.css'
import '../styles/alarms.css'

/*
 * Alarms (W4.5, view only) like the Control Center's Alarm Plugin: alarms logged by JEAlarm in a time frame, open /
 * confirmed / all, search, type and site filter, sortable table (cards on phone width), CSV export and a detail
 * panel with the curve of the data row around the alarm and its limit.
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const narrow = useNarrow()

const range = reactive(defaultAlarmRange())
const rangeCheck = computed(() => checkAlarmRange(range.from, range.to))
const alarmState = ref<AlarmState>('open')

/* the request of the last load (shown time frame, CSV name) */
let requested = { ...range }
const { data, state, reload } = useLoader(() => {
  requested = { ...range }
  return loadAlarms(auth.http, { from: range.from, to: range.to, state: alarmState.value, timeZone: browserTimeZone(), lang: locale.value })
})

/* a valid new time frame, state or language loads again; an invalid time frame keeps the shown alarms */
watch([() => range.from, () => range.to, alarmState, locale], () => {
  if (rangeCheck.value === 'ok') reload()
})

function resetRange() {
  Object.assign(range, defaultAlarmRange())
}

const filter = reactive<AlarmFilter>(defaultAlarmFilter())
const sortId = ref(DEFAULT_ALARM_SORT.id)
const sortDir = ref<SortDirection>(DEFAULT_ALARM_SORT.dir)
const selected = ref<string | null>(null)

const noAccess = computed(() => t('alarms.noAccess'))
const formatTime = (ms: number) => formatDateTime(ms, locale.value)
const stateName = (checked: boolean) => t(checked ? 'alarms.state.checked' : 'alarms.state.open')
const columns = computed(() => alarmColumns({ noAccess: noAccess.value, formatTime, locale: locale.value, stateName }))
const ctx = computed<ColumnContext>(() => ({ locale: locale.value, t: t as ColumnContext['t'] }))

const all = computed<Alarm[]>(() => data.value?.alarms ?? [])
const rows = computed(() => filterAlarms(all.value, filter, locale.value))
const sorted = computed(() =>
  sortRows(
    rows.value,
    columns.value.find((c) => c.id === sortId.value),
    sortDir.value,
    ctx.value,
  ),
)
const types = computed(() => alarmTypeOptions(all.value, filter.types))
const sites = computed(() => alarmSites(all.value, locale.value))
const hasAlarmsWithoutSite = computed(() => all.value.some((a) => !a.site))

/* types or sites that are gone after a new load leave the filter */
watch(data, () => {
  const known = new Set(alarmTypeOptions(all.value).map((x) => x.id))
  filter.types = filter.types.filter((id) => known.has(id))
  if (typeof filter.site === 'number' && !sites.value.some((s) => s.id === filter.site)) filter.site = 'all'
  if (selected.value && !all.value.some((a) => a.id === selected.value)) selected.value = null
})

const siteValue = computed({
  get: () => String(filter.site),
  set: (v: string) => {
    filter.site = (v === 'all' || v === 'none' ? v : Number(v)) as SiteFilter
  },
})

function sortBy(id: string) {
  if (sortId.value === id) sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  else {
    sortId.value = id
    sortDir.value = id === 'date' ? 'desc' : 'asc'
  }
}

function ariaSort(id: string): 'ascending' | 'descending' | 'none' {
  if (sortId.value !== id) return 'none'
  return sortDir.value === 'asc' ? 'ascending' : 'descending'
}

function toggle(id: string) {
  selected.value = selected.value === id ? null : id
}

function toggleTypeFilter(id: string) {
  filter.types = toggleTag(filter.types, id)
}

function exportCsv() {
  const h = (id: string) => t('alarms.col.' + id)
  const csv = alarmsCsv(
    sorted.value,
    {
      date: h('date'),
      site: h('site'),
      configuration: h('configuration'),
      path: h('path'),
      row: h('row'),
      isValue: h('isValue'),
      operator: h('operator'),
      shouldBeValue: h('shouldBeValue'),
      tolerance: h('tolerance'),
      unit: h('unit'),
      type: h('type'),
      alarm: h('alarm'),
      status: h('status'),
      checkedBy: h('checkedBy'),
      checkedAt: h('checkedAt'),
    },
    { noAccess: noAccess.value, stateName },
    locale.value,
  )
  saveBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), notesCsvName(t('alarms.title'), requested.from, requested.to))
}

const cell = (id: string, a: Alarm) => columns.value.find((c) => c.id === id)?.text(a, ctx.value) ?? ''
const rowText = (a: Alarm) => rowFullName(a, noAccess.value)
const showDetails = (a: Alarm) => t('modules.showDetails', { title: `${cell('date', a)} ${a.configuration?.name ?? ''}`.trim() })
const domId = (a: Alarm, suffix: string) => `alarm-${a.id}-${suffix}`
const isChecked = (a: Alarm) => !!a.configuration?.checked
const statusTitle = (a: Alarm) => checkedTitle(a, formatTime) || undefined
const withUnit = (v: number | null, a: Alarm) => valueWithUnit(v, a.unit, locale.value)
const tableColumns = ['date', 'site', 'configuration', 'row', 'isValue', 'operator', 'shouldBeValue', 'tolerance', 'type', 'alarm', 'status'] as const
const numericColumns = new Set(['isValue', 'shouldBeValue', 'tolerance'])
</script>

<template>
  <section class="iso notes alarms" aria-labelledby="alarms-title">
    <div class="mod-head">
      <h1 id="alarms-title">{{ t('alarms.title') }}</h1>
    </div>
    <p class="muted read-only">{{ t('alarms.readOnly') }}</p>

    <fieldset class="mod-filters iso-filters notes-range">
      <legend class="visually-hidden">{{ t('alarms.range.label') }}</legend>
      <div class="control">
        <label for="alarms-from">{{ t('alarms.range.from') }}</label>
        <input
          id="alarms-from"
          v-model="range.from"
          type="date"
          :max="range.to || undefined"
          :aria-invalid="rangeCheck !== 'ok'"
          aria-describedby="alarms-range-hint alarms-range-msg"
        />
      </div>
      <div class="control">
        <label for="alarms-to">{{ t('alarms.range.to') }}</label>
        <input
          id="alarms-to"
          v-model="range.to"
          type="date"
          :min="range.from || undefined"
          :aria-invalid="rangeCheck !== 'ok'"
          aria-describedby="alarms-range-hint alarms-range-msg"
        />
      </div>
      <div class="control">
        <button type="button" class="notes-btn notes-reset" @click="resetRange">{{ t('alarms.range.reset') }}</button>
      </div>
      <div class="control">
        <span class="seg-label" aria-hidden="true">{{ t('alarms.state.label') }}</span>
        <div class="mod-seg alarms-state" role="radiogroup" :aria-label="t('alarms.state.label')">
          <label v-for="s in ALARM_STATES" :key="s">
            <input v-model="alarmState" type="radio" name="alarms-state" :value="s" />
            {{ t('alarms.state.' + s) }}
          </label>
        </div>
      </div>
      <p id="alarms-range-hint" class="muted alarms-hint">{{ t('alarms.rangeHint') }}</p>
      <p id="alarms-range-msg" class="notes-range-msg" :class="{ 'iso-error': rangeCheck !== 'ok' }" role="status">
        <template v-if="rangeCheck !== 'ok'">{{ t('alarms.range.' + rangeCheck) }}</template>
      </p>
    </fieldset>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('alarms.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="data">
      <fieldset class="mod-filters iso-filters">
        <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
        <div class="control search">
          <label for="alarms-search">{{ t('modules.search') }}</label>
          <input id="alarms-search" v-model="filter.text" type="search" :placeholder="t('alarms.searchPlaceholder')" />
        </div>
        <div v-if="sites.length > 1 || (sites.length && hasAlarmsWithoutSite)" class="control">
          <label for="alarms-site">{{ t('modules.site') }}</label>
          <select id="alarms-site" v-model="siteValue">
            <option value="all">{{ t('modules.allSites') }}</option>
            <option v-for="s in sites" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
            <option v-if="hasAlarmsWithoutSite" value="none">{{ t('modules.noSite') }}</option>
          </select>
        </div>
      </fieldset>

      <div v-if="types.length" class="notes-tags" role="group" :aria-label="t('alarms.typeFilter')">
        <span class="seg-label" aria-hidden="true">{{ t('alarms.typeFilter') }}</span>
        <button type="button" class="notes-chip" :aria-pressed="!filter.types.length" @click="filter.types = []">{{ t('alarms.clearTypes') }}</button>
        <button
          v-for="type in types"
          :key="type.id"
          type="button"
          class="notes-chip"
          :aria-pressed="filter.types.includes(type.id)"
          @click="toggleTypeFilter(type.id)"
        >
          {{ type.name }} <span class="notes-chip-count">{{ type.count }}</span>
        </button>
      </div>

      <div v-if="data.truncated" class="alert notes-truncated" role="note">{{ t('alarms.truncated', { n: all.length }) }}</div>

      <div class="iso-toolbar">
        <p class="mod-count muted" role="status">{{ t('modules.count', { shown: rows.length, total: all.length }) }}</p>
        <button type="button" class="notes-btn" :disabled="!rows.length" :aria-label="t('alarms.export.csvLabel')" @click="exportCsv">
          {{ t('alarms.export.csv') }}
        </button>
      </div>

      <p v-if="!all.length" class="muted">{{ t('alarms.empty') }}</p>
      <p v-else-if="!rows.length" class="muted">{{ t('alarms.noMatch') }}</p>

      <ul v-else-if="narrow" class="mod-cards">
        <li v-for="a in sorted" :key="a.id" class="mod-card notes-card alarms-card">
          <h2>{{ cell('date', a) }}</h2>
          <p class="alarms-card-config">
            {{ a.configuration?.name }}
            <span class="mod-badge alarm-state" :class="isChecked(a) ? 'is-checked' : 'is-open'" :title="statusTitle(a)">{{ stateName(isChecked(a)) }}</span>
          </p>
          <dl class="mod-dl">
            <dt>{{ t('alarms.col.row') }}</dt>
            <dd :class="{ muted: a.row && !a.row.readable }">{{ rowText(a) || t('modules.none') }}</dd>
            <template v-if="a.site">
              <dt>{{ t('alarms.col.site') }}</dt>
              <dd>{{ a.site.name }}</dd>
            </template>
            <dt>{{ t('alarms.col.isValue') }}</dt>
            <dd>
              {{ withUnit(a.isValue, a) || t('modules.none') }}
              <template v-if="a.shouldBeValue != null">{{ alarmOperator(a.operator) }} {{ withUnit(a.shouldBeValue, a) }}</template>
            </dd>
            <template v-if="a.tolerance != null">
              <dt>{{ t('alarms.col.tolerance') }}</dt>
              <dd>{{ cell('tolerance', a) }}</dd>
            </template>
            <dt>{{ t('alarms.col.type') }}</dt>
            <dd>{{ cell('type', a) || t('modules.none') }}</dd>
            <dt>{{ t('alarms.col.alarm') }}</dt>
            <dd>{{ a.logValueName || t('modules.none') }}</dd>
          </dl>
          <button
            type="button"
            class="mod-toggle"
            :aria-expanded="selected === a.id"
            :aria-controls="domId(a, 'c')"
            :aria-label="showDetails(a)"
            @click="toggle(a.id)"
          >
            <span class="chevron" aria-hidden="true">›</span>{{ t('modules.details') }}
          </button>
          <div v-if="selected === a.id" :id="domId(a, 'c')" class="mod-details">
            <AlarmDetails :alarm="a" />
          </div>
        </li>
      </ul>

      <div v-else class="mod-table-wrap">
        <table class="mod-table iso-table notes-table alarms-table">
          <caption class="visually-hidden">{{ t('alarms.title') }}</caption>
          <thead>
            <tr>
              <th scope="col" class="iso-toggle-col"><span class="visually-hidden">{{ t('modules.details') }}</span></th>
              <th v-for="c in tableColumns" :key="c" scope="col" :aria-sort="ariaSort(c)" :class="{ num: numericColumns.has(c) }">
                <button type="button" class="iso-sort" @click="sortBy(c)">
                  {{ t('alarms.col.' + c) }}<span class="iso-sort-mark" aria-hidden="true">{{ sortId === c ? (sortDir === 'asc' ? '▲' : '▼') : '' }}</span>
                </button>
              </th>
            </tr>
          </thead>
          <tbody>
            <template v-for="a in sorted" :key="a.id">
              <tr class="mod-row notes-row alarms-row" :class="{ 'is-selected': selected === a.id }" @click="toggle(a.id)">
                <td class="iso-toggle-col">
                  <button
                    type="button"
                    class="mod-toggle"
                    :aria-expanded="selected === a.id"
                    :aria-controls="domId(a, 'd')"
                    :aria-label="showDetails(a)"
                    :title="showDetails(a)"
                    @click.stop="toggle(a.id)"
                  >
                    <span class="chevron" aria-hidden="true">›</span>
                  </button>
                </td>
                <td class="nowrap">{{ cell('date', a) }}</td>
                <td class="iso-text">{{ a.site?.name ?? '' }}</td>
                <td class="iso-text">{{ a.configuration?.name ?? '' }}</td>
                <td class="iso-text notes-row-name" :class="{ muted: a.row && !a.row.readable }">
                  <template v-if="a.row?.readable">
                    <span class="notes-path">{{ a.row.path }}</span>
                    <span>{{ a.row.name || '#' + a.row.id }}</span>
                  </template>
                  <template v-else>{{ rowText(a) }}</template>
                </td>
                <td class="num nowrap">{{ withUnit(a.isValue, a) }}</td>
                <td class="alarms-op">{{ cell('operator', a) }}</td>
                <td class="num nowrap">{{ withUnit(a.shouldBeValue, a) }}</td>
                <td class="num nowrap">{{ cell('tolerance', a) }}</td>
                <td class="iso-text">{{ cell('type', a) }}</td>
                <td class="iso-text">{{ a.logValueName }}</td>
                <td>
                  <span class="mod-badge alarm-state" :class="isChecked(a) ? 'is-checked' : 'is-open'" :title="statusTitle(a)">{{ stateName(isChecked(a)) }}</span>
                </td>
              </tr>
              <tr v-if="selected === a.id" :id="domId(a, 'd')" class="mod-detail">
                <td :colspan="tableColumns.length + 1"><AlarmDetails :alarm="a" /></td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
