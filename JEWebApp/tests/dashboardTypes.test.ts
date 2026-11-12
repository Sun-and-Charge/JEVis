/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import de from '../src/locales/dashboardTypes.de.json'
import en from '../src/locales/dashboardTypes.en.json'
import { typeEditorFor, WIDGET_TYPES } from '../src/dashboard/edit/typeEditors'
import { createWidget, emptyDataHandler } from '../src/dashboard/edit/widgetDefaults'
import { defaultBlock, ensureBlock, rowObjects, sankeyMaxLevel, syncNetGraphRows, syncSankeyRows } from '../src/dashboard/edit/types/shared'
import type { RawWidget } from '../src/dashboard/edit/raw'

/*
 * New widgets as the Control Center creates them (E4) and the pure helpers of the type editors. References are to
 * JEVisControlCenter/src/main/java/org/jevis/jeconfig/plugin/dashboard/.
 */

/* widget/Widgets.java FACTORIES, WIDGET_ID of each widget class */
const CC_IDS = [
  'Title',
  'Chart',
  'Pie',
  'Table',
  'Analyse-Link',
  'Value',
  'String Value',
  'Value Editor',
  'Dashboard-Link',
  'Arrow',
  'Sankey',
  'Image',
  'Gauge',
  'LinearGauge',
  'Shape',
  'Time Frame',
  'Battery',
  'NetGraph',
  'Toggle Switch',
  'Slider',
  'PlusMinus',
  'Report-Link',
  'Alarm Count',
]

/* createDefaultConfig: new Size(yGridInterval * rows, xGridInterval * cols) → [height, width] with grid 25 */
const SIZES: Record<string, [number, number]> = {
  Title: [1, 4], // TitleWidget.java:78
  Chart: [12, 20], // ChartWidget.java:97
  Pie: [12, 12], // PieWidget.java:87
  Table: [20, 20], // TableWidget.java:87
  'Analyse-Link': [1, 1], // LinkerWidget.java:97
  Value: [1, 4], // ValueWidget.java:118
  'String Value': [1, 4], // StringValueWidget.java:114
  'Value Editor': [1, 6], // ValueEditWidget.java:90
  'Dashboard-Link': [1, 1], // DashboadLinkWidget.java:93
  Arrow: [1, 5], // ArrowWidget.java:74
  Sankey: [12, 20], // SankeyWidget.java:98
  Image: [5, 5], // ImageWidget.java:93
  Gauge: [4, 4], // GaugeWidget.java:95
  LinearGauge: [6, 2], // LinearGaugeWidget.java:106
  Shape: [4, 4], // ShapeWidget.java:93
  'Time Frame': [1, 12], // TimeFrameWidget.java:144
  Battery: [2, 4], // BatteryWidget.java:103
  NetGraph: [6, 2], // NetGraphWidget.java:130
  'Toggle Switch': [2, 2], // ToogleSwitchWidget.java:101
  Slider: [2, 4], // SliderWidget.java:102
  PlusMinus: [2, 4], // PlusMinusWidget.java:101
  'Report-Link': [1, 1], // ReportLinkWidget.java:86
  'Alarm Count': [1, 4], // AlarmCountWidget.java:84
}

/* types whose toNode writes `dataHandler` (DataModelDataHandler) */
const ROW_TYPES = [
  'Chart',
  'Pie',
  'Table',
  'Value',
  'String Value',
  'Value Editor',
  'Sankey',
  'Gauge',
  'LinearGauge',
  'Shape',
  'Time Frame',
  'Battery',
  'NetGraph',
  'Toggle Switch',
  'Slider',
  'PlusMinus',
]

/* Widget.createDefaultNode (widget/Widget.java:624-654) with the WidgetPojo defaults (config2/WidgetPojo.java) */
const COMMON = {
  tooltip: '',
  'Background Color': '0xffffffff',
  'Font Color': '0x000000ff',
  fontSize: 13,
  fontWeight: 'NORMAL',
  fontPosture: 'REGULAR',
  fontUnderlined: false,
  'Title Position': 'CENTER',
  borderSize: 0.2,
  borderColor: '0x000000ff',
  cornerRadius: 0,
  shadow: true,
  layer: 1,
  decimals: 2,
  showValue: true,
  fixedTimeframe: false,
  forcedTimeFrame: 'PT0S',
}

