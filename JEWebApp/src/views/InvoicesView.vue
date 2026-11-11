<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import {
  checkInvoiceRange,
  defaultInvoiceFilter,
  defaultInvoiceRange,
  filterInvoices,
  formatAmount,
  formatConsumption,
  invoiceDate,
  invoiceMedium,
  invoicePeriod,
  invoiceSites,
  invoiceSummaries,
  invoicesCsv,
  mediumOptions,
  monthLabel,
  monthlyChartOption,
  monthlySeries,
  type Amount,
  type InvoiceFilter,
} from '../accounting/invoices'
import { echartsLocale } from '../analysis/echarts'
import { formatNumber } from '../analysis/format'
import { browserTimeZone } from '../analysis/settings'
import { loadInvoices, type Invoice, type InvoiceMedium } from '../api/invoices'
import InvoiceDetails from '../components/accounting/InvoiceDetails.vue'
import { saveBlob } from '../iso/download'
import { useLoader } from '../iso/useLoader'
import type { SiteFilter } from '../modules/search'
import { useNarrow } from '../modules/useNarrow'
import { notesCsvName, toggleTag } from '../notes/notes'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'
import '../styles/notes.css'
import '../styles/reports.css'
import '../styles/accounting.css'

/*
 * Energy invoices (W4.7, view only): the imported invoices of a time frame (by period start), medium chips, search
 * and site filter, totals and a monthly column chart per medium, the table (cards on phone width) with a detail
 * panel (line items, original file) and CSV export.
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const narrow = useNarrow()

const range = reactive(defaultInvoiceRange())
const rangeCheck = computed(() => checkInvoiceRange(range.from, range.to))

let requested = { ...range }
const { data, state, reload } = useLoader(() => {
  requested = { ...range }
  return loadInvoices(auth.http, { from: range.from, to: range.to, timeZone: browserTimeZone() })
})

/* a valid new time frame loads again; an invalid one keeps the shown invoices */
watch([() => range.from, () => range.to], () => {
  if (rangeCheck.value === 'ok') reload()
})

function resetRange() {
  Object.assign(range, defaultInvoiceRange())
}

const filter = reactive<InvoiceFilter>(defaultInvoiceFilter())
const selected = ref<number | null>(null)

const all = computed<Invoice[]>(() => data.value?.invoices ?? [])
const rows = computed(() => filterInvoices(all.value, filter, locale.value))
const media = computed(() => mediumOptions(all.value, filter.media))
const sites = computed(() => invoiceSites(all.value, locale.value))
const withoutSite = computed(() => all.value.some((inv) => !inv.site))
const summaries = computed(() => invoiceSummaries(rows.value))
const series = computed(() => monthlySeries(rows.value))

watch(data, () => {
  const known = new Set(mediumOptions(all.value).map((m) => m.id))
  filter.media = filter.media.filter((m) => known.has(m))
  if (typeof filter.site === 'number' && !sites.value.some((s) => s.id === filter.site)) filter.site = 'all'
  if (selected.value != null && !all.value.some((inv) => inv.id === selected.value)) selected.value = null
})

const siteValue = computed({
  get: () => String(filter.site),
  set: (v: string) => {
    filter.site = (v === 'all' || v === 'none' ? v : Number(v)) as SiteFilter
  },
})

function toggleMedium(m: InvoiceMedium) {
  filter.media = toggleTag(filter.media, m) as InvoiceMedium[]
}

function toggle(id: number) {
  selected.value = selected.value === id ? null : id
}

const mediumName = (m: InvoiceMedium) => t('invoices.medium.' + m)
const medium = (inv: Invoice) => mediumName(invoiceMedium(inv))
const period = (inv: Invoice) => invoicePeriod(inv, locale.value)
const date = (inv: Invoice) => invoiceDate(inv, locale.value)
const amount = (v: number | null) => formatAmount(v, locale.value)
const consumption = (inv: Invoice) => formatConsumption(inv.consumption, inv.consumptionUnit, locale.value)
const negative = (v: number | null | undefined) => typeof v === 'number' && v < 0
const showDetails = (inv: Invoice) => t('modules.showDetails', { title: [inv.number, inv.seller].filter(Boolean).join(' – ') || '#' + inv.id })
const domId = (inv: Invoice, suffix: string) => `invoice-${inv.id}-${suffix}`

