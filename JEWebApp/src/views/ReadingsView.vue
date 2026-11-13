<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { loadReadings, type ReadingMeter } from '../api/readings'
import DueBadge from '../components/readings/DueBadge.vue'
import ReadingPanel from '../components/readings/ReadingPanel.vue'
import ReadingPrintSheet from '../components/readings/ReadingPrintSheet.vue'
import MediumIcon from '../components/registers/MediumIcon.vue'
import MeterExchangeDialog from '../components/registers/MeterExchangeDialog.vue'
import { useLoader } from '../iso/useLoader'
import { useNarrow } from '../modules/useNarrow'
import { useSitePreselection } from '../nav/useSitePreselection'
import {
  canRead,
  defaultFilter,
  dueCounts,
  exchangeRow,
  filterMeters,
  formatReading,
  formatReadingTime,
  nextDueMeter,
  type ReadingFilter,
  type ReadingSavedEvent,
} from '../readings/readings'
import { formatLocal } from '../modules/dates'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'
import '../styles/registers.css'
import '../styles/edit.css'
import '../styles/readings.css'

/*
 * Meter reading (E8.1), the web counterpart of the Sun and Charge app: one tab per site (preselected by the site
 * picker), filter "Fällig" / "Alle", search, cards on phones and a table on wide screens; a meter opens the entry
 * panel (also via ?meter=<id> for links and QR codes). "Ableseliste drucken" prints the filtered list.
 */

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const narrow = useNarrow()

const { data, state, reload } = useLoader(() => loadReadings(auth.http, locale.value))
watch(locale, () => reload())

const preselect = useSitePreselection()
const selected = ref<string | null>(null)
const filter = ref<ReadingFilter>('all')
/* the filter is chosen per tab once ("Fällig" if any is due); the user's choice stays while the tab stays */
const filterFor = ref<string | null>(null)
const text = ref('')
const openId = ref<number | null>(null)
const exchanging = ref<ReadingMeter | null>(null)
const message = ref<{ ok: boolean; text: string } | null>(null)

/* sites without meters are left out (the register shows empty measurement directories too) */
const groups = computed(() => (data.value?.groups ?? []).filter((g) => g.meters.length > 0))
const group = computed(() => groups.value.find((g) => g.key === selected.value) ?? groups.value[0] ?? null)
const meters = computed(() => group.value?.meters ?? [])
const counts = computed(() => dueCounts(meters.value))
const list = computed(() => filterMeters(meters.value, filter.value, text.value, locale.value))

watch(data, (d) => {
  if (!d) return
  if (selected.value === null) selected.value = preselect.pick(d.groups)
  applyDeepLink()
})

watch(
  () => group.value?.key ?? null,
  (key) => {
    if (key !== null && filterFor.value !== key) {
      filterFor.value = key
      filter.value = defaultFilter(meters.value)
    }
  },
  { immediate: true },
)

/* ---- the open meter (panel) ---- */
const allMeters = computed(() => groups.value.flatMap((g) => g.meters))
const openMeter = computed(() => (openId.value == null ? null : (allMeters.value.find((m) => m.id === openId.value) ?? null)))
const nextMeter = computed(() => (openMeter.value ? nextDueMeter(list.value.length ? list.value : meters.value, openMeter.value.id) : null))

function queryMeter(): number | null {
  const v = route.query.meter
  const n = Number(Array.isArray(v) ? v[0] : v)
  return Number.isInteger(n) && n > 0 ? n : null
}

/* ?meter=<id>: select its tab and open it (once per link: reloads after saving do not open it again) */
let linkDone: number | null = null
function applyDeepLink() {
  const id = queryMeter()
  if (id == null || !data.value || linkDone === id) return
  linkDone = id
  const g = groups.value.find((gr) => gr.meters.some((m) => m.id === id))
  if (!g) {
    message.value = { ok: false, text: t('readings.notFound') }
    return
  }
  selected.value = g.key
  openId.value = id
}
watch(
  () => route.query.meter,
  () => {
    linkDone = null
    applyDeepLink()
  },
)

function open(meter: ReadingMeter) {
  message.value = null
  openId.value = meter.id
}

function dropQuery() {
  if (route.query.meter === undefined) return
  const query = { ...route.query }
  delete query.meter
  void router.replace({ query })
}

function closePanel() {
  openId.value = null
  dropQuery()
}

async function onSaved(e: ReadingSavedEvent) {
  const next = e.next ? nextMeter.value : null
  const value = formatReading(e.result.value, e.meter, locale.value)
  const time = formatReadingTime(e.result.timestamp, e.meter, locale.value)
  message.value = {
    ok: true,
    text: t(e.photoSkipped ? 'readings.savedNoPhoto' : 'readings.saved', { name: e.meter.name, value, time }),
  }
  openId.value = next?.id ?? null
  if (!next) dropQuery()
  await reload()
}

