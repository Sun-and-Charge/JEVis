<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { bandRect, clickSelection, dragPositions, HANDLES, rectOf, resizeRect, startsOf, widgetsInRect, type Handle, type Rect, type Starts } from '../../../dashboard/edit/canvas'
import type { RawWidget } from '../../../dashboard/edit/raw'
import type { DashboardEditing } from '../../../dashboard/edit/useDashboardEditing'
import { sortByLayer } from '../../../dashboard/layout'
import { useTypeLabel } from './typeLabel'

/*
 * Edit layer above the drawn dashboard (E4): one box per widget of the source, in drawing order. Click selects,
 * Ctrl / Shift + click adds or removes, dragging on the empty canvas draws a rubber band; dragging a box moves the
 * selection, the 8 handles of a single selected widget resize it. Positions snap to the grid of the dashboard (Shift:
 * a quarter of it, Alt: no snapping). Keyboard: Tab to a widget, Space selects (Ctrl+Space adds), arrow keys move by
 * one grid step (Shift: 1 px), Entf deletes, Strg+D duplicates, Strg+Z / Strg+Y undo and redo, Strg+A selects all,
 * Esc clears the selection.
 */

const props = defineProps<{ ed: DashboardEditing; scale: number }>()
const { t } = useI18n()
const typeLabel = useTypeLabel()

const root = ref<HTMLElement | null>(null)
const source = computed(() => props.ed.source.value)
const ordered = computed(() => sortByLayer((source.value?.Widget ?? []).map((w) => ({ w, layer: w.layer ?? 0 }))).map((e) => e.w))
const selection = computed(() => props.ed.selection.value)
const single = computed(() => (selection.value.size === 1 ? ordered.value.find((w) => selection.value.has(w.uuid)) ?? null : null))

const px = (n: number) => `${Math.round(n * props.scale * 100) / 100}px`
function boxStyle(w: RawWidget, i: number) {
  const r = rectOf(w)
  return { left: px(r.x), top: px(r.y), width: px(r.width), height: px(r.height), zIndex: String(i + 1) }
}

const showGrid = computed(() => source.value?.['Show Grid'] === true)
const gridStyle = computed(() => {
  const g = props.ed.grid()
  return { backgroundSize: `${g.x * props.scale}px ${g.y * props.scale}px` }
})

function label(w: RawWidget): string {
  const r = rectOf(w)
  const title = typeof w.title === 'string' && w.title.trim() ? w.title.trim() : t('dashboardEdit.canvas.untitled')
  return t('dashboardEdit.canvas.widgetLabel', { type: typeLabel(w.WidgetType), title, x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) })
}

/* ---------------------------------------------------------------- pointer */

type Drag =
  | { kind: 'move'; id: number; lead: number; x: number; y: number; starts: Starts; moved: boolean; toggled: boolean }
  | { kind: 'resize'; id: number; uuid: number; handle: Handle; x: number; y: number; start: Rect; moved: boolean }
  | { kind: 'band'; id: number; from: { x: number; y: number }; to: { x: number; y: number }; add: boolean; base: Set<number> }
const drag = ref<Drag | null>(null)
/** pointer travel (screen px) below which a press is a click */
const CLICK_SLOP = 3

const isToggle = (e: PointerEvent | KeyboardEvent) => e.ctrlKey || e.metaKey || e.shiftKey
const widgetOf = (uuid: number) => source.value?.Widget.find((w) => w.uuid === uuid)

function capture(e: PointerEvent) {
  try {
    ;(e.currentTarget as HTMLElement)?.setPointerCapture?.(e.pointerId)
  } catch {
    /* not supported (tests) */
  }
}

function canvasPoint(e: PointerEvent) {
  const rect = root.value?.getBoundingClientRect()
  return { x: (e.clientX - (rect?.left ?? 0)) / props.scale, y: (e.clientY - (rect?.top ?? 0)) / props.scale }
}

function onBoxDown(e: PointerEvent, w: RawWidget) {
  if (e.button !== 0) return
  e.preventDefault()
  ;(e.currentTarget as HTMLElement).focus?.({ preventScroll: true })
  const toggle = isToggle(e)
  let next = props.ed.selection.value
  if (toggle) next = clickSelection(next, w.uuid, true)
  else if (!next.has(w.uuid)) next = new Set([w.uuid])
  props.ed.setSelection(next)
  if (!next.has(w.uuid)) return
  drag.value = { kind: 'move', id: e.pointerId, lead: w.uuid, x: e.clientX, y: e.clientY, starts: startsOf(source.value?.Widget ?? [], next), moved: false, toggled: toggle }
  capture(e)
}

function onHandleDown(e: PointerEvent, w: RawWidget, handle: Handle) {
  if (e.button !== 0) return
  e.preventDefault()
  e.stopPropagation()
  drag.value = { kind: 'resize', id: e.pointerId, uuid: w.uuid, handle, x: e.clientX, y: e.clientY, start: rectOf(w), moved: false }
  capture(e)
}

function onBackgroundDown(e: PointerEvent) {
  if (e.button !== 0 || e.target !== e.currentTarget) return
  e.preventDefault()
  root.value?.focus({ preventScroll: true })
  const add = isToggle(e)
  const base = add ? new Set(props.ed.selection.value) : new Set<number>()
  if (!add) props.ed.setSelection([])
  const p = canvasPoint(e)
  drag.value = { kind: 'band', id: e.pointerId, from: p, to: p, add, base }
  capture(e)
}

