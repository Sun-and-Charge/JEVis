/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { ReportFiles, ReportOutput, ReportSummary } from '../../src/api/reports'

/** Mocked answers of /web/reports (neutral names, no real data). */
export function report(id: number, overrides: Partial<ReportSummary> = {}): ReportSummary {
  return {
    id,
    name: 'Monatsbericht Energie',
    className: 'Periodic Report',
    path: 'Berichte',
    site: { id: 7100, name: 'Musterwerk' },
    enabled: true,
    schedule: 'MONTHLY',
    latest: { timestamp: '2026-11-01T06:00:12.000+01:00', pdf: true, xlsx: true, name: 'Monatsbericht Energie_202610.pdf' },
    count: 2,
    ...overrides,
  }
}

export function output(timestamp: string, name: string, xlsx: string | null = null): ReportOutput {
  return { timestamp, name, xlsx }
}

export function reportFiles(id: number, files: ReportOutput[], name = 'Monatsbericht Energie'): ReportFiles {
  return { id, name, files }
}

/** Two outputs of the monthly report: October with XLSX, September PDF only. */
export const monthlyFiles = (id = 23801) =>
  reportFiles(id, [
    output('2026-11-01T06:00:12.000+01:00', 'Monatsbericht Energie_202610.pdf', '2026-11-01T06:00:11.000+01:00'),
    output('2026-10-01T06:00:09.000+02:00', 'Monatsbericht Energie_202609.pdf'),
  ])
