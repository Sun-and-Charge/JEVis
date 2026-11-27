/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { ModuleCounts } from '../api/navigation'

/*
 * Structure of the sidebar: Start, Dashboards, Analysen, four collapsible module groups and Berichte. A module is
 * shown when the navigation counts readable entries for it (as before); a group without visible modules is left out.
 * The route name of a module is its key.
 */

export type ModuleKey = Exclude<keyof ModuleCounts, 'alarmsOpen' | 'readingsReview'>
export type GroupId = 'iso' | 'operations' | 'assets' | 'finance'

export type NavIconName = GroupId | 'home' | 'dashboard' | 'analysis' | 'reports' | 'collapse' | 'expand' | 'search' | 'star' | 'chevron' | 'site' | 'user'

export interface MenuGroupDef {
  id: GroupId
  modules: readonly ModuleKey[]
}

export const MENU_GROUPS: readonly MenuGroupDef[] = [
  { id: 'iso', modules: ['actionPlans', 'nonconformities', 'legal'] },
  { id: 'operations', modules: ['readings', 'appointments', 'maintenance', 'alarms', 'notes'] },
  { id: 'assets', modules: ['meters', 'basedata', 'equipment', 'certificates'] },
  { id: 'finance', modules: ['contracts', 'invoices', 'costs'] },
]

/** Modules outside the groups (own sidebar entry). */
export const SINGLE_MODULES: readonly ModuleKey[] = ['reports']

export interface MenuModule {
  key: ModuleKey
  /** route name */
  route: string
  /** readable entries (badge, as before) */
  count: number
  /** open items (alarms: unconfirmed alarms of the last 7 days; readings: readings to review, E8.5); shown as a warning badge */
  open: number
}

export interface MenuGroup {
  id: GroupId
  modules: MenuModule[]
  /** sum of the open-item badges of the group (shown on the collapsed header) */
  open: number
}

function menuModule(key: ModuleKey, counts: ModuleCounts): MenuModule | null {
  if (!(counts[key] > 0)) return null
  return { key, route: key, count: counts[key], open: openItems(key, counts) }
}

/** Open items of a module (warning badge): unconfirmed alarms, readings to review. */
function openItems(key: ModuleKey, counts: ModuleCounts): number {
  if (key === 'alarms') return counts.alarmsOpen
  if (key === 'readings') return counts.readingsReview
  return 0
}

/** Visible groups with their visible modules (empty groups left out). */
export function menuGroups(counts: ModuleCounts): MenuGroup[] {
  const groups: MenuGroup[] = []
  for (const def of MENU_GROUPS) {
    const modules = def.modules.map((key) => menuModule(key, counts)).filter((m): m is MenuModule => m !== null)
    if (modules.length) groups.push({ id: def.id, modules, open: modules.reduce((n, m) => n + m.open, 0) })
  }
  return groups
}

/** Visible modules outside the groups (Berichte). */
export function singleModules(counts: ModuleCounts): MenuModule[] {
  return SINGLE_MODULES.map((key) => menuModule(key, counts)).filter((m): m is MenuModule => m !== null)
}

/** All visible modules in menu order (for the search). */
export function visibleModules(counts: ModuleCounts): MenuModule[] {
  return [...menuGroups(counts).flatMap((g) => g.modules), ...singleModules(counts)]
}

/** Group containing the module of a route, or null. */
export function groupOfRoute(routeName: unknown): GroupId | null {
  if (typeof routeName !== 'string') return null
  return MENU_GROUPS.find((g) => (g.modules as readonly string[]).includes(routeName))?.id ?? null
}

/**
 * Remembered open state of the groups after navigating to a route: the group of the route opens (and stays open
 * until the user closes it). Returns the same object when nothing changes.
 */
export function openGroupOfRoute(state: Readonly<Record<string, boolean>>, routeName: unknown): Readonly<Record<string, boolean>> {
  const id = groupOfRoute(routeName)
  return id && state[id] !== true ? { ...state, [id]: true } : state
}
