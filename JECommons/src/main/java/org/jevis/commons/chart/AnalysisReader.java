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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.json.JsonAnalysisDataRow;
import org.jevis.commons.json.JsonChartDataModel;
import org.jevis.commons.json.JsonChartSetting;
import org.jevis.commons.json.JsonChartSettings;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.commons.utils.CommonMethods;
import org.jevis.commons.ws.json.JsonUnit;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reads a stored analysis in both formats of the Control Center into an {@link AnalysisDefinition}:
 * <ul>
 *     <li>current: JSON file in the attribute {@value #ANALYSIS_FILE_ATTRIBUTE_NAME}
 *     (Control Center: {@code AnalysisHandler.loadDataModel}/{@code jsonToModel} into {@code DataModel}),</li>
 *     <li>legacy: JSON in the attributes {@value #DATA_MODEL_ATTRIBUTE_NAME} and {@value #CHARTS_ATTRIBUTE_NAME}
 *     (Control Center: {@code AnalysisDataModel} and {@code AnalysisHandler.convertOldToNew}).</li>
 * </ul>
 * Like the Control Center the charts are sorted by name (alphanumeric), as shown in the analysis.
 * Malformed values fall back to the defaults instead of failing the whole analysis.
 */
public class AnalysisReader {
    public static final String ANALYSIS_FILE_ATTRIBUTE_NAME = "Analysis File";
    public static final String CHARTS_ATTRIBUTE_NAME = "Charts";
    public static final String DATA_MODEL_ATTRIBUTE_NAME = "Data Model";
    public static final String NUMBER_OF_CHARTS_PER_SCREEN_ATTRIBUTE_NAME = "Number of Charts per Screen";
    public static final String NUMBER_OF_HORIZONTAL_PIES_ATTRIBUTE_NAME = "Number of Horizontal Pies";
    public static final String NUMBER_OF_HORIZONTAL_TABLES_ATTRIBUTE_NAME = "Number of Horizontal Tables";
    public static final String GRAPH_PLUGIN_CLASS_NAME = "Graph Plugin";
    public static final String VALUE_ATTRIBUTE_NAME = "Value";
    private static final Logger logger = LogManager.getLogger(AnalysisReader.class);
    private static final List<String> COLOR_MAPPINGS = Arrays.asList("LIME_YELLOW_RED", "BLUE_CYAN_GREEN_YELLOW_RED",
            "INFRARED_1", "INFRARED_2", "INFRARED_3", "INFRARED_4", "BLUE_GREEN_RED", "BLUE_BLACK_RED", "BLUE_YELLOW_RED",
            "BLUE_TRANSPARENT_RED", "GREEN_BLACK_RED", "GREEN_YELLOW_RED", "RAINBOW", "BLACK_WHITE", "WHITE_BLACK");
    private final ObjectMapper mapper = new ObjectMapper();

    public AnalysisReader() {
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    }

    /**
     * Reads the analysis stored on the object (analysis file if present, else the legacy attributes) and resolves
     * the objects of the rows with the data source of the object (readable flag and object name).
     *
     * @param analysisObject an object of class "Analysis"
     */
    public AnalysisDefinition read(JEVisObject analysisObject) throws JEVisException {
        JEVisDataSource ds = analysisObject.getDataSource();
        AnalysisDefinition definition;
        JEVisAttribute fileAttribute = analysisObject.getAttribute(ANALYSIS_FILE_ATTRIBUTE_NAME);
        if (fileAttribute != null && fileAttribute.hasSample()) {
            definition = new AnalysisDefinition();
            definition.setFormat(AnalysisDefinition.Format.ANALYSIS_FILE);
            JEVisSample latestSample = fileAttribute.getLatestSample();
            if (latestSample != null) {
                try {
                    JEVisFile file = latestSample.getValueAsFile();
                    definition = readAnalysisFile(file.getBytes());
                } catch (Exception e) {
                    logger.error("Could not read analysis file of object {}", analysisObject.getID(), e);
                }
            }
        } else {
            definition = readLegacy(ds, analysisObject);
        }
        definition.setId(analysisObject.getID());
        resolveRows(ds, definition);
        return definition;
    }

    /**
     * Parses an analysis file (JSON written by the Control Center's {@code AnalysisHandler.toJsonNode}).
     * Rows are not resolved (readable is {@code null}).
     */
    public AnalysisDefinition readAnalysisFile(byte[] json) throws java.io.IOException {
        return readAnalysisFile(mapper.readTree(new String(json, StandardCharsets.UTF_8)));
    }

    /**
     * Parses the JSON tree of an analysis file, see {@link #readAnalysisFile(byte[])}.
     */
    public AnalysisDefinition readAnalysisFile(JsonNode root) {
        AnalysisDefinition definition = new AnalysisDefinition();
        definition.setFormat(AnalysisDefinition.Format.ANALYSIS_FILE);
        if (root == null || !root.isObject()) return definition;

        definition.setAutoSize(bool(root, "autoSize", true));
        definition.setChartsPerScreen(integer(root, "chartsPerScreen", 2));
        definition.setHorizontalPies(integer(root, "horizontalPies", 3));
        definition.setHorizontalTables(integer(root, "horizontalTables", 3));
        definition.setForcedInterval(text(root, "forcedInterval", ""));
        definition.setDayFilterWeekdays(text(root, "dayFilterWeekdays", ""));
        definition.setDayFilterHolidays(bool(root, "dayFilterHolidays", false));
        definition.setDayFilterDates(text(root, "dayFilterDates", ""));

        JsonNode limitLines = root.get("limitLines");
        if (limitLines != null && limitLines.isArray()) {
            for (JsonNode node : limitLines) {
                LimitLineDefinition line = new LimitLineDefinition();
                line.setValue(number(node, "value", 0d));
                line.setLabel(text(node, "label", ""));
                line.setColor(text(node, "color", "#FF0000"));
                line.setAxis(integer(node, "axis", 0));
                line.setFill(text(node, "fill", "NONE"));
                definition.getLimitLines().add(line);
            }
        }

        JsonNode chartModels = root.get("chartModels");
        if (chartModels != null && chartModels.isArray()) {
            for (JsonNode chartNode : chartModels) {
                ChartDefinition chart = new ChartDefinition();
                chart.setId(integer(chartNode, "chartId", -1));
                chart.setName(text(chartNode, "chartName", ""));
                if (has(chartNode, "chartType")) chart.setType(AnalysisChartType.parse(chartNode.get("chartType").asText()));
                chart.setMinFractionDigits(integer(chartNode, "minFractionDigits", ChartDefinition.DEFAULT_FRACTION_DIGITS));
                chart.setMaxFractionDigits(integer(chartNode, "maxFractionDigits", ChartDefinition.DEFAULT_FRACTION_DIGITS));
                if (has(chartNode, "fractionDigitsAuto")) chart.setFractionDigitsAuto(chartNode.get("fractionDigitsAuto").asBoolean());
                chart.setGroupingInterval(number(chartNode, "groupingInterval", 30d));
                chart.setHeight(number(chartNode, "height", -1d));
                chart.setColorMapping(parseColorMapping(text(chartNode, "colorMapping", null)));
                chart.setOrientation(parseOrientation(text(chartNode, "orientation", null)));
                chart.setDayStart(text(chartNode, "dayStart", null));
                chart.setDayEnd(text(chartNode, "dayEnd", null));
                chart.setFilterEnabled(bool(chartNode, "filterEnabled", false));
                chart.setFixYAxisToZero(bool(chartNode, "fixYAxisToZero", false));
                chart.setColoringEnabled(bool(chartNode, "coloringEnabled", false));
                chart.setShowColumnSums(bool(chartNode, "showColumnSums", false));
                chart.setShowRowSums(bool(chartNode, "showRowSums", false));
                chart.setXAxisTitle(text(chartNode, "xAxisTitle", null));
                chart.setYAxisTitle(text(chartNode, "yAxisTitle", null));
                JsonNode filterTexts = chartNode.get("filterTexts");
                if (filterTexts != null && filterTexts.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = filterTexts.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        chart.getFilterTexts().put(entry.getKey(), entry.getValue().asText());
                    }
                }

                JsonNode chartData = chartNode.get("chartData");
                if (chartData != null && chartData.isArray()) {
                    for (JsonNode rowNode : chartData) {
                        chart.getRows().add(readRow(rowNode));
                    }
                }
                definition.getCharts().add(chart);
            }
        }

        finish(definition);
        return definition;
    }

    private RowDefinition readRow(JsonNode rowNode) {
        RowDefinition row = new RowDefinition();
        row.setObjectId(longValue(rowNode, "id", -1L));
        row.setAttribute(text(rowNode, "attributeString", ""));
        if (rowNode.has("unitPrefix")) row.setUnitPrefix(text(rowNode, "unitPrefix", row.getUnitPrefix()));
        if (rowNode.has("unitFormula")) row.setUnitFormula(text(rowNode, "unitFormula", row.getUnitFormula()));
        if (rowNode.has("unitLabel")) row.setUnitLabel(text(rowNode, "unitLabel", row.getUnitLabel()));
        row.setName(text(rowNode, "name", ""));
        if (has(rowNode, "color")) {
            row.setColor(WebColor.toHex(rowNode.get("color").asText(), WebColor.DEFAULT_ROW_COLOR));
        }
        row.setAxis(integer(rowNode, "axis", 0));
        row.setCalculation(bool(rowNode, "calculation", false));
        row.setCalculationId(longValue(rowNode, "calculationId", -1L));
        if (rowNode.has("bubbleType")) row.setBubbleType(parseBubbleType(text(rowNode, "bubbleType", null)));
        /* ChartData.setChartType(String) -> ChartType.parseChartType, which gives LINE for null/unknown */
        if (rowNode.has("chartType")) row.setChartType(AnalysisChartType.parse(text(rowNode, "chartType", null)));
        row.setDecimalDigits(integer(rowNode, "decimalDigits", -1));
        row.setCss(text(rowNode, "css", ""));
        row.setIntervalStart(text(rowNode, "intervalStart", null));
        row.setIntervalEnd(text(rowNode, "intervalEnd", null));
        row.setIntervalEnabled(bool(rowNode, "intervalEnabled", false));
        return row;
    }

    /**
     * Reads the legacy format from the attributes {@value #DATA_MODEL_ATTRIBUTE_NAME} and {@value #CHARTS_ATTRIBUTE_NAME}
     * and the layout attributes of the analysis (with fallback to the "Graph Plugin" objects).
     * Rows whose object is readable but no data object are skipped like in the Control Center.
     */
    public AnalysisDefinition readLegacy(JEVisDataSource ds, JEVisObject analysisObject) {
        String chartsJson = latestString(analysisObject, CHARTS_ATTRIBUTE_NAME);
        String dataModelJson = latestString(analysisObject, DATA_MODEL_ATTRIBUTE_NAME);
        AnalysisDefinition definition = readLegacy(dataModelJson, chartsJson);
        definition.setChartsPerScreen((int) loadLongAttributeWithFallback(ds, analysisObject, NUMBER_OF_CHARTS_PER_SCREEN_ATTRIBUTE_NAME, 2L));
        definition.setHorizontalPies((int) loadLongAttributeWithFallback(ds, analysisObject, NUMBER_OF_HORIZONTAL_PIES_ATTRIBUTE_NAME, 2L));
        definition.setHorizontalTables((int) loadLongAttributeWithFallback(ds, analysisObject, NUMBER_OF_HORIZONTAL_TABLES_ATTRIBUTE_NAME, 3L));

        /* convertOldToNew skips rows without object or "Value" attribute; unreadable rows are kept (readable = false) */
        for (ChartDefinition chart : definition.getCharts()) {
            List<RowDefinition> keep = new ArrayList<>();
            for (RowDefinition row : chart.getRows()) {
                try {
                    JEVisObject object = ds.getObject(row.getObjectId());
                    if (object == null || resolveValueAttribute(object) != null) {
                        keep.add(row);
                    } else {
                        logger.warn("Skipping data row {} of analysis, attribute not available", row.getObjectId());
                    }
                } catch (Exception e) {
                    keep.add(row);
                }
            }
            chart.setRows(keep);
        }
        finish(definition);
        return definition;
    }

    /**
     * Parses the legacy JSON strings without data source (layout attributes keep their defaults, all rows are kept).
     *
     * @param dataModelJson value of the attribute {@value #DATA_MODEL_ATTRIBUTE_NAME}, may be {@code null}
     * @param chartsJson    value of the attribute {@value #CHARTS_ATTRIBUTE_NAME}, may be {@code null}
     */
    public AnalysisDefinition readLegacy(String dataModelJson, String chartsJson) {
        AnalysisDefinition definition = new AnalysisDefinition();
        definition.setFormat(AnalysisDefinition.Format.LEGACY);
        /* AnalysisDataModel defaults */
        definition.setHorizontalPies(2);

        JsonChartSettings jsonChartSettings = new JsonChartSettings();
        if (chartsJson != null) {
            try {
                if (chartsJson.startsWith("[")) {
                    jsonChartSettings = new JsonChartSettings();
                    jsonChartSettings.setListSettings(Arrays.asList(mapper.readValue(chartsJson, JsonChartSetting[].class)));
                } else {
                    jsonChartSettings = mapper.readValue(chartsJson, JsonChartSettings.class);
                }
            } catch (Exception e) {
                logger.error("Error: could not read chart settings", e);
            }
        }

        List<ChartDefinition> charts = new ArrayList<>();
        if (jsonChartSettings != null && jsonChartSettings.getListSettings() != null && !jsonChartSettings.getListSettings().isEmpty()) {
            /* the Control Center fails on a missing autoSize (unboxing null), we keep the default */
            if (jsonChartSettings.getAutoSize() != null) {
                definition.setAutoSize(Boolean.parseBoolean(jsonChartSettings.getAutoSize()));
            }
            boolean needsIds = false;
            for (JsonChartSetting settings : jsonChartSettings.getListSettings()) {
                ChartDefinition chart = new ChartDefinition();
                if (settings.getId() != null) {
                    chart.setId(parseInt(settings.getId(), -1));
                } else {
                    needsIds = true;
                }
                chart.setName(settings.getName());
                chart.setType(settings.getChartType() != null ? AnalysisChartType.parse(settings.getChartType()) : AnalysisChartType.LINE);
                chart.setColorMapping(parseColorMapping(settings.getColorMapping()));
                chart.setOrientation(parseOrientation(settings.getOrientation()));
                chart.setGroupingInterval(settings.getGroupingInterval() != null ? parseDouble(settings.getGroupingInterval(), 30d) : 30d);
                chart.setMinFractionDigits(settings.getMinFractionDigits() != null ? parseInt(settings.getMinFractionDigits(), 2) : 2);
                chart.setMaxFractionDigits(settings.getMaxFractionDigits() != null ? parseInt(settings.getMaxFractionDigits(), 2) : 2);
                /* the Control Center fails on a missing height (unboxing null), we use the default of ChartModel */
                chart.setHeight(settings.getHeight() != null ? parseDouble(settings.getHeight(), -1d) : -1d);
                /* filterEnabled is read by AnalysisDataModel but not copied by convertOldToNew */
                if (definition.getLegacyTimeFrame() == null && settings.getAnalysisTimeFrame() != null) {
                    definition.setLegacyTimeFrame(settings.getAnalysisTimeFrame().getTimeframe());
                }
                charts.add(chart);
            }
            if (needsIds) {
                AlphanumComparator ac = new AlphanumComparator();
                charts.sort((o1, o2) -> ac.compare(o1.getName(), o2.getName()));
                for (ChartDefinition chart : charts) chart.setId(charts.indexOf(chart));
            }
        }

        /* data rows, keyed by object id like AnalysisDataModel.updateSelectedData (a later row replaces an earlier one) */
        Map<Long, LegacyRow> rows = new LinkedHashMap<>();
        for (JsonAnalysisDataRow mdl : readLegacyRows(dataModelJson)) {
            try {
                LegacyRow legacyRow = new LegacyRow();
                RowDefinition row = legacyRow.row;
                long id = Long.parseLong(mdl.getObject());
                if (mdl.getDataProcessorObject() != null) id = Long.parseLong(mdl.getDataProcessorObject());
                row.setObjectId(id);
                row.setAttribute(VALUE_ATTRIBUTE_NAME);
                if (mdl.getUnit() != null) {
                    try {
                        row.setUnit(new JEVisUnitImp(mapper.readValue(mdl.getUnit(), JsonUnit.class)));
                    } catch (Exception e) {
                        logger.error("Error: could not parse unit", e);
                        continue;
                    }
                }
                if (mdl.getColor() != null) {
                    row.setColor(WebColor.toHex(mdl.getColor(), WebColor.DEFAULT_ROW_COLOR));
                }
                row.setName(mdl.getName());
                if (mdl.getAxis() != null) row.setAxis(parseInt(mdl.getAxis(), 0));
                if (mdl.getIsEnPI() != null) row.setCalculation(Boolean.parseBoolean(mdl.getIsEnPI()));
                if (mdl.getBubbleType() != null) row.setBubbleType(parseBubbleType(mdl.getBubbleType()));
                row.setChartType(mdl.getChartType() != null ? AnalysisChartType.parse(mdl.getChartType()) : AnalysisChartType.DEFAULT);
                legacyRow.selectedCharts = stringToList(mdl.getSelectedCharts(), charts);
                rows.remove(id);
                rows.put(id, legacyRow);
            } catch (Exception e) {
                logger.error("Could not read data row {} of analysis", mdl.getObject(), e);
            }
        }

        for (ChartDefinition chart : charts) {
            for (LegacyRow legacyRow : rows.values()) {
                if (legacyRow.selectedCharts.contains(chart.getId())) {
                    chart.getRows().add(copy(legacyRow.row));
                }
            }
        }
        definition.setCharts(charts);
        finish(definition);
        return definition;
    }

    private List<JsonAnalysisDataRow> readLegacyRows(String str) {
        if (str == null) return Collections.emptyList();
        try {
            if (str.startsWith("[")) {
                return Arrays.asList(mapper.readValue(str, JsonAnalysisDataRow[].class));
            } else {
                try {
                    JsonChartDataModel model = mapper.readValue(str, JsonChartDataModel.class);
                    return model.getListDataRows() != null ? model.getListDataRows() : Collections.emptyList();
                } catch (Exception e) {
                    logger.error(e);
                    return Collections.singletonList(mapper.readValue(str, JsonAnalysisDataRow.class));
                }
            }
        } catch (Exception e) {
            logger.error("Error: could not read data model", e);
            return Collections.emptyList();
        }
    }

    /**
     * Port of {@code AnalysisDataModel.stringToList}: chart ids separated by ", " or (very old) chart names.
     */
    static List<Integer> stringToList(String s, List<ChartDefinition> charts) {
        List<Integer> idList = new ArrayList<>();
        if (s == null) return idList;
        List<String> tempList = new ArrayList<>(Arrays.asList(s.split(", ")));
        try {
            for (String str : tempList) {
                idList.add(Integer.parseInt(str));
            }
        } catch (Exception e) {
            AlphanumComparator ac = new AlphanumComparator();
            tempList.sort(ac::compare);
            for (String str : tempList) {
                for (ChartDefinition chart : charts) {
                    if (chart.getName() != null && chart.getName().equals(str)) idList.add(chart.getId());
                }
            }
        }
        return idList;
    }

    /**
     * Sorts the charts like {@code ChartPlugin.update()} and assigns the row keys.
     */
    private void finish(AnalysisDefinition definition) {
        try {
            AlphanumComparator ac = new AlphanumComparator();
            definition.getCharts().sort((s1, s2) -> ac.compare(s1.getName(), s2.getName()));
        } catch (Exception ignored) {
        }
        Set<String> keys = new HashSet<>();
        for (ChartDefinition chart : definition.getCharts()) {
            for (RowDefinition row : chart.getRows()) {
                String base = chart.getId() + "-" + row.getObjectId() + "-" + row.getAttribute();
                String key = base;
                int n = 2;
                while (!keys.add(key)) {
                    key = base + "-" + n++;
                }
                row.setKey(key);
            }
        }
    }

    /**
     * Resolves the objects of the rows: readable flag and object name (for a clean data row the name of the parent
     * data object, like {@code ChartDataRow.getName()}).
     */
    public void resolveRows(JEVisDataSource ds, AnalysisDefinition definition) {
        Map<Long, JEVisObject> cache = new HashMap<>();
        Set<Long> unreadable = new HashSet<>();
        for (ChartDefinition chart : definition.getCharts()) {
            for (RowDefinition row : chart.getRows()) {
                long id = row.getObjectId();
                JEVisObject object = cache.get(id);
                if (object == null && !unreadable.contains(id)) {
                    try {
                        object = ds.getObject(id);
                    } catch (Exception e) {
                        logger.debug("Could not get object {}", id, e);
                    }
                    if (object == null) unreadable.add(id);
                    else cache.put(id, object);
                }
                row.setReadable(object != null);
                if (object != null) {
                    try {
                        JEVisObject named = object;
                        if (RowDataLoader.CLEAN_DATA_CLASS.equals(object.getJEVisClassName())) {
                            named = CommonMethods.getFirstParentalDataObject(object);
                        }
                        row.setObjectName(named.getName());
                    } catch (Exception e) {
                        row.setObjectName(object.getName());
                    }
                }
            }
        }
    }

    static JEVisAttribute resolveValueAttribute(JEVisObject object) throws JEVisException {
        JEVisObject dataObject = object;
        JEVisObject cleanObject = null;
        if (RowDataLoader.CLEAN_DATA_CLASS.equals(object.getJEVisClassName())) {
            cleanObject = object;
            dataObject = CommonMethods.getFirstParentalDataObject(object);
        }
        if (dataObject == null || !RowDataLoader.DATA_CLASSES.contains(dataObject.getJEVisClassName())) return null;
        return cleanObject != null ? cleanObject.getAttribute(VALUE_ATTRIBUTE_NAME) : dataObject.getAttribute(VALUE_ATTRIBUTE_NAME);
    }

    private long loadLongAttributeWithFallback(JEVisDataSource ds, JEVisObject analysisObject, String attributeName, long defaultValue) {
        try {
            JEVisAttribute attribute = analysisObject.getAttribute(attributeName);
            if (attribute == null) return defaultValue;
            JEVisSample sample = attribute.getLatestSample();
            if (sample != null) return sample.getValueAsLong();
            JEVisClass graphServiceClass = ds.getJEVisClass(GRAPH_PLUGIN_CLASS_NAME);
            if (graphServiceClass == null) return defaultValue;
            for (JEVisObject graphPlugin : ds.getObjects(graphServiceClass, true)) {
                JEVisAttribute attr = graphPlugin.getAttribute(attributeName);
                if (attr != null) {
                    JEVisSample lastSample = attr.getLatestSample();
                    if (lastSample != null) return lastSample.getValueAsLong();
                }
            }
        } catch (Exception e) {
            logger.error("Could not load attribute '{}' from analysis or fallback", attributeName, e);
        }
        return defaultValue;
    }

    private static String latestString(JEVisObject object, String attributeName) {
        try {
            JEVisAttribute attribute = object.getAttribute(attributeName);
            if (attribute != null && attribute.hasSample()) {
                JEVisSample sample = attribute.getLatestSample();
                if (sample != null) return sample.getValueAsString();
            }
        } catch (Exception e) {
            logger.error("Could not read attribute {} of {}", attributeName, object.getID(), e);
        }
        return null;
    }

    private static RowDefinition copy(RowDefinition source) {
        RowDefinition row = new RowDefinition();
        row.setObjectId(source.getObjectId());
        row.setAttribute(source.getAttribute());
        row.setName(source.getName());
        row.setColor(source.getColor());
        row.setAxis(source.getAxis());
        row.setChartType(source.getChartType());
        row.setUnitPrefix(source.getUnitPrefix());
        row.setUnitFormula(source.getUnitFormula());
        row.setUnitLabel(source.getUnitLabel());
        row.setDecimalDigits(source.getDecimalDigits());
        row.setCalculation(source.isCalculation());
        row.setCalculationId(source.getCalculationId());
        row.setBubbleType(source.getBubbleType());
        row.setIntervalEnabled(source.isIntervalEnabled());
        row.setIntervalStart(source.getIntervalStart());
        row.setIntervalEnd(source.getIntervalEnd());
        row.setCss(source.getCss());
        return row;
    }

    private static String parseColorMapping(String value) {
        if (value != null && COLOR_MAPPINGS.contains(value)) return value;
        return "GREEN_YELLOW_RED";
    }

    private static String parseOrientation(String value) {
        return "VERTICAL".equals(value) ? "VERTICAL" : "HORIZONTAL";
    }

    private static String parseBubbleType(String value) {
        if ("X".equals(value) || "Y".equals(value) || "SIZE".equals(value)) return value;
        return "NONE";
    }

    private static boolean has(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull();
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        if (!node.has(field)) return defaultValue;
        JsonNode value = node.get(field);
        return value.isNull() ? null : value.asText();
    }

    private static boolean bool(JsonNode node, String field, boolean defaultValue) {
        if (!has(node, field)) return defaultValue;
        return node.get(field).asBoolean(defaultValue);
    }

    private static int integer(JsonNode node, String field, int defaultValue) {
        if (!has(node, field)) return defaultValue;
        return node.get(field).asInt(defaultValue);
    }

    private static long longValue(JsonNode node, String field, long defaultValue) {
        if (!has(node, field)) return defaultValue;
        return node.get(field).asLong(defaultValue);
    }

    private static double number(JsonNode node, String field, double defaultValue) {
        if (!has(node, field)) return defaultValue;
        return node.get(field).asDouble(defaultValue);
    }

    private static int parseInt(String s, int defaultValue) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static double parseDouble(String s, double defaultValue) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static class LegacyRow {
        private final RowDefinition row = new RowDefinition();
        private List<Integer> selectedCharts = new ArrayList<>();
    }
}
