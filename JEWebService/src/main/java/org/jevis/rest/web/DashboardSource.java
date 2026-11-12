/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.commons.chart.AnalysisChartType;
import org.jevis.commons.dashboard.DashboardReader;
import org.jevis.commons.dashboard.DashboardTimeFrames;
import org.jevis.commons.dashboard.WidgetDefinition;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.ws.json.JsonObject;

import java.util.*;
import java.util.regex.Pattern;

/**
 * The dashboard JSON as the Control Center stores it ("source", see docs/WEB-API.md, stage E4): the empty dashboard,
 * the normalisation of a stored file and the check of a source sent by the web app.
 * <p>
 * The source is checked and stored as a tree: keys the check does not know stay as sent, so every key of every
 * widget survives a web save. Colours are stored as JavaFX {@code Color.toString()} ({@code 0xrrggbbaa}). Objects the
 * user may not read (data rows, link targets, alarm and image objects) are accepted only if the widget with the same
 * uuid of the stored source already has them.
 */
final class DashboardSource {

    static final String WIDGETS = "Widget";
    static final String TYPE = "WidgetType";
    static final String UUID = "uuid";
    static final String DATA_HANDLER = "dataHandler";
    static final String DATA_PERIOD = "Data Period";
    /**
     * The widget types registered in the Control Center ({@code Widgets.FACTORIES}, the widgets' {@code WIDGET_ID})
     */
    static final List<String> TYPES = Collections.unmodifiableList(Arrays.asList(WidgetDefinition.TYPE_CHART,
            WidgetDefinition.TYPE_PIE, WidgetDefinition.TYPE_TABLE, WidgetDefinition.TYPE_VALUE,
            WidgetDefinition.TYPE_STRING_VALUE, WidgetDefinition.TYPE_VALUE_EDITOR, WidgetDefinition.TYPE_TOGGLE_SWITCH,
            WidgetDefinition.TYPE_SLIDER, WidgetDefinition.TYPE_PLUS_MINUS, WidgetDefinition.TYPE_GAUGE,
            WidgetDefinition.TYPE_LINEAR_GAUGE, WidgetDefinition.TYPE_BATTERY, WidgetDefinition.TYPE_SHAPE,
            WidgetDefinition.TYPE_NET_GRAPH, WidgetDefinition.TYPE_SANKEY, WidgetDefinition.TYPE_TIME_FRAME,
            WidgetDefinition.TYPE_TITLE, WidgetDefinition.TYPE_ARROW, WidgetDefinition.TYPE_IMAGE,
            WidgetDefinition.TYPE_ANALYSIS_LINK, WidgetDefinition.TYPE_DASHBOARD_LINK, WidgetDefinition.TYPE_REPORT_LINK,
            WidgetDefinition.TYPE_ALARM_COUNT));
    static final List<String> BACKGROUND_MODES = Collections.unmodifiableList(Arrays.asList("default", "repeat", "stretch"));
    static final List<String> FONT_WEIGHTS = Collections.unmodifiableList(Arrays.asList("THIN", "EXTRA_LIGHT", "LIGHT",
            "NORMAL", "MEDIUM", "SEMI_BOLD", "BOLD", "EXTRA_BOLD", "BLACK"));
    static final List<String> FONT_POSTURES = Collections.unmodifiableList(Arrays.asList("REGULAR", "ITALIC"));
    static final List<String> POSITIONS = Collections.unmodifiableList(Arrays.asList("TOP_LEFT", "TOP_CENTER",
            "TOP_RIGHT", "CENTER_LEFT", "CENTER", "CENTER_RIGHT", "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT",
            "BASELINE_LEFT", "BASELINE_CENTER", "BASELINE_RIGHT"));
    static final double[] FIT_ZOOMS = {97d, 98d, 99d};
    static final int MAX_WIDGETS = 1000;
    static final int MAX_TEXT_LENGTH = 10000;
    private static final Pattern COLOR = Pattern.compile("(#|0x)([0-9a-fA-F]{6})([0-9a-fA-F]{2})?");
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final WebEdit.Store store;
    private final ResourceWebEditDashboard.Engine engine;
    private final ObjectNode stored;
    private final List<String> messages;
    private final Map<Integer, ObjectNode> storedWidgets = new HashMap<>();

