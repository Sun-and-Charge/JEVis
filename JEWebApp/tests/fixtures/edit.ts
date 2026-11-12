/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { EntryInfo } from '../../src/api/entry'
import { HttpError } from '../../src/api/http'
import { browserTimeZone } from '../../src/analysis/settings'

/** Mocked answer of GET /web/rows/{id}/entry (neutral names, no real data). */
export function entryInfo(overrides: Partial<EntryInfo> = {}): EntryInfo {
  return {
    id: 8100,
    name: 'Strom - Gesamt',
    unit: 'kWh',
    canEnter: true,
    canDelete: true,
    counter: true,
    period: 'P1D',
    mode: 'DAY',
    // the browser zone, so local dates of the tests are the site's dates
    timeZone: browserTimeZone(),
    suggestedTimestamp: new Date(2026, 10, 12).toISOString(),
    last: { timestamp: new Date(2026, 10, 11).toISOString(), value: 1812.5 },
    limits: { min: 0, max: 500 },
    ...overrides,
  }
}

/** An HttpError as the client throws it (body as text). */
export function httpError(status: number, body: unknown = ''): HttpError {
  return new HttpError(status, typeof body === 'string' ? body : JSON.stringify(body))
}
