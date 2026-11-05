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
