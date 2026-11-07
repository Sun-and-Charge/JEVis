/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { Action, ActionPlan, LegalPlan, Nonconformity, NonconformityPlan, Npv, Obligation } from '../../src/api/iso'

/* Neutral placeholder data (no customer data). */

export function npv(overrides: Partial<Npv> = {}): Npv {
  return {
    interestRate: 10,
    investment: 1000,
    saving: 300,
    runningCost: 0,
    inflation: 3.1,
    years: 5,
    duration: 3,
    result: 123.4,
    profitabilityIndex: 1.12,
    resultOverX: 1,
    profitabilityIndexOverX: 1,
    sumDeposits: 1000,
    sumPayouts: 1500,
    sumNet: 500,
    yearRows: [
      { year: 1, deposit: 309.3, cost: 0, net: 309.3, discounted: 281.2 },
      { year: 2, deposit: 318.89, cost: 0, net: 318.89, discounted: 263.55 },
    ],
    ...overrides,
  }
}

export function action(id: number, overrides: Partial<Action> = {}): Action {
  return {
    id,
    no: id,
    number: `AP ${String(id).padStart(3, '0')}`,
    title: `Action ${id}`,
    description: null,
    note: null,
    fromUser: null,
    responsible: 'User 1',
    distributor: null,
    createDate: '2026-01-10T00:00:00.000+01:00',
    plannedDate: '2026-06-30T00:00:00.000+02:00',
    doneDate: null,
    status: 'Open',
    field: 'Area 1',
    medium: 'Power',
    mediumName: 'Power',
    seu: '',
    correction: null,
    followAction: null,
    alternativeMeasures: null,
    affectedProcessNote: null,
    energyFlowNote: null,
    evaluationNote: null,
    npv: npv(),
    consumption: { before: 100, after: 80, diff: 20, unit: 'kWh', objectId: null, beforeFrom: null, beforeUntil: null, afterFrom: null, afterUntil: null },
    enpi: null,
    checkList: null,
    daysSinceDone: null,
    savingsSinceDone: null,
    attachments: 0,
    ...overrides,
  }
}

export function actionPlan(id: number, actions: Action[], overrides: Partial<ActionPlan> = {}): ActionPlan {
  return {
    id,
    name: `Plan ${id}`,
    site: null,
    prefix: 'AP',
    statuses: ['Open', 'Done'],
    fields: ['Area 1', 'Area 2'],
    seus: ['SEU 1'],
    media: [
      { id: 'Power', name: 'Power', co2: 0.4 },
      { id: 'Gas', name: 'Gas', co2: 0.2 },
    ],
    actions,
    ...overrides,
  }
}

export function nonconformity(id: number, overrides: Partial<Nonconformity> = {}): Nonconformity {
  return {
    id,
    nr: id,
    number: `NC ${String(id).padStart(3, '0')}`,
    title: `Nonconformity ${id}`,
    creator: 'User 1',
    responsible: 'User 2',
    description: null,
    cause: null,
    immediateMeasures: null,
    correctiveActions: null,
    action: null,
    medium: 'Power',
    fields: ['Area 1'],
    seu: null,
    createDate: '2026-02-01T00:00:00.000+01:00',
    plannedDate: '2026-12-01T00:00:00.000+01:00',
    doneDate: null,
    open: true,
    overdue: false,
    checkList: null,
    warnings: [],
    attachments: 0,
    ...overrides,
  }
}

export function nonconformityPlan(id: number, items: Nonconformity[], overrides: Partial<NonconformityPlan> = {}): NonconformityPlan {
  return { id, name: `NC plan ${id}`, site: null, prefix: 'NC', media: ['Power', 'Gas'], fields: ['Area 1', 'Area 2'], seus: [], items, ...overrides }
}

export function obligation(id: number, overrides: Partial<Obligation> = {}): Obligation {
  return {
    id,
    uuid: null,
    nr: id,
    title: `Provision ${id}`,
    designation: null,
    description: null,
    importance: null,
    link: null,
    category: 'Category 1',
    scope: 'Scope 1',
    relevant: true,
    issueDate: '2020-01-01T00:00:00.000+01:00',
    currentVersionDate: '2024-01-01T00:00:00.000+01:00',
    examinationDate: '2026-03-01T00:00:00.000+01:00',
    attachments: 0,
    ...overrides,
  }
}

export function legalPlan(id: number, obligations: Obligation[], overrides: Partial<LegalPlan> = {}): LegalPlan {
  return { id, name: `Register ${id}`, site: null, categories: ['Category 1', 'Category 2'], scopes: ['Scope 1'], obligations, ...overrides }
}
