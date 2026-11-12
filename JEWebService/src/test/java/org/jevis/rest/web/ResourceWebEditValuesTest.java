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
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Test;

import java.time.LocalTime;
import java.util.Arrays;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.jevis.rest.web.ResourceWebEditValues.Mode;
import static org.junit.Assert.*;

/**
 * Manual values (stage E1).
 */
public class ResourceWebEditValuesTest {

    private static final String LIMITS = "[{\"name\":\"Step 1\",\"min\":\"0\",\"max\":\"50\"},{\"min\":\"-5\",\"max\":\"500\"}]";
    private static final DateTime NOW = new DateTime(2026, 11, 12, 10, 0, ZONE);
    private static final DateTime D10 = new DateTime(2026, 11, 10, 0, 0, ZONE);
    private static final DateTime D11 = new DateTime(2026, 11, 11, 0, 0, ZONE);

    /* folder 3 > counter row 20 (P1D) > clean data 25 (counter, limits) > dependent 26;
       folder 3 > monthly row 30 > clean data 35 (limits 0..10); folder 3 > base data 60 (counter);
       dependents 70 (no delete right) and 80 (hidden) */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        store.add(obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(3, "Data", "Data Directory", 1),
                obj(20, "Meter", "Data", 3),
                obj(25, "Meter clean", "Clean Data", 20),
                obj(26, "Meter clean 2", "Clean Data", 25),
                obj(30, "Consumption", "Data", 3),
                obj(35, "Consumption clean", "Clean Data", 30),
                obj(60, "Base", ResourceWebEditValues.CLASS_BASE_DATA, 3),
                obj(70, "Calc output", "Data", 3),
                obj(80, "Hidden output", "Data", 3));
        store.hidden.add(80L);
        store.type("Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE, "Period", JEVisConstants.PrimitiveType.STRING);
        store.type("Clean Data", "Data", "Value", JEVisConstants.PrimitiveType.DOUBLE);
        store.type(ResourceWebEditValues.CLASS_BASE_DATA, "Data", "Value", JEVisConstants.PrimitiveType.DOUBLE);
        DateTime old = new DateTime(2026, 1, 1, 0, 0, ZONE);
        store.sample(20, "Period", old, "P1D", null);
        store.sample(25, ResourceWebEditValues.ATTRIBUTE_DIFFERENTIAL, old, "true", null);
        store.sample(25, ResourceWebEditValues.ATTRIBUTE_LIMITS, old, LIMITS, null);
        store.sample(20, "Value", D10, "1000", "import");
        store.sample(30, "Period", old, "P1M", null);
        store.sample(35, ResourceWebEditValues.ATTRIBUTE_DIFFERENTIAL, old, "false", null);
        store.sample(35, ResourceWebEditValues.ATTRIBUTE_LIMITS, old, "[{\"min\":\"0\",\"max\":\"10\"}]", null);
        store.sample(60, ResourceWebEditValues.ATTRIBUTE_DIFFERENTIAL, old, "true", null);
        return store;
    }

    private static String body(String timestamp, String value, Boolean confirm) {
        return "{\"timestamp\":\"" + timestamp + "\",\"value\":" + value + (confirm != null ? ",\"confirm\":" + confirm : "") + "}";
    }

    // ---------------------------------------------------------------- GET entry

    @Test
    public void entryOfACounter() throws Exception {
        FakeEditStore store = store();
        store.write.add(20L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditValues.entry(s, 20));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("Meter", json.get("name").asText());
        assertEquals("kWh", json.get("unit").asText());
        assertTrue(json.get("canEnter").asBoolean());
        assertFalse(json.get("canDelete").asBoolean());
        assertTrue(json.get("counter").asBoolean());
        assertEquals("P1D", json.get("period").asText());
        assertEquals("DAY", json.get("mode").asText());
        assertEquals("Europe/Berlin", json.get("timeZone").asText());
        assertEquals("2026-11-11T00:00:00.000+01:00", json.get("suggestedTimestamp").asText());
        assertEquals("2026-11-10T00:00:00.000+01:00", json.get("last").get("timestamp").asText());
        assertEquals(1000.0, json.get("last").get("value").asDouble(), 0);
        assertEquals(0.0, json.get("limits").get("min").asDouble(), 0);
        assertEquals(50.0, json.get("limits").get("max").asDouble(), 0);
    }

