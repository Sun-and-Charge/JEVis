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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.alarm.AlarmConfiguration;
import org.jevis.commons.alarm.AlarmType;
import org.jevis.commons.json.JsonAlarm;
import org.jevis.commons.json.JsonTools;
import org.jevis.commons.ws.json.JsonFactory;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Alarms for the web app, view only (see docs/WEB-API.md, W4.5), like the Control Center's "Alarm Plugin": JEAlarm
 * writes the alarms it finds as JSON files ({@link JsonAlarm}[]) into the attribute "Log File" of an "Alarm
 * Configuration"; an alarm is one entry of such a file. Confirmation is per configuration (latest "Alarm Checked").
 * <p>
 * Only configurations the user may read, that are enabled and not in the recycle bin are used; names and paths of rows
 * and sites are only given if the user may read them.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebAlarms {

    static final String CLASS_ALARM_CONFIGURATION = AlarmConfiguration.CLASS_NAME;
    static final String ATTRIBUTE_ENABLED = "Enabled";
    static final String ATTRIBUTE_CHECKED = AlarmConfiguration.ALARM_CHECKED;
    static final String ATTRIBUTE_LOG_FILE = "Log File";
    static final String STATE_OPEN = "open";
    static final String STATE_CHECKED = "checked";
    static final String STATE_ALL = "all";
    static final String CHECKED_BY_PREFIX = "Checked by ";
    /**
     * At most this many alarms in the answer of {@code GET /alarms}
     */
    static final int MAX_ALARMS = 5000;
    static final int DEFAULT_DAYS = 7;
    /**
     * Upper bound of the log file samples read per configuration
     */
    static final int FETCH_LIMIT = 50000;

    private static final Logger logger = LogManager.getLogger(ResourceWebAlarms.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------------------------------------------------------------- endpoint

    @GET
    @Path("alarms")
    @Produces(MediaType.APPLICATION_JSON)
    public Response alarms(@Context HttpHeaders httpHeaders, @QueryParam("from") String from,
                           @QueryParam("to") String to, @QueryParam("state") String state,
                           @QueryParam("timeZone") String timeZone, @QueryParam("lang") String lang) {
        DateTimeZone zone = ResourceWebNotes.zone(timeZone);
        DateTime[] range;
        String normalizedState;
        try {
            range = dayRange(from, to, zone, LocalDate.now(zone));
            normalizedState = state(state);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).type(MediaType.TEXT_PLAIN)
                    .header("Cache-Control", "no-store").build();
        }
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            ResourceWebRegisters.Classes classes = ResourceWebRegisters.Classes.fromCache();
            Map<String, Object> result = alarmsJson(lookup, classes, sqlStore(ds), range[0], range[1], normalizedState,
                    zone, ResourceWebRegisters.language(lang));
            return Response.ok(MAPPER.writeValueAsString(result)).type(MediaType.APPLICATION_JSON)
                    .header("Cache-Control", "no-store").build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the alarms", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    // ---------------------------------------------------------------- parameters

    /**
     * Days in the time zone, default the last {@link #DEFAULT_DAYS} days up to today, at most one year.
     *
     * @throws IllegalArgumentException for invalid days, {@code to} before {@code from} or more than one year
     */
    static DateTime[] dayRange(String from, String to, DateTimeZone zone, LocalDate today) {
        return ResourceWebNotes.dayRange(from, to, zone, today, Period.days(DEFAULT_DAYS), Period.years(1), "1 year");
    }

    /**
     * @return {@code open} (default), {@code checked} or {@code all}
     * @throws IllegalArgumentException for other values
     */
    static String state(String state) {
        if (state == null || state.trim().isEmpty()) return STATE_OPEN;
        String value = state.trim().toLowerCase(Locale.ROOT);
        if (STATE_OPEN.equals(value) || STATE_CHECKED.equals(value) || STATE_ALL.equals(value)) return value;
        throw new IllegalArgumentException("Invalid state");
    }

    // ---------------------------------------------------------------- data access

    /**
     * Samples, files and units of one request.
     */
    interface Store {
        /**
         * @return the samples of the attribute in the time frame (both inclusive), oldest first
         */
        List<JsonSample> samples(long id, String attribute, DateTime from, DateTime to, long limit) throws Exception;

        /**
         * @return the latest sample of the attribute or null
         */
        JsonSample latest(long id, String attribute) throws Exception;

        /**
         * @return the content of a file sample, null if the file does not exist
         */
        byte[] file(long id, String attribute, JsonSample sample) throws Exception;

        /**
         * @return the label of the display unit of the attribute, null if none
         */
        String unit(long id, String attribute) throws Exception;
    }

    static Store sqlStore(SQLDataSource ds) {
        return new Store() {
            @Override
            public List<JsonSample> samples(long id, String attribute, DateTime from, DateTime to, long limit) throws Exception {
                return ds.getSamples(id, attribute, from, to, limit);
            }

            @Override
            public JsonSample latest(long id, String attribute) throws Exception {
                return ds.getLastSample(id, attribute);
            }

            @Override
            public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
                return sampleFile(Config.getFileDir(), id, attribute, sample);
            }

            @Override
            public String unit(long id, String attribute) {
                return ResourceWebRegisters.unitLabel(ds.getAttribute(id, attribute));
            }
        };
    }

    /**
     * Reads the file of a file sample directly from the file directory, like {@code ResourceSample}:
     * {@code <file dir>/<id>/<attribute>/<UTC yyyyMMddHHmmss>_<file name>}. Stored names with a path, {@code ..} or
     * control characters are rejected, as is any file that would lie outside the attribute's directory.
     *
     * @return the content, null if the name is not safe or the file does not exist
     */
    static byte[] sampleFile(File fileDir, long id, String attribute, JsonSample sample) throws Exception {
        String name = sample.getValue();
        if (!isSafeStoredName(name) || !isSafeStoredName(attribute)) return null;
        DateTime ts = JsonFactory.sampleDTF.parseDateTime(sample.getTs());
        File dir = new File(new File(fileDir, String.valueOf(id)), attribute);
        File file = new File(dir, DateTimeFormat.forPattern("yyyyMMddHHmmss").withZoneUTC().print(ts) + "_" + name);
        java.nio.file.Path dirPath = dir.toPath().toAbsolutePath().normalize();
        if (!dirPath.equals(file.toPath().toAbsolutePath().normalize().getParent())) return null;
        return file.isFile() && file.canRead() ? Files.readAllBytes(file.toPath()) : null;
    }

    /**
     * @return whether the stored file name (or attribute name) can be used as part of a path: not empty, no
     * {@code ..}, no slash or backslash, no control characters
     */
    static boolean isSafeStoredName(String name) {
        if (name == null || name.trim().isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- configurations

    /**
     * State of one configuration: latest "Alarm Checked".
     */
    static final class Checked {
        final boolean checked;
        final String by;
        final Long at;

        Checked(boolean checked, String by, Long at) {
            this.checked = checked;
            this.by = by;
            this.at = at;
        }
    }

    static boolean isTrue(JsonSample sample) {
        if (sample == null || sample.getValue() == null) return false;
        String value = sample.getValue().trim();
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    /**
     * @return whether the latest sample of "Alarm Checked" is true and, then, its note (without
     * {@code "Checked by "}) and timestamp
     */
    static Checked checked(long configuration, Store store) {
        try {
            JsonSample sample = store.latest(configuration, ATTRIBUTE_CHECKED);
            if (!isTrue(sample)) return new Checked(false, null, null);
            String note = sample.getNote();
            if (note != null) {
                note = note.trim();
                if (note.startsWith(CHECKED_BY_PREFIX)) note = note.substring(CHECKED_BY_PREFIX.length()).trim();
                if (note.isEmpty()) note = null;
            }
            return new Checked(true, note, ResourceWebNotes.millis(sample.getTs()));
        } catch (Exception e) {
            logger.warn("Could not read {} of {}", ATTRIBUTE_CHECKED, configuration, e);
            return new Checked(false, null, null);
        }
    }

    /**
     * @return the readable "Alarm Configuration" objects that are not in the recycle bin and are enabled (latest
     * sample of "Enabled" is true)
     */
    static List<JsonObject> configurations(ResourceWebModules.Lookup lookup, Store store) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonObject object : lookup.readableOfClass(CLASS_ALARM_CONFIGURATION)) {
            if (ResourceWebIso.isDeleted(object)) continue;
            try {
                if (isTrue(store.latest(object.getId(), ATTRIBUTE_ENABLED))) result.add(object);
            } catch (Exception e) {
                logger.warn("Could not read {} of {}", ATTRIBUTE_ENABLED, object.getId(), e);
            }
        }
        return result;
    }

    /**
     * One alarm: an entry of a log file.
     */
    static final class Alarm {
        final long configuration;
        final long millis;
        final long logged;
        final int index;
        final JsonAlarm json;

        Alarm(long configuration, long millis, long logged, int index, JsonAlarm json) {
            this.configuration = configuration;
            this.millis = millis;
            this.logged = logged;
            this.index = index;
            this.json = json;
        }
    }

    /**
     * @return the entries of the log file samples in the time frame; unreadable files are skipped (logged)
     */
    static List<Alarm> alarmsOf(long configuration, DateTime from, DateTime to, Store store) throws Exception {
        List<Alarm> alarms = new ArrayList<>();
        for (JsonSample sample : store.samples(configuration, ATTRIBUTE_LOG_FILE, from, to, FETCH_LIMIT)) {
            Long logged = ResourceWebNotes.millis(sample.getTs());
            JsonAlarm[] entries = read(configuration, sample, store);
            if (logged == null || entries == null) continue;
            for (int i = 0; i < entries.length; i++) {
                JsonAlarm entry = entries[i];
                Long millis = entry != null ? alarmMillis(entry.getTimeStamp()) : null;
                if (millis == null) {
                    logger.debug("Alarm {} of {} at {} without valid time stamp", i, configuration, sample.getTs());
                    continue;
                }
                alarms.add(new Alarm(configuration, millis, logged, i, entry));
            }
        }
        return alarms;
    }

    /**
     * @return the entries of a log file, null if it cannot be read
     */
    static JsonAlarm[] read(long configuration, JsonSample sample, Store store) {
        try {
            byte[] bytes = store.file(configuration, ATTRIBUTE_LOG_FILE, sample);
            if (bytes == null) {
                logger.warn("Alarm log file of {} at {} not found", configuration, sample.getTs());
                return null;
            }
            JsonAlarm[] entries = JsonTools.objectMapper().readValue(bytes, JsonAlarm[].class);
            return entries != null ? entries : new JsonAlarm[0];
        } catch (Exception e) {
            logger.warn("Could not read the alarm log file of {} at {}", configuration, sample.getTs(), e);
            return null;
        }
    }

    /**
     * @return the time of an alarm ({@code DateTime.toString()} of JEAlarm, ISO 8601), null if invalid
     */
    static Long alarmMillis(String timeStamp) {
        if (timeStamp == null || timeStamp.trim().isEmpty()) return null;
        try {
            return new DateTime(timeStamp.trim()).getMillis();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- answer

    /**
     * {@code GET /alarms}: the alarms of the log file samples in the time frame of the readable, enabled
     * configurations in the state, newest first.
     */
    static Map<String, Object> alarmsJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                          Store store, DateTime from, DateTime to, String state, DateTimeZone zone,
                                          String language) throws Exception {
        Map<Long, Checked> checkedById = new HashMap<>();
        List<Alarm> alarms = new ArrayList<>();
        for (JsonObject configuration : configurations(lookup, store)) {
            Checked checked = checked(configuration.getId(), store);
            if (STATE_OPEN.equals(state) && checked.checked) continue;
            if (STATE_CHECKED.equals(state) && !checked.checked) continue;
            checkedById.put(configuration.getId(), checked);
            try {
                alarms.addAll(alarmsOf(configuration.getId(), from, to, store));
            } catch (Exception e) {
                logger.warn("Could not read the alarms of {}", configuration.getId(), e);
            }
        }
        alarms.sort(Comparator.comparingLong((Alarm a) -> a.millis).reversed()
                .thenComparing(Comparator.comparingLong((Alarm a) -> a.logged).reversed())
                .thenComparingLong(a -> a.configuration).thenComparingInt(a -> a.index));
        boolean truncated = alarms.size() > MAX_ALARMS;
        if (truncated) alarms = new ArrayList<>(alarms.subList(0, MAX_ALARMS));

        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        Map<Long, Map<String, Object>> configurationJson = new HashMap<>();
        Map<Long, Map<String, Object>> rows = new HashMap<>();
        Map<String, Map<String, Object>> sites = new HashMap<>();
        Map<String, String> units = new HashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Alarm alarm : alarms) {
            JsonAlarm entry = alarm.json;
            Long rowId = entry.getObject();
            Map<String, Object> config = configurationJson.computeIfAbsent(alarm.configuration,
                    id -> configurationJson(lookup.object(id), checkedById.get(id), zone));
            Map<String, Object> row = rowId != null
                    ? rows.computeIfAbsent(rowId, id -> ResourceWebNotes.rowJson(id, lookup, buildingClasses)) : null;
            Map<String, Object> site = sites.computeIfAbsent(rowId + "/" + alarm.configuration,
                    k -> site(rowId, alarm.configuration, lookup, buildingClasses));
            String attribute = entry.getAttribute();
            String unit = units.computeIfAbsent(rowId + "/" + attribute, k -> unit(rowId, attribute, lookup, store));

            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", alarm.configuration + "-" + alarm.millis + "-" + alarm.index);
            json.put("timestamp", ISODateTimeFormat.dateTime().print(new DateTime(alarm.millis, zone)));
            json.put("logged", ISODateTimeFormat.dateTime().print(new DateTime(alarm.logged, zone)));
            json.put("configuration", config);
            json.put("row", row);
            json.put("site", site);
            json.put("attribute", attribute);
            json.put("isValue", entry.getIsValue());
            json.put("operator", operator(entry.getOperator()));
            json.put("shouldBeValue", entry.getShouldBeValue());
            json.put("tolerance", entry.getTolerance());
            json.put("unit", unit);
            AlarmType type = entry.getAlarmType() != null ? entry.getAlarmType() : AlarmType.NONE;
            json.put("type", type.name());
            json.put("typeName", typeName(type, language));
            json.put("logValue", entry.getLogValue());
            json.put("logValueName", logValueName(entry.getLogValue(), language));
            list.add(json);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", ISODateTimeFormat.dateTime().print(from));
        result.put("to", ISODateTimeFormat.dateTime().print(to));
        result.put("state", state);
        result.put("alarms", list);
        result.put("truncated", truncated);
        return result;
    }

    private static Map<String, Object> configurationJson(JsonObject configuration, Checked checked, DateTimeZone zone) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", configuration.getId());
        json.put("name", configuration.getName());
        boolean isChecked = checked != null && checked.checked;
        json.put("checked", isChecked);
        json.put("checkedBy", isChecked ? checked.by : null);
        json.put("checkedAt", isChecked && checked.at != null
                ? ISODateTimeFormat.dateTime().print(new DateTime(checked.at, zone)) : null);
        return json;
    }

    /**
     * @return the next readable building above the row, else above the configuration, else null
     */
    static Map<String, Object> site(Long rowId, long configuration, ResourceWebModules.Lookup lookup,
                                    Set<String> buildingClasses) {
        JsonObject row = lookup.object(rowId);
        Map<String, Object> site = row != null ? ResourceWebNotes.siteJson(row, lookup, buildingClasses) : null;
        if (site != null) return site;
        JsonObject config = lookup.object(configuration);
        return config != null ? ResourceWebNotes.siteJson(config, lookup, buildingClasses) : null;
    }

    /**
     * @return the display unit of the attribute of the row, {@code ""} if none or the row is not readable
     */
    static String unit(Long rowId, String attribute, ResourceWebModules.Lookup lookup, Store store) {
        if (rowId == null || attribute == null || lookup.readable(rowId) == null) return "";
        try {
            String unit = store.unit(rowId, attribute);
            return unit != null ? unit : "";
        } catch (Exception e) {
            logger.debug("Could not read the unit of {} of {}", attribute, rowId, e);
            return "";
        }
    }

    /**
     * Like the operator column of the Control Center's alarm table.
     */
    static String operator(String operator) {
        if (operator == null) return null;
        switch (operator) {
            case ">":
                return ">";
            case ">=":
            case "":
            case "≥":
                return "≥";
            case "=":
                return "=";
            case "!=":
            case "≠":
                return "≠";
            case "<":
                return "<";
            case "<=":
            case "≤":
                return "≤";
            default:
                return operator;
        }
    }

    /**
     * Like {@code Alarm.getTranslatedTypeName}.
     */
    static String typeName(AlarmType type, String language) {
        String key;
        switch (type) {
            case L1:
                key = "l1";
                break;
            case L2:
                key = "l2";
                break;
            case D1:
                key = "d1";
                break;
            case D2:
                key = "d2";
                break;
            case DYNAMIC:
                key = "dynamic";
                break;
            case STATIC:
                key = "static";
                break;
            case GAP:
                key = "gap";
                break;
            case NONE:
            default:
                key = "none";
        }
        String name = ResourceWebNotes.NoteTags.translate("plugin.alarm.table.translation." + key, language);
        return name != null ? name : type.name();
    }

    /**
     * Like {@code AlarmPlugin.getAlarm}: {@code 4} silent, {@code 2} standby, {@code 1} normal, else system.
     */
    static String logValueName(Integer logValue, String language) {
        String key;
        int value = logValue != null ? logValue : 0;
        switch (value) {
            case 4:
                key = "silent";
                break;
            case 2:
                key = "standby";
                break;
            case 1:
                key = "normal";
                break;
            case 0:
            default:
                key = "system";
        }
        String name = ResourceWebNotes.NoteTags.translate("plugin.alarm.table.alarm." + key, language);
        return name != null ? name : key;
    }

    // ---------------------------------------------------------------- navigation

    /**
     * @return {@code alarms}: number of readable, enabled configurations; {@code alarmsOpen}: number of alarms in the
     * log file samples of the last {@link #DEFAULT_DAYS} days of those that are not confirmed
     */
    static Map<String, Object> moduleCounts(ResourceWebModules.Lookup lookup, Store store, DateTime now) {
        List<JsonObject> configurations = configurations(lookup, store);
        int open = 0;
        DateTime from = now.minusDays(DEFAULT_DAYS);
        for (JsonObject configuration : configurations) {
            if (checked(configuration.getId(), store).checked) continue;
            try {
                for (JsonSample sample : store.samples(configuration.getId(), ATTRIBUTE_LOG_FILE, from, now, FETCH_LIMIT)) {
                    JsonAlarm[] entries = read(configuration.getId(), sample, store);
                    if (entries != null) open += entries.length;
                }
            } catch (Exception e) {
                logger.warn("Could not count the alarms of {}", configuration.getId(), e);
            }
        }
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put("alarms", configurations.size());
        modules.put("alarmsOpen", open);
        return modules;
    }
}
