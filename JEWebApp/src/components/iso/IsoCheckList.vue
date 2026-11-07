<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CheckList } from '../../api/iso'
import { humanizeKey } from '../../iso/format'

/* Check list as stored: yes / no for booleans, text values as they are; unknown keys get a readable label. */

const props = defineProps<{ checkList: CheckList | null; labels: string }>()
const { t, te } = useI18n()

const entries = computed(() =>
  Object.entries(props.checkList ?? {}).map(([key, value]) => {
    const labelKey = `${props.labels}.${key}`
    const label = te(labelKey) ? t(labelKey) : humanizeKey(key)
    if (typeof value === 'boolean') return { key, label, text: t(value ? 'iso.yes' : 'iso.no'), state: value ? 'yes' : 'no' }
    return { key, label, text: value == null || value === '' ? t('modules.none') : String(value), state: 'text' }
  }),
)
</script>

<template>
  <p v-if="!entries.length" class="muted">{{ t('modules.none') }}</p>
  <dl v-else class="mod-dl iso-check">
    <template v-for="e in entries" :key="e.key">
      <dt>{{ e.label }}</dt>
      <dd :class="'is-' + e.state">{{ e.text }}</dd>
    </template>
  </dl>
</template>
