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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.chart.RowDefinition;
import org.jevis.commons.chart.RowResult;
import org.jevis.commons.chart.RowStatus;
import org.jevis.commons.dashboard.*;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

/**
 * Editing of dashboards (stage E4).
 */
public class ResourceWebEditDashboardTest {

    private static final DateTime NOW = new DateTime(2026, 11, 12, 10, 30, 15, 0, ZONE);
    private static final DateTime EARLIER = new DateTime(2026, 11, 1, 8, 0, ZONE);
    private static final String FILE = ResourceWebEditDashboard.ATTRIBUTE_FILE;
    private static final String BACKGROUND = ResourceWebEditDashboard.ATTRIBUTE_BACKGROUND;
    private static final String DIRECTORY = ResourceWebEditDashboard.CLASS_DIRECTORY;
    private static final String DASHBOARD = ResourceWebEditDashboard.CLASS_DASHBOARD;
    private static final byte[] PNG = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    /**
     * Dashboard 10 as the Control Center writes it: a Value widget with Meter A (21) and the unreadable Meter B (23),
     * a title, a link to the unreadable analysis 43, the input widgets on the switch 24 and Meter A, an image (42);
     * unknown keys on the top level and in a widget
     */
    private static final String OVERVIEW = ("{'version':'1.2','Dash Board Color':'0xf4f4f4ff','backgroundMode':'default',"
            + "'Snap to Grid':false,'Show Grid':false,'X Axis Grid Interval':25.0,'Y Axis Grid Interval':25.0,"
            + "'Update Rate':10,'Zoom Factor':98.0,'width':1853.0,'height':876.0,'Data Period':'P1W','customTop':{'keep':1},"
            + "'Widget':["
            + "{'uuid':1,'WidgetType':'Value','title':'Electricity','Background Color':'0xffffffff','xPos':25.0,'yPos':50.0,"
            + "'width':200.0,'height':120.0,'layer':1,'decimals':2,'customWidgetKey':'keep',"
            + "'limit':{'source':2,'lowerLimit':true,'upperLimit':false,'lowerLimitColor':'0xff0000ff','upperLimitColor':'0x00ff00ff'},"
            + "'dataHandler':{'type':'AnalysisHandler','forcedInterval':'','chartModels':[{'chartId':0,'chartType':'LINE',"
            + "'chartData':[{'id':21,'attributeString':'Value','name':'Meter A','color':'0x00538aff','aggregationPeriod':'DAILY',"
            + "'manipulationMode':'NONE','chartType':'DEFAULT'},"
            + "{'id':23,'attributeString':'Value','name':'Secret meter','color':'0xff0000ff','aggregationPeriod':'MONTHLY'}]}]}},"
            + "{'uuid':2,'WidgetType':'Title','title':'Header','layer':2},"
            + "{'uuid':3,'WidgetType':'Analyse-Link','analysisLinker':{'graphAnalysisObject':43}},"
            + "{'uuid':4,'WidgetType':'Toggle Switch','decimals':0,'dataHandler':{'type':'AnalysisHandler','chartModels':[{'chartId':0,"
            + "'chartData':[{'id':24,'attributeString':'Value'}]}]}},"
            + "{'uuid':5,'WidgetType':'Slider','decimals':1,'minMax':{'minimum':0.0,'maximum':50.0},'dataHandler':"
            + "{'type':'AnalysisHandler','chartModels':[{'chartId':0,'chartData':[{'id':24,'attributeString':'Value'}]}]}},"
            + "{'uuid':6,'WidgetType':'PlusMinus','decimals':1,'minMax':{'Increment':2.5},'dataHandler':"
            + "{'type':'AnalysisHandler','chartModels':[{'chartId':0,'chartData':[{'id':24,'attributeString':'Value'}]}]}},"
            + "{'uuid':7,'WidgetType':'Value Editor','dataHandler':{'type':'AnalysisHandler','chartModels':[{'chartId':0,"
            + "'chartData':[{'id':21,'attributeString':'Value'}]}]}},"
            + "{'uuid':8,'WidgetType':'Image','image':{'objectID':'42'}},"
            + "{'uuid':9,'WidgetType':'Toggle Switch','dataHandler':{'type':'AnalysisHandler','chartModels':[{'chartId':0,"
            + "'chartData':[{'id':26,'attributeString':'Value'}]}]}}"
            + "]}").replace('\'', '"');

    /**
     * Dashboard 12: an old file with a widget without uuid and a legacy data handler
     */
    private static final String LEGACY = ("{'Dash Board Color':'0xffffffff','Data Period':'P1D','Widget':["
            + "{'uuid':4,'WidgetType':'Title','title':'A'},"
            + "{'WidgetType':'Gauge','dataHandler':{'type':'SimpleDataHandler','forcedInterval':'',"
            + "'data':[{'objectID':21,'attribute':'Value','name':'Meter A','unit':'kWh','aggregationPeriod':'DAILY'}]}}]}")
            .replace('\'', '"');

