/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

import type { RawDataHandler, RawWidget } from './raw'

/*
 * New widgets exactly as the Control Center creates them (E4): `NewWidgetSelector.getSelectedWidget` →
 * `Widgets.createWidget(type, control, null)` → the widget's `createDefaultConfig()` (title, size, own overrides of the
 * `WidgetPojo` defaults), then `DashboardControl.addWidget` → `init()` creates the type settings with their defaults,
 * and the next save writes `toNode()` = `Widget.createDefaultNode()` + `dataHandler` + the type block. All references
 * below are to JEVisControlCenter/src/main/java/org/jevis/jeconfig/plugin/dashboard/.
 */

/* JavaFX `Color.toString()` of the colours used as defaults */
const BLACK = '0x000000ff'
const WHITE = '0xffffffff' // Color.web("#ffffff")
const TRANSPARENT = '0x00000000'
const WHITE_TRANSPARENT = '0xffffff00' // Color.web("#ffffff", 0.0)
const WHITESMOKE = '0xf5f5f5ff'
const RED = '0xff0000ff'
const GREEN = '0x008000ff'
const DARKBLUE = '0x00008bff'

/** Default grid interval of a dashboard (DashboardPojo.xGridInterval / yGridInterval = 25). */
export const DEFAULT_GRID = 25

/** Literal default titles of `createDefaultConfig()` (JEVis_de/en.properties `plugin.dashboard.*.newname`). */
const TITLES: Record<'de' | 'en', Record<string, string>> = {
  de: {
    value: 'Neues Werte Widget',
    title: 'Neuer Titel',
    image: 'Neues Bild',
    analysisLink: 'Neuer Analyse Link',
    dashboardLink: 'Neue Dashboard Verknüpfung',
    reportLink: 'Bericht-Verknüpfung',
    alarm: 'Alarme',
    pie: 'Neues Tortendiagramm',
    table: 'Neue Tabelle',
  },
  en: {
    value: 'new value widget',
    title: 'new title',
    image: 'New Image',
    analysisLink: 'new analysis link',
    dashboardLink: 'New dashboard link',
    reportLink: 'Report link',
    alarm: 'Alarms',
    pie: 'new Piechart',
    table: 'new table',
  },
}

interface TypeDefaults {
  /** key of TITLES, or a literal title (`chart` = "new Chart Widget" in every language) */
  title: string
  /** Size(yGridInterval * rows, xGridInterval * cols) of createDefaultConfig */
  rows: number
  cols: number
  /** overrides of the WidgetPojo defaults */
  common?: Partial<RawWidget>
  /** the type uses a DataModelDataHandler (toNode writes `dataHandler`) */
  data: boolean
  /** type block written by toNode, as created by init() without a stored node */
  block?: () => Record<string, unknown>
}

const CHART_TITLE = '\u0000new Chart Widget'

