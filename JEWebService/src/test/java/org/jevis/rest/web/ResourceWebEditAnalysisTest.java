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
import org.jevis.commons.chart.AnalysisDefinition;
import org.jevis.commons.chart.AnalysisReader;
import org.jevis.commons.chart.AnalysisWriter;
import org.jevis.commons.chart.EvaluationSettings;
import org.jevis.commons.chart.RowDefinition;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonUnit;
import org.joda.time.DateTime;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

/**
 * Editing of analyses (stage E3).
 */
public class ResourceWebEditAnalysisTest {

    private static final DateTime NOW = new DateTime(2026, 11, 12, 10, 30, 15, 0, ZONE);
    private static final DateTime EARLIER = new DateTime(2026, 11, 1, 8, 0, ZONE);
    private static final String FILE = ResourceWebEditAnalysis.ATTRIBUTE_FILE;
    private static final String DIRECTORY = ResourceWebEditAnalysis.CLASS_DIRECTORY;
    private static final String ANALYSIS = ResourceWebEditAnalysis.CLASS_ANALYSIS;

    /**
     * Analysis 10 as the Control Center writes it: chart 0 with Meter A (21) and the unreadable Meter B (23), chart 1
     * with the clean data of Meter A (22); unknown keys on all levels
     */
    private static final String OVERVIEW = "{\n" +
            "  \"type\" : \"AnalysisHandler\",\n" +
            "  \"autoSize\" : true,\n" +
            "  \"chartsPerScreen\" : 2,\n" +
            "  \"horizontalPies\" : 3,\n" +
            "  \"horizontalTables\" : 3,\n" +
            "  \"forcedInterval\" : \"\",\n" +
            "  \"dayFilterWeekdays\" : \"6,7\",\n" +
            "  \"dayFilterHolidays\" : false,\n" +
            "  \"dayFilterDates\" : \"\",\n" +
            "  \"limitLines\" : [ { \"value\" : 100.0, \"label\" : \"Limit\", \"color\" : \"#FF0000\", \"axis\" : 0, \"fill\" : \"NONE\" } ],\n" +
            "  \"chartModels\" : [ {\n" +
            "    \"chartId\" : 0, \"chartName\" : \"Chart A\", \"chartType\" : \"LINE\", \"minFractionDigits\" : 2,\n" +
            "    \"maxFractionDigits\" : 2, \"groupingInterval\" : 30.0, \"height\" : -1.0, \"colorMapping\" : \"GREEN_YELLOW_RED\",\n" +
            "    \"orientation\" : \"HORIZONTAL\", \"dayStart\" : \"06:00:00.000\", \"filterEnabled\" : false, \"fixYAxisToZero\" : false,\n" +
            "    \"coloringEnabled\" : false, \"showColumnSums\" : false, \"showRowSums\" : false, \"customChart\" : 7,\n" +
            "    \"chartData\" : [ {\n" +
            "      \"id\" : 21, \"attributeString\" : \"Value\", \"unitPrefix\" : \"KILO\", \"unitFormula\" : \"Wh\", \"unitLabel\" : \"kWh\",\n" +
            "      \"name\" : \"Meter A\", \"color\" : \"0x00538a80\", \"axis\" : 0, \"calculation\" : false, \"calculationId\" : -1,\n" +
            "      \"bubbleType\" : \"NONE\", \"chartType\" : \"DEFAULT\", \"aggregationPeriod\" : \"DAILY\", \"decimalDigits\" : -1,\n" +
            "      \"css\" : \"\", \"intervalStart\" : \"2026-01-01T00:00:00.000+01:00\", \"intervalEnd\" : \"2026-01-02T00:00:00.000+01:00\",\n" +
            "      \"intervalEnabled\" : false, \"customRow\" : \"keep\"\n" +
            "    }, {\n" +
            "      \"id\" : 23, \"attributeString\" : \"Value\", \"unitPrefix\" : \"NONE\", \"unitFormula\" : \"m³\", \"unitLabel\" : \"m³\",\n" +
            "      \"name\" : \"Secret meter\", \"color\" : \"0xff0000ff\", \"axis\" : 1, \"calculation\" : false, \"calculationId\" : -1,\n" +
            "      \"bubbleType\" : \"NONE\", \"chartType\" : \"COLUMN\", \"decimalDigits\" : 3, \"css\" : \"\",\n" +
            "      \"intervalStart\" : null, \"intervalEnd\" : null, \"intervalEnabled\" : false\n" +
            "    } ]\n" +
            "  }, {\n" +
            "    \"chartId\" : 1, \"chartName\" : \"Chart B\", \"chartType\" : \"TABLE\", \"minFractionDigits\" : 2,\n" +
            "    \"maxFractionDigits\" : 2, \"groupingInterval\" : 30.0, \"height\" : -1.0, \"colorMapping\" : \"GREEN_YELLOW_RED\",\n" +
            "    \"orientation\" : \"VERTICAL\", \"filterEnabled\" : true, \"fixYAxisToZero\" : false, \"coloringEnabled\" : true,\n" +
            "    \"showColumnSums\" : true, \"showRowSums\" : false, \"filterTexts\" : { \"Meter A\" : \">5\" },\n" +
            "    \"chartData\" : [ {\n" +
            "      \"id\" : 22, \"attributeString\" : \"Value\", \"unitPrefix\" : \"KILO\", \"unitFormula\" : \"Wh\", \"unitLabel\" : \"kWh\",\n" +
            "      \"name\" : \"\", \"color\" : \"0x00ff00ff\", \"axis\" : 0, \"calculation\" : false, \"calculationId\" : -1,\n" +
            "      \"bubbleType\" : \"NONE\", \"chartType\" : \"DEFAULT\", \"decimalDigits\" : -1, \"css\" : \"\",\n" +
            "      \"intervalStart\" : null, \"intervalEnd\" : null, \"intervalEnabled\" : false\n" +
            "    } ]\n" +
            "  } ],\n" +
            "  \"customTop\" : true\n" +
            "}";

