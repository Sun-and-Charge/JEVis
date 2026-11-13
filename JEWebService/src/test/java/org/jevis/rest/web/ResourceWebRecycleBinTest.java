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
import org.jevis.api.JEVisException;
import org.jevis.commons.ws.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.jevis.rest.web.FakeEditStore.json;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

/**
 * The recycle bin of the web app for analyses and dashboards.
 */
public class ResourceWebRecycleBinTest {

    private static final String ANALYSIS = ResourceWebEditAnalysis.CLASS_ANALYSIS;
    private static final String ANALYSIS_DIR = ResourceWebEditAnalysis.CLASS_DIRECTORY;
    private static final String DASHBOARD = ResourceWebEditDashboard.CLASS_DASHBOARD;
    private static final String DASHBOARD_DIR = ResourceWebEditDashboard.CLASS_DIRECTORY;

    /**
     * In-memory bin: original places, restores and permanent deletes
     */
    static class FakeBin implements ResourceWebRecycleBin.Bin {
        final Map<Long, List<Long>> deletedParents = new HashMap<>();
        final List<String> restored = new ArrayList<>();
        final List<Long> deleted = new ArrayList<>();
        JEVisException failRestore;

        FakeBin from(long id, long parent) {
            deletedParents.computeIfAbsent(id, k -> new ArrayList<>()).add(parent);
            return this;
        }

        @Override
        public List<Long> deletedParents(long id) {
            return deletedParents.getOrDefault(id, Collections.emptyList());
        }

        @Override
        public void restore(JsonObject object, Long parent) throws Exception {
            if (failRestore != null) throw failRestore;
            restored.add(object.getId() + (parent != null ? " -> " + parent : ""));
        }

        @Override
        public void deleteForever(JsonObject object) {
            deleted.add(object.getId());
        }
    }

    private FakeEditStore store;
    private FakeBin bin;

    private static JsonObject deleted(JsonObject object, String time, Long by) {
        object.setDeleteTS(time.substring(0, 10));
        object.setDeleted(time);
        object.setDeletedBy(by);
        return object;
    }

    /**
     * Analyses: directory "Analyses" (1) with "Energy" (10) and the deleted "Old" (11); a hidden directory (12).
     * In the bin: "Heat" (101, from 10, by user 500), "Secret" (102, hidden), "Water" (106, deleted with 11, newest),
     * "Gas" (107, from the hidden 12, oldest); live "Power" (103); dashboard "Board" (104, from 20), data row 105.
     */
    @Before
    public void setUp() {
        store = new FakeEditStore();
        bin = new FakeBin();
        store.add(obj(1, "Analyses", ANALYSIS_DIR, 0),
                obj(10, "Energy", ANALYSIS_DIR, 1),
                deleted(obj(11, "Old", ANALYSIS_DIR, 1), "2026-11-10T09:00:00.000+01:00", 500L),
                obj(12, "Hidden", ANALYSIS_DIR, 1),
                obj(2, "Dashboards", DASHBOARD_DIR, 0),
                obj(20, "Site", DASHBOARD_DIR, 2),
                obj(500, "jane.doe", "User", 0),
                deleted(obj(101, "Heat", ANALYSIS, 0), "2026-11-11T08:00:00.000+01:00", 500L),
                deleted(obj(102, "Secret", ANALYSIS, 0), "2026-11-11T09:00:00.000+01:00", null),
                obj(103, "Power", ANALYSIS, 10),
                deleted(obj(104, "Board", DASHBOARD, 0), "2026-11-11T10:00:00.000+01:00", null),
                deleted(obj(105, "Row", "Data", 0), "2026-11-11T11:00:00.000+01:00", null),
                deleted(obj(106, "Water", ANALYSIS, 11), "2026-11-12T08:00:00.000+01:00", 500L),
                deleted(obj(107, "Gas", ANALYSIS, 0), "2026-11-01T08:00:00.000+01:00", null));
        store.hidden.addAll(Arrays.asList(12L, 102L, 500L));
        bin.from(101, 10).from(102, 10).from(104, 20).from(105, 10).from(107, 12).from(11, 1);
        store.delete.addAll(Arrays.asList(101L, 104L, 106L));
    }

    private JsonNode list(String kind) throws Exception {
        WebEdit.Reply reply = ResourceWebRecycleBin.list(store, bin, kind, "de");
        assertEquals(200, reply.status);
        return json(reply);
    }

