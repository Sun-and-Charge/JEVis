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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

public class AnalysisWriterTest {

    private static final DateTime NOW = new DateTime(2026, 11, 12, 10, 30, 15, 0, DateTimeZone.forID("Europe/Berlin"));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * An analysis file as the Control Center's {@code AnalysisHandler.toJsonNode} writes it (key order and value
     * spelling), plus keys the writer does not know and the per-row aggregation of an older Control Center.
     */
    private static final String CC_FILE = "{\n" +
            "  \"type\" : \"AnalysisHandler\",\n" +
            "  \"autoSize\" : false,\n" +
            "  \"chartsPerScreen\" : 3,\n" +
            "  \"horizontalPies\" : 2,\n" +
            "  \"horizontalTables\" : 4,\n" +
            "  \"forcedInterval\" : \"\",\n" +
            "  \"dayFilterWeekdays\" : \"6,7\",\n" +
            "  \"dayFilterHolidays\" : true,\n" +
            "  \"dayFilterDates\" : \"2026-12-24\",\n" +
            "  \"limitLines\" : [ {\n" +
            "    \"value\" : 10.5,\n" +
            "    \"label\" : \"Limit A\",\n" +
            "    \"color\" : \"#00FF00\",\n" +
            "    \"axis\" : 1,\n" +
            "    \"fill\" : \"ABOVE\"\n" +
            "  } ],\n" +
            "  \"chartModels\" : [ {\n" +
            "    \"chartId\" : 0,\n" +
            "    \"chartName\" : \"Chart A\",\n" +
            "    \"chartType\" : \"LINE\",\n" +
            "    \"minFractionDigits\" : 1,\n" +
            "    \"maxFractionDigits\" : 3,\n" +
            "    \"fractionDigitsAuto\" : false,\n" +
            "    \"groupingInterval\" : 15.0,\n" +
            "    \"height\" : 300.0,\n" +
            "    \"colorMapping\" : \"RAINBOW\",\n" +
            "    \"orientation\" : \"VERTICAL\",\n" +
            "    \"dayStart\" : \"08:00:00.000\",\n" +
            "    \"dayEnd\" : \"18:30:00.000\",\n" +
            "    \"filterEnabled\" : true,\n" +
            "    \"fixYAxisToZero\" : true,\n" +
            "    \"coloringEnabled\" : false,\n" +
            "    \"showColumnSums\" : true,\n" +
            "    \"showRowSums\" : false,\n" +
            "    \"filterTexts\" : {\n" +
            "      \"Meter A\" : \">10\"\n" +
            "    },\n" +
            "    \"xAxisTitle\" : \"Time\",\n" +
            "    \"yAxisTitle\" : \"Energy\",\n" +
            "    \"customChart\" : \"keep\",\n" +
            "    \"chartData\" : [ {\n" +
            "      \"id\" : 101,\n" +
            "      \"attributeString\" : \"Value\",\n" +
            "      \"unitPrefix\" : \"KILO\",\n" +
            "      \"unitFormula\" : \"kWh\",\n" +
            "      \"unitLabel\" : \"kWh\",\n" +
            "      \"name\" : \"Meter A\",\n" +
            "      \"color\" : \"0x00538a80\",\n" +
            "      \"axis\" : 0,\n" +
            "      \"calculation\" : false,\n" +
            "      \"calculationId\" : -1,\n" +
            "      \"bubbleType\" : \"NONE\",\n" +
            "      \"chartType\" : \"DEFAULT\",\n" +
            "      \"aggregationPeriod\" : \"DAILY\",\n" +
            "      \"manipulationMode\" : \"SUM\",\n" +
            "      \"decimalDigits\" : 2,\n" +
            "      \"css\" : \"\",\n" +
            "      \"intervalStart\" : \"2026-01-01T00:00:00.000+01:00\",\n" +
            "      \"intervalEnd\" : \"2026-01-02T00:00:00.000+01:00\",\n" +
            "      \"intervalEnabled\" : false,\n" +
            "      \"customRow\" : true\n" +
            "    }, {\n" +
            "      \"id\" : 102,\n" +
            "      \"attributeString\" : \"Value\",\n" +
            "      \"unitPrefix\" : \"NONE\",\n" +
            "      \"unitFormula\" : \"m³\",\n" +
            "      \"unitLabel\" : \"m³\",\n" +
            "      \"name\" : \"Meter B\",\n" +
            "      \"color\" : \"0xff0000ff\",\n" +
            "      \"axis\" : 1,\n" +
            "      \"calculation\" : true,\n" +
            "      \"calculationId\" : 555,\n" +
            "      \"bubbleType\" : \"NONE\",\n" +
            "      \"chartType\" : \"COLUMN\",\n" +
            "      \"decimalDigits\" : -1,\n" +
            "      \"css\" : \"\",\n" +
            "      \"intervalStart\" : \"2026-01-01T00:00:00.000+01:00\",\n" +
            "      \"intervalEnd\" : \"2026-01-02T00:00:00.000+01:00\",\n" +
            "      \"intervalEnabled\" : false\n" +
            "    } ]\n" +
            "  }, {\n" +
            "    \"chartId\" : 1,\n" +
            "    \"chartName\" : \"Chart B\",\n" +
            "    \"chartType\" : \"TABLE_H\",\n" +
            "    \"minFractionDigits\" : 2,\n" +
            "    \"maxFractionDigits\" : 2,\n" +
            "    \"groupingInterval\" : 30.0,\n" +
            "    \"height\" : -1.0,\n" +
            "    \"colorMapping\" : \"GREEN_YELLOW_RED\",\n" +
            "    \"orientation\" : \"HORIZONTAL\",\n" +
            "    \"filterEnabled\" : false,\n" +
            "    \"fixYAxisToZero\" : false,\n" +
            "    \"coloringEnabled\" : false,\n" +
            "    \"showColumnSums\" : false,\n" +
            "    \"showRowSums\" : false,\n" +
            "    \"chartData\" : [ ]\n" +
            "  } ],\n" +
            "  \"customTop\" : 1\n" +
            "}";

    private static ObjectNode stored() throws Exception {
        return (ObjectNode) MAPPER.readTree(CC_FILE);
    }

    private static List<String> keys(JsonNode node) {
        List<String> keys = new ArrayList<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) keys.add(it.next());
        return keys;
    }

    @Test
    public void unchangedDefinitionKeepsTheStoredJson() throws Exception {
        ObjectNode stored = stored();
        AnalysisDefinition definition = new AnalysisReader().readAnalysisFile(stored);
        ObjectNode written = new AnalysisWriter().patch(stored, definition, NOW);
        assertEquals(stored, written);
        assertEquals(keys(stored), keys(written));
        assertEquals(keys(stored.get("chartModels").get(0)), keys(written.get("chartModels").get(0)));
        assertEquals(keys(stored.get("chartModels").get(0).get("chartData").get(0)),
                keys(written.get("chartModels").get(0).get("chartData").get(0)));
        /* the stored tree is not changed */
        assertEquals(MAPPER.readTree(CC_FILE), stored);
    }

    @Test
    public void writtenFileIsPrettyPrintedLikeTheControlCenter() throws Exception {
        ObjectNode stored = stored();
        AnalysisWriter writer = new AnalysisWriter();
        byte[] bytes = writer.write(writer.patch(stored, new AnalysisReader().readAnalysisFile(stored), NOW));
        assertEquals(CC_FILE.replace("\n", System.lineSeparator()), new String(bytes, StandardCharsets.UTF_8));
        assertEquals(stored, writer.parse(bytes));
        assertNull(writer.parse("[1]".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Strom_20261112103015.json", AnalysisWriter.fileName("Strom", NOW));
    }

    @Test
    public void changesOnlyTouchTheChangedKeys() throws Exception {
        ObjectNode stored = stored();
        AnalysisDefinition definition = new AnalysisReader().readAnalysisFile(stored);
        definition.setChartsPerScreen(1);
        definition.getLimitLines().get(0).setColor("#0000ff");
        ChartDefinition chartA = definition.getCharts().get(0);
        chartA.setName("Chart A2");
        chartA.setXAxisTitle(null);
        chartA.setFractionDigitsAuto(null);
        chartA.setDayStart("07:15:00");
        chartA.getFilterTexts().clear();
        RowDefinition meterA = chartA.getRows().get(0);
        meterA.setColor("#ff000080");
        meterA.setAxis(1);
        RowDefinition meterB = chartA.getRows().get(1);
        meterB.setColor("#ff0000");

        ObjectNode written = new AnalysisWriter().patch(stored, definition, NOW);
        assertEquals(1, written.get("chartsPerScreen").asInt());
        assertEquals("#0000FF", written.get("limitLines").get(0).get("color").asText());
        JsonNode chart = written.get("chartModels").get(0);
        assertEquals("Chart A2", chart.get("chartName").asText());
        assertFalse(chart.has("xAxisTitle"));
        assertFalse(chart.has("fractionDigitsAuto"));
        assertFalse(chart.has("filterTexts"));
        assertEquals("07:15:00.000", chart.get("dayStart").asText());
        assertEquals("18:30:00.000", chart.get("dayEnd").asText());
        assertEquals("keep", chart.get("customChart").asText());
        JsonNode rowA = chart.get("chartData").get(0);
        assertEquals("0xff000080", rowA.get("color").asText());
        assertEquals(1, rowA.get("axis").asInt());
        /* aggregation of an old file and unknown keys stay */
        assertEquals("DAILY", rowA.get("aggregationPeriod").asText());
        assertEquals("SUM", rowA.get("manipulationMode").asText());
        assertTrue(rowA.get("customRow").asBoolean());
        /* same RGB without alpha: stored spelling kept */
        assertEquals("0xff0000ff", chart.get("chartData").get(1).get("color").asText());
        assertEquals(1, written.get("customTop").asInt());
    }

    @Test
    public void chartsAndRowsInTheOrderSentNewOnesWithControlCenterKeysOnly() throws Exception {
        ObjectNode stored = stored();
        AnalysisDefinition definition = new AnalysisReader().readAnalysisFile(stored);
        ChartDefinition chartA = definition.getCharts().get(0);
        ChartDefinition chartB = definition.getCharts().get(1);
        /* row 101 removed, row 102 kept, a new row added; chart B first; a new chart 5 */
        chartA.getRows().remove(0);
        RowDefinition added = new RowDefinition();
        added.setObjectId(103);
        added.setAttribute("Value");
        added.setName("Meter C");
        added.setColor("#00ff00");
        added.setChartType(AnalysisChartType.DEFAULT);
        chartA.getRows().add(added);
        ChartDefinition chartNew = new ChartDefinition();
        chartNew.setId(5);
        chartNew.setName("Chart C");
        definition.setCharts(new ArrayList<>(Arrays.asList(chartB, chartA, chartNew)));
        definition.getLimitLines().clear();

        ObjectNode written = new AnalysisWriter().patch(stored, definition, NOW);
        assertFalse(written.has("limitLines"));
        JsonNode charts = written.get("chartModels");
        assertEquals(3, charts.size());
        assertEquals(1, charts.get(0).get("chartId").asInt());
        assertEquals(0, charts.get(1).get("chartId").asInt());
        assertEquals(5, charts.get(2).get("chartId").asInt());
        JsonNode rows = charts.get(1).get("chartData");
        assertEquals(2, rows.size());
        assertEquals(102, rows.get(0).get("id").asLong());
        assertEquals(555, rows.get(0).get("calculationId").asLong());
        JsonNode newRow = rows.get(1);
        assertEquals(Arrays.asList("id", "attributeString", "unitPrefix", "unitFormula", "unitLabel", "name", "color",
                "axis", "calculation", "calculationId", "bubbleType", "chartType", "decimalDigits", "css",
                "intervalStart", "intervalEnd", "intervalEnabled"), keys(newRow));
        assertEquals("0x00ff00ff", newRow.get("color").asText());
        assertEquals("DEFAULT", newRow.get("chartType").asText());
        assertEquals(new DateTime(2026, 11, 12, 0, 0, 0, 0, NOW.getZone()).toString(), newRow.get("intervalStart").asText());
        assertEquals(NOW.toString(), newRow.get("intervalEnd").asText());
        assertEquals(Arrays.asList("chartId", "chartName", "chartType", "minFractionDigits", "maxFractionDigits",
                "groupingInterval", "height", "colorMapping", "orientation", "filterEnabled", "fixYAxisToZero",
                "coloringEnabled", "showColumnSums", "showRowSums", "chartData"), keys(charts.get(2)));
        assertEquals(-1.0, charts.get(2).get("height").asDouble(), 0);
    }

    @Test
    public void newAnalysisHasTheControlCenterLayout() throws Exception {
        AnalysisDefinition definition = new AnalysisDefinition();
        ChartDefinition chart = new ChartDefinition();
        chart.setId(0);
        chart.setName("Chart A");
        definition.getCharts().add(chart);
        LimitLineDefinition line = new LimitLineDefinition();
        line.setValue(5);
        line.setColor("#ff000080");
        definition.getLimitLines().add(line);
        ObjectNode json = new AnalysisWriter().toJson(definition, NOW);
        assertEquals(Arrays.asList("type", "autoSize", "chartsPerScreen", "horizontalPies", "horizontalTables",
                "forcedInterval", "dayFilterWeekdays", "dayFilterHolidays", "dayFilterDates", "limitLines",
                "chartModels"), keys(json));
        assertEquals("AnalysisHandler", json.get("type").asText());
        assertEquals("#FF000080", json.get("limitLines").get(0).get("color").asText());
        /* reads back */
        AnalysisDefinition read = new AnalysisReader().readAnalysisFile(json);
        assertEquals(1, read.getCharts().size());
        assertEquals("Chart A", read.getCharts().get(0).getName());
        assertEquals(5d, read.getLimitLines().get(0).getValue(), 0);
    }

    @Test
    public void legacyAnalysisIsWrittenInTheCurrentFormat() {
        String charts = "{\"listSettings\":[{\"id\":\"0\",\"name\":\"Overview\",\"chartType\":\"AREA\",\"height\":\"250\"}],\"autoSize\":\"false\"}";
        String unit = "{\\\"prefix\\\":\\\"\\\",\\\"formula\\\":\\\"kWh\\\",\\\"label\\\":\\\"kWh\\\"}";
        String dataModel = "{\"listDataRows\":[{\"name\":\"Meter A\",\"object\":\"201\",\"color\":\"0xff0000ff\","
                + "\"unit\":\"" + unit + "\",\"selectedCharts\":\"0\",\"axis\":\"1\",\"chartType\":\"COLUMN\"}]}";
        AnalysisReader reader = new AnalysisReader();
        AnalysisDefinition legacy = reader.readLegacy(dataModel, charts);
        ObjectNode json = new AnalysisWriter().patch(null, legacy, NOW);
        AnalysisDefinition read = reader.readAnalysisFile(json);
        assertFalse(read.isAutoSize());
        assertEquals(1, read.getCharts().size());
        assertEquals(AnalysisChartType.AREA, read.getCharts().get(0).getType());
        assertEquals(250d, read.getCharts().get(0).getHeight(), 0);
        RowDefinition row = read.getCharts().get(0).getRows().get(0);
        assertEquals(201, row.getObjectId());
        assertEquals("Value", row.getAttribute());
        assertEquals("#ff0000", row.getColor());
        assertEquals(1, row.getAxis());
        assertEquals(AnalysisChartType.COLUMN, row.getChartType());
        assertEquals("kWh", row.getUnitLabel());
        assertFalse(json.get("chartModels").get(0).get("chartData").get(0).has("aggregationPeriod"));
    }

    @Test
    public void colors() {
        assertEquals("0x00538aff", AnalysisWriter.rowColor("#00538a"));
        assertEquals("0x00538a80", AnalysisWriter.rowColor("#00538A80"));
        assertEquals("#00538A", AnalysisWriter.lineColor("#00538aff"));
        assertTrue(AnalysisWriter.sameColor("0x00538a80", "#00538a"));
        assertFalse(AnalysisWriter.sameColor("0x00538a80", "#00538aff"));
        assertTrue(AnalysisWriter.sameColor("lightblue", "#add8e6"));
        assertFalse(AnalysisWriter.sameColor("nonsense", "#add8e6"));
    }
}
