<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DueState } from '../../api/readings'

/* Due state of a meter: Überfällig red, Fällig amber, OK neutral, "Kein Turnus" grey (the text carries the state). */

const props = defineProps<{ due: DueState | string }>()
const { t, te } = useI18n()

const CLASSES: Record<string, string> = { OVERDUE: 'is-overdue', DUE: 'is-dueSoon', OK: 'is-ok', NONE: 'is-none', AUTOMATIC: 'is-none' }
const cls = computed(() => CLASSES[props.due] ?? 'is-none')
const label = computed(() => (te('readings.due.' + props.due) ? t('readings.due.' + props.due) : t('readings.due.NONE')))
</script>

<template>
  <span class="mod-badge rd-due" :class="cls">{{ label }}</span>
</template>
