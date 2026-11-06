/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { MaintenanceEntry } from '../api/modules'
import { parseLocal } from './dates'
import { compareTimes } from './search'

/** Refresh interval of the maintenance banner. */
export const BANNER_REFRESH_MS = 15 * 60 * 1000

export const DISMISSED_STORAGE_KEY = 'jevis.maintenance.dismissed'
/** upper bound of remembered keys */
const MAX_KEYS = 200

type BannerFields = Pick<MaintenanceEntry, 'id' | 'status' | 'start' | 'end' | 'title' | 'impact'>

/** Identifies an entry in the state it was dismissed: a change of status, time, title or impact shows it again. */
export function bannerKey(e: BannerFields): string {
  return JSON.stringify([e.id, e.status, e.start, e.end, e.title, e.impact])
}

/** Active entries that were not dismissed, earliest start first. */
export function bannerEntries(entries: readonly MaintenanceEntry[], dismissed: ReadonlySet<string>): MaintenanceEntry[] {
  return entries
    .filter((e) => e.active && !dismissed.has(bannerKey(e)))
    .sort((a, b) => compareTimes(parseLocal(a.start), parseLocal(b.start)) || a.id - b.id)
}

function defaultStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

/** Dismissed banner keys of this browser; empty if the storage is not available or broken. */
export function loadDismissed(storage: Storage | null = defaultStorage()): Set<string> {
  try {
    const raw = storage?.getItem(DISMISSED_STORAGE_KEY)
    const list: unknown = raw ? JSON.parse(raw) : []
    return new Set(Array.isArray(list) ? list.filter((k): k is string => typeof k === 'string') : [])
  } catch {
    return new Set()
  }
}

export function saveDismissed(keys: ReadonlySet<string>, storage: Storage | null = defaultStorage()): void {
  try {
    storage?.setItem(DISMISSED_STORAGE_KEY, JSON.stringify([...keys].slice(-MAX_KEYS)))
  } catch {
    /* only for this page */
  }
}

/** Forgets keys of entries that are no longer active or have changed (keeps the storage small). */
export function pruneDismissed(keys: ReadonlySet<string>, entries: readonly MaintenanceEntry[]): Set<string> {
  const current = new Set(entries.filter((e) => e.active).map(bannerKey))
  return new Set([...keys].filter((k) => current.has(k)))
}
