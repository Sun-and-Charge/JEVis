/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { reactive, type Component } from 'vue'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n } from '../src/i18n'
import de from '../src/locales/dashboardTypes.de.json'
import en from '../src/locales/dashboardTypes.en.json'
import type { RawDashboard, RawWidget } from '../src/dashboard/edit/raw'
import { typeEditorFor } from '../src/dashboard/edit/typeEditors'
import { createWidget } from '../src/dashboard/edit/widgetDefaults'
import { useAuthStore } from '../src/stores/auth'
import { useNavigationStore } from '../src/stores/navigation'

/* The per-type settings editors of the dashboard editor (E4) on a reactive source; neutral data ("Musterwerk"). */

type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []

beforeAll(() => {
  i18n.global.mergeLocaleMessage('de', { dashboardEdit: { types: de } })
  i18n.global.mergeLocaleMessage('en', { dashboardEdit: { types: en } })
  i18n.global.locale.value = 'de'
})

let http: { get: Fn; postForm: Fn; getBlob: Fn }

beforeEach(() => {
  setActivePinia(createPinia())
  http = {
    get: vi.fn(async (path: string) => {
      throw new Error('GET ' + path)
    }),
    postForm: vi.fn(),
    getBlob: vi.fn(async () => new Blob(['x'])),
  }
  Object.assign(useAuthStore().http, http)
})

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
})

function withRows(w: RawWidget, ids: number[]): RawWidget {
  w.dataHandler!.chartModels![0].chartData = ids.map((id) => ({ id, attributeString: 'Value', name: 'Zähler ' + id }))
  return w
}

function setup(widgets: RawWidget[], index = 0, dashboardId: number | null = 5) {
  const source = reactive<RawDashboard>({ Widget: widgets })
  const widget = source.Widget[index]
  const editor = typeEditorFor(widget.WidgetType) as Component
  const w = mount(editor, { props: { widget, source, dashboardId }, global: { plugins: [i18n] }, attachTo: document.body })
  mounted.push(w)
  return { w, widget, source }
}

/** The control of a field by its label text (label[for] or a wrapping label). */
function field<T extends Element = HTMLInputElement>(w: VueWrapper, label: string): DOMWrapper<T> {
  const l = w.findAll('label').find((x) => x.text().trim() === label)
  if (!l) throw new Error('no field ' + label)
  const id = l.attributes('for')
  return id ? w.find<T>(`[id="${id}"]`) : l.find<T>('input')
}

async function type(input: DOMWrapper<HTMLInputElement>, value: string) {
  input.element.value = value
  await input.trigger('change')
}

describe('Gauge', () => {
  it('writes the checkboxes and min/max and creates the block only on change', async () => {
    const g = createWidget('Gauge', 1, 0, 0)
    delete g.gaugeDesign
    const { w, widget } = setup([g])
    expect(widget.gaugeDesign).toBeUndefined()
    expect(field(w, 'Titel anzeigen').element.checked).toBe(true)
    await field(w, 'Titel anzeigen').setValue(false)
    expect(widget.gaugeDesign).toMatchObject({ showTitle: false, showUnit: true, sections: [] })
    await type(field(w, 'Maximum Wert'), '120.5')
    expect((widget.gaugeDesign as Record<string, unknown>).maximum).toBe(120.5)
    await type(field(w, 'Minimum Wert'), 'abc')
    expect((widget.gaugeDesign as Record<string, unknown>).minimum).toBe(0)
  })

  it('chains the sections and takes minimum/maximum from them', async () => {
    const { w, widget } = setup([createWidget('Gauge', 1, 0, 0)])
    const add = w.findAll('button').find((b) => b.text() === 'Sektion hinzufügen')!
    await add.trigger('click')
    await type(field(w, 'End Wert der Sektion 1'), '40')
    await add.trigger('click')
    const block = widget.gaugeDesign as { sections: Record<string, unknown>[]; minimum: number; maximum: number }
    expect(block.sections).toEqual([
      { end: 40, start: 0, color: '0x00008bff' },
      { end: 0, start: 40, color: '0x00008bff' },
    ])
    await type(field(w, 'End Wert der Sektion 2'), '100')
    await type(field(w, 'Start Wert der Sektion 2'), '50')
    expect(block.sections[0].end).toBe(50)
    expect([block.minimum, block.maximum]).toEqual([0, 100])
    expect(field(w, 'Maximum Wert').element.disabled).toBe(true)
    /* colour: JavaFX 0xRRGGBBAA, alpha kept */
    block.sections[1].color = '0x00008b80'
    await flushPromises()
    await type(field(w, 'Farbe der Sektion 2'), '#FF0000')
    expect(block.sections[1].color).toBe('0xff000080')
    /* removing the first re-chains as the CC's loader would */
    await w.find('button[aria-label="Sektion entfernen 1"]').trigger('click')
    expect(block.sections).toEqual([{ end: 100, start: 50, color: '0xff000080' }])
    expect([block.minimum, block.maximum]).toEqual([50, 100])
  })
})