function onExchanged(time: string) {
  exchanging.value = null
  message.value = { ok: true, text: t('registerEdit.exchange.saved', { time: formatLocal(time, locale.value) }) }
  void reload()
}

/* ---- tabs ---- */
function onTabKey(event: KeyboardEvent, index: number) {
  const all = groups.value
  let next = -1
  if (event.key === 'ArrowRight') next = (index + 1) % all.length
  else if (event.key === 'ArrowLeft') next = (index - 1 + all.length) % all.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = all.length - 1
  if (next < 0) return
  event.preventDefault()
  selected.value = all[next].key
  document.getElementById(tabId(all[next].key))?.focus()
}

const tabId = (key: string) => `rd-tab-${key}`
const id = (suffix: string) => `rd-${suffix}`

/* ---- texts ---- */
const lastText = (m: ReadingMeter) => (m.readings[0] ? formatReading(m.readings[0].value, m, locale.value) : '')
const lastTime = (m: ReadingMeter) => (m.readings[0] ? formatReadingTime(m.readings[0].timestamp, m, locale.value) : '')
function problemText(m: ReadingMeter): string {
  if (m.problem === 'AUTOMATIC') return t('readings.problem.AUTOMATIC')
  if (m.row && !m.row.canEnter) return t('readings.problem.NO_RIGHT')
  if (m.row) return ''
  const key = 'readings.problem.' + (m.problem ?? 'NO_TARGET')
  return te(key) ? t(key) : t('readings.problem.NO_TARGET')
}
const badgeState = (m: ReadingMeter) => (m.problem === 'AUTOMATIC' ? 'AUTOMATIC' : m.due)
const rowClass = (m: ReadingMeter) => (m.due === 'OVERDUE' ? 'is-overdue' : m.due === 'DUE' ? 'is-dueSoon' : undefined)
const actionLabel = (m: ReadingMeter) => (canRead(m) ? t('readings.readLabel', { name: m.name }) : t('readings.showLabel', { name: m.name }))
const emptyText = computed(() => (filter.value === 'due' && !text.value.trim() ? t('readings.noneDue') : t('readings.noMatch')))
const headCount = computed(() =>
  counts.value.overdue ? t('readings.headCountOverdue', { due: counts.value.due, overdue: counts.value.overdue }) : t('readings.headCount', counts.value.due),
)

/* ---- print ---- */
/* only meters that are read by hand (no automatic rows, no meters without row) */
const printList = computed(() => list.value.filter((m) => canRead(m)))
const printSubtitle = computed(() =>
  [group.value?.name, filter.value === 'due' ? t('readings.filter.due') : t('readings.filter.all'), text.value.trim() ? `„${text.value.trim()}“` : '']
    .filter(Boolean)
    .join(' · '),
)
function print() {
  window.print()
}
</script>

