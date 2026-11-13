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
import org.jevis.commons.ws.json.JsonType;
import org.joda.time.DateTime;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

/**
 * Meter reading (stage E8.1).
 */
public class ResourceWebReadingsTest {

    private static final DateTime NOW = new DateTime(2026, 11, 13, 10, 30, 15, ZONE);
    private static final DateTime D8 = new DateTime(2026, 11, 8, 0, 0, ZONE);
    private static final DateTime D9 = new DateTime(2026, 11, 9, 0, 0, ZONE);
    private static final DateTime D10 = new DateTime(2026, 11, 10, 0, 0, ZONE);
    private static final DateTime D11 = new DateTime(2026, 11, 11, 0, 0, ZONE);
    private static final String MI = "Measurement Instrument";
    private static final String EMI = "Electricity Measurement Instrument";
    private static final String GMI = "Gas Measurement Instrument";
    private static final String MANUAL_NOTE = "Manuelle Dateneingabe 2026-11-10T08:00:00.000+01:00";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final byte[] HEIC = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};

    /* organisation 1 > building 2 "Musterwerk" > directory 3 > meters 101 (row 201, counter P1D), 102 (no Online
       ID), 103 (row 203 hidden), 104 (row 204 Clean Data), 105 (gas, row 205 monthly, no Reading Photo);
       organisation 1 > building 5 "Zweitwerk" > directory 6 > meter 111 (row 211 without period) */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        store.add(obj(1, "Musterfirma", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(2, "Musterwerk", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(3, "Messstellen", ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY, 2),
                obj(101, "Hauptzähler Strom", EMI, 3),
                obj(102, "Zähler ohne Ziel", EMI, 3),
                obj(103, "Zähler verborgen", EMI, 3),
                obj(104, "Zähler berechnet", EMI, 3),
                obj(105, "Gaszähler", GMI, 3),
                obj(10, "Daten", "Data Directory", 2),
                obj(201, "Strom - Hauptzähler", "Data", 10),
                obj(202, "Strom - Hauptzähler bereinigt", "Clean Data", 201),
                obj(203, "Verborgen", "Data", 10),
                obj(204, "Berechnet", "Clean Data", 201),
                obj(205, "Gas", "Data", 10),
                obj(206, "Gas bereinigt", "Clean Data", 205),
                obj(5, "Zweitwerk", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(6, "Messstellen", ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY, 5),
                obj(111, "Wärmezähler", EMI, 6),
                obj(211, "Wärme", "Data", 6));
        store.hidden.add(203L);
        store.type(MI, null, meterTypes(true));
        store.type(EMI, MI, meterTypes(true));
        store.type(GMI, MI, meterTypes(false));
        store.type("Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE, "Period", JEVisConstants.PrimitiveType.STRING);
        store.type("Clean Data", "Data", "Value", JEVisConstants.PrimitiveType.DOUBLE);
        DateTime old = new DateTime(2026, 1, 1, 0, 0, ZONE);
        store.sample(101, "Online ID", old, "201:Value", null);
        store.sample(101, "Serial Number", old, "A-123", null);
        store.sample(101, "Meter Point", old, "DE0001", null);
        store.sample(101, "Location", old, "Keller, Raum 0.12", null);
        store.sample(101, "Decimal Places", old, "1", null);
        store.sample(103, "Online ID", old, "203", null);
        store.sample(104, "Online ID", old, "204:Value", null);
        store.sample(105, "Online ID", old, "205", null);
        store.sample(111, "Online ID", old, "211", null);
        store.sample(201, "Period", old, "P1D", null);
        store.sample(202, ResourceWebEditValues.ATTRIBUTE_DIFFERENTIAL, old, "true", null);
        store.sample(201, "Value", D8, "100", "import");
        store.sample(201, "Value", D9, "110", "import");
        store.sample(201, "Value", D10, "120", MANUAL_NOTE);
        store.file(101, ResourceWebReadings.ATTRIBUTE_READING_PHOTO, D10, "Ablesung_20261110_0000.jpg", "photo");
        store.sample(205, "Period", old, "P1M", null);
        store.sample(206, ResourceWebEditValues.ATTRIBUTE_DIFFERENTIAL, old, "false", null);
        store.sample(205, "Value", new DateTime(2026, 9, 1, 0, 0, ZONE), "40", null);
        store.sample(205, "Value", new DateTime(2026, 10, 1, 0, 0, ZONE), "60", null);
        store.sample(211, "Value", D10, "5", null);
        store.write.add(201L);
        store.write.add(101L);
        return store;
    }

    private static Object[] meterTypes(boolean photo) {
        Object[] types = {"Online ID", JEVisConstants.PrimitiveType.STRING, "Serial Number", JEVisConstants.PrimitiveType.STRING,
                "Meter Point", JEVisConstants.PrimitiveType.STRING, "Location", JEVisConstants.PrimitiveType.STRING,
                "Decimal Places", JEVisConstants.PrimitiveType.LONG, "Picture", JEVisConstants.PrimitiveType.FILE};
        if (!photo) return types;
        Object[] withPhoto = new Object[types.length + 2];
        System.arraycopy(types, 0, withPhoto, 0, types.length);
        withPhoto[types.length] = ResourceWebReadings.ATTRIBUTE_READING_PHOTO;
        withPhoto[types.length + 1] = JEVisConstants.PrimitiveType.FILE;
        return withPhoto;
    }

    private static ResourceWebRegisters.Translations translations(FakeEditStore store) {
        return new ResourceWebRegisters.Translations(Collections.emptyList(), "de", store.classes());
    }

    private static JsonNode list(FakeEditStore store, Long site) throws Exception {
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebReadings.list(s, translations(store), site, NOW));
        assertEquals(200, reply.status);
        return FakeEditStore.json(reply);
    }

    private static JsonNode meter(JsonNode json, long id) {
        for (JsonNode group : json.get("groups")) {
            for (JsonNode meter : group.get("meters")) {
                if (meter.get("id").asLong() == id) return meter;
            }
        }
        return null;
    }

    private static WebEdit.Reply enter(FakeEditStore store, long meterId, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebReadings.enter(s, meterId, body, "de", NOW));
    }

    private static String body(String timestamp, String value, String note, Boolean confirm) {
        return "{\"timestamp\":\"" + timestamp + "\",\"value\":" + value
                + (note != null ? ",\"note\":" + note : "")
                + (confirm != null ? ",\"confirm\":" + confirm : "") + "}";
    }

    // ---------------------------------------------------------------- GET

    @Test
    public void listGroupsMetersByDirectory() throws Exception {
        JsonNode json = list(store(), null);
        assertEquals("2026-11-13", json.get("today").asText());
        JsonNode groups = json.get("groups");
        assertEquals(2, groups.size());
        assertEquals("Musterwerk", groups.get(0).get("name").asText());
        assertEquals("3", groups.get(0).get("key").asText());
        assertEquals(2, groups.get(0).get("site").get("id").asLong());
        assertEquals("Zweitwerk", groups.get(1).get("name").asText());
        JsonNode names = groups.get(0).get("meters");
        assertEquals(5, names.size());
        assertEquals("Gaszähler", names.get(0).get("name").asText());
        assertEquals("Hauptzähler Strom", names.get(1).get("name").asText());

        JsonNode site = list(store(), 5L).get("groups");
        assertEquals(1, site.size());
        assertEquals("Zweitwerk", site.get(0).get("name").asText());
        assertEquals(0, list(store(), 999L).get("groups").size());
    }

    @Test
    public void listMeterWithRow() throws Exception {
        JsonNode meter = meter(list(store(), null), 101);
        assertNotNull(meter);
        assertEquals(EMI, meter.get("className").asText());
        assertEquals(EMI, meter.get("classLabel").asText());
        assertEquals("Musterwerk / Messstellen", meter.get("path").asText());
        assertEquals("A-123", meter.get("serialNumber").asText());
        assertEquals("DE0001", meter.get("meterPoint").asText());
        assertEquals("Keller, Raum 0.12", meter.get("location").asText());
        assertTrue(meter.get("canPhoto").asBoolean());
        assertTrue(meter.get("canExchange").asBoolean());
        assertTrue(meter.get("problem").isNull());

        JsonNode row = meter.get("row");
        assertEquals(201, row.get("id").asLong());
        assertEquals("kWh", row.get("unit").asText());
        assertTrue(row.get("counter").asBoolean());
        assertEquals("P1D", row.get("period").asText());
        assertEquals("DAY", row.get("mode").asText());
        assertEquals("Europe/Berlin", row.get("timeZone").asText());
        assertTrue(row.get("limits").isNull());
        assertTrue(row.get("canEnter").asBoolean());
        assertFalse(row.get("canDelete").asBoolean());
        assertEquals(1, row.get("decimals").asInt());

        assertEquals("OVERDUE", meter.get("due").asText());
        assertEquals("2026-11-11T00:00:00.000+01:00", meter.get("suggestedTimestamp").asText());
        /* 10 per day, spread at least 20 % */
        assertEquals(130.0, meter.get("expected").get("value").asDouble(), 1e-6);
        assertEquals(128.0, meter.get("expected").get("low").asDouble(), 1e-6);
        assertEquals(132.0, meter.get("expected").get("high").asDouble(), 1e-6);

        JsonNode readings = meter.get("readings");
        assertEquals(3, readings.size());
        assertEquals("2026-11-10T00:00:00.000+01:00", readings.get(0).get("timestamp").asText());
        assertEquals(120.0, readings.get(0).get("value").asDouble(), 0);
        assertEquals(MANUAL_NOTE, readings.get(0).get("note").asText());
        assertTrue(readings.get(0).get("manual").asBoolean());
        assertTrue(readings.get(0).get("photo").asBoolean());
        assertFalse(readings.get(1).get("manual").asBoolean());
        assertFalse(readings.get(1).get("photo").asBoolean());
        assertEquals("2026-11-08T00:00:00.000+01:00", readings.get(2).get("timestamp").asText());
    }

    @Test
    public void listRights() throws Exception {
        FakeEditStore store = store();
        store.write.clear();
        JsonNode meter = meter(list(store, null), 101);
        assertFalse(meter.get("canPhoto").asBoolean());
        assertFalse(meter.get("canExchange").asBoolean());
        assertFalse(meter.get("row").get("canEnter").asBoolean());
        store.delete.add(201L);
        store.write.add(201L);
        meter = meter(list(store, null), 101);
        assertTrue(meter.get("row").get("canDelete").asBoolean());
        assertFalse("exchange also needs the write right on the meter", meter.get("canExchange").asBoolean());
    }

    @Test
    public void listProblems() throws Exception {
        JsonNode json = list(store(), null);
        assertEquals("NO_TARGET", meter(json, 102).get("problem").asText());
        assertEquals("NOT_READABLE", meter(json, 103).get("problem").asText());
        assertEquals("NOT_MANUAL", meter(json, 104).get("problem").asText());
        for (long id : new long[]{102, 103, 104}) {
            JsonNode meter = meter(json, id);
            assertTrue(meter.get("row").isNull());
            assertEquals("NONE", meter.get("due").asText());
            assertTrue(meter.get("expected").isNull());
            assertEquals(0, meter.get("readings").size());
            assertFalse(meter.get("canExchange").asBoolean());
        }
    }

    @Test
    public void listOtherRows() throws Exception {
        JsonNode json = list(store(), null);
        JsonNode gas = meter(json, 105);
        assertFalse("no Reading Photo in the class", gas.get("canPhoto").asBoolean());
        assertEquals("GAS", gas.get("medium").asText());
        assertFalse(gas.get("row").get("counter").asBoolean());
        assertEquals("MONTH", gas.get("row").get("mode").asText());
        assertTrue(gas.get("row").get("decimals").isNull());
        assertEquals("2026-11-01T00:00:00.000+01:00", gas.get("suggestedTimestamp").asText());
        assertEquals("DUE", gas.get("due").asText());
        /* average 50, margin max(20 % of 20, 20 % of 50) = 10 */
        assertEquals(50.0, gas.get("expected").get("value").asDouble(), 1e-6);
        assertEquals(30.0, gas.get("expected").get("low").asDouble(), 1e-6);
        assertEquals(70.0, gas.get("expected").get("high").asDouble(), 1e-6);
        assertFalse(gas.get("readings").get(0).get("photo").asBoolean());

        JsonNode heat = meter(json, 111);
        assertEquals("NONE", heat.get("due").asText());
        assertTrue(heat.get("expected").isNull());
        assertTrue(heat.get("row").get("period").isNull());
        assertEquals("SPECIFIC_DATETIME", heat.get("row").get("mode").asText());
    }

    /** rows filled automatically are listed but never due (demo 2026-11-13: 15-minute rows showed as overdue) */
    @Test
    public void automaticRowsAreNeverDue() throws Exception {
        FakeEditStore store = store();
        DateTime old = new DateTime(2026, 1, 1, 0, 0, ZONE);
        /* gas: target of a data point (hidden from the user) */
        store.add(obj(301, "Datenquelle", "CSV Data Point", 1));
        store.hidden.add(301L);
        store.sample(301, "Target", old, "205:Value", null);
        /* heat: 15-minute values */
        store.sample(211, "Period", old, "PT15M", null);
        JsonNode json = list(store, null);
        assertEquals("AUTOMATIC", meter(json, 105).get("problem").asText());
        assertEquals("NONE", meter(json, 105).get("due").asText());
        assertFalse(meter(json, 105).get("row").isNull());
        assertEquals("AUTOMATIC", meter(json, 111).get("problem").asText());
        assertTrue(meter(json, 101).get("problem").isNull());

        /* a manual reading among the last ones: read by hand although the period is short */
        store.sample(211, "Value", D11, "7", MANUAL_NOTE);
        assertTrue(meter(list(store, null), 111).get("problem").isNull());
    }

    @Test
    public void targetIdsAndSubDailyPeriods() {
        assertEquals(new HashSet<>(Arrays.asList(12L, 13L)), ResourceWebReadings.targetIds("12:Value; 13"));
        assertTrue(ResourceWebReadings.targetIds("x:Value").isEmpty());
        assertTrue(ResourceWebReadings.targetIds(null).isEmpty());
        assertTrue(ResourceWebReadings.subDaily(org.joda.time.Period.parse("PT15M")));
        assertTrue(ResourceWebReadings.subDaily(org.joda.time.Period.parse("PT1H")));
        assertFalse(ResourceWebReadings.subDaily(org.joda.time.Period.parse("P1D")));
        assertFalse(ResourceWebReadings.subDaily(org.joda.time.Period.parse("P1M")));
        assertFalse(ResourceWebReadings.subDaily(null));
    }

    @Test
    public void listWithoutReadings() throws Exception {
        FakeEditStore store = store();
        store.samples.remove("201/Value");
        JsonNode meter = meter(list(store, null), 101);
        assertEquals("DUE", meter.get("due").asText());
        assertTrue(meter.get("suggestedTimestamp").isNull());
        assertTrue(meter.get("expected").isNull());
        assertEquals(0, meter.get("readings").size());
    }

    // ---------------------------------------------------------------- POST

    @Test
    public void enterWritesTheReadingWithTheNote() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = enter(store, 101, body("2026-11-11T09:12:00", "130.5", "\"  Zähler verschmutzt \"", null));
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("2026-11-11T00:00:00.000+01:00", json.get("timestamp").asText());
        assertEquals(130.5, json.get("value").asDouble(), 0);
        assertEquals(0, json.get("warnings").size());
        String note = store.at(201, "Value", D11).getNote();
        assertEquals("Manuelle Dateneingabe " + NOW + " – Zähler verschmutzt", note);
        assertTrue(ResourceWebEditValues.isManual(note));
        assertEquals("130.5", store.at(201, "Value", D11).getValue());
        assertTrue(store.logs.toString(), store.logs.get(0).endsWith("|web:reading-enter"));

        /* without note as E1 */
        store = store();
        assertEquals(201, enter(store, 101, body("2026-11-11", "131", "null", false)).status);
        assertEquals("Manuelle Dateneingabe " + NOW, store.at(201, "Value", D11).getNote());
        store = store();
        assertEquals(201, enter(store, 101, body("2026-11-11", "131", "\"  \"", false)).status);
        assertEquals("Manuelle Dateneingabe " + NOW, store.at(201, "Value", D11).getNote());
    }

    @Test
    public void enterWarnsAboutTheExpectedValue() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = enter(store, 101, body("2026-11-11", "200", null, null));
        assertEquals(409, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("2026-11-11T00:00:00.000+01:00", json.get("timestamp").asText());
        assertEquals(1, json.get("warnings").size());
        assertEquals("Abweichung vom Erwartungswert: 200 (128 … 132)", json.get("warnings").get(0).asText());
        assertNull(store.at(201, "Value", D11));

        reply = WebEdit.call(store, s -> ResourceWebReadings.enter(s, 101, body("2026-11-11", "1234.5", null, null), "en", NOW));
        assertEquals("Deviation from the expected value: 1,234.5 (128 … 132)",
                FakeEditStore.json(reply).get("warnings").get(0).asText());

        reply = enter(store, 101, body("2026-11-11", "200", null, true));
        assertEquals(201, reply.status);
        assertEquals(1, FakeEditStore.json(reply).get("warnings").size());
        assertEquals("200", store.at(201, "Value", D11).getValue());
    }

    @Test
    public void counterWarningWithoutExpectedWarning() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = enter(store, 101, body("2026-11-11", "100", null, null));
        assertEquals(409, reply.status);
        JsonNode warnings = FakeEditStore.json(reply).get("warnings");
        assertEquals(1, warnings.size());
        assertEquals(ResourceWebEditValues.text(ResourceWebEditValues.KEY_COUNTER_LOWER, "de"), warnings.get(0).asText());
    }

    @Test
    public void enterErrors() throws Exception {
        FakeEditStore hidden = store();
        hidden.hidden.add(101L);
        assertEquals(404, enter(hidden, 101, body("2026-11-11", "130", null, null)).status);
        FakeEditStore store = store();
        assertEquals(404, enter(store, 999, body("2026-11-11", "130", null, null)).status);
        assertEquals("a row is no meter", 404, enter(store, 201, body("2026-11-11", "130", null, null)).status);

        for (long id : new long[]{102, 103, 104}) {
            WebEdit.Reply reply = enter(store, id, body("2026-11-11", "130", null, null));
            assertEquals("meter " + id, 400, reply.status);
            assertNotNull(FakeEditStore.json(reply).get("messages"));
        }
        assertEquals("NO_TARGET", FakeEditStore.json(enter(store, 102, body("2026-11-11", "130", null, null))).get("problem").asText());

        store.write.remove(201L);
        assertEquals(403, enter(store, 101, body("2026-11-11", "130", null, null)).status);
        store.write.add(201L);

        assertEquals(400, enter(store, 101, "not json").status);
        assertEquals(400, enter(store, 101, body("2026-11-11", "\"130\"", null, null)).status);
        assertEquals(400, enter(store, 101, "{\"value\":130}").status);
        assertEquals(400, enter(store, 101, body("2026-11-11", "130", "12", null)).status);
        assertEquals(400, enter(store, 101, body("2026-11-11", "130", "\"a\\u0007b\"", null)).status);
        StringBuilder longNote = new StringBuilder("\"");
        for (int i = 0; i < 501; i++) longNote.append('x');
        WebEdit.Reply reply = enter(store, 101, body("2026-11-11", "130", longNote + "\"", null));
        assertEquals(400, reply.status);
        assertTrue(FakeEditStore.json(reply).get("messages").get(0).asText().contains("500"));
        assertTrue(store.writes.isEmpty());
    }

    // ---------------------------------------------------------------- photos

    private static WebEdit.Reply upload(FakeEditStore store, long meterId, String timestamp, byte[] bytes, int max)
            throws Exception {
        return WebEdit.call(store, s -> ResourceWebReadings.uploadPhoto(s, meterId, timestamp,
                bytes != null ? new ByteArrayInputStream(bytes) : null, max));
    }

    @Test
    public void uploadPhoto() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = upload(store, 101, "2026-11-11T00:00:00.000+01:00", PNG, ResourceWebReadings.MAX_PHOTO_SIZE);
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("2026-11-11T00:00:00.000+01:00", json.get("timestamp").asText());
        assertEquals("Ablesung_20261111_0000.png", json.get("file").asText());
        assertEquals("Ablesung_20261111_0000.png", store.at(101, ResourceWebReadings.ATTRIBUTE_READING_PHOTO, D11).getValue());
        assertTrue(store.logs.get(0), store.logs.get(0).endsWith("101:Reading Photo|1|web:reading-photo"));

        assertEquals("Ablesung_20261111_0912.jpg", FakeEditStore.json(upload(store, 101, "2026-11-11T09:12:30.250",
                JPEG, ResourceWebReadings.MAX_PHOTO_SIZE)).get("file").asText());
        assertEquals("Ablesung_20261111_0913.heic", FakeEditStore.json(upload(store, 101, "2026-11-11T09:13:00",
                HEIC, ResourceWebReadings.MAX_PHOTO_SIZE)).get("file").asText());

        /* the reading of the photo is listed with photo: true */
        store.sample(201, "Value", D11, "130", null);
        assertTrue(meter(list(store, null), 101).get("readings").get(0).get("photo").asBoolean());
    }

    @Test
    public void uploadPhotoErrors() throws Exception {
        FakeEditStore store = store();
        int max = ResourceWebReadings.MAX_PHOTO_SIZE;
        String ts = "2026-11-11T00:00:00";
        assertEquals(404, upload(store, 999, ts, PNG, max).status);
        assertEquals(404, upload(store, 201, ts, PNG, max).status);
        store.write.add(105L);
        assertEquals("no Reading Photo", 400, upload(store, 105, ts, PNG, max).status);
        store.write.remove(101L);
        assertEquals(403, upload(store, 101, ts, PNG, max).status);
        store.write.add(101L);
        assertEquals(400, upload(store, 101, null, PNG, max).status);
        assertEquals(400, upload(store, 101, "yesterday", PNG, max).status);
        assertEquals(400, upload(store, 101, ts, null, max).status);
        assertEquals("no image", 400, upload(store, 101, ts, "<svg/>".getBytes(StandardCharsets.UTF_8), max).status);
        assertEquals("GIF is not offered", 400, upload(store, 101, ts, "GIF89a....".getBytes(StandardCharsets.UTF_8), max).status);
        assertEquals(413, upload(store, 101, ts, PNG, 8).status);
        assertNull(store.at(101, ResourceWebReadings.ATTRIBUTE_READING_PHOTO, D11));
    }

    @Test
    public void photoDetection() {
        assertEquals("png", ResourceWebReadings.photoExtension(PNG));
        assertEquals("jpg", ResourceWebReadings.photoExtension(JPEG));
        assertEquals("heic", ResourceWebReadings.photoExtension(HEIC));
        assertEquals("webp", ResourceWebReadings.photoExtension("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)));
        byte[] mp4 = HEIC.clone();
        mp4[8] = 'i';
        mp4[9] = 's';
        mp4[10] = 'o';
        mp4[11] = 'm';
        assertNull(ResourceWebReadings.photoExtension(mp4));
        assertNull(ResourceWebReadings.photoExtension(new byte[0]));
    }

    @Test
    public void readPhoto() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebReadings.photo(s, 101, "2026-11-10T00:00:00.000+01:00"));
        assertEquals(200, reply.status);
        ResourceWebReadings.Photo photo = (ResourceWebReadings.Photo) reply.body;
        assertEquals("Ablesung_20261110_0000.jpg", photo.name);
        assertEquals("photo", new String(photo.bytes, StandardCharsets.UTF_8));

        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadings.photo(s, 101, "2026-11-09T00:00:00")).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadings.photo(s, 105, "2026-11-10T00:00:00")).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadings.photo(s, 999, "2026-11-10T00:00:00")).status);
        FakeEditStore hidden = store();
        hidden.hidden.add(101L);
        assertEquals(404, WebEdit.call(hidden, s -> ResourceWebReadings.photo(s, 101, "2026-11-10T00:00:00")).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebReadings.photo(s, 102, "nonsense")).status);
    }

    // ---------------------------------------------------------------- registers

    @Test
    public void readingPhotoIsNoFormField() {
        JsonType photo = new JsonType();
        photo.setName(ResourceWebReadings.ATTRIBUTE_READING_PHOTO);
        photo.setPrimitiveType(JEVisConstants.PrimitiveType.FILE);
        photo.setGuiType("File Selector");
        assertFalse(ResourceWebEditRegisters.editable(photo));
        photo.setName("Picture");
        assertTrue(ResourceWebEditRegisters.editable(photo));
        assertFalse(ResourceWebRegisters.METER_DEFAULT_COLUMNS.contains(ResourceWebReadings.ATTRIBUTE_READING_PHOTO));
    }
}
