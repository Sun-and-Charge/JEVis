/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { ENERGY_CLASSES, type Certificate, type CertificateData, type CertificateStatus, type CertificateType, type CertificateUse } from '../api/certificates'
import { localName } from '../api/navigation'
import { parseUserNumber } from '../edit/edit'
import { matchesSite, matchesTerms, searchTerms, type SiteFilter } from '../modules/search'

/*
 * Energy certificates (BA-07): filter and order of the overview, the form of the editor (numbers as text in the user's
 * format) and its checks (the same rules as CertificateLogic.validate on the server), and the CSV of the overview.
 */

export type StatusFilter = 'all' | 'attention' | CertificateStatus

export interface CertificateFilter {
  status: StatusFilter
  site: SiteFilter
  text: string
}

export const DEFAULT_CERTIFICATE_FILTER: CertificateFilter = { status: 'all', site: 'all', text: '' }

/** "attention" = expired or expiring */
export function matchesStatus(c: Certificate, status: StatusFilter): boolean {
  if (status === 'all') return true
  if (status === 'attention') return c.status === 'EXPIRED' || c.status === 'EXPIRING'
  return c.status === status
}

export function filterCertificates(list: readonly Certificate[], filter: CertificateFilter, locale: string): Certificate[] {
  const terms = searchTerms(filter.text, locale)
  return list.filter(
    (c) =>
      matchesStatus(c, filter.status) &&
      matchesSite(c.site, filter.site) &&
      matchesTerms(
        terms,
        [c.name, c.building ? localName(c.building, locale) : null, c.data.registrationNumber, c.data.issuer, c.data.energyClass, c.data.note, ...c.responsible.names],
        locale,
      ),
  )
}

/** Expiring first: by valid-until date (without date last), then building and name. */
export function sortCertificates(list: readonly Certificate[], locale: string): Certificate[] {
  return [...list].sort((a, b) => {
    if (a.validUntil !== b.validUntil) {
      if (!a.validUntil) return 1
      if (!b.validUntil) return -1
      return a.validUntil < b.validUntil ? -1 : 1
    }
    const ba = a.building ? localName(a.building, locale) : ''
    const bb = b.building ? localName(b.building, locale) : ''
    return ba.localeCompare(bb, locale) || a.name.localeCompare(b.name, locale)
  })
}

export function statusCounts(list: readonly Certificate[]): Record<CertificateStatus, number> {
  const counts: Record<CertificateStatus, number> = { UNKNOWN: 0, VALID: 0, EXPIRING: 0, EXPIRED: 0 }
  for (const c of list) counts[c.status]++
  return counts
}

/* ---------------------------------------------------------------- form */

export const NUMBER_FIELDS = ['finalEnergy', 'primaryEnergy', 'heatConsumption', 'electricityConsumption', 'referenceArea', 'co2'] as const
export type NumberField = (typeof NUMBER_FIELDS)[number]

/** Value fields shown for the type (consumption: measured values, demand: calculated values); area and CO₂ always. */
export function numberFieldsOf(type: CertificateType | null): NumberField[] {
  if (type === 'DEMAND') return ['finalEnergy', 'primaryEnergy', 'referenceArea', 'co2']
  return ['heatConsumption', 'electricityConsumption', 'finalEnergy', 'primaryEnergy', 'referenceArea', 'co2']
}

export interface CertificateForm {
  building: number | null
  type: CertificateType | null
  use: CertificateUse | ''
  registrationNumber: string
  issued: string
  validUntil: string
  issuer: string
  responsible: number[]
  energyClass: string
  numbers: Record<NumberField, string>
  reminderMonths: string
  note: string
}

function numberText(v: number | null | undefined, locale: string): string {
  return v == null ? '' : v.toLocaleString(locale, { maximumFractionDigits: 6, useGrouping: false })
}

export function emptyForm(building: number | null): CertificateForm {
  return {
    building,
    type: 'CONSUMPTION',
    use: '',
    registrationNumber: '',
    issued: '',
    validUntil: '',
    issuer: '',
    responsible: [],
    energyClass: '',
    numbers: { finalEnergy: '', primaryEnergy: '', heatConsumption: '', electricityConsumption: '', referenceArea: '', co2: '' },
    reminderMonths: '6, 1',
    note: '',
  }
}

export function certificateForm(c: Certificate, locale: string): CertificateForm {
  const d = c.data
  const numbers = {} as Record<NumberField, string>
  for (const f of NUMBER_FIELDS) numbers[f] = numberText(d[f], locale)
  return {
    building: c.building?.id ?? null,
    type: d.type ?? null,
    use: d.use ?? '',
    registrationNumber: d.registrationNumber ?? '',
    issued: d.issued ?? '',
    validUntil: d.validUntil ?? '',
    issuer: d.issuer ?? '',
    responsible: [...(d.responsible ?? [])],
    energyClass: d.energyClass ?? '',
    numbers,
    reminderMonths: (d.reminderMonths ?? [6, 1]).join(', '),
    note: d.note ?? '',
  }
}

