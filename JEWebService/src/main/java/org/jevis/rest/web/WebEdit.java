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
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisObject;
import org.jevis.commons.datetime.WorkDays;
import org.jevis.commons.utils.CommonMethods;
import org.jevis.commons.ws.json.JsonFactory;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.CachedAccessControl;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.JEVisUserSQL;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.UserRightManagerForWS;
import org.jevis.commons.ws.sql.local.LocalDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;
import java.util.*;

/**
 * Shared parts of the write endpoints of the web app (stage E1, see docs/WEB-API.md): data access with the rights of
 * the logged-in user ({@link Store}, {@link Rights}), the rights rules of the generic resources, answers
 * ({@link Reply}) and the format of the user action log.
 * <p>
 * Every write goes through {@link Store} and is checked with {@link Rights} before; the rules are those of
 * {@code ResourceSample}, {@code ResourceObject} and {@code UserRightManagerForWS} (including the execute exceptions for
 * "Data Notes", "User Data" and "Alarm Configuration"). Nothing is written with elevated rights.
 */
final class WebEdit {

    static final String CLASS_DATA_NOTES = ResourceWebNotes.CLASS_DATA_NOTES;
    static final String CLASS_USER_DATA = "User Data";
    /**
     * Suffix of the details of every user action logged by the web app ({@code 24202:Alarm Checked|1|web:alarm-confirm})
     */
    static final String ACTION_PREFIX = "web:";

