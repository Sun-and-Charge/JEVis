/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/*
 * Navigation settings of a user, kept in the browser (localStorage, one key per user id): favourite and recently
 * opened dashboards / analyses, the chosen site, the collapsed sidebar and the open menu groups. Without storage
 * (private mode, blocked) everything works for the page only.
 */

export type ItemKind = 'dashboard' | 'analysis'
/** A chosen site id or 'all'. */
export type SiteChoiceValue = number | 'all'

export interface NavPrefs {
  /** item keys (see itemKey) */
  favourites: string[]
  /** item keys, most recent first */
  recent: string[]
  site: SiteChoiceValue
  collapsed: boolean
  /** open state of the menu groups by group id */
  groups: Record<string, boolean>
}

export const RECENT_MAX = 6
export const FAVOURITES_IN_MENU = 8
const KEY_PREFIX = 'jevis.nav.'

export const prefsKey = (userId: number) => KEY_PREFIX + userId

export function itemKey(kind: ItemKind, id: number): string {
  return (kind === 'dashboard' ? 'd:' : 'a:') + id
}

export function parseItemKey(key: string): { kind: ItemKind; id: number } | null {
  const m = /^([da]):(\d+)$/.exec(key)
  return m ? { kind: m[1] === 'd' ? 'dashboard' : 'analysis', id: Number(m[2]) } : null
}

export function emptyPrefs(): NavPrefs {
  return { favourites: [], recent: [], site: 'all', collapsed: false, groups: {} }
}

export function safeStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

const keys = (v: unknown): string[] => (Array.isArray(v) ? [...new Set(v.filter((x): x is string => typeof x === 'string' && parseItemKey(x) !== null))] : [])

/** Checks a stored value field by field (anything unexpected falls back to the default). */
export function parsePrefs(raw: unknown): NavPrefs {
  const prefs = emptyPrefs()
  if (!raw || typeof raw !== 'object') return prefs
  const r = raw as Record<string, unknown>
  prefs.favourites = keys(r.favourites)
  prefs.recent = keys(r.recent).slice(0, RECENT_MAX)
  if (typeof r.site === 'number' && Number.isInteger(r.site) && r.site > 0) prefs.site = r.site
  prefs.collapsed = r.collapsed === true
  if (r.groups && typeof r.groups === 'object' && !Array.isArray(r.groups)) {
    for (const [id, open] of Object.entries(r.groups as Record<string, unknown>)) if (typeof open === 'boolean') prefs.groups[id] = open
  }
  return prefs
}

export function loadPrefs(userId: number | null | undefined, storage: Storage | null = safeStorage()): NavPrefs {
  if (userId == null || !storage) return emptyPrefs()
  try {
    const text = storage.getItem(prefsKey(userId))
    return text ? parsePrefs(JSON.parse(text)) : emptyPrefs()
  } catch {
    return emptyPrefs()
  }
}

/** Saves the settings; false if the storage refused (full, blocked). */
export function savePrefs(userId: number | null | undefined, prefs: NavPrefs, storage: Storage | null = safeStorage()): boolean {
  if (userId == null || !storage) return false
  try {
    storage.setItem(prefsKey(userId), JSON.stringify(prefs))
    return true
  } catch {
    return false
  }
}

/** Adds or removes a favourite. */
export function toggleKey(list: readonly string[], key: string): string[] {
  return list.includes(key) ? list.filter((k) => k !== key) : [...list, key]
}

/** Moves the key to the front of the recently opened list (at most `max` entries). */
export function pushRecent(list: readonly string[], key: string, max = RECENT_MAX): string[] {
  return [key, ...list.filter((k) => k !== key)].slice(0, max)
}
