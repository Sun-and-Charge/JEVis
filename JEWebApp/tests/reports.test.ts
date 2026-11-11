/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import { loadReportFile, loadReportFiles, loadReports, reportFilePath, reportFilesPath } from '../src/api/reports'
import {
  filterReports,
  groupReports,
  hasOutput,
  outputFileName,
  outputLabel,
  outputTs,
  parseReportId,
  scheduleKey,
  scheduleLabel,
  sortOutputs,
  sortReports,
} from '../src/reports/reports'
import { output, report } from './fixtures/reports'

const fmt = (ms: number) => new Date(ms).toISOString().slice(0, 16)

describe('reports api', () => {
  it('builds the paths of the contract', () => {
    expect(reportFilesPath(23801)).toBe('/web/reports/23801/files')
    expect(reportFilePath(23801, 'pdf')).toBe('/web/reports/23801/file?format=pdf')
    expect(reportFilePath(23801, 'xlsx', '2026-11-01T06:00:11.000+01:00')).toBe('/web/reports/23801/file?format=xlsx&ts=2026-11-01T06%3A00%3A11.000%2B01%3A00')
    expect(reportFilePath(5, 'pdf', '2026-11-01T06:00:12.000+01:00', true)).toBe('/web/reports/5/file?format=pdf&ts=2026-11-01T06%3A00%3A12.000%2B01%3A00&inline=true')
    expect(reportFilePath(5, 'pdf', null, true)).toBe('/web/reports/5/file?format=pdf&inline=true')
  })

  it('loads the list, the outputs and a file as blob', async () => {
    const get = vi.fn(async () => ({ reports: [] }))
    const getBlob = vi.fn(async () => new Blob(['x']))
    await loadReports({ get } as never)
    await loadReportFiles({ get } as never, 3)
    await loadReportFile({ getBlob } as never, 3, 'xlsx', 'T')
    expect(get.mock.calls.map((c) => (c as unknown[])[0])).toEqual(['/web/reports', '/web/reports/3/files'])
    expect((getBlob.mock.calls[0] as unknown[])[0]).toBe('/web/reports/3/file?format=xlsx&ts=T')
  })
})

describe('report list', () => {
  const a = report(1, { name: 'zählerbericht', path: 'Berichte', site: { id: 2, name: 'Zweigwerk' } })
  const b = report(2, { name: 'Monatsbericht Energie', path: 'Berichte', site: { id: 1, name: 'musterwerk' } })
  const c = report(3, { name: 'Abrechnung', path: 'Berichte / Strom', site: { id: 1, name: 'musterwerk' } })
  const d = report(4, { name: 'Jahresbericht', path: null, site: null, latest: null, count: 0 })
  const e = report(5, { name: 'Anlagenbericht', path: null, site: { id: 1, name: 'musterwerk' } })

  it('sorts by site, path and name case-insensitively, reports without site last', () => {
    expect(sortReports([d, a, c, b, e], 'de').map((r) => r.id)).toEqual([5, 2, 3, 1, 4])
  })

  it('groups by site keeping the order of the answer', () => {
    const groups = groupReports(sortReports([d, a, c, b, e], 'de'), 'de')
    expect(groups.map((g) => [g.key, g.site, g.reports.map((r) => r.id)])).toEqual([
      ['1', 'musterwerk', [5, 2, 3]],
      ['2', 'Zweigwerk', [1]],
      ['none', null, [4]],
    ])
    // no re-sorting: the server order wins
    expect(groupReports([c, b, d, a], 'de').map((g) => [g.key, g.reports.map((r) => r.id)])).toEqual([
      ['1', [3, 2]],
      ['none', [4]],
      ['2', [1]],
    ])
  })

  it('uses the localized site name', () => {
    const r = report(9, { site: { id: 1, name: 'Musterwerk', names: { en: 'Sample plant' } } })
    expect(groupReports([r], 'en')[0].site).toBe('Sample plant')
    expect(groupReports([r], 'de')[0].site).toBe('Musterwerk')
  })

  it('searches every word in name, path, site and file name', () => {
    const all = [a, b, c, d, e]
    expect(filterReports(all, '', 'de')).toHaveLength(5)
    expect(filterReports(all, 'MUSTER strom', 'de').map((r) => r.id)).toEqual([3])
    expect(filterReports(all, 'zweig', 'de').map((r) => r.id)).toEqual([1])
    expect(filterReports(all, '202610', 'de').map((r) => r.id)).toEqual([1, 2, 3, 5])
    expect(filterReports(all, 'nichts', 'de')).toEqual([])
  })

  it('knows reports without output', () => {
    expect(hasOutput(d)).toBe(false)
    expect(hasOutput(a)).toBe(true)
  })
})