    private static final Logger logger = LogManager.getLogger(WebEdit.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebEdit() {
    }

    // ---------------------------------------------------------------- rights

    /**
     * The rights of the logged-in user on an object (production: {@link UserRightManagerForWS}).
     */
    interface Rights {
        boolean canWrite(JsonObject object);

        boolean canExecute(JsonObject object);

        boolean canDelete(JsonObject object);

        /**
         * Like {@link UserRightManagerForWS#canCreate(JsonObject, String)}: create right on the parent, or execute
         * right for the classes of {@code PermissionExplainer.CREATE_EXCEPTION_CLASSES} ("Data Notes", "User Data").
         */
        boolean canCreate(JsonObject parent, String jevisClass);

        Rights NONE = new Rights() {
            @Override
            public boolean canWrite(JsonObject object) {
                return false;
            }

            @Override
            public boolean canExecute(JsonObject object) {
                return false;
            }

            @Override
            public boolean canDelete(JsonObject object) {
                return false;
            }

            @Override
            public boolean canCreate(JsonObject parent, String jevisClass) {
                return false;
            }
        };
    }

    static Rights sqlRights(UserRightManagerForWS um) {
        return new Rights() {
            @Override
            public boolean canWrite(JsonObject object) {
                return object != null && um.canWriteWOE(object);
            }

            @Override
            public boolean canExecute(JsonObject object) {
                return object != null && um.canExecuteWOE(object);
            }

            @Override
            public boolean canDelete(JsonObject object) {
                try {
                    return object != null && um.canDelete(object);
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public boolean canCreate(JsonObject parent, String jevisClass) {
                return parent != null && um.canCreateWOE(parent, jevisClass);
            }
        };
    }

    /**
     * "Alarm Checked" of an "Alarm Configuration" ({@code ResourceSample.postSamples}, {@code AlarmConfiguration.setChecked}):
     * write or execute right.
     */
    static boolean canConfirmAlarm(Rights rights, JsonObject configuration) {
        return rights.canWrite(configuration) || rights.canExecute(configuration);
    }

    /**
     * Samples of a "Data Notes" object ({@code ResourceSample.postSamples}): write or execute right.
     */
    static boolean canWriteNotes(Rights rights, JsonObject notesObject) {
        return rights.canWrite(notesObject) || rights.canExecute(notesObject);
    }

    /**
     * Deleting samples of a "Data Notes" object ({@code ResourceSample.deleteSamples}): execute or delete right.
     */
    static boolean canDeleteNotes(Rights rights, JsonObject notesObject) {
        return rights.canExecute(notesObject) || rights.canDelete(notesObject);
    }

    /**
     * Deleting samples of any other object ({@code ResourceSample.deleteSamples}): delete right; "Data Notes" and
     * "User Data" also with execute right.
     */
    static boolean canDeleteSamples(Rights rights, JsonObject object) {
        if (CLASS_DATA_NOTES.equals(object.getJevisClass()) || CLASS_USER_DATA.equals(object.getJevisClass())) {
            return canDeleteNotes(rights, object);
        }
        return rights.canDelete(object);
    }

    /**
     * Raw rows that take manual values ({@code EnterDataDialog})
     */
    static final List<String> MANUAL_ROW_CLASSES = Collections.unmodifiableList(Arrays.asList("Data", "Base Data"));

    /**
     * @return whether the user may enter values for the row: raw "Data" or "Base Data" outside the recycle bin with
     * write right
     */
    static boolean canEnter(JsonObject row, Rights rights) {
        return row != null && !ResourceWebIso.isDeleted(row) && MANUAL_ROW_CLASSES.contains(row.getJevisClass())
                && rights.canWrite(row);
    }

    // ---------------------------------------------------------------- data access

    /**
     * Start and end of the working day and the time zone of the site of a row ({@link WorkDays}).
     */
    static final class WorkDay {
        static final LocalTime DAY_START = LocalTime.of(0, 0, 0, 0);
        static final LocalTime DAY_END = LocalTime.of(23, 59, 59, 999999999);

        final DateTimeZone zone;
        final LocalTime start;
        final LocalTime end;

        WorkDay(DateTimeZone zone, LocalTime start, LocalTime end) {
            this.zone = zone != null ? zone : DateTimeZone.getDefault();
            this.start = start != null ? start : DAY_START;
            this.end = end != null ? end : DAY_END;
        }

        static WorkDay standard(DateTimeZone zone) {
            return new WorkDay(zone, DAY_START, DAY_END);
        }

        /**
         * {@link WorkDays#isCustomWorkDay()}
         */
        boolean isCustom() {
            return !(start.equals(DAY_START) && end.equals(DAY_END));
        }

        /**
         * The working day starts on the day before (end before start, e.g. 06:00 to 05:59)
         */
        boolean startsDayBefore() {
            return isCustom() && end.isBefore(start);
        }
    }

    /**
     * Objects, rights, samples and writes of one request (production: the {@link SQLDataSource} of the request).
     */
    interface Store {
        ResourceWebModules.Lookup lookup() throws Exception;

        ResourceWebRegisters.Classes classes();

        Rights rights();

        /**
         * @return the account name of the logged-in user
         */
        String account();

        /**
         * @return the samples of the attribute in the time frame (both inclusive), oldest first
         */
        List<JsonSample> samples(long id, String attribute, DateTime from, DateTime to, long limit) throws Exception;

        /**
         * @return the latest sample of the attribute or null
         */
        JsonSample latest(long id, String attribute) throws Exception;

        /**
         * Inserts or overwrites samples (same time stamp) of the attribute.
         *
         * @return number of changed rows
         */
        int write(long id, String attribute, int primitiveType, List<JsonSample> samples) throws Exception;

        /**
         * Deletes the samples of the attribute in the time frame (both inclusive).
         */
        void delete(long id, String attribute, DateTime from, DateTime to) throws Exception;

        /**
         * Creates an object below the parent (owners are copied from the parent).
         */
        JsonObject createObject(String name, String jevisClass, long parent) throws Exception;

        /**
         * Renames the object, keeping its translations and public flag.
         */
        void rename(JsonObject object, String name) throws Exception;

        void log(SQLDataSource.LOG_EVENT event, String details);

        /**
         * @return the label of the display unit of the attribute, null if none
         */
        String unit(long id, String attribute);

        /**
         * @return working day and time zone of the site of the object
         */
        WorkDay workDay(JsonObject object);

        /**
         * Like {@code CommonMethods.cleanDependentObjects}: the objects whose values depend on the row (its data rows
         * below and the outputs of calculations using them), as far as the user may read them.
         */
        List<Long> dependents(JsonObject row) throws Exception;

        /**
         * @return first and last name of the logged-in user, the account name if both are empty
         */
        String userName();

        /**
         * @param sample a sample of the FILE attribute (its value is the file name)
         * @return the content of the stored file of the sample, null if there is none. Rights are not checked: the
         * caller checks them (or only uses the content internally, e.g. for the next number).
         */
        byte[] file(long id, String attribute, JsonSample sample) throws Exception;

        /**
         * Stores the file ({@code <file dir>/<id>/<attribute>/<UTC yyyyMMddHHmmss>_<name>}) and then its FILE sample at
         * the time stamp (second precision, so one save per second and attribute).
         *
         * @return number of changed rows
         */
        int writeFile(long id, String attribute, DateTime timestamp, String fileName, byte[] content) throws Exception;

        /**
         * Removes an object created in the same request for good (rollback after a failure).
         */
        void deleteObject(JsonObject object) throws Exception;

        /**
         * Moves the object to the recycle bin ({@code ResourceObject.deleteObject} without {@code deleteForever}).
         */
        void recycle(JsonObject object) throws Exception;

        /**
         * Waits until the time is reached (two saves in the same second would overwrite each other's file sample).
         */
        void waitUntil(DateTime time) throws InterruptedException;
    }

    static Store sqlStore(SQLDataSource ds) {
        return new Store() {
            private ResourceWebModules.Lookup lookup;
            private ResourceWebRegisters.Classes classes;
            private Rights rights;

            @Override
            public ResourceWebModules.Lookup lookup() throws Exception {
                if (lookup == null) lookup = ResourceWebModules.lookup(ds);
                return lookup;
            }

            @Override
            public ResourceWebRegisters.Classes classes() {
                if (classes == null) classes = ResourceWebRegisters.Classes.fromCache();
                return classes;
            }

            @Override
            public Rights rights() {
                if (rights == null) rights = sqlRights(ds.getUserManager());
                return rights;
            }

            @Override
            public String account() {
                return ds.getCurrentUser().getAccountName();
            }

            @Override
            public List<JsonSample> samples(long id, String attribute, DateTime from, DateTime to, long limit) throws Exception {
                return ds.getSamples(id, attribute, from, to, limit);
            }

            @Override
            public JsonSample latest(long id, String attribute) throws Exception {
                return ds.getLastSample(id, attribute);
            }

            @Override
            public int write(long id, String attribute, int primitiveType, List<JsonSample> samples) throws Exception {
                return ds.setSamples(id, attribute, primitiveType, samples);
            }

            @Override
            public void delete(long id, String attribute, DateTime from, DateTime to) {
                ds.deleteSamplesBetween(id, attribute, from, to);
            }

            @Override
            public JsonObject createObject(String name, String jevisClass, long parent) throws Exception {
                JsonObject request = new JsonObject();
                request.setName(name);
                request.setJevisClass(jevisClass);
                request.setParent(parent);
                request.setisPublic(false);
                return ds.buildObject(request, parent, null);
            }

            @Override
            public void rename(JsonObject object, String name) throws Exception {
                JsonObject current = ds.getObject(object.getId());
                String i18n = current.getI18n() != null && !current.getI18n().isEmpty()
                        ? MAPPER.writeValueAsString(current.getI18n()) : null;
                ds.updateObject(object.getId(), name, current.getisPublic(), i18n);
            }

            @Override
            public void log(SQLDataSource.LOG_EVENT event, String details) {
                ds.logUserAction(event, details);
            }

            @Override
            public String unit(long id, String attribute) {
                return ResourceWebRegisters.unitLabel(ds.getAttribute(id, attribute));
            }

            @Override
            public WorkDay workDay(JsonObject object) {
                try {
                    WorkDays workDays = new WorkDays(ds, object);
                    return new WorkDay(workDays.getDateTimeZone(), workDays.getWorkdayStart(), workDays.getWorkdayEnd());
                } catch (Exception e) {
                    logger.warn("Could not read the working day of {}", object.getId(), e);
                    return WorkDay.standard(DateTimeZone.getDefault());
                }
            }

            @Override
            public List<Long> dependents(JsonObject row) throws Exception {
                LocalDataSource local = new LocalDataSource(ds);
                JEVisObject object = local.getObject(row.getId());
                if (object == null) return Collections.emptyList();
                List<JEVisObject> objects = new ArrayList<>(Collections.singletonList(object));
                List<Long> ids = new ArrayList<>();
                for (JEVisObject dependent : CommonMethods.getAllDependentObjects(CommonMethods.createCalculationMap(local), objects)) {
                    if (dependent != null && !ids.contains(dependent.getID())) ids.add(dependent.getID());
                }
                return ids;
            }

            @Override
            public String userName() {
                JEVisUserSQL user = ds.getCurrentUser();
                String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                        + (user.getLastName() != null ? user.getLastName() : "")).trim();
                return name.isEmpty() ? user.getAccountName() : name;
            }

            @Override
            public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
                if (sample == null || sample.getValue() == null || sample.getTs() == null) return null;
                File file = storedFile(id, attribute, JsonFactory.sampleDTF.parseDateTime(sample.getTs()), sample.getValue());
                return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
            }

            @Override
            public int writeFile(long id, String attribute, DateTime timestamp, String fileName, byte[] content)
                    throws Exception {
                File file = storedFile(id, attribute, timestamp, fileName);
                if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                    throw new IOException("Could not create directory " + file.getParentFile());
                }
                /* the sample only after the file, otherwise it would point to a missing file */
                Files.write(file.toPath(), content);
                return ds.setSamples(id, attribute, JEVisConstants.PrimitiveType.FILE,
                        new ArrayList<>(Collections.singletonList(sample(timestamp, fileName, null))));
            }

            @Override
            public void deleteObject(JsonObject object) throws Exception {
                ds.deleteObject(object);
            }

            @Override
            public void recycle(JsonObject object) throws Exception {
                ds.markAsDeletedObject(object);
                try {
                    CachedAccessControl.getInstance(ds, true).invalidate();
                } catch (Exception e) {
                    logger.error("Could not invalidate the access control", e);
                }
            }

            @Override
            public void waitUntil(DateTime time) throws InterruptedException {
                long wait = time.getMillis() - System.currentTimeMillis();
                if (wait > 0) Thread.sleep(wait);
            }
        };
    }

