/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/*
 * File exports without extra libraries (R18): XLSX (SpreadsheetML in an uncompressed ZIP) for the values of a chart and a
 * one-page PDF that embeds the chart as JPEG.
 */
import type { RowView } from './model'
import { buildTimeTable } from './table'

const encoder = new TextEncoder()

/* ------------------------------------------------------------------------------------------------ ZIP (stored) */

const CRC_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c >>> 0
  }
  return table
})()

export function crc32(data: Uint8Array): number {
  let c = 0xffffffff
  for (let i = 0; i < data.length; i++) c = CRC_TABLE[(c ^ data[i]) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

/** A ZIP archive with the files stored uncompressed (enough for small office files). */
export function zipStore(files: { name: string; data: Uint8Array }[]): Uint8Array {
  const parts: Uint8Array[] = []
  const central: Uint8Array[] = []
  let offset = 0
  for (const file of files) {
    const name = encoder.encode(file.name)
    const crc = crc32(file.data)
    const local = new DataView(new ArrayBuffer(30))
    local.setUint32(0, 0x04034b50, true)
    local.setUint16(4, 20, true)
    local.setUint16(6, 0x0800, true) // UTF-8 names
    local.setUint16(8, 0, true) // stored
    local.setUint32(14, crc, true)
    local.setUint32(18, file.data.length, true)
    local.setUint32(22, file.data.length, true)
    local.setUint16(26, name.length, true)
    parts.push(new Uint8Array(local.buffer), name, file.data)
    const entry = new DataView(new ArrayBuffer(46))
    entry.setUint32(0, 0x02014b50, true)
    entry.setUint16(4, 20, true)
    entry.setUint16(6, 20, true)
    entry.setUint16(8, 0x0800, true)
    entry.setUint32(16, crc, true)
    entry.setUint32(20, file.data.length, true)
    entry.setUint32(24, file.data.length, true)
    entry.setUint16(28, name.length, true)
    entry.setUint32(42, offset, true)
    central.push(new Uint8Array(entry.buffer), name)
    offset += 30 + name.length + file.data.length
  }
  const centralSize = central.reduce((n, p) => n + p.length, 0)
  const end = new DataView(new ArrayBuffer(22))
  end.setUint32(0, 0x06054b50, true)
  end.setUint16(8, files.length, true)
  end.setUint16(10, files.length, true)
  end.setUint32(12, centralSize, true)
  end.setUint32(16, offset, true)
  return concat([...parts, ...central, new Uint8Array(end.buffer)])
}

function concat(parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0))
  let at = 0
  for (const p of parts) {
    out.set(p, at)
    at += p.length
  }
  return out
}

/* ------------------------------------------------------------------------------------------------ XLSX */

function xml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/** Excel serial date of an epoch time, in the browser's local time (like the CSV export). */
export function excelDate(epochMs: number): number {
  const local = epochMs - new Date(epochMs).getTimezoneOffset() * 60_000
  return local / 86_400_000 + 25_569
}

function column(index: number): string {
  let s = ''
  for (let n = index + 1; n > 0; n = Math.floor((n - 1) / 26)) s = String.fromCharCode(65 + ((n - 1) % 26)) + s
  return s
}

/** XLSX with one sheet: time stamps (as Excel dates) and one column per row, header with name and unit. */
export function buildXlsx(rows: Pick<RowView, 'name' | 'unit' | 'samples'>[], timeHeader: string, sheetName: string): Uint8Array {
  const table = buildTimeTable(rows)
  const header = [timeHeader, ...rows.map((r) => (r.unit ? `${r.name} [${r.unit}]` : r.name))]
  const lines: string[] = []
  lines.push(`<row r="1">${header.map((h, c) => `<c r="${column(c)}1" t="inlineStr" s="1"><is><t>${xml(h)}</t></is></c>`).join('')}</row>`)
  table.timestamps.forEach((ts, i) => {
    const r = i + 2
    const cells = [`<c r="A${r}" s="2"><v>${excelDate(ts)}</v></c>`]
    table.values.forEach((values, c) => {
      const v = values[i]
      const ref = `${column(c + 1)}${r}`
      if (typeof v === 'number' && Number.isFinite(v)) cells.push(`<c r="${ref}"><v>${v}</v></c>`)
      else if (typeof v === 'string' && v) cells.push(`<c r="${ref}" t="inlineStr"><is><t>${xml(v)}</t></is></c>`)
    })
    lines.push(`<row r="${r}">${cells.join('')}</row>`)
  })
  const widths = header.map((h, c) => `<col min="${c + 1}" max="${c + 1}" width="${c === 0 ? 18 : Math.min(40, Math.max(12, h.length + 2))}" customWidth="1"/>`).join('')
  const sheet =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
    '<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>' +
    `<cols>${widths}</cols><sheetData>${lines.join('')}</sheetData></worksheet>`
  const name = xml(sheetName.replace(/[\\/?*[\]:]/g, ' ').slice(0, 31).trim() || 'Daten')
  const files: Record<string, string> = {
    '[Content_Types].xml':
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
      '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
      '<Default Extension="xml" ContentType="application/xml"/>' +
      '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>' +
      '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' +
      '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>' +
      '</Types>',
    '_rels/.rels':
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>' +
      '</Relationships>',
    'xl/workbook.xml':
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' +
      `<sheets><sheet name="${name}" sheetId="1" r:id="rId1"/></sheets></workbook>`,
    'xl/_rels/workbook.xml.rels':
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>' +
      '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>' +
      '</Relationships>',
    'xl/styles.xml':
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
      '<numFmts count="1"><numFmt numFmtId="164" formatCode="dd.mm.yyyy hh:mm"/></numFmts>' +
      '<fonts count="2"><font><sz val="10"/><name val="Arial"/></font><font><b/><sz val="10"/><name val="Arial"/></font></fonts>' +
      '<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>' +
      '<borders count="1"><border/></borders>' +
      '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
      '<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>' +
      '<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>' +
      '<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>' +
      '</styleSheet>',
    'xl/worksheets/sheet1.xml': sheet,
  }
  return zipStore(Object.entries(files).map(([n, text]) => ({ name: n, data: encoder.encode(text) })))
}

