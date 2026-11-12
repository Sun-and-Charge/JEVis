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
import org.jevis.api.JEVisConstants;
import org.jevis.commons.chart.AnalysisResult;
import org.jevis.commons.chart.ChartResult;
import org.jevis.commons.chart.ChartSample;
import org.jevis.commons.chart.RowResult;
import org.jevis.commons.chart.RowStatus;
import org.jevis.commons.ws.json.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebNotesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final String NOTES = ResourceWebNotes.CLASS_DATA_NOTES;

    // ---------------------------------------------------------------- objects

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    /* Organisation 1 > site 2 (subclass of Building) > folder 3 "Data" > folder 4 "Electricity" > row 20 > notes 21;
       row 20 > clean data 25 > notes 26; hidden row 30 > notes 31; row 20 > hidden notes 41; row 20 > notes in the
       recycle bin 51; row 60 without building > notes 61 */
    private static final JsonObject ORG = obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
    private static final JsonObject SITE = obj(2, "Site A", "Special Building", 1);
    private static final JsonObject DATA_FOLDER = obj(3, "Data", "Data Directory", 2);
    private static final JsonObject POWER_FOLDER = obj(4, "Electricity", "Data Directory", 3);
    private static final JsonObject ROW = obj(20, "Total", "Data", 4);
    private static final JsonObject ROW_NOTES = obj(21, "Total Notes", NOTES, 20);
    private static final JsonObject CLEAN = obj(25, "Total clean", "Clean Data", 20);
    private static final JsonObject CLEAN_NOTES = obj(26, "Clean Notes", NOTES, 25);
    private static final JsonObject HIDDEN_ROW = obj(30, "Hidden row", "Data", 4);
    private static final JsonObject HIDDEN_ROW_NOTES = obj(31, "Hidden row Notes", NOTES, 30);
    private static final JsonObject HIDDEN_NOTES = obj(41, "Hidden Notes", NOTES, 20);
    private static final JsonObject BIN_NOTES = obj(51, "Bin Notes", NOTES, 20);
    private static final JsonObject LONE_ROW = obj(60, "Lone row", "Data", 0);
    private static final JsonObject LONE_NOTES = obj(61, "Lone Notes", NOTES, 60);

    static {
        BIN_NOTES.setDeleteTS("2026-11-01T00:00:00.000+01:00");
    }

    private static final List<JsonObject> ALL = Arrays.asList(ORG, SITE, DATA_FOLDER, POWER_FOLDER, ROW, ROW_NOTES,
            CLEAN, CLEAN_NOTES, HIDDEN_ROW, HIDDEN_ROW_NOTES, HIDDEN_NOTES, BIN_NOTES, LONE_ROW, LONE_NOTES);

    private static ResourceWebModules.Lookup lookup() {
        List<JsonObject> readable = new ArrayList<>(ALL);
        readable.removeAll(Arrays.asList(HIDDEN_ROW, HIDDEN_NOTES));
        Map<Long, Long> parents = new HashMap<>();
        for (JsonObject o : ALL) parents.put(o.getId(), o.getParent());
        return new ResourceWebModules.Lookup(ALL, readable, parents);
    }

    private static JsonType type(String cls, String name, int primitive) {
        JsonType type = new JsonType();
        type.setJevisclass(cls);
        type.setName(name);
        type.setPrimitiveType(primitive);
        return type;
    }

    private static JsonJEVisClass jclass(String name, String inherits, JsonType... types) {
        JsonJEVisClass jclass = new JsonJEVisClass();
        jclass.setName(name);
        List<JsonClassRelationship> relationships = new ArrayList<>();
        if (inherits != null) {
            JsonClassRelationship rel = new JsonClassRelationship();
            rel.setStart(name);
            rel.setEnd(inherits);
            rel.setType(JEVisConstants.ClassRelationship.INHERIT);
            relationships.add(rel);
        }
        jclass.setRelationships(relationships);
        jclass.setTypes(new ArrayList<>(Arrays.asList(types)));
        return jclass;
    }

    private static ResourceWebRegisters.Classes classes() {
        Map<String, JsonJEVisClass> map = new HashMap<>();
        for (JsonJEVisClass c : Arrays.asList(
                jclass(ResourceWebNavigation.CLASS_BUILDING, null),
                jclass("Special Building", ResourceWebNavigation.CLASS_BUILDING),
                jclass("Data", null, type("Data", "Value", JEVisConstants.PrimitiveType.DOUBLE)),
                jclass("Clean Data", null, type("Clean Data", "Value", JEVisConstants.PrimitiveType.DOUBLE)),
                jclass("String Data", null, type("String Data", "Value", JEVisConstants.PrimitiveType.STRING)),
                jclass("Data Directory", null),
                jclass(NOTES, null, type(NOTES, "Value", JEVisConstants.PrimitiveType.STRING),
                        type(NOTES, "Tag", JEVisConstants.PrimitiveType.STRING),
                        type(NOTES, "User", JEVisConstants.PrimitiveType.STRING)))) {
            map.put(c.getName(), c);
        }
        return new ResourceWebRegisters.Classes(map);
    }

    // ---------------------------------------------------------------- samples

    private static String ts(DateTime time) {
        return ISODateTimeFormat.dateTime().print(time);
    }

    private static JsonSample sample(DateTime time, String value) {
        JsonSample sample = new JsonSample();
        sample.setTs(ts(time));
        sample.setValue(value);
        return sample;
    }

    private static final class TestSamples implements ResourceWebNotes.Samples {
        final Map<String, List<JsonSample>> samples = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        TestSamples add(long id, String attribute, DateTime time, String value) {
            samples.computeIfAbsent(id + "/" + attribute, k -> new ArrayList<>()).add(sample(time, value));
            return this;
        }

        @Override
        public List<JsonSample> get(long id, String attribute, DateTime from, DateTime to, long limit) {
            calls.add(id + "/" + attribute);
            List<JsonSample> result = new ArrayList<>();
            for (JsonSample sample : samples.getOrDefault(id + "/" + attribute, Collections.emptyList())) {
                long millis = ResourceWebNotes.millis(sample.getTs());
                if (millis >= from.getMillis() && millis <= to.getMillis()) result.add(sample);
            }
            result.sort(Comparator.comparingLong(s -> ResourceWebNotes.millis(s.getTs())));
            return result.size() > limit ? new ArrayList<>(result.subList(0, (int) limit)) : result;
        }
    }

    private static final DateTime T1 = new DateTime(2026, 11, 3, 9, 15, ZONE);
    private static final DateTime T2 = new DateTime(2026, 11, 4, 10, 0, ZONE);
    private static final DateTime T3 = new DateTime(2026, 11, 5, 11, 30, ZONE);
    private static final DateTime OLD = new DateTime(2026, 1, 5, 11, 30, ZONE);

    private static TestSamples samples() {
        return new TestSamples()
                .add(21, "Value", T1, "Peak load at start of compressor 2")
                .add(21, "Tag", T1, "4")
                .add(21, "User", T1, "Demo")
                .add(21, "Value", T3, "")
                .add(21, "Tag", T3, "Ereignis;Task;Something else")
                .add(21, "Value", OLD, "Old note")
                .add(26, "Value", T2, "Clean note")
                .add(26, "User", T1, "Other time")
                .add(31, "Value", T2, "Note of a hidden row")
                .add(41, "Value", T2, "Hidden note")
                .add(51, "Value", T2, "Deleted note")
                .add(61, "Value", T1, "Lone note")
                .add(61, "Tag", T1, "1;10;13");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    // ---------------------------------------------------------------- tags

    @Test
    public void parseTagsMapsIdsAndNamesOfAnyLanguage() {
        assertEquals(Arrays.asList("4", "1"), ResourceWebNotes.NoteTags.parse("4;1"));
        assertEquals(Arrays.asList("4", "2", "Something else"),
                ResourceWebNotes.NoteTags.parse("Ereignis; task ;Something else;;4"));
        assertEquals(Arrays.asList("0", "9"), ResourceWebNotes.NoteTags.parse("Keiner;Kennzahl"));
        assertEquals(Collections.singletonList("13"), ResourceWebNotes.NoteTags.parse("Maintenance"));
        assertTrue(ResourceWebNotes.NoteTags.parse(null).isEmpty());
        assertTrue(ResourceWebNotes.NoteTags.parse(" ; ").isEmpty());
    }

    @Test
    public void tagNamesAreTranslatedWithFallbackToEnglish() {
        assertEquals("Ereignis", ResourceWebNotes.NoteTags.name("4", "de"));
        assertEquals("Event", ResourceWebNotes.NoteTags.name("4", "en"));
        assertEquals("Event", ResourceWebNotes.NoteTags.name("4", "fr"));
        assertEquals("Keiner", ResourceWebNotes.NoteTags.name("0", "de"));
        assertEquals("Something", ResourceWebNotes.NoteTags.name("Something", "de"));

        List<Map<String, Object>> tags = ResourceWebNotes.NoteTags.tagList(Arrays.asList("4", "zz", "aa"), "de");
        assertEquals(16, tags.size());
        assertEquals("0", tags.get(0).get("id"));
        assertEquals("Audit", tags.get(1).get("name"));
        assertEquals("aa", tags.get(14).get("id"));
        assertEquals("zz", tags.get(15).get("name"));
    }

    // ---------------------------------------------------------------- time ranges

    @Test
    public void dayRangeDefaultsToTheLastThreeMonths() {
        DateTime[] range = ResourceWebNotes.dayRange(null, null, ZONE, new LocalDate(2026, 11, 11));
        assertEquals("2026-08-11T00:00:00.000+02:00", ts(range[0]));
        assertEquals("2026-11-11T23:59:59.999+01:00", ts(range[1]));

        range = ResourceWebNotes.dayRange("2026-01-01", "2026-01-31", ZONE, new LocalDate(2026, 11, 11));
        assertEquals("2026-01-01T00:00:00.000+01:00", ts(range[0]));
        assertEquals("2026-01-31T23:59:59.999+01:00", ts(range[1]));
        ResourceWebNotes.dayRange("2023-11-11", "2026-11-11", ZONE, new LocalDate(2026, 11, 11));
    }

    @Test
    public void dayRangeRejectsInvalidRanges() {
        LocalDate today = new LocalDate(2026, 11, 11);
        for (String[] range : new String[][]{{"2026-02-01", "2026-01-01"}, {"2023-11-10", "2026-11-11"},
                {"yesterday", null}, {null, "2026-13-01"}}) {
            try {
                ResourceWebNotes.dayRange(range[0], range[1], ZONE, today);
                fail(Arrays.toString(range));
            } catch (IllegalArgumentException expected) {
                // 400
            }
        }
    }

    @Test
    public void sampleRangeAllowsAtMost31Days() {
        DateTime[] range = ResourceWebNotes.sampleRange("2026-11-01T00:00:00+01:00", "2026-12-02T00:00:00+01:00", ZONE);
        assertEquals(new DateTime(2026, 11, 1, 0, 0, ZONE).getMillis(), range[0].getMillis());
        assertEquals(new DateTime(2026, 11, 1, 0, 0, ZONE).getMillis(),
                ResourceWebNotes.sampleRange("2026-11-01T00:00:00", "2026-11-02T00:00:00", ZONE)[0].getMillis());
        /* October with the switch to winter time is 31 days and one hour long, also for a server time zone without
           daylight saving time */
        for (DateTimeZone zone : new DateTimeZone[]{ZONE, DateTimeZone.UTC}) {
            DateTime[] october = ResourceWebNotes.sampleRange("2026-10-01T00:00:00+02:00", "2026-10-31T23:59:59.999+01:00", zone);
            assertEquals(new DateTime(2026, 10, 31, 23, 59, 59, 999, ZONE).getMillis(), october[1].getMillis());
            ResourceWebNotes.sampleRange("2026-10-01T00:00:00+02:00", "2026-11-01T00:00:00+01:00", zone);
        }
        try {
            ResourceWebNotes.sampleRange("2026-10-01T00:00:00+02:00", "2026-11-01T00:00:01+01:00", DateTimeZone.UTC);
            fail("31 days and one hour plus one second");
        } catch (IllegalArgumentException expected) {
            // 400
        }
        for (String[] invalid : new String[][]{{"2026-11-01T00:00:00+01:00", "2026-12-02T01:00:01+01:00"},
                {"2026-11-02T00:00:00", "2026-11-01T00:00:00"}, {null, "2026-11-01T00:00:00"}, {"x", "y"}}) {
            try {
                ResourceWebNotes.sampleRange(invalid[0], invalid[1], ZONE);
                fail(Arrays.toString(invalid));
            } catch (IllegalArgumentException expected) {
                // 400
            }
        }
    }

    // ---------------------------------------------------------------- notes

    @Test
    public void notesOfReadableObjectsNewestFirst() throws Exception {
        DateTime[] range = ResourceWebNotes.dayRange("2026-11-01", "2026-11-11", ZONE, new LocalDate(2026, 11, 11));
        Map<String, Object> json = ResourceWebNotes.notesJson(lookup(), classes(), samples(), range[0], range[1], ZONE, "de");
        assertEquals("2026-11-01T00:00:00.000+01:00", json.get("from"));
        assertEquals(false, json.get("truncated"));

        List<Map<String, Object>> notes = list(json.get("notes"));
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> note : notes) ids.add((String) note.get("id"));
        assertEquals(Arrays.asList("21-" + T3.getMillis(), "26-" + T2.getMillis(), "31-" + T2.getMillis(),
                "21-" + T1.getMillis(), "61-" + T1.getMillis()), ids);

        Map<String, Object> newest = notes.get(0);
        assertEquals("", newest.get("text"));
        assertEquals(Arrays.asList("4", "2", "Something else"), newest.get("tags"));
        assertNull(newest.get("user"));
        assertEquals(21L, newest.get("notesObject"));
        assertEquals("2026-11-05T11:30:00.000+01:00", newest.get("timestamp"));
        Map<String, Object> row = map(newest.get("row"));
        assertEquals(20L, row.get("id"));
        assertEquals("Total", row.get("name"));
        assertEquals("Data", row.get("className"));
        assertEquals("Data / Electricity", row.get("path"));
        assertEquals(true, row.get("readable"));
        assertEquals(2L, map(newest.get("site")).get("id"));
        assertEquals("Site A", map(newest.get("site")).get("name"));

        Map<String, Object> clean = notes.get(1);
        assertNull("the User sample is at another time", clean.get("user"));
        assertEquals("Data / Electricity / Total", map(clean.get("row")).get("path"));

        Map<String, Object> hiddenRow = map(notes.get(2).get("row"));
        assertEquals(30L, hiddenRow.get("id"));
        assertEquals(false, hiddenRow.get("readable"));
        assertNull(hiddenRow.get("name"));
        assertNull(hiddenRow.get("path"));
        assertEquals(2L, map(notes.get(2).get("site")).get("id"));

        Map<String, Object> first = notes.get(3);
        assertEquals("Peak load at start of compressor 2", first.get("text"));
        assertEquals(Collections.singletonList("4"), first.get("tags"));
        assertEquals("Demo", first.get("user"));

        Map<String, Object> lone = notes.get(4);
        assertNull(lone.get("site"));
        assertNull(map(lone.get("row")).get("path"));

        List<Map<String, Object>> tags = list(json.get("tags"));
        assertEquals(15, tags.size());
        assertEquals("Ereignis", tags.get(4).get("name"));
        assertEquals("Something else", tags.get(14).get("id"));
        assertEquals("Something else", tags.get(14).get("name"));
        MAPPER.writeValueAsString(json);
    }

    @Test
    public void notesAreTruncated() throws Exception {
        TestSamples samples = new TestSamples();
        DateTime start = new DateTime(2026, 11, 1, 0, 0, ZONE);
        for (int i = 0; i < ResourceWebNotes.MAX_NOTES + 1; i++) samples.add(21, "Value", start.plusMinutes(i), "n" + i);
        Map<String, Object> json = ResourceWebNotes.notesJson(lookup(), classes(), samples, start, start.plusDays(10),
                ZONE, "en");
        assertEquals(true, json.get("truncated"));
        List<Map<String, Object>> notes = list(json.get("notes"));
        assertEquals(ResourceWebNotes.MAX_NOTES, notes.size());
        assertEquals("n" + ResourceWebNotes.MAX_NOTES, notes.get(0).get("text"));
    }

    @Test
    public void rowNotesIncludeTheRawParentOfCleanData() throws Exception {
        Set<String> clean = classes().withHeirs(ResourceWebNotes.CLASS_CLEAN_DATA);
        DateTime from = new DateTime(2026, 11, 1, 0, 0, ZONE);
        DateTime to = new DateTime(2026, 11, 30, 0, 0, ZONE);
        List<Map<String, Object>> notes = ResourceWebNotes.rowNotes(25, from, to, lookup(), clean, samples());
        assertEquals(3, notes.size());
        assertEquals(T1.getMillis(), notes.get(0).get("timestamp"));
        assertEquals("Demo", notes.get(0).get("user"));
        assertEquals("Clean note", notes.get(1).get("text"));
        assertEquals(Arrays.asList("4", "2", "Something else"), notes.get(2).get("tags"));

        /* the raw row: only its own notes (not those of the clean data below it, not hidden or deleted ones) */
        assertEquals(2, ResourceWebNotes.rowNotes(20, from, to, lookup(), clean, samples()).size());
        assertTrue(ResourceWebNotes.rowNotes(4, from, to, lookup(), clean, samples()).isEmpty());
    }

    @Test
    public void rowNotesAreLimited() throws Exception {
        TestSamples samples = new TestSamples();
        DateTime start = new DateTime(2026, 11, 1, 0, 0, ZONE);
        for (int i = 0; i < ResourceWebNotes.MAX_ROW_NOTES + 5; i++) {
            samples.add(21, "Value", start.plusMinutes(2 * i), "a" + i);
            samples.add(26, "Value", start.plusMinutes(2 * i + 1), "b" + i);
        }
        List<Map<String, Object>> notes = ResourceWebNotes.rowNotes(25, start, start.plusDays(1), lookup(),
                classes().withHeirs(ResourceWebNotes.CLASS_CLEAN_DATA), samples);
        assertEquals(ResourceWebNotes.MAX_ROW_NOTES, notes.size());
        assertEquals("a0", notes.get(0).get("text"));
        assertEquals("b0", notes.get(1).get("text"));
    }

    // ---------------------------------------------------------------- samples of a row

    @Test
    public void valueTypeOnlyForReadableRowsWithValue() {
        assertNotNull(ResourceWebNotes.valueType(lookup(), classes(), 20));
        assertNotNull(ResourceWebNotes.valueType(lookup(), classes(), 25));
        assertNull(ResourceWebNotes.valueType(lookup(), classes(), 30));
        assertNull(ResourceWebNotes.valueType(lookup(), classes(), 4));
        assertNull(ResourceWebNotes.valueType(lookup(), classes(), 51));
        assertNull(ResourceWebNotes.valueType(lookup(), classes(), 999));
        assertTrue(ResourceWebNotes.isText(ResourceWebNotes.valueType(lookup(), classes(), 21)));
        assertFalse(ResourceWebNotes.isText(ResourceWebNotes.valueType(lookup(), classes(), 20)));
    }

    @Test
    public void samplesJsonConvertsAndTruncates() {
        List<JsonSample> samples = Arrays.asList(sample(T1, "12.5"), sample(T2, "NaN"), sample(T3, "13"));
        Map<String, Object> json = ResourceWebNotes.samplesJson("kWh", samples, false);
        assertEquals("kWh", json.get("unit"));
        assertEquals(false, json.get("truncated"));
        @SuppressWarnings("unchecked")
        List<Object[]> list = (List<Object[]>) json.get("samples");
        assertEquals(3, list.size());
        assertEquals(T1.getMillis(), list.get(0)[0]);
        assertEquals(12.5, list.get(0)[1]);
        assertNull(list.get(1)[1]);

        @SuppressWarnings("unchecked")
        List<Object[]> text = (List<Object[]>) ResourceWebNotes.samplesJson(null, samples, true).get("samples");
        assertEquals("13", text.get(2)[1]);

        List<JsonSample> many = new ArrayList<>();
        for (int i = 0; i < ResourceWebNotes.MAX_SAMPLES + 1; i++) many.add(sample(T1.plusMinutes(i), String.valueOf(i)));
        Map<String, Object> truncated = ResourceWebNotes.samplesJson("kWh", many, false);
        assertEquals(true, truncated.get("truncated"));
        assertEquals(ResourceWebNotes.MAX_SAMPLES, ((List<?>) truncated.get("samples")).size());
    }

    // ---------------------------------------------------------------- navigation and analyses

    @Test
    public void moduleCountsCountReadableNotesObjects() {
        assertEquals(1, ResourceWebNotes.moduleCounts(Arrays.asList(ROW, ROW_NOTES, BIN_NOTES)).get("notes"));
        assertEquals(1, ResourceWebModules.moduleCounts(Arrays.asList(ROW, ROW_NOTES, BIN_NOTES)).get("notes"));
    }

    @Test
    public void analysisDataHasNotesExceptForPieRows() throws Exception {
        AnalysisResult result = new AnalysisResult();
        result.setFrom(new DateTime(2026, 11, 1, 0, 0, ZONE));
        result.setTo(new DateTime(2026, 11, 30, 0, 0, ZONE));
        ChartResult chart = new ChartResult(0);
        RowResult line = new RowResult("0-20-Value", RowStatus.OK, null);
        line.setSamples(Collections.singletonList(new ChartSample(T1, 1.0, null)));
        chart.getRows().add(line);
        RowResult pie = new RowResult("0-25-Value", RowStatus.OK, null);
        pie.setPieValue(2.0);
        chart.getRows().add(pie);
        result.getCharts().add(chart);

        Map<String, List<Map<String, Object>>> notes = new HashMap<>();
        notes.put("0-20-Value", ResourceWebNotes.rowNotes(20, result.getFrom(), result.getTo(), lookup(),
                classes().withHeirs(ResourceWebNotes.CLASS_CLEAN_DATA), samples()));
        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(ResourceWebAnalysis.dataJson(result, notes)));
        JsonNode rowNotes = json.get("charts").get(0).get("rows").get(0).get("notes");
        assertEquals(2, rowNotes.size());
        assertEquals(T1.getMillis(), rowNotes.get(0).get("timestamp").asLong());
        assertEquals("4", rowNotes.get(0).get("tags").get(0).asText());
        assertFalse(json.get("charts").get(0).get("rows").get(1).has("notes"));
    }
}
