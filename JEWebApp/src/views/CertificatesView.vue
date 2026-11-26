<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadCertificates, requestTransfer, type Certificate, type CertificateList, type CertificateTransfer } from '../api/certificates'
import { localName } from '../api/navigation'
import { certificatesCsv, DEFAULT_CERTIFICATE_FILTER, filterCertificates, sortCertificates, statusCounts, type CertificateFilter, type StatusFilter } from '../certificates/certificates'
import CertificateEditDialog from '../components/certificates/CertificateEditDialog.vue'
import { useBusy, writeErrorText } from '../edit/edit'
import { formatLocal, formatLocalDate } from '../modules/dates'
import { siteOptions, type SiteFilter } from '../modules/search'
import { useNarrow } from '../modules/useNarrow'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/edit.css'
import '../styles/registers.css'

/*
 * Energy certificates of the buildings (tender point BA-07): overview with status chips, site filter, search and CSV
 * export; "Neuer Energieausweis" and a click on a row open the editor (side panel). Tab "Übermittlung": the transfer
 * targets with their last result and "Jetzt übermitteln" (the JENotifier sends in its next run).
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const narrow = useNarrow()

const list = shallowRef<CertificateList | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const filter = reactive<CertificateFilter>({ ...DEFAULT_CERTIFICATE_FILTER })
const tab = ref<'list' | 'transfers'>('list')
const message = ref<{ ok: boolean; text: string } | null>(null)
let seq = 0

async function fetchList() {
  const current = ++seq
  state.value = 'loading'
  try {
    const result = await loadCertificates(auth.http)
    if (current !== seq) return
    list.value = result
    state.value = 'ready'
    if (editing.value && editing.value !== 'new') {
      const fresh = result.certificates.find((c) => c.id === (editing.value as Certificate).id)
      if (fresh) editing.value = fresh
    }
  } catch {
    if (current !== seq) return
    state.value = 'error'
  }
}

onMounted(fetchList)

const all = computed(() => list.value?.certificates ?? [])
const counts = computed(() => statusCounts(all.value))
const sites = computed(() => siteOptions(all.value, locale.value))
const rows = computed(() => sortCertificates(filterCertificates(all.value, filter, locale.value), locale.value))
const canCreate = computed(() => (list.value?.buildings ?? []).some((b) => b.canCreate))
const statusOptions: StatusFilter[] = ['all', 'attention', 'EXPIRED', 'EXPIRING', 'VALID']

const siteValue = computed({
  get: () => String(filter.site),
  set: (v: string) => {
    filter.site = (v === 'all' || v === 'none' ? v : Number(v)) as SiteFilter
  },
})

function chipCount(s: StatusFilter): number {
  if (s === 'all') return all.value.length
  if (s === 'attention') return counts.value.EXPIRED + counts.value.EXPIRING
  return counts.value[s]
}

const typeLabel = (c: Certificate) => (c.data.type ? t('certificates.type.' + c.data.type) : c.name)
const buildingLabel = (c: Certificate) => (c.building ? localName(c.building, locale.value) : c.site ? localName(c.site, locale.value) : t('certificates.field.none'))

function daysText(c: Certificate): string {
  if (c.daysLeft == null) return ''
  return c.daysLeft >= 0 ? t('certificates.daysLeft', c.daysLeft) : t('certificates.daysAgo', -c.daysLeft)
}

function num(v: number | null | undefined): string {
  return v == null ? '–' : v.toLocaleString(locale.value, { maximumFractionDigits: 1 })
}

/** the energy value of the row: consumption (heat) for consumption certificates, final energy otherwise */
function mainValue(c: Certificate): string {
  const v = c.data.type === 'CONSUMPTION' && c.data.heatConsumption != null ? c.data.heatConsumption : c.data.finalEnergy
  return v == null ? '–' : `${num(v)} ${t('certificates.unit.energy')}`
}

/* ---------- editor ---------- */
const editing = shallowRef<Certificate | 'new' | null>(null)

