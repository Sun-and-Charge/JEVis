<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts" generic="R extends { plan: PlanRef; item: { id: number } }">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { localName } from '../../api/navigation'
import { loadColumnPrefs, orderColumns, saveColumnPrefs, sortRows, toggleColumn, type ColumnContext, type ColumnDef, type SortDirection } from '../../iso/columns'
import type { PlanFilter, PlanRef } from '../../iso/filter'
import { useNarrow } from '../../modules/useNarrow'

/*
 * Generic list of the ISO modules (action plans, nonconformities, legal register): filters (plan, module specific
 * ones in the slot "filters", date range on a selectable date field, search), key figures (slot "stats"), a
 * sortable table with column chooser (cards on phone width) and a detail panel per row (slot "detail").
 */

const props = defineProps<{
  /** id prefix and key of the stored columns */
  module: string
  plans: readonly PlanRef[]
  /** filtered rows */
  rows: readonly R[]
  total: number
  columns: readonly ColumnDef<R>[]
  defaultColumns: readonly string[]
  dateFields: readonly { value: string; label: string }[]
  rowTitle: (row: R) => string
  rowClass?: (row: R) => string | undefined
  emptyText: string
}>()

const plan = defineModel<PlanFilter>('plan', { required: true })
const text = defineModel<string>('text', { required: true })
const dateField = defineModel<string>('dateField', { required: true })
const from = defineModel<string>('from', { required: true })
const until = defineModel<string>('until', { required: true })

defineSlots<{
  filters?: () => unknown
  stats?: () => unknown
  detail?: (props: { row: R }) => unknown
}>()

const { t, locale } = useI18n()
const narrow = useNarrow()
const ctx = computed<ColumnContext>(() => ({ locale: locale.value, t: t as ColumnContext['t'] }))

const showPlan = computed(() => props.plans.length > 1 && plan.value === 'all')
const planColumn: ColumnDef<R> = {
  id: 'plan',
  label: 'iso.col.plan',
  text: (r, c) => (r.plan.site ? `${r.plan.name} (${localName(r.plan.site, c.locale)})` : r.plan.name),
}

const allIds = computed(() => props.columns.map((c) => c.id))
const visibleIds = ref<string[]>(loadColumnPrefs(props.module, allIds.value, props.defaultColumns))
const visible = computed<ColumnDef<R>[]>(() => {
  const ids = orderColumns(visibleIds.value, allIds.value)
  const cols = props.columns.filter((c) => ids.includes(c.id))
  return showPlan.value ? [planColumn, ...cols] : cols
})

function toggle(id: string) {
  visibleIds.value = toggleColumn(visibleIds.value, id, allIds.value)
  saveColumnPrefs(props.module, visibleIds.value, props.defaultColumns)
}

function resetColumns() {
  visibleIds.value = orderColumns(props.defaultColumns, allIds.value)
  saveColumnPrefs(props.module, visibleIds.value, props.defaultColumns)
}

const sortId = ref<string | null>(null)
const sortDir = ref<SortDirection>('asc')

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

const sorted = computed(() => {
  const column = [planColumn, ...props.columns].find((c) => c.id === sortId.value)
  return sortRows(props.rows, column, sortDir.value, ctx.value)
})

const expanded = ref(new Set<number>())
function toggleRow(id: number) {
  const next = new Set(expanded.value)
  if (!next.delete(id)) next.add(id)
  expanded.value = next
}

/** Opens the detail panel of an item (e.g. a new one) and moves the focus to its toggle once it is shown. */
async function expand(id: number) {
  if (!expanded.value.has(id)) expanded.value = new Set([...expanded.value, id])
  await nextTick()
  const panel = document.getElementById(`${props.module}-d-${id}`) ?? document.getElementById(`${props.module}-c-${id}`)
  const toggle = document.querySelector<HTMLElement>(`[aria-controls="${props.module}-d-${id}"], [aria-controls="${props.module}-c-${id}"]`)
  toggle?.focus()
  panel?.scrollIntoView?.({ block: 'nearest' })
}

defineExpose({ expand })

/* collapse panels of rows that are filtered out */
watch(
  () => props.rows,
  (rows) => {
    if (!expanded.value.size) return
    const ids = new Set(rows.map((r) => r.item.id))
    const next = new Set([...expanded.value].filter((id) => ids.has(id)))
    if (next.size !== expanded.value.size) expanded.value = next
  },
)

const planValue = computed({
  get: () => String(plan.value),
  set: (v: string) => {
    plan.value = v === 'all' ? 'all' : Number(v)
  },
})

const hasDateRange = computed(() => !!from.value || !!until.value)
function clearDates() {
  from.value = ''
  until.value = ''
}

function cell(column: ColumnDef<R>, row: R): string {
  return column.text(row, ctx.value)
}

function href(column: ColumnDef<R>, row: R): string | null {
  return column.href ? column.href(row) : null
}

const id = (suffix: string) => `${props.module}-${suffix}`
</script>

