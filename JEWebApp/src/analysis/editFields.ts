/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { computed, reactive } from 'vue'
import { loadRowUnits, type JsonClient, type UnitOption } from '../api/analysis'
import type { DraftRow, Problem } from './editor'

/*
 * Small helpers of the analysis edit panel (E3): field ids and problems by field path, and the unit lists of the
 * rows (GET /web/rows/{id}/units, loaded once per row).
 */

export interface FieldHelpers {
  /** id of the field of a path ("charts.0.name") */
  fid: (path: string) => string
  /** id of the error text of a path */
  errId: (path: string) => string
  /** first problem of a path */
  problem: (path: string) => Problem | undefined
  /** aria-describedby of a field: its error, plus extra ids */
  describedBy: (path: string, ...extra: (string | undefined)[]) => string | undefined
}

export function fieldHelpers(uid: string, problems: () => readonly Problem[]): FieldHelpers {
  const byPath = computed(() => {
    const map = new Map<string, Problem>()
    for (const p of problems()) if (!map.has(p.path)) map.set(p.path, p)
    return map
  })
  const fid = (path: string) => `${uid}-${path.replace(/\./g, '-')}`
  const errId = (path: string) => fid(path) + '-err'
  const problem = (path: string) => byPath.value.get(path)
  const describedBy = (path: string, ...extra: (string | undefined)[]) => {
    const ids = [problem(path) ? errId(path) : '', ...extra].filter(Boolean)
    return ids.length ? ids.join(' ') : undefined
  }
  return { fid, errId, problem, describedBy }
}

export type UnitsState = UnitOption[] | 'loading' | 'error'

export interface RowUnits {
  /** loads the units of a row (once); null on error */
  load: (row: Pick<DraftRow, 'objectId' | 'attribute'>) => Promise<UnitOption[] | null>
  state: (row: Pick<DraftRow, 'objectId' | 'attribute'>) => UnitsState | undefined
  unitsOf: (row: Pick<DraftRow, 'objectId' | 'attribute'>) => UnitOption[] | undefined
}

export function useRowUnits(http: JsonClient): RowUnits {
  const cache = reactive<Record<string, UnitsState>>({})
  const pending = new Map<string, Promise<UnitOption[] | null>>()
  const key = (row: Pick<DraftRow, 'objectId' | 'attribute'>) => `${row.objectId}|${row.attribute}`

  function load(row: Pick<DraftRow, 'objectId' | 'attribute'>): Promise<UnitOption[] | null> {
    const k = key(row)
    const known = cache[k]
    if (Array.isArray(known)) return Promise.resolve(known)
    const running = pending.get(k)
    if (running) return running
    cache[k] = 'loading'
    const promise = loadRowUnits(http, row.objectId, row.attribute)
      .then((list) => {
        const units = Array.isArray(list) ? list : []
        cache[k] = units
        return units
      })
      .catch(() => {
        cache[k] = 'error'
        return null
      })
      .finally(() => pending.delete(k))
    pending.set(k, promise)
    return promise
  }

  return {
    load,
    state: (row) => cache[key(row)],
    unitsOf: (row) => {
      const v = cache[key(row)]
      return Array.isArray(v) ? v : undefined
    },
  }
}
