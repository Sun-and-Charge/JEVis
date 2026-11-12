/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import {
  bandRect,
  changeLayer,
  clickSelection,
  deleteWidgets,
  dragPositions,
  duplicateWidgets,
  gridOf,
  nudge,
  placeInView,
  resizeRect,
  snap,
  startsOf,
  widgetsInRect,
} from '../src/dashboard/edit/canvas'
import { COALESCE_MS, HISTORY_STEPS, SourceHistory } from '../src/dashboard/edit/history'
import { nextUuid, widgetRows } from '../src/dashboard/edit/raw'
import {
  addRows,
  commonValue,
  editableSource,
  newRawRow,
  overlaySource,
  previewKey,
  setAlphaAll,
  setColorAll,
  setOwnTimeFrame,
  timeFrameChoices,
  titlePosition,
  validateSource,
  viewStyle,
  viewZoom,
} from '../src/dashboard/edit/source'
import { dashboard, widget } from './fixtures/dashboard'
import { rawRow, rawSource, rawWidget, withRows } from './fixtures/dashboardEdit'

/* Dashboard editor (E4): canvas maths, undo / redo, source ↔ view mapping. */

const grid = { x: 25, y: 25 }

describe('grid and snapping', () => {
  it('uses the grid interval of the dashboard, a quarter with Shift', () => {
    expect(gridOf(rawSource([]))).toEqual({ x: 25, y: 25 })
    expect(gridOf(rawSource([], { 'X Axis Grid Interval': 40, 'Y Axis Grid Interval': 20 }), true)).toEqual({ x: 10, y: 5 })
    expect(gridOf({})).toEqual({ x: 25, y: 25 })
    expect(gridOf({ 'X Axis Grid Interval': 2, 'Y Axis Grid Interval': 2 }, true)).toEqual({ x: 1, y: 1 })
  })

  it('snaps to the nearest grid line', () => {
    expect(snap(37, 25)).toBe(25)
    expect(snap(38, 25)).toBe(50)
    expect(snap(13, 6.25)).toBe(12.5)
    expect(snap(13.333, 0)).toBe(13.33)
  })
})

describe('moving', () => {
  it('snaps the dragged widget and keeps the distance of the others', () => {
    const ws = [rawWidget(1, 'Value', { xPos: 10, yPos: 20 }), rawWidget(2, 'Value', { xPos: 110, yPos: 220 })]
    const starts = startsOf(ws, new Set([1, 2]))
    const moved = dragPositions(starts, 1, 32, 3, grid)
    expect(moved.get(1)).toEqual({ x: 50, y: 25 })
    expect(moved.get(2)).toEqual({ x: 150, y: 225 })
    // without grid (Alt) exact
    expect(dragPositions(starts, 1, 3.5, 0, null).get(1)).toEqual({ x: 13.5, y: 20 })
  })

  it('keeps the group inside the canvas (x, y ≥ 0)', () => {
    const starts = startsOf([rawWidget(1, 'Value', { xPos: 10, yPos: 20 }), rawWidget(2, 'Value', { xPos: 50, yPos: 30 })], new Set([1, 2]))
    const moved = dragPositions(starts, 2, -200, -200, grid)
    expect(moved.get(1)).toEqual({ x: 0, y: 0 })
    expect(moved.get(2)).toEqual({ x: 40, y: 10 })
  })

  it('arrow keys move by one grid step, with Shift by 1 px, only the selection', () => {
    const ws = [rawWidget(1, 'Value', { xPos: 10, yPos: 20 }), rawWidget(2, 'Value', { xPos: 0, yPos: 0 })]
    expect(nudge(ws, new Set([1]), 'ArrowRight', false, grid)).toBe(true)
    expect([ws[0].xPos, ws[0].yPos]).toEqual([25, 20])
    nudge(ws, new Set([1]), 'ArrowDown', true, grid)
    expect([ws[0].xPos, ws[0].yPos]).toEqual([25, 21])
    nudge(ws, new Set([1]), 'ArrowLeft', true, grid)
    expect(ws[0].xPos).toBe(24)
    expect([ws[1].xPos, ws[1].yPos]).toEqual([0, 0])
    // not out of the canvas, nothing to do
    expect(nudge(ws, new Set([2]), 'ArrowUp', false, grid)).toBe(false)
    expect(nudge(ws, new Set([1]), 'Enter', false, grid)).toBe(false)
    expect(nudge(ws, new Set(), 'ArrowUp', false, grid)).toBe(false)
  })
})

