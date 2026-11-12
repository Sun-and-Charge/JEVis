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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.constants.NoteConstants;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Write endpoints for notes (stage E1, see docs/WEB-API.md), written like the analysis note dialog of the Control
 * Center ({@code DataPointNoteDialog}): a note is the samples of "Value" (text), "Tag" (tag ids joined with {@code ;})
 * and "User" (account name) of a "Data Notes" object at the note's time stamp; the raw sample of the row at that time
 * gets the marker {@code ,userNotes} in its note.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditNotes {

    static final String ACTION_CREATE = "note-create";
    static final String ACTION_UPDATE = "note-update";
    static final String ACTION_DELETE = "note-delete";
    static final String NOTES_NAME_SUFFIX = " Notes";
    static final String MARKER = "," + NoteConstants.User.USER_NOTES;
    static final int MAX_TEXT_LENGTH = 10000;
    private static final Pattern NOTE_ID = Pattern.compile("^(\\d+)-(-?\\d+)$");

    // ---------------------------------------------------------------- endpoints

    @POST
    @Path("rows/{rowId}/notes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders httpHeaders, @PathParam("rowId") long rowId,
                           @QueryParam("timeZone") String timeZone, String body) {
        return WebEdit.run(httpHeaders, "add a note to " + rowId,
                store -> create(store, rowId, body, ResourceWebNotes.zone(timeZone)));
    }

    @PUT
    @Path("notes/{noteId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("noteId") String noteId,
                           @QueryParam("timeZone") String timeZone, String body) {
        return WebEdit.run(httpHeaders, "change the note " + noteId,
                store -> update(store, noteId, body, ResourceWebNotes.zone(timeZone)));
    }

    @DELETE
    @Path("notes/{noteId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("noteId") String noteId,
                           @QueryParam("version") String version, @QueryParam("timeZone") String timeZone) {
        return WebEdit.run(httpHeaders, "delete the note " + noteId,
                store -> delete(store, noteId, version, ResourceWebNotes.zone(timeZone)));
    }

    // ---------------------------------------------------------------- create

    /**
     * {@code POST /rows/{rowId}/notes} with {@code {timestamp, text, tags}}
     */
    static WebEdit.Reply create(WebEdit.Store store, long rowId, String body, DateTimeZone zone) throws Exception {
        JsonNode json = WebEdit.body(body);
        DateTime timestamp = WebEdit.timestamp(WebEdit.text(json, "timestamp"), zone, "timestamp").withMillisOfSecond(0);
        String text = text(json);
        List<String> tags = standardTags(json.get("tags"));
        if (tags == null) tags = new ArrayList<>();

        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebNotes.NotesTarget target = ResourceWebNotes.notesTarget(rowId, lookup, store.classes());
        if (target == null) return WebEdit.Reply.notFound("Row not found");
        WebEdit.Rights rights = store.rights();
        if (!ResourceWebNotes.canAddNote(target, rights)) return WebEdit.Reply.forbidden("Not allowed to add notes to this row");

        JsonObject notesObject = target.notesObject;
        if (notesObject != null) {
            ResourceWebNotes.Note existing = noteAt(store, notesObject.getId(), timestamp.getMillis());
            if (existing != null) return WebEdit.Reply.conflict(noteJson(store, existing, zone));
        } else {
            String name = target.row.getName() + NOTES_NAME_SUFFIX;
            notesObject = store.createObject(name, ResourceWebNotes.CLASS_DATA_NOTES, target.row.getId());
            store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(notesObject.getId(), notesObject.getName(), ACTION_CREATE));
            lookup.addReadable(notesObject, target.row.getId());
        }

        String account = store.account();
        long id = notesObject.getId();
        write(store, notesObject, ResourceWebNotes.ATTRIBUTE_VALUE, WebEdit.sample(timestamp, text, null), ACTION_CREATE);
        write(store, notesObject, ResourceWebNotes.ATTRIBUTE_TAG, WebEdit.sample(timestamp, String.join(";", tags), null), ACTION_CREATE);
        write(store, notesObject, ResourceWebNotes.ATTRIBUTE_USER, WebEdit.sample(timestamp, account, null), ACTION_CREATE);
        marker(store, target.row, timestamp, true, ACTION_CREATE);
        return WebEdit.Reply.created(noteJson(store, new ResourceWebNotes.Note(id, timestamp.getMillis(), text, tags, account), zone));
    }

    // ---------------------------------------------------------------- update and delete

    /**
     * {@code PUT /notes/{noteId}} with {@code {text, tags, version}}: overwrites "Value" (keeping the note of the
     * sample) and "Tag" (only if the tags changed, so custom tags of old data are kept) at the same time stamp,
     * "User" becomes the editing account.
     */
    static WebEdit.Reply update(WebEdit.Store store, String noteId, String body, DateTimeZone zone) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject notesObject = notesObject(store, noteId);
        ResourceWebNotes.Note note = notesObject != null ? noteAt(store, notesObject.getId(), millis(noteId)) : null;
        if (note == null) return WebEdit.Reply.notFound("Note not found");
        if (!WebEdit.canWriteNotes(store.rights(), notesObject)) return WebEdit.Reply.forbidden("Not allowed to change this note");
        String text = text(json);
        String version = WebEdit.text(json, "version");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        JsonNode tagsNode = json.get("tags");
        boolean keepTags = tagsNode == null || tagsNode.isNull() || sameTags(tagsNode, note.tags);
        List<String> tags = keepTags ? note.tags : standardTags(tagsNode);
        if (!version.trim().equals(ResourceWebNotes.version(note))) return WebEdit.Reply.conflict(noteJson(store, note, zone));

        DateTime timestamp = new DateTime(note.millis, zone);
        String sampleNote = null;
        List<JsonSample> current = store.samples(notesObject.getId(), ResourceWebNotes.ATTRIBUTE_VALUE, timestamp, timestamp, 1);
        if (current != null && !current.isEmpty()) sampleNote = current.get(0).getNote();
        String account = store.account();
        write(store, notesObject, ResourceWebNotes.ATTRIBUTE_VALUE, WebEdit.sample(timestamp, text, sampleNote), ACTION_UPDATE);
        if (!keepTags) {
            write(store, notesObject, ResourceWebNotes.ATTRIBUTE_TAG, WebEdit.sample(timestamp, String.join(";", tags), null), ACTION_UPDATE);
        }
        write(store, notesObject, ResourceWebNotes.ATTRIBUTE_USER, WebEdit.sample(timestamp, account, null), ACTION_UPDATE);
        return WebEdit.Reply.ok(noteJson(store, new ResourceWebNotes.Note(note.notesObject, note.millis, text, tags, account), zone));
    }

    /**
     * {@code DELETE /notes/{noteId}?version=…}: deletes "Value", "Tag" and "User" at the time stamp and removes the
     * marker {@code ,userNotes} from the sample of the row.
     */
    static WebEdit.Reply delete(WebEdit.Store store, String noteId, String version, DateTimeZone zone) throws Exception {
        JsonObject notesObject = notesObject(store, noteId);
        ResourceWebNotes.Note note = notesObject != null ? noteAt(store, notesObject.getId(), millis(noteId)) : null;
        if (note == null) return WebEdit.Reply.notFound("Note not found");
        if (!WebEdit.canDeleteNotes(store.rights(), notesObject)) return WebEdit.Reply.forbidden("Not allowed to delete this note");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        if (!version.trim().equals(ResourceWebNotes.version(note))) return WebEdit.Reply.conflict(noteJson(store, note, zone));

        DateTime timestamp = new DateTime(note.millis, zone);
        for (String attribute : Arrays.asList(ResourceWebNotes.ATTRIBUTE_VALUE, ResourceWebNotes.ATTRIBUTE_TAG, ResourceWebNotes.ATTRIBUTE_USER)) {
            WebEdit.deleteSamples(store, notesObject.getId(), attribute, timestamp, timestamp, ACTION_DELETE);
        }
        Long rowId = store.lookup().parent(notesObject.getId());
        JsonObject row = rowId != null ? WebEdit.readable(store, rowId) : null;
        if (row != null) marker(store, row, timestamp, false, ACTION_DELETE);
        return WebEdit.Reply.noContent();
    }

    // ---------------------------------------------------------------- helpers

    /**
     * @return the readable "Data Notes" object of the note id ({@code <notes object>-<epoch ms>}) outside the recycle
     * bin whose row is not in the recycle bin either; null if invalid or not found
     */
    static JsonObject notesObject(WebEdit.Store store, String noteId) throws Exception {
        Matcher matcher = noteId != null ? NOTE_ID.matcher(noteId.trim()) : null;
        if (matcher == null || !matcher.matches()) return null;
        long id;
        try {
            id = Long.parseLong(matcher.group(1));
            Long.parseLong(matcher.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
        JsonObject object = WebEdit.readable(store, id);
        if (object == null || !ResourceWebNotes.CLASS_DATA_NOTES.equals(object.getJevisClass())) return null;
        JsonObject row = store.lookup().object(store.lookup().parent(id));
        return row != null && ResourceWebIso.isDeleted(row) ? null : object;
    }

    private static long millis(String noteId) {
        Matcher matcher = NOTE_ID.matcher(noteId.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid note id");
        return Long.parseLong(matcher.group(2));
    }

    /**
     * @return the note of the "Data Notes" object at the time, null if there is none
     */
    static ResourceWebNotes.Note noteAt(WebEdit.Store store, long notesObject, long millis) throws Exception {
        DateTime time = new DateTime(millis);
        List<ResourceWebNotes.Note> notes = ResourceWebNotes.notesOf(notesObject, time, time, store::samples, 1);
        for (ResourceWebNotes.Note note : notes) {
            if (note.millis == millis) return note;
        }
        return null;
    }

    /**
     * @return the note in the format of {@code GET /notes}
     */
    static Map<String, Object> noteJson(WebEdit.Store store, ResourceWebNotes.Note note, DateTimeZone zone) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        Set<String> buildingClasses = store.classes().withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        Map<String, Object> row = ResourceWebNotes.editableRowJson(lookup.parent(note.notesObject), lookup, buildingClasses,
                store.classes(), store.rights());
        return ResourceWebNotes.noteJson(note, zone, row, ResourceWebNotes.siteOfNote(note.notesObject, lookup, buildingClasses),
                lookup, store.rights());
    }

    /**
     * @return the text of the note
     * @throws WebEdit.Stop {@code 400} if missing, blank or too long
     */
    static String text(JsonNode json) throws WebEdit.Stop {
        String text = WebEdit.text(json, "text");
        if (text == null || text.trim().isEmpty()) throw WebEdit.Stop.badRequest("Missing text");
        if (text.length() > MAX_TEXT_LENGTH) throw WebEdit.Stop.badRequest("Text longer than " + MAX_TEXT_LENGTH + " characters");
        return text;
    }

    /**
     * @return the tag ids of the field (repeats left out), null if missing
     * @throws WebEdit.Stop {@code 400} if not a list of standard tag ids ({@code 0}–{@code 13})
     */
    static List<String> standardTags(JsonNode node) throws WebEdit.Stop {
        if (node == null || node.isNull()) return null;
        List<String> tags = tagTexts(node);
        for (String tag : tags) {
            if (!ResourceWebNotes.NoteTags.KEYS.containsKey(tag)) throw WebEdit.Stop.badRequest("Unknown tag: " + tag);
        }
        return tags;
    }

    private static List<String> tagTexts(JsonNode node) throws WebEdit.Stop {
        if (!node.isArray()) throw WebEdit.Stop.badRequest("Invalid tags");
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : node) {
            if (!tag.isTextual() && !tag.isIntegralNumber()) throw WebEdit.Stop.badRequest("Invalid tags");
            String id = tag.asText().trim();
            if (!id.isEmpty() && !tags.contains(id)) tags.add(id);
        }
        return tags;
    }

    /**
     * @return whether the tags of the request are those of the note (in any order)
     */
    private static boolean sameTags(JsonNode node, List<String> current) throws WebEdit.Stop {
        return new HashSet<>(tagTexts(node)).equals(new HashSet<>(current));
    }

    private static void write(WebEdit.Store store, JsonObject notesObject, String attribute, JsonSample sample,
                              String action) throws Exception {
        WebEdit.writeSample(store, notesObject.getId(), attribute,
                WebEdit.primitiveType(store, notesObject, attribute, JEVisConstants.PrimitiveType.STRING), sample, action);
    }

    /**
     * Adds ({@code add}) or removes the marker {@code ,userNotes} in the note of the "Value" sample of the row at the
     * time, like {@code DataPointNoteDialog}: only if the user may write the row and there is such a sample.
     */
    static void marker(WebEdit.Store store, JsonObject row, DateTime timestamp, boolean add, String action) throws Exception {
        if (!store.rights().canWrite(row)) return;
        List<JsonSample> samples = store.samples(row.getId(), ResourceWebNotes.ATTRIBUTE_VALUE, timestamp, timestamp, 1);
        if (samples == null || samples.isEmpty()) return;
        JsonSample sample = samples.get(0);
        String note = sample.getNote() != null ? sample.getNote() : "";
        String changed;
        if (add) {
            if (note.contains(NoteConstants.User.USER_NOTES)) return;
            changed = note + MARKER;
        } else {
            if (!note.contains(MARKER)) return;
            changed = note.replace(MARKER, "");
        }
        JsonSample updated = new JsonSample();
        updated.setTs(sample.getTs());
        updated.setValue(sample.getValue());
        updated.setNote(changed);
        WebEdit.writeSample(store, row.getId(), ResourceWebNotes.ATTRIBUTE_VALUE,
                WebEdit.primitiveType(store, row, ResourceWebNotes.ATTRIBUTE_VALUE, JEVisConstants.PrimitiveType.DOUBLE),
                updated, action);
    }
}
