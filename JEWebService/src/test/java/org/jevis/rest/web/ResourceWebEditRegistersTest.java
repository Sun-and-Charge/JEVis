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
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.*;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

public class ResourceWebEditRegistersTest {

    private static final DateTime NOW = new DateTime(2026, 11, 13, 10, 30, 15, 500, ZONE);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final String MI = "Measurement Instrument";
    private static final String EMI = "Electricity Measurement Instrument";
    private static final String GMI = "Gas Measurement Instrument";
    private static final String MD = "Measurement Directory";
    private static final String EMD = "Electricity Measurement Directory";
    private static final String GMD = "Gas Measurement Directory";
    private static final String BE = "Building Equipment";
    private static final String PUMP = "Pump";
    private static final String ENGINE = "Engine";
    private static final String ED = "Equipment Directory";
    private static final String HED = "Heating Equipment Directory";
    private static final String SHED = "Special Heating Directory";

    // ---------------------------------------------------------------- classes

    private static JsonType type(String name, int primitive, String gui, int position) {
        JsonType type = new JsonType();
        type.setName(name);
        type.setPrimitiveType(primitive);
        type.setGuiType(gui);
        type.setGUIPosition(position);
        return type;
    }

    private static JsonClassRelationship rel(String start, String end, int type) {
        JsonClassRelationship rel = new JsonClassRelationship();
        rel.setStart(start);
        rel.setEnd(end);
        rel.setType(type);
        return rel;
    }

    private static void jclass(FakeEditStore store, String name, String inherits, List<JsonType> types, String... okParents) {
        JsonJEVisClass jclass = new JsonJEVisClass();
        jclass.setName(name);
        List<JsonClassRelationship> relationships = new ArrayList<>();
        if (inherits != null) relationships.add(rel(name, inherits, JEVisConstants.ClassRelationship.INHERIT));
        for (String parent : okParents) relationships.add(rel(name, parent, JEVisConstants.ClassRelationship.OK_PARENT));
        jclass.setRelationships(relationships);
        jclass.setTypes(new ArrayList<>(types));
        store.classMap.put(name, jclass);
    }

    private static final int S = JEVisConstants.PrimitiveType.STRING;
    private static final int D = JEVisConstants.PrimitiveType.DOUBLE;
    private static final int L = JEVisConstants.PrimitiveType.LONG;
    private static final int F = JEVisConstants.PrimitiveType.FILE;
    private static final int B = JEVisConstants.PrimitiveType.BOOLEAN;

    private static final List<JsonType> METER_TYPES = Arrays.asList(
            type("Online ID", S, "Object Target", 4),
            type("Decimal Places", L, "Text", 6),
            type("Factor", D, "Number", 7),
            type("Conversion Factor", 8, "Text", 8),
            type("Serial Number", S, "Text", 13),
            type("Picture", F, "File Selector", 17),
            type("GPS Position", F, "GPS", 18),
            type("Installation Date", S, "Date", 20),
            type("Verified", B, "Check Box", 21),
            type("Remarks", S, "Text Area", 23),
            type("Password", S, "Password", 30));

    private static final List<JsonType> EQUIPMENT_TYPES = Arrays.asList(
            type("Manufacturer", S, "Text", 1),
            type("Nominal Power", D, "Number with Unit", 2),
            type("Operating", B, "Check Box", 3),
            type("Commissioning", S, "Date", 4),
            type("Year of Construction", L, "Number", 5),
            type("Measuring Point", S, "Object Target", 6));

    private static final List<JsonType> VALUE = Collections.singletonList(type("Value", D, "Number", 1));