describe('resizing with the 8 handles', () => {
  const start = { x: 50, y: 50, width: 200, height: 100 }

  it('moves only the dragged edges, snapped', () => {
    expect(resizeRect(start, 'se', 30, 12, grid)).toEqual({ x: 50, y: 50, width: 225, height: 100 })
    expect(resizeRect(start, 'se', 38, 13, grid)).toEqual({ x: 50, y: 50, width: 250, height: 125 })
    expect(resizeRect(start, 'e', 30, 99, grid)).toEqual({ x: 50, y: 50, width: 225, height: 100 })
    expect(resizeRect(start, 's', 99, 30, grid)).toEqual({ x: 50, y: 50, width: 200, height: 125 })
    expect(resizeRect(start, 'nw', -26, -24, grid)).toEqual({ x: 25, y: 25, width: 225, height: 125 })
    expect(resizeRect(start, 'n', 0, 30, grid)).toEqual({ x: 50, y: 75, width: 200, height: 75 })
    expect(resizeRect(start, 'w', 20, 0, grid)).toEqual({ x: 75, y: 50, width: 175, height: 100 })
    expect(resizeRect(start, 'ne', 10, -10, null)).toEqual({ x: 50, y: 40, width: 210, height: 110 })
    expect(resizeRect(start, 'sw', -10, 10, null)).toEqual({ x: 40, y: 50, width: 210, height: 110 })
  })

  it('keeps at least 10 px and stays inside the canvas', () => {
    expect(resizeRect(start, 'e', -500, 0, grid).width).toBe(10)
    expect(resizeRect(start, 'w', 500, 0, grid)).toMatchObject({ x: 240, width: 10 })
    expect(resizeRect(start, 'nw', -500, -500, grid)).toMatchObject({ x: 0, y: 0, width: 250, height: 150 })
  })
})

describe('selection', () => {
  it('click replaces, Ctrl / Shift toggles, a click into a group keeps it (for dragging)', () => {
    expect([...clickSelection(new Set([1]), 2, false)]).toEqual([2])
    expect([...clickSelection(new Set([1]), 2, true)]).toEqual([1, 2])
    expect([...clickSelection(new Set([1, 2]), 2, true)]).toEqual([1])
    expect([...clickSelection(new Set([1, 2]), 2, false)]).toEqual([1, 2])
  })

  it('the rubber band selects the widgets it touches', () => {
    const ws = [rawWidget(1, 'Value', { xPos: 0, yPos: 0, width: 50, height: 50 }), rawWidget(2, 'Value', { xPos: 100, yPos: 100, width: 50, height: 50 }), rawWidget(3, 'Value', { xPos: 300, yPos: 0, width: 50, height: 50 })]
    expect(widgetsInRect(ws, bandRect({ x: 160, y: 120 }, { x: 40, y: 40 }))).toEqual([1, 2])
    expect(widgetsInRect(ws, bandRect({ x: 60, y: 60 }, { x: 90, y: 90 }))).toEqual([])
  })
})

describe('layers, duplicate, delete', () => {
  it('layer up / down (not below 0)', () => {
    const ws = [rawWidget(1, 'Value', { layer: 0 }), rawWidget(2, 'Value', { layer: 3 })]
    expect(changeLayer(ws, new Set([1, 2]), 1)).toBe(true)
    expect(ws.map((w) => w.layer)).toEqual([1, 4])
    changeLayer(ws, new Set([1, 2]), -1)
    changeLayer(ws, new Set([1, 2]), -1)
    expect(ws.map((w) => w.layer)).toEqual([0, 2])
    expect(changeLayer(ws, new Set([1]), -1)).toBe(false)
  })

  it('Strg+D copies with uuid max + 1, title " #2", one grid step offset and every key', () => {
    const s = rawSource([withRows(rawWidget(4, 'Value', { custom: { a: 1 } }), [rawRow(2001)]), rawWidget(9, 'Title')])
    const added = duplicateWidgets(s, new Set([4, 9]), grid)
    expect(added).toEqual([10, 11])
    const copy = s.Widget[2]
    expect(copy).toMatchObject({ uuid: 10, title: 'Widget 4 #2', xPos: 35, yPos: 45, custom: { a: 1 } })
    expect(widgetRows(copy)[0].id).toBe(2001)
    // a deep copy
    widgetRows(copy)[0].name = 'changed'
    expect(widgetRows(s.Widget[0])[0].name).toBe('Musterwerk 2001')
    expect(s.Widget[3]).toMatchObject({ uuid: 11, title: 'Widget 9 #2' })
    expect(nextUuid(s)).toBe(12)
  })

  it('delete clears the references of the other widgets', () => {
    const s = rawSource([
      rawWidget(1, 'Value'),
      rawWidget(2, 'Value', { limit: { source: 1, upperLimit: true }, percent: { source: 1 } }),
      rawWidget(3, 'Time Frame', { 'Time Frame': { format: 'x', selectedWidget: { id: 1, start: 'NONE' } } }),
      rawWidget(4, 'Value', { limit: { source: 3 } }),
    ])
    expect(deleteWidgets(s, new Set([1]))).toBe(1)
    expect(s.Widget.map((w) => w.uuid)).toEqual([2, 3, 4])
    expect(s.Widget[0].limit).toEqual({ source: -1, upperLimit: true })
    expect(s.Widget[0].percent).toEqual({ source: -1 })
    expect((s.Widget[1]['Time Frame'] as { selectedWidget: { id: number } }).selectedWidget.id).toBe(-1)
    expect(s.Widget[2].limit).toEqual({ source: 3 })
  })

  it('new widgets go to the visible area on the grid', () => {
    expect(placeInView({ left: 0, top: 0 }, grid)).toEqual({ x: 25, y: 25 })
    expect(placeInView({ left: 310, top: -20 }, grid)).toEqual({ x: 325, y: 25 })
  })
})

