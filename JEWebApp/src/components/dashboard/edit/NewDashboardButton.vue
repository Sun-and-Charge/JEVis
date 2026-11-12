<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { createDashboard } from '../../../api/dashboardEdit'
import { nameTakenId } from '../../../analysis/editor'
import { useBusy, writeErrorText } from '../../../edit/edit'
import { useAuthStore } from '../../../stores/auth'
import { useNavigationStore } from '../../../stores/navigation'
import AnalysisNameDialog from '../../analysis/edit/AnalysisNameDialog.vue'

/*
 * "Neues Dashboard" in the header of the dashboards overview (E4, shown when a "Dashboard Directory" allows creating
 * dashboards): name and directory, then POST /web/dashboards (the server creates the Control Center's empty
 * dashboard) and the new dashboard opens in edit mode. A taken name links to the existing dashboard.
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

onMounted(() => void nav.loadDashboardDirectories())

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
      const created = await createDashboard(auth.http, locale.value, value.directoryId, value.name)
      open.value = false
      void nav.load()
      emit('navigate')
      await router.push({ name: 'dashboard', params: { id: created.id }, query: { edit: '1' } })
    } catch (e) {
      const taken = nameTakenId(e)
      if (taken !== null) takenId.value = taken
      else error.value = writeErrorText(e, t, 'dashboardEdit.conflict')
    }
  })
}

async function openExisting(id: number) {
  open.value = false
  emit('navigate')
  await router.push({ name: 'dashboard', params: { id } })
}
</script>

<template>
  <button v-if="nav.creatableDashboardDirectories.length" type="button" class="btn btn-primary new-dashboard" @click="start">
    <span aria-hidden="true">+</span> {{ t('dashboardEdit.newDashboard') }}
  </button>
  <Teleport to="body">
    <AnalysisNameDialog
      v-if="open"
      :title="t('dashboardEdit.nameDialog.newTitle')"
      :confirm-label="t('dashboardEdit.nameDialog.create')"
      :directories="nav.creatableDashboardDirectories"
      name=""
      :directory-id="null"
      :busy="busy"
      :error="error"
      :taken-id="takenId"
      :texts="{ noDirectory: 'dashboardEdit.nameDialog.noDirectory', taken: 'dashboardEdit.nameDialog.taken', openExisting: 'dashboardEdit.nameDialog.openExisting' }"
      @close="open = false"
      @submit="create"
      @open="openExisting"
    />
  </Teleport>
</template>
