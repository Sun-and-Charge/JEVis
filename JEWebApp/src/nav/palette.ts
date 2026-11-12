/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { searchTerms } from '../modules/search'

/*
 * Search of the command palette (Strg+K): pages, modules, dashboards and analyses of the navigation data, all on
 * the client. Every search word must occur in the label or the detail (site path); hits whose label starts with
 * the search come first, then hits at a word start, then the rest; within a rank pages and modules before
 * dashboards before analyses, then by name.
 */

export type PaletteKind = 'page' | 'module' | 'dashboard' | 'analysis'

export interface PaletteItem {
  /** unique id (DOM id of the option) */
  id: string
  kind: PaletteKind
  label: string
  /** site path or other context shown below the label */
  detail: string | null
  /** route name and params to open */
  route: { name: string; params?: Record<string, number> }
}

export const PALETTE_MAX = 50

const KIND_ORDER: Record<PaletteKind, number> = { page: 0, module: 0, dashboard: 1, analysis: 2 }

function rank(label: string, query: string, locale: string): number {
  const l = label.toLocaleLowerCase(locale)
  if (l.startsWith(query)) return 0
  if (l.split(/[\s\-–/›(]+/).some((word) => word.startsWith(query))) return 1
  return 2
}

/** Matching items, best first (at most `max`). An empty search returns `initial` (e.g. recently opened, modules). */
export function paletteSearch(
  items: readonly PaletteItem[],
  query: string,
  locale: string,
  initial: readonly PaletteItem[] = items,
  max = PALETTE_MAX,
): PaletteItem[] {
  const terms = searchTerms(query, locale)
  if (!terms.length) return initial.slice(0, max)
  const q = query.trim().toLocaleLowerCase(locale)
  return items
    .filter((item) => {
      const haystack = (item.label + '\n' + (item.detail ?? '')).toLocaleLowerCase(locale)
      return terms.every((t) => haystack.includes(t))
    })
    .map((item) => ({ item, r: rank(item.label, q, locale) }))
    .sort((a, b) => a.r - b.r || KIND_ORDER[a.item.kind] - KIND_ORDER[b.item.kind] || a.item.label.localeCompare(b.item.label, locale, { sensitivity: 'base' }))
    .slice(0, max)
    .map((x) => x.item)
}

export type PaletteAction = { type: 'move'; index: number } | { type: 'open'; index: number } | { type: 'close' } | null

/** Keyboard handling of the palette input (combobox): arrows move (wrapping), Enter opens, Esc closes. */
export function paletteKey(key: string, index: number, count: number): PaletteAction {
  switch (key) {
    case 'ArrowDown':
      return count ? { type: 'move', index: index < 0 ? 0 : (index + 1) % count } : null
    case 'ArrowUp':
      return count ? { type: 'move', index: index <= 0 ? count - 1 : index - 1 } : null
    case 'PageDown':
      return count ? { type: 'move', index: count - 1 } : null
    case 'PageUp':
      return count ? { type: 'move', index: 0 } : null
    case 'Enter':
      return count && index >= 0 && index < count ? { type: 'open', index } : null
    case 'Escape':
      return { type: 'close' }
    default:
      return null
  }
}

/** True if the element takes text input (then "/" types a slash instead of opening the search). */
export function isTyping(target: EventTarget | null): boolean {
  if (!target || typeof (target as HTMLElement).tagName !== 'string') return false
  const el = target as HTMLElement
  const tag = el.tagName.toLowerCase()
  if (tag === 'textarea' || tag === 'select') return true
  if (tag === 'input') {
    const type = ((el as HTMLInputElement).type || 'text').toLowerCase()
    return !['button', 'checkbox', 'radio', 'submit', 'reset', 'range', 'color', 'file', 'image'].includes(type)
  }
  return el.isContentEditable || el.getAttribute('contenteditable') === 'true'
}

/** Global shortcut opening the palette: Strg+K / Cmd+K, or "/" when not typing. */
export function isPaletteShortcut(e: Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'metaKey' | 'altKey' | 'shiftKey' | 'target'>): boolean {
  if ((e.ctrlKey || e.metaKey) && !e.altKey && (e.key === 'k' || e.key === 'K')) return true
  return e.key === '/' && !e.ctrlKey && !e.metaKey && !e.altKey && !isTyping(e.target)
}