function onSaved(c: Certificate, created: boolean) {
  if (created) message.value = { ok: true, text: t('certificates.created', { name: c.name }) }
  editing.value = c
  void fetchList()
}

function onDeleted(name: string) {
  editing.value = null
  message.value = { ok: true, text: t('certificates.deleted', { name }) }
  void fetchList()
}

/* ---------- export ---------- */
function exportCsv() {
  const header = ['building', 'type', 'registrationNumber', 'issued', 'validUntil', 'status', 'energyClass', 'heatConsumption', 'electricityConsumption', 'finalEnergy', 'primaryEnergy', 'referenceArea', 'co2', 'issuer', 'responsible', 'file'].map((k) => t('certificates.col.' + k))
  const csv = certificatesCsv(rows.value, header, typeLabel, locale.value)
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = t('certificates.title') + '.csv'
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/* ---------- transfers ---------- */
const { busy, run } = useBusy()
const transfers = computed(() => list.value?.transfers ?? [])

function scheduleLabel(tr: CertificateTransfer): string {
  const key = 'certificates.transfer.schedule.' + tr.schedule
  return tr.schedule && t(key) !== key ? t(key) : tr.schedule ?? '–'
}

function lastLabel(tr: CertificateTransfer): string {
  const l = tr.lastTransfer
  if (!l) return '–'
  if (typeof l === 'string') return l
  const date = formatLocal((l.at ?? '').slice(0, 16), locale.value)
  return l.error ? t('certificates.transfer.lastError', { date, error: l.error }) : t('certificates.transfer.lastOk', { date, count: l.count ?? 0 })
}

function requestedLabel(tr: CertificateTransfer): string {
  const r = tr.requested
  if (!r) return '–'
  if (typeof r === 'string') return r
  return t('certificates.transfer.requestedAt', { date: formatLocal((r.at ?? '').slice(0, 16), locale.value), user: r.by ?? '' })
}

async function runTransfer(tr: CertificateTransfer) {
  message.value = null
  await run(async () => {
    try {
      const updated = await requestTransfer(auth.http, tr.id)
      if (list.value) list.value = { ...list.value, transfers: list.value.transfers.map((x) => (x.id === tr.id ? updated : x)) }
      message.value = { ok: true, text: t('certificates.transfer.requestedOk', { name: tr.name }) }
    } catch (e) {
      message.value = { ok: false, text: writeErrorText(e, t) }
    }
  })
}
</script>

<template>
  <section class="certificates" aria-labelledby="ce-title">
    <div class="mod-head">
      <h1 id="ce-title">{{ t('certificates.title') }}</h1>
      <span v-if="list?.today" class="muted">{{ t('certificates.today', { date: formatLocalDate(list.today, locale) }) }}</span>
      <span class="ce-actions">
        <button v-if="list && rows.length && tab === 'list'" type="button" class="edit-btn" @click="exportCsv">{{ t('certificates.export') }}</button>
        <button v-if="canCreate" type="button" class="btn btn-primary" @click="editing = 'new'">{{ t('certificates.new') }}</button>
      </span>
    </div>
    <p class="muted ce-hint">{{ t('certificates.hint') }}</p>
    <p v-if="message" class="alert edit-result" :class="message.ok ? 'alert-ok' : 'alert-error'" :role="message.ok ? 'status' : 'alert'">{{ message.text }}</p>

    <p v-if="state === 'loading' && !list" class="muted" role="status">{{ t('certificates.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link" @click="fetchList">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="list">
      <div class="reg-tabs" role="tablist" :aria-label="t('certificates.title')">
        <button id="ce-tab-list" type="button" role="tab" class="reg-tab" :aria-selected="tab === 'list'" aria-controls="ce-panel" @click="tab = 'list'">
          {{ t('certificates.tabs.list') }} <span class="reg-tab-count">{{ all.length }}</span>
        </button>
        <button id="ce-tab-transfers" type="button" role="tab" class="reg-tab" :aria-selected="tab === 'transfers'" aria-controls="ce-panel" @click="tab = 'transfers'">
          {{ t('certificates.tabs.transfers') }} <span class="reg-tab-count">{{ transfers.length }}</span>
        </button>
      </div>

      <div id="ce-panel" role="tabpanel" :aria-labelledby="tab === 'list' ? 'ce-tab-list' : 'ce-tab-transfers'">
        <template v-if="tab === 'list'">
          <fieldset class="mod-filters">
            <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
            <div class="control">
              <span class="seg-label" aria-hidden="true">{{ t('modules.status') }}</span>
              <div class="mod-seg" role="radiogroup" :aria-label="t('modules.status')">
                <label v-for="s in statusOptions" :key="s">
                  <input v-model="filter.status" type="radio" name="ce-status" :value="s" />
                  {{ t('certificates.filter.' + s) }} ({{ chipCount(s) }})
                </label>
              </div>
            </div>
            <div v-if="sites.length > 1" class="control">
              <label for="ce-site">{{ t('modules.site') }}</label>
              <select id="ce-site" v-model="siteValue">
                <option value="all">{{ t('modules.allSites') }}</option>
                <option v-for="s in sites" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
              </select>
            </div>
            <div class="control search">
              <label for="ce-search">{{ t('modules.search') }}</label>
              <input id="ce-search" v-model="filter.text" type="search" :placeholder="t('modules.searchPlaceholder')" />
            </div>
          </fieldset>

          <p class="mod-count muted" role="status">{{ t('modules.count', { shown: rows.length, total: all.length }) }}</p>
          <p v-if="!rows.length" class="muted">{{ t('certificates.empty') }}</p>

          <ul v-else-if="narrow" class="mod-cards">
            <li v-for="c in rows" :key="c.id" class="mod-card" :class="'is-' + c.status.toLowerCase()">
              <h2>
                <button type="button" class="btn-link" @click="editing = c">{{ buildingLabel(c) }} · {{ typeLabel(c) }}</button>
              </h2>
              <dl class="mod-dl">
                <dt>{{ t('certificates.col.status') }}</dt>
                <dd><span class="mod-badge" :class="'ce-' + c.status">{{ t('certificates.status.' + c.status) }}</span> {{ daysText(c) }}</dd>
                <dt>{{ t('certificates.col.validUntil') }}</dt>
                <dd>{{ formatLocalDate(c.validUntil, locale) || '–' }}</dd>
                <dt>{{ t('certificates.col.energyClass') }}</dt>
                <dd>{{ c.data.energyClass || '–' }}</dd>
                <dt>{{ t('certificates.col.finalEnergy') }}</dt>
                <dd>{{ mainValue(c) }}</dd>
                <dt>{{ t('certificates.col.responsible') }}</dt>
                <dd>{{ c.responsible.names.join(', ') || '–' }}</dd>
              </dl>
            </li>
          </ul>

          <div v-else class="mod-table-wrap">
            <table class="mod-table">
              <caption class="visually-hidden">{{ t('certificates.title') }}</caption>
              <thead>
                <tr>
                  <th scope="col">{{ t('certificates.col.building') }}</th>
                  <th scope="col">{{ t('certificates.col.type') }}</th>
                  <th scope="col">{{ t('certificates.col.registrationNumber') }}</th>
                  <th scope="col">{{ t('certificates.col.issued') }}</th>
                  <th scope="col">{{ t('certificates.col.validUntil') }}</th>
                  <th scope="col">{{ t('certificates.col.status') }}</th>
                  <th scope="col">{{ t('certificates.col.energyClass') }}</th>
                  <th scope="col">{{ t('certificates.col.finalEnergy') }}</th>
                  <th scope="col">{{ t('certificates.col.issuer') }}</th>
                  <th scope="col">{{ t('certificates.col.responsible') }}</th>
                  <th scope="col">{{ t('certificates.col.file') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in rows" :key="c.id" class="mod-row">
                  <th scope="row" class="ce-title">
                    <button type="button" class="btn-link" @click="editing = c">{{ buildingLabel(c) }}</button>
                  </th>
                  <td>{{ typeLabel(c) }}</td>
                  <td>{{ c.data.registrationNumber || '–' }}</td>
                  <td class="nowrap">{{ formatLocalDate(c.data.issued, locale) || '–' }}</td>
                  <td class="nowrap">{{ formatLocalDate(c.validUntil, locale) || '–' }}<br /><span class="muted">{{ daysText(c) }}</span></td>
                  <td><span class="mod-badge" :class="'ce-' + c.status">{{ t('certificates.status.' + c.status) }}</span></td>
                  <td class="ce-class">{{ c.data.energyClass || '–' }}</td>
                  <td class="nowrap">{{ mainValue(c) }}</td>
                  <td>{{ c.data.issuer || '–' }}</td>
                  <td>{{ c.responsible.names.join(', ') || (c.responsible.count ? c.responsible.count : '–') }}</td>
                  <td>{{ c.file ? '✓' : '–' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>

        <template v-else>
          <p class="muted ce-hint">{{ t('certificates.transfer.hint') }}</p>
          <p v-if="!transfers.length" class="muted">{{ t('certificates.transfer.empty') }}</p>
          <div v-else class="mod-table-wrap">
            <table class="mod-table">
              <caption class="visually-hidden">{{ t('certificates.tabs.transfers') }}</caption>
              <thead>
                <tr>
                  <th scope="col">{{ t('certificates.transfer.col.name') }}</th>
                  <th scope="col">{{ t('certificates.transfer.col.enabled') }}</th>
                  <th scope="col">{{ t('certificates.transfer.col.channel') }}</th>
                  <th scope="col">{{ t('certificates.transfer.col.format') }}</th>
                  <th scope="col">{{ t('certificates.transfer.col.schedule') }}</th>
                  <th scope="col">{{ t('certificates.transfer.col.last') }}</th>
                  <th scope="col">{{ t('certificates.transfer.col.requested') }}</th>
                  <th scope="col"><span class="visually-hidden">{{ t('certificates.transfer.run') }}</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="tr in transfers" :key="tr.id" class="mod-row">
                  <th scope="row">{{ tr.name }}</th>
                  <td>{{ tr.enabled === 'true' || tr.enabled === '1' ? t('certificates.transfer.yes') : t('certificates.transfer.no') }}</td>
                  <td>{{ tr.channel || '–' }}</td>
                  <td>{{ tr.format || '–' }}</td>
                  <td>{{ scheduleLabel(tr) }}</td>
                  <td>{{ lastLabel(tr) }}</td>
                  <td>{{ requestedLabel(tr) }}</td>
                  <td>
                    <button v-if="tr.canRun" type="button" class="edit-btn is-small" :disabled="busy" @click="runTransfer(tr)">{{ t('certificates.transfer.run') }}</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>
    </template>

    <CertificateEditDialog
      v-if="editing && list"
      :key="editing === 'new' ? 'new' : 'c' + editing.id"
      :certificate="editing === 'new' ? null : editing"
      :buildings="list.buildings"
      :people="list.people"
      :building="typeof filter.site === 'number' ? filter.site : null"
      @close="editing = null"
      @saved="onSaved"
      @deleted="onDeleted"
      @changed="fetchList"
    />
  </section>
</template>

<style scoped>
.ce-actions {
  margin-left: auto;
  display: flex;
  gap: 0.5rem;
}

.ce-hint {
  font-size: 0.85rem;
  margin: 0 0 1rem;
}

.seg-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 0.15rem;
}

.ce-title {
  text-align: left;
  font-weight: 600;
}

.ce-class {
  font-weight: 700;
}

.mod-badge.ce-EXPIRED {
  background: var(--danger-bg, #fde8e8);
  color: var(--danger, #a4161a);
}

.mod-badge.ce-EXPIRING {
  background: var(--warn-bg, #fff4cc);
  color: var(--warn, #7a5b00);
}

.mod-badge.ce-VALID {
  background: var(--ok-bg, #e6f4ea);
  color: var(--ok, #1e6b34);
}
</style>
