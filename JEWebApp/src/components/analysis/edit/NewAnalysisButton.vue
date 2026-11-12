<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { createAnalysis } from '../../../api/analysis'
import { emptyDefinition, nameTakenId } from '../../../analysis/editor'
import { useBusy, writeErrorText } from '../../../edit/edit'
import { useAuthStore } from '../../../stores/auth'
import { useNavigationStore } from '../../../stores/navigation'
import AnalysisNameDialog from './AnalysisNameDialog.vue'

/*
 * "Neue Analyse" in the header of the analyses overview (E3, shown when a directory allows creating analyses): name
 * and directory, then POST /web/analyses with an empty analysis (one line chart) and the new analysis opens in edit
 * mode. A taken name links to the existing analysis (overwriting it with an empty one would only lose data).
 */

const emit = defineEmits<{ navigate: [] }>()
const { t, locale } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()
const router = useRouter()
const { busy, run } = useBusy()

const open = ref(false)
const error = ref<string | null>(null)
const takenId = ref<number | null>(null)

onMounted(() => void nav.loadDirectories())

function start() {
  error.value = null
  takenId.value = null
  open.value = true
}

async function create(value: { name: string; directoryId: number }) {
  error.value = null
  takenId.value = null
  await run(async () => {
    try {
      const created = await createAnalysis(auth.http, locale.value, value.directoryId, value.name, emptyDefinition(t('analysisEdit.chartName', { n: 1 })))
      open.value = false
      void nav.load()
      emit('navigate')
      await router.push({ name: 'analysis', params: { id: created.id }, query: { edit: '1' } })
    } catch (e) {
      const taken = nameTakenId(e)
      if (taken !== null) takenId.value = taken
      else error.value = writeErrorText(e, t, 'analysisEdit.conflict')
    }
  })
}

async function openExisting(id: number) {
  open.value = false
  emit('navigate')
  await router.push({ name: 'analysis', params: { id } })
}
</script>

<template>
  <button v-if="nav.creatableDirectories.length" type="button" class="btn btn-primary new-analysis" @click="start">
    <span aria-hidden="true">+</span> {{ t('nav.newAnalysis') }}
  </button>
  <Teleport to="body">
    <AnalysisNameDialog
      v-if="open"
      :title="t('analysisEdit.nameDialog.newTitle')"
      :confirm-label="t('analysisEdit.nameDialog.create')"
      :directories="nav.creatableDirectories"
      name=""
      :directory-id="null"
      :busy="busy"
      :error="error"
      :taken-id="takenId"
      @close="open = false"
      @submit="create"
      @open="openExisting"
    />
  </Teleport>
</template>