    /**
     * @param stored the normalised stored source of the dashboard ({@link #normalize}), null for a new one
     */
    DashboardSource(WebEdit.Store store, ResourceWebEditDashboard.Engine engine, ObjectNode stored, List<String> messages) {
        this.store = store;
        this.engine = engine;
        this.stored = stored;
        this.messages = messages;
        JsonNode widgets = stored != null ? stored.get(WIDGETS) : null;
        if (widgets != null && widgets.isArray()) {
            for (JsonNode widget : widgets) {
                if (widget.isObject() && widget.path(UUID).canConvertToInt()) {
                    storedWidgets.putIfAbsent(widget.path(UUID).asInt(), (ObjectNode) widget);
                }
            }
        }
    }

    // ---------------------------------------------------------------- empty and stored dashboards

    /**
     * @return the dashboard the Control Center saves for a new dashboard without widgets ({@code DashboardControl
     * .selectDashboard(null)}, {@code ConfigManager.toJson}); the update rate is 900 s (the Control Center's
     * {@code DashboardPojo} starts with 10 s, its reader with 900 s)
     */
    static ObjectNode empty() {
        ObjectNode source = defaults();
        source.put("width", 1853d);
        source.put("height", 876d);
        return source;
    }

    /**
     * @return the values {@code ConfigManager.loadDashboard} uses for missing keys, in the order of {@code toJson}
     */
    private static ObjectNode defaults() {
        ObjectNode source = NODES.objectNode();
        source.put("version", "1.2");
        source.put("Dash Board Color", "0xf4f4f4ff");
        source.put("backgroundMode", "default");
        source.put("Snap to Grid", false);
        source.put("Show Grid", false);
        source.put("X Axis Grid Interval", 25d);
        source.put("Y Axis Grid Interval", 25d);
        source.put("Update Rate", 900);
        source.put("Zoom Factor", 1d);
        source.put("width", 1863d);
        source.put("height", 886d);
        source.put(DATA_PERIOD, DashboardTimeFrames.DAY);
        source.putArray(WIDGETS);
        return source;
    }

    /**
     * Normalises a stored file in place as the Control Center reads it: widgets with the legacy "SimpleDataHandler"
     * get the "AnalysisHandler" format, widgets without uuid the next free one ({@code ConfigManager.createWidgets},
     * same order as {@code DashboardReader}).
     */
    static ObjectNode normalize(ObjectNode source, ResourceWebEditDashboard.Engine engine) {
        JsonNode widgets = source.get(WIDGETS);
        if (widgets == null || !widgets.isArray()) return source;
        int max = 0;
        for (JsonNode widget : widgets) {
            if (widget.isObject()) max = Math.max(max, widget.path(UUID).asInt(-1));
        }
        for (JsonNode widget : widgets) {
            if (!widget.isObject()) continue;
            ObjectNode node = (ObjectNode) widget;
            if (node.path(UUID).asInt(-1) <= 0) node.put(UUID, ++max);
            convertLegacy(node, engine);
        }
        return source;
    }

    private static void convertLegacy(ObjectNode widget, ResourceWebEditDashboard.Engine engine) {
        JsonNode handler = widget.get(DATA_HANDLER);
        if (handler != null && handler.isObject()
                && DashboardReader.LEGACY_HANDLER_TYPE.equals(handler.path("type").asText(""))) {
            widget.set(DATA_HANDLER, engine.convertLegacy(handler));
        }
    }

    // ---------------------------------------------------------------- check

