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

import jakarta.ws.rs.core.Response;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonClassRelationship;
import org.jevis.commons.ws.json.JsonJEVisClass;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonFactory;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebReportsTest {

    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final String REPORT = ResourceWebReports.CLASS_REPORT;
    private static final String PERIODIC = ResourceWebReports.CLASS_PERIODIC_REPORT;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // ---------------------------------------------------------------- objects

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    /* Organisation 1 > site 2 "Musterwerk" > folder 3 "Berichte" > 100 "Monatsbericht Energie" (periodic),
       101 "Abweichungen" (plain report, no output), 102 hidden, 103 recycle bin, 104 "Wochenbericht" (periodic);
       site 2 > 105 "Tagesbericht" directly below the site; site 6 "Anderes Werk" > 107 "Jahresbericht";
       108 "Ohne Standort" below the organisation; 109 is no report */
    private static final JsonObject ORG = obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
    private static final JsonObject SITE = obj(2, "Musterwerk", ResourceWebNavigation.CLASS_BUILDING, 1);
    private static final JsonObject FOLDER = obj(3, "Berichte", "Report Directory", 2);
    private static final JsonObject OTHER_SITE = obj(6, "Anderes Werk", ResourceWebNavigation.CLASS_BUILDING, 1);
    private static final JsonObject MONTHLY = obj(100, "Monatsbericht Energie", PERIODIC, 3);
    private static final JsonObject PLAIN = obj(101, "Abweichungen", REPORT, 3);
    private static final JsonObject HIDDEN = obj(102, "Geheimbericht", PERIODIC, 3);
    private static final JsonObject BIN = obj(103, "Alter Bericht", PERIODIC, 3);
    private static final JsonObject WEEKLY = obj(104, "wochenbericht", PERIODIC, 3);
    private static final JsonObject DAILY = obj(105, "Tagesbericht", PERIODIC, 2);
    private static final JsonObject YEARLY = obj(107, "Jahresbericht", PERIODIC, 6);
    private static final JsonObject LONE = obj(108, "Ohne Standort", PERIODIC, 1);
    private static final JsonObject NO_REPORT = obj(109, "Daten", "Data Directory", 2);

    static {
        BIN.setDeleteTS("2026-11-01T00:00:00.000+01:00");
    }

    private static final List<JsonObject> ALL = Arrays.asList(ORG, SITE, FOLDER, OTHER_SITE, MONTHLY, PLAIN, HIDDEN,
            BIN, WEEKLY, DAILY, YEARLY, LONE, NO_REPORT);

    private static ResourceWebModules.Lookup lookup() {
        List<JsonObject> readable = new ArrayList<>(ALL);
        readable.remove(HIDDEN);
        Map<Long, Long> parents = new HashMap<>();
        for (JsonObject o : ALL) parents.put(o.getId(), o.getParent());
        return new ResourceWebModules.Lookup(ALL, readable, parents);
    }

    private static JsonJEVisClass jevisClass(String name, String inherits) {
        JsonJEVisClass jevisClass = new JsonJEVisClass();
        jevisClass.setName(name);
        List<JsonClassRelationship> relationships = new ArrayList<>();
        if (inherits != null) {
            JsonClassRelationship rel = new JsonClassRelationship();
            rel.setStart(name);
            rel.setEnd(inherits);
            rel.setType(JEVisConstants.ClassRelationship.INHERIT);
            relationships.add(rel);
        }
        jevisClass.setRelationships(relationships);
        jevisClass.setTypes(new ArrayList<>());
        return jevisClass;
    }

    private static ResourceWebRegisters.Classes classes() {
        Map<String, JsonJEVisClass> map = new HashMap<>();
        for (JsonJEVisClass c : Arrays.asList(jevisClass(ResourceWebNavigation.CLASS_BUILDING, null),
                jevisClass(REPORT, null), jevisClass(PERIODIC, REPORT))) {
            map.put(c.getName(), c);
        }
        return new ResourceWebRegisters.Classes(map);
    }

    // ---------------------------------------------------------------- samples and files

    private static JsonSample sample(DateTime time, String value) {
        JsonSample sample = new JsonSample();
        sample.setTs(ISODateTimeFormat.dateTime().print(time));
        sample.setValue(value);
        return sample;
    }

    private static class TestStore implements ResourceWebReports.Store {
        final Map<String, List<JsonSample>> samples = new HashMap<>();
        final Map<String, byte[]> files = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        TestStore add(long id, String attribute, DateTime time, String value) {
            samples.computeIfAbsent(id + "/" + attribute, k -> new ArrayList<>()).add(sample(time, value));
            return this;
        }

        TestStore file(long id, String attribute, DateTime time, String name, String content) {
            add(id, attribute, time, name);
            files.put(id + "/" + attribute + "/" + time.getMillis(), content.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        @Override
        public List<JsonSample> all(long id, String attribute) {
            calls.add("all " + id + "/" + attribute);
            List<JsonSample> result = new ArrayList<>(samples.getOrDefault(id + "/" + attribute, Collections.emptyList()));
            result.sort(Comparator.comparingLong(s -> ResourceWebNotes.millis(s.getTs())));
            return result;
        }

        @Override
        public JsonSample latest(long id, String attribute) {
            calls.add("latest " + id + "/" + attribute);
            List<JsonSample> all = samples.getOrDefault(id + "/" + attribute, Collections.emptyList());
            return all.stream().max(Comparator.comparingLong(s -> ResourceWebNotes.millis(s.getTs()))).orElse(null);
        }

        @Override
        public JsonSample at(long id, String attribute, long millis) {
            calls.add("at " + id + "/" + attribute);
            for (JsonSample sample : samples.getOrDefault(id + "/" + attribute, Collections.emptyList())) {
                if (ResourceWebNotes.millis(sample.getTs()) == millis) return sample;
            }
            return null;
        }

        @Override
        public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
            calls.add("file " + id + "/" + attribute);
            return files.get(id + "/" + attribute + "/" + ResourceWebNotes.millis(sample.getTs()));
        }
    }

    private static final DateTime OCT = new DateTime(2026, 10, 1, 6, 0, 12, ZONE);
    private static final DateTime NOV = new DateTime(2026, 11, 1, 6, 0, 12, ZONE);
    private static final DateTime OLD = new DateTime(2026, 1, 1, 0, 0, ZONE);

    private static TestStore store() {
        TestStore store = new TestStore();
        store.add(MONTHLY.getId(), ResourceWebReports.ATTRIBUTE_ENABLED, OLD, "1");
        store.add(MONTHLY.getId(), ResourceWebReports.ATTRIBUTE_SCHEDULE, OLD, "MONTHLY");
        store.file(MONTHLY.getId(), ResourceWebReports.ATTRIBUTE_PDF, OCT, "Monatsbericht Energie_202609.pdf", "%PDF-sep");
        store.file(MONTHLY.getId(), ResourceWebReports.ATTRIBUTE_PDF, NOV, "Monatsbericht Energie_202610.pdf", "%PDF-oct");
        /* XLSX 1 s before the newer PDF (matches), 61 s after the older one (does not match) */
        store.file(MONTHLY.getId(), ResourceWebReports.ATTRIBUTE_XLSX, NOV.minusSeconds(1), "Monatsbericht Energie_202610.xlsx", "PK-oct");
        store.file(MONTHLY.getId(), ResourceWebReports.ATTRIBUTE_XLSX, OCT.plusSeconds(61), "Monatsbericht Energie_202609.xlsx", "PK-sep");
        /* a template must never be given */
        store.file(MONTHLY.getId(), "Template", NOV, "template.xlsx", "PK-template");

        store.add(PLAIN.getId(), ResourceWebReports.ATTRIBUTE_ENABLED, OLD, "0");
        store.add(PLAIN.getId(), ResourceWebReports.ATTRIBUTE_SCHEDULE, OLD, "DAILY");
        store.file(HIDDEN.getId(), ResourceWebReports.ATTRIBUTE_PDF, NOV, "geheim.pdf", "%PDF-secret");
        store.file(BIN.getId(), ResourceWebReports.ATTRIBUTE_PDF, NOV, "alt.pdf", "%PDF-old");
        store.add(WEEKLY.getId(), ResourceWebReports.ATTRIBUTE_ENABLED, OLD, "true");
        store.add(WEEKLY.getId(), ResourceWebReports.ATTRIBUTE_SCHEDULE, OLD, "WEEKLY");
        store.file(WEEKLY.getId(), ResourceWebReports.ATTRIBUTE_PDF, NOV, "Wochenbericht.pdf", "%PDF-week");
        /* PDF without stored name */
        store.file(DAILY.getId(), ResourceWebReports.ATTRIBUTE_PDF, NOV, "", "%PDF-day");
        /* stored name with a path */
        store.file(YEARLY.getId(), ResourceWebReports.ATTRIBUTE_PDF, NOV, "../../etc/passwd", "secret");
        return store;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> reports(TestStore store) {
        return (List<Map<String, Object>>) ResourceWebReports.reportsJson(lookup(), classes(), store, ZONE).get("reports");
    }

    private static Map<String, Object> byId(List<Map<String, Object>> reports, long id) {
        for (Map<String, Object> r : reports) if (((Long) r.get("id")) == id) return r;
        return null;
    }

    // ---------------------------------------------------------------- list

    @Test
    public void listOnlyReadableNotDeletedReportsSorted() {
        List<Map<String, Object>> reports = reports(store());
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> r : reports) ids.add(r.get("id"));
        /* "Anderes Werk" before "Musterwerk"; in Musterwerk without path first, then "Berichte" by name
           (case-insensitive); reports without site last */
        assertEquals(Arrays.asList(107L, 105L, 101L, 100L, 104L, 108L), ids);
        assertNull(byId(reports, HIDDEN.getId()));
        assertNull(byId(reports, BIN.getId()));
        assertNull(byId(reports, NO_REPORT.getId()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listEntry() {
        TestStore store = store();
        Map<String, Object> monthly = byId(reports(store), MONTHLY.getId());
        assertEquals("Monatsbericht Energie", monthly.get("name"));
        assertEquals(PERIODIC, monthly.get("className"));
        assertEquals("Berichte", monthly.get("path"));
        Map<String, Object> site = (Map<String, Object>) monthly.get("site");
        assertEquals(2L, site.get("id"));
        assertEquals("Musterwerk", site.get("name"));
        assertEquals(true, monthly.get("enabled"));
        assertEquals("MONTHLY", monthly.get("schedule"));
        assertEquals(2, monthly.get("count"));
        Map<String, Object> latest = (Map<String, Object>) monthly.get("latest");
        assertEquals(ISODateTimeFormat.dateTime().print(NOV), latest.get("timestamp"));
        assertEquals(true, latest.get("pdf"));
        assertEquals(true, latest.get("xlsx"));
        assertEquals("Monatsbericht Energie_202610.pdf", latest.get("name"));

        /* no bytes are read for the list */
        for (String call : store.calls) assertFalse(call, call.startsWith("file"));
    }

    @Test
    public void listWithoutOutputAndPlainReport() {
        List<Map<String, Object>> reports = reports(store());
        Map<String, Object> plain = byId(reports, PLAIN.getId());
        assertNull(plain.get("latest"));
        assertEquals(0, plain.get("count"));
        assertEquals(false, plain.get("enabled"));
        /* a plain report has no schedule, even with a stray sample */
        assertNull(plain.get("schedule"));

        Map<String, Object> daily = byId(reports, DAILY.getId());
        assertNull(daily.get("path"));
        assertEquals(false, daily.get("enabled"));
        assertNull(daily.get("schedule"));

        Map<String, Object> lone = byId(reports, LONE.getId());
        assertNull(lone.get("site"));
        assertNull(lone.get("path"));
    }

    // ---------------------------------------------------------------- XLSX matching

    @Test
    public void xlsxWithinOneMinute() {
        TreeMap<Long, JsonSample> xlsx = new TreeMap<>();
        long pdf = NOV.getMillis();
        xlsx.put(pdf - 60_000, null);
        assertEquals(Long.valueOf(pdf - 60_000), ResourceWebReports.matchingXlsx(xlsx, pdf));
        xlsx.clear();
        xlsx.put(pdf + 59_999, null);
        assertEquals(Long.valueOf(pdf + 59_999), ResourceWebReports.matchingXlsx(xlsx, pdf));
        xlsx.clear();
        xlsx.put(pdf - 61_000, null);
        xlsx.put(pdf + 61_000, null);
        assertNull(ResourceWebReports.matchingXlsx(xlsx, pdf));
        xlsx.put(pdf + 60_000, null);
        assertNull(ResourceWebReports.matchingXlsx(xlsx, pdf));
        assertNull(ResourceWebReports.matchingXlsx(new TreeMap<>(), pdf));
    }

    // ---------------------------------------------------------------- files

    @Test
    @SuppressWarnings("unchecked")
    public void filesNewestFirst() throws Exception {
        Map<String, Object> result = ResourceWebReports.filesJson(lookup(), classes(), store(), MONTHLY.getId(), ZONE);
        assertEquals(MONTHLY.getId(), result.get("id"));
        assertEquals("Monatsbericht Energie", result.get("name"));
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.get("files");
        assertEquals(2, files.size());
        assertEquals(ISODateTimeFormat.dateTime().print(NOV), files.get(0).get("timestamp"));
        assertEquals("Monatsbericht Energie_202610.pdf", files.get(0).get("name"));
        assertEquals(ISODateTimeFormat.dateTime().print(NOV.minusSeconds(1)), files.get(0).get("xlsx"));
        assertEquals(ISODateTimeFormat.dateTime().print(OCT), files.get(1).get("timestamp"));
        /* the XLSX 61 s after the older PDF does not belong to it */
        assertNull(files.get(1).get("xlsx"));
    }

    @Test
    public void filesOfUnreadableDeletedOrNoReport() throws Exception {
        for (JsonObject o : Arrays.asList(HIDDEN, BIN, NO_REPORT)) {
            assertNull(o.getName(), ResourceWebReports.filesJson(lookup(), classes(), store(), o.getId(), ZONE));
        }
        assertNull(ResourceWebReports.filesJson(lookup(), classes(), store(), 4711L, ZONE));
    }

    private static String body(Response response) {
        return new String((byte[]) response.getEntity(), StandardCharsets.UTF_8);
    }

    @Test
    public void filePdfNewestAndByTimestamp() throws Exception {
        Response newest = ResourceWebReports.fileResponse(lookup(), classes(), store(), MONTHLY.getId(), "pdf", null, false);
        assertEquals(200, newest.getStatus());
        assertEquals("%PDF-oct", body(newest));
        assertEquals("application/pdf", newest.getMediaType().toString());
        assertEquals("attachment; filename=\"Monatsbericht Energie_202610.pdf\"; filename*=UTF-8''Monatsbericht%20Energie_202610.pdf",
                newest.getHeaderString("Content-Disposition"));
        assertEquals("nosniff", newest.getHeaderString("X-Content-Type-Options"));
        assertEquals("private, no-store", newest.getHeaderString("Cache-Control"));
        assertNotNull(newest.getHeaderString("Content-Security-Policy"));

        Response older = ResourceWebReports.fileResponse(lookup(), classes(), store(), MONTHLY.getId(), "pdf",
                ResourceWebReports.timestamp(ISODateTimeFormat.dateTime().print(OCT)), true);
        assertEquals("%PDF-sep", body(older));
        assertTrue(older.getHeaderString("Content-Disposition").startsWith("inline; "));
        assertNull(older.getHeaderString("Content-Security-Policy"));
    }

    @Test
    public void fileXlsx() throws Exception {
        Response xlsx = ResourceWebReports.fileResponse(lookup(), classes(), store(), MONTHLY.getId(), "xlsx",
                ResourceWebReports.timestamp(ISODateTimeFormat.dateTime().print(NOV.minusSeconds(1).withZone(DateTimeZone.UTC))),
                true);
        assertEquals(200, xlsx.getStatus());
        assertEquals("PK-oct", body(xlsx));
        assertEquals(ResourceWebReports.TYPE_XLSX, xlsx.getMediaType().toString());
        /* only PDFs are shown inline */
        assertTrue(xlsx.getHeaderString("Content-Disposition").startsWith("attachment; "));

        Response newest = ResourceWebReports.fileResponse(lookup(), classes(), store(), MONTHLY.getId(), "xlsx", null, false);
        assertEquals("PK-oct", body(newest));
    }

    @Test
    public void fileNotFound() throws Exception {
        TestStore store = store();
        /* not readable, recycle bin, no report, unknown */
        for (long id : new long[]{HIDDEN.getId(), BIN.getId(), NO_REPORT.getId(), 4711L}) {
            assertEquals(404, ResourceWebReports.fileResponse(lookup(), classes(), store, id, "pdf", null, false).getStatus());
        }
        for (String call : store.calls) assertFalse(call, call.startsWith("file"));
        /* no output, no output at the time */
        assertEquals(404, ResourceWebReports.fileResponse(lookup(), classes(), store, PLAIN.getId(), "pdf", null, false).getStatus());
        assertEquals(404, ResourceWebReports.fileResponse(lookup(), classes(), store, MONTHLY.getId(), "pdf",
                NOV.plusSeconds(1).getMillis(), false).getStatus());
        /* the PDF time is no XLSX time */
        assertEquals(404, ResourceWebReports.fileResponse(lookup(), classes(), store, MONTHLY.getId(), "xlsx",
                NOV.getMillis(), false).getStatus());
    }

    @Test
    public void fileWithoutStoredNameUsesReportName() throws Exception {
        Response response = ResourceWebReports.fileResponse(lookup(), classes(), store(), DAILY.getId(), "pdf", null, false);
        assertEquals(200, response.getStatus());
        assertTrue(response.getHeaderString("Content-Disposition").contains("filename=\"Tagesbericht.pdf\""));
    }

    @Test
    public void fileNameNonAscii() throws Exception {
        TestStore store = store();
        DateTime time = NOV.plusDays(1);
        store.file(WEEKLY.getId(), ResourceWebReports.ATTRIBUTE_PDF, time, "Übersicht Wärme.pdf", "%PDF-heat");
        Response response = ResourceWebReports.fileResponse(lookup(), classes(), store, WEEKLY.getId(), "pdf", null, false);
        assertEquals("attachment; filename=\"_bersicht W_rme.pdf\"; filename*=UTF-8''%C3%9Cbersicht%20W%C3%A4rme.pdf",
                response.getHeaderString("Content-Disposition"));
    }

    @Test
    public void formatAndTimestampValidation() {
        assertEquals("pdf", ResourceWebReports.format(null));
        assertEquals("pdf", ResourceWebReports.format(" PDF "));
        assertEquals("xlsx", ResourceWebReports.format("xlsx"));
        for (String bad : new String[]{"docx", "template", "../pdf"}) {
            try {
                ResourceWebReports.format(bad);
                fail(bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        assertNull(ResourceWebReports.timestamp(null));
        assertNull(ResourceWebReports.timestamp(" "));
        assertEquals(Long.valueOf(NOV.getMillis()), ResourceWebReports.timestamp("2026-11-01T06:00:12.000+01:00"));
        assertEquals(Long.valueOf(NOV.getMillis()), ResourceWebReports.timestamp("2026-11-01T05:00:12Z"));
        for (String bad : new String[]{"yesterday", "2026-13-01T00:00:00Z", "1.11.2026"}) {
            try {
                ResourceWebReports.timestamp(bad);
                fail(bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void endpointAnswersBadRequestBeforeDatabase() {
        ResourceWebReports resource = new ResourceWebReports();
        assertEquals(400, resource.file(null, 100L, "docx", null, false).getStatus());
        assertEquals(400, resource.file(null, 100L, "pdf", "gestern", false).getStatus());
    }

    // ---------------------------------------------------------------- reading files from disk

    private static File stored(File dir, long id, String attribute, DateTime time, String name, String content) throws Exception {
        File attributeDir = new File(new File(dir, String.valueOf(id)), attribute);
        assertTrue(attributeDir.isDirectory() || attributeDir.mkdirs());
        File file = new File(attributeDir, DateTimeFormat.forPattern("yyyyMMddHHmmss").withZoneUTC().print(time) + "_" + name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static JsonSample fileSample(DateTime time, String name) {
        JsonSample sample = new JsonSample();
        sample.setTs(JsonFactory.sampleDTF.print(time));
        sample.setValue(name);
        return sample;
    }

    @Test
    public void sampleFileFromDisk() throws Exception {
        File dir = folder.newFolder("files");
        stored(dir, 100, ResourceWebReports.ATTRIBUTE_PDF, NOV, "Monatsbericht Energie_202610.pdf", "%PDF-oct");
        assertEquals("%PDF-oct", new String(ResourceWebAlarms.sampleFile(dir, 100, ResourceWebReports.ATTRIBUTE_PDF,
                fileSample(NOV, "Monatsbericht Energie_202610.pdf")), StandardCharsets.UTF_8));
        assertNull(ResourceWebAlarms.sampleFile(dir, 100, ResourceWebReports.ATTRIBUTE_PDF,
                fileSample(OCT, "Monatsbericht Energie_202610.pdf")));
    }

    @Test
    public void sampleFileRejectsPathTraversal() throws Exception {
        File dir = folder.newFolder("files");
        /* a file next to the attribute directory that a crafted name would reach */
        stored(dir, 100, "Template", NOV, "secret.xlsx", "PK-template");
        File outside = new File(new File(dir, "100"), "secret.txt");
        Files.write(outside.toPath(), "secret".getBytes(StandardCharsets.UTF_8));
        String prefix = DateTimeFormat.forPattern("yyyyMMddHHmmss").withZoneUTC().print(NOV) + "_";
        for (String name : new String[]{"../Template/" + prefix + "secret.xlsx", "..\\secret.txt", "/etc/passwd",
                "..", "", "  ", null, "a\u0000b.pdf", "sub/x.pdf"}) {
            assertNull(String.valueOf(name), ResourceWebAlarms.sampleFile(dir, 100, ResourceWebReports.ATTRIBUTE_PDF,
                    fileSample(NOV, name)));
        }
        assertNull(ResourceWebAlarms.sampleFile(dir, 100, "../100/Template", fileSample(NOV, "secret.xlsx")));
        assertFalse(ResourceWebAlarms.isSafeStoredName("../x"));
        assertTrue(ResourceWebAlarms.isSafeStoredName("Monatsbericht Energie_202610.pdf"));
    }

    @Test
    public void storedNameWithPathIsNotServed() throws Exception {
        /* the store gives no bytes for a name with a path (as the SQL store); the answer is 404 */
        TestStore store = new TestStore() {
            @Override
            public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
                return ResourceWebAlarms.sampleFile(folder.getRoot(), id, attribute, sample);
            }
        };
        store.add(YEARLY.getId(), ResourceWebReports.ATTRIBUTE_PDF, NOV, "../../etc/passwd");
        assertEquals(404, ResourceWebReports.fileResponse(lookup(), classes(), store, YEARLY.getId(), "pdf", null, false).getStatus());
    }

    // ---------------------------------------------------------------- navigation

    @Test
    public void moduleCount() {
        List<JsonObject> readable = new ArrayList<>(ALL);
        readable.remove(HIDDEN);
        /* 100, 101, 104, 105, 107, 108; not hidden 102, not 103 in the recycle bin */
        assertEquals(6, ResourceWebReports.moduleCounts(readable, classes()).get("reports"));
        /* without class cache only "Report" itself counts */
        assertEquals(1, ResourceWebReports.moduleCounts(readable,
                new ResourceWebRegisters.Classes(Collections.emptyMap())).get("reports"));
        assertEquals(1, ResourceWebModules.moduleCounts(Arrays.asList(SITE, PLAIN, BIN)).get("reports"));
    }
}