    /* Building 1 > Meters 10 > EMD 11 > Main meter 12 > Sub meter 13; Meters 10 > Gas meter 14 (directly);
       data 20 > clean 21; folder 22; hidden data 23; Other meters 30 (no medium directory); equipment directory 40,
       heating directory 41, special heating directory 42; unit 43 (BE), pump 44, engine 46; base data 45;
       hidden meter 15; meter in the bin 16 */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        jclass(store, MD, null, Collections.emptyList());
        jclass(store, EMD, MD, Collections.emptyList(), MD);
        jclass(store, GMD, MD, Collections.emptyList(), MD);
        jclass(store, MI, null, METER_TYPES, MD);
        jclass(store, EMI, MI, METER_TYPES, MD, EMD, EMI);
        jclass(store, GMI, MI, METER_TYPES, MD, GMD, GMI);
        jclass(store, "Data", null, VALUE);
        jclass(store, "Clean Data", null, Arrays.asList(type("Value", D, "Number", 1),
                type("Conversion to Differential", B, "Check Box", 2)));
        jclass(store, "Base Data", "Data", VALUE);
        jclass(store, "Folder", null, Collections.emptyList());
        jclass(store, ED, null, Collections.emptyList());
        jclass(store, HED, null, Collections.emptyList());
        jclass(store, SHED, HED, Collections.emptyList());
        jclass(store, BE, null, EQUIPMENT_TYPES, ED);
        jclass(store, PUMP, BE, EQUIPMENT_TYPES, HED);
        jclass(store, ENGINE, BE, EQUIPMENT_TYPES);
        JsonObject bin = obj(16, "Meter bin", EMI, 11);
        bin.setDeleteTS("2026-11-01T00:00:00.000+01:00");
        store.add(obj(1, "Building", ResourceWebNavigation.CLASS_BUILDING, 0),
                obj(10, "Meters", MD, 1),
                obj(11, EMD, EMD, 10),
                obj(12, "Main meter", EMI, 11),
                obj(13, "Sub meter", EMI, 12),
                obj(14, "Gas meter", GMI, 10),
                obj(15, "Hidden meter", EMI, 11),
                bin,
                obj(20, "Main meter data", "Data", 1),
                obj(21, "Main meter clean", "Clean Data", 20),
                obj(22, "Folder", "Folder", 1),
                obj(23, "Hidden data", "Data", 1),
                obj(30, "Other meters", MD, 1),
                obj(40, "Equipment", ED, 1),
                obj(41, "Heating", HED, 1),
                obj(42, "Special heating", SHED, 1),
                obj(43, "Unit", BE, 40),
                obj(44, "Pump 1", PUMP, 41),
                obj(45, "Price", "Base Data", 1),
                obj(46, "Engine 1", ENGINE, 40));
        store.hidden.addAll(Arrays.asList(15L, 23L));
        store.sample(12, "Serial Number", NOW.minusDays(10), "A-1", null);
        store.sample(12, "Online ID", NOW.minusDays(10), "20:Value", null);
        store.sample(12, "Factor", NOW.minusDays(5), "1.0", null);
        store.inputUnits.put("12/Factor", "m³");
        store.inputUnits.put("13/Factor", "kWh");
        return store;
    }

    private static ResourceWebRegisters.Translations translations(FakeEditStore store) {
        return new ResourceWebRegisters.Translations(Collections.emptyList(), "en", store.classes());
    }

    private static String version(FakeEditStore store, long id, String attribute) {
        return WebEdit.version(store.latest(id, attribute));
    }

    private static WebEdit.Reply put(FakeEditStore store, long id, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditRegisters.update(s, translations(store), id, body, NOW));
    }

    private static WebEdit.Reply create(FakeEditStore store, String kind, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditRegisters.create(s, translations(store), kind, body, NOW));
    }

    private static List<String> messages(WebEdit.Reply reply) throws Exception {
        assertEquals(400, reply.status);
        List<String> list = new ArrayList<>();
        for (JsonNode node : FakeEditStore.json(reply).get("messages")) list.add(node.asText());
        return list;
    }

    private static boolean anyContains(List<String> messages, String part) {
        for (String message : messages) {
            if (message.contains(part)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> json, String key) {
        return (List<Map<String, Object>>) json.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> byKey(List<Map<String, Object>> list, String field, Object value) {
        for (Map<String, Object> m : list) {
            if (value.equals(m.get(field))) return m;
        }
        throw new AssertionError("missing " + value);
    }

    // ---------------------------------------------------------------- reading

    @Test
    public void meterReadAnswerCarriesEditInfo() {
        FakeEditStore store = store();
        store.write.add(12L);
        store.delete.add(13L);
        store.create.add(11L);
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Data data = store.registerData();
        List<Map<String, Object>> groups = ResourceWebRegisters.meterGroups(lookup, store.classes(), translations(store),
                data, TODAY);
        ResourceWebEditRegisters.addEditInfo("meters", groups, lookup, store.classes(), store.rights(), data);

        Map<String, Object> meters = byKey(groups, "key", "10");
        assertEquals(true, meters.get("canCreate"));
        assertEquals(10L, meters.get("createTarget"));
        Map<String, Object> other = byKey(groups, "key", "30");
        assertEquals(false, other.get("canCreate"));
        assertEquals(30L, other.get("createTarget"));

        List<Map<String, Object>> columns = list(meters, "columns");
        Map<String, Object> serial = byKey(columns, "key", "Serial Number");
        assertEquals("STRING", serial.get("primitiveType"));
        assertEquals("Text", serial.get("guiType"));
        assertEquals(true, serial.get("editable"));
        assertEquals(false, serial.get("required"));
        assertNull(serial.get("inputUnit"));
        Map<String, Object> factor = byKey(columns, "key", "Factor");
        assertEquals("DOUBLE", factor.get("primitiveType"));
        assertEquals("m³", factor.get("inputUnit"));
        assertEquals(false, byKey(columns, "key", "GPS Position").get("editable"));
        assertEquals(true, byKey(columns, "key", "Picture").get("editable"));
        Map<String, Object> conversion = byKey(columns, "key", "Conversion Factor");
        assertEquals("UNKNOWN", conversion.get("primitiveType"));
        assertEquals(false, conversion.get("editable"));
        for (Map<String, Object> column : columns) assertNotEquals("Password", column.get("key"));

        Map<String, Object> main = byKey(list(meters, "rows"), "id", 12L);
        assertEquals(true, main.get("canWrite"));
        assertEquals(false, main.get("canDelete"));
        assertEquals(true, main.get("canUpload"));
        Map<String, Object> versions = map(main.get("versions"));
        assertEquals(NOW.minusDays(10).getMillis(), new DateTime(versions.get("Serial Number")).getMillis());
        assertTrue(versions.containsKey("Remarks"));
        assertNull(versions.get("Remarks"));
        assertFalse(versions.containsKey("Password"));
        assertEquals(NOW.minusDays(5).getMillis(), new DateTime(main.get("version")).getMillis());
        assertTrue(map(main.get("inputUnits")).isEmpty());
        Map<String, Object> values = map(main.get("values"));
        assertEquals("A-1", values.get("Serial Number"));
        assertTrue(values.containsKey("Remarks"));
        assertNull(values.get("Remarks"));
        assertFalse(values.containsKey("Password"));

        Map<String, Object> sub = byKey(list(meters, "rows"), "id", 13L);
        assertEquals(false, sub.get("canWrite"));
        assertEquals(true, sub.get("canDelete"));
        assertEquals("none", sub.get("version"));
        assertEquals("kWh", map(sub.get("inputUnits")).get("Factor"));
    }

    @Test
    public void meterGroupCanCreateWhenTheMediumDirectoryCanBeCreated() {
        FakeEditStore store = store();
        store.create.add(30L);
        ResourceWebModules.Lookup lookup = store.lookup();
        List<Map<String, Object>> groups = ResourceWebRegisters.meterGroups(lookup, store.classes(), translations(store),
                store.registerData(), TODAY);
        ResourceWebEditRegisters.addEditInfo("meters", groups, lookup, store.classes(), store.rights(), store.registerData());
        assertEquals(true, byKey(groups, "key", "30").get("canCreate"));
        assertEquals(false, byKey(groups, "key", "10").get("canCreate"));
    }

    @Test
    public void equipmentReadAnswerCarriesEditInfo() {
        FakeEditStore store = store();
        store.create.add(42L);
        store.write.add(44L);
        ResourceWebModules.Lookup lookup = store.lookup();
        List<Map<String, Object>> groups = ResourceWebRegisters.equipmentGroups(lookup, store.classes(),
                translations(store), store.registerData());
        ResourceWebEditRegisters.addEditInfo("equipment", groups, lookup, store.classes(), store.rights(), store.registerData());
        Map<String, Object> pumps = byKey(groups, "key", PUMP);
        assertEquals(true, pumps.get("canCreate"));
        assertNull(pumps.get("createTarget"));
        assertEquals(false, byKey(groups, "key", BE).get("canCreate"));
        assertEquals(false, byKey(groups, "key", ENGINE).get("canCreate"));
        Map<String, Object> pump = list(pumps, "rows").get(0);
        assertEquals(true, pump.get("canWrite"));
        assertEquals(6, map(pump.get("versions")).size());
        assertTrue(map(pump.get("values")).containsKey("Manufacturer"));
        assertNull(map(pump.get("values")).get("Manufacturer"));
    }

    @Test
    public void baseDataGetsNoEditInfo() {
        FakeEditStore store = store();
        List<Map<String, Object>> groups = ResourceWebRegisters.baseDataGroups(store.lookup(), store.classes(),
                translations(store), store.registerData());
        ResourceWebEditRegisters.addEditInfo("basedata", groups, store.lookup(), store.classes(), store.rights(), store.registerData());
        assertFalse(groups.get(0).containsKey("canCreate"));
        assertFalse(list(groups.get(0), "rows").get(0).containsKey("canWrite"));
    }

    // ---------------------------------------------------------------- PUT

    @Test
    public void updateWritesTypedValuesAtSaveTime() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        String body = "{\"fields\": {"
                + "\"Serial Number\": {\"version\": \"" + version(store, 12, "Serial Number") + "\", \"value\": \"B-2\"},"
                + "\"Factor\": {\"version\": \"" + version(store, 12, "Factor") + "\", \"value\": 12.5},"
                + "\"Decimal Places\": {\"version\": null, \"value\": 3},"
                + "\"Verified\": {\"version\": null, \"value\": true},"
                + "\"Installation Date\": {\"version\": null, \"value\": \"2015-10-01\"},"
                + "\"Remarks\": {\"version\": null, \"value\": null},"
                + "\"Online ID\": {\"version\": \"" + version(store, 12, "Online ID") + "\", \"value\": {\"id\": 20}}"
                + "}}";
        WebEdit.Reply reply = put(store, 12, body);
        assertEquals(200, reply.status);
        DateTime saved = NOW.withMillisOfSecond(0);
        assertEquals("B-2", store.at(12, "Serial Number", saved).getValue());
        assertEquals("12.5", store.at(12, "Factor", saved).getValue());
        assertEquals("3", store.at(12, "Decimal Places", saved).getValue());
        assertEquals("true", store.at(12, "Verified", saved).getValue());
        assertEquals("2015-10-01T00:00:00.000Z", store.at(12, "Installation Date", saved).getValue());
        assertEquals("", store.at(12, "Remarks", saved).getValue());
        assertEquals("20:Value", store.at(12, "Online ID", saved).getValue());
        /* the old values stay as history */
        assertEquals("A-1", store.at(12, "Serial Number", NOW.minusDays(10)).getValue());
        assertTrue(store.writes.contains("12/Factor " + D + " 12.5 | null"));
        assertTrue(store.logs.contains("CREATE_SAMPLE 12:Serial Number|1|web:register-save"));
        assertTrue(store.waits.isEmpty());

        JsonNode row = FakeEditStore.json(reply);
        assertEquals(12, row.get("id").asInt());
        assertEquals("B-2", row.get("values").get("Serial Number").asText());
        assertEquals("2015-10-01", row.get("values").get("Installation Date").asText());
        assertEquals(20, row.get("target").get("id").asInt());
        assertTrue(row.get("values").get("Remarks").isNull());
        assertEquals(saved.getMillis(), new DateTime(row.get("versions").get("Factor").asText()).getMillis());
        assertTrue(row.get("canWrite").asBoolean());
        assertTrue(row.has("canEnter"));
    }

    @Test
    public void nullClearsNumbersDatesAndBooleansWithAnEmptySampleAsTheControlCenter() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        store.sample(12, "Installation Date", NOW.minusDays(5), "2015-10-01T00:00:00.000Z", null);
        String body = "{\"fields\": {"
                + "\"Factor\": {\"version\": \"" + version(store, 12, "Factor") + "\", \"value\": null},"
                + "\"Installation Date\": {\"version\": \"" + version(store, 12, "Installation Date") + "\", \"value\": \"\"},"
                + "\"Decimal Places\": {\"version\": null, \"value\": null},"
                + "\"Verified\": {\"version\": null, \"value\": null}"
                + "}}";
        WebEdit.Reply reply = put(store, 12, body);
        assertEquals(200, reply.status);
        DateTime saved = NOW.withMillisOfSecond(0);
        for (String attribute : Arrays.asList("Factor", "Installation Date", "Decimal Places", "Verified")) {
            assertEquals(attribute, "", store.at(12, attribute, saved).getValue());
        }
        JsonNode values = FakeEditStore.json(reply).get("values");
        assertTrue(values.get("Factor").isNull());
        assertTrue(values.get("Installation Date").isNull());
    }

    @Test
    public void updateEquipmentDatesInTheSiteZoneAndTargets() throws Exception {
        FakeEditStore store = store();
        store.write.add(44L);
        String body = "{\"fields\": {"
                + "\"Commissioning\": {\"version\": null, \"value\": \"2026-03-01\"},"
                + "\"Year of Construction\": {\"version\": null, \"value\": 2020},"
                + "\"Nominal Power\": {\"version\": null, \"value\": 3},"
                + "\"Measuring Point\": {\"version\": null, \"value\": {\"id\": 21, \"attribute\": \"Value\"}},"
                + "\"Operating\": {\"version\": null, \"value\": false}"
                + "}}";
        WebEdit.Reply reply = put(store, 44, body);
        assertEquals(200, reply.status);
        DateTime saved = NOW.withMillisOfSecond(0);
        assertEquals("2026-03-01T00:00:00.000+01:00", store.at(44, "Commissioning", saved).getValue());
        assertEquals("2020", store.at(44, "Year of Construction", saved).getValue());
        assertEquals("3.0", store.at(44, "Nominal Power", saved).getValue());
        assertEquals("21:Value", store.at(44, "Measuring Point", saved).getValue());
        assertEquals("false", store.at(44, "Operating", saved).getValue());
        JsonNode row = FakeEditStore.json(reply);
        assertEquals("2026-03-01", row.get("values").get("Commissioning").asText());
        assertEquals(21, row.get("values").get("Measuring Point").get("id").asInt());
    }

    @Test
    public void storedDatesFollowTheControlCenter() {
        LocalDate date = new LocalDate(2026, 7, 1);
        assertEquals("2026-07-01T00:00:00.000Z", ResourceWebEditRegisters.storedDate(date, true, ZONE));
        assertEquals("2026-07-01T00:00:00.000+02:00", ResourceWebEditRegisters.storedDate(date, false, ZONE));
        /* read back as the same day */
        assertEquals(date, ResourceWebRegisters.parseDate("2026-07-01T00:00:00.000Z"));
        assertEquals(date, ResourceWebRegisters.parseDate("2026-07-01T00:00:00.000+02:00"));
        assertNull(ResourceWebEditRegisters.parseDate("2026-02-30"));
        assertNull(ResourceWebEditRegisters.parseDate("01.07.2026"));
    }

    @Test
    public void updateVersionConflictWritesNothing() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        String body = "{\"fields\": {\"Serial Number\": {\"version\": \"2026-01-01T00:00:00.000+01:00\", \"value\": \"B-2\"},"
                + "\"Remarks\": {\"version\": null, \"value\": \"x\"}}}";
        WebEdit.Reply reply = put(store, 12, body);
        assertEquals(409, reply.status);
        assertTrue(store.writes.isEmpty());
        JsonNode row = FakeEditStore.json(reply);
        assertEquals("A-1", row.get("values").get("Serial Number").asText());

        /* null version although there is a sample */
        reply = put(store, 12, "{\"fields\": {\"Serial Number\": {\"version\": null, \"value\": \"B-2\"}}}");
        assertEquals(409, reply.status);
        /* a version although there is no sample */
        reply = put(store, 12, "{\"fields\": {\"Remarks\": {\"version\": \"" + version(store, 12, "Serial Number")
                + "\", \"value\": \"x\"}}}");
        assertEquals(409, reply.status);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void updateNeedsVersions() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        WebEdit.Reply reply = put(store, 12, "{\"fields\": {\"Serial Number\": {\"value\": \"B-2\"}}}");
        assertEquals(400, reply.status);
        assertEquals("version missing", reply.body);
        reply = put(store, 12, "{\"fields\": {\"Serial Number\": {\"version\": \"soon\", \"value\": \"B-2\"}}}");
        assertEquals(400, reply.status);
        assertEquals("Invalid version", reply.body);
        assertEquals("Missing fields", put(store, 12, "{}").body);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void updateValidatesAllFieldsAtOnce() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        String body = "{\"fields\": {"
                + "\"Unknown\": {\"version\": null, \"value\": \"x\"},"
                + "\"Password\": {\"version\": null, \"value\": \"x\"},"
                + "\"GPS Position\": {\"version\": null, \"value\": \"x\"},"
                + "\"Picture\": {\"version\": null, \"value\": \"x\"},"
                + "\"Conversion Factor\": {\"version\": null, \"value\": \"x\"},"
                + "\"Factor\": {\"version\": null, \"value\": \"abc\"},"
                + "\"Decimal Places\": {\"version\": null, \"value\": 1.5},"
                + "\"Verified\": {\"version\": null, \"value\": \"yes\"},"
                + "\"Installation Date\": {\"version\": null, \"value\": \"01.10.2015\"},"
                + "\"Serial Number\": {\"version\": null, \"value\": 5},"
                + "\"Remarks\": {\"version\": null}"
                + "}}";
        List<String> messages = messages(put(store, 12, body));
        assertTrue(anyContains(messages, "Unknown attribute: Unknown"));
        assertTrue(anyContains(messages, "Password cannot be changed"));
        assertTrue(anyContains(messages, "GPS Position cannot be changed"));
        assertTrue(anyContains(messages, "Picture: files are uploaded"));
        assertTrue(anyContains(messages, "Conversion Factor cannot be changed"));
        assertTrue(anyContains(messages, "Factor: number expected"));
        assertTrue(anyContains(messages, "Decimal Places: integer expected"));
        assertTrue(anyContains(messages, "Verified: true or false expected"));
        assertTrue(anyContains(messages, "Installation Date: date yyyy-MM-dd expected"));
        assertTrue(anyContains(messages, "Serial Number: text expected"));
        assertTrue(anyContains(messages, "Remarks: value missing"));
        assertEquals(11, messages.size());
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void targetsMustBeReadableDataRowsWithTheAttribute() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        for (String target : Arrays.asList("{\"id\": 22}", "{\"id\": 23}", "{\"id\": 999}", "{\"id\": 20, \"attribute\": \"Nope\"}",
                "\"20:Value\"", "{\"id\": \"20\"}")) {
            WebEdit.Reply reply = put(store, 12, "{\"fields\": {\"Online ID\": {\"version\": \""
                    + version(store, 12, "Online ID") + "\", \"value\": " + target + "}}}");
            assertEquals(target, 400, reply.status);
        }
        assertTrue(store.writes.isEmpty());
        /* the attribute is stored with the type's name; clean data rows are allowed */
        WebEdit.Reply reply = put(store, 12, "{\"fields\": {\"Online ID\": {\"version\": \""
                + version(store, 12, "Online ID") + "\", \"value\": {\"id\": 21, \"attribute\": \"Value\"}}}}");
        assertEquals(200, reply.status);
        assertEquals("21:Value", store.latest(12, "Online ID").getValue());
        /* null clears */
        reply = put(store, 12, "{\"fields\": {\"Online ID\": {\"version\": \""
                + version(store, 12, "Online ID") + "\", \"value\": null}}}");
        assertEquals(200, reply.status);
        assertEquals("", store.latest(12, "Online ID").getValue());
    }

    @Test
    public void updateRightsAndNotFound() throws Exception {
        FakeEditStore store = store();
        String body = "{\"fields\": {\"Remarks\": {\"version\": null, \"value\": \"x\"}}}";
        assertEquals(403, put(store, 12, body).status);
        store.write.addAll(Arrays.asList(15L, 16L, 45L, 20L));
        assertEquals(404, put(store, 15, body).status);
        assertEquals(404, put(store, 16, body).status);
        assertEquals(404, put(store, 45, body).status);
        assertEquals(404, put(store, 20, body).status);
        assertEquals(404, put(store, 999, body).status);
        assertEquals(400, put(store, 12, "no json").status);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void updateRenames() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        WebEdit.Reply reply = put(store, 12, "{\"name\": \"  New main meter \"}");
        assertEquals(200, reply.status);
        assertEquals(Collections.singletonList("12 New main meter"), store.renames);
        assertTrue(store.logs.contains("UPDATE_OBJECT 12:New main meter|web:register-save"));
        assertEquals("New main meter", FakeEditStore.json(reply).get("name").asText());
        assertTrue(messages(put(store, 12, "{\"name\": \"  \"}")).contains("Missing name"));
        assertTrue(messages(put(store, 12, "{\"name\": \"a\\u0001b\"}")).contains("Invalid characters in name"));
    }

    @Test
    public void saveInTheSameSecondWaitsForTheNextSecond() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        store.sample(12, "Remarks", NOW.withMillisOfSecond(0), "first", null);
        WebEdit.Reply reply = put(store, 12, "{\"fields\": {\"Remarks\": {\"version\": \"" + version(store, 12, "Remarks")
                + "\", \"value\": \"second\"}}}");
        assertEquals(200, reply.status);
        DateTime next = NOW.withMillisOfSecond(0).plusSeconds(1);
        assertEquals("second", store.at(12, "Remarks", next).getValue());
        assertEquals("first", store.at(12, "Remarks", NOW.withMillisOfSecond(0)).getValue());
        assertEquals(Collections.singletonList(next), store.waits);
    }

    // ---------------------------------------------------------------- create

    @Test
    public void createMeterInTheExistingMediumDirectory() throws Exception {
        FakeEditStore store = store();
        store.create.add(11L);
        WebEdit.Reply reply = create(store, "meters", "{\"groupKey\": \"10\", \"class\": \"" + EMI
                + "\", \"name\": \" New meter \", \"parentId\": null, \"fields\": {\"Serial Number\": \"C-3\","
                + " \"Installation Date\": {\"value\": \"2026-11-01\"}}}");
        assertEquals(201, reply.status);
        JsonObject meter = store.object(9000);
        assertEquals("New meter", meter.getName());
        assertEquals(EMI, meter.getJevisClass());
        assertEquals(11L, (long) meter.getParent());
        assertEquals("C-3", store.at(9000, "Serial Number", NOW.withMillisOfSecond(0)).getValue());
        assertEquals("2026-11-01T00:00:00.000Z", store.latest(9000, "Installation Date").getValue());
        assertTrue(store.logs.contains("CREATE_OBJECT 9000:New meter|web:register-create"));
        assertTrue(store.logs.contains("CREATE_SAMPLE 9000:Serial Number|1|web:register-create"));
        JsonNode row = FakeEditStore.json(reply);
        assertEquals(9000, row.get("id").asInt());
        assertEquals("ELECTRICITY", row.get("medium").asText());
        assertEquals("Building / Meters / " + EMD, row.get("path").asText());
        assertEquals("C-3", row.get("values").get("Serial Number").asText());
    }

    @Test
    public void createMeterCreatesTheMediumDirectoryAsTheControlCenter() throws Exception {
        FakeEditStore store = store();
        store.create.add(30L);
        WebEdit.Reply reply = create(store, "meters", "{\"groupKey\": 30, \"class\": \"" + GMI
                + "\", \"name\": \"Gas main\"}");
        assertEquals(201, reply.status);
        JsonObject directory = store.object(9000);
        assertEquals(GMD, directory.getJevisClass());
        assertEquals(GMD, directory.getName());
        assertEquals(30L, (long) directory.getParent());
        JsonObject meter = store.object(9001);
        assertEquals(9000L, (long) meter.getParent());
        assertTrue(store.logs.contains("CREATE_OBJECT 9000:" + GMD + "|web:register-create"));

        /* the second meter goes into the same directory */
        assertEquals(201, create(store, "meters", "{\"groupKey\": \"30\", \"class\": \"" + GMI
                + "\", \"name\": \"Gas sub\"}").status);
        assertEquals(9000L, (long) store.object(9002).getParent());
    }

    @Test
    public void createRollsBackObjectAndDirectoryAfterAFailure() throws Exception {
        FakeEditStore store = new FakeEditStore() {
            @Override
            public int write(long id, String attribute, int primitiveType, List<JsonSample> written) {
                if ("Serial Number".equals(attribute)) throw new IllegalStateException("database gone");
                return super.write(id, attribute, primitiveType, written);
            }
        };
        FakeEditStore template = store();
        store.classMap.putAll(template.classMap);
        store.add(template.objects.toArray(new JsonObject[0]));
        store.create.add(30L);
        try {
            create(store, "meters", "{\"groupKey\": \"30\", \"class\": \"" + GMI
                    + "\", \"name\": \"Gas main\", \"fields\": {\"Serial Number\": \"X\"}}");
            fail("expected the write error");
        } catch (IllegalStateException expected) {
            // rethrown after the rollback
        }
        assertEquals(Arrays.asList(9001L, 9000L), store.deletedObjects);
        assertTrue(store.logs.contains("DELETE_OBJECT 9001:Gas main|web:register-create-rollback"));
        assertNull(store.object(9000));
    }

    @Test
    public void createMeterBelowAParentMeter() throws Exception {
        FakeEditStore store = store();
        store.create.addAll(Arrays.asList(12L, 14L));
        WebEdit.Reply reply = create(store, "meters", "{\"groupKey\": \"10\", \"class\": \"" + EMI
                + "\", \"name\": \"Sub 2\", \"parentId\": 12}");
        assertEquals(201, reply.status);
        assertEquals(12L, (long) store.object(9000).getParent());
        assertEquals("Building / Meters / " + EMD + " / Main meter", FakeEditStore.json(reply).get("path").asText());

        /* not allowed under a meter of another medium (class relationships) */
        List<String> messages = messages(create(store, "meters", "{\"groupKey\": \"10\", \"class\": \"" + GMI
                + "\", \"name\": \"Gas sub\", \"parentId\": 12}"));
        assertTrue(anyContains(messages, "cannot be created below " + EMI));
        /* parent outside of the group, hidden or in the bin */
        for (long parent : new long[]{20, 15, 16}) {
            assertTrue(anyContains(messages(create(store, "meters", "{\"groupKey\": \"10\", \"class\": \"" + EMI
                    + "\", \"name\": \"x\", \"parentId\": " + parent + "}")), "The parent must be a meter of the group"));
        }
        assertEquals(403, create(store, "meters", "{\"groupKey\": \"10\", \"class\": \"" + EMI
                + "\", \"name\": \"x\", \"parentId\": 13}").status);
    }

    @Test
    public void createMeterValidationRightsAndNotFound() throws Exception {
        FakeEditStore store = store();
        String ok = "\", \"name\": \"x\"}";
        assertEquals(403, create(store, "meters", "{\"groupKey\": \"10\", \"class\": \"" + EMI + ok).status);
        assertEquals(403, create(store, "meters", "{\"groupKey\": \"30\", \"class\": \"" + EMI + ok).status);
        assertEquals(404, create(store, "meters", "{\"groupKey\": \"11\", \"class\": \"" + EMI + ok).status);
        assertEquals(404, create(store, "meters", "{\"groupKey\": \"999\", \"class\": \"" + EMI + ok).status);
        assertEquals("Missing groupKey", create(store, "meters", "{\"class\": \"" + EMI + ok).body);
        assertEquals(404, create(store, "basedata", "{}").status);
        store.create.add(11L);
        List<String> messages = messages(create(store, "meters", "{\"groupKey\": \"10\", \"class\": \"" + MI
                + "\", \"fields\": {\"Nope\": 1}}"));
        assertTrue(messages.contains("Unknown class: " + MI));
        assertTrue(messages.contains("Missing name"));
        messages = messages(create(store, "meters", "{\"groupKey\": \"10\", \"class\": \"" + EMI
                + "\", \"name\": \"x\", \"fields\": {\"Nope\": 1, \"Factor\": \"a\"}}"));
        assertEquals(Arrays.asList("Unknown attribute: Nope", "Factor: number expected"), messages);
        assertTrue(anyContains(messages(create(store, "meters", "{\"groupKey\": \"10\", \"class\": \"" + EMI
                + "\", \"name\": \"" + String.join("", Collections.nCopies(101, "a")) + "\"}")), "longer than 100"));
        assertTrue(store.objects.size() == store().objects.size());
    }

    @Test
    public void meterCreateOptions() throws Exception {
        FakeEditStore store = store();
        store.create.addAll(Arrays.asList(11L, 12L));
        JsonNode json = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditRegisters.meterOptions(s,
                translations(store), "10")));
        assertEquals(1, json.get("classes").size());
        assertEquals(EMI, json.get("classes").get(0).get("class").asText());
        assertEquals("ELECTRICITY", json.get("classes").get(0).get("medium").asText());
        assertEquals(EMI, json.get("classes").get(0).get("label").asText());
        assertEquals(1, json.get("parents").size());
        assertEquals(12, json.get("parents").get(0).get("id").asInt());

        store.create.add(10L);
        json = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditRegisters.meterOptions(s, translations(store), "10")));
        assertEquals(2, json.get("classes").size());
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditRegisters.meterOptions(s, translations(store), "x")).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditRegisters.meterOptions(s, translations(store), "40")).status);
    }

    @Test
    public void createEquipmentBelowAnAllowedParent() throws Exception {
        FakeEditStore store = store();
        store.create.addAll(Arrays.asList(40L, 41L, 42L));
        WebEdit.Reply reply = create(store, "equipment", "{\"class\": \"" + PUMP
                + "\", \"parentId\": 42, \"name\": \"Pump 2\", \"fields\": {\"Commissioning\": \"2026-01-15\","
                + " \"Manufacturer\": \"Maker\"}}");
        assertEquals(201, reply.status);
        JsonObject pump = store.object(9000);
        assertEquals(42L, (long) pump.getParent());
        assertEquals("2026-01-15T00:00:00.000+01:00", store.latest(9000, "Commissioning").getValue());
        JsonNode row = FakeEditStore.json(reply);
        assertEquals("Maker", row.get("values").get("Manufacturer").asText());
        assertEquals(PUMP, row.get("className").asText());

        /* pumps only below heating directories (own relationship of the class) */
        assertTrue(anyContains(messages(create(store, "equipment", "{\"class\": \"" + PUMP
                + "\", \"parentId\": 40, \"name\": \"x\"}")), "cannot be created below " + ED));
        assertTrue(anyContains(messages(create(store, "equipment", "{\"class\": \"" + ENGINE
                + "\", \"parentId\": 40, \"name\": \"x\"}")), "cannot be created below " + ED));
        assertTrue(messages(create(store, "equipment", "{\"class\": \"" + PUMP + "\", \"name\": \"x\"}"))
                .contains("Missing parentId"));
        assertTrue(messages(create(store, "equipment", "{\"class\": \"" + EMI + "\", \"parentId\": 41, \"name\": \"x\"}"))
                .contains("Unknown class: " + EMI));
        assertEquals(404, create(store, "equipment", "{\"class\": \"" + PUMP + "\", \"parentId\": 23, \"name\": \"x\"}").status);
        store.create.remove(41L);
        assertEquals(403, create(store, "equipment", "{\"class\": \"" + PUMP + "\", \"parentId\": 41, \"name\": \"x\"}").status);
        assertEquals(201, create(store, "equipment", "{\"class\": \"" + BE + "\", \"parentId\": 40, \"name\": \"Unit 2\"}").status);
    }

    @Test
    public void equipmentCreateOptions() throws Exception {
        FakeEditStore store = store();
        store.create.addAll(Arrays.asList(41L, 42L, 40L));
        JsonNode json = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditRegisters.equipmentOptions(s, PUMP)));
        assertEquals(2, json.get("parents").size());
        assertEquals(41, json.get("parents").get(0).get("id").asInt());
        assertEquals("Building", json.get("parents").get(0).get("path").asText());
        assertEquals(42, json.get("parents").get(1).get("id").asInt());
        json = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditRegisters.equipmentOptions(s, ENGINE)));
        assertEquals(0, json.get("parents").size());
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditRegisters.equipmentOptions(s, EMI)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditRegisters.equipmentOptions(s, null)).status);
    }

    // ---------------------------------------------------------------- delete

    private static WebEdit.Reply delete(FakeEditStore store, long id, String version) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditRegisters.delete(s, translations(store), id, version, NOW));
    }

    @Test
    public void deleteMovesToTheRecycleBin() throws Exception {
        FakeEditStore store = store();
        store.delete.addAll(Arrays.asList(12L, 13L, 44L, 45L));
        assertEquals(409, delete(store, 12, version(store, 12, "Serial Number")).status);
        assertEquals(409, delete(store, 12, "none").status);
        assertEquals("version missing", delete(store, 12, " ").body);
        assertTrue(store.recycled.isEmpty());
        assertEquals(204, delete(store, 12, version(store, 12, "Factor")).status);
        assertEquals(Collections.singletonList(12L), store.recycled);
        assertTrue(store.logs.contains("MARK_AS_DELETE_OBJECT 12:Main meter|web:register-delete"));

        /* an object without samples (new meter): empty version accepted */
        assertEquals(204, delete(store, 13, "").status);
        assertEquals(403, delete(store, 14, null).status);
        assertEquals(204, delete(store, 44, "none").status);
        assertEquals(404, delete(store, 45, "none").status);
        assertEquals(404, delete(store, 12, "none").status);
        assertEquals(403, delete(store, 14, "none").status);
    }

    // ---------------------------------------------------------------- files

    private static WebEdit.Reply upload(FakeEditStore store, long id, String attribute, String name, byte[] content,
                                        int max) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditRegisters.upload(s, id, attribute, name,
                content != null ? new ByteArrayInputStream(content) : null, max, NOW));
    }

    @Test
    public void uploadWritesANewFileSample() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        store.file(12, "Picture", NOW.minusDays(3), "old.jpg", "old");
        WebEdit.Reply reply = upload(store, 12, "picture", "C:\\fakepath\\..\\me<ter>.JPG", "img".getBytes(StandardCharsets.UTF_8), 1000);
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("Picture", json.get("attribute").asText());
        assertEquals("me_ter_.JPG", json.get("file").asText());
        assertTrue(json.get("image").asBoolean());
        assertEquals(NOW.withMillisOfSecond(0).getMillis(), new DateTime(json.get("version").asText()).getMillis());
        assertEquals("img", store.latestFile(12, "Picture"));
        assertEquals("old.jpg", store.at(12, "Picture", NOW.minusDays(3)).getValue());
        assertTrue(store.logs.contains("CREATE_SAMPLE 12:Picture|1|web:register-file"));

        assertEquals(false, FakeEditStore.json(upload(store, 12, "Picture", "sheet.pdf", new byte[]{1}, 1000)).get("image").asBoolean());
        assertEquals(413, upload(store, 12, "Picture", "big.jpg", new byte[1001], 1000).status);
        assertEquals("Missing file", upload(store, 12, "Picture", "x.jpg", null, 1000).body);
        assertEquals(400, upload(store, 12, "GPS Position", "x.gpx", new byte[]{1}, 1000).status);
        assertEquals(404, upload(store, 12, "Serial Number", "x.jpg", new byte[]{1}, 1000).status);
        assertEquals(404, upload(store, 12, "Nope", "x.jpg", new byte[]{1}, 1000).status);
        assertEquals(404, upload(store, 45, "Value", "x.jpg", new byte[]{1}, 1000).status);
        assertEquals(403, upload(store, 13, "Picture", "x.jpg", new byte[]{1}, 1000).status);
    }

    // ---------------------------------------------------------------- meter exchange

    private static final DateTime EXCHANGE = new DateTime(2026, 11, 12, 8, 0, 0, 0, ZONE);

    private static FakeEditStore exchangeStore() {
        FakeEditStore store = store();
        store.write.addAll(Arrays.asList(12L, 20L));
        store.delete.add(21L);
        store.dependents.add(21L);
        store.sample(21, "Conversion to Differential", NOW.minusDays(100), "true", null);
        store.sample(20, "Value", EXCHANGE.minusHours(2), "1000", null);
        store.sample(21, "Value", EXCHANGE.minusHours(1), "5", null);
        store.sample(21, "Value", EXCHANGE.plusHours(1), "5", null);
        return store;
    }

    private static WebEdit.Reply exchange(FakeEditStore store, long id, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditRegisters.exchange(s, id, body, "de", NOW));
    }

    private static String exchangeBody(Object oldReading, Object newReading, boolean confirm) {
        return "{\"timestamp\": \"2026-11-12T08:00:00\", \"oldReading\": " + oldReading + ", \"newReading\": "
                + newReading + ", \"confirm\": " + confirm + "}";
    }

    @Test
    public void exchangeWritesTwoReadingsAndResetsTheDependents() throws Exception {
        FakeEditStore store = exchangeStore();
        WebEdit.Reply reply = exchange(store, 12, exchangeBody(1234.5, 0, false));
        assertEquals(201, reply.status);
        JsonSample old = store.at(20, "Value", EXCHANGE.minusSeconds(1));
        assertEquals("1234.5", old.getValue());
        assertEquals("Meter Tausch", old.getNote());
        JsonSample first = store.at(20, "Value", EXCHANGE);
        assertEquals("0", first.getValue());
        assertEquals("Meter Tausch", first.getNote());
        assertTrue(store.logs.contains("CREATE_SAMPLE 20:Value|1|web:meter-exchange"));
        /* the clean data are reset from the old reading on */
        assertNotNull(store.at(21, "Value", EXCHANGE.minusHours(1)));
        assertNull(store.at(21, "Value", EXCHANGE.plusHours(1)));
        assertTrue(store.deletes.contains("21/Value " + EXCHANGE.minusSeconds(1).getMillis() + "-" + EXCHANGE.plusHours(1).getMillis()));

        JsonNode json = FakeEditStore.json(reply);
        assertEquals(20, json.get("rowId").asInt());
        assertEquals(2, json.get("samples").size());
        assertEquals(EXCHANGE.minusSeconds(1).getMillis(), new DateTime(json.get("samples").get(0).get("timestamp").asText()).getMillis());
        assertEquals("2026-11-12T08:00:00.000+01:00", json.get("samples").get(1).get("timestamp").asText());
        assertEquals(0, json.get("warnings").size());
    }

    @Test
    public void exchangePlausibilityNeedsConfirm() throws Exception {
        FakeEditStore store = exchangeStore();
        WebEdit.Reply reply = exchange(store, 12, exchangeBody(900, 0, false));
        assertEquals(409, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals(1, json.get("warnings").size());
        assertTrue(json.get("warnings").get(0).asText().endsWith("900 < 1000"));
        assertEquals(900, json.get("oldReading").asInt());
        assertTrue(store.writes.isEmpty());

        store.sample(20, "Value", EXCHANGE, "7", null);
        json = FakeEditStore.json(exchange(store, 12, exchangeBody(1100, 0, false)));
        assertEquals(1, json.get("warnings").size());
        assertTrue(json.get("warnings").get(0).asText().endsWith(" 7"));

        reply = exchange(store, 12, exchangeBody(900, 0, true));
        assertEquals(201, reply.status);
        assertEquals(2, FakeEditStore.json(reply).get("warnings").size());
        assertEquals("0", store.at(20, "Value", EXCHANGE).getValue());
    }

    @Test
    public void exchangeAlwaysWritesValueAsTheControlCenter() throws Exception {
        FakeEditStore store = exchangeStore();
        store.sample(12, "Online ID", NOW.minusDays(1), "20:Other", null);
        assertEquals(201, exchange(store, 12, exchangeBody(1000, 1, false)).status);
        assertEquals("1", store.at(20, "Value", EXCHANGE).getValue());
    }

    @Test
    public void exchangeValidationRightsAndNotFound() throws Exception {
        FakeEditStore store = exchangeStore();
        List<String> messages = messages(exchange(store, 12,
                "{\"timestamp\": \"2026-11-14T08:00:00\", \"oldReading\": -1, \"newReading\": \"a\"}"));
        assertEquals(Arrays.asList("The exchange cannot be in the future", "oldReading must not be negative",
                "Invalid newReading"), messages);
        messages = messages(exchange(store, 12, "{\"oldReading\": 1}"));
        assertEquals(Arrays.asList("Missing timestamp", "Missing newReading"), messages);
        assertTrue(store.writes.isEmpty());

        assertEquals(404, exchange(store, 44, exchangeBody(1, 1, false)).status);
        assertEquals(404, exchange(store, 15, exchangeBody(1, 1, false)).status);
        assertEquals(400, exchange(store, 13, exchangeBody(1, 1, false)).status);
        store.sample(13, "Online ID", NOW.minusDays(1), "21:Value", null);
        store.write.add(13L);
        assertEquals(400, exchange(store, 13, exchangeBody(1, 1, false)).status);
        store.sample(13, "Online ID", NOW, "23:Value", null);
        assertEquals(404, exchange(store, 13, exchangeBody(1, 1, false)).status);
        store.write.remove(20L);
        assertEquals(403, exchange(store, 12, exchangeBody(1, 1, false)).status);
        store.write.add(20L);
        store.write.remove(12L);
        assertEquals(403, exchange(store, 12, exchangeBody(1, 1, false)).status);
        assertTrue(store.writes.isEmpty());
    }
}
