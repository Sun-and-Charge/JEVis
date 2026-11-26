/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import { buildXlsx, crc32, excelDate, jpegPdf, pdfText, zipStore } from '../src/analysis/fileExport'

const text = (b: Uint8Array) => new TextDecoder('latin1').decode(b)

describe('file exports (R18)', () => {
  it('crc32 of a known text', () => {
    expect(crc32(new TextEncoder().encode('123456789'))).toBe(0xcbf43926)
  })

  it('zip with stored files: signatures, names and contents', () => {
    const zip = zipStore([{ name: 'a.txt', data: new TextEncoder().encode('hello') }])
    const view = new DataView(zip.buffer)
    expect(view.getUint32(0, true)).toBe(0x04034b50)
    expect(view.getUint32(zip.length - 22, true)).toBe(0x06054b50)
    expect(text(zip)).toContain('a.txthello')
  })

  it('excel date of a local time stamp', () => {
    expect(excelDate(new Date(2026, 0, 1, 12, 0).getTime())).toBeCloseTo(46023.5, 6)
  })

  it('xlsx with header, dates and values', () => {
    const t0 = new Date(2026, 10, 1, 0, 0).getTime()
    const xlsx = buildXlsx(
      [{ name: 'Strom <Gesamt>', unit: 'kWh', samples: [[t0, 1.5, null], [t0 + 900_000, 2, null]] }],
      'Zeit',
      'Lastgang',
    )
    const s = text(xlsx)
    expect(s).toContain('xl/worksheets/sheet1.xml')
    expect(s).toContain('Strom &lt;Gesamt&gt; [kWh]')
    expect(s).toContain('<c r="B2"><v>1.5</v></c>')
    expect(s).toContain('<sheet name="Lastgang"')
  })

  it('pdf with the image, a title and a valid trailer', () => {
    const pdf = text(jpegPdf(new Uint8Array([0xff, 0xd8, 0xff, 0xd9]), 800, 400, 'Analyse (Strom)'))
    expect(pdf.startsWith('%PDF-1.4')).toBe(true)
    expect(pdf).toContain('/Filter /DCTDecode /Length 4')
    expect(pdf).toContain(String.raw`(Analyse \(Strom\)) Tj`)
    const startxref = Number(pdf.match(/startxref\n(\d+)/)![1])
    expect(pdf.slice(startxref, startxref + 4)).toBe('xref')
    expect(pdf.trimEnd().endsWith('%%EOF')).toBe(true)
  })

  it('pdf text in WinAnsi: en dash and euro sign, unknown characters as ?', () => {
    expect([...pdfText('a – 5 €')]).toEqual([97, 32, 0x96, 32, 53, 32, 0x80])
    expect([...pdfText('Ω')]).toEqual([63])
  })
})