    private static final String LEGACY_CHARTS = "{\"listSettings\":[{\"id\":\"0\",\"name\":\"Legacy chart\",\"chartType\":\"AREA\"}],\"autoSize\":\"true\"}";
    private static final String LEGACY_DATA_MODEL = "{\"listDataRows\":[{\"name\":\"Meter A\",\"object\":\"21\",\"color\":\"0xff0000ff\","
            + "\"selectedCharts\":\"0\",\"axis\":\"0\",\"chartType\":\"LINE\"}]}";

    /* organisation Musterwerk 1
         analyses directory 2 > analyses 10 (Overview, file), 11 (Old, legacy), 12 (Locked), 13 (Hidden, not readable)
         analyses directory 3 (Archiv, no create right)
         data directory 20 > Meter A 21 (Data) > Meter A clean 22 (Clean Data); Meter B 23 (not readable);
                             Alarm 24 (Alarm Configuration); Meter C 25 (Data)
         calculation output: Meter C is calculated by 30 */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        store.add(obj(1, "Musterwerk", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(2, "Analysen", DIRECTORY, 1),
                obj(3, "Archiv", DIRECTORY, 1),
                obj(10, "Overview", ANALYSIS, 2),
                obj(11, "Old", ANALYSIS, 2),
                obj(12, "Locked", ANALYSIS, 2),
                obj(13, "Hidden", ANALYSIS, 2),
                obj(20, "Meters", "Data Directory", 1),
                obj(21, "Meter A", "Data", 20),
                obj(22, "Meter A clean", "Clean Data", 21),
                obj(23, "Meter B", "Data", 20),
                obj(24, "Alarm", "Alarm Configuration", 20),
                obj(25, "Meter C", "Data", 20));
        store.hidden.add(13L);
        store.hidden.add(23L);
        store.type("Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE)
                .type("Clean Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE)
                .type("Alarm Configuration", null, "Alarm Checked", JEVisConstants.PrimitiveType.BOOLEAN);
        store.write.addAll(Arrays.asList(2L, 10L, 11L));
        store.delete.add(10L);
        store.create.add(2L);
        store.file(10, FILE, EARLIER, "Overview_20261101080000.json", OVERVIEW)
                .file(12, FILE, EARLIER, "Locked_20261101080000.json", "{\"chartModels\":[]}")
                .sample(11, AnalysisReader.DATA_MODEL_ATTRIBUTE_NAME, EARLIER, LEGACY_DATA_MODEL, null)
                .sample(11, AnalysisReader.CHARTS_ATTRIBUTE_NAME, EARLIER, LEGACY_CHARTS, null);
        return store;
    }

    /**
     * Engine on the fake store: reads like W2, calculations and units from maps, records the preview
     */
    static final class FakeEngine implements ResourceWebEditAnalysis.Engine {
        final FakeEditStore store;
        final Map<Long, Long> calculations = new HashMap<>();
        final Map<Long, JsonUnit> units = new HashMap<>();
        AnalysisDefinition previewed;
        Long previewContext;

        FakeEngine(FakeEditStore store) {
            this.store = store;
            calculations.put(25L, 30L);
            units.put(21L, unit("KILO", "Wh", "kWh"));
            units.put(25L, unit("KILO", "Wh", "kWh"));
        }

        @Override
        public AnalysisDefinition read(long id) throws Exception {
            JsonSample file = store.latest(id, FILE);
            AnalysisDefinition definition = file != null
                    ? new AnalysisReader().readAnalysisFile(store.file(id, FILE, file)) : readLegacy(id);
            definition.setId(id);
            ResourceWebEditAnalysis.resolveRows(store, definition);
            return definition;
        }

        @Override
        public AnalysisDefinition readLegacy(long id) {
            JsonSample dataModel = store.latest(id, AnalysisReader.DATA_MODEL_ATTRIBUTE_NAME);
            JsonSample charts = store.latest(id, AnalysisReader.CHARTS_ATTRIBUTE_NAME);
            return new AnalysisReader().readLegacy(dataModel != null ? dataModel.getValue() : null,
                    charts != null ? charts.getValue() : null);
        }

        @Override
        public long calculationId(long rowId) {
            return calculations.getOrDefault(rowId, -1L);
        }

        @Override
        public JsonUnit displayUnit(long id, String attribute) {
            return units.get(id);
        }

        @Override
        public Map<String, Object> data(AnalysisDefinition definition, Long contextId, EvaluationSettings settings) throws Exception {
            previewed = definition;
            previewContext = contextId;
            ResourceWebEditAnalysis.resolveRows(store, definition);
            List<String> keys = new ArrayList<>();
            for (org.jevis.commons.chart.ChartDefinition chart : definition.getCharts()) {
                for (RowDefinition row : chart.getRows()) keys.add(row.getKey() + (Boolean.TRUE.equals(row.getReadable()) ? "" : ":NO_PERMISSION"));
            }
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("rows", keys);
            json.put("timeFrame", settings.getTimeFrame().name());
            return json;
        }
    }

    static JsonUnit unit(String prefix, String formula, String label) {
        JsonUnit unit = new JsonUnit();
        unit.setPrefix(prefix);
        unit.setFormula(formula);
        unit.setLabel(label);
        return unit;
    }

    private static WebEdit.Reply get(FakeEditStore store, long id) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditAnalysis.config(s, new FakeEngine(store), id));
    }

