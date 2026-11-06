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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.commons.chart.*;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ResourceWebAnalysisTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void parsesAFullRequest() {
        EvaluationSettings s = ResourceWebAnalysis.parseSettings("{\"timeFrame\":\"LAST_7_DAYS\",\"aggregation\":\"DAILY\","
                + "\"manipulation\":\"SUM\",\"timeZone\":\"Europe/Berlin\"}");
        assertEquals(TimeFrame.LAST_7_DAYS, s.getTimeFrame());
        assertEquals(AggregationPeriod.DAILY, s.getAggregation());
        assertEquals(ManipulationMode.SUM, s.getManipulation());
        assertEquals(DateTimeZone.forID("Europe/Berlin"), s.getZone());
    }

    @Test
    public void unknownValuesFallBackToTheDefaults() {
        EvaluationSettings s = ResourceWebAnalysis.parseSettings("{\"timeFrame\":\"PREVIEW\",\"aggregation\":\"x\","
                + "\"manipulation\":null,\"timeZone\":\"Mars/Base\"}");
        assertEquals(TimeFrame.TODAY, s.getTimeFrame());
        assertEquals(AggregationPeriod.NONE, s.getAggregation());
        assertEquals(ManipulationMode.NONE, s.getManipulation());
        assertEquals(DateTimeZone.getDefault(), s.getZone());
        assertEquals(TimeFrame.TODAY, ResourceWebAnalysis.parseSettings("").getTimeFrame());
    }

    @Test
    public void customNeedsFromAndTo() {
        EvaluationSettings s = ResourceWebAnalysis.parseSettings("{\"timeFrame\":\"CUSTOM\",\"from\":\"2026-03-01T00:00:00Z\","
                + "\"to\":\"2026-03-02T00:00:00Z\",\"timeZone\":\"Europe/Berlin\"}");
        assertEquals(new DateTime("2026-03-01T00:00:00Z").getMillis(), s.getFrom().getMillis());
        assertEquals(DateTimeZone.forID("Europe/Berlin"), s.getFrom().getZone());
        for (String bad : Arrays.asList("{\"timeFrame\":\"CUSTOM\"}",
                "{\"timeFrame\":\"CUSTOM\",\"from\":\"2026-03-02T00:00:00Z\",\"to\":\"2026-03-01T00:00:00Z\"}",
                "{\"timeFrame\":\"CUSTOM\",\"from\":\"yesterday\",\"to\":\"2026-03-01T00:00:00Z\"}", "[1]", "{")) {
            try {
                ResourceWebAnalysis.parseSettings(bad);
                fail("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void configHidesUnreadableRows() throws Exception {
        AnalysisDefinition def = new AnalysisDefinition();
        ChartDefinition chart = new ChartDefinition();
        chart.setId(0);
        chart.setName("Consumption");
        chart.setType(AnalysisChartType.LINE);
        chart.setHeight(Double.NaN);
        RowDefinition readable = new RowDefinition();
        readable.setKey("0-1-Value");
        readable.setObjectId(1);
        readable.setAttribute("Value");
        readable.setName("Meter A");
        readable.setUnitLabel("kWh");
        readable.setReadable(true);
        RowDefinition hidden = new RowDefinition();
        hidden.setKey("0-2-Value");
        hidden.setObjectId(2);
        hidden.setName("Secret");
        hidden.setUnitLabel("kWh");
        hidden.setReadable(false);
        chart.getRows().addAll(Arrays.asList(readable, hidden));
        def.getCharts().add(chart);

        Map<String, String> names = new HashMap<>();
        names.put("de", "Verbrauch");
        names.put("en", " ");
        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(ResourceWebAnalysis.configJson(7, "Usage", names, def)));
        assertEquals(7, json.get("id").asLong());
        assertEquals("Verbrauch", json.get("names").get("de").asText());
        assertFalse(json.get("names").has("en"));
        assertEquals("TODAY", json.get("defaults").get("timeFrame").asText());
        JsonNode c = json.get("charts").get(0);
        assertEquals("LINE", c.get("type").asText());
        assertTrue(c.get("height").isNull());
        assertEquals("Meter A", c.get("rows").get(0).get("name").asText());
        assertTrue(c.get("rows").get(0).get("readable").asBoolean());
        JsonNode h = c.get("rows").get(1);
        assertFalse(h.get("readable").asBoolean());
        assertTrue(h.get("name").isNull());
        assertTrue(h.get("unit").isNull());
    }

    @Test
    public void dataWritesSamplesAndStats() throws Exception {
        AnalysisResult result = new AnalysisResult();
        DateTimeZone zone = DateTimeZone.forID("Europe/Berlin");
        result.setFrom(new DateTime(2026, 3, 1, 0, 0, zone));
        result.setTo(new DateTime(2026, 3, 1, 23, 59, zone));
        ChartResult chart = new ChartResult(0);
        RowResult ok = new RowResult("0-1-Value", RowStatus.OK, null);
        ok.setUnit("kWh");
        ok.setSamples(Arrays.asList(new ChartSample(new DateTime(1000L, zone), 1.5, null),
                new ChartSample(new DateTime(2000L, zone), Double.NaN, "gap"),
                new ChartSample(new DateTime(3000L, zone), "open", null)));
        RowStatistics stats = new RowStatistics();
        stats.setMin(1.5);
        stats.setMax(1.5);
        stats.setSum(Double.POSITIVE_INFINITY);
        stats.setCount(1);
        ok.setStats(stats);
        chart.getRows().add(ok);
        chart.getRows().add(new RowResult("0-2-Value", RowStatus.NO_PERMISSION, null));
        RowResult pie = new RowResult("0-3-Value", RowStatus.OK, null);
        pie.setSamples(Arrays.asList(new ChartSample(new DateTime(1000L, zone), 2.0, null), new ChartSample(new DateTime(2000L, zone), 3.0, null)));
        pie.setPieValue(5.0);
        chart.getRows().add(pie);
        result.getCharts().add(chart);

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(ResourceWebAnalysis.dataJson(result)));
        assertEquals("2026-03-01T00:00:00.000+01:00", json.get("from").asText());
        JsonNode row = json.get("charts").get(0).get("rows").get(0);
        assertEquals("OK", row.get("status").asText());
        assertEquals(1000L, row.get("samples").get(0).get(0).asLong());
        assertEquals(1.5, row.get("samples").get(0).get(1).asDouble(), 0);
        assertTrue(row.get("samples").get(1).get(1).isNull());
        assertEquals("gap", row.get("samples").get(1).get(2).asText());
        assertEquals("open", row.get("samples").get(2).get(1).asText());
        assertTrue(row.get("stats").get("sum").isNull());
        assertTrue(row.get("stats").get("avg").isNull());
        JsonNode denied = json.get("charts").get(0).get("rows").get(1);
        assertEquals("NO_PERMISSION", denied.get("status").asText());
        assertEquals(0, denied.get("samples").size());
        assertFalse(denied.has("pieValue"));
        JsonNode pieRow = json.get("charts").get(0).get("rows").get(2);
        assertEquals(5.0, pieRow.get("pieValue").asDouble(), 0);
        assertEquals(0, pieRow.get("samples").size());
    }
}