/* one entry per type of widget/Widgets.java; the line numbers are those of createDefaultConfig / the pojo defaults */
const DEFAULTS: Record<string, TypeDefaults> = {
  // TitleWidget.java:75-80
  Title: { title: 'title', rows: 1, cols: 4, data: false },
  // ChartWidget.java:94-99, toNode 348-352
  Chart: { title: CHART_TITLE, rows: 12, cols: 20, data: true },
  // PieWidget.java:84-89
  Pie: { title: 'pie', rows: 12, cols: 12, data: true },
  // TableWidget.java:84-89
  Table: { title: 'table', rows: 20, cols: 20, data: true },
  // LinkerWidget.java:93-99 (border 0), init 310-315: new GraphAnalysisLinkerNode() (graphAnalysisObject 0L, forcedInterval "" not written: NON_EMPTY)
  'Analyse-Link': {
    title: 'analysisLink',
    rows: 1,
    cols: 1,
    common: { borderSize: 0 },
    data: false,
    block: () => ({ analysisLinker: { graphAnalysisObject: 0 } }),
  },
  // ValueWidget.java:114-121, toNode 578-596; config2/Limit.java:46-64 + toJSON (STATIC), config2/Percent.java toJSON, ValueWidget.java:104
  Value: {
    title: 'value',
    rows: 1,
    cols: 4,
    data: true,
    block: () => ({
      limit: {
        lowerLimitValue: 0,
        upperLimitValue: 0,
        lowerLimit: false,
        upperLimit: false,
        lowerLimitColor: GREEN,
        upperLimitColor: RED,
      },
      percent: { source: -1, minFracDigits: 0, maxFracDigits: 0, diff: false },
      dimIncompletePeriod: false,
    }),
  },
  // StringValueWidget.java:110-117
  'String Value': { title: 'value', rows: 1, cols: 4, data: true },
  // ValueEditWidget.java:86-93
  'Value Editor': { title: 'value', rows: 1, cols: 6, data: true },
  // DashboadLinkWidget.java:88-97, init: new DashboardLinkerNode() (dashboardObject 0L)
  'Dashboard-Link': {
    title: 'dashboardLink',
    rows: 1,
    cols: 1,
    common: { borderSize: 0, 'Background Color': WHITE_TRANSPARENT },
    data: false,
    block: () => ({ dashboardLinker: { dashboardObject: 0 } }),
  },
  // ArrowWidget.java:70-81, config2/ArrowConfig.java defaults (LEFT_RIGHT, ARROW)
  Arrow: {
    title: 'title',
    rows: 1,
    cols: 5,
    common: { shadow: true, 'Background Color': TRANSPARENT, borderSize: 0 },
    data: false,
    block: () => ({ arrow: { orientation: 'LEFT_RIGHT', shape: 'ARROW' } }),
  },
  // SankeyWidget.java:94-101, config2/SankeyPojo.java defaults + toJSON
  Sankey: {
    title: CHART_TITLE,
    rows: 12,
    cols: 20,
    data: true,
    block: () => ({
      Sankey: {
        showValue: true,
        showPercent: true,
        refersTo: 'PARENT',
        autoGap: false,
        gap: 0,
        errorTolerance: 0,
        sankeyDataRows: [],
        offsets: [],
      },
    }),
  },
  // ImageWidget.java:89-100, init 180: new ImageConfig(control, null) → toJSON {"objectID": "-1"} (a string, ImageConfig.toJSON)
  Image: {
    title: 'image',
    rows: 5,
    cols: 5,
    common: { shadow: true, 'Background Color': WHITESMOKE, borderSize: 0 },
    data: false,
    block: () => ({ image: { objectID: '-1' } }),
  },
  // GaugeWidget.java:91-102 (decimals 1, decimalsAuto true), config2/GaugePojo.java defaults + toJSON
  Gauge: {
    title: 'value',
    rows: 4,
    cols: 4,
    common: { decimals: 1, decimalsAuto: true },
    data: true,
    block: () => ({
      gaugeDesign: { minimum: 0, maximum: 0, inPercent: false, showTitle: true, showValue: true, showUnit: true, sections: [] },
    }),
  },
  // LinearGaugeWidget.java:102-110, config2/LinearGaugePojo.java defaults + toJSON
  LinearGauge: {
    title: 'value',
    rows: 6,
    cols: 2,
    common: { decimals: 1 },
    data: true,
    block: () => ({
      linearGaugeDesign: {
        minimum: 0,
        maximum: 0,
        inPercent: false,
        showTitle: true,
        showValue: true,
        showUnit: true,
        color: DARKBLUE,
        showMajorTick: true,
        showMediumTick: true,
        showMinorTick: true,
        colorBorder: BLACK,
        majorTickStep: 5,
      },
    }),
  },
  // ShapeWidget.java:89-104, config2/ShapePojo.java defaults + toJSON
  Shape: {
    title: 'title',
    rows: 4,
    cols: 4,
    common: { shadow: true, 'Background Color': BLACK, borderSize: 0 },
    data: true,
    block: () => ({
      shapeDesign: { shape: 'RECTANGLE', maxColor: RED, maxValue: 100, minColor: GREEN, minValue: 0, increment: 0 },
    }),
  },
  // TimeFrameWidget.java:140-147, config2/TimeFramePojo.java defaults + toJSON
  'Time Frame': {
    title: 'title',
    rows: 1,
    cols: 12,
    data: true,
    block: () => ({
      'Time Frame': { format: 'yyyy.MM.dd HH:mm', selectedWidget: { start: '', end: '', id: -1, isCount: false } },
    }),
  },
  // BatteryWidget.java:99-110 (decimals 1, border 0), config2/BatteryPojo.java defaults + toJSON
  Battery: {
    title: 'value',
    rows: 2,
    cols: 4,
    common: { decimals: 1, borderSize: 0 },
    data: true,
    block: () => ({ BatteryDesign: { minimum: 0, maximum: 0, showValue: true, color: GREEN } }),
  },
  // NetGraphWidget.java:126-134 (decimals 1), config2/NetGraphPojo.java defaults (SECTOR, max 35) + toJSON
  NetGraph: {
    title: 'value',
    rows: 6,
    cols: 2,
    common: { decimals: 1 },
    data: true,
    block: () => ({ NetGraph: { inPercent: false, skin: 'SECTOR', max: 35, netGraphDataRows: [] } }),
  },
  // ToogleSwitchWidget.java:97-103
  'Toggle Switch': { title: 'value', rows: 2, cols: 2, data: true },
  // SliderWidget.java:98-105 (border 0), config2/MinMaxPojo.java defaults + toJSON
  Slider: {
    title: 'value',
    rows: 2,
    cols: 4,
    common: { borderSize: 0 },
    data: true,
    block: () => ({ minMax: { minimum: 0, maximum: 0 } }),
  },
  // PlusMinusWidget.java:97-104 (border 0), config2/IncrementPojo.java (Increment 0.1) + toJSON
  PlusMinus: {
    title: 'value',
    rows: 2,
    cols: 4,
    common: { borderSize: 0 },
    data: true,
    block: () => ({ minMax: { Increment: 0.1 } }),
  },
  // ReportLinkWidget.java:81-90, ReportLinkerNode (reportObject 0L)
  'Report-Link': {
    title: 'reportLink',
    rows: 1,
    cols: 1,
    common: { borderSize: 0, 'Background Color': WHITE_TRANSPARENT },
    data: false,
    block: () => ({ reportLinker: { reportObject: 0 } }),
  },
  // AlarmCountWidget.java:80-86, AlarmCountNode (alarmObject 0L)
  'Alarm Count': { title: 'alarm', rows: 1, cols: 4, data: false, block: () => ({ alarmCount: { alarmObject: 0 } }) },
}