    private static ObjectNode getJson(FakeEditStore store, long id) throws Exception {
        WebEdit.Reply reply = get(store, id);
        assertEquals(200, reply.status);
        return (ObjectNode) FakeEditStore.json(reply);
    }

    private static WebEdit.Reply put(FakeEditStore store, long id, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditAnalysis.update(s, new FakeEngine(store), id, body, "de", NOW));
    }

    /**
     * @return {@code {version, definition}} with the GET answer as definition (read-only keys included, they are
     * ignored)
     */
    private static ObjectNode putBody(FakeEditStore store, long id) throws Exception {
        ObjectNode current = getJson(store, id);
        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        body.put("version", current.get("version").asText());
        body.set("definition", current);
        return body;
    }

    private static ObjectNode storedTree(FakeEditStore store, long id) throws Exception {
        return (ObjectNode) FakeEditStore.MAPPER.readTree(store.latestFile(id, FILE));
    }

    private static List<String> messages(WebEdit.Reply reply) throws Exception {
        List<String> messages = new ArrayList<>();
        for (JsonNode message : FakeEditStore.json(reply).get("messages")) messages.add(message.asText());
        return messages;
    }

    // ---------------------------------------------------------------- reading

    @Test
    public void getCarriesTheEditableSettingsAndRights() throws Exception {
        FakeEditStore store = store();
        JsonNode json = getJson(store, 10);
        assertEquals(WebEdit.version(store.latest(10, FILE)), json.get("version").asText());
        assertTrue(json.get("canWrite").asBoolean());
        assertTrue(json.get("canDelete").asBoolean());
        assertEquals(2, json.get("directoryId").asLong());
        assertEquals("", json.get("forcedInterval").asText());
        assertEquals("6,7", json.get("dayFilter").get("weekdays").asText());
        assertFalse(json.get("dayFilter").get("holidays").asBoolean());
        assertEquals("#ff0000", json.get("limitLines").get(0).get("color").asText());
        assertEquals(100.0, json.get("limitLines").get(0).get("value").asDouble(), 0);

        JsonNode chartA = json.get("charts").get(0);
        assertEquals("06:00:00", chartA.get("dayStart").asText());
        assertTrue(chartA.get("dayEnd").isNull());
        assertTrue(chartA.get("fractionDigitsAuto").isNull());
        JsonNode chartB = json.get("charts").get(1);
        assertTrue(chartB.get("filterEnabled").asBoolean());
        assertTrue(chartB.get("coloringEnabled").asBoolean());
        assertTrue(chartB.get("showColumnSums").asBoolean());
        assertEquals(">5", chartB.get("filterTexts").get("Meter A").asText());

        JsonNode meterA = chartA.get("rows").get(0);
        assertEquals("Data", meterA.get("rowClass").asText());
        assertEquals("KILO", meterA.get("unitPrefix").asText());
        assertEquals("Wh", meterA.get("unitFormula").asText());
        assertEquals("kWh", meterA.get("unitLabel").asText());
        assertEquals(-1, meterA.get("calculationId").asLong());
        assertFalse(meterA.get("interval").get("enabled").asBoolean());
        assertEquals("2026-01-01T00:00:00.000+01:00", meterA.get("interval").get("start").asText());
        assertEquals("", meterA.get("css").asText());
        /* the unreadable row shows nothing of its object */
        JsonNode meterB = chartA.get("rows").get(1);
        assertFalse(meterB.get("readable").asBoolean());
        assertTrue(meterB.get("name").isNull());
        assertTrue(meterB.get("unitLabel").isNull());
        assertTrue(meterB.get("rowClass").isNull());
        assertFalse(json.toString().contains("Secret"));
        /* the clean data row is named after its data object if it has no name */
        assertEquals("Clean Data", chartB.get("rows").get(0).get("rowClass").asText());
        assertEquals("Meter A", chartB.get("rows").get(0).get("name").asText());
    }

    @Test
    public void getVersionOfLegacyAndNeverSavedAnalyses() throws Exception {
        FakeEditStore store = store();
        JsonNode legacy = getJson(store, 11);
        assertEquals(WebEdit.version(store.latest(11, AnalysisReader.DATA_MODEL_ATTRIBUTE_NAME)), legacy.get("version").asText());
        assertFalse(legacy.get("canDelete").asBoolean());
        JsonNode locked = getJson(store, 12);
        assertFalse(locked.get("canWrite").asBoolean());
        store.add(obj(14, "Empty", ANALYSIS, 1));
        JsonNode empty = getJson(store, 14);
        assertEquals("none", empty.get("version").asText());
        assertTrue(empty.get("directoryId").isNull());
        assertEquals(404, get(store, 13).status);
        assertEquals(404, get(store, 21).status);
        assertEquals(404, get(store, 999).status);
    }

    // ---------------------------------------------------------------- change

    @Test
    public void saveWithoutChangesKeepsTheStoredJson() throws Exception {
        FakeEditStore store = store();
        ObjectNode before = storedTree(store, 10);
        WebEdit.Reply reply = put(store, 10, putBody(store, 10).toString());
        assertEquals(200, reply.status);
        assertEquals(2, store.count(10, FILE));
        assertEquals(before, storedTree(store, 10));
        assertTrue(store.writes.get(0).startsWith("10/Analysis File 3 Overview_20261112103015.json"));
        assertTrue(store.logs.contains("CREATE_SAMPLE 10:Analysis File|1|web:analysis-save"));
        JsonNode json = FakeEditStore.json(reply);
        assertEquals(WebEdit.version(store.latest(10, FILE)), json.get("version").asText());
        assertFalse(json.toString().contains("Secret"));
    }

    @Test
    public void saveChangesOnlyWhatWasSent() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = putBody(store, 10);
        ObjectNode definition = (ObjectNode) body.get("definition");
        definition.put("chartsPerScreen", 1);
        ObjectNode chartA = (ObjectNode) definition.get("charts").get(0);
        chartA.put("name", "Chart A2");
        chartA.put("dayEnd", "18:00:00");
        ArrayNode rows = (ArrayNode) chartA.get("rows");
        ((ObjectNode) rows.get(0)).put("color", "#112233");
        /* a new row (Meter C, calculated) with only object and attribute: Control Center defaults */
        ObjectNode added = rows.addObject();
        added.put("objectId", 25);
        added.put("attribute", "Value");
        added.put("calculation", true);
        /* chart B removed */
        ((ArrayNode) definition.get("charts")).remove(1);

        WebEdit.Reply reply = put(store, 10, body.toString());
        assertEquals(FakeEditStore.json(reply).toString(), 200, reply.status);
        ObjectNode tree = storedTree(store, 10);
        assertEquals(1, tree.get("chartsPerScreen").asInt());
        assertTrue(tree.get("customTop").asBoolean());
        assertEquals(1, tree.get("chartModels").size());
        JsonNode chart = tree.get("chartModels").get(0);
        assertEquals("Chart A2", chart.get("chartName").asText());
        assertEquals("18:00:00.000", chart.get("dayEnd").asText());
        assertEquals("06:00:00.000", chart.get("dayStart").asText());
        assertEquals(7, chart.get("customChart").asInt());
        JsonNode meterA = chart.get("chartData").get(0);
        assertEquals("0x112233ff", meterA.get("color").asText());
        assertEquals("DAILY", meterA.get("aggregationPeriod").asText());
        assertEquals("keep", meterA.get("customRow").asText());
        /* the unreadable row keeps its stored settings */
        JsonNode meterB = chart.get("chartData").get(1);
        assertEquals(23, meterB.get("id").asLong());
        assertEquals("Secret meter", meterB.get("name").asText());
        assertEquals("0xff0000ff", meterB.get("color").asText());
        assertEquals(3, meterB.get("decimalDigits").asInt());
        JsonNode meterC = chart.get("chartData").get(2);
        assertEquals(25, meterC.get("id").asLong());
        assertTrue(meterC.get("calculation").asBoolean());
        assertEquals(30, meterC.get("calculationId").asLong());
        assertEquals("0xadd8e6ff", meterC.get("color").asText());
        assertEquals("DEFAULT", meterC.get("chartType").asText());
        assertEquals("kWh", meterC.get("unitLabel").asText());
        assertFalse(meterC.has("aggregationPeriod"));
    }

    @Test
    public void validationReportsAllProblems() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = putBody(store, 10);
        ObjectNode definition = (ObjectNode) body.get("definition");
        definition.put("chartsPerScreen", 0);
        definition.put("unknownKey", 1);
        ObjectNode chartA = (ObjectNode) definition.get("charts").get(0);
        chartA.put("type", "PIZZA");
        chartA.put("dayStart", "25:00:00");
        chartA.put("maxFractionDigits", 21);
        ((ObjectNode) definition.get("charts").get(1)).put("id", 0);
        ArrayNode rows = (ArrayNode) chartA.get("rows");
        ObjectNode meterA = (ObjectNode) rows.get(0);
        meterA.put("color", "red");
        meterA.put("axis", 2);
        meterA.put("bubbleType", "Z");
        meterA.put("unitLabel", "furlong");
        rows.addObject().put("objectId", 24);
        rows.addObject().put("objectId", 999);
        ObjectNode calculated = rows.addObject();
        calculated.put("objectId", 21);
        calculated.put("calculation", true);
        String version = body.get("version").asText();

        WebEdit.Reply reply = put(store, 10, body.toString());
        assertEquals(400, reply.status);
        List<String> messages = messages(reply);
        assertEquals(messages.toString(), 13, messages.size());
        assertTrue(messages.contains("Invalid chartsPerScreen (1–20)"));
        assertTrue(messages.contains("Unknown key: unknownKey"));
        assertTrue(messages.contains("Duplicate chart id: 0"));
        assertTrue(messages.contains("Row 999 not found or not readable"));
        assertTrue(messages.contains("Row 24 is no data row (Alarm Configuration)"));
        assertTrue(messages.contains("Row 21 is not calculated (no calculation)"));
        assertTrue(messages.contains("Invalid unit of row 21: furlong"));
        assertEquals(1, store.count(10, FILE));
        assertTrue(store.logs.isEmpty());

        /* version of the read answer still valid: nothing was written */
        assertEquals(version, WebEdit.version(store.latest(10, FILE)));
    }

    @Test
    public void unitsOfTheUnitBoxAreAccepted() throws Exception {
        FakeEditStore store = store();
        List<Map<String, String>> units = ResourceWebEditAnalysis.unitChoices(unit("KILO", "Wh", "kWh"));
        JEVisUnitImp own = new JEVisUnitImp(unit("KILO", "Wh", "kWh"));
        assertEquals(String.valueOf(own.getPrefix()), units.get(0).get("prefix"));
        assertEquals(own.getFormula(), units.get(0).get("formula"));
        assertEquals("kWh", units.get(0).get("label"));
        assertTrue(units.size() > 1);
        Map<String, String> other = units.get(units.size() - 1);

        ObjectNode body = putBody(store, 10);
        ObjectNode meterA = (ObjectNode) body.get("definition").get("charts").get(0).get("rows").get(0);
        meterA.put("unitPrefix", other.get("prefix"));
        meterA.put("unitFormula", other.get("formula"));
        meterA.put("unitLabel", other.get("label"));
        WebEdit.Reply reply = put(store, 10, body.toString());
        assertEquals(FakeEditStore.json(reply).toString(), 200, reply.status);
        JsonNode row = storedTree(store, 10).get("chartModels").get(0).get("chartData").get(0);
        assertEquals(other.get("label"), row.get("unitLabel").asText());

        /* the units endpoint */
        WebEdit.Reply list = WebEdit.call(store, s -> ResourceWebEditAnalysis.units(s, new FakeEngine(store), 21, null));
        assertEquals(200, list.status);
        assertEquals("kWh", FakeEditStore.json(list).get(0).get("label").asText());
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditAnalysis.units(s, new FakeEngine(store), 23, "Value")).status);
        /* without display unit: the dimensionless unit of a new row */
        WebEdit.Reply none = WebEdit.call(store, s -> ResourceWebEditAnalysis.units(s, new FakeEngine(store), 22, "Value"));
        assertEquals(1, FakeEditStore.json(none).size());
    }

    @Test
    public void rightsVersionAndOrderOfChecks() throws Exception {
        FakeEditStore store = store();
        String body = putBody(store, 10).toString();
        assertEquals(404, put(store, 13, body).status);
        assertEquals(404, put(store, 21, body).status);
        WebEdit.Reply forbidden = put(store, 12, "{\"definition\":{\"unknown\":1}}");
        assertEquals(403, forbidden.status);
        assertTrue(forbidden.body instanceof String);
        WebEdit.Reply missing = put(store, 10, "{\"definition\":{}}");
        assertEquals(400, missing.status);
        assertEquals("version missing", missing.body);
        assertEquals(400, put(store, 10, "{\"version\":\"yesterday\",\"definition\":{}}").status);
        /* validation before the version check */
        WebEdit.Reply invalid = put(store, 10, "{\"version\":\"2020-01-01T00:00:00.000+01:00\",\"definition\":{\"autoSize\":3}}");
        assertEquals(400, invalid.status);
        WebEdit.Reply conflict = put(store, 10, "{\"version\":\"2020-01-01T00:00:00.000+01:00\",\"definition\":{}}");
        assertEquals(409, conflict.status);
        assertEquals(10, FakeEditStore.json(conflict).get("id").asLong());
        assertEquals("Chart A", FakeEditStore.json(conflict).get("charts").get(0).get("name").asText());
        assertEquals(1, store.count(10, FILE));
        assertTrue(store.logs.isEmpty());
    }

    @Test
    public void legacyAnalysisIsSavedInTheCurrentFormat() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = putBody(store, 11);
        ((ObjectNode) body.get("definition").get("charts").get(0)).put("name", "Renamed chart");
        WebEdit.Reply reply = put(store, 11, body.toString());
        assertEquals(FakeEditStore.json(reply).toString(), 200, reply.status);
        ObjectNode tree = storedTree(store, 11);
        assertEquals("AnalysisHandler", tree.get("type").asText());
        assertEquals("Renamed chart", tree.get("chartModels").get(0).get("chartName").asText());
        assertEquals(21, tree.get("chartModels").get(0).get("chartData").get(0).get("id").asLong());
        assertEquals("0xff0000ff", tree.get("chartModels").get(0).get("chartData").get(0).get("color").asText());
        /* the legacy attributes stay */
        assertEquals(1, store.count(11, AnalysisReader.DATA_MODEL_ATTRIBUTE_NAME));
        assertEquals(LEGACY_DATA_MODEL, store.latest(11, AnalysisReader.DATA_MODEL_ATTRIBUTE_NAME).getValue());
        /* the version is now the one of the file */
        assertEquals(WebEdit.version(store.latest(11, FILE)), FakeEditStore.json(reply).get("version").asText());
    }

    @Test
    public void secondSaveInTheSameSecondWaits() throws Exception {
        FakeEditStore store = store();
        store.file(10, FILE, NOW, "Overview_20261112103015.json", OVERVIEW);
        WebEdit.Reply reply = put(store, 10, putBody(store, 10).toString());
        assertEquals(200, reply.status);
        assertEquals(Collections.singletonList(NOW.plusSeconds(1)), store.waits);
        assertTrue(store.writes.get(0).startsWith("10/Analysis File 3 Overview_20261112103016.json"));
    }

    @Test
    public void renameChecksTheNamesOfTheDirectory() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = putBody(store, 10);
        body.put("name", "Old");
        WebEdit.Reply taken = put(store, 10, body.toString());
        assertEquals(409, taken.status);
        assertTrue(FakeEditStore.json(taken).get("nameTaken").asBoolean());
        assertEquals(11, FakeEditStore.json(taken).get("id").asLong());
        assertTrue(store.renames.isEmpty());

        body.put("name", "  Strom / Gas  ");
        WebEdit.Reply renamed = put(store, 10, body.toString());
        assertEquals(200, renamed.status);
        assertEquals(Collections.singletonList("10 Strom / Gas"), store.renames);
        assertTrue(store.logs.contains("UPDATE_OBJECT 10:Strom / Gas|web:analysis-save"));
        assertEquals("Strom / Gas", FakeEditStore.json(renamed).get("name").asText());
        /* the file name has no path characters */
        assertTrue(store.writes.get(0), store.writes.get(0).startsWith("10/Analysis File 3 Strom _ Gas_20261112103015.json"));

        /* rename only, without definition: no file */
        WebEdit.Reply only = put(store, 10, "{\"version\":\"" + WebEdit.version(store.latest(10, FILE)) + "\",\"name\":\"Overview\"}");
        assertEquals(200, only.status);
        assertEquals(1, store.writes.size());
        WebEdit.Reply empty = put(store, 10, "{\"version\":\"x\",\"name\":\" \"}");
        assertEquals(400, empty.status);
    }

    // ---------------------------------------------------------------- create, copy, delete

    private static WebEdit.Reply create(FakeEditStore store, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditAnalysis.create(s, new FakeEngine(store), body, "de", NOW));
    }

    @Test
    public void createWritesTheControlCenterFormat() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = create(store, "{\"directoryId\":2,\"name\":\" Neue Analyse \",\"definition\":{"
                + "\"charts\":[{\"name\":\"Diagramm\",\"type\":\"COLUMN\",\"rows\":[{\"objectId\":21,\"name\":\"Meter A\"}]}]}}");
        assertEquals(FakeEditStore.json(reply).toString(), 201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        long id = json.get("id").asLong();
        JsonObject created = store.object(id);
        assertEquals("Neue Analyse", created.getName());
        assertEquals(2L, created.getParent());
        assertEquals(ANALYSIS, created.getJevisClass());
        assertEquals("Neue Analyse", json.get("name").asText());
        assertEquals(2, json.get("directoryId").asLong());
        assertTrue(json.get("canWrite").asBoolean());
        assertNotEquals("none", json.get("version").asText());
        assertEquals("0-21-Value", json.get("charts").get(0).get("rows").get(0).get("key").asText());

        ObjectNode tree = storedTree(store, id);
        assertEquals("AnalysisHandler", tree.get("type").asText());
        JsonNode chart = tree.get("chartModels").get(0);
        assertEquals(0, chart.get("chartId").asInt());
        assertEquals("COLUMN", chart.get("chartType").asText());
        JsonNode row = chart.get("chartData").get(0);
        assertEquals("kWh", row.get("unitLabel").asText());
        assertEquals("0xadd8e6ff", row.get("color").asText());
        assertTrue(store.writes.get(0).startsWith(id + "/Analysis File 3 Neue Analyse_20261112103015.json"));
        assertTrue(store.logs.contains("CREATE_OBJECT " + id + ":Neue Analyse|web:analysis-create"));
        assertTrue(store.logs.contains("CREATE_SAMPLE " + id + ":Analysis File|1|web:analysis-create"));
    }

    @Test
    public void createChecksRightsNamesAndRollsBack() throws Exception {
        FakeEditStore store = store();
        int objects = store.objects.size();
        assertEquals(403, create(store, "{\"directoryId\":3,\"name\":\"A\"}").status);
        assertEquals(404, create(store, "{\"directoryId\":1,\"name\":\"A\"}").status);
        assertEquals(400, create(store, "{\"name\":\"A\"}").status);
        assertEquals(400, create(store, "{\"directoryId\":2,\"name\":\"\"}").status);
        assertEquals(400, create(store, "{\"directoryId\":2,\"name\":\"A\\u0007B\"}").status);
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 101; i++) longName.append('x');
        assertEquals(400, create(store, "{\"directoryId\":2,\"name\":\"" + longName + "\"}").status);
        WebEdit.Reply taken = create(store, "{\"directoryId\":2,\"name\":\"Overview\"}");
        assertEquals(409, taken.status);
        assertEquals(10, FakeEditStore.json(taken).get("id").asLong());
        assertEquals(objects, store.objects.size());

        store.failFileWrites = true;
        try {
            create(store, "{\"directoryId\":2,\"name\":\"Broken\"}");
            fail("the write error is passed on");
        } catch (java.io.IOException expected) {
            /* rolled back */
        }
        assertEquals(objects, store.objects.size());
        assertEquals(1, store.deletedObjects.size());
        assertTrue(store.logs.contains("DELETE_OBJECT " + store.deletedObjects.get(0) + ":Broken|web:analysis-create-rollback"));
    }

    @Test
    public void copyStoresTheSourceOrTheDefinitionSent() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply copy = WebEdit.call(store, s -> ResourceWebEditAnalysis.copy(s, new FakeEngine(store), 10,
                "{\"directoryId\":2,\"name\":\"Overview 2\"}", "de", NOW));
        assertEquals(201, copy.status);
        long id = FakeEditStore.json(copy).get("id").asLong();
        assertEquals(storedTree(store, 10), storedTree(store, id));
        assertTrue(store.logs.contains("CREATE_OBJECT " + id + ":Overview 2|web:analysis-copy"));
        assertTrue(store.logs.contains("CREATE_SAMPLE " + id + ":Analysis File|1|web:analysis-copy"));

        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        body.put("directoryId", 2);
        body.put("name", "Overview 3");
        ObjectNode definition = getJson(store, 10);
        definition.put("chartsPerScreen", 4);
        body.set("definition", definition);
        WebEdit.Reply changed = WebEdit.call(store, s -> ResourceWebEditAnalysis.copy(s, new FakeEngine(store), 10,
                body.toString(), "de", NOW));
        assertEquals(201, changed.status);
        ObjectNode tree = storedTree(store, FakeEditStore.json(changed).get("id").asLong());
        assertEquals(4, tree.get("chartsPerScreen").asInt());
        assertTrue(tree.get("customTop").asBoolean());
        /* the unreadable stored row of the source is kept */
        assertEquals(23, tree.get("chartModels").get(0).get("chartData").get(1).get("id").asLong());

        /* legacy source: written in the current format */
        WebEdit.Reply legacy = WebEdit.call(store, s -> ResourceWebEditAnalysis.copy(s, new FakeEngine(store), 11,
                "{\"directoryId\":2,\"name\":\"Old 2\"}", "de", NOW));
        assertEquals(201, legacy.status);
        assertEquals("Legacy chart", storedTree(store, FakeEditStore.json(legacy).get("id").asLong())
                .get("chartModels").get(0).get("chartName").asText());

        assertEquals(409, WebEdit.call(store, s -> ResourceWebEditAnalysis.copy(s, new FakeEngine(store), 10,
                "{\"directoryId\":2,\"name\":\"Overview\"}", "de", NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditAnalysis.copy(s, new FakeEngine(store), 13,
                "{\"directoryId\":2,\"name\":\"X\"}", "de", NOW)).status);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditAnalysis.copy(s, new FakeEngine(store), 10,
                "{\"directoryId\":3,\"name\":\"X\"}", "de", NOW)).status);
    }

    @Test
    public void deleteMovesToTheRecycleBin() throws Exception {
        FakeEditStore store = store();
        String version = WebEdit.version(store.latest(10, FILE));
        FakeEngine engine = new FakeEngine(store);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditAnalysis.delete(s, engine, 11, version)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditAnalysis.delete(s, engine, 10, null)).status);
        assertEquals(409, WebEdit.call(store, s -> ResourceWebEditAnalysis.delete(s, engine, 10, "2020-01-01T00:00:00Z")).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditAnalysis.delete(s, engine, 13, version)).status);
        assertTrue(store.recycled.isEmpty());
        assertEquals(204, WebEdit.call(store, s -> ResourceWebEditAnalysis.delete(s, engine, 10, version)).status);
        assertEquals(Collections.singletonList(10L), store.recycled);
        assertTrue(store.logs.contains("MARK_AS_DELETE_OBJECT 10:Overview|web:analysis-delete"));
        assertEquals(404, get(store, 10).status);

        /* never saved: version "none" */
        store.add(obj(14, "Empty", ANALYSIS, 2));
        store.delete.add(14L);
        assertEquals(204, WebEdit.call(store, s -> ResourceWebEditAnalysis.delete(s, engine, 14, "none")).status);
    }

    // ---------------------------------------------------------------- helpers of the editor

    @Test
    public void directoriesWithPathAndCreateRight() throws Exception {
        FakeEditStore store = store();
        JsonNode json = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditAnalysis.directories(s, "de")));
        assertEquals(2, json.size());
        assertEquals("Musterwerk / Analysen", json.get(0).get("path").asText());
        assertEquals("Analysen", json.get(0).get("name").asText());
        assertTrue(json.get(0).get("canCreate").asBoolean());
        assertEquals(3, json.get(1).get("id").asLong());
        assertFalse(json.get(1).get("canCreate").asBoolean());
    }

    @Test
    public void rowTreeAndSearch() throws Exception {
        FakeEditStore store = store();
        JsonNode roots = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditAnalysis.tree(s, null, null, "de")));
        assertEquals(1, roots.size());
        assertEquals("Musterwerk", roots.get(0).get("name").asText());
        assertTrue(roots.get(0).get("hasChildren").asBoolean());
        assertFalse(roots.get(0).get("selectable").asBoolean());

        JsonNode children = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditAnalysis.tree(s, "20", null, "de")));
        List<String> names = new ArrayList<>();
        for (JsonNode child : children) names.add(child.get("name").asText());
        assertEquals(Arrays.asList("Alarm", "Meter A", "Meter C"), names);
        JsonNode meterA = children.get(1);
        assertEquals("Data", meterA.get("class").asText());
        assertTrue(meterA.get("selectable").asBoolean());
        assertTrue(meterA.get("hasChildren").asBoolean());
        assertEquals("kWh", meterA.get("unit").asText());
        assertEquals("Value", meterA.get("attributes").get(0).asText());
        assertFalse(children.get(0).get("selectable").asBoolean());
        assertTrue(children.get(0).get("unit").isNull());

        JsonNode hits = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditAnalysis.tree(s, null, "meter", "de")));
        names.clear();
        for (JsonNode hit : hits) names.add(hit.get("name").asText());
        assertEquals(Arrays.asList("Meter A", "Meter A clean", "Meter C", "Meters"), names);
        assertEquals("Musterwerk / Meters / Meter A", hits.get(1).get("path").asText());
        JsonNode below = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditAnalysis.tree(s, "21", "clean", "de")));
        assertEquals(1, below.size());
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditAnalysis.tree(s, null, "m", "de")).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditAnalysis.tree(s, "23", null, "de")).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditAnalysis.tree(s, "abc", null, "de")).status);
    }

    @Test
    public void previewEvaluatesTheUnsavedDefinition() throws Exception {
        FakeEditStore store = store();
        FakeEngine engine = new FakeEngine(store);
        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        body.put("timeFrame", "LAST_7_DAYS");
        body.put("id", 10);
        ObjectNode definition = getJson(store, 10);
        ((ObjectNode) definition.get("charts").get(0)).put("name", "Unsaved");
        body.set("definition", definition);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditAnalysis.preview(s, engine, body.toString(), NOW));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("LAST_7_DAYS", json.get("timeFrame").asText());
        /* charts sorted by name as in W2 */
        assertEquals("[\"1-22-Value\",\"0-21-Value\",\"0-23-Value:NO_PERMISSION\"]", json.get("rows").toString());
        assertEquals("Unsaved", engine.previewed.getCharts().get(1).getName());
        assertEquals(Long.valueOf(10), engine.previewContext);
        assertTrue(store.writes.isEmpty());

        /* a new unreadable row is allowed in the preview, it has no data */
        ObjectNode fresh = FakeEditStore.MAPPER.createObjectNode();
        fresh.put("directoryId", 2);
        fresh.set("definition", FakeEditStore.MAPPER.readTree("{\"charts\":[{\"id\":0,\"name\":\"C\",\"rows\":[{\"objectId\":23},{\"objectId\":25}]}]}"));
        JsonNode freshJson = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditAnalysis.preview(s, engine, fresh.toString(), NOW)));
        assertEquals("[\"0-23-Value:NO_PERMISSION\",\"0-25-Value\"]", freshJson.get("rows").toString());
        assertEquals(Long.valueOf(2), engine.previewContext);

        /* same validation as saving */
        fresh.set("definition", FakeEditStore.MAPPER.readTree("{\"charts\":[{\"id\":0,\"rows\":[{\"objectId\":24}]}]}"));
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditAnalysis.preview(s, engine, fresh.toString(), NOW)).status);
        fresh.put("timeFrame", "CUSTOM");
        WebEdit.Reply custom = WebEdit.call(store, s -> ResourceWebEditAnalysis.preview(s, engine, fresh.toString(), NOW));
        assertEquals(400, custom.status);
        assertTrue(custom.body instanceof String);
    }

    @Test
    public void writerOutputIsReadByTheReader() throws Exception {
        FakeEditStore store = store();
        AnalysisDefinition read = new AnalysisReader().readAnalysisFile(OVERVIEW.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = new AnalysisWriter().write(new AnalysisWriter().patch(storedTree(store, 10), read, NOW));
        assertEquals(storedTree(store, 10), FakeEditStore.MAPPER.readTree(bytes));
    }
}
