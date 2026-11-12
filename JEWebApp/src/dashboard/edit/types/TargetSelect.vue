<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Target } from './targets'

/* Selection of a link target (0 = none). A stored target that is not in the list (not readable) stays selectable. */

const props = defineProps<{ id: string; label: string; targets: readonly Target[]; value: number; disabled?: boolean; loading?: boolean }>()
const emit = defineEmits<{ change: [id: number] }>()
const { t } = useI18n()
const unknown = computed(() => props.value > 0 && !props.targets.some((x) => x.id === props.value))
</script>

<template>
  <div class="edit-field ae-wide">
    <label :for="id">{{ label }}</label>
    <select :id="id" :value="value > 0 ? value : 0" :disabled="disabled" @change="emit('change', Number(($event.target as HTMLSelectElement).value) || 0)">
      <option :value="0">{{ loading ? t('dashboardEdit.types.common.loading') : t('dashboardEdit.types.common.noTarget') }}</option>
      <option v-for="x in targets" :key="x.id" :value="x.id">{{ x.label }}</option>
      <option v-if="unknown" :value="value">{{ t('dashboardEdit.types.common.unknownTarget', { id: value }) }}</option>
    </select>
  </div>
</template>