describe('LinearGauge, Battery, Shape, Arrow', () => {
  it('linear gauge: tick step only with major ticks, colours', async () => {
    const { w, widget } = setup([createWidget('LinearGauge', 1, 0, 0)])
    const block = widget.linearGaugeDesign as Record<string, unknown>
    await type(field(w, 'Schrittweite Große Teilstriche'), '10')
    expect(block.majorTickStep).toBe(10)
    await field(w, 'Große Teilstriche anzeigen').setValue(false)
    expect(block.showMajorTick).toBe(false)
    expect(w.findAll('label').some((l) => l.text() === 'Schrittweite Große Teilstriche')).toBe(false)
    await type(field(w, 'Rand Farbe'), '#123456')
    expect(block.colorBorder).toBe('0x123456ff')
    await type(field(w, 'Farbe Wertindikator'), 'nonsense')
    expect(block.color).toBe('0x00008bff')
    expect(field(w, 'Farbe Wertindikator').element.value).toBe('#00008b')
  })

  it('battery: show value, range, colour', async () => {
    const { w, widget } = setup([createWidget('Battery', 1, 0, 0)])
    await field(w, 'Wert anzeigen').setValue(false)
    await type(field(w, 'Maximum Wert'), '100')
    await type(field(w, 'Farbe Wertindikator'), '#00ff00')
    expect(widget.BatteryDesign).toEqual({ minimum: 0, maximum: 100, showValue: false, color: '0x00ff00ff' })
  })

  it('shape: form, values and colours, step distance', async () => {
    const { w, widget } = setup([createWidget('Shape', 1, 0, 0)])
    await field<HTMLSelectElement>(w, 'Form').setValue('ELLIPSE')
    await type(field(w, 'Minimum Wert'), '-5')
    await type(field(w, 'Farbe Maximum'), '#0000ff')
    await type(field(w, 'Schrittweite'), '2.5')
    expect(widget.shapeDesign).toEqual({ shape: 'ELLIPSE', maxColor: '0x0000ffff', maxValue: 100, minColor: '0x008000ff', minValue: -5, increment: 2.5 })
  })

  it('arrow: orientation and shape', async () => {
    const { w, widget } = setup([createWidget('Arrow', 1, 0, 0)])
    const orientation = field<HTMLSelectElement>(w, 'Ausrichtung')
    expect(orientation.findAll('option').map((o) => o.text())).toEqual(['→ Links nach Rechts', '← Pfeil nach links', '↓ Pfeil nach unten', '↑ Pfeil nach oben'])
    await orientation.setValue('BOTTOM_TOP')
    await field<HTMLSelectElement>(w, 'Form').setValue('LINE')
    expect(widget.arrow).toEqual({ orientation: 'BOTTOM_TOP', shape: 'LINE' })
  })
})