    /**
     * The stored file of a FILE sample: {@code <file dir>/<id>/<attribute>/<UTC yyyyMMddHHmmss>_<name>}
     * ({@code SQLDataSource.getFile}).
     *
     * @throws IOException if the name contains path parts
     */
    static File storedFile(long id, String attribute, DateTime timestamp, String fileName) throws IOException {
        if (fileName.isEmpty() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")
                || attribute.contains("..") || attribute.contains("/") || attribute.contains("\\")) {
            throw new IOException("Invalid file name");
        }
        return new File(Config.getFileDir().getAbsolutePath() + File.separator + id + File.separator + attribute
                + File.separator + DateTimeFormat.forPattern("yyyyMMddHHmmss").withZoneUTC().print(timestamp)
                + "_" + fileName);
    }

    // ---------------------------------------------------------------- answers

    /**
     * The answer of a write endpoint: status and body (text for errors, else JSON).
     */
    static final class Reply {
        final int status;
        final Object body;

        Reply(int status, Object body) {
            this.status = status;
            this.body = body;
        }

        static Reply ok(Object body) {
            return new Reply(200, body);
        }

        static Reply created(Object body) {
            return new Reply(201, body);
        }

        static Reply noContent() {
            return new Reply(204, null);
        }

        static Reply badRequest(String message) {
            return new Reply(400, message);
        }

