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

import org.jevis.api.*;
import org.jevis.commons.JEVisFileImp;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AnalysisReaderTest {

    private static final String ANALYSIS_FILE = "{\n" +
            "  \"type\": \"AnalysisHandler\",\n" +
            "  \"autoSize\": false,\n" +
            "  \"chartsPerScreen\": 3,\n" +
            "  \"horizontalPies\": 2,\n" +
            "  \"horizontalTables\": 4,\n" +
            "  \"forcedInterval\": \"\",\n" +
            "  \"dayFilterWeekdays\": \"6,7\",\n" +
            "  \"dayFilterHolidays\": true,\n" +
            "  \"dayFilterDates\": \"2026-12-24\",\n" +
            "  \"limitLines\": [ { \"value\": 10.5, \"label\": \"Limit A\", \"color\": \"#00FF00\", \"axis\": 1, \"fill\": \"ABOVE\" } ],\n" +
            "  \"chartModels\": [\n" +
            "    {\n" +
            "      \"chartId\": 1, \"chartName\": \"Chart B\", \"chartType\": \"COLUMN\",\n" +
            "      \"minFractionDigits\": 1, \"maxFractionDigits\": 3, \"groupingInterval\": 15.0, \"height\": 300.0,\n" +
            "      \"colorMapping\": \"RAINBOW\", \"orientation\": \"VERTICAL\", \"filterEnabled\": false, \"fixYAxisToZero\": true,\n" +
            "      \"coloringEnabled\": false, \"showColumnSums\": false, \"showRowSums\": false,\n" +
            "      \"xAxisTitle\": \"Time\", \"yAxisTitle\": \"Energy\",\n" +
            "      \"chartData\": [\n" +
            "        { \"id\": 101, \"attributeString\": \"Value\", \"unitPrefix\": \"k\", \"unitFormula\": \"Wh\", \"unitLabel\": \"kWh\",\n" +
            "          \"name\": \"Row One\", \"color\": \"0x00538aff\", \"axis\": 0, \"calculation\": false, \"calculationId\": -1,\n" +
            "          \"bubbleType\": \"NONE\", \"chartType\": \"DEFAULT\", \"decimalDigits\": 2, \"css\": \"\",\n" +
            "          \"intervalStart\": \"2026-01-01T00:00:00.000+01:00\", \"intervalEnd\": \"2026-01-02T00:00:00.000+01:00\", \"intervalEnabled\": false },\n" +
            "        { \"id\": 102, \"attributeString\": \"Value\", \"name\": \"\", \"color\": \"#ff0000\", \"axis\": 1, \"chartType\": \"LINE\",\n" +
            "          \"calculation\": true, \"calculationId\": 555 }\n" +
            "      ]\n" +
            "    },\n" +
            "    { \"chartId\": 0, \"chartName\": \"Chart A\", \"chartType\": \"TABLE_H\",\n" +
            "      \"chartData\": [ { \"id\": 103, \"attributeString\": \"Value\", \"name\": \"Third\" } ] }\n" +
            "  ]\n" +
            "}";

    private static final String LEGACY_CHARTS = "{\"listSettings\":[" +
            "{\"id\":\"0\",\"name\":\"Overview\",\"chartType\":\"AREA\",\"height\":\"250\",\"groupingInterval\":\"20\"," +
            "\"minFractionDigits\":\"1\",\"maxFractionDigits\":\"1\",\"colorMapping\":\"UNKNOWN\",\"orientation\":\"HORIZONTAL\"," +
            "\"analysisTimeFrame\":{\"timeframe\":\"last7Days\",\"id\":\"-1\"}}," +
            "{\"id\":\"1\",\"name\":\"Details\",\"chartType\":\"TABLE\"}" +
            "],\"autoSize\":\"false\"}";

    private static final String UNIT_KWH = "{\\\"prefix\\\":\\\"k\\\",\\\"formula\\\":\\\"Wh\\\",\\\"label\\\":\\\"kWh\\\"}";

    private static final String LEGACY_DATA_MODEL = "{\"listDataRows\":[" +
            "{\"name\":\"Meter 1\",\"object\":\"201\",\"dataProcessorObject\":\"202\",\"color\":\"0xff0000ff\"," +
            "\"unit\":\"" + UNIT_KWH + "\",\"selectedCharts\":\"0, 1\",\"axis\":\"1\",\"isEnPI\":\"false\",\"chartType\":\"COLUMN\"}," +
            "{\"name\":\"Meter 2\",\"object\":\"203\",\"color\":\"#00ff00\",\"unit\":\"" + UNIT_KWH + "\",\"selectedCharts\":\"1\",\"isEnPI\":\"true\"}," +
            "{\"name\":\"Unused\",\"object\":\"204\",\"unit\":\"" + UNIT_KWH + "\",\"selectedCharts\":\"\"}" +
            "]}";

    @Test
    public void readsAnalysisFile() throws Exception {
        AnalysisDefinition definition = new AnalysisReader().readAnalysisFile(ANALYSIS_FILE.getBytes(StandardCharsets.UTF_8));

        assertEquals(AnalysisDefinition.Format.ANALYSIS_FILE, definition.getFormat());
        assertFalse(definition.isAutoSize());
        assertEquals(3, definition.getChartsPerScreen());
        assertEquals(2, definition.getHorizontalPies());
        assertEquals(4, definition.getHorizontalTables());
        assertEquals("6,7", definition.getDayFilterWeekdays());
        assertTrue(definition.isDayFilterHolidays());
        assertEquals("2026-12-24", definition.getDayFilterDates());
        assertEquals(1, definition.getLimitLines().size());
        assertEquals(10.5, definition.getLimitLines().get(0).getValue(), 0d);
        assertEquals("ABOVE", definition.getLimitLines().get(0).getFill());

        /* sorted by name like the Control Center shows them */
        assertEquals(2, definition.getCharts().size());
        ChartDefinition chartA = definition.getCharts().get(0);
        ChartDefinition chartB = definition.getCharts().get(1);
        assertEquals("Chart A", chartA.getName());
        assertEquals(AnalysisChartType.TABLE_H, chartA.getType());
        /* defaults of ChartModel */
        assertEquals(2, chartA.getMinFractionDigits());
        assertEquals(-1d, chartA.getHeight(), 0d);
        assertEquals(30d, chartA.getGroupingInterval(), 0d);
        assertEquals("GREEN_YELLOW_RED", chartA.getColorMapping());
        assertEquals("HORIZONTAL", chartA.getOrientation());

        assertEquals(1, chartB.getId());
        assertEquals(AnalysisChartType.COLUMN, chartB.getType());
        assertEquals(1, chartB.getMinFractionDigits());
        assertEquals(3, chartB.getMaxFractionDigits());
        assertFalse(chartB.isAutoFractionDigits());
        assertEquals(300d, chartB.getHeight(), 0d);
        assertEquals(15d, chartB.getGroupingInterval(), 0d);
        assertEquals("RAINBOW", chartB.getColorMapping());
        assertEquals("VERTICAL", chartB.getOrientation());
        assertTrue(chartB.isFixYAxisToZero());
        assertEquals("Time", chartB.getXAxisTitle());
        assertEquals("Energy", chartB.getYAxisTitle());

        RowDefinition one = chartB.getRows().get(0);
        assertEquals("1-101-Value", one.getKey());
        assertEquals(101, one.getObjectId());
        assertEquals("Row One", one.getName());
        assertEquals("#00538a", one.getColor());
        assertEquals("kWh", one.getUnitLabel());
        assertEquals("kWh", one.getUnitDisplay());
        assertEquals(AnalysisChartType.DEFAULT, one.getChartType());
        assertEquals(AnalysisChartType.COLUMN, chartB.effectiveRowType(one));
        assertEquals(2, one.getDecimalDigits());
        assertFalse(one.isIntervalEnabled());

        RowDefinition two = chartB.getRows().get(1);
        assertEquals("#ff0000", two.getColor());
        assertEquals(1, two.getAxis());
        assertTrue(two.isCalculation());
        assertEquals(555, two.getCalculationId());

        /* defaults of ChartData: color LIGHTBLUE, chart type AREA, decimal digits -1 */
        RowDefinition third = chartA.getRows().get(0);
        assertEquals("0-103-Value", third.getKey());
        assertEquals(WebColor.DEFAULT_ROW_COLOR, third.getColor());
        assertEquals(AnalysisChartType.AREA, third.getChartType());
        assertEquals(-1, third.getDecimalDigits());
    }

    @Test
    public void readsLegacyFormat() {
        AnalysisDefinition definition = new AnalysisReader().readLegacy(LEGACY_DATA_MODEL, LEGACY_CHARTS);

        assertEquals(AnalysisDefinition.Format.LEGACY, definition.getFormat());
        assertFalse(definition.isAutoSize());
        assertEquals("last7Days", definition.getLegacyTimeFrame());
        assertEquals(2, definition.getCharts().size());

        ChartDefinition details = definition.getCharts().get(0);
        ChartDefinition overview = definition.getCharts().get(1);
        assertEquals("Details", details.getName());
        assertEquals(AnalysisChartType.TABLE, details.getType());
        assertEquals(-1d, details.getHeight(), 0d);
        assertEquals("Overview", overview.getName());
        assertEquals(AnalysisChartType.AREA, overview.getType());
        assertEquals(250d, overview.getHeight(), 0d);
        assertEquals(20d, overview.getGroupingInterval(), 0d);
        assertEquals(1, overview.getMinFractionDigits());
        assertEquals("GREEN_YELLOW_RED", overview.getColorMapping());

        /* row of the clean data object 202 in both charts, row 203 only in "Details", 204 in none */
        assertEquals(1, overview.getRows().size());
        RowDefinition meter1 = overview.getRows().get(0);
        assertEquals(202, meter1.getObjectId());
        assertEquals("Value", meter1.getAttribute());
        assertEquals("Meter 1", meter1.getName());
        assertEquals("#ff0000", meter1.getColor());
        assertEquals(1, meter1.getAxis());
        assertEquals(AnalysisChartType.COLUMN, meter1.getChartType());
        assertEquals("kWh", meter1.getUnitLabel());
        assertEquals("0-202-Value", meter1.getKey());

        assertEquals(2, details.getRows().size());
        assertEquals(202, details.getRows().get(0).getObjectId());
        RowDefinition meter2 = details.getRows().get(1);
        assertEquals(203, meter2.getObjectId());
        assertEquals("#00ff00", meter2.getColor());
        assertEquals(AnalysisChartType.DEFAULT, meter2.getChartType());
        assertTrue(meter2.isCalculation());
        assertEquals(-1, meter2.getCalculationId());
    }

    @Test
    public void legacyChartsWithoutIdsAreNumberedByName() {
        String charts = "[{\"name\":\"Zeta\"},{\"name\":\"Alpha\"}]";
        String rows = "[{\"name\":\"R\",\"object\":\"7\",\"unit\":\"" + UNIT_KWH + "\",\"selectedCharts\":\"Zeta\"}]";
        AnalysisDefinition definition = new AnalysisReader().readLegacy(rows, charts);
        assertEquals("Alpha", definition.getCharts().get(0).getName());
        assertEquals(0, definition.getCharts().get(0).getId());
        assertEquals(AnalysisChartType.LINE, definition.getCharts().get(0).getType());
        assertEquals(1, definition.getCharts().get(1).getId());
        assertEquals(1, definition.getCharts().get(1).getRows().size());
        assertTrue(definition.getCharts().get(0).getRows().isEmpty());
    }

    @Test
    public void readsFromObjectAndResolvesPermissions() throws Exception {
        JEVisDataSource ds = mock(JEVisDataSource.class);
        JEVisObject analysis = mock(JEVisObject.class);
        when(analysis.getID()).thenReturn(900L);
        when(analysis.getDataSource()).thenReturn(ds);
        JEVisAttribute file = mock(JEVisAttribute.class);
        JEVisSample sample = mock(JEVisSample.class);
        when(analysis.getAttribute(AnalysisReader.ANALYSIS_FILE_ATTRIBUTE_NAME)).thenReturn(file);
        when(file.hasSample()).thenReturn(true);
        when(file.getLatestSample()).thenReturn(sample);
        when(sample.getValueAsFile()).thenReturn(new JEVisFileImp("analysis.json", ANALYSIS_FILE.getBytes(StandardCharsets.UTF_8)));

        JEVisObject readable = mock(JEVisObject.class);
        when(readable.getName()).thenReturn("Meter A");
        when(readable.getJEVisClassName()).thenReturn("Data");
        when(ds.getObject(101L)).thenReturn(readable);
        when(ds.getObject(102L)).thenReturn(null);
        JEVisObject cleanParent = mock(JEVisObject.class);
        when(cleanParent.getName()).thenReturn("Meter C");
        when(cleanParent.getJEVisClassName()).thenReturn("Data");
        JEVisObject clean = mock(JEVisObject.class);
        when(clean.getName()).thenReturn("Clean");
        when(clean.getJEVisClassName()).thenReturn("Clean Data");
        when(clean.getParents()).thenReturn(Collections.singletonList(cleanParent));
        when(ds.getObject(103L)).thenReturn(clean);

        AnalysisDefinition definition = new AnalysisReader().read(analysis);

        assertEquals(Long.valueOf(900L), definition.getId());
        RowDefinition one = definition.findRow("1-101-Value");
        assertTrue(one.getReadable());
        assertEquals("Row One", one.getDisplayName());
        RowDefinition two = definition.findRow("1-102-Value");
        assertFalse(two.getReadable());
        assertNull(two.getDisplayName());
        RowDefinition third = definition.findRow("0-103-Value");
        assertEquals("Meter C", third.getObjectName());
        assertEquals("Third", third.getDisplayName());
    }

    @Test
    public void readsLegacyFromObjectAndSkipsNonDataObjects() throws Exception {
        JEVisDataSource ds = mock(JEVisDataSource.class);
        JEVisObject analysis = mock(JEVisObject.class);
        when(analysis.getID()).thenReturn(901L);
        when(analysis.getDataSource()).thenReturn(ds);
        JEVisAttribute chartsAttribute = stringAttribute(LEGACY_CHARTS);
        JEVisAttribute dataModelAttribute = stringAttribute(LEGACY_DATA_MODEL);
        when(analysis.getAttribute(AnalysisReader.CHARTS_ATTRIBUTE_NAME)).thenReturn(chartsAttribute);
        when(analysis.getAttribute(AnalysisReader.DATA_MODEL_ATTRIBUTE_NAME)).thenReturn(dataModelAttribute);
        JEVisAttribute perScreen = mock(JEVisAttribute.class);
        JEVisSample perScreenSample = mock(JEVisSample.class);
        when(perScreenSample.getValueAsLong()).thenReturn(4L);
        when(perScreen.getLatestSample()).thenReturn(perScreenSample);
        when(analysis.getAttribute(AnalysisReader.NUMBER_OF_CHARTS_PER_SCREEN_ATTRIBUTE_NAME)).thenReturn(perScreen);

        /* 202: clean data below a data object; 203: not readable; nothing else */
        JEVisObject data = mock(JEVisObject.class);
        when(data.getJEVisClassName()).thenReturn("Data");
        when(data.getName()).thenReturn("Meter");
        JEVisObject clean = mock(JEVisObject.class);
        when(clean.getJEVisClassName()).thenReturn("Clean Data");
        when(clean.getParents()).thenReturn(Collections.singletonList(data));
        JEVisAttribute cleanValue = mock(JEVisAttribute.class);
        when(clean.getAttribute("Value")).thenReturn(cleanValue);
        when(ds.getObject(202L)).thenReturn(clean);
        when(ds.getObject(203L)).thenReturn(null);

        AnalysisDefinition definition = new AnalysisReader().read(analysis);
        assertEquals(AnalysisDefinition.Format.LEGACY, definition.getFormat());
        assertEquals(4, definition.getChartsPerScreen());
        assertEquals(2, definition.getHorizontalPies());
        assertEquals(3, definition.getHorizontalTables());
        List<RowDefinition> details = definition.getCharts().get(0).getRows();
        assertEquals(Arrays.asList(202L, 203L), Arrays.asList(details.get(0).getObjectId(), details.get(1).getObjectId()));
        assertTrue(details.get(0).getReadable());
        assertEquals("Meter", details.get(0).getObjectName());
        assertFalse(details.get(1).getReadable());
    }

    @Test
    public void colorsLikeJavaFx() {
        assertEquals("#00538a", WebColor.toHex("0x00538aff"));
        assertEquals("#00538a", WebColor.toHex("#00538A"));
        assertEquals("#add8e6", WebColor.toHex("lightblue"));
        assertEquals("#ffaa00", WebColor.toHex("#fa0"));
        assertEquals("#ff8000", WebColor.toHex("rgb(255,128,0)"));
        assertEquals("#123456", WebColor.toHex("123456"));
        assertEquals("#000000", WebColor.toHex("nonsense", "#000000"));
    }

    private static JEVisAttribute stringAttribute(String value) throws JEVisException {
        JEVisAttribute attribute = mock(JEVisAttribute.class);
        JEVisSample sample = mock(JEVisSample.class);
        when(sample.getValueAsString()).thenReturn(value);
        when(attribute.hasSample()).thenReturn(true);
        when(attribute.getLatestSample()).thenReturn(sample);
        return attribute;
    }
}
