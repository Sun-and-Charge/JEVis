/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { HttpError, type HttpClient } from './http'

/*
 * Recycle bin of analyses and dashboards (docs/WEB-API.md "Recycle bin (analyses, dashboards)"): the deleted items
 * the user may read, restoring them (original place or a chosen directory) and deleting them for good.
 */

export type RecycleKind = 'dashboards' | 'analyses'

export const RECYCLE_KINDS: readonly RecycleKind[] = ['dashboards', 'analyses']

/** One entry of GET /web/recycle */
export interface RecycleEntry {
  id: number
  name: string
  kind: RecycleKind
  /** ISO date-time (older databases: only the day) */
  deletedAt: string | null
  /** name of the user who deleted it, null if unknown */
  deletedBy: string | null
  /** path of the original place, null if it is gone or not readable */
  path: string | null
  /** the original place no longer exists (or is in the bin itself): restoring needs a target directory */
  parentGone: boolean
  canRestore: boolean
  canDeleteForever: boolean
}

/** Answer of POST /web/recycle/{id}/restore */
export interface RestoredEntry {
  id: number
  name: string
  kind: RecycleKind
  /** the directory it is in now */
  parentId: number
  path: string | null
}

type Client = Pick<HttpClient, 'get' | 'post' | 'delete'>
const lang = (locale: string) => `lang=${encodeURIComponent(locale)}`

/** `?kind=` of the page; anything else is "dashboards". */
export function parseKind(value: unknown): RecycleKind {
  const v = Array.isArray(value) ? value[0] : value
  return v === 'analyses' ? 'analyses' : 'dashboards'
}

export function loadRecycleBin(http: Pick<HttpClient, 'get'>, kind: RecycleKind, locale: string): Promise<RecycleEntry[]> {
  return http.get<RecycleEntry[]>(`/web/recycle?kind=${kind}&${lang(locale)}`)
}

/** Restores the entry to its original place, or with `parent` into that directory. */
export function restoreEntry(http: Pick<Client, 'post'>, id: number, locale: string, parent?: number | null): Promise<RestoredEntry> {
  return http.post<RestoredEntry>(`/web/recycle/${id}/restore?${lang(locale)}`, parent != null ? { parent } : {})
}

export function deleteForever(http: Pick<Client, 'delete'>, id: number): Promise<void> {
  return http.delete<void>(`/web/recycle/${id}`)
}

/** 409 `{ "parentGone": true }`: the original place is gone, a target directory is needed. */
export function isParentGone(e: unknown): boolean {
  return e instanceof HttpError && e.status === 409 && e.json<{ parentGone?: boolean }>()?.parentGone === true
}
