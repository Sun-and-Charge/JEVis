/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { ModuleCounts, NavItem, NavSite, Navigation } from '../../src/api/navigation'

/* Neutral navigation data: "Muster AG" with "Musterwerk" and its "Halle 2", a second top site "Lager Nord". */

export const item = (id: number, name: string, names: Record<string, string> = {}): NavItem => ({ id, name, names })

export function site(id: number, name: string, parentId: number | null, dashboards: NavItem[] = [], analyses: NavItem[] = []): NavSite {
  return { id, name, names: {}, className: parentId == null ? 'Organization' : 'Building', parentId, dashboards, analyses }
}

export const noModules: ModuleCounts = {
  appointments: 0,
  maintenance: 0,
  actionPlans: 0,
  nonconformities: 0,
  legal: 0,
  meters: 0,
  basedata: 0,
  equipment: 0,
  notes: 0,
  alarms: 0,
  alarmsOpen: 0,
  reports: 0,
  contracts: 0,
  invoices: 0,
  costs: 0,
  readings: 0,
}

export function navigation(overrides: Partial<Navigation> = {}): Navigation {
  return {
    user: { id: 7, name: 'Muster', sysAdmin: false },
    sites: [
      site(1, 'Muster AG', null, [], [item(300, 'Strom gesamt')]),
      site(2, 'Musterwerk', 1, [item(100, 'Übersicht', { en: 'Overview' }), item(101, 'Lageplan')], [item(301, 'Gas')]),
      site(3, 'Halle 2', 2, [item(102, 'Sankey')]),
      site(4, 'Lager Nord', null, [], [item(302, 'Wasser')]),
    ],
    other: { dashboards: [item(103, 'Wetter')], analyses: [] },
    startDashboard: null,
    modules: { ...noModules, actionPlans: 2, legal: 1, appointments: 3, alarms: 2, alarmsOpen: 4, meters: 5, reports: 1 },
    ...overrides,
  }
}