describe('Slider and PlusMinus', () => {
  it('slider: minimum and maximum', async () => {
    const { w, widget } = setup([createWidget('Slider', 1, 0, 0)])
    await type(field(w, 'Minimum Wert'), '10')
    await type(field(w, 'Maximum Wert'), '90')
    expect(widget.minMax).toEqual({ minimum: 10, maximum: 90 })
  })

  it('plus/minus: increment within 0–10', async () => {
    const { w, widget } = setup([createWidget('PlusMinus', 1, 0, 0)])
    const input = field(w, 'Schrittweite')
    expect(input.element.value).toBe('0.1')
    await type(input, '25')
    expect(widget.minMax).toEqual({ Increment: 10 })
    await type(input, '-1')
    expect(widget.minMax).toEqual({ Increment: 0 })
  })
})

describe('Value', () => {
  const widgets = () => {
    const a = createWidget('Value', 1, 0, 0)
    const b = createWidget('Value', 2, 0, 0)
    b.title = 'Bezug Musterwerk'
    const c = createWidget('Gauge', 3, 0, 0)
    const d = createWidget('Value', 4, 0, 0)
    d.title = ''
    return [a, b, c, d]
  }

  it('static limits: enable, value, colour', async () => {
    const { w, widget } = setup(widgets())
    await field(w, 'Aktiviere oberes Limit').setValue(true)
    const inputs = w.findAll('label').filter((l) => l.text() === 'Grenzwert')
    await type(w.find(`[id="${inputs[0].attributes('for')}"]`), '80')
    await type(field(w, 'Farbe oberes Limit'), '#aa0000')
    expect(widget.limit).toEqual({
      lowerLimitValue: 0,
      upperLimitValue: 80,
      lowerLimit: false,
      upperLimit: true,
      lowerLimitColor: '0x008000ff',
      upperLimitColor: '0xaa0000ff',
    })
    /* the lower limit's inputs stay disabled */
    expect(w.find(`[id="${inputs[1].attributes('for')}"]`).element.hasAttribute('disabled')).toBe(true)
  })

  it('dynamic limit refers to another Value widget', async () => {
    const { w, widget } = setup(widgets())
    await field<HTMLSelectElement>(w, 'Limit Typ').setValue('DYNAMIC')
    expect(widget.limit).toEqual({ source: -1, lowerLimitOffset: 1, upperLimitOffset: 1, lowerLimit: false, upperLimit: false, lowerLimitColor: '0x008000ff', upperLimitColor: '0xff0000ff' })
    const source = w.findAll('select').find((s) => s.findAll('option').some((o) => o.text() === 'Widget auswählen'))!
    /* only other Value widgets, sorted by uuid, without the widget itself */
    expect(source.findAll('option').map((o) => o.text())).toEqual(['Widget auswählen', "[2] 'Bezug Musterwerk'", "[4] 'Ohne Titel'"])
    await source.setValue('2')
    expect((widget.limit as Record<string, unknown>).source).toBe(2)
    await field<HTMLSelectElement>(w, 'Limit Typ').setValue('STATIC')
    expect(widget.limit).toEqual({ lowerLimit: false, upperLimit: false, lowerLimitColor: '0x008000ff', upperLimitColor: '0xff0000ff', lowerLimitValue: 0, upperLimitValue: 0 })
  })

  it('reads a stored dynamic limit', () => {
    const list = widgets()
    list[0].limit = { source: 4, lowerLimitOffset: 0.9, upperLimitOffset: 1.1, lowerLimit: true, upperLimit: true, lowerLimitColor: '0x00ff00ff', upperLimitColor: '0xff0000ff' }
    const { w } = setup(list)
    expect(field<HTMLSelectElement>(w, 'Limit Typ').element.value).toBe('DYNAMIC')
    const offsets = w.findAll('label').filter((l) => l.text() === 'Toleranz (Faktor)')
    expect(offsets.map((l) => (w.find(`[id="${l.attributes('for')}"]`).element as HTMLInputElement).value)).toEqual(['1.1', '0.9'])
  })

  it('percent: source, fraction digits (0–20), diff; display: dim incomplete', async () => {
    const { w, widget } = setup(widgets())
    const sources = w.findAll('select').filter((s) => s.findAll('option').some((o) => o.text() === 'Keine Verknüpfung'))
    await sources[0].setValue('4')
    await type(field(w, 'Maximale Dezimalstellen'), '25')
    await type(field(w, 'Minimale Dezimalstellen'), '1.6')
    await field(w, 'Umwandlung in Änderung').setValue(true)
    expect(widget.percent).toEqual({ source: 4, minFracDigits: 2, maxFracDigits: 20, diff: true })
    await sources[0].setValue('-1')
    expect((widget.percent as Record<string, unknown>).source).toBe(-1)
    await field(w, 'Unvollständigen Zeitraum abgeschwächt anzeigen').setValue(true)
    expect(widget.dimIncompletePeriod).toBe(true)
  })

  it('shows a reference to a widget that is gone', () => {
    const list = widgets()
    list[0].percent = { source: 77, minFracDigits: 0, maxFracDigits: 0, diff: false }
    const { w } = setup(list)
    expect(w.text()).toContain('Widget 77 (nicht mehr vorhanden)')
  })
})

