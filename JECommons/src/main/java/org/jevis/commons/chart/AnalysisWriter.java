/**
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
package org.jevis.commons.chart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Writes an {@link AnalysisDefinition} in the format of the Control Center's {@code AnalysisHandler.toJsonNode}
 * (counterpart of {@link AnalysisReader}): the JSON of the attribute {@value AnalysisReader#ANALYSIS_FILE_ATTRIBUTE_NAME}.
 * <p>
 * {@link #patch(JsonNode, AnalysisDefinition, DateTime)} starts from the latest stored JSON and only changes what
 * differs, so keys the writer does not know, the key order and the stored spelling of unchanged values (e.g. a colour
 * {@code 0x00538aff} or a day start {@code 08:00:00.000}) are kept:
 * <ul>
 *     <li>the top level as a whole,</li>
 *     <li>charts matched by {@code chartId}; charts not in the definition are removed, the order is the order of the
 *     definition,</li>
 *     <li>rows matched by {@code id} + {@code attributeString} within their chart; rows not in the definition are
 *     removed.</li>
 * </ul>
 * New charts and rows get the Control Center's keys only. Like the Control Center the time frame and the per-row
 * {@code aggregationPeriod}/{@code manipulationMode} are not written (they come from the tool bar, OpenJEVis #2358);
 * stored ones of old files stay untouched. Optional keys the Control Center leaves out when empty
 * ({@code fractionDigitsAuto}, {@code dayStart}, {@code dayEnd}, {@code filterTexts}, axis titles,
 * {@code limitLines}) are removed when the definition has no value.
 * <p>
 * Colours of the definition are {@code #rrggbb} or {@code #rrggbbaa}; rows are written like JavaFX
 * {@code Color.toString()} ({@code 0xrrggbbaa}), limit lines like {@code UserLimitLine.setFxColor}
 * ({@code #RRGGBB}). A colour without alpha keeps the stored alpha if the RGB part is unchanged.
 */
public class AnalysisWriter {
    public static final String TYPE = "AnalysisHandler";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @return the JSON of the definition as the Control Center writes a new analysis
     */
    public ObjectNode toJson(AnalysisDefinition definition, DateTime now) {
        return patch(null, definition, now);
    }

    /**
     * @param stored     the latest stored JSON (not changed), {@code null} for a new analysis or one in the legacy
     *                   format
     * @param definition the complete definition to store
     * @param now        for the Control Center's default interval of new rows without interval
     * @return the JSON to store
     */
    public ObjectNode patch(JsonNode stored, AnalysisDefinition definition, DateTime now) {
        ObjectNode root = stored != null && stored.isObject() ? ((ObjectNode) stored).deepCopy() : NODES.objectNode();
        set(root, "type", NODES.textNode(TYPE), Same.TEXT);
        set(root, "autoSize", NODES.booleanNode(definition.isAutoSize()), Same.BOOLEAN);
        set(root, "chartsPerScreen", NODES.numberNode(definition.getChartsPerScreen()), Same.NUMBER);
        set(root, "horizontalPies", NODES.numberNode(definition.getHorizontalPies()), Same.NUMBER);
        set(root, "horizontalTables", NODES.numberNode(definition.getHorizontalTables()), Same.NUMBER);
        set(root, "forcedInterval", text(definition.getForcedInterval()), Same.TEXT);
        set(root, "dayFilterWeekdays", text(definition.getDayFilterWeekdays()), Same.TEXT);
        set(root, "dayFilterHolidays", NODES.booleanNode(definition.isDayFilterHolidays()), Same.BOOLEAN);
        set(root, "dayFilterDates", text(definition.getDayFilterDates()), Same.TEXT);

        if (definition.getLimitLines() == null || definition.getLimitLines().isEmpty()) {
            root.remove("limitLines");
        } else {
            JsonNode storedLines = root.get("limitLines");
            ArrayNode lines = NODES.arrayNode();
            for (int i = 0; i < definition.getLimitLines().size(); i++) {
                LimitLineDefinition line = definition.getLimitLines().get(i);
                JsonNode storedLine = storedLines != null && storedLines.isArray() ? storedLines.get(i) : null;
                ObjectNode node = storedLine != null && storedLine.isObject() ? ((ObjectNode) storedLine).deepCopy() : NODES.objectNode();
                set(node, "value", NODES.numberNode(line.getValue()), Same.NUMBER);
                set(node, "label", text(line.getLabel()), Same.TEXT);
                setColor(node, line.getColor(), lineColor(line.getColor()));
                set(node, "axis", NODES.numberNode(line.getAxis()), Same.NUMBER);
                set(node, "fill", text(line.getFill()), Same.TEXT);
                lines.add(node);
            }
            root.set("limitLines", lines);
        }

        Map<Integer, ObjectNode> storedCharts = new HashMap<>();
        JsonNode storedChartModels = root.get("chartModels");
        if (storedChartModels != null && storedChartModels.isArray()) {
            for (JsonNode chartNode : storedChartModels) {
                if (chartNode.isObject() && chartNode.has("chartId") && chartNode.get("chartId").canConvertToInt()) {
                    storedCharts.putIfAbsent(chartNode.get("chartId").asInt(), (ObjectNode) chartNode);
                }
            }
        }
        ArrayNode chartModels = NODES.arrayNode();
        for (ChartDefinition chart : definition.getCharts()) {
            ObjectNode storedChart = storedCharts.remove(chart.getId());
            chartModels.add(chart(storedChart != null ? storedChart.deepCopy() : NODES.objectNode(), chart, now));
        }
        root.set("chartModels", chartModels);
        return root;
    }

