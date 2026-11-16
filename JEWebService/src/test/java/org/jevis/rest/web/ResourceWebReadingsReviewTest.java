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
import org.joda.time.LocalDate;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

/**
 * Review of contradictory readings (stage E8.5).
 */
public class ResourceWebReadingsReviewTest {

    private static final DateTime NOW = new DateTime(2026, 11, 13, 10, 30, 15, ZONE);
    private static final DateTime NOV = new DateTime(2026, 11, 1, 0, 0, ZONE);
    private static final String NOV_ISO = "2026-11-01T00:00:00.000+01:00";
    private static final String MI = "Measurement Instrument";
    private static final String EMI = "Electricity Measurement Instrument";
    private static final String MANUAL_NOTE = "Manuelle Dateneingabe 2026-11-01T08:00:00.000+01:00";
    private static final String NOTES = ResourceWebNotes.CLASS_DATA_NOTES;

    /* organisation 1 > building 2 "Musterwerk" > directory 3 > meters 101 (counter row 201, P1M), 102 (row 205, no
       counter, P1M, limits 0..10), 103 (row 203, target of a data point), 104 (no Online ID), 106 (row 208 hidden);
       organisation 1 > building 5 "Zweitwerk" > directory 6 > meter 111 (counter row 211, P1M, limits max 35) */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        store.add(obj(1, "Musterfirma", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(2, "Musterwerk", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(3, "Messstellen", ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY, 2),
                obj(101, "Hauptzähler Strom", EMI, 3),
                obj(102, "Wasserzähler", EMI, 3),
                obj(103, "Zähler automatisch", EMI, 3),
                obj(104, "Zähler ohne Ziel", EMI, 3),
                obj(106, "Zähler verborgen", EMI, 3),
                obj(10, "Daten", "Data Directory", 2),
                obj(201, "Strom - Hauptzähler", "Data", 10),
                obj(202, "Strom - Hauptzähler bereinigt", "Clean Data", 201),
                obj(203, "Automatisch", "Data", 10),
                obj(204, "Automatisch bereinigt", "Clean Data", 203),
                obj(205, "Wasser", "Data", 10),
                obj(206, "Wasser bereinigt", "Clean Data", 205),
                obj(208, "Verborgen", "Data", 10),
                obj(301, "Datenquelle", "CSV Data Point", 1),
                obj(5, "Zweitwerk", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(6, "Messstellen", ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY, 5),
                obj(111, "Wärmezähler", EMI, 6),
                obj(211, "Wärme", "Data", 6),
                obj(212, "Wärme bereinigt", "Clean Data", 211));
        store.hidden.add(208L);
        store.hidden.add(301L);
        Object[] meterTypes = {"Online ID", JEVisConstants.PrimitiveType.STRING};
        store.type(MI, null, meterTypes);
        store.type(EMI, MI, meterTypes);
        store.type("Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE, "Period", JEVisConstants.PrimitiveType.STRING);
        store.type("Clean Data", "Data", "Value", JEVisConstants.PrimitiveType.DOUBLE);
        DateTime old = new DateTime(2020, 1, 1, 0, 0, ZONE);
        store.sample(101, "Online ID", old, "201:Value", null);
        store.sample(102, "Online ID", old, "205", null);
        store.sample(103, "Online ID", old, "203", null);
        store.sample(106, "Online ID", old, "208", null);
        store.sample(111, "Online ID", old, "211", null);
        store.sample(301, "Target", old, "203:Value", null);
        for (long row : new long[]{201, 203, 205, 208, 211}) store.sample(row, "Period", old, "P1M", null);
        store.sample(202, ResourceWebEditValues.ATTRIBUTE_DIFFERENTIAL, old, "true", null);
        store.sample(204, ResourceWebEditValues.ATTRIBUTE_DIFFERENTIAL, old, "true", null);
        store.sample(206, ResourceWebEditValues.ATTRIBUTE_DIFFERENTIAL, old, "false", null);
        store.sample(206, ResourceWebEditValues.ATTRIBUTE_LIMITS, old, "[{\"min\":\"0\",\"max\":\"10\"}]", null);
        store.sample(212, ResourceWebEditValues.ATTRIBUTE_DIFFERENTIAL, old, "true", null);
        store.sample(212, ResourceWebEditValues.ATTRIBUTE_LIMITS, old, "[{\"min\":\"0\",\"max\":\"35\"},{\"max\":\"500\"}]", null);
        store.write.add(101L);
        store.write.add(201L);
        return store;
    }

    /**
     * Monthly samples on the first of the months from {@code first} on
     */
    private static void monthly(FakeEditStore store, long row, LocalDate first, double... values) {
        for (int i = 0; i < values.length; i++) {
            store.sample(row, "Value", first.plusMonths(i).toDateTimeAtStartOfDay(ZONE), plain(values[i]), "import");
        }
    }

    private static String plain(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * Counter row 201: 1000 … 1900 from January to October 2026 and the value of November
     */
    private static FakeEditStore counter(double november, String note) {
        FakeEditStore store = store();
        monthly(store, 201, new LocalDate(2026, 1, 1), 1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900);
        store.sample(201, "Value", NOV, plain(november), note);
        return store;
    }

    private static JsonNode list(FakeEditStore store, Long site, int days) throws Exception {
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebReadingsReview.list(s, site, days, NOW));
        assertEquals(200, reply.status);
        return FakeEditStore.json(reply);
    }

    private static JsonNode items(FakeEditStore store) throws Exception {
        return list(store, null, ResourceWebReadingsReview.DEFAULT_DAYS).get("items");
    }

    private static List<String> issues(JsonNode item) {
        List<String> issues = new ArrayList<>();
        for (JsonNode issue : item.get("issues")) issues.add(issue.asText());
        return issues;
    }

    private static WebEdit.Reply post(FakeEditStore store, long meterId, String body, String language) throws Exception {
        return WebEdit.call(store, s -> ResourceWebReadingsReview.review(s, meterId, body, language, NOW));
    }

    private static String body(String action, String comment, String value) {
        return "{\"timestamp\":\"" + NOV_ISO + "\",\"action\":\"" + action + "\""
                + (comment != null ? ",\"comment\":" + comment : "")
                + (value != null ? ",\"value\":" + value : "") + "}";
    }

    /**
     * the note of the first "Data Notes" object below the row at the time, null if none
     */
    private static String noteText(FakeEditStore store, long row, DateTime time) {
        for (org.jevis.commons.ws.json.JsonObject o : store.objects) {
            if (NOTES.equals(o.getJevisClass()) && o.getParent() == row) {
                JsonSample sample = store.at(o.getId(), "Value", time);
                if (sample != null) return sample.getValue();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- what is listed

    @Test
    public void counterDecreaseIsListed() throws Exception {
        JsonNode json = list(counter(1850, MANUAL_NOTE), null, 365);
        assertEquals(1, json.get("count").asInt());
        assertFalse(json.get("truncated").asBoolean());
        JsonNode item = json.get("items").get(0);
        assertEquals(101, item.get("meterId").asLong());
        assertEquals("Hauptzähler Strom", item.get("meterName").asText());
        assertEquals(2, item.get("site").get("id").asLong());
        assertEquals("Musterwerk", item.get("site").get("name").asText());
        JsonNode row = item.get("row");
        assertEquals(201, row.get("id").asLong());
        assertEquals("Strom - Hauptzähler", row.get("name").asText());
        assertEquals("kWh", row.get("unit").asText());
        assertTrue(row.get("counter").asBoolean());
        assertTrue(row.get("canEnter").asBoolean());
        assertFalse(row.get("canDelete").asBoolean());
        assertFalse(row.get("canNote").asBoolean());
        assertEquals(NOV_ISO, item.get("timestamp").asText());
        assertEquals(1850, item.get("value").asDouble(), 0);
        assertEquals(MANUAL_NOTE, item.get("note").asText());
        assertTrue(item.get("manual").asBoolean());
        assertEquals("2026-10-01T00:00:00.000+02:00", item.get("previous").get("timestamp").asText());
        assertEquals(1900, item.get("previous").get("value").asDouble(), 0);
        assertTrue(item.get("expected").get("low").asDouble() > 1900);
        assertTrue(item.get("expected").get("high").asDouble() > item.get("expected").get("value").asDouble());
        assertEquals("far from expected too, but not listed twice", Arrays.asList("COUNTER_DECREASE"), issues(item));
        assertTrue(item.get("canExchange").asBoolean());
    }

    @Test
    public void meterExchangeIsNoIssue() throws Exception {
        assertEquals(0, items(counter(5, "Meter Tausch")).size());
        assertEquals(0, items(counter(5, "Meter Tausch,userNotes")).size());

        /* the note on the sample before: the old meter's last reading */
        FakeEditStore store = counter(5, null);
        store.sample(201, "Value", new DateTime(2026, 10, 1, 0, 0, ZONE), "1900", "meter tausch");
        assertEquals(0, items(store).size());

        /* any other note: listed */
        assertEquals(1, items(counter(5, "Zähler verschmutzt")).size());
    }

    @Test
    public void farFromExpected() throws Exception {
        JsonNode items = items(counter(3000, null));
        assertEquals(1, items.size());
        assertEquals(Arrays.asList("FAR_FROM_EXPECTED"), issues(items.get(0)));
        assertTrue(items.get(0).get("note").isNull());
        assertFalse(items.get(0).get("manual").asBoolean());

        /* within the range: nothing */
        assertEquals(0, items(counter(2000, null)).size());
    }

    @Test
    public void limitsOfCounters() throws Exception {
        FakeEditStore store = store();
        monthly(store, 211, new LocalDate(2026, 1, 1), 100, 140, 180, 220, 260, 300, 340, 380, 420, 460, 500);
        JsonNode items = items(store);
        /* every difference to the month before is 40 > 35; January has no sample one period earlier */
        assertEquals(10, items.size());
        for (JsonNode item : items) assertEquals(Arrays.asList("LIMIT"), issues(item));
        assertEquals(NOV_ISO, items.get(0).get("timestamp").asText());
        assertEquals("2026-02-01T00:00:00.000+01:00", items.get(9).get("timestamp").asText());
        assertEquals(111, items.get(0).get("meterId").asLong());
        assertEquals(5, items.get(0).get("site").get("id").asLong());
    }

    @Test
    public void limitsOfOtherRows() throws Exception {
        FakeEditStore store = store();
        monthly(store, 205, new LocalDate(2026, 1, 1), 1, 9, 1, 9, 1, 9, 1, 9, 1, 9, 10.5);
        JsonNode items = items(store);
        assertEquals(1, items.size());
        assertEquals(Arrays.asList("LIMIT"), issues(items.get(0)));
        assertFalse(items.get(0).get("row").get("counter").asBoolean());
        assertFalse(items.get(0).get("canExchange").asBoolean());
        assertEquals(5, items.get(0).get("expected").get("value").asDouble(), 0);

        /* far from the average and outside the limits */
        store.sample(205, "Value", NOV, "-3", null);
        assertEquals(Arrays.asList("FAR_FROM_EXPECTED", "LIMIT"), issues(items(store).get(0)));
    }

    @Test
    public void confirmedSamplesAreResolved() throws Exception {
        for (String text : Arrays.asList("Ablesung geprüft: 1.850 kWh ist plausibel.", "Reading checked: 1,850 kWh is plausible.")) {
            FakeEditStore store = counter(1850, null);
            store.add(obj(207, "Strom - Hauptzähler Notes", NOTES, 201));
            store.sample(207, "Value", NOV, text, null);
            assertEquals(text, 0, items(store).size());
        }
        /* another note or a confirmation at another time: still listed */
        FakeEditStore store = counter(1850, null);
        store.add(obj(207, "Strom - Hauptzähler Notes", NOTES, 201));
        store.sample(207, "Value", NOV, "Zähler verschmutzt", null);
        store.sample(207, "Value", NOV.plusSeconds(1), "Ablesung geprüft: …", null);
        assertEquals(1, items(store).size());
    }

    @Test
    public void automaticAndUnreadableRowsAreLeftOut() throws Exception {
        FakeEditStore store = store();
        LocalDate jan = new LocalDate(2026, 1, 1);
        monthly(store, 203, jan, 1000, 1100, 1200, 50);
        monthly(store, 208, jan, 1000, 1100, 1200, 50);
        assertEquals(0, items(store).size());

        /* the same row read by hand: listed */
        store.objects.removeIf(o -> o.getId() == 301);
        store.add();
        assertEquals(1, items(store).size());
    }

    @Test
    public void daysWindowAndSite() throws Exception {
        FakeEditStore store = store();
        monthly(store, 201, new LocalDate(2025, 1, 1), 1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 1000);
        assertEquals("November 2025 is older than 365 days", 0, items(store).size());
        assertEquals(1, list(store, null, 1095).get("count").asInt());

        monthly(store, 211, new LocalDate(2026, 10, 1), 100, 140);
        assertEquals(1, list(store, 5L, 1095).get("count").asInt());
        assertEquals(111, list(store, 5L, 1095).get("items").get(0).get("meterId").asLong());
        assertEquals(2, list(store, null, 1095).get("count").asInt());
        assertEquals(0, list(store, 999L, 1095).get("count").asInt());

        assertEquals(365, ResourceWebReadingsReview.days(null));
        assertEquals(90, ResourceWebReadingsReview.days("90"));
        assertEquals(1095, ResourceWebReadingsReview.days("1095"));
        for (String invalid : Arrays.asList("0", "1096", "-5", "x")) {
            try {
                ResourceWebReadingsReview.days(invalid);
                fail(invalid);
            } catch (WebEdit.Stop stop) {
                assertEquals(400, stop.reply.status);
            }
        }
    }

    @Test
    public void atMost500ItemsNewestFirst() throws Exception {
        FakeEditStore store = store();
        store.sample(205, "Period", new DateTime(2020, 1, 1, 0, 0, ZONE), "P1D", null);
        LocalDate first = new LocalDate(2025, 1, 1);
        for (int i = 0; i < 600; i++) {
            store.sample(205, "Value", first.plusDays(i).toDateTimeAtStartOfDay(ZONE), "20", null);
        }
        JsonNode json = list(store, null, 1095);
        assertEquals(500, json.get("count").asInt());
        assertTrue(json.get("truncated").asBoolean());
        assertEquals(first.plusDays(599).toDateTimeAtStartOfDay(ZONE).getMillis(),
                new DateTime(json.get("items").get(0).get("timestamp").asText()).getMillis());
        assertFalse(json.get("items").get(0).has("millis"));
    }

    // ---------------------------------------------------------------- CONFIRM

    @Test
    public void confirmWritesTheNote() throws Exception {
        FakeEditStore store = counter(1850, MANUAL_NOTE);
        store.execute.add(201L);
        assertTrue(items(store).get(0).get("row").get("canNote").asBoolean());

        WebEdit.Reply reply = post(store, 101, body("CONFIRM", "\"vor Ort geprüft\"", "1850"), "de");
        assertEquals(201, reply.status);
        assertEquals(NOV_ISO, FakeEditStore.json(reply).get("timestamp").asText());
        assertEquals("CONFIRM", FakeEditStore.json(reply).get("action").asText());
        assertEquals("Ablesung geprüft: 1.850 kWh ist plausibel. – vor Ort geprüft", noteText(store, 201, NOV));
        assertEquals("the notes object was created", "Strom - Hauptzähler Notes", store.object(9000).getName());
        assertEquals(FakeEditStore.ACCOUNT, store.at(9000, "User", NOV).getValue());
        assertEquals("", store.at(9000, "Tag", NOV).getValue());
        assertEquals("1850", store.at(201, "Value", NOV).getValue());
        assertEquals(MANUAL_NOTE + ",userNotes", store.at(201, "Value", NOV).getNote());
        assertTrue(store.logs.stream().anyMatch(l -> l.contains("9000:Value|1|web:reading-review")));
        assertEquals(0, items(store).size());

        /* English, without comment */
        store = counter(1850, null);
        store.execute.add(201L);
        assertEquals(201, post(store, 101, body("CONFIRM", null, null), "en").status);
        assertEquals("Reading checked: 1,850 kWh is plausible.", noteText(store, 201, NOV));
    }

    @Test
    public void confirmKeepsAnExistingNote() throws Exception {
        FakeEditStore store = counter(1850, null);
        store.add(obj(207, "Strom - Hauptzähler Notes", NOTES, 201));
        store.sample(207, "Value", NOV, "Zähler verschmutzt", null);
        store.sample(207, "Tag", NOV, "4", null);
        store.write.add(207L);
        assertEquals(201, post(store, 101, body("CONFIRM", null, null), "de").status);
        assertEquals("Ablesung geprüft: 1.850 kWh ist plausibel.\nZähler verschmutzt", store.at(207, "Value", NOV).getValue());
        assertEquals("4", store.at(207, "Tag", NOV).getValue());
        assertEquals(0, items(store).size());
    }

    @Test
    public void confirmNeedsTheNoteRight() throws Exception {
        FakeEditStore store = counter(1850, null);
        WebEdit.Reply reply = post(store, 101, body("CONFIRM", null, null), "de");
        assertEquals(403, reply.status);
        assertTrue(store.writes.isEmpty());

        /* create right alone is not enough: the new notes object needs write or execute on the row */
        store.write.remove(201L);
        store.create.add(201L);
        assertEquals(403, post(store, 101, body("CONFIRM", null, null), "de").status);
        store.write.add(201L);
        assertEquals(201, post(store, 101, body("CONFIRM", null, null), "de").status);
    }

    // ---------------------------------------------------------------- IGNORE

    @Test
    public void ignoreDeletesTheSampleAndWritesTheNote() throws Exception {
        FakeEditStore store = counter(1850, MANUAL_NOTE);
        store.execute.add(201L);
        store.delete.add(201L);
        store.dependents.add(202L);
        store.delete.add(202L);
        store.sample(202, "Value", NOV, "0", null);
        store.sample(202, "Value", NOV.minusMonths(1), "100", null);

        WebEdit.Reply reply = post(store, 101, body("IGNORE", "\"Ablesefehler\"", "1850"), "de");
        assertEquals(201, reply.status);
        assertEquals("IGNORE", FakeEditStore.json(reply).get("action").asText());
        assertNull(store.at(201, "Value", NOV));
        assertEquals("Ablesung ignoriert: 1.850 kWh (Rücklauf) – Ablesefehler", noteText(store, 201, NOV));
        assertNull("dependent row reset from that time", store.at(202, "Value", NOV));
        assertNotNull(store.at(202, "Value", NOV.minusMonths(1)));
        assertTrue(store.logs.stream().anyMatch(l -> l.startsWith("DELETE_SAMPLE 201:Value|") && l.endsWith("web:reading-review")));
        /* gone from the list (the sample is removed) */
        assertEquals(0, items(store).size());
        /* a second time: no sample any more */
        assertEquals(404, post(store, 101, body("IGNORE", null, null), "de").status);
    }

    @Test
    public void ignoreTextsInEnglish() {
        assertEquals("Reading ignored: 3,000 kWh (deviation from the expected value, limit)",
                ResourceWebReadingsReview.ignoreText(3000, "kWh", Arrays.asList("FAR_FROM_EXPECTED", "LIMIT"), "en"));
        assertEquals("Ablesung ignoriert: 12,5", ResourceWebReadingsReview.ignoreText(12.5, null, new ArrayList<>(), "de"));
    }

    @Test
    public void ignoreNeedsTheDeleteRight() throws Exception {
        FakeEditStore store = counter(1850, null);
        store.execute.add(201L);
        assertEquals(403, post(store, 101, body("IGNORE", null, null), "de").status);
        assertNotNull(store.at(201, "Value", NOV));
        assertTrue(store.deletes.isEmpty());
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void ignoreWritesTheSampleBackIfTheNoteFails() throws Exception {
        FakeEditStore store = counter(1850, MANUAL_NOTE);
        store.execute.add(201L);
        store.delete.add(201L);
        store.dependents.add(202L);
        store.delete.add(202L);
        store.sample(202, "Value", NOV, "0", null);
        store.failWritesOf.add("9000/User");
        try {
            post(store, 101, body("IGNORE", null, null), "de");
            fail("the failure reaches the request (500)");
        } catch (IllegalStateException e) {
            assertEquals("write failed", e.getMessage());
        }
        JsonSample restored = store.at(201, "Value", NOV);
        assertNotNull(restored);
        assertEquals("1850", restored.getValue());
        assertEquals(MANUAL_NOTE, restored.getNote());
        assertTrue("the new notes object is removed again", store.deletedObjects.contains(9000L));
        assertNotNull("dependents not reset", store.at(202, "Value", NOV));
        assertTrue(store.logs.stream().anyMatch(l -> l.endsWith("web:reading-review-rollback")));

        /* an existing notes object: the samples written so far are removed */
        store = counter(1850, null);
        store.add(obj(207, "Strom - Hauptzähler Notes", NOTES, 201));
        store.write.add(207L);
        store.delete.add(201L);
        store.failWritesOf.add("207/User");
        try {
            post(store, 101, body("IGNORE", null, null), "de");
            fail();
        } catch (IllegalStateException e) {
            /* expected */
        }
        assertEquals("1850", store.at(201, "Value", NOV).getValue());
        assertNull(store.at(207, "Value", NOV));
        assertNull(store.at(207, "Tag", NOV));
    }

    // ---------------------------------------------------------------- errors

    @Test
    public void changedValueIsAConflict() throws Exception {
        FakeEditStore store = counter(1850, null);
        store.execute.add(201L);
        store.delete.add(201L);
        WebEdit.Reply reply = post(store, 101, body("IGNORE", null, "1800"), "de");
        assertEquals(409, reply.status);
        JsonNode item = FakeEditStore.json(reply);
        assertEquals(1850, item.get("value").asDouble(), 0);
        assertEquals(NOV_ISO, item.get("timestamp").asText());
        assertEquals(Arrays.asList("COUNTER_DECREASE"), issues(item));
        assertTrue(store.writes.isEmpty());
        assertTrue(store.deletes.isEmpty());
    }

    @Test
    public void errors() throws Exception {
        FakeEditStore store = counter(1850, null);
        store.execute.add(201L);
        assertEquals(404, post(store, 999, body("CONFIRM", null, null), "de").status);
        store.hidden.add(101L);
        store.add();
        assertEquals(404, post(store, 101, body("CONFIRM", null, null), "de").status);
        store.hidden.remove(101L);
        store.add();

        WebEdit.Reply reply = post(store, 104, body("CONFIRM", null, null), "de");
        assertEquals(400, reply.status);
        assertEquals("NO_TARGET", FakeEditStore.json(reply).get("problem").asText());
        reply = post(store, 106, body("CONFIRM", null, null), "de");
        assertEquals("NOT_READABLE", FakeEditStore.json(reply).get("problem").asText());
        store.sample(203, "Value", NOV, "5", null);
        reply = post(store, 103, body("CONFIRM", null, null), "de");
        assertEquals(400, reply.status);
        assertEquals("AUTOMATIC", FakeEditStore.json(reply).get("problem").asText());

        assertEquals("no sample at that time", 404, WebEdit.call(store, s -> ResourceWebReadingsReview.review(s, 101,
                "{\"timestamp\":\"2026-11-02T00:00:00.000+01:00\",\"action\":\"CONFIRM\"}", "de", NOW)).status);
        assertEquals(400, post(store, 101, body("DELETE", null, null), "de").status);
        assertEquals(400, post(store, 101, "{\"action\":\"CONFIRM\"}", "de").status);
        assertEquals(400, post(store, 101, "{\"timestamp\":\"gestern\",\"action\":\"CONFIRM\"}", "de").status);
        assertEquals(400, post(store, 101, body("CONFIRM", "\"" + repeat('x', 501) + "\"", null), "de").status);
        assertEquals(400, post(store, 101, body("CONFIRM", "\"a\\u0007b\"", null), "de").status);
        assertEquals(400, post(store, 101, body("CONFIRM", "42", null), "de").status);
        assertEquals(400, post(store, 101, body("CONFIRM", null, "\"1850\""), "de").status);
        assertTrue(store.writes.isEmpty());

        /* line breaks are allowed in the comment */
        assertEquals(201, post(store, 101, body("CONFIRM", "\"Zeile 1\\nZeile 2\"", null), "de").status);
        assertTrue(noteText(store, 201, NOV).endsWith("– Zeile 1\nZeile 2"));
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    // ---------------------------------------------------------------- navigation

    @Test
    public void navigationCount() throws Exception {
        FakeEditStore store = counter(1850, null);
        monthly(store, 211, new LocalDate(2026, 10, 1), 100, 140);
        assertEquals(Integer.valueOf(2), ResourceWebReadingsReview.navigationCount(store, NOW));
        assertNull("too many meters for the navigation", ResourceWebReadingsReview.review(store, null, 365, NOW, 1));

        ResourceWebAlarms.Store alarms = new ResourceWebAlarms.Store() {
            @Override
            public List<JsonSample> samples(long id, String attribute, DateTime from, DateTime to, long limit) {
                return store.samples(id, attribute, from, to, limit);
            }

            @Override
            public JsonSample latest(long id, String attribute) {
                return store.latest(id, attribute);
            }

            @Override
            public byte[] file(long id, String attribute, JsonSample sample) {
                return null;
            }

            @Override
            public String unit(long id, String attribute) {
                return null;
            }
        };
        Map<String, Object> modules = ResourceWebModules.moduleCounts(store.lookup(), alarms, store, NOW);
        assertEquals(2, modules.get("readingsReview"));
        assertTrue(modules.containsKey("alarms"));

        /* confirmed: one less */
        store.add(obj(207, "Strom - Hauptzähler Notes", NOTES, 201));
        store.sample(207, "Value", NOV, "Ablesung geprüft: ok", null);
        assertEquals(Integer.valueOf(1), ResourceWebReadingsReview.navigationCount(store, NOW));
    }
}