describe('WIDGET_TYPES', () => {
  it('lists the 23 registered types with their exact ids', () => {
    expect(WIDGET_TYPES.map((t) => t.id)).toEqual(CC_IDS)
  })

  it('has a name in both languages and marks the row types', () => {
    for (const type of WIDGET_TYPES) {
      const key = type.labelKey.replace('dashboardEdit.types.names.', '') as keyof typeof de.names
      expect(type.labelKey.startsWith('dashboardEdit.types.names.')).toBe(true)
      expect(de.names[key], type.id).toBeTruthy()
      expect(en.names[key], type.id).toBeTruthy()
      expect(type.hasRows, type.id).toBe(ROW_TYPES.includes(type.id))
    }
  })

  it('offers a settings component for the types with an own CC tab only', () => {
    const without = ['Title', 'Chart', 'Pie', 'Table', 'String Value', 'Value Editor', 'Toggle Switch']
    for (const id of CC_IDS) expect(typeEditorFor(id) === null, id).toBe(without.includes(id))
    expect(typeEditorFor('Unknown')).toBeNull()
    expect(typeEditorFor('Slider')).toBe(typeEditorFor('PlusMinus'))
  })
})

describe('locale files', () => {
  const keys = (o: unknown, prefix = ''): string[] =>
    typeof o === 'object' && o !== null ? Object.entries(o).flatMap(([k, v]) => keys(v, prefix + k + '.')) : [prefix]
  it('have the same keys in German and English', () => {
    expect(keys(en).sort()).toEqual(keys(de).sort())
  })
  it('use no vue-i18n special characters by accident', () => {
    for (const k of keys(de)) {
      const text = k.split('.').slice(0, -1).reduce<unknown>((o, p) => (o as Record<string, unknown>)[p], de) as string
      expect(text, k).not.toMatch(/[|@$]/)
    }
  })
})

