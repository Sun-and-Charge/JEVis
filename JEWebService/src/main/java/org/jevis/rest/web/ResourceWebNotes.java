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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonFactory;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonType;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Notes for the web app, view only (see docs/WEB-API.md, W4.4), like the Control Center's "Notes Plugin": a note is one
 * timestamp of a "Data Notes" object (child of a data row) with the samples of "Value", "Tag" and "User" at that
 * timestamp. Also the raw samples of a data row (the curve around a note) and the notes of the rows of an analysis.
 * <p>
 * Only "Data Notes" objects the user may read and that are not in the recycle bin are used; names and paths of rows
 * and sites are only given if the user may read them.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebNotes {

    static final String CLASS_DATA_NOTES = "Data Notes";
    static final String CLASS_CLEAN_DATA = "Clean Data";
    static final String CLASS_DATA = "Data";
    static final String ATTRIBUTE_VALUE = "Value";
    static final String ATTRIBUTE_TAG = "Tag";
    static final String ATTRIBUTE_USER = "User";
    /**
     * At most this many notes in the answer of {@code GET /notes}
     */
    static final int MAX_NOTES = 5000;
    /**
     * At most this many notes per row of an analysis
     */
    static final int MAX_ROW_NOTES = 200;
    /**
     * At most this many samples in the answer of {@code GET /rows/{id}/samples}
     */
    static final int MAX_SAMPLES = 5000;
    static final int MAX_SAMPLE_DAYS = 31;
    static final int MAX_YEARS = 3;
    static final int DEFAULT_MONTHS = 3;
    /**
     * Upper bound of the samples read per attribute of one "Data Notes" object (notes are written by hand)
     */
    static final int FETCH_LIMIT = 100000;

    private static final Logger logger = LogManager.getLogger(ResourceWebNotes.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("notes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response notes(@Context HttpHeaders httpHeaders, @QueryParam("from") String from,
                          @QueryParam("to") String to, @QueryParam("timeZone") String timeZone,
                          @QueryParam("lang") String lang) {
        DateTimeZone zone = zone(timeZone);
        DateTime[] range;
        try {
            range = dayRange(from, to, zone, LocalDate.now(zone));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            ResourceWebRegisters.Classes classes = ResourceWebRegisters.Classes.fromCache();
            return json(notesJson(lookup, classes, sqlSamples(ds), range[0], range[1], zone,
                    ResourceWebRegisters.language(lang), WebEdit.sqlRights(ds.getUserManager())));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the notes", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("rows/{id}/samples")
    @Produces(MediaType.APPLICATION_JSON)
    public Response samples(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                            @QueryParam("from") String from, @QueryParam("to") String to) {
        DateTime[] range;
        try {
            range = sampleRange(from, to, DateTimeZone.getDefault());
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            JsonType type = valueType(lookup, ResourceWebRegisters.Classes.fromCache(), id);
            if (type == null) return notFound("Row not found");
            List<JsonSample> samples = ds.getSamples(id, ATTRIBUTE_VALUE, range[0], range[1], MAX_SAMPLES + 1);
            String unit = ResourceWebRegisters.unitLabel(ds.getAttribute(id, ATTRIBUTE_VALUE));
            return json(samplesJson(unit, samples, isText(type)));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the samples of {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    // ---------------------------------------------------------------- navigation

    /**
     * @param readable the objects the user may read
     * @return {@code notes}: number of readable "Data Notes" objects (not in the recycle bin)
     */
    static Map<String, Object> moduleCounts(Collection<JsonObject> readable) {
        int notes = 0;
        for (JsonObject object : readable) {
            if (CLASS_DATA_NOTES.equals(object.getJevisClass()) && !ResourceWebIso.isDeleted(object)) notes++;
        }
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put("notes", notes);
        return modules;
    }

    // ---------------------------------------------------------------- time ranges

    /**
     * @return the time zone, the time zone of the server if not set or unknown
     */
    static DateTimeZone zone(String id) {
        if (id != null && !id.trim().isEmpty()) {
            try {
                return DateTimeZone.forID(id.trim());
            } catch (IllegalArgumentException ex) {
                logger.debug("Unknown time zone {}", id);
            }
        }
        return DateTimeZone.getDefault();
    }

    /**
     * @param from  first day ({@code yyyy-MM-dd}), default: three months before {@code to}
     * @param to    last day (inclusive), default: today
     * @param today today in the time zone
     * @return start of the first day and the last millisecond of the last day in the time zone
     * @throws IllegalArgumentException for invalid days, {@code to} before {@code from} or more than three years
     */
    static DateTime[] dayRange(String from, String to, DateTimeZone zone, LocalDate today) {
        return dayRange(from, to, zone, today, Period.months(DEFAULT_MONTHS), Period.years(MAX_YEARS), MAX_YEARS + " years");
    }

    /**
     * @param defaultLength {@code from} if not set: {@code to} minus this
     * @param max           longest allowed time frame
     * @param maxText       {@code max} for the error message ({@code "3 years"})
     * @see #dayRange(String, String, DateTimeZone, LocalDate)
     */
    static DateTime[] dayRange(String from, String to, DateTimeZone zone, LocalDate today, Period defaultLength,
                               Period max, String maxText) {
        LocalDate toDay = to != null && !to.trim().isEmpty() ? parseDay(to, "to") : today;
        LocalDate fromDay = from != null && !from.trim().isEmpty() ? parseDay(from, "from") : toDay.minus(defaultLength);
        if (toDay.isBefore(fromDay)) throw new IllegalArgumentException("to is before from");
        if (toDay.isAfter(fromDay.plus(max))) {
            throw new IllegalArgumentException("At most " + maxText);
        }
        return new DateTime[]{fromDay.toDateTimeAtStartOfDay(zone),
                toDay.plusDays(1).toDateTimeAtStartOfDay(zone).minusMillis(1)};
    }

    private static LocalDate parseDay(String value, String name) {
        try {
            return ISODateTimeFormat.date().parseLocalDate(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    /**
     * @param from ISO 8601 date-time (without offset: in the time zone)
     * @param to   ISO 8601 date-time, at most {@link #MAX_SAMPLE_DAYS} days (plus one hour for the switch to winter
     *             time) after {@code from}
     * @throws IllegalArgumentException if missing, invalid, {@code to} before {@code from} or too far apart
     */
    static DateTime[] sampleRange(String from, String to, DateTimeZone zone) {
        DateTime start = parseDateTime(from, zone, "from");
        DateTime end = parseDateTime(to, zone, "to");
        if (end.isBefore(start)) throw new IllegalArgumentException("to is before from");
        /* plus one hour: a month with the switch to winter time is one hour longer (dates with fixed offsets) */
        if (end.isAfter(start.plusDays(MAX_SAMPLE_DAYS).plusHours(1))) {
            throw new IllegalArgumentException("At most " + MAX_SAMPLE_DAYS + " days");
        }
        return new DateTime[]{start, end};
    }

    private static DateTime parseDateTime(String value, DateTimeZone zone, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Missing " + name);
        try {
            return new DateTime(value.trim(), zone);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    // ---------------------------------------------------------------- notes

    /**
     * Samples of an attribute in a time frame, oldest first ({@link SQLDataSource#getSamples}).
     */
    interface Samples {
        List<JsonSample> get(long id, String attribute, DateTime from, DateTime to, long limit) throws Exception;
    }

    static Samples sqlSamples(SQLDataSource ds) {
        return ds::getSamples;
    }

    /**
     * One note: a "Value" sample of a "Data Notes" object with the "Tag" and "User" samples at the same time.
     */
    static final class Note {
        final long notesObject;
        final long millis;
        final String text;
        final List<String> tags;
        final String user;

        Note(long notesObject, long millis, String text, List<String> tags, String user) {
            this.notesObject = notesObject;
            this.millis = millis;
            this.text = text;
            this.tags = tags;
            this.user = user;
        }
    }

    /**
     * @return the notes of the "Data Notes" object in the time frame (both inclusive), oldest first
     */
    static List<Note> notesOf(long notesObject, DateTime from, DateTime to, Samples samples, int limit) throws Exception {
        List<JsonSample> values = samples.get(notesObject, ATTRIBUTE_VALUE, from, to, limit);
        List<Note> notes = new ArrayList<>();
        if (values == null || values.isEmpty()) return notes;
        Map<Long, String> tags = byMillis(samples.get(notesObject, ATTRIBUTE_TAG, from, to, FETCH_LIMIT));
        Map<Long, String> users = byMillis(samples.get(notesObject, ATTRIBUTE_USER, from, to, FETCH_LIMIT));
        for (JsonSample value : values) {
            Long millis = millis(value.getTs());
            if (millis == null) continue;
            String user = users.get(millis);
            notes.add(new Note(notesObject, millis, value.getValue() != null ? value.getValue() : "",
                    NoteTags.parse(tags.get(millis)), user != null && !user.trim().isEmpty() ? user : null));
        }
        notes.sort(Comparator.comparingLong(n -> n.millis));
        return notes;
    }

    private static Map<Long, String> byMillis(List<JsonSample> samples) {
        Map<Long, String> map = new HashMap<>();
        if (samples == null) return map;
        for (JsonSample sample : samples) {
            Long millis = millis(sample.getTs());
            if (millis != null) map.put(millis, sample.getValue());
        }
        return map;
    }

    static Long millis(String ts) {
        if (ts == null) return null;
        try {
            return JsonFactory.sampleDTF.withOffsetParsed().parseDateTime(ts).getMillis();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return the readable "Data Notes" objects that are not in the recycle bin and whose row is not either
     */
    static List<JsonObject> notesObjects(ResourceWebModules.Lookup lookup) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, Collections.singleton(CLASS_DATA_NOTES))) {
            JsonObject row = lookup.object(lookup.parent(object.getId()));
            if (row == null || !ResourceWebIso.isDeleted(row)) result.add(object);
        }
        return result;
    }

    static Map<String, Object> notesJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                         Samples samples, DateTime from, DateTime to, DateTimeZone zone,
                                         String language) throws Exception {
        return notesJson(lookup, classes, samples, from, to, zone, language, WebEdit.Rights.NONE);
    }

    /**
     * {@code GET /notes}: all notes of the readable "Data Notes" objects in the time frame, newest first; with what
     * the user may do ({@code canEdit}, {@code canDelete} per note, {@code canAddNote} and {@code notesTarget} per row).
     */
    static Map<String, Object> notesJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                         Samples samples, DateTime from, DateTime to, DateTimeZone zone,
                                         String language, WebEdit.Rights rights) throws Exception {
        List<Note> notes = new ArrayList<>();
        for (JsonObject object : notesObjects(lookup)) {
            notes.addAll(notesOf(object.getId(), from, to, samples, FETCH_LIMIT));
        }
        notes.sort(Comparator.comparingLong((Note n) -> n.millis).reversed().thenComparingLong(n -> n.notesObject));
        boolean truncated = notes.size() > MAX_NOTES;
        if (truncated) notes = new ArrayList<>(notes.subList(0, MAX_NOTES));

        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        Map<Long, Map<String, Object>> rows = new HashMap<>();
        Map<Long, Map<String, Object>> sites = new HashMap<>();
        Set<String> usedTags = new LinkedHashSet<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Note note : notes) {
            Long rowId = lookup.parent(note.notesObject);
            if (!rows.containsKey(note.notesObject)) {
                rows.put(note.notesObject, editableRowJson(rowId, lookup, buildingClasses, classes, rights));
                sites.put(note.notesObject, siteOfNote(note.notesObject, lookup, buildingClasses));
            }
            usedTags.addAll(note.tags);
            list.add(noteJson(note, zone, rows.get(note.notesObject), sites.get(note.notesObject), lookup, rights));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", ISODateTimeFormat.dateTime().print(from));
        result.put("to", ISODateTimeFormat.dateTime().print(to));
        result.put("tags", NoteTags.tagList(usedTags, language));
        result.put("notes", list);
        result.put("truncated", truncated);
        return result;
    }

    /**
     * @return the site of a note: the nearest readable building above its row (else above the notes object)
     */
    static Map<String, Object> siteOfNote(long notesObject, ResourceWebModules.Lookup lookup, Set<String> buildingClasses) {
        Long rowId = lookup.parent(notesObject);
        JsonObject start = rowId != null && lookup.object(rowId) != null ? lookup.object(rowId) : lookup.object(notesObject);
        return start != null ? siteJson(start, lookup, buildingClasses) : null;
    }

    /**
     * One note in the format of {@code GET /notes}.
     */
    static Map<String, Object> noteJson(Note note, DateTimeZone zone, Map<String, Object> row, Map<String, Object> site,
                                        ResourceWebModules.Lookup lookup, WebEdit.Rights rights) {
        JsonObject notesObject = lookup.readable(note.notesObject);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", noteId(note));
        json.put("timestamp", ISODateTimeFormat.dateTime().print(new DateTime(note.millis, zone)));
        json.put("text", note.text);
        json.put("tags", note.tags);
        json.put("user", note.user);
        json.put("notesObject", note.notesObject);
        json.put("row", row);
        json.put("site", site);
        json.put("version", version(note));
        json.put("canEdit", notesObject != null && WebEdit.canWriteNotes(rights, notesObject));
        json.put("canDelete", notesObject != null && WebEdit.canDeleteNotes(rights, notesObject));
        return json;
    }

    /**
     * @return {@code <notes object>-<epoch ms>}
     */
    static String noteId(Note note) {
        return note.notesObject + "-" + note.millis;
    }

    /**
     * The version of a note for concurrent edits: the first 16 hex digits of the SHA-256 of text, tags and user (the
     * samples table keeps the insert time of a sample when it is overwritten, so it cannot be used).
     */
    static String version(Note note) {
        String content = note.text + '\u001f' + String.join(";", note.tags) + '\u001f' + (note.user != null ? note.user : "");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", hash[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@link #rowJson} plus {@code canAddNote} and {@code notesTarget} (id of the row the notes are stored below; null
     * if notes cannot be stored for the row)
     */
    static Map<String, Object> editableRowJson(Long rowId, ResourceWebModules.Lookup lookup, Set<String> buildingClasses,
                                               ResourceWebRegisters.Classes classes, WebEdit.Rights rights) {
        Map<String, Object> json = rowJson(rowId, lookup, buildingClasses);
        if (json == null) return null;
        NotesTarget target = notesTarget(rowId, lookup, classes);
        json.put("canAddNote", canAddNote(target, rights));
        json.put("notesTarget", target != null ? target.row.getId() : null);
        return json;
    }

    /**
     * Where the notes of a row are stored (like the analysis note dialog {@code DataPointNoteDialog}): the target row
     * is the row itself, or its raw parent if the row is "Clean Data" (or a subclass); the notes object is the first
     * (lowest id) readable "Data Notes" child of the target row that is not in the recycle bin, null if one has to be
     * created.
     */
    static final class NotesTarget {
        final JsonObject row;
        final JsonObject notesObject;

        NotesTarget(JsonObject row, JsonObject notesObject) {
            this.row = row;
            this.notesObject = notesObject;
        }
    }

    /**
     * @return the target of the notes of the row; null if the row (or the raw parent of a clean data row) is not a
     * readable data row ("Data" or a subclass) outside the recycle bin
     */
    static NotesTarget notesTarget(Long rowId, ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes) {
        JsonObject row = rowId != null ? lookup.readable(rowId) : null;
        if (row == null || ResourceWebIso.isDeleted(row)) return null;
        Set<String> cleanClasses = classes.withHeirs(CLASS_CLEAN_DATA);
        JsonObject target = row;
        if (cleanClasses.contains(row.getJevisClass())) {
            target = lookup.readable(lookup.parent(row.getId()));
            if (target == null || ResourceWebIso.isDeleted(target)) return null;
        }
        if (!classes.withHeirs(CLASS_DATA).contains(target.getJevisClass()) && !cleanClasses.contains(target.getJevisClass())) {
            return null;
        }
        JsonObject notesObject = null;
        for (JsonObject child : lookup.readableChildren(target.getId(), Collections.singleton(CLASS_DATA_NOTES))) {
            if (ResourceWebIso.isDeleted(child)) continue;
            if (notesObject == null || child.getId() < notesObject.getId()) notesObject = child;
        }
        return new NotesTarget(target, notesObject);
    }

    /**
     * @return whether the user may add a note to the target: write or execute right on its notes object, or (without
     * one) the right to create it and to write to it (the new object gets the owners of the target row)
     */
    static boolean canAddNote(NotesTarget target, WebEdit.Rights rights) {
        if (target == null) return false;
        if (target.notesObject != null) return WebEdit.canWriteNotes(rights, target.notesObject);
        return rights.canCreate(target.row, CLASS_DATA_NOTES) && WebEdit.canWriteNotes(rights, target.row);
    }

    /**
     * @return {@code id, name, className, path, readable}; name, class and path only if the user may read the row;
     * null without row
     */
    static Map<String, Object> rowJson(Long rowId, ResourceWebModules.Lookup lookup, Set<String> buildingClasses) {
        if (rowId == null || lookup.object(rowId) == null) return null;
        JsonObject row = lookup.readable(rowId);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", rowId);
        json.put("name", row != null ? row.getName() : null);
        json.put("className", row != null ? row.getJevisClass() : null);
        json.put("path", row != null ? path(row, lookup, buildingClasses) : null);
        json.put("readable", row != null);
        return json;
    }

    /**
     * @return the names of the readable parents below the nearest building (or organisation) down to the parent of
     * the object ({@code "Data / Electricity"}), up to the first parent the user may not read; null if there is none
     */
    static String path(JsonObject object, ResourceWebModules.Lookup lookup, Set<String> buildingClasses) {
        LinkedList<String> names = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        Long current = lookup.parent(object.getId());
        while (current != null && visited.add(current)) {
            JsonObject parent = lookup.readable(current);
            if (parent == null || buildingClasses.contains(parent.getJevisClass())
                    || ResourceWebNavigation.CLASS_ORGANIZATION.equals(parent.getJevisClass())) {
                break;
            }
            names.addFirst(parent.getName());
            current = lookup.parent(current);
        }
        return names.isEmpty() ? null : String.join(" / ", names);
    }

    /**
     * @return {@code id, name} of the nearest readable building (or subclass) above the object, or null
     */
    static Map<String, Object> siteJson(JsonObject object, ResourceWebModules.Lookup lookup, Set<String> buildingClasses) {
        Set<Long> visited = new HashSet<>();
        Long current = lookup.parent(object.getId());
        while (current != null && visited.add(current)) {
            JsonObject parent = lookup.object(current);
            if (parent == null) return null;
            if (buildingClasses.contains(parent.getJevisClass()) && lookup.readable(current) != null) {
                Map<String, Object> site = new LinkedHashMap<>();
                site.put("id", parent.getId());
                site.put("name", parent.getName());
                return site;
            }
            current = lookup.parent(current);
        }
        return null;
    }

    // ---------------------------------------------------------------- analyses

    /**
     * The "Data Notes" objects of a row: below the row's object and, for a Clean Data row, below its raw data parent;
     * only readable ones that are not in the recycle bin.
     */
    static List<JsonObject> notesObjectsOfRow(long objectId, ResourceWebModules.Lookup lookup,
                                              Set<String> cleanDataClasses) {
        List<Long> owners = new ArrayList<>();
        owners.add(objectId);
        JsonObject object = lookup.object(objectId);
        Long parent = lookup.parent(objectId);
        if (object != null && cleanDataClasses.contains(object.getJevisClass()) && parent != null) owners.add(parent);
        List<JsonObject> result = new ArrayList<>();
        for (Long owner : owners) {
            for (JsonObject child : lookup.readableChildren(owner, Collections.singleton(CLASS_DATA_NOTES))) {
                if (!ResourceWebIso.isDeleted(child)) result.add(child);
            }
        }
        return result;
    }

    /**
     * @return the notes of a row of an analysis in the time frame: {@code [{timestamp, text, tags, user}]}, oldest
     * first, at most {@link #MAX_ROW_NOTES}
     */
    static List<Map<String, Object>> rowNotes(long objectId, DateTime from, DateTime to,
                                              ResourceWebModules.Lookup lookup, Set<String> cleanDataClasses,
                                              Samples samples) throws Exception {
        return rowNotes(objectId, from, to, lookup, cleanDataClasses, samples, WebEdit.Rights.NONE);
    }

    /**
     * @return the notes of a row of an analysis in the time frame: {@code [{id, notesObject, timestamp, text, tags,
     * user, version, canEdit, canDelete}]}, oldest first, at most {@link #MAX_ROW_NOTES}
     */
    static List<Map<String, Object>> rowNotes(long objectId, DateTime from, DateTime to,
                                              ResourceWebModules.Lookup lookup, Set<String> cleanDataClasses,
                                              Samples samples, WebEdit.Rights rights) throws Exception {
        List<Note> notes = new ArrayList<>();
        for (JsonObject notesObject : notesObjectsOfRow(objectId, lookup, cleanDataClasses)) {
            notes.addAll(notesOf(notesObject.getId(), from, to, samples, MAX_ROW_NOTES));
        }
        notes.sort(Comparator.comparingLong((Note n) -> n.millis).thenComparingLong(n -> n.notesObject));
        if (notes.size() > MAX_ROW_NOTES) notes = notes.subList(0, MAX_ROW_NOTES);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Note note : notes) {
            JsonObject notesObject = lookup.readable(note.notesObject);
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", noteId(note));
            json.put("notesObject", note.notesObject);
            json.put("timestamp", note.millis);
            json.put("text", note.text);
            json.put("tags", note.tags);
            json.put("user", note.user);
            json.put("version", version(note));
            json.put("canEdit", notesObject != null && WebEdit.canWriteNotes(rights, notesObject));
            json.put("canDelete", notesObject != null && WebEdit.canDeleteNotes(rights, notesObject));
            result.add(json);
        }
        return result;
    }

    // ---------------------------------------------------------------- samples of a row

    /**
     * @return the type "Value" of the row if the user may read it, it is not in the recycle bin and its class has the
     * attribute; else null
     */
    static JsonType valueType(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes, long id) {
        JsonObject object = lookup.readable(id);
        if (object == null || ResourceWebIso.isDeleted(object)) return null;
        return classes.type(object.getJevisClass(), ATTRIBUTE_VALUE);
    }

    static boolean isText(JsonType type) {
        return type.getPrimitiveType() == JEVisConstants.PrimitiveType.STRING;
    }

    /**
     * @param samples oldest first, up to {@link #MAX_SAMPLES} + 1 (to detect the truncation)
     * @param text    text row: values as strings
     * @return {@code unit, samples: [[millis, value]], truncated}
     */
    static Map<String, Object> samplesJson(String unit, List<JsonSample> samples, boolean text) {
        List<Object[]> list = new ArrayList<>();
        boolean truncated = false;
        for (JsonSample sample : samples) {
            if (list.size() >= MAX_SAMPLES) {
                truncated = true;
                break;
            }
            Long millis = millis(sample.getTs());
            if (millis == null) continue;
            Object value = text ? sample.getValue() : ResourceWebRegisters.number(sample.getValue());
            list.add(new Object[]{millis, value});
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unit", unit);
        result.put("samples", list);
        result.put("truncated", truncated);
        return result;
    }

    // ---------------------------------------------------------------- helpers

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(message).type(MediaType.TEXT_PLAIN)
                .header("Cache-Control", "no-store").build();
    }

    private static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND).entity(message).type(MediaType.TEXT_PLAIN)
                .header("Cache-Control", "no-store").build();
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }

    /**
     * The tags of notes, headless port of the Control Center's {@code NoteTag}: ids {@code 0}–{@code 13} with the
     * names of the keys {@code plugin.notes.tags.*} (and {@code plugin.alarm.table.translation.none} for {@code 0}) of
     * the JEVis language files.
     */
    static final class NoteTags {
        /**
         * Tag id → key of the name ({@code NoteTag.getAllTags})
         */
        static final Map<String, String> KEYS;
        static final List<String> LANGUAGES = Collections.unmodifiableList(Arrays.asList("en", "de", "ru", "uk", "th", "ar"));
        private static Map<String, Properties> bundles;
        private static Map<String, String> idByName;

        static {
            Map<String, String> keys = new LinkedHashMap<>();
            keys.put("0", "plugin.alarm.table.translation.none");
            String[] names = {"audit", "task", "report", "event", "reminder", "error", "question", "service",
                    "characteristic", "comment", "measure", "disturbance", "maintenance"};
            for (int i = 0; i < names.length; i++) keys.put(String.valueOf(i + 1), "plugin.notes.tags." + names[i]);
            KEYS = Collections.unmodifiableMap(keys);
        }

        private NoteTags() {
        }

        private static synchronized Map<String, Properties> bundles() {
            if (bundles == null) {
                Map<String, Properties> map = new LinkedHashMap<>();
                for (String language : LANGUAGES) {
                    try (InputStream in = NoteTags.class.getClassLoader().getResourceAsStream("JEVis_" + language + ".properties")) {
                        if (in == null) continue;
                        Properties properties = new Properties();
                        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                            properties.load(reader);
                        }
                        map.put(language, properties);
                    } catch (Exception e) {
                        logger.warn("Could not read the language file {}", language, e);
                    }
                }
                bundles = map;
            }
            return bundles;
        }

        private static synchronized Map<String, String> idByName() {
            if (idByName == null) {
                Map<String, String> map = new HashMap<>();
                for (Properties properties : bundles().values()) {
                    for (Map.Entry<String, String> entry : KEYS.entrySet()) {
                        String name = properties.getProperty(entry.getValue());
                        if (name != null && !name.trim().isEmpty()) {
                            map.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), entry.getKey());
                        }
                    }
                }
                idByName = map;
            }
            return idByName;
        }

        /**
         * Like {@code NoteTag.parseTags}: the {@code ;}-separated parts are tag ids or tag names in any language;
         * unknown texts are kept as their own id (the text itself, trimmed); empty parts and repeats are left out.
         */
        static List<String> parse(String tagString) {
            List<String> ids = new ArrayList<>();
            if (tagString == null) return ids;
            for (String part : tagString.split(";")) {
                String text = part.trim();
                if (text.isEmpty()) continue;
                String id = KEYS.containsKey(text) ? text : idByName().get(text.toLowerCase(Locale.ROOT));
                if (id == null) id = text;
                if (!ids.contains(id)) ids.add(id);
            }
            return ids;
        }

        /**
         * @return the name of a standard tag in the language (fallback English, then the id); other ids are their text
         */
        static String name(String id, String language) {
            String key = KEYS.get(id);
            if (key == null) return id;
            String name = translate(key, language);
            return name != null ? name : id;
        }

        /**
         * @return the text of the key in the JEVis language file of the language (fallback English), null if none
         */
        static String translate(String key, String language) {
            for (String lang : new String[]{language, "en"}) {
                Properties properties = bundles().get(lang);
                String name = properties != null ? properties.getProperty(key) : null;
                if (name != null && !name.trim().isEmpty()) return name.trim();
            }
            return null;
        }

        /**
         * @return {@code [{id, name}]}: the standard tags, then the other used ids (sorted)
         */
        static List<Map<String, Object>> tagList(Collection<String> used, String language) {
            List<String> ids = new ArrayList<>(KEYS.keySet());
            Set<String> other = new TreeSet<>(used);
            other.removeAll(KEYS.keySet());
            ids.addAll(other);
            List<Map<String, Object>> list = new ArrayList<>();
            for (String id : ids) {
                Map<String, Object> tag = new LinkedHashMap<>();
                tag.put("id", id);
                tag.put("name", name(id, language));
                list.add(tag);
            }
            return list;
        }
    }
}
