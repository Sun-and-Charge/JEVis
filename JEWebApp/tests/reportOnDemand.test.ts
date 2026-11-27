/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import { loadOnDemand, loadOnDemandFile, onDemandFilePath, onDemandPath, requestOnDemand } from '../src/api/reports'
import { canAddRequest, formats, hasOpen, isoWeek, onDemandFileName, partsIn, periodLabel } from '../src/reports/onDemand'

const t = (key: string, v?: Record<string, unknown>) => `${key.split('.').pop()}:${JSON.stringify(v ?? {})}`

describe('report on demand api', () => {
  it('uses the paths of the contract', async () => {
    expect(onDemandPath(42)).toBe('/web/reports/42/on-demand')
    expect(onDemandFilePath(42, 'a b', 'pdf')).toBe('/web/reports/42/on-demand/a%20b/file?format=pdf')
    const get = vi.fn(async () => ({}))
    const post = vi.fn(async () => ({}))
    const getBlob = vi.fn(async () => new Blob(['x']))
    await loadOnDemand({ get } as never, 42)
    await requestOnDemand({ post } as never, 42, { start: '2026-10-01T00:00:00.000+02:00', mail: true })
    await loadOnDemandFile({ getBlob } as never, 42, 'r1', 'xlsx')
    expect((get.mock.calls[0] as unknown[])[0]).toBe('/web/reports/42/on-demand')
    expect(post.mock.calls[0]).toEqual(['/web/reports/42/on-demand', { start: '2026-10-01T00:00:00.000+02:00', mail: true }])
    expect((getBlob.mock.calls[0] as unknown[])[0]).toBe('/web/reports/42/on-demand/r1/file?format=xlsx')
  })
})

describe('report on demand periods', () => {
  it('reads the date in the time zone of the report', () => {
    /* 1 October 00:00 in Berlin is 30 September in UTC */
    expect(partsIn('2026-10-01T00:00:00.000+02:00', 'Europe/Berlin')).toEqual({ year: 2026, month: 10, day: 1 })
    expect(partsIn('2026-10-01T00:00:00.000+02:00', 'UTC')).toEqual({ year: 2026, month: 9, day: 30 })
    expect(partsIn('kaputt', 'UTC')).toBeNull()
  })

  it('labels the period by the schedule', () => {
    const start = '2026-10-01T00:00:00.000+02:00'
    expect(periodLabel(start, 'MONTHLY', 'Europe/Berlin', 'de-DE', t)).toBe('Oktober 2026')
    expect(periodLabel(start, 'YEARLY', 'Europe/Berlin', 'de-DE', t)).toBe('2026')
    expect(periodLabel(start, 'QUARTERLY', 'Europe/Berlin', 'de-DE', t)).toBe('quarter:{"quarter":4,"year":2026}')
    expect(periodLabel('2026-11-16T00:00:00.000+01:00', 'WEEKLY', 'Europe/Berlin', 'de-DE', t)).toBe('week:{"week":47,"year":2026}')
    expect(periodLabel('2026-11-27T00:00:00.000+01:00', 'DAILY', 'Europe/Berlin', 'de-DE', t)).toBe('27.11.2026')
    expect(periodLabel('kaputt', 'MONTHLY', 'Europe/Berlin', 'de-DE', t)).toBe('kaputt')
  })

  it('counts ISO weeks across the turn of the year', () => {
    expect(isoWeek(2026, 1, 1)).toEqual({ week: 1, year: 2026 })
    expect(isoWeek(2027, 1, 1)).toEqual({ week: 53, year: 2026 })
    expect(isoWeek(2024, 12, 30)).toEqual({ week: 1, year: 2025 })
  })
})

describe('report on demand requests', () => {
  it('polls while a request is open and allows three open ones', () => {
    expect(hasOpen([{ status: 'DONE' }, { status: 'FAILED' }])).toBe(false)
    expect(hasOpen([{ status: 'DONE' }, { status: 'RUNNING' }])).toBe(true)
    expect(canAddRequest([{ status: 'PENDING' }, { status: 'RUNNING' }, { status: 'DONE' }])).toBe(true)
    expect(canAddRequest([{ status: 'PENDING' }, { status: 'RUNNING' }, { status: 'PENDING' }])).toBe(false)
  })

  it('offers the files of a finished request', () => {
    expect(formats({ status: 'DONE', pdf: true, xlsx: true })).toEqual(['pdf', 'xlsx'])
    expect(formats({ status: 'DONE', pdf: false, xlsx: true })).toEqual(['xlsx'])
    expect(formats({ status: 'RUNNING', pdf: true, xlsx: true })).toEqual([])
    expect(onDemandFileName('Energiebericht Monat', 'Oktober 2026', 'pdf')).toBe('Energiebericht_Monat_Oktober_2026.pdf')
    expect(onDemandFileName('a/b', 'KW 47 2026', 'xlsx')).toBe('ab_KW_47_2026.xlsx')
  })
})