describe('createWidget', () => {
  it('creates every type with the common defaults, position, uuid and the CC default size', () => {
    for (const id of CC_IDS) {
      const w = createWidget(id, 7, 50, 75)
      expect(w.uuid).toBe(7)
      expect(w.WidgetType).toBe(id)
      expect(w.xPos).toBe(50)
      expect(w.yPos).toBe(75)
      const [rows, cols] = SIZES[id]
      expect([w.height, w.width], id).toEqual([rows * 25, cols * 25])
      for (const [key, value] of Object.entries(COMMON)) {
        if (['Background Color', 'borderSize', 'decimals'].includes(key)) continue // type overrides, below
        expect(w[key], `${id}.${key}`).toEqual(value)
      }
      expect('dataHandler' in w, id).toBe(ROW_TYPES.includes(id))
    }
  })

  it('writes the common keys in the order of createDefaultNode', () => {
    expect(Object.keys(createWidget('Title', 1, 0, 0))).toEqual([
      'uuid',
      'WidgetType',
      'title',
      'tooltip',
      'Background Color',
      'Font Color',
      'fontSize',
      'fontWeight',
      'fontPosture',
      'fontUnderlined',
      'Title Position',
      'borderSize',
      'borderColor',
      'cornerRadius',
      'shadow',
      'layer',
      'width',
      'height',
      'decimals',
      'showValue',
      'xPos',
      'yPos',
      'fixedTimeframe',
      'forcedTimeFrame',
    ])
  })

  it('applies the overrides of createDefaultConfig', () => {
    const bg = (t: string) => createWidget(t, 1, 0, 0)['Background Color']
    const border = (t: string) => createWidget(t, 1, 0, 0).borderSize
    const decimals = (t: string) => createWidget(t, 1, 0, 0).decimals
    expect(bg('Arrow')).toBe('0x00000000') // ArrowWidget.java:76 Color.TRANSPARENT
    expect(bg('Image')).toBe('0xf5f5f5ff') // ImageWidget.java:95 Color.WHITESMOKE
    expect(bg('Shape')).toBe('0x000000ff') // ShapeWidget.java:95 Color.BLACK
    expect(bg('Dashboard-Link')).toBe('0xffffff00') // DashboadLinkWidget.java:94 Color.web("#ffffff", 0.0)
    expect(bg('Report-Link')).toBe('0xffffff00') // ReportLinkWidget.java:87
    expect(bg('Value')).toBe('0xffffffff') // WidgetPojo: Color.web("#ffffff")
    for (const t of ['Arrow', 'Image', 'Shape', 'Dashboard-Link', 'Report-Link', 'Analyse-Link', 'Battery', 'Slider', 'PlusMinus'])
      expect(border(t), t).toBe(0) // BorderWidths.EMPTY / new BorderWidths(0)
    expect(border('Value')).toBe(0.2) // WidgetPojo: new BorderWidths(0.2)
    for (const t of ['Gauge', 'LinearGauge', 'Battery', 'NetGraph']) expect(decimals(t), t).toBe(1) // setDecimals(1)
    /* decimalsAuto only where set (Widget.java:650): GaugeWidget.java:99 */
    expect(createWidget('Gauge', 1, 0, 0).decimalsAuto).toBe(true)
    expect('decimalsAuto' in createWidget('Value', 1, 0, 0)).toBe(false)
  })

  it('uses the translated default titles of the CC', () => {
    expect(createWidget('Value', 1, 0, 0).title).toBe('Neues Werte Widget') // plugin.dashboard.valuewidget.newname
    expect(createWidget('Value', 1, 0, 0, { locale: 'en' }).title).toBe('new value widget')
    expect(createWidget('Title', 1, 0, 0).title).toBe('Neuer Titel')
    expect(createWidget('Image', 1, 0, 0).title).toBe('Neues Bild')
    expect(createWidget('Analyse-Link', 1, 0, 0).title).toBe('Neuer Analyse Link')
    expect(createWidget('Dashboard-Link', 1, 0, 0).title).toBe('Neue Dashboard Verknüpfung')
    expect(createWidget('Report-Link', 1, 0, 0).title).toBe('Bericht-Verknüpfung')
    expect(createWidget('Alarm Count', 1, 0, 0).title).toBe('Alarme')
    expect(createWidget('Pie', 1, 0, 0).title).toBe('Neues Tortendiagramm')
    expect(createWidget('Table', 1, 0, 0, { locale: 'en-GB' }).title).toBe('new table')
    /* ChartWidget.java:95 / SankeyWidget.java:96 use a literal title */
    expect(createWidget('Chart', 1, 0, 0).title).toBe('new Chart Widget')
    expect(createWidget('Sankey', 1, 0, 0, { locale: 'en' }).title).toBe('new Chart Widget')
  })

  it('sizes by the dashboard grid', () => {
    const w = createWidget('Chart', 1, 0, 0, { grid: { x: 10, y: 20 } })
    expect([w.width, w.height]).toEqual([200, 240])
  })

  it('gives row types the empty AnalysisHandler of a new DataModelDataHandler', () => {
    const w = createWidget('Value', 1, 0, 0)
    expect(w.dataHandler).toEqual(emptyDataHandler())
    expect(w.dataHandler).toMatchObject({ type: 'AnalysisHandler', forcedInterval: '', chartModels: [{ chartId: -1, chartType: 'LINE', chartData: [] }] })
    /* every widget gets its own objects */
    expect(createWidget('Value', 2, 0, 0).dataHandler).not.toBe(w.dataHandler)
  })

  it('adds the type blocks with the defaults of the CC pojos', () => {
    const block = (t: string, k: string) => createWidget(t, 1, 0, 0)[k]
    /* config2/Limit.java:46-64 (STATIC, colours GREEN/RED), Percent.java, ValueWidget.java:104 */
    expect(block('Value', 'limit')).toEqual({
      lowerLimitValue: 0,
      upperLimitValue: 0,
      lowerLimit: false,
      upperLimit: false,
      lowerLimitColor: '0x008000ff',
      upperLimitColor: '0xff0000ff',
    })
    expect(block('Value', 'percent')).toEqual({ source: -1, minFracDigits: 0, maxFracDigits: 0, diff: false })
    expect(block('Value', 'dimIncompletePeriod')).toBe(false)
    /* config2/GaugePojo.java */
    expect(block('Gauge', 'gaugeDesign')).toEqual({ minimum: 0, maximum: 0, inPercent: false, showTitle: true, showValue: true, showUnit: true, sections: [] })
    /* config2/LinearGaugePojo.java (DARKBLUE, BLACK, step 5) */
    expect(block('LinearGauge', 'linearGaugeDesign')).toEqual({
      minimum: 0,
      maximum: 0,
      inPercent: false,
      showTitle: true,
      showValue: true,
      showUnit: true,
      color: '0x00008bff',
      showMajorTick: true,
      showMediumTick: true,
      showMinorTick: true,
      colorBorder: '0x000000ff',
      majorTickStep: 5,
    })
    /* config2/BatteryPojo.java */
    expect(block('Battery', 'BatteryDesign')).toEqual({ minimum: 0, maximum: 0, showValue: true, color: '0x008000ff' })
    /* config2/ShapePojo.java */
    expect(block('Shape', 'shapeDesign')).toEqual({ shape: 'RECTANGLE', maxColor: '0xff0000ff', maxValue: 100, minColor: '0x008000ff', minValue: 0, increment: 0 })
    /* config2/NetGraphPojo.java */
    expect(block('NetGraph', 'NetGraph')).toEqual({ inPercent: false, skin: 'SECTOR', max: 35, netGraphDataRows: [] })
    /* config2/SankeyPojo.java */
    expect(block('Sankey', 'Sankey')).toEqual({
      showValue: true,
      showPercent: true,
      refersTo: 'PARENT',
      autoGap: false,
      gap: 0,
      errorTolerance: 0,
      sankeyDataRows: [],
      offsets: [],
    })
    /* config2/TimeFramePojo.java */
    expect(block('Time Frame', 'Time Frame')).toEqual({ format: 'yyyy.MM.dd HH:mm', selectedWidget: { start: '', end: '', id: -1, isCount: false } })
    /* config2/MinMaxPojo.java, IncrementPojo.java */
    expect(block('Slider', 'minMax')).toEqual({ minimum: 0, maximum: 0 })
    expect(block('PlusMinus', 'minMax')).toEqual({ Increment: 0.1 })
    /* config2/ArrowConfig.java */
    expect(block('Arrow', 'arrow')).toEqual({ orientation: 'LEFT_RIGHT', shape: 'ARROW' })
    /* config2/ImageConfig.java toJSON: objectID.toString() */
    expect(block('Image', 'image')).toEqual({ objectID: '-1' })
    /* the linker nodes start with 0L */
    expect(block('Analyse-Link', 'analysisLinker')).toEqual({ graphAnalysisObject: 0 })
    expect(block('Dashboard-Link', 'dashboardLinker')).toEqual({ dashboardObject: 0 })
    expect(block('Report-Link', 'reportLinker')).toEqual({ reportObject: 0 })
    expect(block('Alarm Count', 'alarmCount')).toEqual({ alarmObject: 0 })
  })

  it('keeps unknown types to the common keys', () => {
    const w = createWidget('Mystery', 3, 1, 2)
    expect(w.WidgetType).toBe('Mystery')
    expect([w.width, w.height]).toEqual([250, 150]) // Size.DEFAULT
    expect('dataHandler' in w).toBe(false)
  })
})