/* ------------------------------------------------------------------------------------------------ PDF */

/** Bytes of a data URL (base64). */
export function dataUrlBytes(url: string): Uint8Array {
  const base64 = url.slice(url.indexOf(',') + 1)
  const raw = atob(base64)
  const out = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i)
  return out
}

/** WinAnsi codes of the typographic characters outside Latin-1 that occur in names. */
const WIN_ANSI: Record<number, number> = {
  0x20ac: 0x80, // €
  0x201a: 0x82,
  0x201e: 0x84, // „
  0x2026: 0x85, // …
  0x2018: 0x91,
  0x2019: 0x92,
  0x201c: 0x93, // “
  0x201d: 0x94,
  0x2022: 0x95,
  0x2013: 0x96, // –
  0x2014: 0x97, // —
}

/** PDF text string in WinAnsi with escaped brackets; other characters become "?". */
export function pdfText(text: string): Uint8Array {
  const escaped = text.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)')
  const out = new Uint8Array(escaped.length)
  for (let i = 0; i < escaped.length; i++) {
    const c = escaped.charCodeAt(i)
    out[i] = WIN_ANSI[c] ?? (c < 256 ? c : 63)
  }
  return out
}

/**
 * A one-page A4 landscape PDF with a title line and the JPEG image (pixel size `width`×`height`) scaled to the page.
 */
export function jpegPdf(jpeg: Uint8Array, width: number, height: number, title: string): Uint8Array {
  const pageW = 842
  const pageH = 595
  const margin = 36
  const titleH = title ? 28 : 0
  const scale = Math.min((pageW - 2 * margin) / width, (pageH - 2 * margin - titleH) / height)
  const w = width * scale
  const h = height * scale
  const x = (pageW - w) / 2
  const y = margin
  const ascii = (s: string) => encoder.encode(s)
  const content = concat([
    ascii(`q ${w.toFixed(2)} 0 0 ${h.toFixed(2)} ${x.toFixed(2)} ${y.toFixed(2)} cm /Im1 Do Q\n`),
    title ? concat([ascii(`BT /F1 14 Tf ${margin} ${pageH - margin - 14} Td (`), pdfText(title), ascii(') Tj ET\n')]) : new Uint8Array(0),
  ])
  const objects: Uint8Array[] = [
    ascii('<< /Type /Catalog /Pages 2 0 R >>'),
    ascii('<< /Type /Pages /Kids [3 0 R] /Count 1 >>'),
    ascii(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageW} ${pageH}] /Resources << /XObject << /Im1 4 0 R >> /Font << /F1 6 0 R >> >> /Contents 5 0 R >>`),
    concat([
      ascii(`<< /Type /XObject /Subtype /Image /Width ${width} /Height ${height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.length} >>\nstream\n`),
      jpeg,
      ascii('\nendstream'),
    ]),
    concat([ascii(`<< /Length ${content.length} >>\nstream\n`), content, ascii('endstream')]),
    ascii('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>'),
  ]
  const parts: Uint8Array[] = [ascii('%PDF-1.4\n%âãÏÓ\n')]
  const offsets: number[] = []
  let at = parts[0].length
  objects.forEach((body, i) => {
    offsets.push(at)
    const obj = concat([ascii(`${i + 1} 0 obj\n`), body, ascii('\nendobj\n')])
    parts.push(obj)
    at += obj.length
  })
  const xref = [`xref\n0 ${objects.length + 1}\n`, '0000000000 65535 f \n', ...offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`)].join('')
  parts.push(ascii(xref), ascii(`trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${at}\n%%EOF\n`))
  return concat(parts)
}
