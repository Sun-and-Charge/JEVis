<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { effectiveSite, siteChoices } from '../../nav/catalog'
import { useNavigationStore } from '../../stores/navigation'
import { useNavPrefsStore } from '../../stores/navPrefs'

/*
 * "Standort" in the top bar: Alle or one site (sub-sites indented). The choice is remembered per user and is the
 * default filter of the overview pages and the preselected tab of module views with one tab per site.
 */

const { t, locale } = useI18n()
const nav = useNavigationStore()
const prefs = useNavPrefsStore()

const choices = computed(() => siteChoices(nav.data, locale.value))
const indent = (depth: number) => '   '.repeat(depth)

const value = computed({
  get: () => String(effectiveSite(prefs.site, nav.data)),
  set: (v: string) => prefs.setSite(v === 'all' ? 'all' : Number(v)),
})
</script>

<template>
  <div v-if="choices.length > 1" class="site-picker">
    <label for="site-picker" class="site-label">{{ t('nav.site') }}</label>
    <select id="site-picker" v-model="value" :title="t('nav.siteLabel')">
      <option value="all">{{ t('nav.allSites') }}</option>
      <option v-for="s in choices" :key="s.id" :value="String(s.id)">{{ indent(s.depth) + s.name }}</option>
    </select>
  </div>
</template>

<style scoped>
.site-picker {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

.site-label {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 600;
  opacity: 0.85;
  white-space: nowrap;
}

select {
  max-width: 14rem;
  min-width: 0;
  padding: 0.3rem 0.5rem;
  font: inherit;
  font-size: 0.9rem;
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 4px;
  cursor: pointer;
}

select option {
  color: var(--ink);
  background: var(--surface);
}

@media (max-width: 800px) {
  .site-label {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }

  select {
    max-width: 7.5rem;
  }
}
</style>
