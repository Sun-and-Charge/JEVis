/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { useNavigationStore } from '../stores/navigation'
import { useNavPrefsStore } from '../stores/navPrefs'
import { preferredBySite } from './catalog'

/**
 * Preselection of the tab (or plan) of the site chosen in the top bar, for module views with one tab per site.
 * `pick` answers once per view (the first time items are there); later reloads keep the user's choice. `reset`
 * allows it again (e.g. the register view showing another kind).
 */
export function useSitePreselection() {
  const nav = useNavigationStore()
  const prefs = useNavPrefsStore()
  let done = false
  return {
    pick<K>(items: readonly { key: K; site: { id: number } | null }[]): K | null {
      if (done || !items.length) return null
      done = true
      return preferredBySite(items, prefs.site, nav.data)
    },
    reset(): void {
      done = false
    },
  }
}
