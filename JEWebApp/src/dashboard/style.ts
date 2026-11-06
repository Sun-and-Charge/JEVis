/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { DashboardBackground, WidgetStyle } from '../api/dashboard'

/**
 * A color of the configuration if it is a plain CSS color (#hex, rgb/rgba/hsl/hsla with numbers, a color name),
 * otherwise null. Keeps e.g. `url(...)` out of the styles.
 */
export function cssColor(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const v = value.trim()
  if (/^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(v)) return v
  if (/^(?:rgb|rgba|hsl|hsla)\(\s*[-\d.%\s,/]+\)$/i.test(v)) return v
  if (/^[a-z]{3,30}$/i.test(v)) return v
  return null
}

/** Flex alignment of a JavaFX `Pos` (title position / alignment of the text). */
export interface Alignment {
  justifyContent: 'flex-start' | 'center' | 'flex-end'
  alignItems: 'flex-start' | 'center' | 'flex-end'
  textAlign: 'left' | 'center' | 'right'
}

export function alignment(pos: string | null | undefined): Alignment {
  const p = (pos || '').toUpperCase()
  if (p === 'LEFT') return { justifyContent: 'flex-start', alignItems: 'flex-start', textAlign: 'left' }
  if (p === 'RIGHT') return { justifyContent: 'flex-end', alignItems: 'flex-start', textAlign: 'right' }
  const [vertical, horizontal] = p === 'CENTER' ? ['CENTER', 'CENTER'] : p.split('_')
  const v = vertical === 'TOP' ? 'flex-start' : vertical === 'BOTTOM' ? 'flex-end' : 'center'
  const h = horizontal === 'LEFT' ? 'flex-start' : horizontal === 'RIGHT' ? 'flex-end' : 'center'
  return { justifyContent: h, alignItems: p ? v : 'center', textAlign: h === 'flex-start' ? 'left' : h === 'flex-end' ? 'right' : 'center' }
}

const WEIGHTS: Record<string, number> = {
  THIN: 100,
  EXTRA_LIGHT: 200,
  LIGHT: 300,
  NORMAL: 400,
  MEDIUM: 500,
  SEMI_BOLD: 600,
  BOLD: 700,
  EXTRA_BOLD: 800,
  BLACK: 900,
}

export function fontWeight(weight: string | null | undefined): number {
  return WEIGHTS[(weight || '').toUpperCase()] ?? 400
}

/** Default font size of the Control Center widgets. */
export const DEFAULT_FONT_SIZE = 13

/** CSS of the frame of a widget (scaled like its position). */
export function frameStyle(style: Partial<WidgetStyle> | null | undefined, scale: number): Record<string, string> {
  const s = style ?? {}
  const css: Record<string, string> = {}
  const background = cssColor(s.background)
  if (background) css.background = background
  const color = cssColor(s.fontColor)
  if (color) css.color = color
  const size = typeof s.fontSize === 'number' && s.fontSize > 0 ? s.fontSize : DEFAULT_FONT_SIZE
  css.fontSize = `${Math.round(size * scale * 100) / 100}px`
  css.fontWeight = String(fontWeight(s.fontWeight))
  if (s.italic) css.fontStyle = 'italic'
  if (s.underline) css.textDecoration = 'underline'
  const border = typeof s.borderSize === 'number' && s.borderSize > 0 ? s.borderSize : 0
  if (border) css.border = `${Math.max(1, Math.round(border * scale * 100) / 100)}px solid ${cssColor(s.borderColor) ?? 'currentColor'}`
  if (typeof s.cornerRadius === 'number' && s.cornerRadius > 0) css.borderRadius = `${Math.round(s.cornerRadius * scale * 100) / 100}px`
  if (s.shadow) css.boxShadow = '0 2px 6px rgba(0, 0, 0, 0.25)'
  return css
}

/** CSS of the dashboard area (color and image). `imageUrl`: blob URL, `imageSize`: native size of the image. */
export function backgroundStyle(
  background: DashboardBackground | null | undefined,
  imageUrl: string | null,
  imageSize: { width: number; height: number } | null,
  scale: number,
): Record<string, string> {
  const css: Record<string, string> = {}
  const color = cssColor(background?.color)
  if (color) css.backgroundColor = color
  if (imageUrl) {
    css.backgroundImage = `url("${imageUrl}")`
    const mode = (background?.mode || 'DEFAULT').toUpperCase()
    css.backgroundPosition = 'left top'
    if (mode === 'STRETCH') {
      css.backgroundSize = '100% 100%'
      css.backgroundRepeat = 'no-repeat'
    } else {
      css.backgroundRepeat = mode === 'REPEAT' ? 'repeat' : 'no-repeat'
      if (imageSize) css.backgroundSize = `${Math.round(imageSize.width * scale)}px ${Math.round(imageSize.height * scale)}px`
    }
  }
  return css
}

const hex2 = (n: number) => Math.round(Math.min(255, Math.max(0, n))).toString(16).padStart(2, '0')

/** Parses #rgb, #rrggbb, #rrggbbaa and rgb()/rgba() into [r, g, b, a]. */
export function parseColor(color: string | null | undefined): [number, number, number, number] | null {
  if (!color) return null
  const c = color.trim()
  let m = /^#([0-9a-f]{3})$/i.exec(c)
  if (m) return [...m[1].split('').map((d) => parseInt(d + d, 16)), 1] as [number, number, number, number]
  m = /^#([0-9a-f]{6})([0-9a-f]{2})?$/i.exec(c)
  if (m) {
    const n = parseInt(m[1], 16)
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255, m[2] ? parseInt(m[2], 16) / 255 : 1]
  }
  m = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/i.exec(c)
  if (m) return [+m[1], +m[2], +m[3], m[4] !== undefined ? +m[4] : 1]
  return null
}

/** Color between `from` and `to` (Shape widget: by the value between its minimum and maximum). */
export function mixColor(from: string | null | undefined, to: string | null | undefined, fraction: number): string | null {
  const a = parseColor(from)
  const b = parseColor(to)
  if (!a || !b) return cssColor(from) ?? cssColor(to)
  const f = Math.min(1, Math.max(0, Number.isFinite(fraction) ? fraction : 0))
  const mix = (i: number) => a[i] + (b[i] - a[i]) * f
  const alpha = mix(3)
  return alpha >= 1 ? `#${hex2(mix(0))}${hex2(mix(1))}${hex2(mix(2))}` : `rgba(${Math.round(mix(0))},${Math.round(mix(1))},${Math.round(mix(2))},${Math.round(alpha * 1000) / 1000})`
}