describe('report details', () => {
  const t = (key: string) => `[${key}]`

  it('translates the common schedules and keeps others as stored', () => {
    expect(scheduleKey('MONTHLY')).toBe('reports.schedule.MONTHLY')
    expect(scheduleKey(' daily ')).toBe('reports.schedule.DAILY')
    expect(scheduleKey('CUSTOM')).toBeNull()
    expect(scheduleKey(null)).toBeNull()
    expect(scheduleLabel('QUARTERLY', t)).toBe('[reports.schedule.QUARTERLY]')
    expect(scheduleLabel('CUSTOM_7', t)).toBe('CUSTOM_7')
    expect(scheduleLabel(null, t)).toBe('')
  })

  it('labels outputs with date/time and file name', () => {
    expect(outputLabel(output('2026-11-01T06:00:12.000+01:00', 'Monatsbericht Energie_202610.pdf'), fmt)).toBe(
      '2026-11-01T05:00 · Monatsbericht Energie_202610.pdf',
    )
    expect(outputLabel(output('kaputt', ''), fmt)).toBe('kaputt')
  })

  it('sorts outputs newest first, unreadable times last', () => {
    const files = [output('2026-10-01T06:00:00+02:00', 'a'), output('x', 'b'), output('2026-11-01T06:00:00+01:00', 'c')]
    expect(sortOutputs(files).map((f) => f.name)).toEqual(['c', 'a', 'b'])
  })

  it('uses the PDF or XLSX timestamp as ts', () => {
    const o = output('P', 'n.pdf', 'X')
    expect(outputTs(o, 'pdf')).toBe('P')
    expect(outputTs(o, 'xlsx')).toBe('X')
    expect(outputTs(output('P', 'n.pdf'), 'xlsx')).toBeNull()
  })

  it('names downloads after the stored file', () => {
    const o = output('2026-11-01T06:00:12.000+01:00', 'Monatsbericht Energie_202610.pdf')
    expect(outputFileName(o, 'pdf', 'Monatsbericht')).toBe('Monatsbericht Energie_202610.pdf')
    expect(outputFileName(o, 'xlsx', 'Monatsbericht')).toBe('Monatsbericht Energie_202610.xlsx')
    expect(outputFileName(output('2026-11-01T06:00:12+01:00', '../x/Bericht.PDF'), 'pdf', 'R')).toBe('Bericht.pdf')
    expect(outputFileName(output('2026-11-01T06:00:12+01:00', ''), 'xlsx', 'Monatsbericht Energie')).toBe('Monatsbericht Energie_2026-11-01.xlsx')
    expect(outputFileName(null, 'pdf', '')).toBe('report.pdf')
  })

  it('reads the ?id= of the route', () => {
    expect(parseReportId('23801')).toBe(23801)
    expect(parseReportId(['5', '6'])).toBe(5)
    expect(parseReportId('0')).toBeNull()
    expect(parseReportId('-3')).toBeNull()
    expect(parseReportId('1e3')).toBeNull()
    expect(parseReportId(undefined)).toBeNull()
    expect(parseReportId('99999999999999999999')).toBeNull()
  })
})
