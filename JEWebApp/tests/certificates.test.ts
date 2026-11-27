/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import type { Certificate } from '../src/api/certificates'
import {
  certificateForm,
  certificatesCsv,
  defaultValidUntil,
  emptyForm,
  filterCertificates,
  formData,
  numberFieldsOf,
  parseReminderMonths,
  serverProblem,
  sortCertificates,
  statusCounts,
} from '../src/certificates/certificates'

function cert(id: number, over: Partial<Certificate> = {}, data: Partial<Certificate['data']> = {}): Certificate {
  return {
    id,
    name: 'Verbrauchsausweis 2017',
    site: { id: 1, name: 'Musterwerk', names: {} },
    building: { id: 10, name: 'Musterwerk', names: {} },
    directory: null,
    data: { type: 'CONSUMPTION', issued: '2017-03-01', validUntil: '2027-02-28', energyClass: 'C', ...data },
    validUntil: data.validUntil ?? '2027-02-28',
    status: 'EXPIRING',
    daysLeft: 94,
    responsible: { count: 1, names: ['Energieteam'] },
    file: null,
    lastReminder: null,
    reminderError: null,
    transfers: {},
    canWrite: true,
    canDelete: true,
    version: 'v1',
    ...over,
  }
}

describe('certificates overview', () => {
  const list = [
    cert(1),
    cert(2, { status: 'VALID', validUntil: '2031-05-31', site: { id: 2, name: 'Außenlager', names: {} } }, { validUntil: '2031-05-31', issuer: 'Ingenieurbüro Muster' }),
    cert(3, { status: 'EXPIRED', validUntil: '2025-01-01' }, { validUntil: '2025-01-01' }),
    cert(4, { status: 'UNKNOWN', validUntil: null }, { validUntil: null }),
  ]

  it('filters by status, site and text', () => {
    expect(filterCertificates(list, { status: 'attention', site: 'all', text: '' }, 'de').map((c) => c.id)).toEqual([1, 3])
    expect(filterCertificates(list, { status: 'all', site: 2, text: '' }, 'de').map((c) => c.id)).toEqual([2])
    expect(filterCertificates(list, { status: 'all', site: 'all', text: 'muster ingenieur' }, 'de').map((c) => c.id)).toEqual([2])
  })

  it('orders by valid-until, without date last', () => {
    expect(sortCertificates(list, 'de').map((c) => c.id)).toEqual([3, 1, 2, 4])
    expect(statusCounts(list)).toEqual({ UNKNOWN: 1, VALID: 1, EXPIRING: 1, EXPIRED: 1 })
  })

  it('writes a CSV with BOM and quoted cells', () => {
    const csv = certificatesCsv([cert(1, {}, { issuer: 'Büro "Nord"; Kassel' })], ['Gebäude', 'Art'], () => 'Verbrauchsausweis', 'de')
    expect(csv.startsWith('﻿Gebäude;Art\r\n')).toBe(true)
    expect(csv).toContain('"Büro ""Nord""; Kassel"')
  })
})

describe('certificate form', () => {
  it('validates like the server and builds the data', () => {
    const form = emptyForm(10)
    form.issued = '2017-03-01'
    form.validUntil = '2016-01-01'
    form.energyClass = 'Z'
    form.numbers.finalEnergy = '-1'
    form.reminderMonths = '6, 30'
    expect(formData(form, 'de').problems.map((p) => p.field + ':' + p.key)).toEqual([
      'validUntil:order',
      'energyClass:class',
      'finalEnergy:number',
      'reminderMonths:range',
    ])

    form.validUntil = defaultValidUntil('2017-03-01')
    form.energyClass = 'C'
    form.numbers.finalEnergy = '142,5'
    form.reminderMonths = '1 6 6'
    const { data, problems } = formData(form, 'de')
    expect(problems).toEqual([])
    expect(data).toMatchObject({ type: 'CONSUMPTION', validUntil: '2027-02-28', finalEnergy: 142.5, reminderMonths: [6, 1], issuer: null })
  })

  it('round-trips a stored certificate', () => {
    const c = cert(1, {}, { finalEnergy: 142.5, responsible: [50], reminderMonths: [3] })
    const form = certificateForm(c, 'de')
    expect(form.numbers.finalEnergy).toBe('142,5')
    expect(form.reminderMonths).toBe('3')
    expect(formData(form, 'de').data).toMatchObject({ finalEnergy: 142.5, responsible: [50], reminderMonths: [3] })
  })

  it('helpers', () => {
    expect(defaultValidUntil('2024-02-29')).toBe('2034-02-28')
    expect(defaultValidUntil('x')).toBe('')
    expect(parseReminderMonths('')).toEqual([6, 1])
    expect(parseReminderMonths('a')).toBeNull()
    expect(numberFieldsOf('DEMAND')).not.toContain('heatConsumption')
    expect(serverProblem('energyClass:class')).toEqual({ field: 'energyClass', key: 'class' })
    expect(serverProblem('Not allowed')).toBeNull()
  })
})