    /**
     * Checks the source sent; all problems are added to the messages.
     *
     * @return the source to store: a copy of the one sent with missing top-level keys filled in, colours as
     * {@code 0xrrggbbaa}, legacy data handlers converted, missing uuids assigned and unreadable stored rows as stored
     */
    ObjectNode check(JsonNode sent) {
        if (sent == null || !sent.isObject()) {
            messages.add("Invalid source");
            return empty();
        }
        ObjectNode source = ((ObjectNode) sent).deepCopy();
        ObjectNode defaults = defaults();
        Iterator<Map.Entry<String, JsonNode>> it = defaults.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            JsonNode value = source.get(entry.getKey());
            if (value == null || value.isNull()) source.set(entry.getKey(), entry.getValue());
        }
        if (!source.path("version").isTextual()) messages.add("Invalid version");
        color(source, "Dash Board Color", "Dash Board Color", stored);
        String mode = source.path("backgroundMode").asText(null);
        if (mode == null || !source.get("backgroundMode").isTextual() || !BACKGROUND_MODES.contains(mode)) {
            if (!unchanged(source, stored, "backgroundMode")) messages.add("Invalid backgroundMode (default, repeat, stretch)");
        }
        bool(source, "Snap to Grid", "Snap to Grid");
        bool(source, "Show Grid", "Show Grid");
        number(source, "X Axis Grid Interval", "X Axis Grid Interval", 1, 1000);
        number(source, "Y Axis Grid Interval", "Y Axis Grid Interval", 1, 1000);
        if (!unchanged(source, stored, "Update Rate")) integer(source, "Update Rate", "Update Rate", 1, 86400);
        zoom(source);
        number(source, "width", "width", 100, 20000);
        number(source, "height", "height", 100, 20000);
        JsonNode period = source.get(DATA_PERIOD);
        if (!period.isTextual() || !knownTimeFrame(period.asText())) {
            if (!unchanged(source, stored, DATA_PERIOD)) messages.add("Invalid Data Period: " + period.asText());
        }
        JsonNode widgets = source.get(WIDGETS);
        if (!widgets.isArray()) {
            messages.add("Invalid Widget");
            return source;
        }
        if (widgets.size() > MAX_WIDGETS) {
            messages.add("More than " + MAX_WIDGETS + " widgets");
            return source;
        }
        Set<Integer> uuids = uuids((ArrayNode) widgets);
        int index = 0;
        for (JsonNode widget : widgets) {
            String prefix = "Widget[" + index++ + "]";
            if (!widget.isObject()) {
                messages.add("Invalid " + prefix);
                continue;
            }
            widget((ObjectNode) widget, prefix, uuids);
        }
        return source;
    }

    /**
     * Checks the uuids (integers ≥ 0, unique) and assigns the next free one to widgets without uuid or with 0.
     *
     * @return the uuids of the widgets
     */
    private Set<Integer> uuids(ArrayNode widgets) {
        Set<Integer> uuids = new HashSet<>();
        int max = 0;
        int index = 0;
        for (JsonNode widget : widgets) {
            String prefix = "Widget[" + index++ + "]";
            if (!widget.isObject()) continue;
            JsonNode uuid = widget.get(UUID);
            if (uuid == null || uuid.isNull()) continue;
            if (!uuid.isIntegralNumber() || !uuid.canConvertToInt() || uuid.asInt() < 0) {
                messages.add("Invalid " + prefix + ".uuid");
                continue;
            }
            max = Math.max(max, uuid.asInt());
            if (uuid.asInt() > 0 && !uuids.add(uuid.asInt())) messages.add("Duplicate uuid: " + uuid.asInt());
        }
        for (JsonNode widget : widgets) {
            if (!widget.isObject()) continue;
            JsonNode uuid = widget.get(UUID);
            if (uuid == null || uuid.isNull() || (uuid.isIntegralNumber() && uuid.asInt() == 0)) {
                ((ObjectNode) widget).put(UUID, ++max);
                uuids.add(max);
            }
        }
        return uuids;
    }

    private void widget(ObjectNode widget, String prefix, Set<Integer> uuids) {
        JsonNode typeNode = widget.get(TYPE);
        String type = typeNode != null && typeNode.isTextual() ? typeNode.asText() : null;
        if (type == null || !TYPES.contains(type)) {
            messages.add("Unknown widget type in " + prefix + ": " + (typeNode != null ? typeNode.asText() : null));
        }
        ObjectNode storedWidget = widget.path(UUID).canConvertToInt() ? storedWidgets.get(widget.path(UUID).asInt()) : null;
        text(widget, "title", prefix + ".title");
        text(widget, "tooltip", prefix + ".tooltip");
        color(widget, "Background Color", prefix + ".Background Color", storedWidget);
        color(widget, "Font Color", prefix + ".Font Color", storedWidget);
        color(widget, "borderColor", prefix + ".borderColor", storedWidget);
        number(widget, "fontSize", prefix + ".fontSize", 1, 400, storedWidget);
        oneOf(widget, "fontWeight", prefix + ".fontWeight", FONT_WEIGHTS, storedWidget);
        oneOf(widget, "fontPosture", prefix + ".fontPosture", FONT_POSTURES, storedWidget);
        JsonNode position = widget.get("Title Position");
        if (position != null && !position.isNull() && !(position.isTextual() && (POSITIONS.contains(position.asText())
                || Arrays.asList("left", "right", "center").contains(position.asText().toLowerCase(Locale.ROOT))))) {
            if (!unchanged(widget, storedWidget, "Title Position")) messages.add("Invalid " + prefix + ".Title Position");
        }
        bool(widget, "fontUnderlined", prefix + ".fontUnderlined");
        bool(widget, "shadow", prefix + ".shadow");
        bool(widget, "showValue", prefix + ".showValue");
        bool(widget, "fixedTimeframe", prefix + ".fixedTimeframe");
        bool(widget, "decimalsAuto", prefix + ".decimalsAuto");
        number(widget, "borderSize", prefix + ".borderSize", 0, 100, storedWidget);
        number(widget, "cornerRadius", prefix + ".cornerRadius", 0, 10000, storedWidget);
        number(widget, "xPos", prefix + ".xPos", -1e9, 1e9, storedWidget);
        number(widget, "yPos", prefix + ".yPos", -1e9, 1e9, storedWidget);
        number(widget, "width", prefix + ".width", 10, 20000, storedWidget);
        number(widget, "height", prefix + ".height", 10, 20000, storedWidget);
        integer(widget, "layer", prefix + ".layer", Integer.MIN_VALUE, Integer.MAX_VALUE, storedWidget);
        integer(widget, "decimals", prefix + ".decimals", 0, 20, storedWidget);
        text(widget, "forcedTimeFrame", prefix + ".forcedTimeFrame");

        reference(widget, prefix, uuids, "limit", "source");
        reference(widget, prefix, uuids, "percent", "source");
        reference(widget, prefix, uuids, "Time Frame", "selectedWidget", "id");

        target(widget, storedWidget, prefix, ResourceWebEditAnalysis.CLASS_ANALYSIS, "analysisLinker", "graphAnalysisObject");
        target(widget, storedWidget, prefix, ResourceWebEditDashboard.CLASS_DASHBOARD, "dashboardLinker", "dashboardObject");
        target(widget, storedWidget, prefix, ResourceWebReports.CLASS_REPORT, "reportLinker", "reportObject");
        target(widget, storedWidget, prefix, ResourceWebIso.CLASS_FILE, "image", "objectID");
        target(widget, storedWidget, prefix, ResourceWebEditDashboard.CLASS_DASHBOARD, "image", "dashboardLinker", "dashboardObject");
        target(widget, storedWidget, prefix, ResourceWebEditAnalysis.CLASS_ANALYSIS, "image", "analysisLinker", "graphAnalysisObject");
        target(widget, storedWidget, prefix, null, "alarmCount", "alarmObject");

        dataHandler(widget, storedWidget, prefix);
    }

    // ---------------------------------------------------------------- data rows

    private void dataHandler(ObjectNode widget, ObjectNode storedWidget, String prefix) {
        JsonNode handler = widget.get(DATA_HANDLER);
        if (handler == null || handler.isNull()) return;
        if (!handler.isObject()) {
            messages.add("Invalid " + prefix + ".dataHandler");
            return;
        }
        convertLegacy(widget, engine);
        handler = widget.get(DATA_HANDLER);
        if (!DashboardReader.ANALYSIS_HANDLER_TYPE.equals(handler.path("type").asText(""))) {
            messages.add("Invalid " + prefix + ".dataHandler.type: " + handler.path("type").asText(null));
            return;
        }
        ObjectNode storedHandler = storedWidget != null && storedWidget.get(DATA_HANDLER) instanceof ObjectNode
                ? (ObjectNode) storedWidget.get(DATA_HANDLER) : null;
        JsonNode forced = handler.get("forcedInterval");
        if (forced != null && !forced.isNull()) {
            if (!forced.isTextual() || (!forced.asText().trim().isEmpty() && !knownTimeFrame(forced.asText().trim())
                    && !unchanged(handler, storedHandler, "forcedInterval"))) {
                messages.add("Invalid " + prefix + ".dataHandler.forcedInterval: " + forced.asText());
            }
        }
        JsonNode charts = handler.get("chartModels");
        if (charts == null || charts.isNull()) return;
        if (!charts.isArray()) {
            messages.add("Invalid " + prefix + ".dataHandler.chartModels");
            return;
        }
        List<ObjectNode> storedRows = storedRows(storedHandler);
        int c = 0;
        for (JsonNode chart : charts) {
            String chartPrefix = prefix + ".dataHandler.chartModels[" + c++ + "]";
            if (!chart.isObject()) {
                messages.add("Invalid " + chartPrefix);
                continue;
            }
            enumValue(chart, "chartType", chartPrefix + ".chartType", names(AnalysisChartType.values()));
            JsonNode rows = chart.get("chartData");
            if (rows == null || rows.isNull()) continue;
            if (!rows.isArray()) {
                messages.add("Invalid " + chartPrefix + ".chartData");
                continue;
            }
            ArrayNode checked = NODES.arrayNode();
            int r = 0;
            for (JsonNode row : rows) {
                String rowPrefix = chartPrefix + ".chartData[" + r++ + "]";
                if (!row.isObject()) {
                    messages.add("Invalid " + rowPrefix);
                    continue;
                }
                JsonNode result = row((ObjectNode) row, rowPrefix, storedRows);
                if (result != null) checked.add(result);
            }
            ((ObjectNode) chart).set("chartData", checked);
        }
    }

    private static List<ObjectNode> storedRows(ObjectNode handler) {
        List<ObjectNode> rows = new ArrayList<>();
        JsonNode charts = handler != null ? handler.get("chartModels") : null;
        if (charts == null || !charts.isArray()) return rows;
        for (JsonNode chart : charts) {
            JsonNode data = chart.get("chartData");
            if (data == null || !data.isArray()) continue;
            for (JsonNode row : data) {
                if (row.isObject()) rows.add((ObjectNode) row);
            }
        }
        return rows;
    }

    /**
     * @return the row to store (a stored row the user may not read: as stored), null if it is dropped
     */
    private JsonNode row(ObjectNode row, String prefix, List<ObjectNode> storedRows) {
        JsonNode idNode = row.get("id");
        if (idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
            messages.add("Missing or invalid " + prefix + ".id");
            return row;
        }
        long id = idNode.asLong();
        JsonNode attributeNode = row.get("attributeString");
        if (attributeNode == null || attributeNode.isNull() || (attributeNode.isTextual() && attributeNode.asText().trim().isEmpty())) {
            row.put("attributeString", ResourceWebEditAnalysis.ATTRIBUTE_VALUE);
        } else if (!attributeNode.isTextual()) {
            messages.add("Invalid " + prefix + ".attributeString");
            return row;
        }
        String attribute = row.get("attributeString").asText();
        ObjectNode storedRow = null;
        for (ObjectNode candidate : storedRows) {
            if (candidate.path("id").asLong(Long.MIN_VALUE) == id && attribute.equals(candidate.path("attributeString").asText(""))) {
                storedRow = candidate;
                break;
            }
        }
        JsonObject object;
        try {
            object = WebEdit.readable(store, id);
        } catch (Exception e) {
            object = null;
        }
        if (object == null) {
            if (storedRow != null) return storedRow.deepCopy();
            messages.add("Row " + id + " not found or not readable");
            return null;
        }
        if (!ResourceWebEditAnalysis.ROW_CLASSES.contains(object.getJevisClass())) {
            messages.add("Row " + id + " is no data row (" + object.getJevisClass() + ")");
            return null;
        }
        if (store.classes().type(object.getJevisClass(), attribute) == null) {
            messages.add("Row " + id + " has no attribute " + attribute);
            return null;
        }
        enumValue(row, "chartType", prefix + ".chartType", names(AnalysisChartType.values()));
        enumValue(row, "aggregationPeriod", prefix + ".aggregationPeriod", names(AggregationPeriod.values()));
        enumValue(row, "manipulationMode", prefix + ".manipulationMode", names(ManipulationMode.values()));
        enumValue(row, "bubbleType", prefix + ".bubbleType", ResourceWebEditAnalysis.BUBBLE_TYPES);
        color(row, "color", prefix + ".color", storedRow);
        integer(row, "axis", prefix + ".axis", 0, 1, storedRow);
        integer(row, "decimalDigits", prefix + ".decimalDigits", -1, 20, storedRow);
        text(row, "name", prefix + ".name");
        text(row, "unitPrefix", prefix + ".unitPrefix");
        text(row, "unitFormula", prefix + ".unitFormula");
        text(row, "unitLabel", prefix + ".unitLabel");
        bool(row, "intervalEnabled", prefix + ".intervalEnabled");
        JsonNode calculation = row.get("calculation");
        if (calculation != null && !calculation.isNull() && !calculation.isBoolean()) {
            messages.add("Invalid " + prefix + ".calculation");
        } else if (calculation != null && calculation.asBoolean()) {
            if (row.path("calculationId").asLong(-1L) <= 0) {
                long calculationId = engine.calculationId(id);
                if (calculationId == -1 && storedRow != null && storedRow.path("calculationId").asLong(-1L) > 0) {
                    calculationId = storedRow.path("calculationId").asLong();
                }
                if (calculationId == -1 && storedRow != null && storedRow.path("calculation").asBoolean(false)) {
                    // stored as calculated by the Control Center without a calculation it could find: keep as stored
                    row.put("calculationId", storedRow.path("calculationId").asLong(-1L));
                } else if (calculationId == -1) {
                    messages.add("Row " + id + " is not calculated (no calculation)");
                } else {
                    row.put("calculationId", calculationId);
                }
            }
        } else if (row.has("calculationId")) {
            row.put("calculationId", -1L);
        }
        return row;
    }

    // ---------------------------------------------------------------- references and targets

    /**
     * A reference to another widget of the dashboard (uuid; 0 or negative = none)
     */
    private void reference(ObjectNode widget, String prefix, Set<Integer> uuids, String... path) {
        JsonNode node = at(widget, path);
        if (node == null || node.isNull()) return;
        String key = prefix + "." + String.join(".", path);
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            messages.add("Invalid " + key);
            return;
        }
        if (node.asInt() > 0 && !uuids.contains(node.asInt())) messages.add("Unknown widget in " + key + ": " + node.asInt());
    }

    /**
     * An object a widget links to: readable and of the class (any class for null), unless the stored widget has the
     * same target (number or numeric text; 0 or negative = none)
     */
    private void target(ObjectNode widget, ObjectNode storedWidget, String prefix, String jevisClass, String... path) {
        JsonNode node = at(widget, path);
        if (node == null || node.isNull()) return;
        String key = prefix + "." + String.join(".", path);
        Long id = objectId(node);
        if (id == null) {
            messages.add("Invalid " + key);
            return;
        }
        if (id <= 0) return;
        JsonNode storedNode = storedWidget != null ? at(storedWidget, path) : null;
        if (storedNode != null && id.equals(objectId(storedNode))) return;
        JsonObject object;
        try {
            object = WebEdit.readable(store, id);
        } catch (Exception e) {
            object = null;
        }
        if (object == null) {
            messages.add("Object " + id + " of " + key + " not found or not readable");
        } else if (jevisClass != null && !store.classes().withHeirs(jevisClass).contains(object.getJevisClass())) {
            messages.add("Object " + id + " of " + key + " is no " + jevisClass);
        }
    }

    private static Long objectId(JsonNode node) {
        if (node.isIntegralNumber() && node.canConvertToLong()) return node.asLong();
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static JsonNode at(JsonNode node, String... path) {
        JsonNode current = node;
        for (String name : path) {
            if (current == null || !current.isObject()) return null;
            current = current.get(name);
        }
        return current;
    }

    // ---------------------------------------------------------------- values

    private boolean knownTimeFrame(String id) {
        return id != null && !DashboardTimeFrames.CUSTOM.equals(id) && engine.knownTimeFrame(id);
    }

    private static boolean unchanged(JsonNode node, JsonNode storedNode, String key) {
        if (storedNode == null || storedNode.get(key) == null) return false;
        return same(storedNode.get(key), node.get(key));
    }

    /**
     * Equal JSON values; numbers by value (a browser sends a stored 4.0 back as 4)
     */
    static boolean same(JsonNode a, JsonNode b) {
        if (a == null || b == null) return a == b;
        if (a.isNumber() && b.isNumber()) return a.decimalValue().compareTo(b.decimalValue()) == 0;
        return a.equals(b);
    }

    private void zoom(ObjectNode source) {
        JsonNode zoom = source.get("Zoom Factor");
        if (zoom.isNumber() && !Double.isNaN(zoom.asDouble()) && !Double.isInfinite(zoom.asDouble())) {
            double value = zoom.asDouble();
            if (value > 0 && value <= 20) return;
            for (double fit : FIT_ZOOMS) {
                if (Double.compare(fit, value) == 0) return;
            }
        }
        messages.add("Invalid Zoom Factor (factor up to 20, or 97, 98, 99)");
    }

    private void color(ObjectNode node, String field, String key, JsonNode storedNode) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return;
        if (unchanged(node, storedNode, field)) return;
        String formatted = value.isTextual() ? javaFxColor(value.asText()) : null;
        if (formatted == null) {
            messages.add("Invalid " + key + " (#rrggbb, #rrggbbaa or 0xrrggbbaa)");
            return;
        }
        node.put(field, formatted);
    }

    /**
     * @return the colour as JavaFX {@code Color.toString()} ({@code 0xrrggbbaa}), null if it is no
     * {@code #rrggbb}, {@code #rrggbbaa}, {@code 0xrrggbb} or {@code 0xrrggbbaa}
     */
    static String javaFxColor(String color) {
        java.util.regex.Matcher m = COLOR.matcher(color != null ? color.trim() : "");
        if (!m.matches()) return null;
        String alpha = m.group(3) != null ? m.group(3) : "ff";
        return ("0x" + m.group(2) + alpha).toLowerCase(Locale.ROOT);
    }

    private void bool(ObjectNode node, String field, String key) {
        JsonNode value = node.get(field);
        if (value != null && !value.isNull() && !value.isBoolean()) messages.add("Invalid " + key);
    }

    private void text(ObjectNode node, String field, String key) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return;
        if (!value.isTextual()) {
            messages.add("Invalid " + key);
        } else if (value.asText().length() > MAX_TEXT_LENGTH) {
            messages.add(key + " longer than " + MAX_TEXT_LENGTH + " characters");
        }
    }

    private void number(ObjectNode node, String field, String key, double min, double max) {
        number(node, field, key, min, max, null);
    }

    /**
     * Checks a number; a value the stored node has unchanged is accepted (dashboards saved by the Control Center may
     * hold values outside the web's ranges, e.g. widgets narrower than 10 px), so saving an unchanged source works.
     */
    private void number(ObjectNode node, String field, String key, double min, double max, JsonNode storedNode) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return;
        if (storedNode != null && unchanged(node, storedNode, field)) return;
        if (!value.isNumber() || Double.isNaN(value.asDouble()) || Double.isInfinite(value.asDouble())
                || value.asDouble() < min || value.asDouble() > max) {
            messages.add("Invalid " + key + " (" + format(min) + "–" + format(max) + ")");
        }
    }

    private void integer(ObjectNode node, String field, String key, int min, int max) {
        integer(node, field, key, min, max, null);
    }

    /** As {@link #number(ObjectNode, String, String, double, double, JsonNode)} for integers. */
    private void integer(ObjectNode node, String field, String key, int min, int max, JsonNode storedNode) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return;
        if (storedNode != null && unchanged(node, storedNode, field)) return;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < min || value.asInt() > max) {
            messages.add("Invalid " + key + (min == Integer.MIN_VALUE ? " (integer)" : " (" + min + "–" + max + ")"));
        }
    }

    private void oneOf(ObjectNode node, String field, String key, List<String> allowed, JsonNode storedNode) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return;
        if (value.isTextual() && allowed.contains(value.asText())) return;
        if (unchanged(node, storedNode, field)) return;
        messages.add("Invalid " + key + ": " + value.asText());
    }

    private void enumValue(JsonNode node, String field, String key, List<String> allowed) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return;
        if (!value.isTextual() || !allowed.contains(value.asText())) messages.add("Invalid " + key + ": " + value.asText());
    }

    private static List<String> names(Enum<?>[] values) {
        List<String> names = new ArrayList<>();
        for (Enum<?> value : values) names.add(value.name());
        return names;
    }

    private static String format(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? String.valueOf((long) value) : String.valueOf(value);
    }
}