    private ObjectNode chart(ObjectNode node, ChartDefinition chart, DateTime now) {
        /* the rows first, so a new chart gets "chartData" as last key like the Control Center */
        ArrayNode rows = rows(node.get("chartData"), chart, now);
        set(node, "chartId", NODES.numberNode(chart.getId()), Same.NUMBER);
        set(node, "chartName", text(chart.getName()), Same.TEXT);
        set(node, "chartType", NODES.textNode(chart.getType() != null ? chart.getType().name() : AnalysisChartType.LINE.name()), Same.TEXT);
        set(node, "minFractionDigits", NODES.numberNode(chart.getMinFractionDigits()), Same.NUMBER);
        set(node, "maxFractionDigits", NODES.numberNode(chart.getMaxFractionDigits()), Same.NUMBER);
        setOptional(node, "fractionDigitsAuto", chart.getFractionDigitsAuto() != null ? NODES.booleanNode(chart.getFractionDigitsAuto()) : null, Same.BOOLEAN);
        set(node, "groupingInterval", NODES.numberNode(chart.getGroupingInterval()), Same.NUMBER);
        set(node, "height", NODES.numberNode(chart.getHeight()), Same.NUMBER);
        set(node, "colorMapping", text(chart.getColorMapping()), Same.TEXT);
        set(node, "orientation", text(chart.getOrientation()), Same.TEXT);
        setOptional(node, "dayStart", time(chart.getDayStart()), Same.TIME);
        setOptional(node, "dayEnd", time(chart.getDayEnd()), Same.TIME);
        set(node, "filterEnabled", NODES.booleanNode(chart.isFilterEnabled()), Same.BOOLEAN);
        set(node, "fixYAxisToZero", NODES.booleanNode(chart.isFixYAxisToZero()), Same.BOOLEAN);
        set(node, "coloringEnabled", NODES.booleanNode(chart.isColoringEnabled()), Same.BOOLEAN);
        set(node, "showColumnSums", NODES.booleanNode(chart.isShowColumnSums()), Same.BOOLEAN);
        set(node, "showRowSums", NODES.booleanNode(chart.isShowRowSums()), Same.BOOLEAN);
        if (chart.getFilterTexts() == null || chart.getFilterTexts().isEmpty()) {
            node.remove("filterTexts");
        } else {
            ObjectNode filterTexts = NODES.objectNode();
            for (Map.Entry<String, String> entry : chart.getFilterTexts().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    filterTexts.put(entry.getKey(), entry.getValue());
                }
            }
            set(node, "filterTexts", filterTexts, Same.EQUAL);
        }
        setOptional(node, "xAxisTitle", chart.getXAxisTitle() != null ? NODES.textNode(chart.getXAxisTitle()) : null, Same.TEXT);
        setOptional(node, "yAxisTitle", chart.getYAxisTitle() != null ? NODES.textNode(chart.getYAxisTitle()) : null, Same.TEXT);
        node.set("chartData", rows);
        return node;
    }

    private ArrayNode rows(JsonNode storedRows, ChartDefinition chart, DateTime now) {
        List<ObjectNode> unused = new ArrayList<>();
        if (storedRows != null && storedRows.isArray()) {
            for (JsonNode rowNode : storedRows) {
                if (rowNode.isObject()) unused.add((ObjectNode) rowNode);
            }
        }
        ArrayNode rows = NODES.arrayNode();
        for (RowDefinition row : chart.getRows()) {
            ObjectNode match = null;
            for (ObjectNode candidate : unused) {
                if (candidate.path("id").asLong(Long.MIN_VALUE) == row.getObjectId()
                        && candidate.path("attributeString").asText("").equals(row.getAttribute())) {
                    match = candidate;
                    break;
                }
            }
            if (match != null) unused.remove(match);
            rows.add(row(match != null ? match.deepCopy() : NODES.objectNode(), match == null, row, now));
        }
        return rows;
    }

    private ObjectNode row(ObjectNode node, boolean isNew, RowDefinition row, DateTime now) {
        set(node, "id", NODES.numberNode(row.getObjectId()), Same.NUMBER);
        set(node, "attributeString", text(row.getAttribute()), Same.TEXT);
        set(node, "unitPrefix", text(row.getUnitPrefix()), Same.TEXT);
        set(node, "unitFormula", text(row.getUnitFormula()), Same.TEXT);
        set(node, "unitLabel", text(row.getUnitLabel()), Same.TEXT);
        set(node, "name", text(row.getName() != null ? row.getName() : ""), Same.TEXT);
        if (row.getColor() != null) setColor(node, row.getColor(), rowColor(row.getColor()));
        set(node, "axis", NODES.numberNode(row.getAxis()), Same.NUMBER);
        set(node, "calculation", NODES.booleanNode(row.isCalculation()), Same.BOOLEAN);
        set(node, "calculationId", NODES.numberNode(row.getCalculationId()), Same.NUMBER);
        set(node, "bubbleType", text(row.getBubbleType() != null ? row.getBubbleType() : "NONE"), Same.TEXT);
        set(node, "chartType", NODES.textNode(row.getChartType() != null ? row.getChartType().name() : AnalysisChartType.DEFAULT.name()), Same.TEXT);
        set(node, "decimalDigits", NODES.numberNode(row.getDecimalDigits()), Same.NUMBER);
        set(node, "css", text(row.getCss() != null ? row.getCss() : ""), Same.TEXT);
        /* ChartData's defaults: today 00:00 and now (DateTime.toString) */
        String start = row.getIntervalStart();
        String end = row.getIntervalEnd();
        if (start == null && (isNew || !node.has("intervalStart"))) {
            start = now.withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0).toString();
        }
        if (end == null && (isNew || !node.has("intervalEnd"))) end = now.toString();
        if (start != null) set(node, "intervalStart", NODES.textNode(start), Same.DATE_TIME);
        if (end != null) set(node, "intervalEnd", NODES.textNode(end), Same.DATE_TIME);
        set(node, "intervalEnabled", NODES.booleanNode(row.isIntervalEnabled()), Same.BOOLEAN);
        return node;
    }

    /**
     * @return the JSON pretty printed like {@code AnalysisHandler.saveDataModel} ({@code writerWithDefaultPrettyPrinter})
     */
    public byte[] write(JsonNode tree) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param json the stored file
     * @return the JSON object, {@code null} if it is no JSON object
     */
    public ObjectNode parse(byte[] json) {
        if (json == null) return null;
        try {
            JsonNode node = mapper.readTree(new String(json, StandardCharsets.UTF_8));
            return node != null && node.isObject() ? (ObjectNode) node : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * @return the file name of {@code AnalysisHandler.saveDataModel}: {@code <local name>_<yyyyMMddHHmmss>.json}
     */
    public static String fileName(String localName, DateTime time) {
        return localName + "_" + time.toString("yyyyMMddHHmmss") + ".json";
    }

    // ---------------------------------------------------------------- values

    /**
     * @param color {@code #rrggbb} or {@code #rrggbbaa} (other formats are parsed like {@code Color.web})
     * @return {@code 0xrrggbbaa} like JavaFX {@code Color.toString()}
     */
    static String rowColor(String color) {
        int[] rgba = rgba(color);
        if (rgba == null) return color;
        return String.format(Locale.ROOT, "0x%02x%02x%02x%02x", rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    /**
     * @return {@code #RRGGBB} like {@code UserLimitLine.setFxColor}, with alpha ({@code #RRGGBBAA}) only if not opaque
     */
    static String lineColor(String color) {
        int[] rgba = rgba(color);
        if (rgba == null) return color;
        String hex = String.format(Locale.ROOT, "#%02X%02X%02X", rgba[0], rgba[1], rgba[2]);
        return rgba[3] == 255 ? hex : hex + String.format(Locale.ROOT, "%02X", rgba[3]);
    }

    /**
     * @return {@code r, g, b, a} (0–255) of a colour; {@code #}/{@code 0x} hex with 6 or 8 digits keep the alpha,
     * other formats (named, {@code rgb(...)}) are opaque; {@code null} if invalid
     */
    static int[] rgba(String color) {
        if (color == null) return null;
        String c = color.trim().toLowerCase(Locale.ROOT);
        String hex = c.startsWith("#") ? c.substring(1) : c.startsWith("0x") ? c.substring(2) : null;
        if (hex != null && (hex.length() == 6 || hex.length() == 8) && hex.matches("[0-9a-f]+")) {
            int[] result = new int[]{Integer.parseInt(hex.substring(0, 2), 16), Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16), 255};
            if (hex.length() == 8) result[3] = Integer.parseInt(hex.substring(6, 8), 16);
            return result;
        }
        String rgb = WebColor.toHex(color, null);
        if (rgb == null) return null;
        return new int[]{Integer.parseInt(rgb.substring(1, 3), 16), Integer.parseInt(rgb.substring(3, 5), 16),
                Integer.parseInt(rgb.substring(5, 7), 16), 255};
    }

    /**
     * @return whether a stored colour is the colour of the definition: same RGB, and same alpha if the definition has
     * one ({@code #rrggbbaa})
     */
    static boolean sameColor(String stored, String color) {
        int[] a = rgba(stored);
        int[] b = rgba(color);
        if (a == null || b == null) return false;
        if (a[0] != b[0] || a[1] != b[1] || a[2] != b[2]) return false;
        return !hasAlpha(color) || a[3] == b[3];
    }

    private static boolean hasAlpha(String color) {
        String c = color.trim().toLowerCase(Locale.ROOT);
        String hex = c.startsWith("#") ? c.substring(1) : c.startsWith("0x") ? c.substring(2) : "";
        return hex.length() == 8;
    }

    /**
     * @param time {@code HH:mm}, {@code HH:mm:ss} or {@code HH:mm:ss.SSS}
     * @return Joda's {@code LocalTime.toString()} ({@code HH:mm:ss.SSS}) as the Control Center writes it, null for null
     */
    private static JsonNode time(String time) {
        if (time == null || time.trim().isEmpty()) return null;
        try {
            return NODES.textNode(LocalTime.parse(time.trim()).toString());
        } catch (IllegalArgumentException e) {
            return NODES.textNode(time);
        }
    }

    private static JsonNode text(String value) {
        return value != null ? NODES.textNode(value) : NODES.nullNode();
    }

    private static void set(ObjectNode node, String key, JsonNode value, Same same) {
        JsonNode old = node.get(key);
        if (old != null && same.test(old, value)) return;
        node.set(key, value);
    }

    /**
     * Keeps the stored colour if it is the colour of the definition ({@link #sameColor}), else writes it formatted
     */
    private static void setColor(ObjectNode node, String color, String formatted) {
        JsonNode old = node.get("color");
        if (old != null && old.isTextual() && (old.asText().equals(formatted) || sameColor(old.asText(), color))) return;
        node.put("color", formatted);
    }

    private static void setOptional(ObjectNode node, String key, JsonNode value, Same same) {
        if (value == null) {
            node.remove(key);
        } else {
            set(node, key, value, same);
        }
    }

    /**
     * When a stored value counts as the value of the definition (then the stored spelling is kept)
     */
    private enum Same {
        EQUAL {
            @Override
            boolean test(JsonNode stored, JsonNode value) {
                return stored.equals(value);
            }
        },
        TEXT {
            @Override
            boolean test(JsonNode stored, JsonNode value) {
                if (value.isNull()) return stored.isNull();
                return stored.isTextual() && stored.asText().equals(value.asText());
            }
        },
        NUMBER {
            @Override
            boolean test(JsonNode stored, JsonNode value) {
                return stored.isNumber() && Double.compare(stored.asDouble(), value.asDouble()) == 0;
            }
        },
        BOOLEAN {
            @Override
            boolean test(JsonNode stored, JsonNode value) {
                return stored.isBoolean() && stored.asBoolean() == value.asBoolean();
            }
        },
        TIME {
            @Override
            boolean test(JsonNode stored, JsonNode value) {
                if (!stored.isTextual()) return false;
                try {
                    return LocalTime.parse(stored.asText().trim()).equals(LocalTime.parse(value.asText().trim()));
                } catch (IllegalArgumentException e) {
                    return stored.asText().equals(value.asText());
                }
            }
        },
        DATE_TIME {
            @Override
            boolean test(JsonNode stored, JsonNode value) {
                if (!stored.isTextual()) return false;
                if (stored.asText().equals(value.asText())) return true;
                try {
                    return DateTime.parse(stored.asText().trim()).getMillis() == DateTime.parse(value.asText().trim()).getMillis();
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
        };

        abstract boolean test(JsonNode stored, JsonNode value);
    }
}