    /* organisation Musterwerk 1
         dashboard directory 2 > dashboards 10 (Overview), 11 (Locked), 12 (Legacy), 13 (Hidden), 14 (Empty, no file)
         dashboard directory 3 (Archiv, no create right)
         data directory 20 > Meter A 21 (Data) > Meter A clean 22 (Clean Data); Meter B 23 (not readable);
                             Switch 24 (Data); Meter C 25 (Data, calculated by 30); Setpoint 26 (Data, no write right)
         report 40, analysis 41, analysis 43 (not readable); image file 42 below dashboard 10 */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        store.add(obj(1, "Musterwerk", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(2, "Dashboards", DIRECTORY, 1),
                obj(3, "Archiv", DIRECTORY, 1),
                obj(10, "Overview", DASHBOARD, 2),
                obj(11, "Locked", DASHBOARD, 2),
                obj(12, "Legacy", DASHBOARD, 2),
                obj(13, "Hidden", DASHBOARD, 2),
                obj(14, "Empty", DASHBOARD, 2),
                obj(20, "Meters", "Data Directory", 1),
                obj(21, "Meter A", "Data", 20),
                obj(22, "Meter A clean", "Clean Data", 21),
                obj(23, "Meter B", "Data", 20),
                obj(24, "Switch", "Data", 20),
                obj(25, "Meter C", "Data", 20),
                obj(26, "Setpoint", "Data", 20),
                obj(40, "Monthly report", "Report", 1),
                obj(41, "Analysis Y", ResourceWebEditAnalysis.CLASS_ANALYSIS, 1),
                obj(42, "logo", ResourceWebIso.CLASS_FILE, 10),
                obj(43, "Secret analysis", ResourceWebEditAnalysis.CLASS_ANALYSIS, 1));
        store.hidden.addAll(Arrays.asList(13L, 23L, 43L));
        store.type("Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE)
                .type("Clean Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE)
                .type(ResourceWebIso.CLASS_FILE, null, ResourceWebIso.ATTRIBUTE_FILE, JEVisConstants.PrimitiveType.FILE)
                .type("Report", null)
                .type(DASHBOARD, null, FILE, JEVisConstants.PrimitiveType.FILE, BACKGROUND, JEVisConstants.PrimitiveType.FILE);
        store.write.addAll(Arrays.asList(2L, 10L, 12L, 14L, 21L, 24L));
        store.delete.add(10L);
        store.create.addAll(Arrays.asList(2L, 10L));
        store.file(10, FILE, EARLIER, "Overview_202611010800.json", OVERVIEW)
                .file(10, BACKGROUND, EARLIER, "wall.png", "PNG-BYTES")
                .file(11, FILE, EARLIER, "Locked_202611010800.json", "{\"Widget\":[]}")
                .file(12, FILE, EARLIER, "Legacy_202611010800.json", LEGACY);
        return store;
    }

    /**
     * Engine on the fake store: reads like W3 (rows resolved with the store), time frames, calculations; records the
     * evaluation and answers one row per data row
     */
    static final class FakeEngine implements ResourceWebEditDashboard.Engine {
        final FakeEditStore store;
        final Map<Long, Long> calculations = new HashMap<>();
        DashboardDefinition evaluated;
        Long context;
        DashboardRequest request;

        FakeEngine(FakeEditStore store) {
            this.store = store;
            calculations.put(25L, 30L);
        }

        @Override
        public DashboardDefinition read(JsonNode source) {
            DashboardDefinition definition = new DashboardReader().readDashboardFile(source);
            try {
                for (WidgetDefinition widget : definition.getWidgets()) {
                    ResourceWebEditAnalysis.resolveRows(store, widget.getData());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return definition;
        }

        @Override
        public ObjectNode convertLegacy(JsonNode handler) {
            return new DashboardReader().convertLegacyDataHandler(handler, null);
        }

        @Override
        public List<DashboardTimeFrames.Option> timeFrames() {
            List<DashboardTimeFrames.Option> options = new ArrayList<>();
            for (String id : DashboardTimeFrames.STANDARD) options.add(new DashboardTimeFrames.Option(id, null, false));
            options.add(new DashboardTimeFrames.Option("2005", "Last 4 weeks", true));
            return options;
        }

        @Override
        public boolean knownTimeFrame(String id) {
            return DashboardTimeFrames.STANDARD.contains(id) || DashboardTimeFrames.LAST_VALUE.equals(id) || "2005".equals(id);
        }

        @Override
        public long calculationId(long rowId) {
            return calculations.getOrDefault(rowId, -1L);
        }

        @Override
        public DashboardResult evaluate(DashboardDefinition definition, Long contextId, DashboardRequest request) {
            this.evaluated = definition;
            this.context = contextId;
            this.request = request;
            DashboardResult result = new DashboardResult();
            result.setTimeFrame(request.getTimeFrame() != null ? request.getTimeFrame() : definition.getTimeFrame());
            for (WidgetDefinition widget : definition.getWidgets()) {
                if (widget.getRows().isEmpty()) continue;
                DashboardResult.WidgetResult w = new DashboardResult.WidgetResult(WidgetDefinition.isInputType(widget.getType())
                        ? DashboardResult.WidgetResult.Kind.LATEST : DashboardResult.WidgetResult.Kind.VALUE);
                for (RowDefinition row : widget.getRows()) {
                    boolean readable = Boolean.TRUE.equals(row.getReadable());
                    RowResult r = new RowResult(row.getKey(), readable ? RowStatus.OK : RowStatus.NO_PERMISSION, null);
                    JsonSample latest = readable ? store.latest(row.getObjectId(), row.getAttribute()) : null;
                    if (latest != null) r.setValue(Double.parseDouble(latest.getValue()));
                    w.getRows().add(r);
                }
                result.getWidgets().put(String.valueOf(widget.getUuid()), w);
            }
            return result;
        }
    }

    private static JsonNode json(WebEdit.Reply reply) throws Exception {
        return FakeEditStore.json(reply);
    }

    private static JsonNode source(FakeEditStore store, long id) throws Exception {
        return json(ResourceWebEditDashboard.source(store, new FakeEngine(store), id)).get("source");
    }

    private static JsonNode stored(FakeEditStore store, long id) throws Exception {
        return FakeEditStore.MAPPER.readTree(store.latestFile(id, FILE));
    }

    private static ObjectNode widget(JsonNode source, int uuid) {
        for (JsonNode w : source.get("Widget")) {
            if (w.get("uuid").asInt() == uuid) return (ObjectNode) w;
        }
        return null;
    }

    private static String body(String version, JsonNode source) {
        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        if (version != null) body.put("version", version);
        if (source != null) body.set("source", source);
        return body.toString();
    }

    private static List<String> messages(WebEdit.Reply reply) throws Exception {
        assertEquals(400, reply.status);
        List<String> messages = new ArrayList<>();
        for (JsonNode m : json(reply).get("messages")) messages.add(m.asText());
        return messages;
    }

    private static boolean contains(List<String> messages, String part) {
        for (String m : messages) {
            if (m.contains(part)) return true;
        }
        return false;
    }

    private static String version(FakeEditStore store, long id) {
        return WebEdit.version(store.latest(id, FILE));
    }

    private static WebEdit.Reply update(FakeEditStore store, FakeEngine engine, long id, String body, DateTime now) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditDashboard.update(s, engine, id, body, now));
    }

    private static WebEdit.Reply create(FakeEditStore store, FakeEngine engine, String body, DateTime now) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditDashboard.create(s, engine, body, now));
    }

