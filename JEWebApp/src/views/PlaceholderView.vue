<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { localName, type NavItem } from '../api/navigation'
import { useNavigationStore } from '../stores/navigation'

const props = defineProps<{ kind: 'analysis' | 'dashboard'; id: number }>()
const { t, locale } = useI18n()
const nav = useNavigationStore()

const item = computed<NavItem | undefined>(() => {
  const data = nav.data
  if (!data) return undefined
  const lists =
    props.kind === 'analysis'
      ? [...data.sites.map((s) => s.analyses), data.other.analyses]
      : [...data.sites.map((s) => s.dashboards), data.other.dashboards]
  return lists.flat().find((i) => i.id === props.id)
})
</script>

<template>
  <section aria-labelledby="ph-title">
    <h1 id="ph-title">{{ item ? localName(item, locale) : '…' }}</h1>
    <p class="alert alert-ok">{{ kind === 'analysis' ? t('placeholder.analysis') : t('placeholder.dashboard') }}</p>
    <p class="muted">{{ t('placeholder.openInCC', { id }) }}</p>
  </section>
</template>