describe('Time Frame', () => {
  const widgets = () => {
    const tf = createWidget('Time Frame', 1, 0, 0)
    const chart = createWidget('Chart', 2, 0, 0)
    chart.title = 'Verbrauch'
    const title = createWidget('Title', 3, 0, 0)
    const value = createWidget('Value', 4, 0, 0)
    const other = createWidget('Time Frame', 5, 0, 0)
    return [tf, chart, title, value, other]
  }

  it('lists the other widgets with rows and stores the selected one', async () => {
    const { w, widget } = setup(widgets())
    const rows = w.findAll('tbody tr')
    expect(rows.map((r) => r.findAll('td')[1].text())).toEqual(['2', '4'])
    expect(rows[0].text()).toContain('Diagramm')
    await rows[1].find('input[type="radio"]').setValue(true)
    expect(widget['Time Frame']).toEqual({ format: 'yyyy.MM.dd HH:mm', selectedWidget: { start: 'NONE', end: 'NONE', id: 4, isCount: false } })
    const selects = w.findAll('tbody tr')[1].findAll('select')
    await selects[0].setValue('PERIODE_FROM')
    await selects[1].setValue('LAST_TS')
    const selected = (widget['Time Frame'] as { selectedWidget: Record<string, unknown> }).selectedWidget
    expect(selected).toEqual({ start: 'PERIODE_FROM', end: 'LAST_TS', id: 4, isCount: false })
    await w.findAll('tbody tr')[1].find('input[type="checkbox"]').setValue(true)
    expect(selected).toEqual({ start: 'NONE', end: 'NONE', id: 4, isCount: true })
    await selects[1].setValue('PERIODE_UNTIL')
    expect(selected.isCount).toBe(false)
    await type(field(w, 'Format'), 'dd.MM.yyyy')
    expect((widget['Time Frame'] as Record<string, unknown>).format).toBe('dd.MM.yyyy')
  })

  it('is disabled while the widget has rows of its own', () => {
    const list = widgets()
    withRows(list[0], [2001])
    const { w } = setup(list)
    expect(w.text()).toContain('eigene Datenreihen')
    expect(w.findAll('input[type="radio"]').every((r) => r.element.hasAttribute('disabled'))).toBe(true)
  })
})

