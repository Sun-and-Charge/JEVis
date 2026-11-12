<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NavIcon from '../components/nav/NavIcon.vue'
import { catalog, entryRoute, favouriteEntries, recentEntries } from '../nav/catalog'
import { useNavigationStore } from '../stores/navigation'
import { useNavPrefsStore } from '../stores/navPrefs'
import '../styles/overview.css'

/*
 * Start page for users without a start dashboard (the shell opens that one after login): the favourite and the
 * recently opened dashboards and analyses.
 */

const { t, locale } = useI18n()
const nav = useNavigationStore()
const prefs = useNavPrefsStore()

const all = computed(() => [...catalog(nav.data, 'dashboard', locale.value), ...catalog(nav.data, 'analysis', locale.value)])
const favourites = computed(() => favouriteEntries(all.value, prefs.favourites))
const recent = computed(() => recentEntries(all.value, prefs.recent))
</script>

<template>
  <section aria-labelledby="home-title">
    <h1 id="home-title">{{ t('home.title') }}</h1>
    <p>{{ t('home.text') }}</p>

    <template v-if="nav.data">
      <section class="home-section home-favourites" aria-labelledby="home-favourites">
        <h2 id="home-favourites">{{ t('home.favourites') }}</h2>
        <ul v-if="favourites.length" class="ov-grid">
          <li v-for="e in favourites" :key="e.key" class="ov-card">
            <RouterLink :to="entryRoute(e)" class="ov-link">
              <span class="ov-name">{{ e.name }}</span>
              <span class="ov-site"><span class="visually-hidden">{{ t('overview.kind.' + e.kind) }} · </span>{{ e.sitePath ?? t('overview.other') }}</span>
            </RouterLink>
            <button type="button" class="ov-star" aria-pressed="true" :aria-label="t('overview.favouriteOf', { name: e.name })" :title="t('overview.removeFavourite', { name: e.name })" @click="prefs.toggleFavourite(e.key)">
              <NavIcon name="star" filled />
            </button>
          </li>
        </ul>
        <p v-else class="muted">{{ t('home.noFavourites') }}</p>
      </section>

      <section class="home-section home-recent" aria-labelledby="home-recent">
        <h2 id="home-recent">{{ t('home.recent') }}</h2>
        <div v-if="recent.length" class="ov-recent">
          <ul>
            <li v-for="e in recent" :key="e.key">
              <RouterLink :to="entryRoute(e)" :title="e.sitePath ?? undefined">
                <NavIcon :name="e.kind" /><span class="visually-hidden">{{ t('overview.kind.' + e.kind) }}: </span>{{ e.name }}
              </RouterLink>
            </li>
          </ul>
        </div>
        <p v-else class="muted">{{ t('home.noRecent') }}</p>
      </section>

      <p class="home-links">
        <RouterLink v-if="nav.counts.dashboards" :to="{ name: 'dashboards' }">{{ t('home.allDashboards') }}</RouterLink>
        <RouterLink v-if="nav.counts.analyses" :to="{ name: 'analyses' }">{{ t('home.allAnalyses') }}</RouterLink>
      </p>
      <p class="muted">{{ t('home.count', nav.counts) }}</p>
    </template>
  </section>
</template>
