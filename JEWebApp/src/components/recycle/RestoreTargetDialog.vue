<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AnalysisDirectory } from '../../api/analysis'
import EditDialog from '../edit/EditDialog.vue'

/*
 * "Ziel für die Wiederherstellung wählen" (as the Control Center): the original place of a recycle bin entry is gone,
 * so the user picks a directory he may create analyses / dashboards in.
 */

const props = defineProps<{
  name: string
  /** the directories with create right; null while loading */
  directories: readonly AnalysisDirectory[] | null
  busy?: boolean
  error?: string | null
}>()
const emit = defineEmits<{ close: []; submit: [directoryId: number] }>()
const { t } = useI18n()

const uid = `rtd-${Math.random().toString(36).slice(2, 9)}`
const label = (d: AnalysisDirectory) => d.path || d.name
const sorted = computed(() => [...(props.directories ?? [])].sort((a, b) => label(a).localeCompare(label(b))))
const directoryId = ref<number | null>(sorted.value[0]?.id ?? null)

/* the directories may arrive after the dialog opened */
watch(sorted, (list) => {
  if (directoryId.value == null || !list.some((d) => d.id === directoryId.value)) directoryId.value = list[0]?.id ?? null
})

function submit() {
  if (directoryId.value != null) emit('submit', directoryId.value)
}
</script>

<template>
  <EditDialog :title="t('recycle.target.title')" :busy="busy" :described-by="uid + '-msg'" @close="emit('close')">
    <form :id="uid + '-form'" novalidate @submit.prevent="submit">
      <p :id="uid + '-msg'" class="edit-message">{{ t('recycle.target.message', { name }) }}</p>
      <p v-if="directories === null" class="muted" role="status">{{ t('recycle.target.loading') }}</p>
      <p v-else-if="!directories.length" class="alert alert-error" role="alert">{{ t('recycle.target.none') }}</p>
      <div v-else class="edit-field">
        <label :for="uid + '-dir'">{{ t('recycle.target.directory') }}</label>
        <select :id="uid + '-dir'" v-model="directoryId" :disabled="busy" autofocus>
          <option v-for="d in sorted" :key="d.id" :value="d.id">{{ label(d) }}</option>
        </select>
      </div>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="edit-btn" :disabled="busy" @click="emit('close')">{{ t('edit.cancel') }}</button>
      <button type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="busy || directoryId == null">
        {{ busy ? t('edit.saving') : t('recycle.restore') }}
      </button>
    </template>
  </EditDialog>
</template>