    @Test
    public void entryOfOtherRows() throws Exception {
        FakeEditStore store = store();
        JsonNode json = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditValues.entry(s, 30)));
        assertEquals("MONTH", json.get("mode").asText());
        assertFalse(json.get("counter").asBoolean());
        assertFalse(json.get("canEnter").asBoolean());
        assertTrue(json.get("last").isNull());
        assertTrue(json.get("suggestedTimestamp").isNull());

        json = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebEditValues.entry(s, 60)));
        assertTrue("base data: own conversion to differential", json.get("counter").asBoolean());
        assertEquals("DAY", json.get("mode").asText());
        assertTrue(json.get("limits").isNull());

        for (long id : new long[]{25, 3, 80, 999}) {
            assertEquals("id " + id, 404, WebEdit.call(store, s -> ResourceWebEditValues.entry(s, id)).status);
        }
    }

    @Test
    public void modeFromPeriod() {
        assertEquals(Mode.DAY, ResourceWebEditValues.mode(false, Period.days(1)));
        assertEquals(Mode.DAY, ResourceWebEditValues.mode(false, Period.weeks(1)));
        assertEquals(Mode.MONTH, ResourceWebEditValues.mode(false, Period.months(1)));
        assertEquals(Mode.YEAR, ResourceWebEditValues.mode(false, Period.years(1)));
        assertEquals(Mode.SPECIFIC_DATETIME, ResourceWebEditValues.mode(false, Period.minutes(15)));
        assertEquals(Mode.SPECIFIC_DATETIME, ResourceWebEditValues.mode(false, null));
        assertEquals(Mode.DAY, ResourceWebEditValues.mode(true, Period.minutes(15)));
        assertNull(ResourceWebEditValues.period("nonsense"));
    }

    @Test
    public void suggestedTimestamp() {
        WebEdit.WorkDay standard = WebEdit.WorkDay.standard(ZONE);
        assertEquals(D11.getMillis(), ResourceWebEditValues.suggested(D10, Period.days(1), standard).getMillis());
        DateTime october = new DateTime(2026, 10, 1, 0, 0, ZONE);
        assertEquals(new DateTime(2026, 11, 1, 0, 0, ZONE).getMillis(),
                ResourceWebEditValues.suggested(october, Period.months(1), standard).getMillis());
        assertEquals(D10.getMillis(), ResourceWebEditValues.suggested(D10, null, standard).getMillis());
    }

    // ---------------------------------------------------------------- alignment

    @Test
    public void alignByMode() throws Exception {
        WebEdit.WorkDay standard = WebEdit.WorkDay.standard(ZONE);
        DateTime input = new DateTime(2026, 11, 12, 13, 45, 30, 250, ZONE);
        assertEquals(new DateTime(2026, 1, 1, 0, 0, ZONE), ResourceWebEditValues.align(input, Mode.YEAR, standard));
        assertEquals(new DateTime(2026, 11, 1, 0, 0, ZONE), ResourceWebEditValues.align(input, Mode.MONTH, standard));
        assertEquals(new DateTime(2026, 11, 12, 0, 0, ZONE), ResourceWebEditValues.align(input, Mode.DAY, standard));
        assertEquals(new DateTime(2026, 11, 12, 13, 45, 30, 0, ZONE), ResourceWebEditValues.align(input, Mode.SPECIFIC_DATETIME, standard));
        /* an instant given with another offset is read in the time zone of the site */
        DateTime utc = new DateTime(2026, 11, 11, 23, 30, org.joda.time.DateTimeZone.UTC);
        assertEquals(new DateTime(2026, 11, 12, 0, 0, ZONE), ResourceWebEditValues.align(utc, Mode.DAY, standard));
    }

    @Test
    public void alignWithCustomWorkday() throws Exception {
        WebEdit.WorkDay overnight = new WebEdit.WorkDay(ZONE, LocalTime.of(6, 0), LocalTime.of(5, 59, 59));
        DateTime input = new DateTime(2026, 11, 12, 0, 0, ZONE);
        assertEquals(new DateTime(2026, 11, 11, 6, 0, ZONE), ResourceWebEditValues.align(input, Mode.DAY, overnight));
        assertEquals(new DateTime(2026, 10, 31, 6, 0, ZONE), ResourceWebEditValues.align(input, Mode.MONTH, overnight));
        assertEquals(new DateTime(2025, 12, 31, 6, 0, ZONE), ResourceWebEditValues.align(input, Mode.YEAR, overnight));

        WebEdit.WorkDay late = new WebEdit.WorkDay(ZONE, LocalTime.of(6, 0), LocalTime.of(22, 0));
        assertEquals(new DateTime(2026, 11, 12, 6, 0, ZONE), ResourceWebEditValues.align(input, Mode.DAY, late));
        assertEquals(new DateTime(2026, 11, 1, 0, 0, ZONE), ResourceWebEditValues.align(input, Mode.MONTH, late));
    }

    // ---------------------------------------------------------------- POST value

    @Test
    public void enterWritesAndResetsDependents() throws Exception {
        FakeEditStore store = store();
        store.write.add(20L);
        store.delete.add(26L);
        store.delete.add(80L);
        store.dependents.addAll(Arrays.asList(26L, 70L, 80L));
        store.sample(26, "Value", D10, "1", null);
        store.sample(26, "Value", D11, "2", null);
        store.sample(26, "Value", D11.plusDays(1), "3", null);
        store.sample(70, "Value", D11, "5", null);

        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 20, body("2026-11-11", "1020.5", null), "de", NOW));
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("2026-11-11T00:00:00.000+01:00", json.get("timestamp").asText());
        assertEquals(1020.5, json.get("value").asDouble(), 0);
        assertEquals(0, json.get("warnings").size());

        JsonSample written = store.at(20, "Value", D11);
        assertEquals("1020.5", written.getValue());
        assertEquals(ResourceWebEditValues.manualText("de") + " " + NOW, written.getNote());
        assertTrue(written.getNote().startsWith("Manuelle Dateneingabe "));
        assertEquals("CREATE_SAMPLE 20:Value|1|web:manual-value", store.logs.get(0));

        /* 26 reset from the time on, 70 not allowed, 80 not readable */
        assertEquals(1, store.count(26, "Value"));
        assertNotNull(store.at(26, "Value", D10));
        assertEquals(1, store.count(70, "Value"));
        assertEquals(1, store.deletes.size());
        assertEquals("26/Value " + D11.getMillis() + "-" + D11.plusDays(1).getMillis(), store.deletes.get(0));
        assertTrue(store.logs.get(1).startsWith("DELETE_SAMPLE 26:Value|"));
        assertTrue(store.logs.get(1).endsWith("|web:manual-value-reset"));
    }

    @Test
    public void counterLowerThanLastReading() throws Exception {
        FakeEditStore store = store();
        store.write.add(20L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 20, body("2026-11-11", "990", false), "en", NOW));
        assertEquals(409, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals(1, json.get("warnings").size());
        assertEquals(ResourceWebEditValues.text(ResourceWebEditValues.KEY_COUNTER_LOWER, "en"), json.get("warnings").get(0).asText());
        assertEquals("2026-11-11T00:00:00.000+01:00", json.get("timestamp").asText());
        assertTrue(store.writes.isEmpty());

        reply = WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 20, body("2026-11-11", "990", true), "en", NOW));
        assertEquals(201, reply.status);
        assertEquals(1, FakeEditStore.json(reply).get("warnings").size());
        assertEquals("990", store.at(20, "Value", D11).getValue());
    }

    @Test
    public void counterDifferenceOutsideTheLimits() throws Exception {
        FakeEditStore store = store();
        store.write.add(20L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 20, body("2026-11-11", "1100", null), "en", NOW));
        assertEquals(409, reply.status);
        String warning = FakeEditStore.json(reply).get("warnings").get(0).asText();
        assertEquals(ResourceWebEditValues.text(ResourceWebEditValues.KEY_DIFF_BIGGER, "en") + " 100 > 50", warning);

        /* within the limits */
        assertEquals(201, WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 20, body("2026-11-11", "1030", null), "en", NOW)).status);
    }

    @Test
    public void valueOutsideTheLimits() throws Exception {
        FakeEditStore store = store();
        store.write.add(30L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 30, body("2026-11-20T08:00:00", "20", null), "en", NOW));
        assertEquals(409, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals(ResourceWebEditValues.text(ResourceWebEditValues.KEY_VALUE_BIGGER, "en") + " 20 > 10", json.get("warnings").get(0).asText());
        assertEquals("monthly: first day of the month", "2026-11-01T00:00:00.000+01:00", json.get("timestamp").asText());

        reply = WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 30, body("2026-11-20", "-1", null), "en", NOW));
        assertEquals(ResourceWebEditValues.text(ResourceWebEditValues.KEY_VALUE_SMALLER, "en") + " -1 < 0",
                FakeEditStore.json(reply).get("warnings").get(0).asText());
        assertEquals(201, WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 30, body("2026-11-20", "7.25", null), "en", NOW)).status);
        assertEquals("7.25", store.at(30, "Value", new DateTime(2026, 11, 1, 0, 0, ZONE)).getValue());
    }

    @Test
    public void existingSampleNeedsConfirm() throws Exception {
        FakeEditStore store = store();
        store.write.add(20L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 20, body("2026-11-10", "1000", null), "en", NOW));
        assertEquals(409, reply.status);
        JsonNode warnings = FakeEditStore.json(reply).get("warnings");
        assertEquals(1, warnings.size());
        assertEquals(ResourceWebEditValues.text(ResourceWebEditValues.KEY_EXISTS, "en") + " 1000", warnings.get(0).asText());
        assertEquals("import", store.at(20, "Value", D10).getNote());

        assertEquals(201, WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 20, body("2026-11-10", "1000", true), "en", NOW)).status);
        assertTrue(store.at(20, "Value", D10).getNote().startsWith(ResourceWebEditValues.manualText("en")));
    }

    @Test
    public void enterRightsAndValidation() throws Exception {
        FakeEditStore store = store();
        store.execute.add(20L);
        store.write.add(25L);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 20, body("2026-11-11", "1020", null), "en", NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 25, body("2026-11-11", "1020", null), "en", NOW)).status);
        store.write.add(20L);
        for (String bad : new String[]{"{\"timestamp\":\"2026-11-11\"}", body("2026-11-11", "\"12\"", null),
                body("x", "12", null), "{\"value\":12}", body("2026-11-11", "12", null).replace("}", ",\"confirm\":\"yes\"}")}) {
            assertEquals(bad, 400, WebEdit.call(store, s -> ResourceWebEditValues.enter(s, 20, bad, "en", NOW)).status);
        }
        assertTrue(store.writes.isEmpty());
    }

    // ---------------------------------------------------------------- DELETE value

    @Test
    public void deleteOnlyManualValues() throws Exception {
        FakeEditStore store = store();
        store.sample(20, "Value", D11, "1010", "Entry of values 2026-11-11T09:00:00.000+01:00");
        store.sample(20, "Value", D11.plusDays(1), "1015", "Manuelle Dateneingabe 2026-11-12T09:00:00.000+01:00");
        String ts = WebEdit.iso(D11.getMillis(), ZONE);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditValues.deleteValue(s, 20, ts)).status);
        store.write.add(20L);
        assertEquals("write right is not enough", 403, WebEdit.call(store, s -> ResourceWebEditValues.deleteValue(s, 20, ts)).status);
        store.delete.add(20L);
        store.delete.add(26L);
        store.dependents.add(26L);
        store.sample(26, "Value", D11, "2", null);

        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditValues.deleteValue(s, 20, WebEdit.iso(D10.getMillis(), ZONE))).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditValues.deleteValue(s, 20, "2026-11-01T00:00:00")).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditValues.deleteValue(s, 20, null)).status);
        assertEquals(1000 + "", store.at(20, "Value", D10).getValue());

        assertEquals(204, WebEdit.call(store, s -> ResourceWebEditValues.deleteValue(s, 20, ts)).status);
        assertNull(store.at(20, "Value", D11));
        assertEquals("DELETE_SAMPLE 20:Value|" + D11 + " -> " + D11 + "|web:manual-value-delete", store.logs.get(0));
        assertEquals(0, store.count(26, "Value"));
        assertEquals(204, WebEdit.call(store, s -> ResourceWebEditValues.deleteValue(s, 20, "2026-11-12")).status);
    }

    @Test
    public void canEnterOnlyRawRowsWithWriteRight() throws Exception {
        FakeEditStore store = store();
        store.write.addAll(Arrays.asList(20L, 25L, 60L));
        assertTrue(WebEdit.canEnter(store.lookup().readable(20L), store.rights()));
        assertTrue(WebEdit.canEnter(store.lookup().readable(60L), store.rights()));
        assertFalse("clean data", WebEdit.canEnter(store.lookup().readable(25L), store.rights()));
        assertFalse("no write right", WebEdit.canEnter(store.lookup().readable(30L), store.rights()));
        assertFalse(WebEdit.canEnter(null, store.rights()));
    }

    @Test
    public void registerListsGetCanEnter() throws Exception {
        FakeEditStore store = store();
        store.write.addAll(Arrays.asList(20L, 60L));
        java.util.Map<String, Object> meterToValue = new java.util.LinkedHashMap<>();
        meterToValue.put("id", 500L);
        java.util.Map<String, Object> target = new java.util.LinkedHashMap<>();
        target.put("id", 20L);
        target.put("attribute", "Value");
        meterToValue.put("target", target);
        java.util.Map<String, Object> meterToOther = new java.util.LinkedHashMap<>();
        meterToOther.put("id", 501L);
        java.util.Map<String, Object> other = new java.util.LinkedHashMap<>(target);
        other.put("attribute", "Period");
        meterToOther.put("target", other);
        java.util.Map<String, Object> meterWithout = new java.util.LinkedHashMap<>();
        meterWithout.put("id", 502L);
        meterWithout.put("target", null);
        java.util.Map<String, Object> group = new java.util.LinkedHashMap<>();
        group.put("rows", new java.util.ArrayList<>(Arrays.asList(meterToValue, meterToOther, meterWithout)));
        ResourceWebRegisters.addCanEnter(ResourceWebRegisters.KIND_METERS, java.util.Collections.singletonList(group),
                store.lookup(), store.rights());
        assertEquals(Boolean.TRUE, meterToValue.get("canEnter"));
        assertEquals(Boolean.FALSE, meterToOther.get("canEnter"));
        assertEquals(Boolean.FALSE, meterWithout.get("canEnter"));

        java.util.Map<String, Object> base = new java.util.LinkedHashMap<>();
        base.put("id", 60L);
        java.util.Map<String, Object> baseGroup = new java.util.LinkedHashMap<>();
        baseGroup.put("rows", new java.util.ArrayList<>(java.util.Collections.singletonList(base)));
        ResourceWebRegisters.addCanEnter(ResourceWebRegisters.KIND_BASEDATA, java.util.Collections.singletonList(baseGroup),
                store.lookup(), store.rights());
        assertEquals(Boolean.TRUE, base.get("canEnter"));
    }

    @Test
    public void historyMarksManualSamples() throws Exception {
        FakeEditStore store = store();
        JsonSample manual = new JsonSample();
        manual.setTs(FakeEditStore.ts(D11));
        manual.setValue("5");
        manual.setNote("Entry of values 2026-11-11T09:00:00.000+01:00");
        JsonSample imported = new JsonSample();
        imported.setTs(FakeEditStore.ts(D10));
        imported.setValue("4");
        imported.setNote("import");
        java.util.List<JsonSample> samples = Arrays.asList(manual, imported);

        JsonNode json = FakeEditStore.MAPPER.readTree(FakeEditStore.MAPPER.writeValueAsString(
                ResourceWebRegisters.historyJson("kWh", samples, store.lookup().readable(60L), "Value", store.rights())));
        assertEquals(60, json.get("rowId").asLong());
        assertTrue(json.get("samples").get(0).get("manual").asBoolean());
        assertFalse("no delete right", json.get("samples").get(0).get("canDelete").asBoolean());
        assertFalse(json.get("samples").get(1).get("manual").asBoolean());

        store.delete.add(60L);
        json = FakeEditStore.MAPPER.readTree(FakeEditStore.MAPPER.writeValueAsString(
                ResourceWebRegisters.historyJson("kWh", samples, store.lookup().readable(60L), "Value", store.rights())));
        assertTrue(json.get("samples").get(0).get("canDelete").asBoolean());
        assertFalse(json.get("samples").get(1).get("canDelete").asBoolean());
    }

    @Test
    public void manualTextInAnyLanguage() {
        assertTrue(ResourceWebEditValues.isManual("Manuelle Dateneingabe 2026-11-12T09:00:00.000+01:00"));
        assertTrue(ResourceWebEditValues.isManual(ResourceWebEditValues.manualText("ru") + " 2026"));
        assertFalse(ResourceWebEditValues.isManual("import"));
        assertFalse(ResourceWebEditValues.isManual(null));
    }
}
