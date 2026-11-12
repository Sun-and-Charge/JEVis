/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { toIsoLocal } from '../../src/analysis/settings'
import type { CostCell, CostDefinition, CostList, CostResult } from '../../src/api/costs'

/* Neutral fixtures of the cost and result calculation (W4.8). */

const MUSTERWERK = { id: 23053, name: 'Musterwerk' }

export const COST_LIST: CostList = {
  configurations: [
    { id: 23950, name: 'Strom Musterwerk', path: 'Energieverträge / Abrechnung', site: MUSTERWERK, template: { id: 23960, name: 'Stromkosten', readable: true } },
    { id: 23951, name: 'Gas Musterwerk', path: 'Energieverträge / Abrechnung', site: MUSTERWERK, template: { id: 23970, name: null, readable: false } },
  ],
  templates: [
    { id: 23960, name: 'Stromkosten', path: 'Berechnungen / Vorlagen', site: MUSTERWERK },
    { id: 23961, name: 'Wärmekosten', path: null, site: null },
  ],
}

export function configurationDefinition(overrides: Partial<CostDefinition> = {}): CostDefinition {
  return {
    id: 23950,
    kind: 'configuration',
    name: 'Strom Musterwerk',
    templateName: 'Stromkosten',
    timeFrames: [
      { id: 'P1M', name: 'Monat' },
      { id: 'P1Y', name: 'Jahr' },
    ],
    defaultTimeFrame: 'P1M',
    contract: { contractNumber: 'V-1001', marketLocation: 'DE0000000000000000000000000000001', energyType: 'ELECTRICITY', start: '2026-01-01', end: '2027-12-31' },
    inputs: [
      { id: 'i1', variableName: 'verbrauch', label: 'Verbrauch', objectClass: 'Clean Data', selectable: false, selected: { id: 23684, name: 'Bereinigte Daten', readable: true }, options: [] },
      { id: 'i2', variableName: 'leistung', label: null, objectClass: 'Clean Data', selectable: false, selected: { id: 23690, name: 'Leistung', readable: false }, options: [] },
    ],
    grid: { rows: 4, columns: 3 },
    ...overrides,
  }
}

export function templateDefinition(overrides: Partial<CostDefinition> = {}): CostDefinition {
  return {
    id: 23960,
    kind: 'template',
    name: 'Stromkosten',
    templateName: 'Stromkosten',
    timeFrames: [
      { id: 'P1M', name: 'Monat' },
      { id: 'P1Y', name: 'Jahr' },
    ],
    defaultTimeFrame: 'P1M',
    contract: null,
    inputs: [
      {
        id: 'i1',
        variableName: 'verbrauch',
        label: 'Verbrauch',
        objectClass: 'Clean Data',
        selectable: true,
        selected: { id: 23684, name: 'Bereinigte Daten' },
        options: [
          { id: 23684, name: 'Bereinigte Daten' },
          { id: 23685, name: 'Zähler Halle 2' },
        ],
      },
    ],
    grid: { rows: 4, columns: 3 },
    ...overrides,
  }
}

export const RESULT_CELLS: CostCell[] = [
  { row: 3, column: 1, rowSpan: 1, columnSpan: 1, kind: 'text', name: 'Tarif', nameBold: false, value: null, text: 'HT/NT', unit: null, resultBold: false, tooltip: null, analysis: null },
  { row: 0, column: 0, rowSpan: 1, columnSpan: 3, kind: 'label', name: 'Strom', nameBold: true },
  {
    row: 1,
    column: 0,
    rowSpan: 1,
    columnSpan: 2,
    kind: 'value',
    name: 'Arbeitspreis',
    nameBold: false,
    value: 1234.56,
    text: null,
    unit: '€',
    resultBold: true,
    tooltip: 'Verbrauch × Preis',
    analysis: { objectId: 23684, attribute: 'Value' },
  },
  { row: 1, column: 2, rowSpan: 2, columnSpan: 1, kind: 'separator', orientation: 'vertical' },
  { row: 2, column: 0, rowSpan: 1, columnSpan: 2, kind: 'separator', orientation: 'horizontal' },
  { row: 3, column: 0, rowSpan: 1, columnSpan: 1, kind: 'value', name: 'Netz', nameBold: true, value: null, text: null, unit: '€', resultBold: false, tooltip: null, analysis: null },
]

/** November 2026 in the local time zone (the server resolves the month in the browser's time zone). */
export const NOVEMBER = { from: toIsoLocal(new Date(2026, 10, 1)), to: toIsoLocal(new Date(2026, 10, 30, 23, 59, 59, 999)) }
export const YEAR = { from: toIsoLocal(new Date(2026, 0, 1)), to: toIsoLocal(new Date(2026, 11, 31, 23, 59, 59, 999)) }

export function costResult(range: { from: string; to: string } = NOVEMBER, overrides: Partial<CostResult> = {}): CostResult {
  return {
    ...range,
    rows: 4,
    columns: 3,
    cells: RESULT_CELLS,
    warnings: ['Formel "Netz": Eingabe "leistung" hat keine Werte im Zeitraum'],
    ...overrides,
  }
}
