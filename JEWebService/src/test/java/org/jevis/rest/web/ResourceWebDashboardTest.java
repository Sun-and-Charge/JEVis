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
import jakarta.ws.rs.core.Response;
import org.jevis.commons.chart.*;
import org.jevis.commons.dashboard.*;
import org.jevis.commons.ws.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebDashboardTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");

    @Test
    public void parsesTheRequest() {
        DashboardRequest r = ResourceWebDashboard.parseRequest("{\"timeFrame\":\"P1W\",\"date\":\"2026-11-06T12:00:00+01:00\","
                + "\"timeZone\":\"Europe/Berlin\"}");
        assertEquals("P1W", r.getTimeFrame());
        assertEquals(BERLIN, r.getZone());
        assertEquals(new DateTime("2026-11-06T11:00:00Z").getMillis(), r.getDate().getMillis());

        DashboardRequest empty = ResourceWebDashboard.parseRequest("");
        assertNull(empty.getTimeFrame());
        assertNull(empty.getDate());
        assertEquals(DateTimeZone.getDefault(), ResourceWebDashboard.parseRequest("{\"timeZone\":\"Mars/Base\"}").getZone());

        DashboardRequest custom = ResourceWebDashboard.parseRequest("{\"timeFrame\":\"CUSTOM\",\"from\":\"2026-03-01T00:00:00Z\","
                + "\"to\":\"2026-03-02T00:00:00Z\"}");
        assertNotNull(custom.getFrom());
        for (String bad : Arrays.asList("{\"timeFrame\":\"CUSTOM\"}", "{\"date\":\"tomorrow\"}", "[1]", "{",
                "{\"timeFrame\":\"CUSTOM\",\"from\":\"2026-03-02T00:00:00Z\",\"to\":\"2026-03-01T00:00:00Z\"}")) {
            try {
                ResourceWebDashboard.parseRequest(bad);
                fail("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void configJson() throws Exception {
        DashboardDefinition d = new DashboardDefinition();
        d.setZoom(97d);
        d.setTimeFrame("P1W");
        WidgetDefinition chart = new WidgetDefinition();
        chart.setUuid(12);
        chart.setType("Chart");
        AnalysisDefinition data = new AnalysisDefinition();
        ChartDefinition c = new ChartDefinition();
        c.setId(0);
        c.setType(AnalysisChartType.COLUMN);
        RowDefinition row = new RowDefinition();
        row.setKey("0-1-Value");
        row.setObjectId(1);
        row.setName("Row A");
        row.setReadable(true);
        c.getRows().add(row);
        data.getCharts().add(c);
        chart.setData(data);
        d.getWidgets().add(chart);
        WidgetDefinition gauge = new WidgetDefinition();
        gauge.setUuid(13);
        gauge.setType("Gauge");
        gauge.getConfig().put("maximum", Double.NaN);
        d.getWidgets().add(gauge);
        List<DashboardTimeFrames.Option> options = Arrays.asList(new DashboardTimeFrames.Option("P1D", null, false),
                new DashboardTimeFrames.Option("2005", "Period X", true));

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(ResourceWebDashboard.configJson(1002, "Overview",
                Collections.singletonMap("de", "Übersicht"), d, options)));
        assertEquals("FIT_HEIGHT", json.get("zoom").asText());
        assertEquals("P1W", json.get("timeFrame").asText());
        assertEquals("Period X", json.get("timeFrames").get(1).get("name").asText());
        assertTrue(json.get("timeFrames").get(1).get("custom").asBoolean());
        assertFalse(json.get("timeFrames").get(0).has("name"));
        JsonNode w = json.get("widgets").get(0);
        assertEquals("12", w.get("uuid").asText());
        assertTrue(w.get("uuid").isTextual());
        assertEquals("COLUMN", w.get("chart").get("type").asText());
        assertEquals("Row A", w.get("rows").get(0).get("name").asText());
        assertTrue(w.get("timeFrame").isNull());
        JsonNode g = json.get("widgets").get(1);
        assertTrue(g.get("chart").isNull());
        assertTrue(g.get("config").get("maximum").isNull());
    }

    @Test
    public void dataJson() throws Exception {
        DashboardResult result = new DashboardResult();
        result.setTimeFrame("P1D");
        result.setFrom(new DateTime(2026, 11, 6, 0, 0, BERLIN));
        result.setTo(new DateTime(2026, 11, 6, 23, 59, 59, 999, BERLIN));
        result.setPrevious(new DateTime(2026, 11, 5, 12, 0, BERLIN));

        DashboardResult.WidgetResult value = new DashboardResult.WidgetResult(DashboardResult.WidgetResult.Kind.VALUE);
        RowResult v = new RowResult("0-1-Value", RowStatus.OK, null);
        v.setUnit("kWh");
        v.setValue(1234.5);
        v.setCount(96);
        v.setLastTimestamp(new DateTime(2026, 11, 6, 11, 45, BERLIN));
        v.setSamples(Collections.singletonList(new ChartSample(new DateTime(2026, 11, 6, 0, 0, BERLIN), 1234.5, null)));
        value.getRows().add(v);
        result.getWidgets().put("7", value);

        DashboardResult.WidgetResult text = new DashboardResult.WidgetResult(DashboardResult.WidgetResult.Kind.TEXT);
        RowResult t = new RowResult("0-2-Value", RowStatus.OK, null);
        t.setText("open");
        text.getRows().add(t);
        result.getWidgets().put("16", text);

        DashboardResult.WidgetResult alarms = new DashboardResult.WidgetResult(DashboardResult.WidgetResult.Kind.ALARMS);
        alarms.setAlarmCount(3);
        result.getWidgets().put("15", alarms);

        DashboardResult.WidgetResult series = new DashboardResult.WidgetResult(DashboardResult.WidgetResult.Kind.SERIES);
        RowResult s = new RowResult("0-3-Value", RowStatus.OK, null);
        s.setSamples(Collections.singletonList(new ChartSample(new DateTime(1000L, BERLIN), Double.NaN, null)));
        s.setCount(1);
        series.getRows().add(s);
        result.getWidgets().put("12", series);

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(ResourceWebDashboard.dataJson(result)));
        assertEquals("2026-11-06T00:00:00.000+01:00", json.get("from").asText());
        assertEquals("2026-11-05T12:00:00.000+01:00", json.get("previous").asText());
        assertTrue(json.get("next").isNull());
        JsonNode row = json.get("widgets").get("7").get("rows").get(0);
        assertEquals(1234.5, row.get("value").asDouble(), 0);
        assertEquals(96, row.get("count").asInt());
        assertEquals("2026-11-06T11:45:00.000+01:00", row.get("lastTimestamp").asText());
        assertFalse(row.has("samples"));
        assertEquals("open", json.get("widgets").get("16").get("rows").get(0).get("text").asText());
        assertEquals(3, json.get("widgets").get("15").get("alarmCount").asInt());
        assertFalse(json.get("widgets").get("15").has("rows"));
        JsonNode seriesRow = json.get("widgets").get("12").get("rows").get(0);
        assertTrue(seriesRow.get("samples").get(0).get(1).isNull());
        assertEquals(1, seriesRow.get("count").asInt());
    }

    @Test
    public void onlyImagesAreServed() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        assertEquals("image/png", ResourceWebDashboard.contentType(png));
        assertEquals("image/jpeg", ResourceWebDashboard.contentType(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}));
        assertEquals("image/gif", ResourceWebDashboard.contentType("GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("image/svg+xml", ResourceWebDashboard.contentType(
                "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ResourceWebDashboard.contentType("<html><body>x</body></html>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ResourceWebDashboard.contentType(null));

        Response ok = ResourceWebDashboard.image(png);
        assertEquals(200, ok.getStatus());
        assertEquals("private, max-age=300", ok.getHeaderString("Cache-Control"));
        Response svg = ResourceWebDashboard.image("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8));
        assertNotNull(svg.getHeaderString("Content-Security-Policy"));
        assertEquals(404, ResourceWebDashboard.image(new byte[]{1, 2, 3, 4}).getStatus());
    }

    private static JsonObject obj(long id, String jevisClass) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName("Object " + id);
        object.setJevisClass(jevisClass);
        return object;
    }

    @Test
    public void startDashboard() {
        Map<Long, JsonObject> readable = new HashMap<>();
        readable.put(10L, obj(10, ResourceWebNavigation.CLASS_DASHBOARD));
        readable.put(11L, obj(11, ResourceWebNavigation.CLASS_DASHBOARD));
        readable.put(20L, obj(20, ResourceWebNavigation.CLASS_DASHBOARD_COLLECTION));
        readable.put(30L, obj(30, "Analysis"));
        assertEquals(Long.valueOf(10), ResourceWebNavigation.startDashboard("10", readable, id -> null));
        assertEquals(Long.valueOf(10), ResourceWebNavigation.startDashboard("10:Value", readable, id -> null));
        /* a collection: its first readable dashboard (12 is not readable) */
        assertEquals(Long.valueOf(11), ResourceWebNavigation.startDashboard("20", readable, id -> "12, 11,10"));
        assertNull(ResourceWebNavigation.startDashboard("20", readable, id -> null));
        assertNull(ResourceWebNavigation.startDashboard("30", readable, id -> null));
        assertNull(ResourceWebNavigation.startDashboard("99", readable, id -> null));
        assertNull(ResourceWebNavigation.startDashboard("", readable, id -> null));
        assertNull(ResourceWebNavigation.startDashboard(null, readable, id -> null));
    }
}
