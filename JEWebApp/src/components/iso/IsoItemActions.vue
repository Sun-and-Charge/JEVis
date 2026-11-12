<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ItemRights } from '../../api/iso'
import '../../styles/edit.css'
import '../../styles/isoEdit.css'

/* "Bearbeiten" / "Löschen" of an item in its detail panel (E2), only with the right and a version to send back. */

const props = defineProps<{ item: ItemRights; label: string }>()
const emit = defineEmits<{ edit: []; remove: [] }>()
const { t } = useI18n()

const mayEdit = computed(() => !!props.item.canWrite && !!props.item.version)
const mayDelete = computed(() => !!props.item.canDelete && !!props.item.version)
</script>

<template>
  <div v-if="mayEdit || mayDelete" class="iso-item-actions">
    <button v-if="mayEdit" type="button" class="edit-btn iso-edit" :aria-label="t('isoEdit.editLabel', { title: label })" @click="emit('edit')">
      {{ t('isoEdit.edit') }}
    </button>
    <button v-if="mayDelete" type="button" class="edit-btn is-danger iso-delete" :aria-label="t('isoEdit.deleteLabel', { title: label })" @click="emit('remove')">
      {{ t('isoEdit.delete') }}
    </button>
  </div>
</template>