<template>
  <div class="iso-list">
    <fieldset class="mod-filters iso-filters">
      <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
      <div v-if="plans.length > 1" class="control">
        <label :for="id('plan')">{{ t('iso.filter.plan') }}</label>
        <select :id="id('plan')" v-model="planValue">
          <option value="all">{{ t('iso.filter.allPlans') }}</option>
          <option v-for="p in plans" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
        </select>
      </div>
      <slot name="filters" />
      <div class="control">
        <label :for="id('datefield')">{{ t('iso.filter.dateField') }}</label>
        <select :id="id('datefield')" v-model="dateField">
          <option v-for="f in dateFields" :key="f.value" :value="f.value">{{ f.label }}</option>
        </select>
      </div>
      <div class="control">
        <label :for="id('from')">{{ t('iso.filter.from') }}</label>
        <input :id="id('from')" v-model="from" type="date" :max="until || undefined" />
      </div>
      <div class="control">
        <label :for="id('until')">{{ t('iso.filter.until') }}</label>
        <input :id="id('until')" v-model="until" type="date" :min="from || undefined" />
      </div>
      <div v-if="hasDateRange" class="control">
        <button type="button" class="btn-link iso-clear" @click="clearDates">{{ t('iso.filter.clearDates') }}</button>
      </div>
      <div class="control search">
        <label :for="id('search')">{{ t('modules.search') }}</label>
        <input :id="id('search')" v-model="text" type="search" :placeholder="t('iso.filter.searchPlaceholder')" />
      </div>
    </fieldset>

    <slot name="stats" />

    <div class="iso-toolbar">
      <p class="mod-count muted" role="status">{{ t('modules.count', { shown: rows.length, total }) }}</p>
      <details class="iso-columns">
        <summary>{{ t('iso.columns.title') }}</summary>
        <div class="iso-columns-panel">
          <fieldset>
            <legend class="visually-hidden">{{ t('iso.columns.title') }}</legend>
            <label v-for="c in columns" :key="c.id">
              <input type="checkbox" :checked="visibleIds.includes(c.id)" :disabled="visibleIds.length === 1 && visibleIds.includes(c.id)" @change="toggle(c.id)" />
              {{ t(c.label) }}
            </label>
          </fieldset>
          <button type="button" class="btn-link" @click="resetColumns">{{ t('iso.columns.reset') }}</button>
        </div>
      </details>
    </div>

    <p v-if="!rows.length" class="muted">{{ emptyText }}</p>

    <ul v-else-if="narrow" class="mod-cards">
      <li v-for="row in sorted" :key="row.item.id" class="mod-card" :class="rowClass?.(row)">
        <h2>{{ rowTitle(row) }}</h2>
        <dl class="mod-dl">
          <template v-for="c in visible" :key="c.id">
            <dt>{{ t(c.label) }}</dt>
            <dd>
              <a v-if="href(c, row)" :href="href(c, row)!" target="_blank" rel="noopener noreferrer">{{ cell(c, row) }}</a>
              <span v-else-if="c.badge?.(row)" class="mod-badge" :class="'is-' + c.badge(row)">{{ cell(c, row) }}</span>
              <template v-else>{{ cell(c, row) || t('modules.none') }}</template>
            </dd>
          </template>
        </dl>
        <button
          v-if="$slots.detail"
          type="button"
          class="mod-toggle"
          :aria-expanded="expanded.has(row.item.id)"
          :aria-controls="id('c-' + row.item.id)"
          :aria-label="t('modules.showDetails', { title: rowTitle(row) })"
          @click="toggleRow(row.item.id)"
        >
          <span class="chevron" aria-hidden="true">›</span>{{ t('modules.details') }}
        </button>
        <div v-if="expanded.has(row.item.id)" :id="id('c-' + row.item.id)" class="mod-details">
          <slot name="detail" :row="row" />
        </div>
      </li>
    </ul>

    <div v-else class="mod-table-wrap">
      <table class="mod-table iso-table">
        <caption class="visually-hidden">{{ t('iso.tableCaption') }}</caption>
        <thead>
          <tr>
            <th scope="col" class="iso-toggle-col"><span class="visually-hidden">{{ t('modules.details') }}</span></th>
            <th v-for="c in visible" :key="c.id" scope="col" :aria-sort="ariaSort(c.id)" :class="{ num: c.numeric }">
              <button type="button" class="iso-sort" @click="sortBy(c.id)">
                {{ t(c.label) }}<span class="iso-sort-mark" aria-hidden="true">{{ sortId === c.id ? (sortDir === 'asc' ? '▲' : '▼') : '' }}</span>
              </button>
            </th>
          </tr>
        </thead>
        <tbody>
          <template v-for="row in sorted" :key="row.item.id">
            <tr class="mod-row" :class="rowClass?.(row)">
              <td class="iso-toggle-col">
                <button
                  type="button"
                  class="mod-toggle"
                  :aria-expanded="expanded.has(row.item.id)"
                  :aria-controls="id('d-' + row.item.id)"
                  :aria-label="t('modules.showDetails', { title: rowTitle(row) })"
                  :title="t('modules.showDetails', { title: rowTitle(row) })"
                  @click="toggleRow(row.item.id)"
                >
                  <span class="chevron" aria-hidden="true">›</span>
                </button>
              </td>
              <td v-for="c in visible" :key="c.id" :class="{ nowrap: c.nowrap, num: c.numeric, 'iso-text': !c.nowrap && !c.numeric }">
                <a v-if="href(c, row)" :href="href(c, row)!" target="_blank" rel="noopener noreferrer" class="iso-link">{{ cell(c, row) }}</a>
                <span v-else-if="c.badge?.(row)" class="mod-badge" :class="'is-' + c.badge(row)">{{ cell(c, row) }}</span>
                <template v-else>{{ cell(c, row) }}</template>
              </td>
            </tr>
            <tr v-if="expanded.has(row.item.id)" :id="id('d-' + row.item.id)" class="mod-detail">
              <td :colspan="visible.length + 1"><slot name="detail" :row="row" /></td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </div>
</template>
