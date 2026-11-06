/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { buildSiteTree, moduleCounts, type Navigation } from '../api/navigation'
import { useAuthStore } from './auth'

export const useNavigationStore = defineStore('navigation', () => {
  const data = ref<Navigation | null>(null)
  const loading = ref(false)
  const failed = ref(false)

  const tree = computed(() => (data.value ? buildSiteTree(data.value.sites) : []))
  const counts = computed(() => {
    const sites = data.value?.sites ?? []
    const other = data.value?.other
    return {
      sites: sites.length,
      analyses: sites.reduce((n, s) => n + s.analyses.length, 0) + (other?.analyses.length ?? 0),
      dashboards: sites.reduce((n, s) => n + s.dashboards.length, 0) + (other?.dashboards.length ?? 0),
    }
  })

  /** readable appointments / maintenance entries: the menu shows only modules with entries */
  const modules = computed(() => moduleCounts(data.value))

  async function load(): Promise<void> {
    loading.value = true
    failed.value = false
    try {
      data.value = await useAuthStore().http.get<Navigation>('/web/navigation')
    } catch {
      failed.value = true
    } finally {
      loading.value = false
    }
  }

  function reset(): void {
    data.value = null
  }

  return { data, tree, counts, modules, loading, failed, load, reset }
})