export function sameForm(a: CertificateForm, b: CertificateForm): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}

/** Ten years minus one day after the issue date (GEG), '' for an invalid date. */
export function defaultValidUntil(issued: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(issued)
  if (!m) return ''
  const d = new Date(Date.UTC(Number(m[1]) + 10, Number(m[2]) - 1, Number(m[3])))
  if (Number.isNaN(d.getTime())) return ''
  d.setUTCDate(d.getUTCDate() - 1)
  return d.toISOString().slice(0, 10)
}

export interface FormProblem {
  field: string
  key: string
}

function validDate(text: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return false
  const d = new Date(text + 'T00:00:00Z')
  return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === text
}

/** Reminder months from "6, 1" (0–24, no duplicates); null if invalid. Empty: the defaults. */
export function parseReminderMonths(text: string): number[] | null {
  const parts = text.split(/[,;\s]+/).filter(Boolean)
  if (!parts.length) return [6, 1]
  const months: number[] = []
  for (const p of parts) {
    if (!/^\d+$/.test(p)) return null
    const n = Number(p)
    if (n > 24) return null
    if (!months.includes(n)) months.push(n)
  }
  return months.sort((a, b) => b - a)
}

/**
 * The data to send, or the problems (keys `certificates.problem.<key>`; same rules as the server).
 */
export function formData(form: CertificateForm, locale: string): { data: CertificateData | null; problems: FormProblem[] } {
  const problems: FormProblem[] = []
  if (form.building == null) problems.push({ field: 'building', key: 'required' })
  if (!form.type) problems.push({ field: 'type', key: 'required' })
  if (form.issued && !validDate(form.issued)) problems.push({ field: 'issued', key: 'date' })
  if (!form.validUntil) problems.push({ field: 'validUntil', key: 'required' })
  else if (!validDate(form.validUntil)) problems.push({ field: 'validUntil', key: 'date' })
  else if (form.issued && validDate(form.issued) && form.validUntil <= form.issued) problems.push({ field: 'validUntil', key: 'order' })
  if (form.energyClass && !(ENERGY_CLASSES as readonly string[]).includes(form.energyClass)) problems.push({ field: 'energyClass', key: 'class' })
  const numbers: Partial<Record<NumberField, number | null>> = {}
  for (const f of NUMBER_FIELDS) {
    const text = form.numbers[f].trim()
    if (!text) {
      numbers[f] = null
      continue
    }
    const n = parseUserNumber(text, locale)
    if (n == null || !Number.isFinite(n) || n < 0) problems.push({ field: f, key: 'number' })
    else numbers[f] = n
  }
  const reminderMonths = parseReminderMonths(form.reminderMonths)
  if (!reminderMonths) problems.push({ field: 'reminderMonths', key: 'range' })
  if (problems.length) return { data: null, problems }
  const text = (s: string) => (s.trim() ? s.trim() : null)
  return {
    data: {
      type: form.type,
      use: form.use || null,
      registrationNumber: text(form.registrationNumber),
      issued: text(form.issued),
      validUntil: form.validUntil,
      issuer: text(form.issuer),
      responsible: [...form.responsible],
      energyClass: text(form.energyClass),
      ...numbers,
      reminderMonths: reminderMonths!,
      note: text(form.note),
    },
    problems,
  }
}

/** A server answer "field:key" (400) as problem, null for other texts. */
export function serverProblem(body: string | null | undefined): FormProblem | null {
  const m = /^([A-Za-z]+):([a-z]+)$/.exec((body ?? '').trim())
  return m ? { field: m[1], key: m[2] } : null
}

/* ---------------------------------------------------------------- CSV */

function csvCell(v: unknown): string {
  const s = v == null ? '' : String(v)
  return /[";\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
}

/** CSV of the shown certificates (";", numbers as stored, dates yyyy-MM-dd) with a header in the user's language. */
export function certificatesCsv(list: readonly Certificate[], header: readonly string[], typeLabel: (c: Certificate) => string, locale: string): string {
  const lines = [header.map(csvCell).join(';')]
  for (const c of list) {
    const d = c.data
    lines.push(
      [
        c.building ? localName(c.building, locale) : '',
        typeLabel(c),
        d.registrationNumber,
        d.issued,
        d.validUntil,
        c.status,
        d.energyClass,
        d.heatConsumption,
        d.electricityConsumption,
        d.finalEnergy,
        d.primaryEnergy,
        d.referenceArea,
        d.co2,
        d.issuer,
        c.responsible.names.join(', '),
        c.file?.name,
      ]
        .map(csvCell)
        .join(';'),
    )
  }
  return '﻿' + lines.join('\r\n') + '\r\n'
}
