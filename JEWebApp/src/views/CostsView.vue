<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { loadCosts } from '../api/costs'
import CostDetails from '../components/costs/CostDetails.vue'
import { costEntries, filterCostEntries, groupBySite, isCostStateKey, parseCostId, type CostEntry } from '../costs/costs'
import { useLoader } from '../iso/useLoader'
import { useNarrow } from '../modules/useNarrow'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'
import '../styles/notes.css'
import '../styles/reports.css'
import '../styles/costs.css'

/*
 * Cost and result calculation (W4.8, view only) like the Control Center's Accounting Plugin tab "View" and the
 * Template Result Calculation plugin: accounting configurations and templates grouped by site on the left, the
 * selected one (?id=) with time frame, inputs and the calculated result on the right. On phone width the list comes
 * first; selecting shows the calculation with a back button.
 */

const { t, locale } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const narrow = useNarrow()

const { data, state, reload } = useLoader(() => loadCosts(auth.http))

const search = ref('')
const all = computed<CostEntry[]>(() => costEntries(data.value))
const shown = computed(() => filterCostEntries(all.value, search.value, locale.value))
const sections = computed(() =>
  (['configuration', 'template'] as const)
    .map((kind) => ({ kind, groups: groupBySite(shown.value.filter((e) => e.kind === kind), locale.value) }))
    .filter((s) => s.groups.length),
)

const selectedId = computed(() => parseCostId(route.query.id))
const selected = computed(() => (selectedId.value == null ? null : (all.value.find((e) => e.id === selectedId.value) ?? null)))

const itemId = (id: number) => `cost-item-${id}`

/** Query without the settings of the previous selection. */
function baseQuery() {
  const query = { ...route.query }
  for (const key of Object.keys(query)) if (isCostStateKey(key)) delete query[key]
  return query
}

async function select(e: CostEntry) {
  if (e.id !== selectedId.value) await router.replace({ query: { ...baseQuery(), id: String(e.id) } })
  if (narrow.value) {
    await nextTick()
    document.getElementById('cost-detail-title')?.focus()
  }
}

async function back() {
  const previous = selectedId.value
  const query = baseQuery()
  delete query.id
  await router.replace({ query })
  await nextTick()
  if (previous != null) document.getElementById(itemId(previous))?.focus()
}

const showList = computed(() => !narrow.value || selectedId.value == null)
const showDetail = computed(() => !narrow.value || selectedId.value != null)
</script>

<template>
  <section class="iso reports costs" aria-labelledby="costs-title">
    <div class="mod-head">
      <h1 id="costs-title">{{ t('costs.title') }}</h1>
    </div>
    <p class="muted read-only">{{ t('costs.readOnly') }}</p>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('costs.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <p v-if="data && !all.length" class="muted">{{ t('costs.empty') }}</p>

    <div v-else-if="data" class="rep-layout cost-layout" :class="{ 'is-narrow': narrow }">
      <nav v-if="showList" class="rep-side" :aria-label="t('costs.listLabel')">
        <div class="mod-filters rep-search">
          <div class="control search">
            <label for="costs-search">{{ t('modules.search') }}</label>
            <input id="costs-search" v-model="search" type="search" :placeholder="t('costs.searchPlaceholder')" />
          </div>
        </div>
        <p class="mod-count muted" role="status">{{ t('costs.count', { shown: shown.length, total: all.length }) }}</p>
        <p v-if="!shown.length" class="muted">{{ t('costs.noMatch') }}</p>
        <section v-for="s in sections" :key="s.kind" class="cost-section" :aria-labelledby="'cost-section-' + s.kind">
          <h2 :id="'cost-section-' + s.kind" class="cost-section-title">{{ t('costs.section.' + s.kind) }}</h2>
          <div v-for="g in s.groups" :key="g.key" class="rep-group">
            <h3 :id="'cost-group-' + s.kind + '-' + g.key" class="rep-group-title">{{ g.site ?? t('modules.noSite') }}</h3>
            <ul class="rep-list" :aria-labelledby="'cost-group-' + s.kind + '-' + g.key">
              <li v-for="e in g.entries" :key="e.id">
                <button
                  :id="itemId(e.id)"
                  type="button"
                  class="rep-item cost-item"
                  :class="{ 'is-selected': e.id === selectedId, 'is-empty': !e.templateReadable }"
                  :data-kind="e.kind"
                  :aria-current="e.id === selectedId ? 'true' : undefined"
                  @click="select(e)"
                >
                  <span v-if="e.path" class="rep-item-path">{{ e.path }}</span>
                  <span class="rep-item-name">{{ e.name }}</span>
                  <span v-if="e.kind === 'configuration'" class="rep-item-meta">
                    {{ e.templateReadable ? t('costs.templateOf', { name: e.templateName || t('modules.none') }) : t('costs.templateMissing') }}
                  </span>
                </button>
              </li>
            </ul>
          </div>
        </section>
      </nav>

      <div v-if="showDetail" class="rep-main">
        <button v-if="narrow" type="button" class="notes-btn rep-back" @click="back">
          <span aria-hidden="true">‹ </span>{{ t('costs.back') }}
        </button>
        <CostDetails v-if="selected" :key="selected.id" :entry="selected" heading-id="cost-detail-title" />
        <div v-else-if="selectedId != null" class="alert alert-error rep-missing" role="alert">{{ t('costs.notFound') }}</div>
        <p v-else class="muted rep-placeholder">{{ t('costs.select') }}</p>
      </div>
    </div>
  </section>
</template>
