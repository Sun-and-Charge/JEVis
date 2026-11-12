/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { localName, type NavItem, type Navigation } from '../../../api/navigation'
import type { ReportSummary } from '../../../api/reports'
import { useAuthStore } from '../../../stores/auth'
import { useNavigationStore } from '../../../stores/navigation'
import { loadReportTargets } from '../typeApi'

/* Link targets of the link and image widgets: analyses and dashboards of the navigation, reports of GET /web/reports. */

export interface Target {
  id: number
  label: string
}

/** Analyses or dashboards of the navigation as "Site / Name", sorted. */
export function navTargets(nav: Navigation | null, kind: 'analyses' | 'dashboards', locale: string): Target[] {
  if (!nav) return []
  const list: Target[] = []
  const seen = new Set<number>()
  const add = (item: NavItem, site: string | null) => {
    if (!item || typeof item.id !== 'number' || seen.has(item.id)) return
    seen.add(item.id)
    const name = localName(item, locale)
    list.push({ id: item.id, label: site ? `${site} / ${name}` : name })
  }
  for (const site of nav.sites ?? []) for (const item of site[kind] ?? []) add(item, localName(site, locale))
  for (const item of nav.other?.[kind] ?? []) add(item, null)
  return list.sort((a, b) => a.label.localeCompare(b.label, locale, { numeric: true }))
}

/** Reports as "Site / Path / Name" (the server sorts them). */
export function reportTargets(reports: readonly ReportSummary[], locale: string): Target[] {
  return reports
    .filter((r) => r && typeof r.id === 'number')
    .map((r) => {
      const parts = [r.site ? (r.site.names?.[locale] || r.site.name) : null, r.path, r.name].filter((p): p is string => !!p)
      return { id: r.id, label: parts.join(' / ') }
    })
}

/** Analyses and dashboards of the navigation store (loaded if it is not yet). */
export function useNavTargets() {
  const nav = useNavigationStore()
  const { locale } = useI18n()
  onMounted(() => {
    if (!nav.data && !nav.loading) void nav.load()
  })
  const analyses = computed(() => navTargets(nav.data, 'analyses', locale.value))
  const dashboards = computed(() => navTargets(nav.data, 'dashboards', locale.value))
  const loading = computed(() => nav.loading)
  return { analyses, dashboards, loading }
}

/** Reports, loaded once as soon as `enabled` is true. */
export function useReportTargets(enabled: () => boolean) {
  const { locale } = useI18n()
  const http = useAuthStore().http
  const reports = ref<ReportSummary[]>([])
  const state = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
  watch(
    enabled,
    async (on) => {
      if (!on || state.value !== 'idle') return
      state.value = 'loading'
      try {
        reports.value = await loadReportTargets(http)
        state.value = 'ready'
      } catch {
        state.value = 'error'
      }
    },
    { immediate: true },
  )
  const targets = computed(() => reportTargets(reports.value, locale.value))
  return { targets, state }
}