        static Reply forbidden(String message) {
            return new Reply(403, message);
        }

        static Reply notFound(String message) {
            return new Reply(404, message);
        }

        /**
         * {@code 409} with the current state as JSON
         */
        static Reply conflict(Object current) {
            return new Reply(409, current);
        }

        Response toResponse() throws Exception {
            Response.ResponseBuilder builder = Response.status(status).header("Cache-Control", "no-store");
            if (body == null) return builder.build();
            if (body instanceof String) return builder.entity(body).type(MediaType.TEXT_PLAIN).build();
            return builder.entity(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
        }
    }

    /**
     * Ends the handling of a request with the answer (invalid input, missing object, …).
     */
    static final class Stop extends Exception {
        final transient Reply reply;

        Stop(Reply reply) {
            super(String.valueOf(reply.body));
            this.reply = reply;
        }

        static Stop badRequest(String message) {
            return new Stop(Reply.badRequest(message));
        }

        static Stop notFound(String message) {
            return new Stop(Reply.notFound(message));
        }
    }

    /**
     * One write request on the data source of the request.
     */
    interface Action {
        Reply apply(Store store) throws Exception;
    }

    /**
     * One request that also needs the data source itself (e.g. the read-only {@code LocalDataSource} for evaluations).
     */
    interface SqlAction {
        Reply apply(SQLDataSource ds, Store store) throws Exception;
    }

