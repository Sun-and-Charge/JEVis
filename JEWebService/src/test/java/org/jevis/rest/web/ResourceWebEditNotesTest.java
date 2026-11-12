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
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

/**
 * Write endpoints for notes (stage E1) and the edit fields of the notes answers.
 */
public class ResourceWebEditNotesTest {

    private static final String NOTES = ResourceWebNotes.CLASS_DATA_NOTES;
    private static final DateTime T1 = new DateTime(2026, 11, 5, 10, 15, ZONE);
    private static final DateTime T2 = new DateTime(2026, 11, 6, 9, 0, ZONE);

    /* organisation 1 > site 2 > folder 3 > row 20 "Total" > notes 21, clean data 25;
       folder 3 > row 30 "Hall" (no notes) > clean data 35; folder 3 > hidden row 40; folder 3 > row 50 in the bin */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        org.jevis.commons.ws.json.JsonObject bin = obj(50, "Old", "Data", 3);
        bin.setDeleteTS("2026-11-01T00:00:00.000+01:00");
        store.add(obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(2, "Site A", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(3, "Data", "Data Directory", 2),
                obj(20, "Total", "Data", 3),
                obj(21, "Total Notes", NOTES, 20),
                obj(25, "Total clean", "Clean Data", 20),
                obj(30, "Hall", "Data", 3),
                obj(35, "Hall clean", "Clean Data", 30),
                obj(40, "Hidden", "Data", 3),
                bin);
        store.hidden.add(40L);
        store.type(ResourceWebNavigation.CLASS_BUILDING, null);
        store.type("Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE);
        store.type("Clean Data", "Data", "Value", JEVisConstants.PrimitiveType.DOUBLE);
        store.type(NOTES, null, "Value", JEVisConstants.PrimitiveType.STRING, "Tag", JEVisConstants.PrimitiveType.STRING,
                "User", JEVisConstants.PrimitiveType.STRING);
        store.sample(20, "Value", T1, "12.5", "raw");
        store.sample(20, "Value", T2, "13.5", "raw,userNotes");
        store.sample(21, "Value", T2, "Existing note", "n");
        store.sample(21, "Tag", T2, "4;Something else", null);
        store.sample(21, "User", T2, "someone", null);
        return store;
    }

    private static String body(DateTime time, String text, String tags) {
        return "{\"timestamp\":\"" + WebEdit.iso(time.getMillis(), ZONE) + "\",\"text\":\"" + text + "\""
                + (tags != null ? ",\"tags\":" + tags : "") + "}";
    }

    private static String existingVersion() {
        return ResourceWebNotes.version(new ResourceWebNotes.Note(21, T2.getMillis(), "Existing note",
                Arrays.asList("4", "Something else"), "someone"));
    }

    // ---------------------------------------------------------------- create

    @Test
    public void createOnTheExistingNotesObject() throws Exception {
        FakeEditStore store = store();
        store.write.add(21L);
        store.write.add(20L);
        String body = "{\"timestamp\":\"" + WebEdit.iso(T1.plusSeconds(30).plusMillis(500).getMillis(), ZONE)
                + "\",\"text\":\"Meter replaced\",\"tags\":[\"4\",\"13\",\"4\"]}";
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body, ZONE));
        assertEquals(201, reply.status);
        DateTime ts = T1.plusSeconds(30);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("21-" + ts.getMillis(), json.get("id").asText());
        assertEquals("Meter replaced", json.get("text").asText());
        assertEquals("[\"4\",\"13\"]", json.get("tags").toString());
        assertEquals(FakeEditStore.ACCOUNT, json.get("user").asText());
        assertTrue(json.get("canEdit").asBoolean());
        assertFalse(json.get("canDelete").asBoolean());
        assertEquals(16, json.get("version").asText().length());
        assertEquals(20, json.get("row").get("id").asLong());
        assertEquals("Site A", json.get("site").get("name").asText());

