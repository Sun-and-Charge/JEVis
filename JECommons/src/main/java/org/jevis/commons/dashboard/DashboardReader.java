/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dashboard;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.chart.*;
import org.jevis.commons.object.ValueFormat;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.unit.UnitManager;
import org.jevis.commons.ws.json.JsonUnit;

import javax.measure.Prefix;
import javax.measure.Unit;
import javax.measure.spi.ServiceProvider;
import java.util.*;

/**
 * Reads a dashboard of the Control Center into a {@link DashboardDefinition}: port of {@code ConfigManager.loadDashboard},
 * {@code WidgetPojo}, the configuration nodes of the widgets and the data handler of {@code DataModelDataHandler}
 * (current "AnalysisHandler" format read by {@link AnalysisReader}, legacy "SimpleDataHandler" converted like
 * {@code createChartModelFromOldDataModel}).
 * <p>
 * Old dashboard files use other names for some fields ({@code JsonNames.NameFix}); both names are accepted. JavaFX
 * colors ({@code 0xRRGGBBAA}) become CSS colors. Malformed values fall back to the defaults of the Control Center
 * instead of failing the whole dashboard. Unknown widget types are kept with an empty configuration.
 */
public class DashboardReader {
    public static final String DASHBOARD_CLASS = "Dashboard Analysis";
    public static final String DASHBOARD_COLLECTION_CLASS = "Dashboard Collection";
    public static final String DATA_MODEL_FILE_ATTRIBUTE = "Data Model File";
    public static final String BACKGROUND_ATTRIBUTE = "Background";
    public static final String ANALYSIS_HANDLER_TYPE = "AnalysisHandler";
    public static final String LEGACY_HANDLER_TYPE = "SimpleDataHandler";
    private static final Logger logger = LogManager.getLogger(DashboardReader.class);
    private static final List<String> POSITIONS = Arrays.asList("TOP_LEFT", "TOP_CENTER", "TOP_RIGHT", "CENTER_LEFT",
            "CENTER", "CENTER_RIGHT", "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT", "BASELINE_LEFT", "BASELINE_CENTER",
            "BASELINE_RIGHT");
    private static final List<String> FONT_WEIGHTS = Arrays.asList("THIN", "EXTRA_LIGHT", "LIGHT", "NORMAL", "MEDIUM",
            "SEMI_BOLD", "BOLD", "EXTRA_BOLD", "BLACK");
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalysisReader analysisReader = new AnalysisReader();

    public DashboardReader() {
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Reads the dashboard file of the object (latest sample of "Data Model File") and resolves rows and links with
     * the data source of the object. A dashboard without (readable) file has no widgets.
     */
    public DashboardDefinition read(JEVisObject dashboardObject) throws JEVisException {
        JEVisDataSource ds = dashboardObject.getDataSource();
        JsonNode root = null;
        try {
            JEVisAttribute fileAttribute = dashboardObject.getAttribute(DATA_MODEL_FILE_ATTRIBUTE);
            JEVisSample latest = fileAttribute != null ? fileAttribute.getLatestSample() : null;
            if (latest != null) {
                JEVisFile file = latest.getValueAsFile();
                if (file != null && file.getBytes() != null) root = mapper.readTree(file.getBytes());
            }
        } catch (Exception e) {
            logger.error("Could not read dashboard file of {}", dashboardObject.getID(), e);
        }
        DashboardDefinition definition = readDashboardFile(root, ds);
        definition.setId(dashboardObject.getID());
        try {
            JEVisAttribute background = dashboardObject.getAttribute(BACKGROUND_ATTRIBUTE);
            definition.setBackgroundImage(background != null && background.hasSample());
        } catch (Exception e) {
            logger.debug("Could not check the background of {}", dashboardObject.getID(), e);
        }
        resolve(ds, definition);
        return definition;
    }

    /**
     * Parses a dashboard file without data source (rows and links are not resolved).
     */
    public DashboardDefinition readDashboardFile(JsonNode root) {
        return readDashboardFile(root, null);
    }

    /**
     * Parses a dashboard file.
     *
     * @param ds data source for the legacy data handler (objects, display units), may be {@code null}
     */
    public DashboardDefinition readDashboardFile(JsonNode root, JEVisDataSource ds) {
        DashboardDefinition definition = new DashboardDefinition();
        if (root == null || !root.isObject()) return definition;

        definition.setVersion(text(root, "1.0", "version"));
        definition.setWidth(number(root, 1863d, "width"));
        definition.setHeight(number(root, 886d, "height"));
        definition.setBackgroundColor(color(field(root, "Dash Board Color", "dashboardColor"), "#f4f4f4"));
        definition.setBackgroundMode(backgroundMode(text(root, "default", "backgroundMode")));
        definition.setZoom(number(root, 1d, "Zoom Factor", "zoom"));
        definition.setUpdateRate(integer(root, 900, "Update Rate", "updateInterval", "Update Rat"));
        String period = text(root, null, "Data Period", "dataPeriod");
        /* ConfigManager: a time frame that is not in the list is missing, DashboardControl then uses the day */
        definition.setTimeFrame(period != null && !period.trim().isEmpty() ? period.trim() : DashboardTimeFrames.DAY);

        JsonNode widgets = field(root, "Widget", "widget");
        List<WidgetDefinition> list = new ArrayList<>();
        if (widgets != null && widgets.isArray()) {
            for (JsonNode node : widgets) {
                if (node == null || !node.isObject()) continue;
                try {
                    list.add(readWidget(node, ds));
                } catch (Exception e) {
                    logger.error("Could not read widget {}", node.get("uuid"), e);
                }
            }
        }
        /* ConfigManager.createWidgets: widgets of old dashboards without uuid get the next free one */
        int max = 0;
        for (WidgetDefinition widget : list) max = Math.max(max, widget.getUuid());
        for (WidgetDefinition widget : list) {
            if (widget.getUuid() <= 0) widget.setUuid(++max);
        }
        List<WidgetDefinition> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingInt(WidgetDefinition::getLayer));
        definition.setWidgets(sorted);
        return definition;
    }