const sumText = (list: Amount[], kind: 'unit' | 'currency') =>
  list.length
    ? list
        .map((a) => {
          const value = kind === 'unit' ? formatNumber(a.value, locale.value, { min: 0, max: 2 }) : formatAmount(a.value, locale.value)
          return a.key ? `${value} ${a.key}` : value
        })
        .join(' · ')
    : t('modules.none')

const chartLabels = computed(() => ({
  consumption: t('invoices.chart.consumption'),
  cost: t('invoices.chart.cost'),
  month: (key: string) => monthLabel(key, locale.value),
}))
const charts = computed(() => series.value.map((s) => ({ s, option: monthlyChartOption(s, chartLabels.value, locale.value) })))
const initOptions = computed(() => ({ locale: echartsLocale(locale.value) }))

function exportCsv() {
  const h = (id: string) => t('invoices.col.' + id)
  const csv = invoicesCsv(
    rows.value,
    {
      periodStart: h('periodStart'),
      periodEnd: h('periodEnd'),
      number: h('number'),
      date: h('date'),
      seller: h('seller'),
      medium: h('medium'),
      meterPoint: h('meterPoint'),
      consumption: h('consumption'),
      unit: h('unit'),
      net: h('net'),
      tax: h('tax'),
      gross: h('gross'),
      currency: h('currency'),
      credit: h('credit'),
      site: h('site'),
      path: h('path'),
    },
    { mediumName, yes: t('invoices.yes') },
    locale.value,
  )
  saveBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), notesCsvName(t('invoices.title'), requested.from, requested.to))
}

const tableColumns = ['period', 'number', 'date', 'seller', 'medium', 'meterPoint', 'consumption', 'net', 'tax', 'gross', 'currency'] as const
const numericColumns = new Set(['consumption', 'net', 'tax', 'gross'])
</script>