const rowWidget = (ids: number[], type = 'Sankey'): RawWidget => {
  const w = createWidget(type, 1, 0, 0)
  w.dataHandler!.chartModels![0].chartData = ids.map((id) => ({ id, attributeString: 'Value', name: 'Zähler ' + id }))
  return w
}

describe('type editor helpers', () => {
  it('creates a missing block with the defaults only when writing', () => {
    const w = createWidget('Gauge', 1, 0, 0)
    delete w.gaugeDesign
    expect(defaultBlock('Gauge', 'gaugeDesign')).toMatchObject({ maximum: 0 })
    const b = ensureBlock(w, 'gaugeDesign')
    expect(w.gaugeDesign).toBe(b)
    expect(b).toMatchObject({ showTitle: true, sections: [] })
    expect(ensureBlock(w, 'gaugeDesign')).toBe(b)
  })

  it('lists the distinct row objects of a widget', () => {
    const w = rowWidget([11, 12, 11])
    expect(rowObjects(w)).toEqual([
      { id: 11, name: 'Zähler 11' },
      { id: 12, name: 'Zähler 12' },
    ])
  })

  it('syncs the net graph rows with the widget rows (new 0/100, removed dropped)', () => {
    const rows = rowObjects(rowWidget([11, 12]))
    expect(syncNetGraphRows([{ min: 5, max: 50, id: 12 }, { min: 1, max: 2, id: 99 }], rows)).toEqual([
      { id: 11, min: 0, max: 100 },
      { id: 12, min: 5, max: 50 },
    ])
  })

  it('syncs the sankey rows: stored order, new rows at the end, children among the rows', () => {
    const rows = rowObjects(rowWidget([11, 12, 13]))
    const stored = [
      { id: 13, children: [11, 99, 13] },
      { id: 99, children: [] },
      { id: 11, children: ['12'] },
    ]
    expect(syncSankeyRows(stored, rows)).toEqual([
      { id: 13, children: [11] },
      { id: 11, children: [12] },
      { id: 12, children: [] },
    ])
  })

  it('computes the deepest sankey level without following cycles', () => {
    expect(sankeyMaxLevel([])).toBe(0)
    expect(
      sankeyMaxLevel([
        { id: 1, children: [2] },
        { id: 2, children: [3] },
        { id: 3, children: [] },
      ]),
    ).toBe(2)
    expect(
      sankeyMaxLevel([
        { id: 1, children: [2] },
        { id: 2, children: [1] },
      ]),
    ).toBe(1)
  })
})