    /**
     * Resolves the rows (readable flag, object name) and the targets of links (readable flag).
     */
    public void resolve(JEVisDataSource ds, DashboardDefinition definition) {
        for (WidgetDefinition widget : definition.getWidgets()) {
            analysisReader.resolveRows(ds, widget.getData());
            if (!WidgetDefinition.TYPE_CHART.equals(widget.getType())) {
                for (RowDefinition row : widget.getRows()) {
                    row.setDecimalDigits(effectiveDecimals(ds, widget, row));
                }
            }
            Object link = widget.getConfig().get("link");
            if (link instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) link;
                Object id = map.get("id");
                boolean readable = false;
                if (id instanceof Long) {
                    try {
                        readable = ds.getObject((Long) id) != null;
                    } catch (Exception e) {
                        logger.debug("Could not get link target {}", id, e);
                    }
                }
                map.put("readable", readable);
            }
        }
    }

    /**
     * {@code Widget.getDecimals(ChartDataRow)}: the decimal places of the row if set, else those of the data object
     * ({@code ValueFormat}) if the widget uses the decimals of the data rows, else those of the widget; -1 for rows
     * that are not readable.
     */
    static int effectiveDecimals(JEVisDataSource ds, WidgetDefinition widget, RowDefinition row) {
        if (row.getDecimalDigits() > -1) return row.getDecimalDigits();
        if (Boolean.FALSE.equals(row.getReadable())) return -1;
        if (!widget.isDecimalsAuto()) return widget.getDecimals();
        try {
            JEVisObject object = ds != null ? ds.getObject(row.getObjectId()) : null;
            return object != null ? ValueFormat.decimals(object) : -1;
        } catch (Exception e) {
            logger.debug("Could not get the decimal places of row {}", row.getKey(), e);
            return -1;
        }
    }

    // ---------------------------------------------------------------- widgets

    WidgetDefinition readWidget(JsonNode node, JEVisDataSource ds) {
        WidgetDefinition widget = new WidgetDefinition();
        widget.setUuid(integer(node, -1, "uuid"));
        widget.setType(text(node, "", "WidgetType", "widgetType"));
        widget.setTitle(text(node, "Title", "title"));
        widget.setTooltip(text(node, "", "tooltip"));
        widget.setTitlePosition(titlePosition(text(node, null, "Title Position", "titlePosition")));
        widget.setBackground(color(field(node, "Background Color", "backgroundColor"), "#ffffff"));
        widget.setFontColor(color(field(node, "Font Color", "fontColor"), "#000000"));
        JsonNode height = field(node, "height");
        JsonNode width = field(node, "width");
        if (height != null && width != null) {
            widget.setHeight(height.asDouble(25d));
            widget.setWidth(width.asDouble(25d));
        }
        widget.setFontWeight(fontWeight(text(node, null, "fontWeight", "Font Weight")));
        widget.setItalic("ITALIC".equalsIgnoreCase(text(node, "", "fontPosture")));
        widget.setUnderline(bool(node, false, "fontUnderlined", "Font Underlined"));
        widget.setFontSize(number(node, 13d, "fontSize"));
        JsonNode x = field(node, "xPos");
        JsonNode y = field(node, "yPos");
        if (x != null && y != null) {
            widget.setX(x.asDouble(0d));
            widget.setY(y.asDouble(0d));
        }
        widget.setShadow(bool(node, true, "shadow"));
        widget.setShowValue(bool(node, true, "showValue"));
        widget.setBorderSize(number(node, 0.2d, "borderSize"));
        widget.setBorderColor(color(field(node, "borderColor"), "#000000"));
        widget.setCornerRadius(Math.max(0d, number(node, 0d, "cornerRadius")));
        int decimals = integer(node, 2, "decimals");
        widget.setDecimals(decimals);
        JsonNode decimalsAuto = field(node, "decimalsAuto");
        /* WidgetPojo.isDecimalsAuto (#1194) */
        widget.setDecimalsAuto(decimalsAuto != null ? decimalsAuto.asBoolean() : decimals == 2);
        widget.setLayer(integer(node, 1, "layer"));
        widget.setFixedTimeFrame(bool(node, false, "fixedTimeframe"));

        readDataHandler(widget, field(node, "dataHandler"), ds);
        widget.setConfig(config(widget, node));
        return widget;
    }

    private void readDataHandler(WidgetDefinition widget, JsonNode handler, JEVisDataSource ds) {
        if (handler == null || !handler.isObject()) return;
        String type = text(handler, "", "type");
        if (ANALYSIS_HANDLER_TYPE.equals(type)) {
            AnalysisDefinition data = analysisReader.readAnalysisFile(handler);
            widget.setData(data);
            String forced = data.getForcedInterval();
            if (forced != null && !forced.trim().isEmpty()) widget.setTimeFrame(forced.trim());
        } else if (LEGACY_HANDLER_TYPE.equals(type)) {
            readLegacyDataHandler(widget, handler, ds);
        }
    }

    /**
     * Port of {@code DataModelDataHandler.createChartModelFromOldDataModel}: one chart model with the data points.
     * With a data source, points whose object is readable but has no such attribute are skipped and points without
     * unit get the display unit of the attribute (like the Control Center); points of unreadable objects are kept
     * (shown as not readable).
     */
    private void readLegacyDataHandler(WidgetDefinition widget, JsonNode handler, JEVisDataSource ds) {
        AnalysisDefinition definition = analysisReader.readAnalysisFile(convertLegacyDataHandler(handler, ds));
        widget.setData(definition);
        String forced = text(handler, "", "forcedInterval");
        if (forced != null && !forced.trim().isEmpty()) widget.setTimeFrame(forced.trim());
    }

    /**
     * Converts a legacy "SimpleDataHandler" node of a widget into the current "AnalysisHandler" format, as the Control
     * Center writes it on its next save ({@code DataModelDataHandler.createChartModelFromOldDataModel} and
     * {@code AnalysisHandler.toJsonNode(model, true)}): one chart model with the data points, rows with their
     * aggregation and manipulation, the unit of the point (else, with a data source, the display unit of the
     * attribute) and the {@code forcedInterval} of the handler. Points whose object is readable but has no such
     * attribute are left out; points of unreadable objects are kept.
     *
     * @param handler the "dataHandler" node of a widget with {@code "type": "SimpleDataHandler"}
     * @param ds      data source for the objects and display units, may be {@code null}
     */
    public ObjectNode convertLegacyDataHandler(JsonNode handler, JEVisDataSource ds) {
        ObjectNode converted = mapper.createObjectNode();
        converted.put("type", ANALYSIS_HANDLER_TYPE);
        ArrayNode chartData = mapper.createArrayNode();
        JsonNode data = field(handler, "data");
        if (data != null && data.isArray()) {
            for (JsonNode point : data) {
                long objectId = longValue(point, -1L, "objectID");
                long cleanId = longValue(point, -1L, "cleanObjectID");
                long id = cleanId > 0 ? cleanId : objectId;
                String attributeName = text(point, AnalysisReader.VALUE_ATTRIBUTE_NAME, "attribute");
                JEVisUnit unit = null;
                String unitString = text(point, null, "unit");
                if (unitString != null) unit = parseUnit(unitString);
                if (ds != null) {
                    try {
                        JEVisObject object = ds.getObject(id);
                        if (object != null) {
                            JEVisAttribute attribute = object.getAttribute(attributeName);
                            if (attribute == null) {
                                logger.warn("Attribute does not exist: {}", attributeName);
                                continue;
                            }
                            if (unit == null) unit = attribute.getDisplayUnit();
                        }
                    } catch (Exception e) {
                        logger.debug("Could not resolve data point {}", id, e);
                    }
                }
                ObjectNode row = mapper.createObjectNode();
                row.put("id", id);
                row.put("attributeString", attributeName);
                row.put("name", text(point, "", "name"));
                /* JavaFX Color.toString(), as the Control Center writes the colour of a row */
                row.put("color", "0x" + legacyColor(field(point, "color")).substring(1) + "ff");
                row.put("axis", integer(point, 0, "axis"));
                row.put("calculation", bool(point, false, "enpi"));
                row.put("bubbleType", text(point, "NONE", "bubbleType"));
                row.put("chartType", text(point, "LINE", "chartType"));
                row.put("decimalDigits", integer(point, -1, "decimalDigits"));
                row.put("css", text(point, "", "customCSS"));
                row.put("aggregationPeriod", text(point, "NONE", "aggregationPeriod"));
                row.put("manipulationMode", text(point, "NONE", "manipulationMode"));
                if (unit != null) {
                    /* ChartData.setUnit / AnalysisHandler.toJsonNode */
                    RowDefinition withUnit = new RowDefinition();
                    withUnit.setUnit(unit);
                    row.put("unitPrefix", withUnit.getUnitPrefix());
                    row.put("unitFormula", withUnit.getUnitFormula());
                    row.put("unitLabel", withUnit.getUnitLabel());
                }
                chartData.add(row);
            }
        }
        ObjectNode chartModel = mapper.createObjectNode();
        chartModel.set("chartData", chartData);
        converted.putArray("chartModels").add(chartModel);
        converted.put("forcedInterval", text(handler, "", "forcedInterval"));
        return converted;
    }

    /**
     * Port of {@code ChartData.setUnit(String)}.
     */
    static JEVisUnit parseUnit(String unitString) {
        try {
            Unit<?> unit = ServiceProvider.current().getFormatService().getUnitFormat().parse(unitString);
            return new JEVisUnitImp(unit);
        } catch (Exception e) {
            String prefix = "";
            if (unitString.length() > 1 && !unitString.equals("m²") && !unitString.equals("m³") && !unitString.equals("min")) {
                Prefix p = UnitManager.getInstance().getPrefixFromShort(unitString.substring(0, 1));
                if (p != null) prefix = p.toString();
            }
            JsonUnit jsonUnit = new JsonUnit();
            jsonUnit.setLabel(unitString);
            jsonUnit.setFormula(unitString);
            jsonUnit.setPrefix(prefix);
            return new JEVisUnitImp(jsonUnit);
        }
    }

    /**
     * Colors of the legacy data points: a color string or a serialised JavaFX color (red, green, blue, opacity).
     */
    private static String legacyColor(JsonNode node) {
        if (node == null || node.isNull()) return WebColor.DEFAULT_ROW_COLOR;
        if (node.isTextual()) return WebColor.toHex(node.asText(), WebColor.DEFAULT_ROW_COLOR);
        if (node.isObject() && node.has("red") && node.has("green") && node.has("blue")) {
            int r = (int) Math.round(node.get("red").asDouble() * 255d);
            int g = (int) Math.round(node.get("green").asDouble() * 255d);
            int b = (int) Math.round(node.get("blue").asDouble() * 255d);
            return String.format(Locale.ROOT, "#%02x%02x%02x", clamp(r), clamp(g), clamp(b));
        }
        return WebColor.DEFAULT_ROW_COLOR;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ---------------------------------------------------------------- type specific configuration

    private Map<String, Object> config(WidgetDefinition widget, JsonNode node) {
        Map<String, Object> config = new LinkedHashMap<>();
        String type = widget.getType();
        if (type == null) return config;
        switch (type) {
            case WidgetDefinition.TYPE_VALUE:
                config.put("limit", limit(field(node, "limit")));
                config.put("percent", percent(field(node, "percent")));
                config.put("dimIncompletePeriod", bool(node, false, "dimIncompletePeriod"));
                break;
            case WidgetDefinition.TYPE_GAUGE: {
                JsonNode design = object(node, "gaugeDesign");
                config.put("minimum", number(design, 0d, "minimum"));
                config.put("maximum", number(design, 0d, "maximum"));
                config.put("inPercent", bool(design, false, "inPercent"));
                config.put("showTitle", bool(design, true, "showTitle"));
                config.put("showUnit", bool(design, true, "showUnit"));
                config.put("showValue", bool(design, true, "showValue"));
                List<Map<String, Object>> sections = new ArrayList<>();
                JsonNode list = field(design, "sections");
                if (list != null && list.isArray()) {
                    for (JsonNode section : list) {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("start", finite(number(section, 0d, "start")));
                        s.put("end", finite(number(section, 0d, "end")));
                        s.put("color", color(field(section, "color"), "#000000"));
                        sections.add(s);
                    }
                }
                config.put("sections", sections);
                break;
            }
            case WidgetDefinition.TYPE_LINEAR_GAUGE: {
                JsonNode design = object(node, "linearGaugeDesign");
                config.put("minimum", number(design, 0d, "minimum"));
                config.put("maximum", number(design, 0d, "maximum"));
                config.put("inPercent", bool(design, false, "inPercent"));
                config.put("color", color(field(design, "color"), "#00008b"));
                config.put("borderColor", color(field(design, "colorBorder"), "#000000"));
                config.put("majorTickStep", number(design, 5d, "majorTickStep"));
                config.put("showTitle", bool(design, true, "showTitle"));
                config.put("showUnit", bool(design, true, "showUnit"));
                config.put("showValue", bool(design, true, "showValue"));
                break;
            }
            case WidgetDefinition.TYPE_BATTERY: {
                JsonNode design = object(node, "BatteryDesign");
                config.put("minimum", number(design, 0d, "minimum"));
                config.put("maximum", number(design, 0d, "maximum"));
                config.put("color", color(field(design, "color"), "#008000"));
                config.put("showValue", bool(design, true, "showValue"));
                break;
            }
            case WidgetDefinition.TYPE_SHAPE: {
                JsonNode design = object(node, "shapeDesign");
                config.put("shape", "ELLIPSE".equals(text(design, "RECTANGLE", "shape")) ? "ELLIPSE" : "RECTANGLE");
                config.put("minColor", color(field(design, "minColor"), "#008000"));
                config.put("maxColor", color(field(design, "maxColor"), "#ff0000"));
                config.put("minValue", number(design, 0d, "minValue"));
                config.put("maxValue", number(design, 100d, "maxValue"));
                break;
            }
            case WidgetDefinition.TYPE_ARROW: {
                JsonNode design = object(node, "arrow");
                String orientation = text(design, "LEFT_RIGHT", "orientation");
                if (!Arrays.asList("LEFT_RIGHT", "RIGHT_LEFT", "TOP_BOTTOM", "BOTTOM_TOP").contains(orientation)) {
                    orientation = "LEFT_RIGHT";
                }
                config.put("orientation", orientation);
                config.put("shape", "LINE".equals(text(design, "ARROW", "shape")) ? "LINE" : "ARROW");
                break;
            }
            case WidgetDefinition.TYPE_NET_GRAPH: {
                JsonNode design = object(node, "NetGraph");
                config.put("minimum", 0d);
                config.put("maximum", number(design, 35d, "max"));
                config.put("inPercent", bool(design, false, "inPercent"));
                List<Map<String, Object>> rows = new ArrayList<>();
                JsonNode list = field(design, "netGraphDataRows");
                if (list != null && list.isArray()) {
                    for (JsonNode item : list) {
                        String key = keyOf(widget, longValue(item, -1L, "id"));
                        if (key == null) continue;
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("key", key);
                        r.put("minimum", finite(number(item, 0d, "min")));
                        r.put("maximum", finite(number(item, 0d, "max")));
                        rows.add(r);
                    }
                }
                config.put("rows", rows);
                break;
            }
            case WidgetDefinition.TYPE_SANKEY: {
                JsonNode design = object(node, "Sankey");
                config.put("showValue", bool(design, true, "showValue"));
                config.put("showPercent", bool(design, true, "showPercent"));
                List<Map<String, Object>> nodes = new ArrayList<>();
                JsonNode list = field(design, "sankeyDataRows");
                if (list != null && list.isArray()) {
                    for (JsonNode item : list) {
                        String key = keyOf(widget, longValue(item, -1L, "id"));
                        if (key == null) continue;
                        List<String> children = new ArrayList<>();
                        JsonNode childList = field(item, "children");
                        if (childList != null && childList.isArray()) {
                            for (JsonNode child : childList) {
                                String childKey = keyOf(widget, child.asLong(-1L));
                                if (childKey != null) children.add(childKey);
                            }
                        }
                        Map<String, Object> n = new LinkedHashMap<>();
                        n.put("key", key);
                        n.put("children", children);
                        nodes.add(n);
                    }
                }
                config.put("nodes", nodes);
                break;
            }
            case WidgetDefinition.TYPE_TIME_FRAME: {
                JsonNode design = object(node, "Time Frame");
                JsonNode selected = object(design, "selectedWidget");
                int source = integer(selected, -1, "id");
                config.put("source", source > 0 ? String.valueOf(source) : null);
                String start = text(selected, "NONE", "start");
                config.put("start", start != null && start.startsWith("PERIOD") ? "PERIOD_FROM" : "NONE");
                String end = text(selected, "NONE", "end");
                config.put("end", end == null ? "NONE" : end.startsWith("PERIOD") ? "PERIOD_UNTIL" : "LAST_TS".equals(end) ? "LAST_TS" : "NONE");
                config.put("format", text(design, "yyyy.MM.dd HH:mm", "format"));
                config.put("count", bool(selected, false, "isCount"));
                break;
            }
            case WidgetDefinition.TYPE_ALARM_COUNT: {
                long id = longValue(object(node, "alarmCount"), 0L, "alarmObject");
                config.put("objectId", id > 0 ? id : null);
                break;
            }
            case WidgetDefinition.TYPE_IMAGE: {
                JsonNode image = object(node, "image");
                long id = longValue(image, -1L, "objectID");
                config.put("objectId", id > 0 ? id : null);
                long dashboard = longValue(object(image, "dashboardLinker"), 0L, "dashboardObject");
                JsonNode analysisNode = object(image, "analysisLinker");
                long analysis = longValue(analysisNode, 0L, "graphAnalysisObject");
                Map<String, Object> link = null;
                if (dashboard > 0) {
                    link = link("DASHBOARD", dashboard, null);
                } else if (analysis > 0) {
                    link = link("ANALYSIS", analysis, text(analysisNode, null, "forcedInterval"));
                }
                config.put("link", link);
                break;
            }
            case WidgetDefinition.TYPE_ANALYSIS_LINK: {
                JsonNode linker = object(node, "analysisLinker");
                config.put("link", link("ANALYSIS", longValue(linker, 0L, "graphAnalysisObject"),
                        text(linker, null, "forcedInterval")));
                break;
            }
            case WidgetDefinition.TYPE_DASHBOARD_LINK:
                config.put("link", link("DASHBOARD", longValue(object(node, "dashboardLinker"), 0L, "dashboardObject"), null));
                break;
            case WidgetDefinition.TYPE_REPORT_LINK:
                config.put("link", link("REPORT", longValue(object(node, "reportLinker"), 0L, "reportObject"), null));
                break;
            case WidgetDefinition.TYPE_SLIDER: {
                JsonNode minMax = field(node, "minMax");
                if (minMax != null && minMax.isObject()) {
                    if (minMax.has("minimum")) config.put("minimum", finite(minMax.get("minimum").asDouble()));
                    if (minMax.has("maximum")) config.put("maximum", finite(minMax.get("maximum").asDouble()));
                }
                break;
            }
            case WidgetDefinition.TYPE_PLUS_MINUS: {
                JsonNode increment = field(node, "minMax");
                if (increment != null && increment.isObject() && increment.has("Increment")) {
                    config.put("increment", finite(increment.get("Increment").asDouble(0.1d)));
                }
                break;
            }
            default:
                break;
        }
        return config;
    }

    /**
     * {@code Limit}: dynamic if a source widget is set (offsets), static otherwise (values).
     */
    private Map<String, Object> limit(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        int source = integer(node, -1, "source");
        boolean dynamic = source > 0;
        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("source", dynamic ? String.valueOf(source) : null);
        limit.put("upper", limitSide(node, "upper", dynamic, "#008000"));
        limit.put("lower", limitSide(node, "lower", dynamic, "#ff0000"));
        return limit;
    }

    private Map<String, Object> limitSide(JsonNode node, String side, boolean dynamic, String defaultColor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", bool(node, false, side + "Limit"));
        map.put("value", dynamic ? null : finite(number(node, Double.NaN, side + "LimitValue")));
        map.put("color", color(field(node, side + "LimitColor"), defaultColor));
        map.put("offset", dynamic ? finite(number(node, 1d, side + "LimitOffset")) : null);
        return map;
    }

    private Map<String, Object> percent(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        int source = integer(node, -1, "source");
        if (source <= 0) return null;
        Map<String, Object> percent = new LinkedHashMap<>();
        percent.put("source", String.valueOf(source));
        percent.put("diff", bool(node, false, "diff"));
        percent.put("minFractionDigits", integer(node, 0, "minFracDigits"));
        percent.put("maxFractionDigits", integer(node, 0, "maxFracDigits"));
        return percent;
    }

    private static Map<String, Object> link(String kind, long id, String timeFrame) {
        if (id <= 0) return null;
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("kind", kind);
        link.put("id", id);
        link.put("timeFrame", timeFrame != null && !timeFrame.trim().isEmpty() ? timeFrame.trim() : null);
        link.put("readable", false);
        return link;
    }

    /**
     * @return the key of the first row of the widget with this object id, {@code null} if none
     */
    private static String keyOf(WidgetDefinition widget, long objectId) {
        for (RowDefinition row : widget.getRows()) {
            if (row.getObjectId() == objectId) return row.getKey();
        }
        return null;
    }

    // ---------------------------------------------------------------- values

    private static String backgroundMode(String mode) {
        if ("repeat".equalsIgnoreCase(mode)) return "REPEAT";
        if ("stretch".equalsIgnoreCase(mode)) return "STRETCH";
        return "DEFAULT";
    }

    /**
     * {@code WidgetPojo}: old dashboards have only left/right/center.
     */
    static String titlePosition(String value) {
        if (value == null) return "CENTER";
        if (value.equalsIgnoreCase("left")) return "CENTER_LEFT";
        if (value.equalsIgnoreCase("right")) return "CENTER_RIGHT";
        return POSITIONS.contains(value) ? value : "CENTER";
    }

    /**
     * {@code FontWeight}: stored as enum name (e.g. {@code SEMI_BOLD}), older files may have the weight name
     * ({@code "Semi Bold"}).
     */
    static String fontWeight(String value) {
        if (value == null) return "NORMAL";
        String name = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return FONT_WEIGHTS.contains(name) ? name : "NORMAL";
    }

    private static String color(JsonNode node, String fallback) {
        if (node == null || node.isNull()) return fallback;
        return WebColor.toCss(node.asText(), fallback);
    }

    private static Double finite(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
    }

    /**
     * @return the first of the fields that is present and not null
     */
    private static JsonNode field(JsonNode node, String... names) {
        if (node == null) return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) return value;
        }
        return null;
    }

    private static JsonNode object(JsonNode node, String name) {
        JsonNode value = field(node, name);
        return value != null && value.isObject() ? value : null;
    }

    private static String text(JsonNode node, String fallback, String... names) {
        JsonNode value = field(node, names);
        return value != null ? value.asText() : fallback;
    }

    private static boolean bool(JsonNode node, boolean fallback, String... names) {
        JsonNode value = field(node, names);
        return value != null ? value.asBoolean(fallback) : fallback;
    }

    private static int integer(JsonNode node, int fallback, String... names) {
        JsonNode value = field(node, names);
        return value != null ? value.asInt(fallback) : fallback;
    }

    private static long longValue(JsonNode node, long fallback, String... names) {
        JsonNode value = field(node, names);
        return value != null ? value.asLong(fallback) : fallback;
    }

    private static double number(JsonNode node, double fallback, String... names) {
        JsonNode value = field(node, names);
        return value != null ? value.asDouble(fallback) : fallback;
    }
}
