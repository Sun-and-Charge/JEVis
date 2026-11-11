/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { AccountingConfiguration, ContractAttribute, ContractList, ContractObject } from '../../src/api/contracts'
import type { Invoice, InvoiceDetail } from '../../src/api/invoices'

/** Mocked answers of /web/contracts and /web/invoices (neutral names, no real data). */

const MUSTERWERK = { id: 7100, name: 'Musterwerk' }
const ZWEIGWERK = { id: 7101, name: 'Zweigwerk' }

export function attribute(name: string, overrides: Partial<ContractAttribute> = {}): ContractAttribute {
  return { name, label: name, type: 'number', value: null, unit: null, timestamp: null, history: false, ...overrides }
}

export function contractObject(id: number, overrides: Partial<ContractObject> = {}): ContractObject {
  return {
    id,
    name: 'Strom - Energieversorger Nord',
    className: 'Electricity Supply Contractor',
    classLabel: 'Stromlieferant',
    path: 'Energieverträge / Energielieferung',
    site: MUSTERWERK,
    contractor: { id: 23910, name: 'Energieversorger Nord', readable: true },
    attributes: [
      attribute('Energy Price HT', { label: 'Arbeitspreis HT', value: 0.28, unit: '€/kWh', timestamp: '2026-01-01T00:00:00.000+01:00', history: true }),
      attribute('Demand Charge', {
        label: 'Leistungspreis',
        type: 'ranging',
        value: [
          { from: 100, to: null, value: 10 },
          { from: 0, to: 100, value: 12.5 },
        ],
        unit: null,
        timestamp: '2026-01-01T00:00:00.000+01:00',
        history: true,
      }),
      attribute('Customer Number', { label: 'Kundennummer', type: 'text', value: 'K-4711', timestamp: '2025-12-01T00:00:00.000+01:00' }),
      attribute('Energy Price NT', { label: 'Arbeitspreis NT', value: null, unit: '€/kWh' }),
    ],
    ...overrides,
  }
}

export function configuration(id: number, overrides: Partial<AccountingConfiguration> = {}): AccountingConfiguration {
  return {
    id,
    name: 'Strom Musterwerk',
    path: 'Energieverträge / Abrechnung',
    site: MUSTERWERK,
    contractNumber: 'V-1001',
    contractType: 'ELECTRICITY',
    marketLocation: 'DE0000000000000000000000000000001',
    contractDate: '2025-12-01',
    firstRate: '2026-01-01',
    periodOfNotice: '2027-09-30',
    start: '2026-01-01',
    end: '2027-12-31',
    energyType: 'ELECTRICITY',
    ...overrides,
  }
}

export function contractList(): ContractList {
  return {
    groups: [
      { kind: 'suppliers', objects: [contractObject(23901)] },
      {
        kind: 'gridOperators',
        objects: [
          contractObject(23920, {
            name: 'Netzbetreiber Süd',
            className: 'Energy Grid Operator',
            classLabel: 'Netzbetreiber',
            site: ZWEIGWERK,
            contractor: { id: 23999, readable: false },
            attributes: [attribute('Grid Fee', { label: 'Netzentgelt', value: 0.071, unit: '€/kWh', timestamp: '2026-01-01T00:00:00.000+01:00' })],
          }),
        ],
      },
      {
        kind: 'contractors',
        objects: [
          contractObject(23910, {
            name: 'Energieversorger Nord',
            className: 'Energy Contractor',
            classLabel: 'Vertragspartner',
            path: 'Energieverträge / Partner',
            contractor: null,
            attributes: [attribute('City', { label: 'Ort', type: 'text', value: 'Musterstadt' })],
          }),
        ],
      },
    ],
    configurations: [configuration(23950)],
  }
}

export function invoice(id: number, overrides: Partial<Invoice> = {}): Invoice {
  return {
    id,
    number: 'EN-202610-S',
    date: '2026-11-03',
    format: 'ZUGFeRD 2.3 EN16931',
    seller: 'Energieversorger Nord',
    buyer: 'Musterkunde GmbH',
    periodStart: '2026-10-01',
    periodEnd: '2026-10-31',
    medium: 'ELECTRICITY',
    meterPoint: 'DE0000000000000000000000000000001',
    consumption: 41250,
    consumptionUnit: 'kWh',
    net: 11550,
    tax: 2194.5,
    gross: 13744.5,
    currency: 'EUR',
    credit: false,
    file: 'EN-202610-S.pdf',
    path: 'Energieverträge / Energierechnungen / Strom',
    site: MUSTERWERK,
    ...overrides,
  }
}

/** Electricity October and September, a credit note for September, gas October (XML, other site). */
export function invoiceSet(): Invoice[] {
  return [
    invoice(24601),
    invoice(24604, { number: 'GS-202610', medium: 'GAS', seller: 'Gaswerk Ost', meterPoint: 'DE0000000000000000000000000000002', consumption: 1200, consumptionUnit: 'm³', net: 1000, tax: 190, gross: 1190, file: 'GS-202610.xml', site: ZWEIGWERK, path: 'Energierechnungen / Gas' }),
    invoice(24600, { number: 'EN-202609-S', date: '2026-10-02', periodStart: '2026-09-01', periodEnd: '2026-09-30', consumption: 40000, net: 11200, tax: 2128, gross: 13328 }),
    invoice(24603, { number: 'GU-202609', date: '2026-10-20', periodStart: '2026-09-01', periodEnd: '2026-09-30', consumption: -1000, net: -280, tax: -53.2, gross: -333.2, credit: true, file: null }),
  ]
}

export function invoiceDetail(base: Invoice, overrides: Partial<InvoiceDetail> = {}): InvoiceDetail {
  return {
    ...base,
    sellerVatId: 'DE000000001',
    items: [
      { description: 'Arbeitspreis', quantity: 41250, unit: 'kWh', unitPrice: 0.25, net: 10312.5, taxRate: 19, periodStart: '2026-10-01', periodEnd: '2026-10-31' },
      { description: 'Grundpreis', quantity: 1, unit: 'Monat', unitPrice: 1237.5, net: 1237.5, taxRate: 19, periodStart: null, periodEnd: null },
    ],
    ...overrides,
  }
}
