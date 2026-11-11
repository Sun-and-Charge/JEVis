<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadRegisters, type RegisterKind, type RegisterRow } from '../api/registers'
import RegisterDetails from '../components/registers/RegisterDetails.vue'
import { sortRows, type ColumnContext, type SortDirection } from '../iso/columns'
import { useLoader } from '../iso/useLoader'
import { useNarrow } from '../modules/useNarrow'
import {
  loadColumnChoice,
  registerColumns,
  saveColumnChoice,
  toggleColumnChoice,
  visibleColumnIds,
  emptyChoice,
  type RegisterColumnDef,
  type RegisterEnv,
} from '../registers/columns'
import { registerCsv, registerCsvName } from '../registers/csv'
import { ALL, defaultFilter, filterRows, meterOptions, meterSummary, yearOptions, type RegisterFilter } from '../registers/filter'
import { verificationState } from '../registers/format'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'
import '../styles/registers.css'

/*
 * Meters, base data and equipment (W4.3, view only): one tab per group, a sortable table with column chooser
 * (cards on phone width), search, the meter filters and key figures of the Control Center, CSV export of the
 * visible columns and a detail panel per row.
 */

const props = defineProps<{ kind: RegisterKind }>()
const { t, te, locale } = useI18n()
const auth = useAuthStore()
const narrow = useNarrow()

const { data, state, reload } = useLoader(() => loadRegisters(auth.http, props.kind, locale.value))

const filter = reactive<RegisterFilter>(defaultFilter(null))
const selected = ref<string | null>(null)
const choice = ref(loadColumnChoice(props.kind))
const sortId = ref<string | null>(null)
const sortDir = ref<SortDirection>('asc')
const expanded = ref(new Set<number>())

/* the year of "due until end of" starts with the server's year, once per kind */
let yearSet = false

/* the same view serves all three routes: start over on a new kind; names come in the UI language */
watch(
  () => props.kind,
  (kind) => {
    data.value = null
    yearSet = false
    Object.assign(filter, defaultFilter(null))
    selected.value = null
    choice.value = loadColumnChoice(kind)
    sortId.value = null
    expanded.value = new Set()
    reload()
  },
)
watch(locale, () => reload())

watch(data, (d) => {
  if (d && !yearSet) {
    yearSet = true
    filter.year = defaultFilter(d.today).year
  }
})

const groups = computed(() => data.value?.groups ?? [])
const group = computed(() => groups.value.find((g) => g.key === selected.value) ?? groups.value[0] ?? null)
const meters = computed(() => props.kind === 'meters')
const today = computed(() => data.value?.today ?? new Date().toISOString().slice(0, 10))

const env = computed<RegisterEnv>(() => ({ locale: locale.value, today: today.value, t: (k: string) => t(k), te: (k: string) => te(k) }))
const ctx = computed<ColumnContext>(() => ({ locale: locale.value, t: t as ColumnContext['t'] }))

const columns = computed(() => registerColumns(props.kind, group.value, env.value))
const visibleIds = computed(() => visibleColumnIds(columns.value, choice.value))
const visible = computed(() => columns.value.filter((c) => visibleIds.value.includes(c.id)))

const allRows = computed(() => group.value?.rows ?? [])
const rows = computed(() => filterRows(allRows.value, filter, columns.value, env.value, meters.value))
const sorted = computed(() =>
  sortRows(
    rows.value,
    columns.value.find((c) => c.id === sortId.value),
    sortDir.value,
    ctx.value,
  ),
)
const options = computed(() => meterOptions(allRows.value, env.value))
const summary = computed(() => meterSummary(rows.value, filter.year, env.value))
const years = computed(() => yearOptions(defaultFilter(data.value?.today).year))

/* a new tab has other media, types and locations */
watch(
  () => group.value?.key,
  () => {
    filter.medium = ALL
    filter.type = ALL
    filter.location = ALL
    expanded.value = new Set()
  },
)

const title = computed(() => t('modules.' + props.kind))

function selectTab(key: string) {
  selected.value = key
}

function onTabKey(event: KeyboardEvent, index: number) {
  const list = groups.value
  let next = -1
  if (event.key === 'ArrowRight') next = (index + 1) % list.length
  else if (event.key === 'ArrowLeft') next = (index - 1 + list.length) % list.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = list.length - 1
  if (next < 0) return
  event.preventDefault()
  selectTab(list[next].key)
  const tab = document.getElementById(tabId(list[next].key))
  tab?.focus()
}

const tabId = (key: string) => `reg-${props.kind}-tab-${key}`
const panelId = computed(() => `reg-${props.kind}-panel`)

function toggle(column: RegisterColumnDef) {
  choice.value = toggleColumnChoice(choice.value, column, visibleIds.value.includes(column.id))
  saveColumnChoice(props.kind, choice.value)
}

function resetColumns() {
  choice.value = emptyChoice()
  saveColumnChoice(props.kind, choice.value)
}

function sortBy(id: string) {
  if (sortId.value === id) sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  else {
    sortId.value = id
    sortDir.value = 'asc'
  }
}

