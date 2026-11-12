<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AnalysisDirectory } from '../../../api/analysis'
import { nameProblem } from '../../../analysis/editor'
import EditDialog from '../../edit/EditDialog.vue'

/* Name and directory of an analysis (E3): "Speichern unter" and "Neue Analyse" (as the CC's SaveAnalysisDialog). */

const props = defineProps<{
  title: string
  confirmLabel: string
  /** the directories the user may create analyses in */
  directories: readonly AnalysisDirectory[]
  name: string
  directoryId: number | null
  busy?: boolean
  error?: string | null
  /** "Neue Analyse": the name is taken, link to the existing analysis */
  takenId?: number | null
}>()
const emit = defineEmits<{ close: []; submit: [value: { name: string; directoryId: number }]; open: [id: number] }>()
const { t } = useI18n()

const uid = `and-${Math.random().toString(36).slice(2, 9)}`
const name = ref(props.name)
const directoryId = ref<number | null>(props.directoryId ?? props.directories[0]?.id ?? null)
const problem = ref<string | null>(null)

const label = (d: AnalysisDirectory) => d.path || d.name
const sorted = computed(() => [...props.directories].sort((a, b) => label(a).localeCompare(label(b))))

function submit() {
  problem.value = nameProblem(name.value)
  if (problem.value || directoryId.value == null) return
  emit('submit', { name: name.value.trim(), directoryId: directoryId.value })
}
</script>

<template>
  <EditDialog :title="title" :busy="busy" @close="emit('close')">
    <form :id="uid + '-form'" novalidate @submit.prevent="submit">
      <p v-if="!directories.length" class="alert alert-error" role="alert">{{ t('analysisEdit.nameDialog.noDirectory') }}</p>
      <div class="edit-field">
        <label :for="uid + '-name'">{{ t('analysisEdit.nameDialog.name') }}</label>
        <input
          :id="uid + '-name'"
          v-model="name"
          type="text"
          maxlength="100"
          autocomplete="off"
          autofocus
          :disabled="busy"
          :aria-invalid="problem ? 'true' : undefined"
          :aria-describedby="problem ? uid + '-err' : undefined"
          @input="problem = null"
        />
        <p v-if="problem" :id="uid + '-err'" class="ae-error iso-field-error">{{ t(problem) }}</p>
      </div>
      <div class="edit-field">
        <label :for="uid + '-dir'">{{ t('analysisEdit.nameDialog.directory') }}</label>
        <select :id="uid + '-dir'" v-model="directoryId" :disabled="busy || !directories.length">
          <option v-for="d in sorted" :key="d.id" :value="d.id">{{ label(d) }}</option>
        </select>
      </div>
      <div v-if="takenId != null" class="alert alert-error" role="alert">
        {{ t('analysisEdit.nameDialog.taken') }}
        <button type="button" class="btn-link and-open" @click="emit('open', takenId!)">{{ t('analysisEdit.nameDialog.openExisting') }}</button>
      </div>
      <p v-else-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="edit-btn" :disabled="busy" @click="emit('close')">{{ t('edit.cancel') }}</button>
      <button type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="busy || !directories.length">
        {{ busy ? t('edit.saving') : confirmLabel }}
      </button>
    </template>
  </EditDialog>
</template>
