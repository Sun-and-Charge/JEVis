/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { DashboardZoom, WidgetConfig } from '../api/dashboard'

export interface Size {
  width: number
  height: number
}

export interface Box {
  left: number
  top: number
  width: number
  height: number
}

const MODES = ['FIT_WIDTH', 'FIT_HEIGHT', 'FIT_SCREEN'] as const
/** stored values of the Control Center */
const STORED: Record<number, DashboardZoom> = { 97: 'FIT_HEIGHT', 98: 'FIT_WIDTH', 99: 'FIT_SCREEN' }

/** Zoom of the configuration: a mode, or a factor; unknown values are native size (1). */
export function normaliseZoom(zoom: unknown): DashboardZoom {
  if (typeof zoom === 'string') {
    if ((MODES as readonly string[]).includes(zoom)) return zoom as DashboardZoom
    const n = Number(zoom)
    return Number.isFinite(n) ? normaliseZoom(n) : 1
  }
  if (typeof zoom === 'number' && Number.isFinite(zoom)) {
    if (STORED[zoom]) return STORED[zoom]
    if (zoom > 0 && zoom <= 20) return zoom
  }
  return 1
}

/** Size of the dashboard: its configured size, enlarged to the widgets outside of it. */
export function contentSize(size: Partial<Size>, widgets: Pick<WidgetConfig, 'x' | 'y' | 'width' | 'height'>[]): Size {
  let width = Math.max(0, Number(size.width) || 0)
  let height = Math.max(0, Number(size.height) || 0)
  for (const w of widgets) {
    width = Math.max(width, (w.x || 0) + (w.width || 0))
    height = Math.max(height, (w.y || 0) + (w.height || 0))
  }
  return { width: width || 1, height: height || 1 }
}

export const MIN_SCALE = 0.05
export const MAX_SCALE = 10

/** Scale factor of the dashboard in the available area (viewport = size of the container). */
export function scaleFactor(zoom: DashboardZoom, content: Size, viewport: Size): number {
  const fx = viewport.width > 0 ? viewport.width / content.width : 1
  const fy = viewport.height > 0 ? viewport.height / content.height : 1
  let f: number
  switch (zoom) {
    case 'FIT_WIDTH':
      f = fx
      break
    case 'FIT_HEIGHT':
      f = fy
      break
    case 'FIT_SCREEN':
      f = Math.min(fx, fy)
      break
    default:
      f = zoom
  }
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, Number.isFinite(f) && f > 0 ? f : 1))
}

/** Steps of the zoom control (multiplied with the scale of the configuration). */
export const USER_ZOOM_STEPS = [0.5, 0.67, 0.75, 0.9, 1, 1.25, 1.5, 2, 3, 4]

export function nextZoomStep(current: number, direction: -1 | 1): number {
  if (direction > 0) return USER_ZOOM_STEPS.find((s) => s > current + 1e-6) ?? current
  return [...USER_ZOOM_STEPS].reverse().find((s) => s < current - 1e-6) ?? current
}

/** Widgets in drawing order: by layer, within a layer in the order of the file (a higher layer is on top). */
export function sortByLayer<T extends Pick<WidgetConfig, 'layer'>>(widgets: T[]): T[] {
  return widgets
    .map((widget, index) => ({ widget, index }))
    .sort((a, b) => (a.widget.layer || 0) - (b.widget.layer || 0) || a.index - b.index)
    .map((e) => e.widget)
}

const round = (n: number) => Math.round(n * 100) / 100

/** Position and size of a widget in CSS pixels. */
export function widgetBox(widget: Pick<WidgetConfig, 'x' | 'y' | 'width' | 'height'>, scale: number): Box {
  return {
    left: round((widget.x || 0) * scale),
    top: round((widget.y || 0) * scale),
    width: round(Math.max(0, widget.width || 0) * scale),
    height: round(Math.max(0, widget.height || 0) * scale),
  }
}
