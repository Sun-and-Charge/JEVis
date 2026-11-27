<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Right } from '../../api/admin'
import { RIGHTS, toggleRight } from '../../admin/admin'

/*
 * The five membership rights (LESEN, SCHREIBEN, AUSFÜHREN, ERSTELLEN, LÖSCHEN) of one membership as checkboxes in
 * one table row or line. `of` names the membership for screen readers ("Lesen – Gruppe Energieteam"). Read-only
 * memberships show the boxes disabled.
 */

const props = defineProps<{ modelValue: readonly Right[]; of: string; disabled?: boolean; cells?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [rights: Right[]] }>()
const { t } = useI18n()

function set(right: Right, e: Event) {
  emit('update:modelValue', toggleRight(props.modelValue, right, (e.target as HTMLInputElement).checked))
}
</script>

<template>
  <template v-if="cells">
    <td v-for="r in RIGHTS" :key="r" class="adm-right-cell">
      <input
        type="checkbox"
        :checked="modelValue.includes(r)"
        :disabled="disabled"
        :aria-label="t('admin.rights.' + r) + ' – ' + of"
        :title="t('admin.rights.' + r)"
        @change="set(r, $event)"
      />
    </td>
  </template>
  <span v-else class="adm-rights" role="group" :aria-label="t('admin.rights.label', { name: of })">
    <label v-for="r in RIGHTS" :key="r" class="adm-right">
      <input type="checkbox" :checked="modelValue.includes(r)" :disabled="disabled" @change="set(r, $event)" />
      {{ t('admin.rights.' + r) }}
    </label>
  </span>
</template>
