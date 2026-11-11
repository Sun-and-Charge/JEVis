<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { formatDateTime } from '../analysis/format'
import { loadReports, type ReportSummary } from '../api/reports'
import ReportDetails from '../components/reports/ReportDetails.vue'
import { useLoader } from '../iso/useLoader'
import { useNarrow } from '../modules/useNarrow'
import { filterReports, groupReports, hasOutput, outputTime, parseReportId } from '../reports/reports'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'
import '../styles/notes.css'
import '../styles/reports.css'

/*
 * Reports (W4.6, view only) like the Control Center's Report Plugin: the readable reports grouped by site on the
 * left, the selected report (?id=) with its outputs, downloads and PDF preview on the right. On phone width the
 * list comes first; selecting shows the report with a back button.
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const narrow = useNarrow()

const { data, state, reload } = useLoader(() => loadReports(auth.http))

const search = ref('')
const all = computed<ReportSummary[]>(() => data.value?.reports ?? [])
const shown = computed(() => filterReports(all.value, search.value, locale.value))
const groups = computed(() => groupReports(shown.value, locale.value))

const selectedId = computed(() => parseReportId(route.query.id))
const selected = computed(() => (selectedId.value == null ? null : (all.value.find((r) => r.id === selectedId.value) ?? null)))

const formatTime = (ms: number) => formatDateTime(ms, locale.value)
const latestText = (r: ReportSummary) => (r.latest ? outputTime(r.latest.timestamp, formatTime) : t('reports.noOutput'))
const itemId = (id: number) => `report-item-${id}`

async function select(r: ReportSummary) {
  if (!hasOutput(r)) return
  await router.replace({ query: { ...route.query, id: String(r.id) } })
  if (narrow.value) {
    await nextTick()
    document.getElementById('report-detail-title')?.focus()
  }
}

async function back() {
  const previous = selectedId.value
  const query = { ...route.query }
  delete query.id
  await router.replace({ query })
  await nextTick()
  if (previous != null) document.getElementById(itemId(previous))?.focus()
}

const showList = computed(() => !narrow.value || selectedId.value == null)
const showDetail = computed(() => !narrow.value || selectedId.value != null)
</script>

<template>
  <section class="iso reports" aria-labelledby="reports-title">
    <div class="mod-head">
      <h1 id="reports-title">{{ t('reports.title') }}</h1>
    </div>
    <p class="muted read-only">{{ t('reports.readOnly') }}</p>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('reports.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <p v-if="data && !all.length" class="muted">{{ t('reports.empty') }}</p>

    <div v-else-if="data" class="rep-layout" :class="{ 'is-narrow': narrow }">
      <nav v-if="showList" class="rep-side" :aria-label="t('reports.listLabel')">
        <div class="mod-filters rep-search">
          <div class="control search">
            <label for="reports-search">{{ t('modules.search') }}</label>
            <input id="reports-search" v-model="search" type="search" :placeholder="t('reports.searchPlaceholder')" />
          </div>
        </div>
        <p class="mod-count muted" role="status">{{ t('reports.count', { shown: shown.length, total: all.length }) }}</p>
        <p v-if="!shown.length" class="muted">{{ t('reports.noMatch') }}</p>
        <div v-for="g in groups" :key="g.key" class="rep-group">
          <h2 :id="'report-group-' + g.key" class="rep-group-title">{{ g.site ?? t('modules.noSite') }}</h2>
          <ul class="rep-list" :aria-labelledby="'report-group-' + g.key">
            <li v-for="r in g.reports" :key="r.id">
              <button
                :id="itemId(r.id)"
                type="button"
                class="rep-item"
                :class="{ 'is-selected': r.id === selectedId, 'is-empty': !hasOutput(r) }"
                :aria-current="r.id === selectedId ? 'true' : undefined"
                :disabled="!hasOutput(r)"
                @click="select(r)"
              >
                <span v-if="r.path" class="rep-item-path">{{ r.path }}</span>
                <span class="rep-item-name">{{ r.name }}</span>
                <span class="rep-item-meta">{{ latestText(r) }}</span>
              </button>
            </li>
          </ul>
        </div>
      </nav>

      <div v-if="showDetail" class="rep-main">
        <button v-if="narrow" type="button" class="notes-btn rep-back" @click="back">
          <span aria-hidden="true">‹ </span>{{ t('reports.back') }}
        </button>
        <ReportDetails v-if="selected" :key="selected.id" :report="selected" heading-id="report-detail-title" />
        <div v-else-if="selectedId != null" class="alert alert-error rep-missing" role="alert">{{ t('reports.notFound') }}</div>
        <p v-else class="muted rep-placeholder">{{ t('reports.select') }}</p>
      </div>
    </div>
  </section>
</template>
