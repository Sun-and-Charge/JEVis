<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CONFIGURATION_FIELDS,
  CONFIGURATIONS_TAB,
  configurationValue,
  contractSites,
  contractTabs,
  contractWhere,
  defaultContractFilter,
  filterConfigurations,
  filterContractObjects,
  hasEntriesWithoutSite,
  type ContractFilter,
} from '../accounting/contracts'
import { loadContracts, type AccountingConfiguration } from '../api/contracts'
import ContractCard from '../components/accounting/ContractCard.vue'
import { useLoader } from '../iso/useLoader'
import type { SiteFilter } from '../modules/search'
import { useNarrow } from '../modules/useNarrow'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'
import '../styles/notes.css'
import '../styles/accounting.css'

/*
 * Contracts (W4.7, view only) like the Control Center's Accounting Plugin ("Enter data" / "Configuration"): one tab
 * per group (suppliers, metering point operators, grid operators, contractors, dues) and the accounting
 * configurations; search and site filter over all tabs. The cost calculation follows later.
 */

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const narrow = useNarrow()

const { data, state, reload } = useLoader(() => loadContracts(auth.http, locale.value))
watch(locale, () => reload())

const filter = reactive<ContractFilter>(defaultContractFilter())
const tabs = computed(() => contractTabs(data.value))
const sites = computed(() => contractSites(data.value, locale.value))
const withoutSite = computed(() => hasEntriesWithoutSite(data.value))
const total = computed(() => tabs.value.reduce((n, tab) => n + tab.objects.length + tab.configurations.length, 0))

const shownTabs = computed(() =>
  tabs.value.map((tab) => ({
    id: tab.id,
    total: tab.objects.length + tab.configurations.length,
    objects: filterContractObjects(tab.objects, filter, locale.value),
    configurations: filterConfigurations(tab.configurations, filter, locale.value),
  })),
)
const shownCount = (tab: { objects: unknown[]; configurations: unknown[] }) => tab.objects.length + tab.configurations.length

const activeId = ref<string | null>(null)
const active = computed(() => shownTabs.value.find((tab) => tab.id === activeId.value) ?? shownTabs.value[0] ?? null)
const highlighted = ref<number | null>(null)

/* after a load: keep the tab if it still exists */
watch(tabs, (list) => {
  if (!list.some((tab) => tab.id === activeId.value)) activeId.value = list[0]?.id ?? null
  if (typeof filter.site === 'number' && !sites.value.some((s) => s.id === filter.site)) filter.site = 'all'
})

const siteValue = computed({
  get: () => String(filter.site),
  set: (v: string) => {
    filter.site = (v === 'all' || v === 'none' ? v : Number(v)) as SiteFilter
  },
})

const tabLabel = (id: string) => (te('contracts.kind.' + id) ? t('contracts.kind.' + id) : id)
const tabDomId = (id: string) => `contracts-tab-${id}`
const panelId = 'contracts-panel'

function selectTab(id: string, focus = false) {
  activeId.value = id
  highlighted.value = null
  if (focus) nextTick(() => document.getElementById(tabDomId(id))?.focus())
}

/* arrow keys move between the tabs (WAI-ARIA tabs pattern, automatic activation) */
function onTabKey(e: KeyboardEvent, index: number) {
  const list = shownTabs.value
  let next = -1
  if (e.key === 'ArrowRight') next = (index + 1) % list.length
  else if (e.key === 'ArrowLeft') next = (index - 1 + list.length) % list.length
  else if (e.key === 'Home') next = 0
  else if (e.key === 'End') next = list.length - 1
  if (next < 0) return
  e.preventDefault()
  selectTab(list[next].id, true)
}

/** Contractor ids of the contractor tab (the jump target). */
const contractorIds = computed(() => new Set(tabs.value.find((tab) => tab.id === 'contractors')?.objects.map((o) => o.id) ?? []))

async function openContractor(id: number) {
  if (!contractorIds.value.has(id)) return
  const shown = shownTabs.value.find((tab) => tab.id === 'contractors')
  if (!shown?.objects.some((o) => o.id === id)) Object.assign(filter, defaultContractFilter())
  activeId.value = 'contractors'
  highlighted.value = id
  await nextTick()
  const el = document.getElementById('contract-obj-' + id)
  el?.scrollIntoView?.({ block: 'nearest' })
  el?.focus()
}

const energyName = (code: string) => (te('contracts.energy.' + code) ? t('contracts.energy.' + code) : code)
const configValue = (c: AccountingConfiguration, field: (typeof CONFIGURATION_FIELDS)[number]) => configurationValue(c, field, locale.value, energyName)
const configWhere = (c: AccountingConfiguration) => contractWhere(c, locale.value)
</script>