    private JsonNode byId(JsonNode list, long id) {
        for (JsonNode entry : list) {
            if (entry.get("id").asLong() == id) return entry;
        }
        return null;
    }

    // ---------------------------------------------------------------- list

    @Test
    public void listsOnlyReadableBinObjectsOfTheKindNewestFirst() throws Exception {
        JsonNode list = list("analyses");
        List<Long> ids = new ArrayList<>();
        list.forEach(entry -> ids.add(entry.get("id").asLong()));
        assertEquals(Arrays.asList(106L, 101L, 107L), ids);

        JsonNode dashboards = list("dashboards");
        assertEquals(1, dashboards.size());
        assertEquals(104, dashboards.get(0).get("id").asLong());
        assertEquals("dashboards", dashboards.get(0).get("kind").asText());
    }

    @Test
    public void entryHasPlaceUserAndRights() throws Exception {
        JsonNode heat = byId(list("analyses"), 101);
        assertEquals("Heat", heat.get("name").asText());
        assertEquals("analyses", heat.get("kind").asText());
        assertEquals("2026-11-11T08:00:00.000+01:00", heat.get("deletedAt").asText());
        assertEquals("jane.doe", heat.get("deletedBy").asText());
        assertEquals("Analyses / Energy", heat.get("path").asText());
        assertFalse(heat.get("parentGone").asBoolean());
        assertTrue(heat.get("canRestore").asBoolean());
        assertTrue(heat.get("canDeleteForever").asBoolean());

        JsonNode gas = byId(list("analyses"), 107);
        assertTrue("hidden place is not named", gas.get("path").isNull());
        assertFalse(gas.get("parentGone").asBoolean());
        assertTrue(gas.get("deletedBy").isNull());
        assertFalse(gas.get("canRestore").asBoolean());
        assertFalse(gas.get("canDeleteForever").asBoolean());
    }

    @Test
    public void deletedWithItsDirectoryHasNoPlace() throws Exception {
        JsonNode water = byId(list("analyses"), 106);
        assertTrue(water.get("parentGone").asBoolean());
        assertTrue(water.get("path").isNull());
    }

    @Test
    public void originalPlaceInTheBinIsGone() throws Exception {
        bin.from(101, 11);
        bin.deletedParents.get(101L).remove(Long.valueOf(10));
        assertTrue(byId(list("analyses"), 101).get("parentGone").asBoolean());
    }

    @Test
    public void unknownKindIsRejected() throws Exception {
        assertEquals(400, ResourceWebRecycleBin.list(store, bin, "rows", "de").status);
        assertEquals(400, ResourceWebRecycleBin.list(store, bin, null, "de").status);
    }

    @Test
    public void localNameIsUsed() throws Exception {
        store.object(101).setI18n(Collections.singletonList(i18n("de", "Wärme")));
        assertEquals("Wärme", byId(list("analyses"), 101).get("name").asText());
    }

    private static org.jevis.commons.ws.json.JsonI18n i18n(String language, String value) {
        org.jevis.commons.ws.json.JsonI18n i18n = new org.jevis.commons.ws.json.JsonI18n();
        i18n.setKey(language);
        i18n.setValue(value);
        return i18n;
    }

    // ---------------------------------------------------------------- restore

    @Test
    public void restoresToTheOriginalPlace() throws Exception {
        WebEdit.Reply reply = ResourceWebRecycleBin.restore(store, bin, 101, null, "de");
        assertEquals(200, reply.status);
        assertEquals(Collections.singletonList("101"), bin.restored);
        JsonNode json = json(reply);
        assertEquals(101, json.get("id").asLong());
        assertEquals("analyses", json.get("kind").asText());
        assertEquals(10, json.get("parentId").asLong());
        assertEquals("Analyses / Energy", json.get("path").asText());
        assertEquals(Collections.singletonList("UPDATE_OBJECT restore 101:Heat|web:recycle-restore"), store.logs);
    }

    @Test
    public void emptyBodyRestoresToTheOriginalPlace() throws Exception {
        assertEquals(200, ResourceWebRecycleBin.restore(store, bin, 104, "  ", "de").status);
        assertEquals(Collections.singletonList("104"), bin.restored);
    }