describe('undo / redo', () => {
  it('keeps 30 steps of the whole source', () => {
    const h = new SourceHistory('s0')
    for (let i = 1; i <= 40; i++) h.record('s' + i, null, i * 10_000)
    expect(h.undoCount).toBe(HISTORY_STEPS)
    let last = ''
    while (h.canUndo) last = h.undo()!
    expect(last).toBe('s10')
    expect(h.undo()).toBeNull()
    expect(h.redo()).toBe('s11')
  })

  it('a new change drops the redo steps; unchanged states are no step', () => {
    const h = new SourceHistory('a')
    h.record('b')
    h.record('c')
    expect(h.undo()).toBe('b')
    expect(h.canRedo).toBe(true)
    expect(h.record('b')).toBe(false)
    h.record('d')
    expect(h.canRedo).toBe(false)
    expect(h.undo()).toBe('b')
    expect(h.undo()).toBe('a')
  })

  it('merges changes of the same group close together (typing, holding a key)', () => {
    const h = new SourceHistory('a')
    h.record('ab', 'panel', 1000)
    h.record('abc', 'panel', 1000 + COALESCE_MS - 1)
    h.record('abcd', 'panel', 1000 + 3 * COALESCE_MS)
    h.record('x', null, 1000 + 3 * COALESCE_MS + 1)
    expect(h.undo()).toBe('abcd')
    expect(h.undo()).toBe('abc')
    expect(h.undo()).toBe('a')
  })
})

describe('source → canvas', () => {
  it('converts colours, fonts and title positions of the source', () => {
    const w = rawWidget(1, 'Value', { 'Background Color': '0x11223380', 'Font Color': '#aabbcc', fontPosture: 'ITALIC', fontUnderlined: true, 'Title Position': 'left' })
    expect(viewStyle(w)).toMatchObject({ background: 'rgba(17,34,51,0.502)', fontColor: '#aabbcc', italic: true, underline: true, titlePosition: 'CENTER_LEFT', borderColor: '#d0d4da' })
    expect(titlePosition('right')).toBe('CENTER_RIGHT')
    expect(titlePosition(undefined)).toBe('CENTER')
    expect(viewZoom(97)).toBe('FIT_HEIGHT')
    expect(viewZoom(1.5)).toBe(1.5)
  })

  it('draws positions, sizes and style of the source over the last preview; new widgets as frames', () => {
    const base = dashboard([widget('1', 'Value', { config: { dimIncompletePeriod: true } }), widget('2', 'Title')])
    const s = rawSource([rawWidget(1, 'Value', { xPos: 300, yPos: 40, width: 120, layer: 2, title: 'Neu' }), rawWidget(5, 'Gauge')], { 'Dash Board Color': '0x102030ff', width: 1200, backgroundMode: 'stretch' })
    const shown = overlaySource(base, s)
    expect(shown.widgets.map((w) => w.uuid)).toEqual(['1', '5'])
    expect(shown.widgets[0]).toMatchObject({ x: 300, y: 40, width: 120, layer: 2, title: 'Neu', config: { dimIncompletePeriod: true } })
    expect(shown.widgets[1]).toMatchObject({ type: 'Gauge', x: 10, y: 20, rows: [], config: {} })
    expect(shown.background).toEqual({ color: '#102030', image: false, mode: 'STRETCH' })
    expect(shown.width).toBe(1200)
    expect(shown.zoom).toBe('FIT_WIDTH')
  })

  it('the preview key ignores moving, resizing and layers', () => {
    const s = rawSource([rawWidget(1, 'Value')])
    const key = previewKey(s)
    Object.assign(s.Widget[0], { xPos: 99, yPos: 99, width: 50, height: 50, layer: 4 })
    expect(previewKey(s)).toBe(key)
    s.Widget[0].title = 'other'
    expect(previewKey(s)).not.toBe(key)
  })

  it('makes an editable copy with numeric uuids', () => {
    const loaded = rawSource([rawWidget('3' as unknown as number, 'Value')])
    const copy = editableSource(loaded)
    expect(copy.Widget[0].uuid).toBe(3)
    expect(copy).not.toBe(loaded)
    expect(editableSource(null)).toEqual({ Widget: [] })
  })
})

