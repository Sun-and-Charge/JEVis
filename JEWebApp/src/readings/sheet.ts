/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from '../api/http'
import { loadRoundSheet } from '../api/readingRounds'
import { saveBlob } from '../iso/download'

/*
 * Route sheet of a reading round (E8.2): the PDF needs the Bearer token, so it is loaded as a blob and shown through an
 * object URL. The tab is opened before the request (in the click, else popup blockers refuse it) and gets the PDF
 * when it is there; without a tab (blocked) the PDF is saved as a file. The URL is revoked after a minute.
 */

/** File name as the server's Content-Disposition: Laufzettel_<round>_<due>.pdf */
export function sheetFileName(name: string, due: string | null | undefined): string {
  const base = name.replace(/[\\/:*?"<>|\s]+/g, '_').replace(/^_+|_+$/g, '') || 'Runde'
  return `Laufzettel_${base}${due ? '_' + due : ''}.pdf`
}

/** Opens a PDF loaded by `load` in a new tab (or saves it); rejects with the load error (the tab is closed then). */
export async function openPdf(load: () => Promise<Blob>, fileName: string, loadingText = ''): Promise<void> {
  let win: Window | null = null
  try {
    win = typeof window.open === 'function' ? window.open('', '_blank') : null
  } catch {
    win = null
  }
  if (win && loadingText) {
    try {
      win.document.title = fileName
      win.document.body.textContent = loadingText
    } catch {
      /* another origin or a closed tab: the text is only a courtesy */
    }
  }
  let blob: Blob
  try {
    blob = await load()
  } catch (e) {
    win?.close()
    throw e
  }
  if (blob.type !== 'application/pdf') blob = new Blob([blob], { type: 'application/pdf' })
  if (win && !win.closed && typeof URL.createObjectURL === 'function') {
    const url = URL.createObjectURL(blob)
    win.location.href = url
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
    return
  }
  saveBlob(blob, fileName)
}

/** The route sheet of a round for its current due date (the server's default). */
export function openRoundSheet(http: Pick<HttpClient, 'getBlob'>, round: { id: number; name: string; due?: string | null; nextDue?: string | null }, locale: string, loadingText = ''): Promise<void> {
  return openPdf(() => loadRoundSheet(http, round.id, locale), sheetFileName(round.name, round.due ?? round.nextDue), loadingText)
}