describe('NetGraph', () => {
  it('edits min/max per row only with percent, follows the rows', async () => {
    const g = withRows(createWidget('NetGraph', 1, 0, 0), [2001, 2002])
    const { w, widget } = setup([g])
    expect(w.findAll('tbody tr').map((r) => r.find('th').text())).toEqual(['Zähler 2001', 'Zähler 2002'])
    expect(field(w, 'Minimum Zähler 2001').element.disabled).toBe(true)
    await type(field(w, 'Maximum'), '50')
    expect((widget.NetGraph as Record<string, unknown>).max).toBe(50)
    await field(w, 'Werte in Prozent anzeigen').setValue(true)
    expect(w.findAll('label').some((l) => l.text() === 'Maximum')).toBe(false)
    await type(field(w, 'Maximum Zähler 2002'), '80')
    expect((widget.NetGraph as Record<string, unknown>).netGraphDataRows).toEqual([
      { min: 0, max: 100, id: 2001 },
      { min: 0, max: 80, id: 2002 },
    ])
    await field<HTMLSelectElement>(w, 'Darstellung').setValue('POLYGON')
    expect((widget.NetGraph as Record<string, unknown>).skin).toBe('POLYGON')
    /* a row removed in the data tab is dropped */
    widget.dataHandler!.chartModels![0].chartData!.splice(0, 1)
    await flushPromises()
    expect((widget.NetGraph as Record<string, unknown>).netGraphDataRows).toEqual([{ min: 0, max: 80, id: 2002 }])
  })
})

describe('Sankey', () => {
  it('flows, order, offsets of the inner levels and the options', async () => {
    const s = withRows(createWidget('Sankey', 1, 0, 0), [11, 12, 13])
    const { w, widget } = setup([s])
    const block = () => widget.Sankey as Record<string, unknown>
    const row = (i: number) => w.findAll('tbody tr')[i]
    /* 11 → 12 → 13 */
    const check = async (r: number, name: string) => {
      const label = row(r).findAll('label').find((l) => l.text() === name)!
      await label.find('input').setValue(true)
    }
    await check(0, 'Zähler 12')
    await check(1, 'Zähler 13')
    expect(block().sankeyDataRows).toEqual([
      { id: 11, children: [12] },
      { id: 12, children: [13] },
      { id: 13, children: [] },
    ])
    /* max level 2 → one movable level, labelled "Level 2" */
    const offset = field(w, 'Level 2')
    expect(offset.attributes('min')).toBe('-800')
    await type(offset, '900')
    expect(block().offsets).toEqual([{ level: 1, offset: 800 }])
    await w.find('button[aria-label="Zähler 13 nach oben"]').trigger('click')
    expect((block().sankeyDataRows as { id: number }[]).map((r) => r.id)).toEqual([11, 13, 12])
    await field(w, 'Automatischer Item Lückenabstand').setValue(true)
    expect(w.findAll('label').some((l) => l.text() === 'Abstand')).toBe(false)
    await field(w, 'Zeige Prozentwerte').setValue(true)
    await field<HTMLSelectElement>(w, 'Bezieht sich auf').setValue('ROOT')
    await type(field(w, 'Fehler Toleranz'), '0.5')
    expect(block()).toMatchObject({ autoGap: true, refersTo: 'ROOT', errorTolerance: 0.5, showPercent: true })
  })

  it('adds new rows at the end when the rows change', async () => {
    const s = withRows(createWidget('Sankey', 1, 0, 0), [11])
    s.Sankey = { ...(s.Sankey as object), sankeyDataRows: [{ id: 11, children: [] }] }
    const { widget } = setup([s])
    widget.dataHandler!.chartModels![0].chartData!.push({ id: 14, attributeString: 'Value', name: 'Zähler 14' })
    await flushPromises()
    expect((widget.Sankey as Record<string, unknown>).sankeyDataRows).toEqual([
      { id: 11, children: [] },
      { id: 14, children: [] },
    ])
  })
})

