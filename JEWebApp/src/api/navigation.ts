/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/** Answer of GET /web/navigation. */
export interface NavItem {
  id: number
  name: string
  /** localized names by language code */
  names: Record<string, string>
}

export interface NavSite extends NavItem {
  className: 'Organization' | 'Building' | string
  parentId: number | null
  analyses: NavItem[]
  dashboards: NavItem[]
}

export interface Navigation {
  user: { id: number; name: string; sysAdmin: boolean }
  sites: NavSite[]
  other: { analyses: NavItem[]; dashboards: NavItem[] }
  /** the user's start dashboard (if readable), opened after login */
  startDashboard?: number | null
  /** number of readable appointments / maintenance entries (W4.1), plans (W4.2) and register objects (W4.3), notes (W4.4), alarms (W4.5), reports (W4.6), contracts and invoices (W4.7), cost calculations (W4.8); missing on older servers */
  modules?: Partial<ModuleCounts> | null
}

export interface ModuleCounts {
  appointments: number
  maintenance: number
  actionPlans: number
  nonconformities: number
  legal: number
  /** readable meters, base data objects and equipment (W4.3) */
  meters: number
  basedata: number
  equipment: number
  /** readable "Data Notes" objects (W4.4) */
  notes: number
  /** readable, enabled "Alarm Configuration" objects (W4.5) */
  alarms: number
  /** unconfirmed alarms logged in the last 7 days (menu badge, not a module of its own) */
  alarmsOpen: number
  /** readable reports, not in the recycle bin (W4.6) */
  reports: number
  /** readable contract objects plus accounting configurations, and readable energy invoices (W4.7) */
  contracts: number
  invoices: number
  /** readable accounting configurations plus result calculation templates (W4.8) */
  costs: number
  /** E8.1 meter reading: meters the user may read in; servers without the count use the readable meters */
  readings: number
}

/** Module counts with missing values as 0 (the menu shows only modules with entries). */
export function moduleCounts(nav: Pick<Navigation, 'modules'> | null): ModuleCounts {
  const n = (v: unknown) => (typeof v === 'number' && v > 0 ? v : 0)
  const m = nav?.modules
  return {
    appointments: n(m?.appointments),
    maintenance: n(m?.maintenance),
    actionPlans: n(m?.actionPlans),
    nonconformities: n(m?.nonconformities),
    legal: n(m?.legal),
    meters: n(m?.meters),
    basedata: n(m?.basedata),
    equipment: n(m?.equipment),
    notes: n(m?.notes),
    alarms: n(m?.alarms),
    alarmsOpen: n(m?.alarmsOpen),
    reports: n(m?.reports),
    contracts: n(m?.contracts),
    invoices: n(m?.invoices),
    costs: n(m?.costs),
    readings: n(m?.readings) || n(m?.meters),
  }
}

/** True if the menu shows at least one module. */
export function hasModules(counts: ModuleCounts): boolean {
  return Object.entries(counts).some(([key, n]) => key !== 'alarmsOpen' && n > 0)
}

export interface SiteNode {
  site: NavSite
  children: SiteNode[]
}

export function localName(item: NavItem, locale: string): string {
  return item.names?.[locale] || item.name
}

/** Nests the flat site list by parentId (sites whose parent is not in the list become roots). */
export function buildSiteTree(sites: NavSite[]): SiteNode[] {
  const nodes = new Map<number, SiteNode>()
  for (const site of sites) nodes.set(site.id, { site, children: [] })
  const roots: SiteNode[] = []
  for (const node of nodes.values()) {
    const parent = node.site.parentId != null ? nodes.get(node.site.parentId) : undefined
    if (parent && parent !== node) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

/** Total number of analyses and dashboards of a site including its sub-sites. */
export function itemCount(node: SiteNode): number {
  return node.site.analyses.length + node.site.dashboards.length + node.children.reduce((sum, child) => sum + itemCount(child), 0)
}

/** Id of the start dashboard to open instead of the home page, or null (only right after login / reload on home). */
export function startDashboardFor(nav: Pick<Navigation, 'startDashboard'> | null, routeName: unknown): number | null {
  const id = nav?.startDashboard
  return routeName === 'home' && typeof id === 'number' && id > 0 ? id : null
}
