/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { formatValue, localTimestamp } from './format'
import type { RowView } from './model'

/** Values of several rows on one common time line (union of all timestamps). */
export interface TimeTable {
  timestamps: number[]
  /** values[row][timestamp index]; null where the row has no sample */
  values: (number | string | null)[][]
  notes: (string | null)[][]
}

export function buildTimeTable(rows: Pick<RowView, 'samples'>[]): TimeTable {
  const set = new Set<number>()
  for (const row of rows) for (const s of row.samples) set.add(s[0])
  const timestamps = [...set].sort((a, b) => a - b)
  const index = new Map(timestamps.map((t, i) => [t, i]))
  const values = rows.map(() => new Array<number | string | null>(timestamps.length).fill(null))
  const notes = rows.map(() => new Array<string | null>(timestamps.length).fill(null))
  rows.forEach((row, r) => {
    for (const [t, v, note] of row.samples) {
      const i = index.get(t)!
      values[r][i] = v ?? null
      notes[r][i] = note ?? null
    }
  })
  return { timestamps, values, notes }
}

/** Separators of a CSV file for the locale: German Excel expects ";" and ",", English "," and ".". */
export function csvSeparators(locale: string): { field: string; decimal: string } {
  return locale.toLowerCase().startsWith('de') ? { field: ';', decimal: ',' } : { field: ',', decimal: '.' }
}

/** Quotes a field when needed; text starting with = + - @ is prefixed with ' so spreadsheets do not run it as formula. */
export function csvField(text: string, separator: string): string {
  let value = text
  if (/^[=+\-@\t\r]/.test(value) && !/^-?\d/.test(value)) value = "'" + value
  if (value.includes(separator) || /["\r\n]/.test(value)) value = '"' + value.replace(/"/g, '""') + '"'
  return value
}

function csvNumber(value: number, decimal: string): string {
  // full precision without exponent notation or grouping
  let text = Number.isInteger(value) ? String(value) : String(+value.toPrecision(15))
  if (/e/i.test(text)) text = value.toFixed(20).replace(/0+$/, '').replace(/\.$/, '')
  return decimal === '.' ? text : text.replace('.', decimal)
}

export const CSV_BOM = '﻿'

/**
 * CSV of the rows of a chart: first column the local time (2026-09-01 14:15:00), then one column per row
 * ("name [unit]"). UTF-8 with BOM and CRLF, so Excel opens it with the right encoding.
 */
export function buildCsv(rows: Pick<RowView, 'name' | 'unit' | 'samples'>[], locale: string, timeHeader: string): string {
  const { field, decimal } = csvSeparators(locale)
  const table = buildTimeTable(rows)
  const lines: string[] = []
  lines.push([timeHeader, ...rows.map((r) => (r.unit ? `${r.name} [${r.unit}]` : r.name))].map((h) => csvField(h, field)).join(field))
  table.timestamps.forEach((t, i) => {
    const cells = [localTimestamp(t)]
    for (const column of table.values) {
      const v = column[i]
      cells.push(v == null ? '' : typeof v === 'number' ? csvNumber(v, decimal) : csvField(v, field))
    }
    lines.push(cells.join(field))
  })
  return CSV_BOM + lines.join('\r\n') + '\r\n'
}

/** Formatted cell of a data table. */
export function tableCell(row: Pick<RowView, 'decimals'>, value: number | string | null, locale: string): string {
  return formatValue(value, locale, row.decimals)
}

/** File name part from a chart or analysis name (no characters that file systems reject). */
export function fileName(name: string): string {
  const cleaned = name.replace(/[\\/:*?"<>|\u0000-\u001f]+/g, '_').trim()
  return cleaned || 'chart'
}
