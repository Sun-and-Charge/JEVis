<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { catalog, effectiveSite, entryRoute, filterCatalog, recentEntries, siteChoices, type CatalogEntry, type OverviewFilter } from '../../nav/catalog'
import type { ItemKind } from '../../nav/prefs'
import { useNavigationStore } from '../../stores/navigation'
import { useNavPrefsStore } from '../../stores/navPrefs'
import NavIcon from './NavIcon.vue'
import '../../styles/overview.css'

/*
 * Overview of all dashboards or analyses (pages "Dashboards" / "Analysen"): cards (a list on phone width) with the
 * site path and a favourite star, search by name, site filter (default: the site of the top bar, sub-sites
 * included), "Nur Favoriten" and the recently opened items. Items without site follow under "Weitere". The slot
 * `actions` takes the page's buttons ("Neue Analyse", later "Neues Dashboard").
 */

const props = defineProps<{ kind: ItemKind }>()
const { t, locale } = useI18n()
const nav = useNavigationStore()
const prefs = useNavPrefsStore()

const filter = reactive<OverviewFilter>({ text: '', site: effectiveSite(prefs.site, nav.data), favouritesOnly: false })

/* the site of the top bar is the default; changing it there changes the filter here (the navigation may load later) */
watch(
  () => effectiveSite(prefs.site, nav.data),
  (site) => {
    filter.site = site
  },
)

const title = computed(() => t(props.kind === 'dashboard' ? 'overview.dashboards' : 'overview.analyses'))
const entries = computed(() => catalog(nav.data, props.kind, locale.value))
const shown = computed(() => filterCatalog(entries.value, filter, prefs.favouriteSet, nav.data, locale.value))
const withSite = computed(() => shown.value.filter((e) => e.siteId != null))
const other = computed(() => shown.value.filter((e) => e.siteId == null))
const sites = computed(() => siteChoices(nav.data, locale.value))
const recent = computed(() => recentEntries([...catalog(nav.data, 'dashboard', locale.value), ...catalog(nav.data, 'analysis', locale.value)], prefs.recent))

const siteValue = computed({
  get: () => String(filter.site),
  set: (v: string) => {
    filter.site = v === 'all' ? 'all' : Number(v)
  },
})

const indent = (depth: number) => '   '.repeat(depth)
const uid = 'ov-' + props.kind
const starLabel = (e: CatalogEntry) => t(prefs.isFavourite(e.key) ? 'overview.removeFavourite' : 'overview.addFavourite', { name: e.name })
</script>

<template>
  <section class="overview" :aria-labelledby="uid + '-title'">
    <div class="ov-head">
      <h1 :id="uid + '-title'">{{ title }}</h1>
      <span v-if="entries.length" class="muted ov-total">{{ t('overview.count', { shown: shown.length, total: entries.length }) }}</span>
      <div class="ov-actions"><slot name="actions" /></div>
    </div>

    <p v-if="nav.loading && !nav.data" class="muted" role="status">{{ t('nav.loading') }}</p>
    <p v-else-if="nav.failed" class="alert alert-error" role="alert">{{ t('nav.error') }}</p>
    <p v-else-if="nav.data && !entries.length" class="muted">{{ t(kind === 'dashboard' ? 'overview.noDashboards' : 'overview.noAnalyses') }}</p>

    <template v-else-if="nav.data">
      <div class="ov-filters" role="search" :aria-label="t('modules.filters')">
        <div class="ov-control ov-search">
          <label :for="uid + '-search'" class="visually-hidden">{{ t('overview.searchLabel') }}</label>
          <NavIcon name="search" />
          <input :id="uid + '-search'" v-model="filter.text" type="search" :placeholder="t('overview.searchPlaceholder')" autocomplete="off" />
        </div>
        <div v-if="sites.length > 1" class="ov-control">
          <label :for="uid + '-site'">{{ t('overview.site') }}</label>
          <select :id="uid + '-site'" v-model="siteValue">
            <option value="all">{{ t('overview.allSites') }}</option>
            <option v-for="s in sites" :key="s.id" :value="String(s.id)">{{ indent(s.depth) + s.name }}</option>
          </select>
        </div>
        <button type="button" class="ov-toggle" :aria-pressed="filter.favouritesOnly" @click="filter.favouritesOnly = !filter.favouritesOnly">
          <NavIcon name="star" :filled="filter.favouritesOnly" /> {{ t('overview.favouritesOnly') }}
        </button>
      </div>

      <section v-if="recent.length" class="ov-recent" :aria-labelledby="uid + '-recent'">
        <h2 :id="uid + '-recent'">{{ t('overview.recent') }}:</h2>
        <ul>
          <li v-for="e in recent" :key="e.key">
            <RouterLink :to="entryRoute(e)" :title="e.sitePath ?? undefined">
              <NavIcon :name="e.kind" /><span class="visually-hidden">{{ t('overview.kind.' + e.kind) }}: </span>{{ e.name }}
            </RouterLink>
          </li>
        </ul>
      </section>

      <p v-if="!shown.length" class="muted" role="status">{{ t('overview.empty') }}</p>
      <ul v-if="withSite.length" class="ov-grid">
        <li v-for="e in withSite" :key="e.key" class="ov-card">
          <RouterLink :to="entryRoute(e)" class="ov-link">
            <span class="ov-name">{{ e.name }}</span>
            <span class="ov-site">{{ e.sitePath }}</span>
          </RouterLink>
          <button type="button" class="ov-star" :aria-pressed="prefs.isFavourite(e.key)" :aria-label="t('overview.favouriteOf', { name: e.name })" :title="starLabel(e)" @click="prefs.toggleFavourite(e.key)">
            <NavIcon name="star" :filled="prefs.isFavourite(e.key)" />
          </button>
        </li>
      </ul>
      <section v-if="other.length" class="ov-other" :aria-labelledby="uid + '-other'">
        <h2 :id="uid + '-other'">{{ t('overview.other') }}</h2>
        <ul class="ov-grid">
          <li v-for="e in other" :key="e.key" class="ov-card">
            <RouterLink :to="entryRoute(e)" class="ov-link">
              <span class="ov-name">{{ e.name }}</span>
              <span class="ov-site">{{ t('overview.noSite') }}</span>
            </RouterLink>
            <button type="button" class="ov-star" :aria-pressed="prefs.isFavourite(e.key)" :aria-label="t('overview.favouriteOf', { name: e.name })" :title="starLabel(e)" @click="prefs.toggleFavourite(e.key)">
              <NavIcon name="star" :filled="prefs.isFavourite(e.key)" />
            </button>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>
