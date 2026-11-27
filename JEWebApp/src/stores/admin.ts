/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { loadScope as fetchScope, type AdminScope } from '../api/admin'
import { canAdminister } from '../admin/admin'
import { useAuthStore } from './auth'

/*
 * What the user may administer (E10.1, GET /web/admin/scope): loaded once per session. Any error (401, 403, an older
 * server without the endpoint) leaves the scope empty and the "Verwaltung" group hidden.
 */
export const useAdminStore = defineStore('admin', () => {
  const scope = ref<AdminScope | null>(null)
  const loaded = ref(false)
  let loading: Promise<void> | null = null

  const visible = computed(() => canAdminister(scope.value))
  const sysAdmin = computed(() => scope.value?.sysAdmin === true)

  function load(force = false): Promise<void> {
    if (loaded.value && !force) return Promise.resolve()
    if (!loading) {
      loading = fetchScope(useAuthStore().http)
        .then((s) => {
          scope.value = s && typeof s === 'object' ? { sysAdmin: s.sysAdmin === true, sites: Array.isArray(s.sites) ? s.sites : [] } : null
        })
        .catch(() => {
          scope.value = null
        })
        .finally(() => {
          loaded.value = true
          loading = null
        })
    }
    return loading
  }

  function reset() {
    scope.value = null
    loaded.value = false
  }

  return { scope, loaded, visible, sysAdmin, load, reset }
})