<template>
  <section class="iso contracts" aria-labelledby="contracts-title">
    <div class="mod-head">
      <h1 id="contracts-title">{{ t('contracts.title') }}</h1>
    </div>
    <p class="muted read-only">{{ t('contracts.readOnly') }}</p>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('contracts.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <p v-if="data && !total" class="muted">{{ t('contracts.empty') }}</p>

    <template v-else-if="data && active">
      <fieldset class="mod-filters iso-filters">
        <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
        <div class="control search">
          <label for="contracts-search">{{ t('modules.search') }}</label>
          <input id="contracts-search" v-model="filter.text" type="search" :placeholder="t('contracts.searchPlaceholder')" />
        </div>
        <div v-if="sites.length > 1 || (sites.length && withoutSite)" class="control">
          <label for="contracts-site">{{ t('modules.site') }}</label>
          <select id="contracts-site" v-model="siteValue">
            <option value="all">{{ t('modules.allSites') }}</option>
            <option v-for="s in sites" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
            <option v-if="withoutSite" value="none">{{ t('modules.noSite') }}</option>
          </select>
        </div>
      </fieldset>

      <div class="ctr-tabs" role="tablist" :aria-label="t('contracts.tabsLabel')">
        <button
          v-for="(tab, i) in shownTabs"
          :id="tabDomId(tab.id)"
          :key="tab.id"
          type="button"
          role="tab"
          class="ctr-tab"
          :aria-selected="tab.id === active.id"
          :aria-controls="panelId"
          :tabindex="tab.id === active.id ? 0 : -1"
          @click="selectTab(tab.id)"
          @keydown="onTabKey($event, i)"
        >
          {{ tabLabel(tab.id) }}
          <span class="ctr-tab-count">
            <span aria-hidden="true">{{ shownCount(tab) === tab.total ? tab.total : `${shownCount(tab)}/${tab.total}` }}</span>
            <span class="visually-hidden">{{ t('modules.count', { shown: shownCount(tab), total: tab.total }) }}</span>
          </span>
        </button>
      </div>

      <div :id="panelId" class="ctr-panel" role="tabpanel" :aria-labelledby="tabDomId(active.id)" tabindex="0">
        <p class="mod-count muted" role="status">{{ t('modules.count', { shown: shownCount(active), total: active.total }) }}</p>
        <p v-if="!shownCount(active)" class="muted">{{ t('contracts.noMatch') }}</p>

        <template v-else-if="active.id === CONFIGURATIONS_TAB">
          <p class="alert ctr-hint" role="note">{{ t('contracts.config.hint') }}</p>
          <ul v-if="narrow" class="mod-cards">
            <li v-for="c in active.configurations" :key="c.id" class="mod-card ctr-config-card">
              <p v-if="configWhere(c)" class="ctr-where muted">{{ configWhere(c) }}</p>
              <h2>{{ c.name }}</h2>
              <dl class="mod-dl">
                <template v-for="f in CONFIGURATION_FIELDS" :key="f">
                  <dt>{{ t('contracts.config.' + f) }}</dt>
                  <dd>{{ configValue(c, f) || t('modules.none') }}</dd>
                </template>
              </dl>
            </li>
          </ul>
          <div v-else class="mod-table-wrap">
            <table class="mod-table iso-table ctr-config-table">
              <caption class="visually-hidden">{{ t('contracts.kind.configurations') }}</caption>
              <thead>
                <tr>
                  <th scope="col">{{ t('contracts.config.name') }}</th>
                  <th v-for="f in CONFIGURATION_FIELDS" :key="f" scope="col">{{ t('contracts.config.' + f) }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in active.configurations" :key="c.id" class="mod-row">
                  <th scope="row" class="iso-text ctr-config-name">
                    <span v-if="configWhere(c)" class="notes-path">{{ configWhere(c) }}</span>
                    <span>{{ c.name }}</span>
                  </th>
                  <td v-for="f in CONFIGURATION_FIELDS" :key="f" :class="{ nowrap: f !== 'marketLocation' }">{{ configValue(c, f) || t('modules.none') }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>

        <ul v-else class="ctr-list">
          <li v-for="o in active.objects" :key="o.id">
            <ContractCard
              :object="o"
              :contractor-link="!!o.contractor && contractorIds.has(o.contractor.id)"
              :highlighted="highlighted === o.id"
              @open-contractor="openContractor"
            />
          </li>
        </ul>
      </div>
    </template>
  </section>
</template>