    /**
     * Runs the action on a new {@link SQLDataSource} with the user of the request and closes it.
     */
    static Response run(HttpHeaders headers, String what, Action action) {
        return runSql(headers, what, (ds, store) -> action.apply(store));
    }

    /**
     * Like {@link #run(HttpHeaders, String, Action)}, the action also gets the data source of the request.
     */
    static Response runSql(HttpHeaders headers, String what, SqlAction action) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(headers::getRequestHeader);
            return action.apply(ds, sqlStore(ds)).toResponse();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            if (ex instanceof Stop) {
                try {
                    return ((Stop) ex).reply.toResponse();
                } catch (Exception e) {
                    logger.error("Could not write the answer", e);
                }
            }
            logger.error("Could not {}", what, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    /**
     * Runs the action and maps {@link Stop} to its reply (for tests and callers without a request).
     */
    static Reply call(Store store, Action action) throws Exception {
        try {
            return action.apply(store);
        } catch (Stop stop) {
            return stop.reply;
        }
    }

    // ---------------------------------------------------------------- input

    /**
     * @return the JSON object of the body
     * @throws Stop {@code 400} if empty or not a JSON object
     */
    static JsonNode body(String body) throws Stop {
        if (body == null || body.trim().isEmpty()) throw Stop.badRequest("Missing body");
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node == null || !node.isObject()) throw Stop.badRequest("Body is not a JSON object");
            return node;
        } catch (Stop stop) {
            throw stop;
        } catch (Exception e) {
            throw Stop.badRequest("Invalid JSON");
        }
    }

    /**
     * @return the text of the field, null if missing or null
     * @throws Stop {@code 400} if it is not a text
     */
    static String text(JsonNode body, String field) throws Stop {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw Stop.badRequest("Invalid " + field);
        return node.asText();
    }

    /**
     * @throws Stop {@code 400} if missing or not a boolean
     */
    static boolean bool(JsonNode body, String field) throws Stop {
        JsonNode node = body.get(field);
        if (node == null || !node.isBoolean()) throw Stop.badRequest("Missing or invalid " + field);
        return node.asBoolean();
    }

