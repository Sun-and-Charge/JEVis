/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { CSV_BOM, csvField, csvNumber, csvSeparators, fileName } from '../analysis/table'
import type { RegisterRow } from '../api/registers'
import type { ColumnContext } from '../iso/columns'
import type { RegisterColumnDef } from './columns'

/**
 * CSV of the shown rows with the visible columns of the tab (in the shown order): header = column names, numbers
 * unformatted with the decimal separator of the locale, other cells as shown. UTF-8 with BOM and CRLF (Excel).
 */
export function registerCsv(columns: readonly RegisterColumnDef[], rows: readonly RegisterRow[], locale: string): string {
  const { field, decimal } = csvSeparators(locale)
  const ctx: ColumnContext = { locale, t: (k: string) => k }
  const lines = [columns.map((c) => csvField(c.label, field)).join(field)]
  for (const row of rows) {
    lines.push(
      columns
        .map((c) => {
          const value = c.csv ? c.csv(row) : c.text(row, ctx)
          if (value == null) return ''
          return typeof value === 'number' ? (Number.isFinite(value) ? csvNumber(value, decimal) : '') : csvField(value, field)
        })
        .join(field),
    )
  }
  return CSV_BOM + lines.join('\r\n') + '\r\n'
}

/** "Messstellen - Plant.csv" */
export function registerCsvName(title: string, group: string | null | undefined): string {
  return fileName(group ? `${title} - ${group}` : title) + '.csv'
}