<template>
  <section class="iso notes invoices" aria-labelledby="invoices-title">
    <div class="mod-head">
      <h1 id="invoices-title">{{ t('invoices.title') }}</h1>
    </div>
    <p class="muted read-only">{{ t('invoices.readOnly') }}</p>

    <fieldset class="mod-filters iso-filters notes-range">
      <legend class="visually-hidden">{{ t('invoices.range.label') }}</legend>
      <div class="control">
        <label for="invoices-from">{{ t('invoices.range.from') }}</label>
        <input
          id="invoices-from"
          v-model="range.from"
          type="date"
          :max="range.to || undefined"
          :aria-invalid="rangeCheck !== 'ok'"
          aria-describedby="invoices-range-hint invoices-range-msg"
        />
      </div>
      <div class="control">
        <label for="invoices-to">{{ t('invoices.range.to') }}</label>
        <input
          id="invoices-to"
          v-model="range.to"
          type="date"
          :min="range.from || undefined"
          :aria-invalid="rangeCheck !== 'ok'"
          aria-describedby="invoices-range-hint invoices-range-msg"
        />
      </div>
      <div class="control">
        <button type="button" class="notes-btn notes-reset" @click="resetRange">{{ t('invoices.range.reset') }}</button>
      </div>
      <p id="invoices-range-hint" class="muted inv-hint">{{ t('invoices.rangeHint') }}</p>
      <p id="invoices-range-msg" class="notes-range-msg" :class="{ 'iso-error': rangeCheck !== 'ok' }" role="status">
        <template v-if="rangeCheck !== 'ok'">{{ t('invoices.range.' + rangeCheck) }}</template>
      </p>
    </fieldset>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('invoices.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="data">
      <fieldset class="mod-filters iso-filters">
        <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
        <div class="control search">
          <label for="invoices-search">{{ t('modules.search') }}</label>
          <input id="invoices-search" v-model="filter.text" type="search" :placeholder="t('invoices.searchPlaceholder')" />
        </div>
        <div v-if="sites.length > 1 || (sites.length && withoutSite)" class="control">
          <label for="invoices-site">{{ t('modules.site') }}</label>
          <select id="invoices-site" v-model="siteValue">
            <option value="all">{{ t('modules.allSites') }}</option>
            <option v-for="s in sites" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
            <option v-if="withoutSite" value="none">{{ t('modules.noSite') }}</option>
          </select>
        </div>
      </fieldset>

      <div v-if="media.length" class="notes-tags" role="group" :aria-label="t('invoices.mediumFilter')">
        <span class="seg-label" aria-hidden="true">{{ t('invoices.mediumFilter') }}</span>
        <button type="button" class="notes-chip" :aria-pressed="!filter.media.length" @click="filter.media = []">{{ t('invoices.allMedia') }}</button>
        <button
          v-for="m in media"
          :key="m.id"
          type="button"
          class="notes-chip"
          :data-medium="m.id"
          :aria-pressed="filter.media.includes(m.id)"
          @click="toggleMedium(m.id)"
        >
          {{ mediumName(m.id) }} <span class="notes-chip-count">{{ m.count }}</span>
        </button>
      </div>

      <div v-if="data.truncated" class="alert notes-truncated" role="note">{{ t('invoices.truncated', { n: all.length }) }}</div>

      <section v-if="summaries.length" class="iso-stats inv-summary" aria-labelledby="invoices-summary-title">
        <h2 id="invoices-summary-title" class="iso-stats-title">{{ t('invoices.summary.title') }}</h2>
        <div class="iso-tiles">
          <dl v-for="s in summaries" :key="s.medium" class="iso-tile inv-tile" :data-medium="s.medium">
            <dt>{{ mediumName(s.medium) }}</dt>
            <dd class="iso-sub">
              {{ t('invoices.summary.invoices', s.count) }}<template v-if="s.credits">, {{ t('invoices.summary.credits', s.credits) }}</template>
            </dd>
            <dt class="visually-hidden">{{ t('invoices.summary.consumption') }}</dt>
            <dd class="iso-big inv-sum-consumption">{{ sumText(s.consumption, 'unit') }}</dd>
            <dt class="inv-tile-label">{{ t('invoices.summary.net') }}</dt>
            <dd class="inv-sum-net">{{ sumText(s.net, 'currency') }}</dd>
            <dt class="inv-tile-label">{{ t('invoices.summary.gross') }}</dt>
            <dd class="inv-sum-gross">{{ sumText(s.gross, 'currency') }}</dd>
          </dl>
        </div>
      </section>

      <section v-if="charts.length" class="inv-charts" aria-labelledby="invoices-charts-title">
        <h2 id="invoices-charts-title" class="iso-stats-title">{{ t('invoices.chart.title') }}</h2>
        <div class="inv-chart-grid">
          <figure v-for="c in charts" :key="c.s.medium" class="inv-chart-card" :data-medium="c.s.medium">
            <figcaption>{{ mediumName(c.s.medium) }}</figcaption>
            <div class="inv-chart" role="img" :aria-label="t('invoices.chart.label', { medium: mediumName(c.s.medium) })">
              <VChart :option="c.option" :init-options="initOptions" :update-options="{ notMerge: true }" autoresize />
            </div>
          </figure>
        </div>
      </section>

      <div class="iso-toolbar">
        <p class="mod-count muted" role="status">{{ t('invoices.count', { shown: rows.length, total: all.length }) }}</p>
        <button type="button" class="notes-btn" :disabled="!rows.length" :aria-label="t('invoices.export.csvLabel')" @click="exportCsv">
          {{ t('invoices.export.csv') }}
        </button>
      </div>

      <p v-if="!all.length" class="muted">{{ t('invoices.empty') }}</p>
      <p v-else-if="!rows.length" class="muted">{{ t('invoices.noMatch') }}</p>

      <ul v-else-if="narrow" class="mod-cards">
        <li v-for="inv in rows" :key="inv.id" class="mod-card notes-card inv-card" :class="{ 'is-credit': inv.credit }">
          <h2>{{ period(inv) || date(inv) }}</h2>
          <p class="inv-card-head">
            {{ inv.seller }}
            <span class="mod-badge inv-medium">{{ medium(inv) }}</span>
            <span v-if="inv.credit" class="mod-badge inv-credit">{{ t('invoices.credit') }}</span>
          </p>
          <dl class="mod-dl">
            <dt>{{ t('invoices.col.number') }}</dt>
            <dd>{{ inv.number || t('modules.none') }}</dd>
            <dt>{{ t('invoices.col.date') }}</dt>
            <dd>{{ date(inv) || t('modules.none') }}</dd>
            <template v-if="inv.meterPoint">
              <dt>{{ t('invoices.col.meterPoint') }}</dt>
              <dd class="inv-mono">{{ inv.meterPoint }}</dd>
            </template>
            <dt>{{ t('invoices.col.consumption') }}</dt>
            <dd :class="{ 'is-negative': negative(inv.consumption) }">{{ consumption(inv) || t('modules.none') }}</dd>
            <dt>{{ t('invoices.col.gross') }}</dt>
            <dd :class="{ 'is-negative': negative(inv.gross) }">
              {{ amount(inv.gross) || t('modules.none') }} {{ inv.gross != null ? inv.currency ?? '' : '' }}
            </dd>
          </dl>
          <button
            type="button"
            class="mod-toggle"
            :aria-expanded="selected === inv.id"
            :aria-controls="domId(inv, 'c')"
            :aria-label="showDetails(inv)"
            @click="toggle(inv.id)"
          >
            <span class="chevron" aria-hidden="true">›</span>{{ t('modules.details') }}
          </button>
          <div v-if="selected === inv.id" :id="domId(inv, 'c')" class="mod-details">
            <InvoiceDetails :invoice="inv" />
          </div>
        </li>
      </ul>

      <div v-else class="mod-table-wrap">
        <table class="mod-table iso-table notes-table inv-table">
          <caption class="visually-hidden">{{ t('invoices.title') }}</caption>
          <thead>
            <tr>
              <th scope="col" class="iso-toggle-col"><span class="visually-hidden">{{ t('modules.details') }}</span></th>
              <th v-for="c in tableColumns" :key="c" scope="col" :class="{ num: numericColumns.has(c) }">{{ t('invoices.col.' + c) }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="inv in rows" :key="inv.id">
              <tr class="mod-row notes-row inv-row" :class="{ 'is-selected': selected === inv.id, 'is-credit': inv.credit }" @click="toggle(inv.id)">
                <td class="iso-toggle-col">
                  <button
                    type="button"
                    class="mod-toggle"
                    :aria-expanded="selected === inv.id"
                    :aria-controls="domId(inv, 'd')"
                    :aria-label="showDetails(inv)"
                    :title="showDetails(inv)"
                    @click.stop="toggle(inv.id)"
                  >
                    <span class="chevron" aria-hidden="true">›</span>
                  </button>
                </td>
                <td class="nowrap">{{ period(inv) }}</td>
                <td class="iso-text">
                  {{ inv.number }}
                  <span v-if="inv.credit" class="mod-badge inv-credit">{{ t('invoices.credit') }}</span>
                </td>
                <td class="nowrap">{{ date(inv) }}</td>
                <td class="iso-text">{{ inv.seller }}</td>
                <td class="nowrap">{{ medium(inv) }}</td>
                <td class="iso-text inv-mono">{{ inv.meterPoint }}</td>
                <td class="num nowrap" :class="{ 'is-negative': negative(inv.consumption) }">{{ consumption(inv) }}</td>
                <td class="num nowrap" :class="{ 'is-negative': negative(inv.net) }">{{ amount(inv.net) }}</td>
                <td class="num nowrap" :class="{ 'is-negative': negative(inv.tax) }">{{ amount(inv.tax) }}</td>
                <td class="num nowrap" :class="{ 'is-negative': negative(inv.gross) }">{{ amount(inv.gross) }}</td>
                <td class="nowrap">{{ inv.currency }}</td>
              </tr>
              <tr v-if="selected === inv.id" :id="domId(inv, 'd')" class="mod-detail">
                <td :colspan="tableColumns.length + 1"><InvoiceDetails :invoice="inv" /></td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