    @Test
    public void goneOriginalPlaceNeedsATarget() throws Exception {
        WebEdit.Reply reply = ResourceWebRecycleBin.restore(store, bin, 106, "{}", "de");
        assertEquals(409, reply.status);
        assertTrue(json(reply).get("parentGone").asBoolean());
        assertTrue(bin.restored.isEmpty());
        assertTrue(store.logs.isEmpty());
    }

    @Test
    public void restoresToAChosenDirectory() throws Exception {
        store.create.add(10L);
        WebEdit.Reply reply = ResourceWebRecycleBin.restore(store, bin, 106, "{\"parent\":10}", "de");
        assertEquals(200, reply.status);
        assertEquals(Collections.singletonList("106 -> 10"), bin.restored);
        assertEquals(10, json(reply).get("parentId").asLong());
        assertEquals("Analyses / Energy", json(reply).get("path").asText());
    }

    @Test
    public void chosenDirectoryNeedsTheCreateRight() throws Exception {
        assertEquals(403, ResourceWebRecycleBin.restore(store, bin, 106, "{\"parent\":10}", "de").status);
        assertTrue(bin.restored.isEmpty());
    }

    @Test
    public void chosenDirectoryMustBeALiveReadableDirectoryOfTheKind() throws Exception {
        store.create.addAll(Arrays.asList(11L, 12L, 20L, 103L));
        for (long parent : new long[]{11, 12, 20, 103, 999}) {
            assertEquals("parent " + parent, 404,
                    ResourceWebRecycleBin.restore(store, bin, 106, "{\"parent\":" + parent + "}", "de").status);
        }
        assertTrue(bin.restored.isEmpty());
    }

    @Test
    public void restoreNeedsTheDeleteRight() throws Exception {
        assertEquals(403, ResourceWebRecycleBin.restore(store, bin, 107, null, "de").status);
        assertTrue(bin.restored.isEmpty());
    }

    @Test
    public void restoreOnlyForReadableBinObjectsOfTheTwoClasses() throws Exception {
        store.delete.addAll(Arrays.asList(102L, 103L, 105L, 11L));
        for (long id : new long[]{102, 103, 105, 11, 999}) {
            assertEquals("object " + id, 404, ResourceWebRecycleBin.restore(store, bin, id, null, "de").status);
        }
        assertTrue(bin.restored.isEmpty());
    }

    @Test
    public void conflictOfTheDatabaseIsParentGone() throws Exception {
        bin.failRestore = new JEVisException("The original parent no longer exists, choose a target", 409);
        WebEdit.Reply reply = ResourceWebRecycleBin.restore(store, bin, 101, null, "de");
        assertEquals(409, reply.status);
        assertTrue(json(reply).get("parentGone").asBoolean());
        assertTrue(store.logs.isEmpty());
    }

    @Test
    public void invalidParentIsRejected() throws Exception {
        assertEquals(400, WebEdit.call(store, s -> ResourceWebRecycleBin.restore(s, bin, 101, "{\"parent\":\"x\"}", "de")).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebRecycleBin.restore(s, bin, 101, "[1]", "de")).status);
        assertTrue(bin.restored.isEmpty());
    }

    // ---------------------------------------------------------------- delete forever

    @Test
    public void deletesBinObjectsForGood() throws Exception {
        WebEdit.Reply reply = ResourceWebRecycleBin.delete(store, bin, 104);
        assertEquals(204, reply.status);
        assertEquals(Collections.singletonList(104L), bin.deleted);
        assertEquals(Collections.singletonList("DELETE_OBJECT 104:Board|web:recycle-delete"), store.logs);
    }

    @Test
    public void deleteForeverNeedsTheDeleteRight() throws Exception {
        assertEquals(403, ResourceWebRecycleBin.delete(store, bin, 107).status);
        assertTrue(bin.deleted.isEmpty());
        assertTrue(store.logs.isEmpty());
    }

    @Test
    public void deleteForeverOnlyForBinObjectsOfTheTwoClasses() throws Exception {
        store.delete.addAll(Arrays.asList(102L, 103L, 105L, 11L));
        for (long id : new long[]{102, 103, 105, 11, 999}) {
            assertEquals("object " + id, 404, ResourceWebRecycleBin.delete(store, bin, id).status);
        }
        assertTrue(bin.deleted.isEmpty());
    }
}