function onMove(e: PointerEvent) {
  const d = drag.value
  if (!d || e.pointerId !== d.id) return
  if (d.kind === 'band') {
    d.to = canvasPoint(e)
    const hits = widgetsInRect(source.value?.Widget ?? [], bandRect(d.from, d.to))
    props.ed.setSelection(d.add ? new Set([...d.base, ...hits]) : hits)
    return
  }
  const sx = e.clientX - d.x
  const sy = e.clientY - d.y
  if (!d.moved) {
    if (Math.abs(sx) < CLICK_SLOP && Math.abs(sy) < CLICK_SLOP) return
    d.moved = true
    props.ed.beginGesture()
  }
  const grid = e.altKey ? null : props.ed.grid(e.shiftKey)
  const dx = sx / props.scale
  const dy = sy / props.scale
  if (d.kind === 'move') {
    for (const [uuid, pos] of dragPositions(d.starts, d.lead, dx, dy, grid)) {
      const w = widgetOf(uuid)
      if (w) {
        w.xPos = pos.x
        w.yPos = pos.y
      }
    }
  } else {
    const w = widgetOf(d.uuid)
    if (!w) return
    const r = resizeRect(d.start, d.handle, dx, dy, grid)
    w.xPos = r.x
    w.yPos = r.y
    w.width = r.width
    w.height = r.height
  }
}

function onUp(e: PointerEvent) {
  const d = drag.value
  if (!d || e.pointerId !== d.id) return
  drag.value = null
  if (d.kind === 'band') return
  if (d.moved) props.ed.endGesture()
  else if (d.kind === 'move' && !d.toggled && props.ed.selection.value.size > 1) props.ed.setSelection([d.lead])
}

const band = computed(() => (drag.value?.kind === 'band' ? bandRect(drag.value.from, drag.value.to) : null))

/* ---------------------------------------------------------------- keyboard */

function focusedUuid(e: KeyboardEvent): number | null {
  const el = (e.target as HTMLElement | null)?.closest?.('[data-edit-uuid]') as HTMLElement | null
  return el ? Number(el.dataset.editUuid) : null
}

function onKey(e: KeyboardEvent) {
  const ctrl = e.ctrlKey || e.metaKey
  const key = e.key
  const focused = focusedUuid(e)
  if (ctrl && (key === 'z' || key === 'Z') && !e.shiftKey) {
    e.preventDefault()
    props.ed.undo()
  } else if (ctrl && (key === 'y' || key === 'Y' || ((key === 'z' || key === 'Z') && e.shiftKey))) {
    e.preventDefault()
    props.ed.redo()
  } else if (ctrl && (key === 'd' || key === 'D')) {
    e.preventDefault()
    props.ed.duplicateSelected()
  } else if (ctrl && (key === 'a' || key === 'A')) {
    e.preventDefault()
    props.ed.selectAll()
  } else if (key === 'Delete' || key === 'Backspace') {
    if (!props.ed.selection.value.size) return
    e.preventDefault()
    props.ed.deleteSelected()
    root.value?.focus({ preventScroll: true })
  } else if (key === 'Escape') {
    if (props.ed.selection.value.size) {
      e.preventDefault()
      props.ed.setSelection([])
    }
  } else if ((key === ' ' || key === 'Enter') && focused !== null) {
    e.preventDefault()
    props.ed.setSelection(isToggle(e) ? clickSelection(props.ed.selection.value, focused, true) : [focused])
  } else if (key.startsWith('Arrow')) {
    if (!props.ed.selection.value.size && focused !== null) props.ed.setSelection([focused])
    if (props.ed.nudgeSelection(key, e.shiftKey)) e.preventDefault()
  }
}

defineExpose({ focus: () => root.value?.focus({ preventScroll: true }) })
</script>

<template>
  <div
    ref="root"
    class="dbe-overlay"
    role="listbox"
    aria-multiselectable="true"
    :aria-label="t('dashboardEdit.canvas.label')"
    :aria-describedby="'dbe-canvas-help'"
    tabindex="0"
    @pointerdown="onBackgroundDown"
    @pointermove="onMove"
    @pointerup="onUp"
    @pointercancel="onUp"
    @keydown="onKey"
  >
    <div v-if="showGrid" class="dbe-grid" :style="gridStyle" aria-hidden="true" />
    <div
      v-for="(w, i) in ordered"
      :key="w.uuid"
      class="dbe-box"
      :class="{ 'is-selected': selection.has(w.uuid) }"
      :style="boxStyle(w, i)"
      role="option"
      :aria-selected="selection.has(w.uuid)"
      :aria-label="label(w)"
      :data-edit-uuid="w.uuid"
      tabindex="0"
      @pointerdown="onBoxDown($event, w)"
    >
      <template v-if="single && single.uuid === w.uuid">
        <span
          v-for="h in HANDLES"
          :key="h"
          class="dbe-handle"
          :class="'is-' + h"
          :data-handle="h"
          aria-hidden="true"
          @pointerdown="onHandleDown($event, w, h)"
        />
      </template>
    </div>
    <div
      v-if="band"
      class="dbe-band"
      :style="{ left: px(band.x), top: px(band.y), width: px(band.width), height: px(band.height) }"
      aria-hidden="true"
    />
    <p id="dbe-canvas-help" class="visually-hidden">{{ t('dashboardEdit.canvas.help') }}</p>
  </div>
</template>
