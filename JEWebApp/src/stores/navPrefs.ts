/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { loadPrefs, pushRecent, savePrefs, toggleKey, type NavPrefs, type SiteChoiceValue } from '../nav/prefs'
import { useAuthStore } from './auth'

/** Navigation settings of the logged-in user (favourites, recently opened, site, sidebar), stored per user id. */
export const useNavPrefsStore = defineStore('navPrefs', () => {
  const auth = useAuthStore()
  const userId = computed(() => auth.user?.id ?? null)
  const prefs = ref<NavPrefs>(loadPrefs(userId.value))

  /* another user logs in: his settings */
  watch(userId, (id) => {
    prefs.value = loadPrefs(id)
  })

  function update(change: Partial<NavPrefs>): void {
    prefs.value = { ...prefs.value, ...change }
    savePrefs(userId.value, prefs.value)
  }

  const favourites = computed(() => prefs.value.favourites)
  const favouriteSet = computed(() => new Set(prefs.value.favourites))
  const recent = computed(() => prefs.value.recent)
  const site = computed(() => prefs.value.site)
  const collapsed = computed(() => prefs.value.collapsed)
  const groups = computed(() => prefs.value.groups)

  return {
    favourites,
    favouriteSet,
    recent,
    site,
    collapsed,
    groups,
    isFavourite: (key: string) => favouriteSet.value.has(key),
    toggleFavourite: (key: string) => update({ favourites: toggleKey(prefs.value.favourites, key) }),
    recordOpened: (key: string) => {
      if (prefs.value.recent[0] !== key) update({ recent: pushRecent(prefs.value.recent, key) })
    },
    setSite: (value: SiteChoiceValue) => update({ site: value }),
    setCollapsed: (value: boolean) => update({ collapsed: value }),
    setGroups: (value: Record<string, boolean>) => {
      if (value !== prefs.value.groups) update({ groups: { ...value } })
    },
    setGroupOpen: (id: string, open: boolean) => update({ groups: { ...prefs.value.groups, [id]: open } }),
  }
})