/** The 23 type ids (Widgets.java order). */
export const TYPE_IDS: readonly string[] = Object.keys(DEFAULTS)

/** The type uses data rows (the CC's DataModelDataHandler: toNode writes `dataHandler`). */
export function typeHasRows(type: string): boolean {
  return DEFAULTS[type]?.data ?? false
}

/**
 * The analysis JSON of a new widget: `AnalysisHandler.toJsonNode(dataModel, true)` of a fresh `DataModel` with the one
 * empty `ChartModel` that `DataModelDataHandler`'s constructor adds (datahandler/DataModelDataHandler.java:145-147;
 * defaults application/Chart/data/DataModel.java:52-61, ChartModel.java:72-93).
 */
export function emptyDataHandler(): RawDataHandler {
  return {
    type: 'AnalysisHandler',
    autoSize: true,
    chartsPerScreen: 2,
    horizontalPies: 3,
    horizontalTables: 3,
    forcedInterval: '',
    dayFilterWeekdays: '',
    dayFilterHolidays: false,
    dayFilterDates: '',
    chartModels: [
      {
        chartId: -1,
        chartName: '',
        chartType: 'LINE',
        minFractionDigits: 2,
        maxFractionDigits: 2,
        groupingInterval: 30,
        height: -1,
        colorMapping: 'GREEN_YELLOW_RED',
        orientation: 'HORIZONTAL',
        filterEnabled: false,
        fixYAxisToZero: false,
        coloringEnabled: false,
        showColumnSums: false,
        showRowSums: false,
        chartData: [],
      },
    ],
  }
}

export interface CreateOptions {
  /** language of the default title (the CC writes it translated); default German */
  locale?: string
  /** grid intervals of the dashboard ("X Axis Grid Interval" / "Y Axis Grid Interval"); default 25 */
  grid?: { x?: number; y?: number }
}

/**
 * A new widget of `type` as the Control Center creates it: the common keys of `Widget.createDefaultNode()`
 * (widget/Widget.java:624-654) with the `WidgetPojo` defaults (config2/WidgetPojo.java fields), the type's overrides,
 * an empty `dataHandler` for types with rows and the type block with its defaults. Unknown types get only the common
 * keys.
 */
export function createWidget(type: string, uuid: number, x: number, y: number, options: CreateOptions = {}): RawWidget {
  const d = DEFAULTS[type]
  const gridX = positive(options.grid?.x) ?? DEFAULT_GRID
  const gridY = positive(options.grid?.y) ?? DEFAULT_GRID
  const lang = options.locale?.toLowerCase().startsWith('en') ? 'en' : 'de'
  const title = !d ? type : d.title === CHART_TITLE ? 'new Chart Widget' : TITLES[lang][d.title] ?? d.title
  /* Widget.createDefaultNode, in its key order; WidgetPojo defaults: */
  const widget: RawWidget = {
    uuid,
    WidgetType: type,
    title,
    tooltip: '',
    'Background Color': WHITE, // Color.web("#ffffff")
    'Font Color': BLACK,
    fontSize: 13,
    fontWeight: 'NORMAL',
    fontPosture: 'REGULAR',
    fontUnderlined: false,
    'Title Position': 'CENTER',
    borderSize: 0.2, // new BorderWidths(0.2).getTop()
    borderColor: BLACK,
    cornerRadius: 0,
    shadow: true,
    layer: 1,
    width: d ? gridX * d.cols : 250, // unknown types: Size.DEFAULT = new Size(150, 250)
    height: d ? gridY * d.rows : 150,
    decimals: 2, // WidgetPojo.DEFAULT_DECIMALS
    showValue: true,
    xPos: x,
    yPos: y,
    fixedTimeframe: false,
    forcedTimeFrame: 'PT0S',
  }
  if (!d) return widget
  Object.assign(widget, d.common ?? {})
  /* decimalsAuto is written only if set (Widget.java:650-652): only the Gauge sets it */
  if (d.data) widget.dataHandler = emptyDataHandler()
  if (d.block) Object.assign(widget, d.block())
  return widget
}

function positive(v: unknown): number | null {
  return typeof v === 'number' && Number.isFinite(v) && v > 0 ? v : null
}
