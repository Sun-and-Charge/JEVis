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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.commons.alarm.AlarmType;
import org.jevis.commons.ws.json.JsonJEVisClass;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebAlarmsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final String CONFIG = ResourceWebAlarms.CLASS_ALARM_CONFIGURATION;

    // ---------------------------------------------------------------- objects

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    /* Organisation 1 > site 2 "Musterwerk" > folder 3 "Daten" > folder 4 "Strom" > row 20 "Strom - Gesamt";
       hidden row 30 below 4; site 2 > alarm folder 5 > configurations 100 (open), 101 (checked), 102 (disabled),
       103 (not readable), 104 (open, broken file, alarm of the hidden row), 105 (recycle bin);
       configuration 110 without building whose alarm points at a deleted object 999 */
    private static final JsonObject ORG = obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
    private static final JsonObject SITE = obj(2, "Musterwerk", ResourceWebNavigation.CLASS_BUILDING, 1);
    private static final JsonObject DATA_FOLDER = obj(3, "Daten", "Data Directory", 2);
    private static final JsonObject POWER_FOLDER = obj(4, "Strom", "Data Directory", 3);
    private static final JsonObject ALARM_FOLDER = obj(5, "Alarme", "Alarm Directory", 2);
    private static final JsonObject ROW = obj(20, "Strom - Gesamt", "Data", 4);
    private static final JsonObject HIDDEN_ROW = obj(30, "Strom - Halle", "Data", 4);
    private static final JsonObject OPEN = obj(100, "Strom - Gesamt Alarm", CONFIG, 5);
    private static final JsonObject CHECKED = obj(101, "Strom - Spitze Alarm", CONFIG, 5);
    private static final JsonObject DISABLED = obj(102, "Disabled Alarm", CONFIG, 5);
    private static final JsonObject HIDDEN = obj(103, "Hidden Alarm", CONFIG, 5);
    private static final JsonObject BROKEN = obj(104, "Halle Alarm", CONFIG, 5);
    private static final JsonObject BIN = obj(105, "Bin Alarm", CONFIG, 5);
    private static final JsonObject LONE = obj(110, "Lone Alarm", CONFIG, 0);

    static {
        BIN.setDeleteTS("2026-11-01T00:00:00.000+01:00");
    }

    private static final List<JsonObject> ALL = Arrays.asList(ORG, SITE, DATA_FOLDER, POWER_FOLDER, ALARM_FOLDER, ROW,
            HIDDEN_ROW, OPEN, CHECKED, DISABLED, HIDDEN, BROKEN, BIN, LONE);

    private static ResourceWebModules.Lookup lookup() {
        List<JsonObject> readable = new ArrayList<>(ALL);
        readable.removeAll(Arrays.asList(HIDDEN_ROW, HIDDEN));
        Map<Long, Long> parents = new HashMap<>();
        for (JsonObject o : ALL) parents.put(o.getId(), o.getParent());
        return new ResourceWebModules.Lookup(ALL, readable, parents);
    }

    private static ResourceWebRegisters.Classes classes() {
        Map<String, JsonJEVisClass> map = new HashMap<>();
        JsonJEVisClass building = new JsonJEVisClass();
        building.setName(ResourceWebNavigation.CLASS_BUILDING);
        building.setRelationships(new ArrayList<>());
        building.setTypes(new ArrayList<>());
        map.put(building.getName(), building);
        return new ResourceWebRegisters.Classes(map);
    }

    // ---------------------------------------------------------------- samples and files

    private static JsonSample sample(DateTime time, String value, String note) {
        JsonSample sample = new JsonSample();
        sample.setTs(ISODateTimeFormat.dateTime().print(time));
        sample.setValue(value);
        sample.setNote(note);
        return sample;
    }

    private static final class TestStore implements ResourceWebAlarms.Store {
        final Map<String, List<JsonSample>> samples = new HashMap<>();
        final Map<String, byte[]> files = new HashMap<>();
        final Map<String, String> units = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        TestStore add(long id, String attribute, DateTime time, String value, String note) {
            samples.computeIfAbsent(id + "/" + attribute, k -> new ArrayList<>()).add(sample(time, value, note));
            return this;
        }

        TestStore log(long id, DateTime time, String content) {
            add(id, ResourceWebAlarms.ATTRIBUTE_LOG_FILE, time, "log.json", null);
            files.put(id + "/" + time.getMillis(), content.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        @Override
        public List<JsonSample> samples(long id, String attribute, DateTime from, DateTime to, long limit) {
            calls.add("samples " + id + "/" + attribute);
            List<JsonSample> result = new ArrayList<>();
            for (JsonSample sample : samples.getOrDefault(id + "/" + attribute, Collections.emptyList())) {
                long millis = ResourceWebNotes.millis(sample.getTs());
                if (millis >= from.getMillis() && millis <= to.getMillis()) result.add(sample);
            }
            result.sort(Comparator.comparingLong(s -> ResourceWebNotes.millis(s.getTs())));
            return result.size() > limit ? new ArrayList<>(result.subList(0, (int) limit)) : result;
        }

        @Override
        public JsonSample latest(long id, String attribute) {
            calls.add("latest " + id + "/" + attribute);
            JsonSample latest = null;
            for (JsonSample sample : samples.getOrDefault(id + "/" + attribute, Collections.emptyList())) {
                if (latest == null || ResourceWebNotes.millis(sample.getTs()) > ResourceWebNotes.millis(latest.getTs())) {
                    latest = sample;
                }
            }
            return latest;
        }

        @Override
        public byte[] file(long id, String attribute, JsonSample sample) {
            calls.add("file " + id);
            return files.get(id + "/" + ResourceWebNotes.millis(sample.getTs()));
        }

        @Override
        public String unit(long id, String attribute) {
            calls.add("unit " + id + "/" + attribute);
            return units.get(id + "/" + attribute);
        }
    }

    private static final DateTime A1 = new DateTime(2026, 11, 10, 14, 15, ZONE);
    private static final DateTime A2 = new DateTime(2026, 11, 10, 14, 30, ZONE);
    private static final DateTime A3 = new DateTime(2026, 11, 9, 8, 0, ZONE);
    private static final DateTime OLD_ALARM = new DateTime(2026, 10, 1, 8, 0, ZONE);
    private static final DateTime LOG1 = new DateTime(2026, 11, 10, 23, 43, ZONE);
    private static final DateTime LOG2 = new DateTime(2026, 11, 9, 23, 43, ZONE);
    private static final DateTime LOG_OLD = new DateTime(2026, 10, 2, 23, 43, ZONE);
    private static final DateTime CHECK_TIME = new DateTime(2026, 11, 10, 8, 0, ZONE);
    private static final DateTime FROM = new DateTime(2026, 11, 4, 0, 0, ZONE);
    private static final DateTime TO = new DateTime(2026, 11, 12, 0, 0, ZONE).minusMillis(1);

    private static String entry(long object, String attribute, DateTime time, Double isValue, String operator,
                                Double shouldBe, String type, Integer logValue, Double tolerance) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("attribute", attribute);
        json.put("timeStamp", time.toString());
        json.put("isValue", isValue);
        json.put("operator", operator);
        json.put("shouldBeValue", shouldBe);
        json.put("object", object);
        json.put("alarmType", type);
        json.put("logValue", logValue);
        json.put("tolerance", tolerance);
        return MAPPER.writeValueAsString(json);
    }

    private static TestStore store() throws Exception {
        TestStore store = new TestStore();
        for (JsonObject c : Arrays.asList(OPEN, CHECKED, HIDDEN, BROKEN, BIN, LONE)) {
            store.add(c.getId(), ResourceWebAlarms.ATTRIBUTE_ENABLED, OLD_ALARM, "1", null);
        }
        store.add(DISABLED.getId(), ResourceWebAlarms.ATTRIBUTE_ENABLED, OLD_ALARM, "1", null);
        store.add(DISABLED.getId(), ResourceWebAlarms.ATTRIBUTE_ENABLED, LOG_OLD, "0", null);
        store.add(OPEN.getId(), ResourceWebAlarms.ATTRIBUTE_CHECKED, LOG_OLD, "1", "Checked by demo");
        store.add(OPEN.getId(), ResourceWebAlarms.ATTRIBUTE_CHECKED, LOG1, "0", null);
        store.add(CHECKED.getId(), ResourceWebAlarms.ATTRIBUTE_CHECKED, CHECK_TIME, "true", "Checked by demo");

        store.log(OPEN.getId(), LOG1, "[" + entry(20, "Value", A1, 812.5, ">", 700.0, "L1", 1, 5.0) + ","
                + entry(20, "Value", A2, 650.0, "", 700.0, "L2", 4, null) + "]");
        store.log(OPEN.getId(), LOG2, "[" + entry(20, "Value", OLD_ALARM, 1.0, "<=", 2.0, "D1", 2, null) + "]");
        store.log(OPEN.getId(), LOG_OLD, "[" + entry(20, "Value", OLD_ALARM, 1.0, "<", 2.0, "D2", 0, null) + "]");
        store.log(CHECKED.getId(), LOG2, "[" + entry(20, "Value", A3, 3.0, "!=", 4.0, "GAP", 1, null) + "]");
        store.log(DISABLED.getId(), LOG1, "[" + entry(20, "Value", A1, 1.0, ">", 0.0, "L1", 1, null) + "]");
        store.log(HIDDEN.getId(), LOG1, "[" + entry(20, "Value", A1, 1.0, ">", 0.0, "L1", 1, null) + "]");
        store.log(BIN.getId(), LOG1, "[" + entry(20, "Value", A1, 1.0, ">", 0.0, "L1", 1, null) + "]");
        store.log(BROKEN.getId(), LOG1, "not json");
        store.log(BROKEN.getId(), LOG2, "[" + entry(30, "Value", A3, 5.0, ">=", 4.0, "STATIC", 1, null) + "]");
        store.add(BROKEN.getId(), ResourceWebAlarms.ATTRIBUTE_LOG_FILE, LOG2.plusMinutes(1), "missing.json", null);
        store.log(LONE.getId(), LOG2, "[" + entry(999, "Value", A3, 5.0, "~", 4.0, "DYNAMIC", null, null) + "]");
        store.units.put("20/Value", "kWh");
        store.units.put("30/Value", "kWh");
        return store;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> alarms(String state, String lang, TestStore store) throws Exception {
        return ResourceWebAlarms.alarmsJson(lookup(), classes(), store, FROM, TO, state, ZONE, lang);
    }

    private static List<String> ids(Map<String, Object> json) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> alarm : list(json.get("alarms"))) ids.add((String) alarm.get("id"));
        return ids;
    }

    // ---------------------------------------------------------------- tests

    @Test
    public void openAlarmsOfReadableEnabledConfigurationsNewestFirst() throws Exception {
        Map<String, Object> json = alarms("open", "de", store());
        assertEquals("open", json.get("state"));
        assertEquals("2026-11-04T00:00:00.000+01:00", json.get("from"));
        assertEquals(false, json.get("truncated"));
        /* 101 checked, 102 disabled, 103 not readable, 105 in the recycle bin; LOG_OLD outside the time frame,
           but the old alarm of LOG2 is listed; the broken and the missing file of 104 are skipped */
        assertEquals(Arrays.asList(
                "100-" + A2.getMillis() + "-1",
                "100-" + A1.getMillis() + "-0",
                "104-" + A3.getMillis() + "-0",
                "110-" + A3.getMillis() + "-0",
                "100-" + OLD_ALARM.getMillis() + "-0"), ids(json));

        Map<String, Object> first = list(json.get("alarms")).get(1);
        assertEquals("2026-11-10T14:15:00.000+01:00", first.get("timestamp"));
        assertEquals("2026-11-10T23:43:00.000+01:00", first.get("logged"));
        Map<String, Object> config = map(first.get("configuration"));
        assertEquals(100L, config.get("id"));
        assertEquals("Strom - Gesamt Alarm", config.get("name"));
        assertEquals(false, config.get("checked"));
        assertNull(config.get("checkedBy"));
        assertNull(config.get("checkedAt"));
        Map<String, Object> row = map(first.get("row"));
        assertEquals(20L, row.get("id"));
        assertEquals("Strom - Gesamt", row.get("name"));
        assertEquals("Data", row.get("className"));
        assertEquals("Daten / Strom", row.get("path"));
        assertEquals(true, row.get("readable"));
        assertEquals("Musterwerk", map(first.get("site")).get("name"));
        assertEquals("Value", first.get("attribute"));
        assertEquals(812.5, first.get("isValue"));
        assertEquals(">", first.get("operator"));
        assertEquals(700.0, first.get("shouldBeValue"));
        assertEquals(5.0, first.get("tolerance"));
        assertEquals("kWh", first.get("unit"));
        assertEquals("L1", first.get("type"));
        assertEquals("Grenzwert Stufe 1", first.get("typeName"));
        assertEquals(1, first.get("logValue"));
        assertEquals("Normaler Alarm", first.get("logValueName"));

        Map<String, Object> second = list(json.get("alarms")).get(0);
        assertEquals("≥", second.get("operator"));
        assertNull(second.get("tolerance"));
        assertEquals("Stiller Alarm", second.get("logValueName"));

        /* alarm of a row the user may not read: still listed, without name and unit; site of the row */
        Map<String, Object> hidden = list(json.get("alarms")).get(2);
        assertEquals(false, map(hidden.get("row")).get("readable"));
        assertNull(map(hidden.get("row")).get("name"));
        assertNull(map(hidden.get("row")).get("path"));
        assertEquals("", hidden.get("unit"));
        assertEquals("Musterwerk", map(hidden.get("site")).get("name"));
        assertEquals("≥", hidden.get("operator"));

        /* object that does not exist (any more), configuration without building */
        Map<String, Object> lone = list(json.get("alarms")).get(3);
        assertNull(lone.get("row"));
        assertNull(lone.get("site"));
        assertEquals("", lone.get("unit"));
        assertEquals("~", lone.get("operator"));
        assertNull(lone.get("logValue"));
        assertEquals("Systemalarm", lone.get("logValueName"));
        assertEquals("Dynamisch", lone.get("typeName"));

        Map<String, Object> old = list(json.get("alarms")).get(4);
        assertEquals("≤", old.get("operator"));
        assertEquals("Ignorierter Alarm", old.get("logValueName"));

        MAPPER.writeValueAsString(json);
    }

    @Test
    public void checkedAndAllStates() throws Exception {
        Map<String, Object> checked = alarms("checked", "en", store());
        assertEquals(Collections.singletonList("101-" + A3.getMillis() + "-0"), ids(checked));
        Map<String, Object> alarm = list(checked.get("alarms")).get(0);
        Map<String, Object> config = map(alarm.get("configuration"));
        assertEquals(true, config.get("checked"));
        assertEquals("demo", config.get("checkedBy"));
        assertEquals("2026-11-10T08:00:00.000+01:00", config.get("checkedAt"));
        assertEquals("≠", alarm.get("operator"));
        assertEquals("Gap", alarm.get("typeName"));
        assertEquals("Standard Alarm", alarm.get("logValueName"));

        assertEquals(6, ids(alarms("all", "en", store())).size());
    }

    @Test
    public void checkedNoteWithoutPrefixIsKept() {
        TestStore store = new TestStore();
        store.add(7, ResourceWebAlarms.ATTRIBUTE_CHECKED, CHECK_TIME, "1", "per e-mail");
        assertEquals("per e-mail", ResourceWebAlarms.checked(7, store).by);
        store.add(8, ResourceWebAlarms.ATTRIBUTE_CHECKED, CHECK_TIME, "1", null);
        assertTrue(ResourceWebAlarms.checked(8, store).checked);
        assertNull(ResourceWebAlarms.checked(8, store).by);
        assertFalse(ResourceWebAlarms.checked(9, store).checked);
    }

    @Test
    public void readsEachConfigurationOnce() throws Exception {
        TestStore store = store();
        alarms("all", "de", store);
        assertEquals(1, Collections.frequency(store.calls, "latest 100/" + ResourceWebAlarms.ATTRIBUTE_CHECKED));
        assertEquals(1, Collections.frequency(store.calls, "samples 100/" + ResourceWebAlarms.ATTRIBUTE_LOG_FILE));
        assertFalse(store.calls.contains("samples 102/" + ResourceWebAlarms.ATTRIBUTE_LOG_FILE));
        assertFalse(store.calls.contains("samples 103/" + ResourceWebAlarms.ATTRIBUTE_LOG_FILE));
        assertFalse(store.calls.contains("latest 103/" + ResourceWebAlarms.ATTRIBUTE_ENABLED));
        assertEquals(1, Collections.frequency(store.calls, "unit 20/Value"));
        assertFalse(store.calls.contains("unit 30/Value"));
    }

    @Test
    public void truncatesToTheNewestAlarms() throws Exception {
        TestStore store = new TestStore();
        store.add(100, ResourceWebAlarms.ATTRIBUTE_ENABLED, OLD_ALARM, "1", null);
        StringBuilder content = new StringBuilder("[");
        DateTime start = new DateTime(2026, 11, 5, 0, 0, ZONE);
        for (int i = 0; i < ResourceWebAlarms.MAX_ALARMS + 1; i++) {
            if (i > 0) content.append(',');
            content.append(entry(20, "Value", start.plusMinutes(i), (double) i, ">", 0.0, "L1", 1, null));
        }
        store.log(100, LOG1, content.append(']').toString());
        Map<String, Object> json = alarms("open", "en", store);
        assertEquals(true, json.get("truncated"));
        List<Map<String, Object>> alarms = list(json.get("alarms"));
        assertEquals(ResourceWebAlarms.MAX_ALARMS, alarms.size());
        assertEquals((double) ResourceWebAlarms.MAX_ALARMS, alarms.get(0).get("isValue"));
        assertEquals(1.0, alarms.get(alarms.size() - 1).get("isValue"));
    }

    @Test
    public void operatorLikeTheControlCenter() {
        assertEquals(">", ResourceWebAlarms.operator(">"));
        assertEquals("≥", ResourceWebAlarms.operator(">="));
        assertEquals("≥", ResourceWebAlarms.operator(""));
        assertEquals("≥", ResourceWebAlarms.operator("≥"));
        assertEquals("=", ResourceWebAlarms.operator("="));
        assertEquals("≠", ResourceWebAlarms.operator("!="));
        assertEquals("<", ResourceWebAlarms.operator("<"));
        assertEquals("≤", ResourceWebAlarms.operator("<="));
        assertEquals("between", ResourceWebAlarms.operator("between"));
        assertNull(ResourceWebAlarms.operator(null));
    }

    @Test
    public void translations() {
        assertEquals("Grenzwert Stufe 2", ResourceWebAlarms.typeName(AlarmType.L2, "de"));
        assertEquals("Limit 2", ResourceWebAlarms.typeName(AlarmType.L2, "en"));
        assertEquals("Limit 2", ResourceWebAlarms.typeName(AlarmType.L2, "fr"));
        assertEquals("Delta Stufe 2", ResourceWebAlarms.typeName(AlarmType.D2, "de"));
        assertEquals("Statisch", ResourceWebAlarms.typeName(AlarmType.STATIC, "de"));
        assertEquals("Lücke", ResourceWebAlarms.typeName(AlarmType.GAP, "de"));
        assertEquals("Keiner", ResourceWebAlarms.typeName(AlarmType.NONE, "de"));
        assertEquals("None", ResourceWebAlarms.typeName(AlarmType.NONE, "en"));
        assertEquals("Silent Alarm", ResourceWebAlarms.logValueName(4, "en"));
        assertEquals("Ignored Alarm", ResourceWebAlarms.logValueName(2, "en"));
        assertEquals("Standard Alarm", ResourceWebAlarms.logValueName(1, "en"));
        assertEquals("System Alarm", ResourceWebAlarms.logValueName(0, "en"));
        assertEquals("System Alarm", ResourceWebAlarms.logValueName(7, "en"));
        assertEquals("Systemalarm", ResourceWebAlarms.logValueName(null, "de"));
    }

    @Test
    public void dayRangeAndState() {
        LocalDate today = new LocalDate(2026, 11, 11);
        DateTime[] range = ResourceWebAlarms.dayRange(null, null, ZONE, today);
        assertEquals(new DateTime(2026, 11, 4, 0, 0, ZONE), range[0]);
        assertEquals(new DateTime(2026, 11, 12, 0, 0, ZONE).minusMillis(1), range[1]);
        range = ResourceWebAlarms.dayRange("2025-11-11", "2026-11-11", ZONE, today);
        assertEquals(new DateTime(2025, 11, 11, 0, 0, ZONE), range[0]);
        for (String[] invalid : new String[][]{{"2025-11-10", "2026-11-11"}, {"2026-11-11", "2026-11-10"},
                {"x", null}, {null, "2026-13-01"}}) {
            try {
                ResourceWebAlarms.dayRange(invalid[0], invalid[1], ZONE, today);
                fail("Expected an error for " + Arrays.toString(invalid));
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        assertEquals("open", ResourceWebAlarms.state(null));
        assertEquals("open", ResourceWebAlarms.state(" "));
        assertEquals("checked", ResourceWebAlarms.state("Checked"));
        assertEquals("all", ResourceWebAlarms.state("all"));
        try {
            ResourceWebAlarms.state("closed");
            fail("Expected an error");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void endpointAnswers400ForInvalidParameters() {
        ResourceWebAlarms resource = new ResourceWebAlarms();
        assertEquals(400, resource.alarms(null, "2026-11-11", "2026-11-01", null, null, null).getStatus());
        assertEquals(400, resource.alarms(null, "2024-01-01", "2026-01-01", null, null, null).getStatus());
        assertEquals(400, resource.alarms(null, null, null, "closed", null, null).getStatus());
    }

    @Test
    public void navigationCounts() throws Exception {
        TestStore store = store();
        DateTime now = new DateTime(2026, 11, 11, 12, 0, ZONE);
        Map<String, Object> counts = ResourceWebAlarms.moduleCounts(lookup(), store, now);
        /* enabled and readable: 100, 101, 104, 110 */
        assertEquals(4, counts.get("alarms"));
        /* open within 7 days: 100 (2 + 1), 104 (broken skipped + 1), 110 (1) */
        assertEquals(5, counts.get("alarmsOpen"));
        assertFalse(store.calls.contains("samples 101/" + ResourceWebAlarms.ATTRIBUTE_LOG_FILE));

        Map<String, Object> modules = ResourceWebModules.moduleCounts(lookup(), store, now);
        assertEquals(4, modules.get("alarms"));
        assertEquals(5, modules.get("alarmsOpen"));
        assertTrue(modules.containsKey("notes"));
    }
}