function ariaSort(id: string): 'ascending' | 'descending' | 'none' {
  if (sortId.value !== id) return 'none'
  return sortDir.value === 'asc' ? 'ascending' : 'descending'
}

function toggleRow(id: number) {
  const next = new Set(expanded.value)
  if (!next.delete(id)) next.add(id)
  expanded.value = next
}

const cell = (c: RegisterColumnDef, row: RegisterRow) => c.text(row, ctx.value)
const badge = (c: RegisterColumnDef, row: RegisterRow) => c.badge?.(row) ?? null
/* overdue verification: marked also when its column is hidden */
const rowClass = (row: RegisterRow) => (meters.value && verificationState(row, today.value) === 'overdue' ? 'is-overdue' : undefined)

function exportCsv() {
  const csv = registerCsv(visible.value, sorted.value, locale.value)
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = registerCsvName(title.value, groups.value.length > 1 ? group.value?.name : null)
  a.rel = 'noopener'
  a.style.display = 'none'
  document.body.appendChild(a)
  try {
    a.click()
  } finally {
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 30_000)
  }
}

const id = (suffix: string) => `reg-${props.kind}-${suffix}`
const showDetails = (row: RegisterRow) => t('modules.showDetails', { title: row.name })
</script>

<template>
  <section class="iso registers" :aria-labelledby="id('title')">
    <div class="mod-head">
      <h1 :id="id('title')">{{ title }}</h1>
    </div>
    <p class="muted read-only">{{ t('registers.readOnly') }}</p>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('registers.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <p v-if="data && !groups.length" class="muted">{{ t('registers.empty') }}</p>
    <template v-else-if="data && group">
      <div v-if="groups.length > 1" class="reg-tabs" role="tablist" :aria-label="t('registers.groups')">
        <button
          v-for="(g, i) in groups"
          :id="tabId(g.key)"
          :key="g.key"
          type="button"
          role="tab"
          class="reg-tab"
          :aria-selected="g.key === group.key"
          :aria-controls="panelId"
          :tabindex="g.key === group.key ? 0 : -1"
          @click="selectTab(g.key)"
          @keydown="onTabKey($event, i)"
        >
          {{ g.name }} <span class="reg-tab-count">{{ g.rows.length }}</span>
        </button>
      </div>

      <div :id="panelId" :role="groups.length > 1 ? 'tabpanel' : undefined" :aria-labelledby="groups.length > 1 ? tabId(group.key) : undefined">
        <fieldset class="mod-filters iso-filters">
          <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
          <template v-if="meters">
            <div v-if="options.media.length > 1" class="control">
              <label :for="id('medium')">{{ t('registers.col.medium') }}</label>
              <select :id="id('medium')" v-model="filter.medium">
                <option :value="ALL">{{ t('iso.filter.all') }}</option>
                <option v-for="m in options.media" :key="m" :value="m">{{ m }}</option>
              </select>
            </div>
            <div v-if="options.types.length" class="control">
              <label :for="id('type')">{{ t('registers.filter.type') }}</label>
              <select :id="id('type')" v-model="filter.type">
                <option :value="ALL">{{ t('iso.filter.all') }}</option>
                <option v-for="m in options.types" :key="m" :value="m">{{ m }}</option>
              </select>
            </div>
            <div v-if="options.locations.length" class="control">
              <label :for="id('location')">{{ t('registers.filter.location') }}</label>
              <select :id="id('location')" v-model="filter.location">
                <option :value="ALL">{{ t('iso.filter.all') }}</option>
                <option v-for="m in options.locations" :key="m" :value="m">{{ m }}</option>
              </select>
            </div>
            <div class="control reg-overdue">
              <label :for="id('year')">{{ t('registers.filter.verification') }}</label>
              <div class="reg-overdue-row">
                <label class="reg-check">
                  <input v-model="filter.overdueOnly" type="checkbox" />
                  {{ t('registers.filter.overdue') }}
                </label>
                <select :id="id('year')" v-model.number="filter.year">
                  <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
                </select>
              </div>
            </div>
          </template>
          <div class="control search">
            <label :for="id('search')">{{ t('modules.search') }}</label>
            <input :id="id('search')" v-model="filter.text" type="search" :placeholder="t('registers.searchPlaceholder.' + kind)" />
          </div>
        </fieldset>

        <section v-if="meters" class="iso-stats" :aria-labelledby="id('stats')">
          <h2 :id="id('stats')" class="iso-stats-title">{{ t('registers.summary.title') }}</h2>
          <dl class="iso-tiles">
            <div class="iso-tile">
              <dt>{{ t('registers.summary.total') }}</dt>
              <dd class="iso-big">{{ summary.total }}</dd>
            </div>
            <div class="iso-tile" :class="{ 'reg-tile-alert': summary.overdue > 0 }">
              <dt>{{ t('registers.summary.overdue', { year: filter.year }) }}</dt>
              <dd class="iso-big">{{ summary.overdue }}</dd>
            </div>
            <div v-if="summary.perMedium.length" class="iso-tile">
              <dt>{{ t('registers.summary.perMedium') }}</dt>
              <dd>
                <ul class="iso-status-list">
                  <li v-for="m in summary.perMedium" :key="m.name">
                    <span>{{ m.name }}</span><span class="num-text">{{ m.count }}</span>
                  </li>
                </ul>
              </dd>
            </div>
            <div v-if="summary.perType.length" class="iso-tile">
              <dt>{{ t('registers.summary.perType') }}</dt>
              <dd>
                <ul class="iso-status-list">
                  <li v-for="m in summary.perType" :key="m.name">
                    <span>{{ m.name }}</span><span class="num-text">{{ m.count }}</span>
                  </li>
                </ul>
              </dd>
            </div>
          </dl>
        </section>

        <div class="iso-toolbar">
          <p class="mod-count muted" role="status">{{ t('modules.count', { shown: rows.length, total: allRows.length }) }}</p>
          <div class="reg-actions">
            <button type="button" class="reg-export" :disabled="!rows.length" :aria-label="t('registers.export.csvLabel', { name: group.name })" @click="exportCsv">
              {{ t('registers.export.csv') }}
            </button>
            <details class="iso-columns">
              <summary>{{ t('iso.columns.title') }}</summary>
              <div class="iso-columns-panel">
                <fieldset>
                  <legend class="visually-hidden">{{ t('iso.columns.title') }}</legend>
                  <label v-for="c in columns" :key="c.id">
                    <input
                      type="checkbox"
                      :checked="visibleIds.includes(c.id)"
                      :disabled="visibleIds.length === 1 && visibleIds.includes(c.id)"
                      @change="toggle(c)"
                    />
                    {{ c.label }}
                  </label>
                </fieldset>
                <button type="button" class="btn-link" @click="resetColumns">{{ t('iso.columns.reset') }}</button>
              </div>
            </details>
          </div>
        </div>

        <p v-if="!allRows.length" class="muted">{{ t('registers.emptyGroup') }}</p>
        <p v-else-if="!rows.length" class="muted">{{ t('registers.noMatch') }}</p>

        <ul v-else-if="narrow" class="mod-cards">
          <li v-for="row in sorted" :key="row.id" class="mod-card" :class="rowClass(row)">
            <h2>{{ row.name }}</h2>
            <dl class="mod-dl">
              <template v-for="c in visible.filter((v) => v.id !== 'name')" :key="c.id">
                <dt>{{ c.label }}</dt>
                <dd>
                  <span v-if="badge(c, row)" class="mod-badge" :class="'is-' + badge(c, row)">{{ cell(c, row) }}</span>
                  <template v-else>{{ cell(c, row) || t('modules.none') }}</template>
                </dd>
              </template>
            </dl>
            <button
              type="button"
              class="mod-toggle"
              :aria-expanded="expanded.has(row.id)"
              :aria-controls="id('c-' + row.id)"
              :aria-label="showDetails(row)"
              @click="toggleRow(row.id)"
            >
              <span class="chevron" aria-hidden="true">›</span>{{ t('modules.details') }}
            </button>
            <div v-if="expanded.has(row.id)" :id="id('c-' + row.id)" class="mod-details">
              <RegisterDetails :kind="kind" :row="row" :columns="group.columns" :today="today" />
            </div>
          </li>
        </ul>

        <div v-else class="mod-table-wrap">
          <table class="mod-table iso-table">
            <caption class="visually-hidden">{{ group.name }}</caption>
            <thead>
              <tr>
                <th scope="col" class="iso-toggle-col"><span class="visually-hidden">{{ t('modules.details') }}</span></th>
                <th v-for="c in visible" :key="c.id" scope="col" :aria-sort="ariaSort(c.id)" :class="{ num: c.numeric }">
                  <button type="button" class="iso-sort" @click="sortBy(c.id)">
                    {{ c.label }}<span class="iso-sort-mark" aria-hidden="true">{{ sortId === c.id ? (sortDir === 'asc' ? '▲' : '▼') : '' }}</span>
                  </button>
                </th>
              </tr>
            </thead>
            <tbody>
              <template v-for="row in sorted" :key="row.id">
                <tr class="mod-row" :class="rowClass(row)">
                  <td class="iso-toggle-col">
                    <button
                      type="button"
                      class="mod-toggle"
                      :aria-expanded="expanded.has(row.id)"
                      :aria-controls="id('d-' + row.id)"
                      :aria-label="showDetails(row)"
                      :title="showDetails(row)"
                      @click="toggleRow(row.id)"
                    >
                      <span class="chevron" aria-hidden="true">›</span>
                    </button>
                  </td>
                  <td v-for="c in visible" :key="c.id" :class="{ nowrap: c.nowrap, num: c.numeric, 'iso-text': !c.nowrap && !c.numeric }">
                    <span v-if="badge(c, row)" class="mod-badge" :class="'is-' + badge(c, row)">{{ cell(c, row) }}</span>
                    <template v-else>{{ cell(c, row) }}</template>
                  </td>
                </tr>
                <tr v-if="expanded.has(row.id)" :id="id('d-' + row.id)" class="mod-detail">
                  <td :colspan="visible.length + 1">
                    <RegisterDetails :kind="kind" :row="row" :columns="group.columns" :today="today" />
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </section>
</template>