    /**
     * @return false if missing
     * @throws Stop {@code 400} if not a boolean
     */
    static boolean optionalBool(JsonNode body, String field) throws Stop {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) return false;
        if (!node.isBoolean()) throw Stop.badRequest("Invalid " + field);
        return node.asBoolean();
    }

    /**
     * @return the time zone of the field ({@code timeZone}), else the fallback
     * @throws Stop {@code 400} for unknown time zones
     */
    static DateTimeZone zone(String id, DateTimeZone fallback) throws Stop {
        if (id == null || id.trim().isEmpty()) return fallback;
        try {
            return DateTimeZone.forID(id.trim());
        } catch (IllegalArgumentException e) {
            throw Stop.badRequest("Invalid timeZone");
        }
    }

    /**
     * @param text ISO 8601 date or date-time; without offset in the time zone
     * @throws Stop {@code 400} if missing or invalid
     */
    static DateTime timestamp(String text, DateTimeZone zone, String name) throws Stop {
        if (text == null || text.trim().isEmpty()) throw Stop.badRequest("Missing " + name);
        try {
            return new DateTime(text.trim(), zone).withZone(zone);
        } catch (IllegalArgumentException e) {
            throw Stop.badRequest("Invalid " + name);
        }
    }

    /**
     * @return the language of the parameter ({@code lang}), else of the first {@code Accept-Language}, else English
     */
    static String language(String lang, HttpHeaders headers) {
        if (lang != null && !lang.trim().isEmpty()) return ResourceWebRegisters.language(lang);
        try {
            List<Locale> locales = headers != null ? headers.getAcceptableLanguages() : null;
            if (locales != null && !locales.isEmpty() && !"*".equals(locales.get(0).getLanguage())) {
                return ResourceWebRegisters.language(locales.get(0).getLanguage());
            }
        } catch (Exception e) {
            logger.debug("No Accept-Language", e);
        }
        return "en";
    }

    // ---------------------------------------------------------------- versions and time stamps

    static String iso(long millis, DateTimeZone zone) {
        return ISODateTimeFormat.dateTime().print(new DateTime(millis, zone));
    }

    /**
     * @return the time stamp of a sample in the format of the samples
     */
    static String sampleTs(DateTime time) {
        return JsonFactory.sampleDTF.print(time);
    }

    static JsonSample sample(DateTime time, String value, String note) {
        JsonSample sample = new JsonSample();
        sample.setTs(sampleTs(time));
        sample.setValue(value);
        sample.setNote(note);
        return sample;
    }

    /**
     * @return the version of an entry: the time stamp of its latest sample (ISO, server time zone), null without sample
     */
    static String version(JsonSample latest) {
        Long millis = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
        return millis != null ? iso(millis, DateTimeZone.getDefault()) : null;
    }

    /**
     * @param given the version sent by the client
     * @return whether it is the version of the latest sample (same instant)
     * @throws Stop {@code 400} if missing or not a time stamp
     */
    static boolean sameVersion(String given, JsonSample latest) throws Stop {
        if (given == null || given.trim().isEmpty()) throw Stop.badRequest("version missing");
        long millis;
        try {
            millis = new DateTime(given.trim()).getMillis();
        } catch (IllegalArgumentException e) {
            throw Stop.badRequest("Invalid version");
        }
        Long current = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
        return current != null && current == millis;
    }

    // ---------------------------------------------------------------- user action log

    /**
     * {@code <id>:<attribute>|<count>|web:<action>}, the format of {@code ResourceSample.postSamples} with the web
     * action instead of the duration
     */
    static String sampleLog(long id, String attribute, int count, String action) {
        return String.format("%s:%s|%s|%s%s", id, attribute, count, ACTION_PREFIX, action);
    }

    /**
     * {@code <id>:<attribute>|<from> -> <to>|web:<action>}, the format of {@code ResourceSample.deleteSamples}
     */
    static String deleteLog(long id, String attribute, DateTime from, DateTime to, String action) {
        return String.format("%s:%s|%s -> %s|%s%s", id, attribute, from, to, ACTION_PREFIX, action);
    }

    /**
     * {@code <id>:<name>|web:<action>}, the format of {@code ResourceObject}
     */
    static String objectLog(long id, String name, String action) {
        return String.format("%s:%s|%s%s", id, name, ACTION_PREFIX, action);
    }

    /**
     * Writes one sample and logs it ({@code CREATE_SAMPLE}).
     */
    static void writeSample(Store store, long id, String attribute, int primitiveType, JsonSample sample, String action)
            throws Exception {
        int count = store.write(id, attribute, primitiveType, new ArrayList<>(Collections.singletonList(sample)));
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, sampleLog(id, attribute, count, action));
    }

    /**
     * Deletes the samples in the time frame and logs it ({@code DELETE_SAMPLE}).
     */
    static void deleteSamples(Store store, long id, String attribute, DateTime from, DateTime to, String action)
            throws Exception {
        store.delete(id, attribute, from, to);
        store.log(SQLDataSource.LOG_EVENT.DELETE_SAMPLE, deleteLog(id, attribute, from, to, action));
    }

    /**
     * @return the primitive type of the attribute of the class, the fallback if the class or type is unknown
     */
    static int primitiveType(Store store, JsonObject object, String attribute, int fallback) {
        org.jevis.commons.ws.json.JsonType type = store.classes().type(object.getJevisClass(), attribute);
        return type != null ? type.getPrimitiveType() : fallback;
    }

    /**
     * @return the object if the user may read it and it is not in the recycle bin, else null
     */
    static JsonObject readable(Store store, long id) throws Exception {
        JsonObject object = store.lookup().readable(id);
        return object != null && !ResourceWebIso.isDeleted(object) ? object : null;
    }
}
