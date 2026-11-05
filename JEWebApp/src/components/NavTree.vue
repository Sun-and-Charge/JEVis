<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { itemCount, localName, type SiteNode } from '../api/navigation'

const props = defineProps<{ node: SiteNode; level?: number }>()
const emit = defineEmits<{ navigate: [] }>()
const { t, locale } = useI18n()
/* the first two levels are open, deeper ones closed */
const open = ref((props.level ?? 0) < 2)
</script>

<template>
  <li v-if="itemCount(node) > 0" class="site">
    <button type="button" class="site-btn" :aria-expanded="open" @click="open = !open">
      <span class="chevron" :class="{ open }" aria-hidden="true">›</span>
      <span class="site-name">{{ localName(node.site, locale) }}</span>
    </button>
    <ul v-show="open" class="items">
      <li v-if="node.site.dashboards.length" class="group">{{ t('nav.dashboards') }}</li>
      <li v-for="d in node.site.dashboards" :key="'d' + d.id">
        <RouterLink :to="{ name: 'dashboard', params: { id: d.id } }" class="item" @click="emit('navigate')">{{ localName(d, locale) }}</RouterLink>
      </li>
      <li v-if="node.site.analyses.length" class="group">{{ t('nav.analyses') }}</li>
      <li v-for="a in node.site.analyses" :key="'a' + a.id">
        <RouterLink :to="{ name: 'analysis', params: { id: a.id } }" class="item" @click="emit('navigate')">{{ localName(a, locale) }}</RouterLink>
      </li>
      <NavTree v-for="child in node.children" :key="child.site.id" :node="child" :level="(level ?? 0) + 1" @navigate="emit('navigate')" />
    </ul>
  </li>
</template>

<style scoped>
.site {
  list-style: none;
}

.site-btn {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  width: 100%;
  padding: 0.45rem 0.5rem;
  font: inherit;
  font-weight: 600;
  color: inherit;
  background: none;
  border: none;
  border-radius: 4px;
  text-align: left;
  cursor: pointer;
}

.site-btn:hover {
  background: rgba(255, 255, 255, 0.08);
}

.chevron {
  display: inline-block;
  width: 1em;
  transition: transform 0.15s;
}

.chevron.open {
  transform: rotate(90deg);
}

.items {
  margin: 0;
  padding: 0 0 0 1rem;
}

.group {
  list-style: none;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.6;
  padding: 0.5rem 0.5rem 0.2rem;
}

.item {
  display: block;
  padding: 0.35rem 0.5rem;
  border-radius: 4px;
  color: inherit;
  text-decoration: none;
}

.item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.item.router-link-active {
  background: var(--sun);
  color: var(--blueberry);
  font-weight: 600;
}

li {
  list-style: none;
}
</style>