        assertEquals("Meter replaced", store.at(21, "Value", ts).getValue());
        assertEquals("4;13", store.at(21, "Tag", ts).getValue());
        assertEquals(FakeEditStore.ACCOUNT, store.at(21, "User", ts).getValue());
        assertEquals(Arrays.asList("CREATE_SAMPLE 21:Value|1|web:note-create", "CREATE_SAMPLE 21:Tag|1|web:note-create",
                "CREATE_SAMPLE 21:User|1|web:note-create"), store.logs);
        /* no raw sample at 10:15:30: no marker */
        assertEquals("raw", store.at(20, "Value", T1).getNote());
    }

    @Test
    public void createSetsTheMarkerOnce() throws Exception {
        FakeEditStore store = store();
        store.write.add(21L);
        store.write.add(20L);
        assertEquals(201, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body(T1, "a", "[]"), ZONE)).status);
        JsonSample raw = store.at(20, "Value", T1);
        assertEquals("raw,userNotes", raw.getNote());
        assertEquals("12.5", raw.getValue());
        assertTrue(store.writes.contains("20/Value " + JEVisConstants.PrimitiveType.DOUBLE + " 12.5 | raw,userNotes"));
        assertEquals("CREATE_SAMPLE 20:Value|1|web:note-create", store.logs.get(3));
        assertEquals("", store.at(21, "Tag", T1).getValue());

        /* another notes object of the same row at the same time: marker already there */
        store.writes.clear();
        assertEquals(409, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body(T1, "b", null), ZONE)).status);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void markerOnlyWithWriteRightOnTheRow() throws Exception {
        FakeEditStore store = store();
        store.execute.add(21L);
        assertEquals(201, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body(T1, "a", null), ZONE)).status);
        assertEquals("raw", store.at(20, "Value", T1).getNote());
        assertEquals(3, store.writes.size());
    }

    @Test
    public void cleanRowStoresAtTheRawParent() throws Exception {
        FakeEditStore store = store();
        store.write.add(21L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 25, body(T1, "From clean", "[\"2\"]"), ZONE));
        assertEquals(201, reply.status);
        assertEquals("From clean", store.at(21, "Value", T1).getValue());
        assertEquals("21-" + T1.getMillis(), FakeEditStore.json(reply).get("id").asText());
    }

    @Test
    public void createCreatesTheNotesObject() throws Exception {
        FakeEditStore store = store();
        store.execute.add(30L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 35, body(T1, "New", "[\"6\"]"), ZONE));
        assertEquals(201, reply.status);
        org.jevis.commons.ws.json.JsonObject created = store.objects.get(store.objects.size() - 1);
        assertEquals("Hall Notes", created.getName());
        assertEquals(NOTES, created.getJevisClass());
        assertEquals(30, created.getParent());
        assertEquals("CREATE_OBJECT " + created.getId() + ":Hall Notes|web:note-create", store.logs.get(0));
        assertEquals("New", store.at(created.getId(), "Value", T1).getValue());
        assertEquals("6", store.at(created.getId(), "Tag", T1).getValue());
        JsonNode json = FakeEditStore.json(reply);
        assertEquals(created.getId(), json.get("notesObject").asLong());
        assertTrue(json.get("canEdit").asBoolean());
        assertEquals(30, json.get("row").get("notesTarget").asLong());
        assertTrue(json.get("row").get("canAddNote").asBoolean());
    }

    @Test
    public void createRefusedWithoutRights() throws Exception {
        FakeEditStore store = store();
        /* existing notes object: write or execute on it needed, rights on the row do not count */
        store.create.add(20L);
        store.delete.add(21L);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body(T1, "a", null), ZONE)).status);
        /* new notes object: create right alone is not enough (the samples could not be written) */
        store.create.add(30L);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 30, body(T1, "a", null), ZONE)).status);
        store.write.add(30L);
        assertEquals(201, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 30, body(T1, "a", null), ZONE)).status);
    }

    @Test
    public void createConflictAndValidation() throws Exception {
        FakeEditStore store = store();
        store.write.add(21L);
        WebEdit.Reply conflict = WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body(T2, "x", null), ZONE));
        assertEquals(409, conflict.status);
        assertEquals("Existing note", FakeEditStore.json(conflict).get("text").asText());

        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body(T1, "a", "[\"99\"]"), ZONE)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body(T1, "a", "[\"Something else\"]"), ZONE)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body(T1, "a", "\"4\""), ZONE)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, body(T1, "  ", null), ZONE)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, "{\"text\":\"a\"}", ZONE)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, 20, "{\"text\":\"a\",\"timestamp\":\"x\"}", ZONE)).status);
        for (long row : new long[]{3, 21, 40, 50, 999}) {
            assertEquals("row " + row, 404, WebEdit.call(store, s -> ResourceWebEditNotes.create(s, row, body(T1, "a", null), ZONE)).status);
        }
        assertTrue(store.writes.isEmpty());
    }

    // ---------------------------------------------------------------- update

    @Test
    public void updateKeepsCustomTagsWhenUnchanged() throws Exception {
        FakeEditStore store = store();
        store.execute.add(21L);
        String body = "{\"text\":\"Changed\",\"tags\":[\"Something else\",\"4\"],\"version\":\"" + existingVersion() + "\"}";
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditNotes.update(s, "21-" + T2.getMillis(), body, ZONE));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("Changed", json.get("text").asText());
        assertEquals(FakeEditStore.ACCOUNT, json.get("user").asText());
        assertNotEquals(existingVersion(), json.get("version").asText());
        assertEquals("Changed", store.at(21, "Value", T2).getValue());
        assertEquals("n", store.at(21, "Value", T2).getNote());
        assertEquals("4;Something else", store.at(21, "Tag", T2).getValue());
        assertEquals(FakeEditStore.ACCOUNT, store.at(21, "User", T2).getValue());
        assertEquals(Arrays.asList("CREATE_SAMPLE 21:Value|1|web:note-update", "CREATE_SAMPLE 21:User|1|web:note-update"), store.logs);
    }

    @Test
    public void updateChangesTags() throws Exception {
        FakeEditStore store = store();
        store.write.add(21L);
        String body = "{\"text\":\"Changed\",\"tags\":[\"2\"],\"version\":\"" + existingVersion() + "\"}";
        assertEquals(200, WebEdit.call(store, s -> ResourceWebEditNotes.update(s, "21-" + T2.getMillis(), body, ZONE)).status);
        assertEquals("2", store.at(21, "Tag", T2).getValue());

        String custom = "{\"text\":\"Changed\",\"tags\":[\"Other\"],\"version\":\"" + ResourceWebNotes.version(
                new ResourceWebNotes.Note(21, T2.getMillis(), "Changed", Arrays.asList("2"), FakeEditStore.ACCOUNT)) + "\"}";
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditNotes.update(s, "21-" + T2.getMillis(), custom, ZONE)).status);
    }

    @Test
    public void updateConflictRightsAndNotFound() throws Exception {
        FakeEditStore store = store();
        String id = "21-" + T2.getMillis();
        String good = "{\"text\":\"Changed\",\"version\":\"" + existingVersion() + "\"}";
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditNotes.update(s, id, good, ZONE)).status);
        store.write.add(21L);
        WebEdit.Reply conflict = WebEdit.call(store, s -> ResourceWebEditNotes.update(s, id, "{\"text\":\"Changed\",\"version\":\"0000\"}", ZONE));
        assertEquals(409, conflict.status);
        assertEquals("Existing note", FakeEditStore.json(conflict).get("text").asText());
        assertEquals(existingVersion(), FakeEditStore.json(conflict).get("version").asText());
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditNotes.update(s, id, "{\"text\":\"Changed\"}", ZONE)).status);
        for (String wrong : new String[]{"21-" + T1.getMillis(), "20-" + T2.getMillis(), "x", "21", "40-1"}) {
            assertEquals(wrong, 404, WebEdit.call(store, s -> ResourceWebEditNotes.update(s, wrong, good, ZONE)).status);
        }
        assertTrue(store.writes.isEmpty());
    }

    // ---------------------------------------------------------------- delete

    @Test
    public void deleteRemovesSamplesAndMarker() throws Exception {
        FakeEditStore store = store();
        store.execute.add(21L);
        store.write.add(20L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditNotes.delete(s, "21-" + T2.getMillis(), existingVersion(), ZONE));
        assertEquals(204, reply.status);
        assertEquals(0, store.count(21, "Value"));
        assertEquals(0, store.count(21, "Tag"));
        assertEquals(0, store.count(21, "User"));
        assertEquals("raw", store.at(20, "Value", T2).getNote());
        assertEquals("13.5", store.at(20, "Value", T2).getValue());
        assertTrue(store.logs.get(0).startsWith("DELETE_SAMPLE 21:Value|"));
        assertTrue(store.logs.get(0).endsWith("|web:note-delete"));
        assertEquals("CREATE_SAMPLE 20:Value|1|web:note-delete", store.logs.get(3));
    }

    @Test
    public void deleteRightsAndVersion() throws Exception {
        FakeEditStore store = store();
        String id = "21-" + T2.getMillis();
        store.write.add(21L);
        assertEquals("write alone is not enough to delete", 403,
                WebEdit.call(store, s -> ResourceWebEditNotes.delete(s, id, existingVersion(), ZONE)).status);
        store.delete.add(21L);
        assertEquals(409, WebEdit.call(store, s -> ResourceWebEditNotes.delete(s, id, "abc", ZONE)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditNotes.delete(s, id, null, ZONE)).status);
        assertEquals(1, store.count(21, "Value"));
        assertEquals(204, WebEdit.call(store, s -> ResourceWebEditNotes.delete(s, id, existingVersion(), ZONE)).status);
        /* no write right on the row: the marker stays */
        assertEquals("raw,userNotes", store.at(20, "Value", T2).getNote());
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditNotes.delete(s, id, existingVersion(), ZONE)).status);
    }

    // ---------------------------------------------------------------- read side

    @Test
    public void notesAnswerHasEditFields() throws Exception {
        FakeEditStore store = store();
        store.execute.add(21L);
        DateTime from = new DateTime(2026, 11, 1, 0, 0, ZONE);
        DateTime to = new DateTime(2026, 11, 30, 0, 0, ZONE);
        Map<String, Object> answer = ResourceWebNotes.notesJson(store.lookup(), store.classes(), store::samples, from, to,
                ZONE, "en", store.rights());
        JsonNode note = FakeEditStore.MAPPER.readTree(FakeEditStore.MAPPER.writeValueAsString(answer)).get("notes").get(0);
        assertTrue(note.get("canEdit").asBoolean());
        assertTrue(note.get("canDelete").asBoolean());
        assertEquals(existingVersion(), note.get("version").asText());
        assertTrue(note.get("row").get("canAddNote").asBoolean());
        assertEquals(20, note.get("row").get("notesTarget").asLong());

        /* without rights (old signature) */
        answer = ResourceWebNotes.notesJson(store.lookup(), store.classes(), store::samples, from, to, ZONE, "en");
        note = FakeEditStore.MAPPER.readTree(FakeEditStore.MAPPER.writeValueAsString(answer)).get("notes").get(0);
        assertFalse(note.get("canEdit").asBoolean());
        assertFalse(note.get("row").get("canAddNote").asBoolean());
    }

    @Test
    public void notesTargetResolution() throws Exception {
        FakeEditStore store = store();
        ResourceWebNotes.NotesTarget target = ResourceWebNotes.notesTarget(25L, store.lookup(), store.classes());
        assertEquals(20, target.row.getId());
        assertEquals(21, target.notesObject.getId());
        target = ResourceWebNotes.notesTarget(35L, store.lookup(), store.classes());
        assertEquals(30, target.row.getId());
        assertNull(target.notesObject);
        assertNull(ResourceWebNotes.notesTarget(3L, store.lookup(), store.classes()));
        assertNull(ResourceWebNotes.notesTarget(40L, store.lookup(), store.classes()));
        assertNull(ResourceWebNotes.notesTarget(50L, store.lookup(), store.classes()));
        assertFalse(ResourceWebNotes.canAddNote(target, store.rights()));
        store.execute.add(30L);
        assertTrue("execute suffices for Data Notes", ResourceWebNotes.canAddNote(target, store.rights()));
    }

    @Test
    public void analysisRowNotesHaveIdsAndRights() throws Exception {
        FakeEditStore store = store();
        store.write.add(21L);
        List<Map<String, Object>> notes = ResourceWebNotes.rowNotes(25, T1, T2, store.lookup(),
                store.classes().withHeirs(ResourceWebNotes.CLASS_CLEAN_DATA), store::samples, store.rights());
        assertEquals(1, notes.size());
        assertEquals("21-" + T2.getMillis(), notes.get(0).get("id"));
        assertEquals(Boolean.TRUE, notes.get(0).get("canEdit"));
        assertEquals(Boolean.FALSE, notes.get(0).get("canDelete"));
        assertEquals(existingVersion(), notes.get(0).get("version"));
    }
}