describe('panel ↔ source', () => {
  it('common values of several widgets', () => {
    const ws = [rawWidget(1, 'Value', { fontSize: 13 }), rawWidget(2, 'Value', { fontSize: 13, title: 'B' })]
    expect(commonValue(ws, (w) => w.fontSize)).toEqual({ value: 13, mixed: false })
    expect(commonValue(ws, (w) => w.title)).toEqual({ value: undefined, mixed: true })
    expect(commonValue([], (w) => w.title)).toEqual({ value: undefined, mixed: false })
  })

  it('colours are stored as 0xRRGGBBAA, the alpha of each widget is kept', () => {
    const ws = [rawWidget(1, 'Value', { 'Background Color': '0xffffff80' }), rawWidget(2, 'Value', { 'Background Color': '#ffffff' })]
    setColorAll(ws, 'Background Color', '#1A2A45')
    expect(ws.map((w) => w['Background Color'])).toEqual(['0x1a2a4580', '0x1a2a45ff'])
    setAlphaAll(ws, 'Background Color', 50)
    expect(ws.map((w) => w['Background Color'])).toEqual(['0x1a2a4580', '0x1a2a4580'])
  })

  it('own time frame is the forcedInterval of the data handler', () => {
    const w = withRows(rawWidget(1, 'Value'), [rawRow(2001)])
    setOwnTimeFrame(w, 'LASTVALUE')
    expect(w.dataHandler?.forcedInterval).toBe('LASTVALUE')
    const empty = rawWidget(2, 'Value')
    setOwnTimeFrame(empty, '')
    expect(empty.dataHandler).toBeUndefined()
    expect(timeFrameChoices([{ id: '2005', name: 'Period X', custom: true }]).map((c) => c.id)).toEqual(['P1D', 'P1W', 'P1M', 'P1Y', 'P3Y', 'P5Y', 'P10Y', 'PT24H', 'P7D', 'P30D', 'P365D', 'PALL', 'LASTVALUE', '2005'])
  })

  it('adds rows from the picker with the CC defaults incl. aggregation and manipulation', () => {
    const w = rawWidget(1, 'Value')
    const added = addRows(w, [
      { node: { id: 3001, name: 'Musterwerk Gas' }, attribute: 'Value' },
      { node: { id: 3001, name: 'Musterwerk Gas' }, attribute: 'Value' },
    ])
    expect(added).toHaveLength(1)
    expect(w.dataHandler).toMatchObject({ type: 'AnalysisHandler', chartModels: [{ chartData: [{ id: 3001, attributeString: 'Value', aggregationPeriod: 'NONE', manipulationMode: 'NONE', color: '0x00538aff', decimalDigits: -1 }] }] })
    expect(newRawRow({ id: 1, name: 'x' }, 'Value', 1).color).toBe('0xe0a100ff')
  })

  it('checks the ranges of the server', () => {
    const s = rawSource([rawWidget(1, 'Value', { width: 5, fontSize: 0, decimals: 1.5, borderColor: 'red' })], { width: 50, 'Zoom Factor': 0, 'Update Rate': 0 })
    const paths = validateSource(s, '').map((p) => p.path)
    expect(paths).toEqual(expect.arrayContaining(['name', 'dashboard.width', 'dashboard.zoom', 'dashboard.updateRate', 'widget.1.width', 'widget.1.fontSize', 'widget.1.decimals', 'widget.1.borderColor']))
    expect(validateSource(rawSource([rawWidget(1, 'Value')]), 'Übersicht')).toEqual([])
    expect(validateSource(rawSource([], { 'Zoom Factor': 99 }), null)).toEqual([])
  })

  it('accepts values outside the ranges as loaded (stored by the Control Center), not when changed to them', () => {
    const loaded = rawSource([rawWidget(1, 'Title', { width: 4, height: 2 })])
    const same = rawSource([rawWidget(1, 'Title', { width: 4, height: 2 })])
    expect(validateSource(same, null, loaded)).toEqual([])
    const changed = rawSource([rawWidget(1, 'Title', { width: 5, height: 2 })])
    expect(validateSource(changed, null, loaded).map((p) => p.path)).toEqual(['widget.1.width'])
    // a new widget with such a value is refused
    const added = rawSource([rawWidget(1, 'Title', { width: 4, height: 2 }), rawWidget(2, 'Title', { width: 4 })])
    expect(validateSource(added, null, loaded).map((p) => p.path)).toEqual(['widget.2.width'])
  })
})