<template>
  <section class="iso registers readings" :aria-labelledby="id('title')">
    <div class="mod-head">
      <h1 :id="id('title')">{{ t('modules.readings') }}</h1>
      <p v-if="data && group" class="rd-head-count" :class="{ 'is-alert': counts.overdue > 0 }" role="status">{{ headCount }}</p>
    </div>
    <p class="muted read-only">{{ t('readings.intro') }}</p>
    <p v-if="message" class="alert edit-result rd-message" :class="message.ok ? 'alert-ok' : 'alert-error'" :role="message.ok ? 'status' : 'alert'">
      {{ message.text }}
    </p>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('readings.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <p v-if="data && !groups.length" class="muted">{{ t('readings.empty') }}</p>
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
          :aria-controls="id('panel')"
          :tabindex="g.key === group.key ? 0 : -1"
          @click="selected = g.key"
          @keydown="onTabKey($event, i)"
        >
          {{ g.name }} <span class="reg-tab-count">{{ dueCounts(g.meters).due }}/{{ g.meters.length }}</span>
        </button>
      </div>

      <div :id="id('panel')" :role="groups.length > 1 ? 'tabpanel' : undefined" :aria-labelledby="groups.length > 1 ? tabId(group.key) : undefined">
        <fieldset class="mod-filters rd-filters">
          <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
          <div class="control">
            <span class="rd-filter-label" :id="id('filter')">{{ t('readings.filter.label') }}</span>
            <div class="rd-chips" role="group" :aria-labelledby="id('filter')">
              <button type="button" class="edit-chip rd-chip" :aria-pressed="filter === 'due'" @click="filter = 'due'">
                {{ t('readings.filter.due') }} <span class="rd-chip-count">{{ counts.due }}</span>
              </button>
              <button type="button" class="edit-chip rd-chip" :aria-pressed="filter === 'all'" @click="filter = 'all'">
                {{ t('readings.filter.all') }} <span class="rd-chip-count">{{ meters.length }}</span>
              </button>
            </div>
          </div>
          <div class="control search">
            <label :for="id('search')">{{ t('modules.search') }}</label>
            <input :id="id('search')" v-model="text" type="search" :placeholder="t('readings.searchPlaceholder')" />
          </div>
        </fieldset>

        <div class="iso-toolbar">
          <p class="mod-count muted" role="status">{{ t('readings.count', { shown: list.length, total: meters.length }) }}</p>
          <div class="reg-actions">
            <button type="button" class="edit-btn rd-print-btn" :disabled="!list.length" @click="print">{{ t('readings.print.action') }}</button>
          </div>
        </div>

        <p v-if="!meters.length" class="muted">{{ t('readings.emptyGroup') }}</p>
        <p v-else-if="!list.length" class="muted">{{ emptyText }}</p>

        <ul v-else-if="narrow" class="mod-cards rd-cards">
          <li v-for="m in list" :key="m.id" class="mod-card rd-card" :class="rowClass(m)">
            <div class="rd-card-head">
              <span class="rd-icon"><MediumIcon :medium="m.medium" /></span>
              <h2>{{ m.name }}</h2>
              <DueBadge :due="badgeState(m)" />
            </div>
            <dl class="mod-dl">
              <template v-if="m.location">
                <dt>{{ t('readings.col.location') }}</dt>
                <dd>{{ m.location }}</dd>
              </template>
              <template v-if="m.serialNumber">
                <dt>{{ t('readings.col.serial') }}</dt>
                <dd>{{ m.serialNumber }}</dd>
              </template>
              <dt>{{ t('readings.col.last') }}</dt>
              <dd>
                <template v-if="m.readings[0]">{{ lastText(m) }} <span class="muted">· {{ lastTime(m) }}</span></template>
                <template v-else>{{ t('readings.history.none') }}</template>
              </dd>
            </dl>
            <p v-if="problemText(m)" class="rd-problem-text">{{ problemText(m) }}</p>
            <button type="button" class="btn rd-open" :class="canRead(m) ? 'btn-primary' : 'edit-btn'" :aria-label="actionLabel(m)" @click="open(m)">
              {{ canRead(m) ? t('readings.read') : t('readings.show') }}
            </button>
          </li>
        </ul>

        <div v-else class="mod-table-wrap">
          <table class="mod-table rd-table">
            <caption class="visually-hidden">{{ group.name }}</caption>
            <thead>
              <tr>
                <th scope="col" class="rd-icon-col"><span class="visually-hidden">{{ t('registers.col.medium') }}</span></th>
                <th scope="col">{{ t('readings.col.meter') }}</th>
                <th scope="col">{{ t('readings.col.location') }}</th>
                <th scope="col">{{ t('readings.col.serial') }}</th>
                <th scope="col" class="num">{{ t('readings.col.last') }}</th>
                <th scope="col">{{ t('readings.col.date') }}</th>
                <th scope="col">{{ t('readings.col.due') }}</th>
                <th scope="col"><span class="visually-hidden">{{ t('readings.col.action') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in list" :key="m.id" class="mod-row rd-row" :class="rowClass(m)">
                <td class="rd-icon-col"><MediumIcon :medium="m.medium" /></td>
                <td class="iso-text">
                  <button type="button" class="rd-name" :aria-label="actionLabel(m)" @click="open(m)">{{ m.name }}</button>
                  <span v-if="problemText(m)" class="rd-problem-text">{{ problemText(m) }}</span>
                </td>
                <td class="iso-text">{{ m.location }}</td>
                <td class="nowrap">{{ m.serialNumber }}</td>
                <td class="num nowrap">{{ lastText(m) }}</td>
                <td class="nowrap">{{ lastTime(m) }}</td>
                <td><DueBadge :due="badgeState(m)" /></td>
                <td class="rd-action-col">
                  <button v-if="canRead(m)" type="button" class="edit-btn is-small rd-open" :aria-label="actionLabel(m)" @click="open(m)">{{ t('readings.read') }}</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <ReadingPrintSheet :title="t('readings.print.title')" :subtitle="printSubtitle" :meters="printList" />
    </template>

    <ReadingPanel
      v-if="openMeter"
      :key="openMeter.id"
      :meter="openMeter"
      :next-name="nextMeter?.name ?? null"
      @close="closePanel"
      @saved="onSaved"
      @changed="reload"
      @exchange="exchanging = openMeter"
    />
    <MeterExchangeDialog v-if="exchanging" :row="exchangeRow(exchanging)" @close="exchanging = null" @saved="onExchanged" />
  </section>
</template>