describe('Image', () => {
  it('asks to save first without a dashboard id', () => {
    const { w } = setup([createWidget('Image', 1, 0, 0)], 0, null)
    expect(w.text()).toContain('erst speichern')
    expect(w.find('input[type="file"]').exists()).toBe(false)
  })

  it('uploads the image and stores the object id as the CC does (string)', async () => {
    http.postForm.mockResolvedValue({ objectId: 9001, name: 'Lageplan' })
    const { w, widget } = setup([createWidget('Image', 1, 0, 0)], 0, 42)
    const input = w.find<HTMLInputElement>('input[type="file"]')
    const file = new File(['png'], 'Lageplan.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(http.postForm).toHaveBeenCalledTimes(1)
    const [path, form] = http.postForm.mock.calls[0] as [string, FormData]
    expect(path).toBe('/web/dashboards/42/images')
    expect((form.get('file') as File).name).toBe('Lageplan.png')
    expect(widget.image).toEqual({ objectID: '9001' })
    expect(w.text()).toContain('Hochgeladen: Lageplan')
    expect(http.getBlob).toHaveBeenCalledWith('/web/objects/9001/image')
  })

  it('reports a too large file', async () => {
    const { HttpError } = await import('../src/api/http')
    http.postForm.mockRejectedValue(new HttpError(413, ''))
    const { w, widget } = setup([createWidget('Image', 1, 0, 0)], 0, 42)
    const input = w.find<HTMLInputElement>('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [new File(['x'], 'gross.png')], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(w.find('[role="alert"]').text()).toContain('größer als 10 MB')
    expect(widget.image).toEqual({ objectID: '-1' })
  })

  it('links to a dashboard or an analysis of the navigation', async () => {
    useNavigationStore().data = {
      user: { id: 1, name: 'Muster', sysAdmin: false },
      sites: [{ id: 100, name: 'Musterwerk', names: {}, className: 'Building', parentId: null, analyses: [{ id: 300, name: 'Strom', names: {} }], dashboards: [{ id: 200, name: 'Übersicht', names: {} }] }],
      other: { analyses: [], dashboards: [] },
    }
    const { w, widget } = setup([createWidget('Image', 1, 0, 0)])
    await field<HTMLSelectElement>(w, 'Verknüpfung').setValue('DASHBOARD')
    const target = field<HTMLSelectElement>(w, 'Ziel')
    expect(target.findAll('option').map((o) => o.text())).toEqual(['Kein Ziel', 'Musterwerk / Übersicht'])
    await target.setValue('200')
    expect(widget.image).toEqual({ objectID: '-1', dashboardLinker: { dashboardObject: 200 } })
    await field<HTMLSelectElement>(w, 'Verknüpfung').setValue('ANALYSIS')
    expect(widget.image).toEqual({ objectID: '-1' })
    await field<HTMLSelectElement>(w, 'Ziel').setValue('300')
    expect(widget.image).toEqual({ objectID: '-1', analysisLinker: { graphAnalysisObject: 300 } })
    await field<HTMLSelectElement>(w, 'Verknüpfung').setValue('NONE')
    expect(widget.image).toEqual({ objectID: '-1' })
  })
})

describe('Link widgets', () => {
  const navigation = () => {
    useNavigationStore().data = {
      user: { id: 1, name: 'Muster', sysAdmin: false },
      sites: [{ id: 100, name: 'Musterwerk', names: { en: 'Sample plant' }, className: 'Building', parentId: null, analyses: [{ id: 300, name: 'Strom', names: {} }], dashboards: [{ id: 200, name: 'Übersicht', names: {} }] }],
      other: { analyses: [{ id: 301, name: 'Gas', names: {} }], dashboards: [] },
    }
  }

  it('Analyse-Link: target and time frame (only written if set)', async () => {
    navigation()
    const { w, widget } = setup([createWidget('Analyse-Link', 1, 0, 0)])
    const target = field<HTMLSelectElement>(w, 'Analyse')
    expect(target.findAll('option').map((o) => o.text())).toEqual(['Kein Ziel', 'Gas', 'Musterwerk / Strom'])
    await target.setValue('300')
    const frame = field<HTMLSelectElement>(w, 'Zeitraum der Analyse')
    expect(frame.findAll('option').map((o) => o.attributes('value'))).toContain('LASTVALUE')
    expect(frame.findAll('option').map((o) => o.attributes('value'))).not.toContain('CUSTOM')
    await frame.setValue('P1M')
    expect(widget.analysisLinker).toEqual({ graphAnalysisObject: 300, forcedInterval: 'P1M' })
    await frame.setValue('')
    expect(widget.analysisLinker).toEqual({ graphAnalysisObject: 300 })
  })

  it('Dashboard-Link: dashboards of the navigation; a stored unreadable target stays', async () => {
    navigation()
    const d = createWidget('Dashboard-Link', 1, 0, 0)
    d.dashboardLinker = { dashboardObject: 999 }
    const { w, widget } = setup([d])
    const target = field<HTMLSelectElement>(w, 'Dashboard')
    expect(target.element.value).toBe('999')
    expect(target.text()).toContain('Objekt 999 (nicht verfügbar)')
    await target.setValue('200')
    expect(widget.dashboardLinker).toEqual({ dashboardObject: 200 })
  })

  it('Report-Link: reports of GET /web/reports', async () => {
    http.get.mockImplementation(async (path: string) => {
      if (path === '/web/reports') return { reports: [{ id: 400, name: 'Monatsbericht', className: 'Report', path: 'Berichte', site: { id: 100, name: 'Musterwerk' }, enabled: true, schedule: null, latest: null, count: 0 }] }
      throw new Error('GET ' + path)
    })
    const { w, widget } = setup([createWidget('Report-Link', 1, 0, 0)])
    await flushPromises()
    const target = field<HTMLSelectElement>(w, 'Bericht')
    expect(target.findAll('option').map((o) => o.text())).toEqual(['Kein Ziel', 'Musterwerk / Berichte / Monatsbericht'])
    await target.setValue('400')
    expect(widget.reportLinker).toEqual({ reportObject: 400 })
  })
})

describe('Alarm Count', () => {
  it('searches alarm configurations and directories and stores the chosen one', async () => {
    http.get.mockImplementation(async (path: string) => {
      if (path.startsWith('/web/rows/tree?')) {
        return [
          { id: 700, name: 'Alarme Musterwerk', class: 'Alarm Directory', path: 'Musterwerk' },
          { id: 701, name: 'Alarm Strom', class: 'Alarm Configuration', path: 'Musterwerk / Alarme' },
          { id: 702, name: 'Alarm Zähler', class: 'Data', path: 'Musterwerk' },
        ]
      }
      throw new Error('GET ' + path)
    })
    const { w, widget } = setup([createWidget('Alarm Count', 1, 0, 0)])
    expect(w.find('input[type="search"]').exists()).toBe(false)
    await field(w, 'Nur ausgewählte Alarmkonfiguration / Alarmverzeichnis').setValue(true)
    await field(w, 'Alarmkonfiguration oder Alarmverzeichnis suchen').setValue('Alarm')
    await w.findAll('button').find((b) => b.text() === 'Suchen')!.trigger('click')
    await flushPromises()
    expect(http.get).toHaveBeenCalledWith('/web/rows/tree?q=Alarm&lang=de')
    const hits = w.findAll('.dt-hits button')
    expect(hits).toHaveLength(2)
    await hits[1].trigger('click')
    expect(widget.alarmCount).toEqual({ alarmObject: 701 })
    expect(w.find('.dt-current').text()).toContain('Musterwerk / Alarme / Alarm Strom')
    await field(w, 'Nur ausgewählte Alarmkonfiguration / Alarmverzeichnis').setValue(false)
    expect(widget.alarmCount).toEqual({ alarmObject: 0 })
  })
})