    private static WebEdit.Reply copy(FakeEditStore store, FakeEngine engine, long id, String body, DateTime now) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditDashboard.copy(s, engine, id, body, now));
    }

    private static WebEdit.Reply preview(FakeEditStore store, FakeEngine engine, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditDashboard.preview(s, engine, body));
    }

    // ---------------------------------------------------------------- reading

    @Test
    public void sourceIsTheStoredJsonNormalised() throws Exception {
        FakeEditStore store = store();
        JsonNode answer = json(ResourceWebEditDashboard.source(store, new FakeEngine(store), 10));
        assertEquals(WebEdit.version(store.latest(10, FILE)), answer.get("version").asText());
        JsonNode source = answer.get("source");
        assertEquals(1, source.get("customTop").get("keep").asInt());
        assertEquals("keep", widget(source, 1).get("customWidgetKey").asText());
        assertEquals(2, widget(source, 1).get("dataHandler").get("chartModels").get(0).get("chartData").size());

        /* legacy: uuid assigned (next free), data handler in the current format with aggregation and unit */
        JsonNode legacy = source(store, 12);
        JsonNode gauge = legacy.get("Widget").get(1);
        assertEquals(5, gauge.get("uuid").asInt());
        JsonNode handler = gauge.get("dataHandler");
        assertEquals("AnalysisHandler", handler.get("type").asText());
        JsonNode row = handler.get("chartModels").get(0).get("chartData").get(0);
        assertEquals(21, row.get("id").asLong());
        assertEquals("DAILY", row.get("aggregationPeriod").asText());
        assertEquals("kWh", row.get("unitLabel").asText());

        /* never saved: the Control Center's empty dashboard */
        JsonNode empty = json(ResourceWebEditDashboard.source(store, new FakeEngine(store), 14));
        assertEquals("none", empty.get("version").asText());
        assertEquals(0, empty.get("source").get("Widget").size());
        assertEquals("P1D", empty.get("source").get("Data Period").asText());
        assertEquals(1853d, empty.get("source").get("width").asDouble(), 0d);
        assertEquals("0xf4f4f4ff", empty.get("source").get("Dash Board Color").asText());

        assertEquals(404, ResourceWebEditDashboard.source(store, new FakeEngine(store), 13).status);
        assertEquals(404, ResourceWebEditDashboard.source(store, new FakeEngine(store), 21).status);
    }

    @Test
    public void getHasVersionRightsDirectoryAndCanEnter() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = ResourceWebEditDashboard.config(store, new FakeEngine(store), 10);
        assertEquals(200, reply.status);
        JsonNode json = json(reply);
        assertEquals(10, json.get("id").asLong());
        assertEquals("Overview", json.get("name").asText());
        assertEquals(version(store, 10), json.get("version").asText());
        assertTrue(json.get("canWrite").asBoolean());
        assertTrue(json.get("canDelete").asBoolean());
        assertEquals(2, json.get("directoryId").asLong());
        assertTrue(json.get("background").get("image").asBoolean());
        assertEquals("P1W", json.get("timeFrame").asText());
        Map<String, Boolean> canEnter = new HashMap<>();
        for (JsonNode w : json.get("widgets")) canEnter.put(w.get("uuid").asText(), w.get("canEnter").asBoolean());
        assertFalse(canEnter.get("1"));
        assertTrue(canEnter.get("4"));
        assertTrue(canEnter.get("5"));
        assertTrue(canEnter.get("6"));
        assertTrue(canEnter.get("7"));
        /* no write right on the setpoint */
        assertFalse(canEnter.get("9"));

        JsonNode locked = json(ResourceWebEditDashboard.config(store, new FakeEngine(store), 11));
        assertFalse(locked.get("canWrite").asBoolean());
        assertFalse(locked.get("canDelete").asBoolean());
        assertEquals("none", json(ResourceWebEditDashboard.config(store, new FakeEngine(store), 14)).get("version").asText());
        assertEquals(404, ResourceWebEditDashboard.config(store, new FakeEngine(store), 13).status);
        store.recycle(store.object(11));
        assertEquals(404, ResourceWebEditDashboard.config(store, new FakeEngine(store), 11).status);
    }

    @Test
    public void directories() throws Exception {
        FakeEditStore store = store();
        JsonNode json = json(ResourceWebEditDashboard.directories(store, "de"));
        assertEquals(2, json.size());
        assertEquals("Musterwerk / Archiv", json.get(0).get("path").asText());
        assertFalse(json.get(0).get("canCreate").asBoolean());
        assertEquals(2, json.get(1).get("id").asLong());
        assertTrue(json.get(1).get("canCreate").asBoolean());
    }

    // ---------------------------------------------------------------- save

    @Test
    public void putStoresTheSourceWithAllKeys() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        ObjectNode source = (ObjectNode) source(store, 10);
        widget(source, 2).put("title", "New header").put("Font Color", "#1a2a45");
        source.put("Dash Board Color", "#ff000080");
        ObjectNode added = FakeEditStore.MAPPER.createObjectNode();
        added.put("WidgetType", "Value").put("title", "Meter C");
        added.putObject("dataHandler").put("type", "AnalysisHandler").putArray("chartModels").addObject()
                .putArray("chartData").addObject().put("id", 25).put("calculation", true).put("color", "#00538a");
        ((ArrayNode) source.get("Widget")).add(added);

        WebEdit.Reply reply = update(store, engine, 10, body(version(store, 10), source), NOW);
        assertEquals(200, reply.status);
        JsonNode json = json(reply);
        assertEquals(WebEdit.version(store.latest(10, FILE)), json.get("version").asText());
        assertEquals(10, json.get("widgets").size());

        JsonNode written = stored(store, 10);
        assertEquals(1, written.get("customTop").get("keep").asInt());
        assertEquals("0xff000080", written.get("Dash Board Color").asText());
        assertEquals("New header", widget(written, 2).get("title").asText());
        assertEquals("0x1a2a45ff", widget(written, 2).get("Font Color").asText());
        assertEquals("keep", widget(written, 1).get("customWidgetKey").asText());
        /* the unreadable row of widget 1 stays as stored */
        JsonNode rows = widget(written, 1).get("dataHandler").get("chartModels").get(0).get("chartData");
        assertEquals(2, rows.size());
        assertEquals("Secret meter", rows.get(1).get("name").asText());
        assertEquals("MONTHLY", rows.get(1).get("aggregationPeriod").asText());
        /* the new widget: next free uuid, attribute, colour and calculation set */
        JsonNode newWidget = widget(written, 10);
        assertNotNull(newWidget);
        JsonNode newRow = newWidget.get("dataHandler").get("chartModels").get(0).get("chartData").get(0);
        assertEquals("Value", newRow.get("attributeString").asText());
        assertEquals("0x00538aff", newRow.get("color").asText());
        assertEquals(30L, newRow.get("calculationId").asLong());

        JsonSample sample = store.latest(10, FILE);
        assertEquals("Overview_202611121030.json", sample.getValue());
        assertEquals(NOW.getMillis(), (long) ResourceWebNotes.millis(sample.getTs()));
        assertTrue(store.logs.contains("CREATE_SAMPLE 10:Data Model File|1|web:dashboard-save"));
        assertTrue(store.waits.isEmpty());
        /* pretty printed like the Control Center */
        assertTrue(store.latestFile(10, FILE).contains("\n"));
    }

    @Test
    public void putChecksInOrder() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        JsonNode source = source(store, 10);
        String version = version(store, 10);
        assertEquals(404, update(store, engine, 13, body(version, source), NOW).status);
        assertEquals(404, update(store, engine, 21, body(version, source), NOW).status);
        assertEquals(403, update(store, engine, 11, body(version, source), NOW).status);
        WebEdit.Reply missing = update(store, engine, 10, body(null, source), NOW);
        assertEquals(400, missing.status);
        assertEquals("version missing", missing.body);
        ObjectNode invalid = source.deepCopy();
        invalid.put("width", 5);
        assertEquals(400, update(store, engine, 10, body("2020-01-01T00:00:00.000+01:00", invalid), NOW).status);
        WebEdit.Reply conflict = update(store, engine, 10, body("2020-01-01T00:00:00.000+01:00", source), NOW);
        assertEquals(409, conflict.status);
        assertEquals("Overview", json(conflict).get("name").asText());
        assertEquals(version, json(conflict).get("version").asText());
        assertEquals(1, store.count(10, FILE));
        /* never saved: version "none" */
        assertEquals(200, update(store, engine, 14, body("none", DashboardSource.empty()), NOW).status);
        assertEquals(1, store.count(14, FILE));
        assertEquals("Empty_202611121030.json", store.latest(14, FILE).getValue());
    }

    @Test
    public void validationListsAllProblems() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        ObjectNode source = (ObjectNode) source(store, 10);
        source.put("width", 50).put("Zoom Factor", 0).put("Data Period", "P2D").put("Dash Board Color", "red")
                .put("backgroundMode", "tile");
        widget(source, 2).put("WidgetType", "Clock").put("fontSize", 500).put("decimals", 30).put("width", 5);
        widget(source, 3).put("uuid", 2);
        ((ObjectNode) widget(source, 1).get("limit")).put("source", 99);
        ArrayNode rows = (ArrayNode) widget(source, 4).get("dataHandler").get("chartModels").get(0).get("chartData");
        rows.addObject().put("id", 23).put("attributeString", "Value");
        rows.addObject().put("id", 40).put("attributeString", "Value");
        rows.addObject().put("id", 21).put("attributeString", "Missing");
        rows.addObject().put("id", 21).put("aggregationPeriod", "SOMETIMES").put("color", "blue");
        rows.addObject().put("id", 21).put("calculation", true);
        widget(source, 5).putObject("dashboardLinker").put("dashboardObject", 41);
        widget(source, 6).putObject("reportLinker").put("reportObject", 43);

        WebEdit.Reply reply = update(store, engine, 10, body(version(store, 10), source), NOW);
        List<String> messages = messages(reply);
        for (String expected : Arrays.asList("Invalid width", "Invalid Zoom Factor", "Invalid Data Period: P2D",
                "Invalid Dash Board Color", "Invalid backgroundMode", "Unknown widget type in Widget[1]: Clock",
                "Invalid Widget[1].fontSize", "Invalid Widget[1].decimals", "Invalid Widget[1].width", "Duplicate uuid: 2",
                "Unknown widget in Widget[0].limit.source: 99", "Row 23 not found or not readable",
                "Row 40 is no data row (Report)", "Row 21 has no attribute Missing", "aggregationPeriod: SOMETIMES",
                "chartData[4].color", "Row 21 is not calculated", "Object 41 of Widget[4].dashboardLinker.dashboardObject is no Dashboard Analysis",
                "Object 43 of Widget[5].reportLinker.reportObject not found or not readable")) {
            assertTrue("missing message " + expected + " in " + messages, contains(messages, expected));
        }
        assertEquals(1, store.count(10, FILE));

        assertEquals(400, update(store, engine, 10, "{\"version\":\"none\",\"source\":[1]}", NOW).status);
        assertEquals(400, update(store, engine, 10, "not json", NOW).status);
    }

    @Test
    public void unreadableItemsOnlyIfUnchanged() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        ObjectNode source = (ObjectNode) source(store, 10);
        /* sent back: the unreadable row 23 (with changed settings) and the link to the unreadable analysis 43 */
        ((ObjectNode) widget(source, 1).get("dataHandler").get("chartModels").get(0).get("chartData").get(1))
                .put("name", "Changed").put("aggregationPeriod", "YEARLY");
        assertEquals(200, update(store, engine, 10, body(version(store, 10), source), NOW).status);
        JsonNode written = stored(store, 10);
        JsonNode secret = widget(written, 1).get("dataHandler").get("chartModels").get(0).get("chartData").get(1);
        assertEquals("Secret meter", secret.get("name").asText());
        assertEquals("MONTHLY", secret.get("aggregationPeriod").asText());
        assertEquals(43, widget(written, 3).get("analysisLinker").get("graphAnalysisObject").asLong());
        /* the same unreadable row and link in another widget: rejected */
        ObjectNode moved = (ObjectNode) source(store, 10);
        widget(moved, 2).putObject("analysisLinker").put("graphAnalysisObject", 43);
        widget(moved, 2).set("dataHandler", widget(moved, 1).get("dataHandler").deepCopy());
        List<String> messages = messages(update(store, engine, 10, body(version(store, 10), moved), NOW.plusMinutes(1)));
        assertTrue(messages.toString(), contains(messages, "Row 23 not found or not readable"));
        assertTrue(messages.toString(), contains(messages, "Object 43 of Widget[1].analysisLinker.graphAnalysisObject"));
        /* a new dashboard cannot get them at all */
        messages = messages(create(store, engine,
                "{\"directoryId\":2,\"name\":\"New\",\"source\":" + source(store, 10) + "}", NOW));
        assertTrue(contains(messages, "Row 23 not found or not readable"));
        assertTrue(contains(messages, "Object 43 of Widget[2].analysisLinker.graphAnalysisObject"));
    }

    @Test
    public void renameOnlyAndNameTaken() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        String version = version(store, 10);
        WebEdit.Reply taken = update(store, engine, 10, "{\"version\":\"" + version + "\",\"name\":\"Legacy\"}", NOW);
        assertEquals(409, taken.status);
        assertTrue(json(taken).get("nameTaken").asBoolean());
        assertEquals(12, json(taken).get("id").asLong());
        WebEdit.Reply renamed = update(store, engine, 10, "{\"version\":\"" + version + "\",\"name\":\" Übersicht \"}", NOW);
        assertEquals(200, renamed.status);
        assertEquals("Übersicht", json(renamed).get("name").asText());
        assertEquals(version, json(renamed).get("version").asText());
        assertEquals(Collections.singletonList("10 Übersicht"), store.renames);
        assertEquals(1, store.count(10, FILE));
        assertEquals(400, update(store, engine, 10, "{\"version\":\"" + version + "\"}", NOW).status);
    }

    /**
     * Dashboards saved by the Control Center may hold values outside the web's ranges (widgets narrower than 10 px)
     * and rows marked as calculated without a calculation: saving them unchanged must work, changing them to such
     * values must not (found with the local demo's "Showcase").
     */
    @Test
    public void storedValuesOutsideTheRangesMaySaveUnchanged() throws Exception {
        FakeEditStore store = store();
        String odd = OVERVIEW.replace("{\"uuid\":2,\"WidgetType\":\"Title\",\"title\":\"Header\",\"layer\":2}",
                        "{\"uuid\":2,\"WidgetType\":\"Title\",\"title\":\"Header\",\"layer\":2,\"width\":4.0,\"height\":2.0}")
                .replace("\"name\":\"Meter A\",\"color\":\"0x00538aff\"",
                        "\"name\":\"Meter A\",\"color\":\"0x00538aff\",\"calculation\":true,\"calculationId\":-1");
        assertNotEquals("the test data must change", OVERVIEW, odd);
        store.file(10, FILE, NOW.minusMinutes(5), "Overview_202611121025.json", odd);
        FakeEngine engine = new FakeEngine(store);
        JsonNode source = source(store, 10);
        /* the browser sends the stored 4.0 back as 4 */
        widget(source, 2).put("width", 4).put("height", 2);
        WebEdit.Reply unchanged = update(store, engine, 10, body(version(store, 10), source), NOW);
        assertEquals(String.valueOf(unchanged.body), 200, unchanged.status);
        assertEquals(4d, widget(stored(store, 10), 2).get("width").asDouble(), 0d);

        JsonNode changed = source(store, 10);
        widget(changed, 2).put("width", 5.0);
        WebEdit.Reply refused = update(store, engine, 10, body(version(store, 10), changed), NOW.plusSeconds(5));
        assertEquals(400, refused.status);
        assertTrue(String.valueOf(refused.body), String.valueOf(refused.body).contains("width"));
    }

    @Test
    public void saveInTheSameSecondWaits() throws Exception {
        FakeEditStore store = store();
        store.file(10, FILE, NOW, "Overview_202611121030.json", OVERVIEW);
        JsonNode source = source(store, 10);
        assertEquals(200, update(store, new FakeEngine(store), 10,
                body(version(store, 10), source), NOW.plusMillis(300)).status);
        assertEquals(Collections.singletonList(NOW.plusSeconds(1)), store.waits);
        assertEquals(NOW.plusSeconds(1).getMillis(), (long) ResourceWebNotes.millis(store.latest(10, FILE).getTs()));
        assertEquals(3, store.count(10, FILE));
    }

    @Test
    public void fileNameAsTheControlCenter() {
        assertEquals("bersichtHalle2_202611121030.json", ResourceWebEditDashboard.fileName("Übersicht Halle 2", NOW));
        assertEquals("_202611121030.json", ResourceWebEditDashboard.fileName("../..", NOW));
    }

    // ---------------------------------------------------------------- create, copy, delete

    @Test
    public void createWithAndWithoutSource() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        WebEdit.Reply reply = create(store, engine, "{\"directoryId\":2,\"name\":\"Neu\"}", NOW);
        assertEquals(201, reply.status);
        JsonNode json = json(reply);
        long id = json.get("id").asLong();
        assertEquals(DASHBOARD, store.object(id).getJevisClass());
        assertEquals(WebEdit.version(store.latest(id, FILE)), json.get("version").asText());
        assertTrue(json.get("canWrite").asBoolean());
        assertEquals(2, json.get("directoryId").asLong());
        assertEquals("Neu_202611121030.json", store.latest(id, FILE).getValue());
        assertEquals(0, stored(store, id).get("Widget").size());
        assertTrue(store.logs.contains("CREATE_OBJECT " + id + ":Neu|web:dashboard-create"));

        ObjectNode source = DashboardSource.empty();
        source.putArray("Widget").addObject().put("WidgetType", "Title").put("title", "Hello");
        WebEdit.Reply withSource = create(store, engine,
                "{\"directoryId\":2,\"name\":\"Neu 2\",\"source\":" + source + "}", NOW);
        assertEquals(201, withSource.status);
        JsonNode written = stored(store, json(withSource).get("id").asLong());
        assertEquals(1, written.get("Widget").get(0).get("uuid").asInt());

        WebEdit.Reply taken = create(store, engine, "{\"directoryId\":2,\"name\":\"Overview\"}", NOW);
        assertEquals(409, taken.status);
        assertEquals(10, json(taken).get("id").asLong());
        assertEquals(403, create(store, engine, "{\"directoryId\":3,\"name\":\"X\"}", NOW).status);
        assertEquals(404, create(store, engine, "{\"directoryId\":20,\"name\":\"X\"}", NOW).status);
        assertEquals(400, create(store, engine, "{\"name\":\"X\"}", NOW).status);
        assertTrue(contains(messages(create(store, engine, "{\"directoryId\":2,\"name\":\" \"}", NOW)), "Missing name"));
    }

    @Test
    public void failedCreateIsRolledBack() throws Exception {
        FakeEditStore store = store();
        store.failFileWrites = true;
        int before = store.objects.size();
        try {
            create(store, new FakeEngine(store), "{\"directoryId\":2,\"name\":\"Broken\"}", NOW);
            fail("no exception");
        } catch (java.io.IOException expected) {
            // ok
        }
        assertEquals(before, store.objects.size());
        assertEquals(1, store.deletedObjects.size());
        assertTrue(store.logs.get(store.logs.size() - 1).endsWith("web:dashboard-create-rollback"));
    }

    @Test
    public void copyKeepsTheSourceAndBackground() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        WebEdit.Reply reply = copy(store, engine, 10, "{\"directoryId\":2,\"name\":\"Overview 2\"}", NOW);
        assertEquals(201, reply.status);
        long id = json(reply).get("id").asLong();
        JsonNode written = stored(store, id);
        assertEquals(1, written.get("customTop").get("keep").asInt());
        assertEquals(42, widget(written, 8).get("image").get("objectID").asLong());
        assertEquals("wall.png", store.latest(id, BACKGROUND).getValue());
        assertEquals("PNG-BYTES", store.latestFile(id, BACKGROUND));
        assertTrue(json(reply).get("background").get("image").asBoolean());
        assertTrue(store.logs.contains("CREATE_SAMPLE " + id + ":Background|1|web:dashboard-copy"));

        /* with a source: the unreadable stored items of the original may be kept */
        JsonNode source = source(store, 10);
        WebEdit.Reply withSource = copy(store, engine, 10,
                "{\"directoryId\":2,\"name\":\"Overview 3\",\"source\":" + source + "}", NOW);
        assertEquals(201, withSource.status);
        assertEquals(2, widget(stored(store, json(withSource).get("id").asLong()), 1).get("dataHandler")
                .get("chartModels").get(0).get("chartData").size());

        assertEquals(404, copy(store, engine, 13, "{\"directoryId\":2,\"name\":\"X\"}", NOW).status);
        assertEquals(403, copy(store, engine, 10, "{\"directoryId\":3,\"name\":\"X\"}", NOW).status);
        assertEquals(409, copy(store, engine, 10, "{\"directoryId\":2,\"name\":\"Overview\"}", NOW).status);
    }

    @Test
    public void deleteMovesToTheRecycleBin() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        assertEquals(403, ResourceWebEditDashboard.delete(store, engine, 12, version(store, 12)).status);
        assertEquals(400, ResourceWebEditDashboard.delete(store, engine, 10, null).status);
        assertEquals(409, ResourceWebEditDashboard.delete(store, engine, 10, "2020-01-01T00:00:00.000+01:00").status);
        assertEquals(204, ResourceWebEditDashboard.delete(store, engine, 10, version(store, 10)).status);
        assertEquals(Collections.singletonList(10L), store.recycled);
        assertTrue(store.deletedObjects.isEmpty());
        assertTrue(store.logs.contains("MARK_AS_DELETE_OBJECT 10:Overview|web:dashboard-delete"));
        assertEquals(404, ResourceWebEditDashboard.delete(store, engine, 10, version(store, 10)).status);
    }

    // ---------------------------------------------------------------- uploads

    @Test
    public void backgroundUpload() throws Exception {
        FakeEditStore store = store();
        assertEquals(204, ResourceWebEditDashboard.background(store, 10, "C:\\pics\\wall 2.png", in(PNG), 1000, NOW).status);
        assertEquals("wall 2.png", store.latest(10, BACKGROUND).getValue());
        assertTrue(store.logs.contains("CREATE_SAMPLE 10:Background|1|web:dashboard-background"));
        assertEquals(400, call(s -> ResourceWebEditDashboard.background(store, 10, "a.png",
                in("not an image".getBytes(StandardCharsets.UTF_8)), 1000, NOW)).status);
        assertEquals(413, call(s -> ResourceWebEditDashboard.background(store, 10, "a.png", in(PNG), 4, NOW)).status);
        assertEquals(400, call(s -> ResourceWebEditDashboard.background(store, 10, "a.png", null, 1000, NOW)).status);
        assertEquals(403, ResourceWebEditDashboard.background(store, 11, "a.png", in(PNG), 1000, NOW).status);
        assertEquals(404, ResourceWebEditDashboard.background(store, 13, "a.png", in(PNG), 1000, NOW).status);
    }

    @Test
    public void imageUploadCreatesAFileChild() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = ResourceWebEditDashboard.image(store, 10, "Lageplan.png", in(PNG), 1000, NOW);
        assertEquals(201, reply.status);
        long id = json(reply).get("objectId").asLong();
        assertEquals("Lageplan", json(reply).get("name").asText());
        assertEquals(ResourceWebIso.CLASS_FILE, store.object(id).getJevisClass());
        assertEquals(10, store.object(id).getParent());
        assertEquals("Lageplan.png", store.latest(id, ResourceWebIso.ATTRIBUTE_FILE).getValue());
        assertTrue(store.logs.contains("CREATE_OBJECT " + id + ":Lageplan|web:dashboard-image"));
        assertEquals(403, ResourceWebEditDashboard.image(store, 12, "a.png", in(PNG), 1000, NOW).status);
        assertEquals(400, call(s -> ResourceWebEditDashboard.image(store, 10, "a.txt",
                in("text".getBytes(StandardCharsets.UTF_8)), 1000, NOW)).status);
        int objects = store.objects.size();
        store.failFileWrites = true;
        try {
            ResourceWebEditDashboard.image(store, 10, "b.png", in(PNG), 1000, NOW);
            fail("no exception");
        } catch (java.io.IOException expected) {
            // ok
        }
        assertEquals(objects, store.objects.size());
    }

    private static InputStream in(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static WebEdit.Reply call(WebEdit.Action action) throws Exception {
        return WebEdit.call(null, action);
    }

    // ---------------------------------------------------------------- input widgets

    private static WebEdit.Reply value(FakeEditStore store, FakeEngine engine, int uuid, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditDashboard.value(s, engine, 10, uuid, body, "de", NOW));
    }

    @Test
    public void toggleSwitchWritesOneOrZero() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        WebEdit.Reply reply = value(store, engine, 4, "{\"value\":true}");
        assertEquals(200, reply.status);
        JsonSample written = store.latest(24, "Value");
        assertEquals("1", written.getValue());
        assertNull(written.getNote());
        assertEquals(NOW.getMillis(), (long) ResourceWebNotes.millis(written.getTs()));
        assertTrue(store.logs.contains("CREATE_SAMPLE 24:Value|1|web:dashboard-value"));
        JsonNode json = json(reply);
        assertEquals("OK", json.get("rows").get(0).get("status").asText());
        assertEquals(1d, json.get("rows").get(0).get("value").asDouble(), 0d);
        assertEquals(1, engine.evaluated.getWidgets().size());
        assertEquals(Long.valueOf(10), engine.context);

        assertEquals(200, value(store, engine, 4, "{\"value\":false,\"rowKey\":\"0-24-Value\"}").status);
        assertEquals("0", store.latest(24, "Value").getValue());
        assertEquals(400, value(store, engine, 4, "{\"value\":1}").status);
        assertEquals(400, value(store, engine, 4, "{\"value\":true,\"rowKey\":\"0-21-Value\"}").status);
        assertEquals(400, value(store, engine, 4, "{}").status);
    }

    @Test
    public void sliderRoundsAndStaysWithinMinMax() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        assertEquals(200, value(store, engine, 5, "{\"value\":12.35}").status);
        assertEquals("12.4", store.latest(24, "Value").getValue());
        assertEquals(400, value(store, engine, 5, "{\"value\":50.1}").status);
        assertEquals(400, value(store, engine, 5, "{\"value\":-1}").status);
        assertEquals(400, value(store, engine, 5, "{\"value\":\"10\"}").status);
        assertEquals(1, store.count(24, "Value"));
    }

    @Test
    public void plusMinusAddsTheIncrementToTheLatestValue() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        /* no value yet: 0 - 2.5, kept at the tile's minimum 0 */
        assertEquals(200, value(store, engine, 6, "{\"value\":\"minus\"}").status);
        assertEquals("0", store.latest(24, "Value").getValue());
        store.sample(24, "Value", EARLIER, "10", null);
        store.samples.get("24/Value").remove(NOW.getMillis());
        assertEquals(200, value(store, engine, 6, "{\"value\":\"plus\"}").status);
        assertEquals("12.5", store.latest(24, "Value").getValue());
        store.sample(24, "Value", NOW, "99.5", null);
        assertEquals(200, value(store, engine, 6, "{\"value\":\"plus\"}").status);
        assertEquals("100", store.latest(24, "Value").getValue());
        assertEquals(400, value(store, engine, 6, "{\"value\":2.5}").status);
    }

    @Test
    public void valueEditorEntersAManualValue() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        WebEdit.Reply reply = value(store, engine, 7, "{\"value\":42.5,\"timestamp\":\"2026-11-12T10:00:00+01:00\"}");
        assertEquals(200, reply.status);
        JsonSample written = store.latest(21, "Value");
        assertEquals("42.5", written.getValue());
        assertTrue(written.getNote().startsWith("Manuelle Dateneingabe"));
        assertEquals(new DateTime(2026, 11, 12, 10, 0, ZONE).getMillis(), (long) ResourceWebNotes.millis(written.getTs()));
        /* overwriting asks first (409 with warnings), as the manual values */
        WebEdit.Reply again = value(store, engine, 7, "{\"value\":43,\"timestamp\":\"2026-11-12T10:00:00+01:00\"}");
        assertEquals(409, again.status);
        assertEquals(200, value(store, engine, 7, "{\"value\":43,\"timestamp\":\"2026-11-12T10:00:00+01:00\",\"confirm\":true}").status);
        assertEquals("43", store.latest(21, "Value").getValue());
        /* without time stamp: now */
        assertEquals(200, value(store, engine, 7, "{\"value\":44}").status);
        assertEquals("44", store.latest(21, "Value").getValue());
    }

    @Test
    public void valueRightsAndWidgets() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        assertEquals(403, value(store, engine, 9, "{\"value\":true}").status);
        assertEquals(400, value(store, engine, 1, "{\"value\":1}").status);
        assertEquals(404, value(store, engine, 99, "{\"value\":1}").status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditDashboard.value(s, engine, 13, 4, "{\"value\":true}", "de", NOW)).status);
        assertTrue(store.writes.isEmpty());
    }

    // ---------------------------------------------------------------- preview

    @Test
    public void previewOfAnUnsavedSource() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        ObjectNode source = (ObjectNode) source(store, 10);
        widget(source, 2).put("title", "Draft");
        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        body.put("id", 10).put("timeFrame", "P1M").put("timeZone", "Europe/Berlin").set("source", source);
        WebEdit.Reply reply = preview(store, engine, body.toString());
        assertEquals(200, reply.status);
        JsonNode json = json(reply);
        assertEquals(10, json.get("dashboard").get("id").asLong());
        JsonNode draft = null;
        for (JsonNode w : json.get("dashboard").get("widgets")) if ("2".equals(w.get("uuid").asText())) draft = w;
        assertEquals("Draft", draft.get("title").asText());
        assertTrue(json.get("dashboard").get("background").get("image").asBoolean());
        assertEquals("P1M", json.get("data").get("timeFrame").asText());
        assertEquals("NO_PERMISSION", json.get("data").get("widgets").get("1").get("rows").get(1).get("status").asText());
        assertEquals(Long.valueOf(10), engine.context);
        assertEquals(1, store.count(10, FILE));
        assertTrue(store.writes.isEmpty());

        /* new dashboard: no id, the directory's working day, unreadable rows refused */
        ObjectNode fresh = FakeEditStore.MAPPER.createObjectNode();
        fresh.put("directoryId", 2).set("source", DashboardSource.empty());
        JsonNode freshJson = json(preview(store, engine, fresh.toString()));
        assertTrue(freshJson.get("dashboard").get("id").isNull());
        assertFalse(freshJson.get("dashboard").get("background").get("image").asBoolean());
        assertEquals(Long.valueOf(2), engine.context);
        body.remove("id");
        assertTrue(contains(messages(WebEdit.call(store, s -> ResourceWebEditDashboard.preview(s, engine, body.toString()))),
                "Row 23 not found or not readable"));
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditDashboard.preview(s, engine, "{\"directoryId\":2}")).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditDashboard.preview(s, engine,
                "{\"timeFrame\":\"CUSTOM\",\"source\":{}}")).status);
    }

    @Test
    public void javaFxColours() {
        assertEquals("0x1a2a45ff", DashboardSource.javaFxColor("#1A2A45"));
        assertEquals("0x1a2a4580", DashboardSource.javaFxColor("#1a2a4580"));
        assertEquals("0x1a2a45ff", DashboardSource.javaFxColor("0x1a2a45ff"));
        assertNull(DashboardSource.javaFxColor("red"));
        assertNull(DashboardSource.javaFxColor("#12345"));
    }
}
