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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.alarm.AlarmScope;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Editing of the alarm configurations (stage E7.1, see docs/WEB-API.md): the attributes of an "Alarm Configuration"
 * and of its "E-Mail Notification", written as the Control Center writes them ({@code TargetEditor},
 * {@code CreateAlarms}) and as JEAlarm ({@code AlarmConfiguration}, {@code AlarmProcess}) and the notifier
 * ({@code EmailNotification}) read them.
 * <p>
 * All writes use the rights of the logged-in user ({@link WebEdit.Store}).
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditAlarms {

    static final String ACTION_CREATE = "alarm-config-create";
    static final String ACTION_SAVE = "alarm-config-save";
    static final String ACTION_DELETE = "alarm-config-delete";
    static final String CLASS_CONFIGURATION = ResourceWebAlarms.CLASS_ALARM_CONFIGURATION;
    static final String CLASS_DIRECTORY = "Alarm Directory";
    static final String CLASS_NOTIFICATION = "Notification";
    static final String CLASS_EMAIL = "E-Mail Notification";
    static final String CLASS_DATA_DIRECTORY = "Data Directory";
    static final String CLASS_DATA = ResourceWebNotes.CLASS_DATA;
    static final String CLASS_CLEAN_DATA = ResourceWebNotes.CLASS_CLEAN_DATA;

    static final String ATTRIBUTE_ENABLED = ResourceWebAlarms.ATTRIBUTE_ENABLED;
    static final String ATTRIBUTE_DISABLE_LINK = "Disable Link";
    static final String ATTRIBUTE_INCLUDE_LIMITS = "Include Limits In Notification";
    static final String ATTRIBUTE_SCOPE = "Alarm Scope";
    static final String ATTRIBUTE_OBJECTS = "Alarm Objects";
    static final String ATTRIBUTE_TIME_STAMP = "Time Stamp";
    static final String ATTRIBUTE_PERIOD = "Alarm Period";
    static final String ATTRIBUTE_CUSTOM_SCHEDULE = "Custom Schedule Object";
    static final String ATTRIBUTE_BIND_TO_DATA = "Bind to Data";
    /**
     * The attributes of a configuration the version is taken from (not the JEAlarm outputs "Time Stamp", "Log",
     * "Log File" and "Alarm Checked")
     */
    static final List<String> CONFIGURATION_ATTRIBUTES = Collections.unmodifiableList(Arrays.asList(ATTRIBUTE_ENABLED,
            ATTRIBUTE_DISABLE_LINK, ATTRIBUTE_INCLUDE_LIMITS, ATTRIBUTE_SCOPE, ATTRIBUTE_OBJECTS, ATTRIBUTE_PERIOD,
            ATTRIBUTE_CUSTOM_SCHEDULE, ATTRIBUTE_BIND_TO_DATA));

    static final String MAIL_ENABLED = "Enabled";
    static final String MAIL_RECIPIENTS = "Recipients";
    static final String MAIL_USERS = "Recipient Users";
    static final String MAIL_GROUPS = "Recipient Groups";
    static final String MAIL_CC = "Carbon Copys";
    static final String MAIL_BCC = "Blind Carbon Copys";
    static final String MAIL_SUBJECT = "Subject";
    static final String MAIL_MESSAGE = "Message";
    static final String MAIL_HTML = "HTML E-Mail";
    static final String MAIL_ATTACHMENTS = "Attachments";
    static final List<String> NOTIFICATION_ATTRIBUTES = Collections.unmodifiableList(Arrays.asList(MAIL_ENABLED,
            MAIL_RECIPIENTS, MAIL_USERS, MAIL_GROUPS, MAIL_CC, MAIL_BCC, MAIL_SUBJECT, MAIL_MESSAGE, MAIL_HTML,
            MAIL_ATTACHMENTS));

    /**
     * {@code AlarmScope} values
     */
    static final List<String> SCOPES = Collections.unmodifiableList(Arrays.asList(AlarmScope.COMPLETE.name(),
            AlarmScope.SELECTED.name(), AlarmScope.WITHOUT_SELECTED.name(), AlarmScope.NONE.name()));
    /**
     * The values of "Alarm Period" {@code AlarmConfiguration.getAlarmPeriod} accepts
     */
    static final List<String> PERIODS = Collections.unmodifiableList(Arrays.asList("MINUTELY", "QUARTER_HOURLY",
            "HOURLY", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "CUSTOM", "NONE"));
    static final String PERIOD_CUSTOM = "CUSTOM";
    static final String DEFAULT_SCOPE = AlarmScope.COMPLETE.name();
    static final String DEFAULT_PERIOD = "DAILY";
    static final String DEFAULT_ATTRIBUTE = "Value";
    static final int MAX_NAME_LENGTH = 200;
    static final int MAX_SUBJECT_LENGTH = 200;
    static final int MAX_MESSAGE_LENGTH = 5000;
    static final int MAX_OBJECTS = 2000;
    static final int MAX_RECIPIENTS = 200;
    private static final Pattern MAIL = Pattern.compile("[^@\\s;,<>\"]+@[^@\\s;,<>\"]+\\.[^@\\s;,<>\"]+");
    private static final Object CREATE_LOCK = new Object();
    private static final Logger logger = LogManager.getLogger(ResourceWebEditAlarms.class);

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("alarms/settings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response settings(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "load the alarm settings", store -> settings(store, language));
    }

    @POST
    @Path("alarms/settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "create an alarm configuration", store -> create(store, body, language, DateTime.now()));
    }

    @PUT
    @Path("alarms/settings/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang,
                           String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "save the alarm configuration " + id,
                store -> update(store, id, body, language, DateTime.now()));
    }

    @DELETE
    @Path("alarms/settings/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @QueryParam("version") String version, @QueryParam("lang") String lang) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "delete the alarm configuration " + id, store -> delete(store, id, version, language));
    }

    // ---------------------------------------------------------------- stored values

    /**
     * One entry of "Alarm Objects" ({@code id:Attribute}, as {@code TargetHelper}); the attribute is null for a bare id.
     */
    static final class Target {
        final long id;
        final String attribute;

        Target(long id, String attribute) {
            this.id = id;
            this.attribute = attribute;
        }

        String text() {
            return attribute != null ? id + ":" + attribute : String.valueOf(id);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Target)) return false;
            Target other = (Target) o;
            return id == other.id && Objects.equals(attribute, other.attribute);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, attribute);
        }
    }

    /**
     * @return the entries of a target list like {@code TargetHelper}: separated by {@code ;}, {@code id} or
     * {@code id:Attribute}; invalid entries are left out
     */
    static List<Target> targets(String text) {
        List<Target> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return list;
        for (String part : text.split(";")) {
            int separator = part.indexOf(':');
            String idText = separator >= 0 ? part.substring(0, separator) : part;
            String attribute = separator >= 0 ? part.substring(separator + 1) : null;
            if (attribute != null && attribute.isEmpty()) continue;
            try {
                list.add(new Target(Long.parseLong(idText.trim()), attribute));
            } catch (NumberFormatException e) {
                logger.debug("Invalid target {}", part);
            }
        }
        return list;
    }

    static String targetsText(List<Target> targets) {
        StringBuilder sb = new StringBuilder();
        for (Target target : targets) {
            if (sb.length() > 0) sb.append(";");
            sb.append(target.text());
        }
        return sb.toString();
    }

    /**
     * @return the ids of "Recipient Users"/"Recipient Groups" as {@code MailRecipients.parseIds} reads them
     */
    static List<Long> recipientIds(String text) {
        List<Long> ids = new ArrayList<>();
        if (text == null) return ids;
        for (String part : text.split("[;,\\s]+")) {
            String id = part.split(":")[0].trim();
            if (id.isEmpty()) continue;
            try {
                Long value = Long.parseLong(id);
                if (!ids.contains(value)) ids.add(value);
            } catch (NumberFormatException e) {
                logger.debug("Invalid recipient id {}", part);
            }
        }
        return ids;
    }

    /**
     * @return the ids as the Control Center's {@code TargetEditor} writes them ({@code 12:Value;15:Value})
     */
    static String recipientIdsText(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(";");
            sb.append(id).append(":").append(DEFAULT_ATTRIBUTE);
        }
        return sb.toString();
    }

    /**
     * @return the addresses of "Recipients", "Carbon Copys" or "Blind Carbon Copys" as {@code EmailNotification}
     * splits them ({@code ;}, trimmed, empty ones left out)
     */
    static List<String> addresses(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        for (String part : text.split(";")) {
            String address = part.trim();
            if (!address.isEmpty() && !"null".equals(address)) list.add(address);
        }
        return list;
    }

    static boolean isMail(String address) {
        return address != null && MAIL.matcher(address).matches();
    }

    private static String value(JsonSample sample) {
        return sample != null ? sample.getValue() : null;
    }

    /**
     * The stored "E-Mail Notification" of a configuration.
     */
    static final class Notification {
        final JsonObject object;
        JsonSample enabled;
        JsonSample html;
        String recipients;
        String users;
        String groups;
        String cc;
        String bcc;
        String subject;
        String message;

        Notification(JsonObject object) {
            this.object = object;
        }
    }

    static Notification notification(WebEdit.Store store, JsonObject object) throws Exception {
        Notification n = new Notification(object);
        long id = object.getId();
        n.enabled = store.latest(id, MAIL_ENABLED);
        n.html = store.latest(id, MAIL_HTML);
        n.recipients = value(store.latest(id, MAIL_RECIPIENTS));
        n.users = value(store.latest(id, MAIL_USERS));
        n.groups = value(store.latest(id, MAIL_GROUPS));
        n.cc = value(store.latest(id, MAIL_CC));
        n.bcc = value(store.latest(id, MAIL_BCC));
        n.subject = value(store.latest(id, MAIL_SUBJECT));
        n.message = value(store.latest(id, MAIL_MESSAGE));
        return n;
    }

    /**
     * The stored settings of an "Alarm Configuration".
     */
    static final class Config {
        final JsonObject object;
        JsonSample enabled;
        JsonSample bindToData;
        JsonSample includeLimits;
        JsonSample disableLink;
        String scopeText;
        String objectsText;
        String periodText;
        String timeStamp;
        boolean customSchedule;
        /**
         * the children inheriting from "Notification" outside the recycle bin (readable or not; JEAlarm sends nothing
         * unless there is exactly one)
         */
        List<JsonObject> notifications = new ArrayList<>();
        /**
         * the single notification if it is a readable "E-Mail Notification", else null
         */
        Notification notification;

        Config(JsonObject object) {
            this.object = object;
        }

        /**
         * {@code AlarmConfiguration.getAlarmScope}: one of the values, else {@code NONE}
         */
        String scope() {
            return scopeText != null && SCOPES.contains(scopeText) ? scopeText : AlarmScope.NONE.name();
        }

        /**
         * {@code AlarmConfiguration.getAlarmPeriod}: one of the values, else {@code NONE}
         */
        String period() {
            return periodText != null && PERIODS.contains(periodText) ? periodText : "NONE";
        }

        List<Target> objects() {
            return targets(objectsText);
        }
    }

    static Config config(WebEdit.Store store, JsonObject object) throws Exception {
        Config c = new Config(object);
        long id = object.getId();
        c.enabled = store.latest(id, ATTRIBUTE_ENABLED);
        c.bindToData = store.latest(id, ATTRIBUTE_BIND_TO_DATA);
        c.includeLimits = store.latest(id, ATTRIBUTE_INCLUDE_LIMITS);
        c.disableLink = store.latest(id, ATTRIBUTE_DISABLE_LINK);
        c.scopeText = value(store.latest(id, ATTRIBUTE_SCOPE));
        c.objectsText = value(store.latest(id, ATTRIBUTE_OBJECTS));
        c.periodText = value(store.latest(id, ATTRIBUTE_PERIOD));
        c.timeStamp = value(store.latest(id, ATTRIBUTE_TIME_STAMP));
        String custom = value(store.latest(id, ATTRIBUTE_CUSTOM_SCHEDULE));
        c.customSchedule = custom != null && !custom.trim().isEmpty();
        ResourceWebModules.Lookup lookup = store.lookup();
        Set<String> notificationClasses = store.classes().withHeirs(CLASS_NOTIFICATION);
        notificationClasses.add(CLASS_NOTIFICATION);
        notificationClasses.addAll(store.classes().withHeirs(CLASS_EMAIL));
        for (JsonObject child : lookup.all()) {
            Long parent = lookup.parent(child.getId());
            if (parent != null && parent == id && !ResourceWebIso.isDeleted(child)
                    && notificationClasses.contains(child.getJevisClass())) {
                c.notifications.add(child);
            }
        }
        c.notifications.sort(Comparator.comparingLong(JsonObject::getId));
        if (c.notifications.size() == 1) {
            JsonObject single = c.notifications.get(0);
            if (store.classes().withHeirs(CLASS_EMAIL).contains(single.getJevisClass()) && lookup.readable(single.getId()) != null) {
                c.notification = notification(store, single);
            }
        }
        return c;
    }

    /**
     * @return the readable configuration outside the recycle bin, else null
     */
    static JsonObject configurationObject(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        return object != null && store.classes().withHeirs(CLASS_CONFIGURATION).contains(object.getJevisClass())
                ? object : null;
    }

    /**
     * @return the latest sample over the attributes of the objects
     */
    static JsonSample latestOf(WebEdit.Store store, long id, List<String> attributes, JsonSample latest) throws Exception {
        for (String attribute : attributes) {
            JsonSample sample = store.latest(id, attribute);
            Long millis = sample != null ? ResourceWebNotes.millis(sample.getTs()) : null;
            Long current = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
            if (millis != null && (current == null || millis > current)) latest = sample;
        }
        return latest;
    }

    /**
     * @return the latest sample of the configuration's attributes and of its single notification's attributes
     */
    static JsonSample versionSample(WebEdit.Store store, JsonObject configuration) throws Exception {
        JsonSample latest = latestOf(store, configuration.getId(), CONFIGURATION_ATTRIBUTES, null);
        Config c = config(store, configuration);
        if (c.notifications.size() == 1) {
            latest = latestOf(store, c.notifications.get(0).getId(), NOTIFICATION_ATTRIBUTES, latest);
        }
        return latest;
    }

    // ---------------------------------------------------------------- reading

    /**
     * @return the building and organisation classes (with heirs)
     */
    static Set<String> siteClasses(ResourceWebRegisters.Classes classes) {
        Set<String> sites = new HashSet<>(classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING));
        sites.add(ResourceWebNavigation.CLASS_BUILDING);
        sites.addAll(classes.withHeirs(ResourceWebNavigation.CLASS_ORGANIZATION));
        sites.add(ResourceWebNavigation.CLASS_ORGANIZATION);
        return sites;
    }

    /**
     * @return whether a building or organisation lies above the object
     */
    static boolean belowSite(ResourceWebModules.Lookup lookup, JsonObject object, Set<String> siteClasses) {
        Set<Long> visited = new HashSet<>();
        Long current = lookup.parent(object.getId());
        while (current != null && visited.add(current)) {
            JsonObject parent = lookup.object(current);
            if (parent == null) return false;
            if (siteClasses.contains(parent.getJevisClass())) return true;
            current = lookup.parent(current);
        }
        return false;
    }

    /**
     * @return the readable "Alarm Directory" objects below a building or organisation where the user may create an
     * "Alarm Configuration", as {@code [{id, name, path, site}]}
     */
    static List<Map<String, Object>> targetsJson(WebEdit.Store store) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        Set<String> siteClasses = siteClasses(classes);
        List<Map<String, Object>> targets = new ArrayList<>();
        for (JsonObject directory : ResourceWebRegisters.readableOfClasses(lookup, directoryClasses(classes))) {
            if (!belowSite(lookup, directory, siteClasses) || !store.rights().canCreate(directory, CLASS_CONFIGURATION)) {
                continue;
            }
            targets.add(header(directory, lookup, buildingClasses));
        }
        targets.sort(ResourceWebReports.order());
        return targets;
    }

    private static Set<String> directoryClasses(ResourceWebRegisters.Classes classes) {
        Set<String> set = new HashSet<>(classes.withHeirs(CLASS_DIRECTORY));
        set.add(CLASS_DIRECTORY);
        return set;
    }

    private static Map<String, Object> header(JsonObject object, ResourceWebModules.Lookup lookup, Set<String> buildingClasses) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("path", ResourceWebNotes.path(object, lookup, buildingClasses));
        json.put("site", ResourceWebNotes.siteJson(object, lookup, buildingClasses));
        return json;
    }

    /**
     * @return {@code [{id, attribute, name, path, readable}]} of the targets (name and path only if readable)
     */
    static List<Map<String, Object>> objectsJson(List<Target> targets, ResourceWebModules.Lookup lookup,
                                                 Set<String> buildingClasses) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Target target : targets) {
            JsonObject object = lookup.readable(target.id);
            if (object != null && ResourceWebIso.isDeleted(object)) object = null;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", target.id);
            json.put("attribute", target.attribute != null ? target.attribute : DEFAULT_ATTRIBUTE);
            json.put("name", object != null ? object.getName() : null);
            json.put("path", object != null ? ResourceWebNotes.path(object, lookup, buildingClasses) : null);
            json.put("readable", object != null);
            list.add(json);
        }
        return list;
    }

    private static List<Map<String, Object>> peopleJson(ResourceWebReadingRounds.Session context, List<Long> ids)
            throws Exception {
        List<Map<String, Object>> people = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> person = ResourceWebReadingRounds.person(context, id);
            if (person != null) people.add(person);
        }
        return people;
    }

    static Map<String, Object> notificationJson(ResourceWebReadingRounds.Session context, Notification n) throws Exception {
        if (n == null) return null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", n.object.getId());
        json.put("enabled", ResourceWebAlarms.isTrue(n.enabled));
        json.put("recipientUsers", peopleJson(context, recipientIds(n.users)));
        json.put("recipientGroups", peopleJson(context, recipientIds(n.groups)));
        json.put("recipients", addresses(n.recipients));
        json.put("cc", addresses(n.cc));
        json.put("bcc", addresses(n.bcc));
        json.put("subject", n.subject);
        json.put("message", n.message);
        json.put("html", ResourceWebAlarms.isTrue(n.html));
        json.put("canWrite", context.rights.canWrite(n.object));
        return json;
    }

    /**
     * @return the stored "Time Stamp" (JEAlarm's next start) as ISO in the time zone, null if none or invalid
     */
    static String timeStamp(String stored, DateTimeZone zone) {
        if (stored == null || stored.trim().isEmpty()) return null;
        try {
            return WebEdit.iso(new DateTime(stored.trim()).getMillis(), zone);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Map<String, Object> configurationJson(ResourceWebReadingRounds.Session context, Config c) throws Exception {
        WebEdit.Store store = context.store;
        ResourceWebModules.Lookup lookup = context.lookup;
        Set<String> buildingClasses = context.classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        JsonObject object = c.object;
        Map<String, Object> json = header(object, lookup, buildingClasses);
        json.put("enabled", ResourceWebAlarms.isTrue(c.enabled));
        json.put("scope", c.scope());
        json.put("objects", objectsJson(c.objects(), lookup, buildingClasses));
        json.put("period", c.period());
        json.put("customSchedule", c.customSchedule);
        json.put("bindToData", ResourceWebAlarms.isTrue(c.bindToData));
        json.put("includeLimits", ResourceWebAlarms.isTrue(c.includeLimits));
        json.put("disableLink", ResourceWebAlarms.isTrue(c.disableLink));
        json.put("timeStamp", timeStamp(c.timeStamp, store.workDay(object).zone));
        json.put("notification", notificationJson(context, c.notification));
        json.put("multipleNotifications", c.notifications.size() > 1);
        json.put("canWrite", context.rights.canWrite(object));
        json.put("canDelete", context.rights.canDelete(object));
        json.put("version", ResourceWebEditAnalysis.version(versionSample(store, object)));
        return json;
    }

    static Map<String, Object> configurationJson(WebEdit.Store store, JsonObject object) throws Exception {
        return configurationJson(new ResourceWebReadingRounds.Session(store, DateTime.now()), config(store, object));
    }

    /**
     * @return the readable users and groups as {@code [{id, name, kind, email}]} for the recipients (as
     * {@code GET /reading-rounds/options})
     */
    static List<Map<String, Object>> peopleOptions(WebEdit.Store store, ResourceWebModules.Lookup lookup) throws Exception {
        List<Map<String, Object>> people = new ArrayList<>();
        for (JsonObject object : lookup.readable()) {
            String kind = ResourceWebReadingRounds.kind(object);
            if (kind == null || ResourceWebIso.isDeleted(object)) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", object.getId());
            json.put("name", ResourceWebReadingRounds.personName(store, object));
            json.put("kind", kind);
            String mail = ResourceWebReadingRounds.KIND_USER.equals(kind) ? value(store.latest(object.getId(), "E-Mail")) : null;
            json.put("email", mail != null && !mail.trim().isEmpty());
            people.add(json);
        }
        people.sort(Comparator.comparing((Map<String, Object> p) -> String.valueOf(p.get("name")).toLowerCase(Locale.ROOT))
                .thenComparingLong(p -> (Long) p.get("id")));
        return people;
    }

    /**
     * {@code GET /alarms/settings}
     */
    static WebEdit.Reply settings(WebEdit.Store store, String language) throws Exception {
        ResourceWebReadingRounds.Session context = new ResourceWebReadingRounds.Session(store, DateTime.now());
        ResourceWebModules.Lookup lookup = store.lookup();
        List<Map<String, Object>> configurations = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, store.classes().withHeirs(CLASS_CONFIGURATION))) {
            try {
                configurations.add(configurationJson(context, config(store, object)));
            } catch (Exception e) {
                logger.warn("Could not read the alarm configuration {}", object.getId(), e);
            }
        }
        configurations.sort(ResourceWebReports.order());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configurations", configurations);
        result.put("targets", targetsJson(store));
        result.put("people", peopleOptions(store, lookup));
        return WebEdit.Reply.ok(result);
    }

    // ---------------------------------------------------------------- scope of a row

    /**
     * @return whether the clean data row is in the scope {@code COMPLETE} of the configuration
     * ({@code AlarmProcess.getCompleteList}: the "Clean Data" objects below the "Data Directory" children of the
     * parent of the configuration's directory, through "Data Directory", "Data" and "Clean Data" objects)
     */
    static boolean inCompleteScope(ResourceWebModules.Lookup lookup, long configuration, JsonObject row) {
        if (!CLASS_CLEAN_DATA.equals(row.getJevisClass())) return false;
        Long directory = lookup.parent(configuration);
        Long building = directory != null ? lookup.parent(directory) : null;
        if (building == null) return false;
        Set<Long> visited = new HashSet<>();
        Long current = lookup.parent(row.getId());
        while (current != null && visited.add(current)) {
            JsonObject parent = lookup.object(current);
            if (parent == null || ResourceWebIso.isDeleted(parent)) return false;
            String jevisClass = parent.getJevisClass();
            if (CLASS_DATA_DIRECTORY.equals(jevisClass) && building.equals(lookup.parent(current))) return true;
            if (!CLASS_DATA_DIRECTORY.equals(jevisClass) && !CLASS_DATA.equals(jevisClass)
                    && !CLASS_CLEAN_DATA.equals(jevisClass)) {
                return false;
            }
            current = lookup.parent(current);
        }
        return false;
    }

    /**
     * @return whether JEAlarm checks the row for the configuration ({@code AlarmProcess.getAllCorrespondingCleanDataObjects})
     */
    static boolean inScope(ResourceWebModules.Lookup lookup, Config c, JsonObject row) {
        boolean selected = false;
        for (Target target : c.objects()) {
            if (target.id == row.getId()) selected = true;
        }
        switch (c.scope()) {
            case "COMPLETE":
                return inCompleteScope(lookup, c.object.getId(), row);
            case "SELECTED":
                return selected;
            case "WITHOUT_SELECTED":
                return !selected && inCompleteScope(lookup, c.object.getId(), row);
            default:
                return false;
        }
    }

    /**
     * @return the readable configurations outside the recycle bin whose scope includes the row, as
     * {@code [{id, name, enabled}]}
     */
    static List<Map<String, Object>> configurationsOfRow(WebEdit.Store store, JsonObject row) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, store.classes().withHeirs(CLASS_CONFIGURATION))) {
            Config c = new Config(object);
            c.scopeText = value(store.latest(object.getId(), ATTRIBUTE_SCOPE));
            c.objectsText = value(store.latest(object.getId(), ATTRIBUTE_OBJECTS));
            if (!inScope(lookup, c, row)) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", object.getId());
            json.put("name", object.getName());
            json.put("enabled", ResourceWebAlarms.isTrue(store.latest(object.getId(), ATTRIBUTE_ENABLED)));
            list.add(json);
        }
        list.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("name")))
                .thenComparingLong(m -> (Long) m.get("id")));
        return list;
    }

    // ---------------------------------------------------------------- input

    /**
     * The changes of a POST/PUT (null: not sent).
     */
    static final class Change {
        String name;
        Boolean enabled;
        String scope;
        List<Target> objects;
        String period;
        Boolean bindToData;
        Boolean includeLimits;
        Boolean disableLink;
        MailChange notification;
    }

    static final class MailChange {
        Boolean enabled;
        List<Long> users;
        List<Long> groups;
        List<String> recipients;
        List<String> cc;
        List<String> bcc;
        String subject;
        String message;
        Boolean html;
    }

    private static Boolean optionalBoolean(JsonNode json, String field, List<String> messages) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isBoolean()) {
            messages.add("Invalid " + field);
            return null;
        }
        return node.asBoolean();
    }

    private static String optionalText(JsonNode json, String field, List<String> messages) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) {
            messages.add("Invalid " + field);
            return null;
        }
        return node.asText();
    }

    /**
     * @param stored the stored configuration (null on create)
     */
    static Change change(WebEdit.Store store, JsonNode json, Config stored, List<String> messages) throws Exception {
        Change change = new Change();
        change.enabled = optionalBoolean(json, "enabled", messages);
        change.bindToData = optionalBoolean(json, "bindToData", messages);
        change.includeLimits = optionalBoolean(json, "includeLimits", messages);
        change.disableLink = optionalBoolean(json, "disableLink", messages);
        change.scope = optionalText(json, "scope", messages);
        if (change.scope != null && !SCOPES.contains(change.scope)) {
            messages.add("scope: one of " + String.join(", ", SCOPES) + " expected");
            change.scope = null;
        }
        change.period = optionalText(json, "period", messages);
        if (change.period != null) {
            if (!PERIODS.contains(change.period)) {
                messages.add("period: one of " + String.join(", ", PERIODS) + " expected");
                change.period = null;
            } else if (PERIOD_CUSTOM.equals(change.period) && (stored == null || !stored.customSchedule)
                    && !(stored != null && PERIOD_CUSTOM.equals(stored.periodText))) {
                messages.add("period: CUSTOM needs a Custom Schedule Object (set in the Control Center)");
                change.period = null;
            }
        }
        change.objects = objects(store, json.get("objects"), stored != null ? stored.objects() : Collections.emptyList(),
                messages);
        if (json.has("scope") || json.has("objects")) {
            String scope = change.scope != null ? change.scope : stored != null ? stored.scope() : DEFAULT_SCOPE;
            List<Target> objects = change.objects != null ? change.objects
                    : stored != null ? stored.objects() : Collections.emptyList();
            if ((AlarmScope.SELECTED.name().equals(scope) || AlarmScope.WITHOUT_SELECTED.name().equals(scope))
                    && objects.isEmpty()) {
                messages.add("scope " + scope + " needs at least one object");
            }
        }
        JsonNode notification = json.get("notification");
        if (notification != null && !notification.isNull()) {
            if (!notification.isObject()) {
                messages.add("Invalid notification");
            } else {
                change.notification = mailChange(store, notification, stored != null ? stored.notification : null, messages);
            }
        }
        return change;
    }

    /**
     * @return the objects sent ({@code [{id, attribute}]}), null if not sent
     */
    static List<Target> objects(WebEdit.Store store, JsonNode node, List<Target> current, List<String> messages)
            throws Exception {
        if (node == null || node.isNull()) return null;
        if (!node.isArray()) {
            messages.add("objects: array expected");
            return null;
        }
        if (node.size() > MAX_OBJECTS) {
            messages.add("objects: at most " + MAX_OBJECTS + " entries");
            return null;
        }
        Set<String> rowClasses = rowClasses(store.classes());
        List<Target> list = new ArrayList<>();
        for (JsonNode entry : node) {
            JsonNode idNode = entry.isObject() ? entry.get("id") : entry;
            if (idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
                messages.add("objects: id expected");
                continue;
            }
            long id = idNode.asLong();
            String attribute = DEFAULT_ATTRIBUTE;
            JsonNode attributeNode = entry.isObject() ? entry.get("attribute") : null;
            if (attributeNode != null && !attributeNode.isNull()) {
                if (!attributeNode.isTextual() || attributeNode.asText().trim().isEmpty()
                        || attributeNode.asText().contains(";") || attributeNode.asText().contains(":")) {
                    messages.add("objects: invalid attribute of " + id);
                    continue;
                }
                attribute = attributeNode.asText().trim();
            }
            Target target = new Target(id, attribute);
            /* a stored bare id stays as it was */
            Target bare = new Target(id, null);
            if (DEFAULT_ATTRIBUTE.equals(attribute) && current.contains(bare) && !current.contains(target)) target = bare;
            if (list.contains(target)) {
                messages.add("objects: " + id + " is listed twice");
                continue;
            }
            if (!current.contains(target)) {
                JsonObject object = WebEdit.readable(store, id);
                if (object == null || !rowClasses.contains(object.getJevisClass())) {
                    messages.add("objects: " + id + " is no readable data row");
                    continue;
                }
            }
            list.add(target);
        }
        return list;
    }

    /**
     * @return "Clean Data" and "Data" with their heirs
     */
    static Set<String> rowClasses(ResourceWebRegisters.Classes classes) {
        Set<String> set = new HashSet<>(classes.withHeirs(CLASS_CLEAN_DATA));
        set.add(CLASS_CLEAN_DATA);
        set.addAll(classes.withHeirs(CLASS_DATA));
        set.add(CLASS_DATA);
        return set;
    }

    static MailChange mailChange(WebEdit.Store store, JsonNode json, Notification stored, List<String> messages)
            throws Exception {
        MailChange change = new MailChange();
        change.enabled = optionalBoolean(json, "enabled", messages);
        change.html = optionalBoolean(json, "html", messages);
        change.users = people(store, json, "recipientUsers", ResourceWebReadingRounds.KIND_USER,
                stored != null ? recipientIds(stored.users) : Collections.emptyList(), messages);
        change.groups = people(store, json, "recipientGroups", ResourceWebReadingRounds.KIND_GROUP,
                stored != null ? recipientIds(stored.groups) : Collections.emptyList(), messages);
        change.recipients = mails(json, "recipients", stored != null ? addresses(stored.recipients) : Collections.emptyList(), messages);
        change.cc = mails(json, "cc", stored != null ? addresses(stored.cc) : Collections.emptyList(), messages);
        change.bcc = mails(json, "bcc", stored != null ? addresses(stored.bcc) : Collections.emptyList(), messages);
        if (json.has("subject")) {
            change.subject = optionalText(json, "subject", messages);
            if (change.subject == null) change.subject = "";
            if (change.subject.length() > MAX_SUBJECT_LENGTH) {
                messages.add("subject longer than " + MAX_SUBJECT_LENGTH + " characters");
            }
        }
        if (json.has("message")) {
            change.message = optionalText(json, "message", messages);
            if (change.message == null) change.message = "";
            if (change.message.length() > MAX_MESSAGE_LENGTH) {
                messages.add("message longer than " + MAX_MESSAGE_LENGTH + " characters");
            }
        }
        return change;
    }

    private static List<Long> people(WebEdit.Store store, JsonNode json, String field, String kind, List<Long> current,
                                     List<String> messages) throws Exception {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isArray()) {
            messages.add(field + ": array of ids expected");
            return null;
        }
        if (node.size() > MAX_RECIPIENTS) {
            messages.add(field + ": at most " + MAX_RECIPIENTS + " entries");
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode entry : node) {
            JsonNode idNode = entry.isObject() ? entry.get("id") : entry;
            if (idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
                messages.add(field + ": id expected");
                continue;
            }
            long id = idNode.asLong();
            if (ids.contains(id)) {
                messages.add(field + ": " + id + " is listed twice");
                continue;
            }
            JsonObject object = WebEdit.readable(store, id);
            if (!current.contains(id) && (object == null || !kind.equals(ResourceWebReadingRounds.kind(object)))) {
                messages.add(field + ": " + id + " is no readable "
                        + (ResourceWebReadingRounds.KIND_USER.equals(kind) ? "user" : "group"));
                continue;
            }
            ids.add(id);
        }
        return ids;
    }

    private static List<String> mails(JsonNode json, String field, List<String> current, List<String> messages) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isArray()) {
            messages.add(field + ": array of addresses expected");
            return null;
        }
        if (node.size() > MAX_RECIPIENTS) {
            messages.add(field + ": at most " + MAX_RECIPIENTS + " entries");
            return null;
        }
        List<String> list = new ArrayList<>();
        for (JsonNode entry : node) {
            if (!entry.isTextual()) {
                messages.add(field + ": address expected");
                continue;
            }
            String address = entry.asText().trim();
            if (address.isEmpty() || list.contains(address)) continue;
            if (!current.contains(address) && !isMail(address)) {
                messages.add(field + ": invalid address " + address);
                continue;
            }
            list.add(address);
        }
        return list;
    }

    // ---------------------------------------------------------------- writing

    private static boolean changed(JsonSample latest, String value) {
        return latest == null || !Objects.equals(latest.getValue(), value);
    }

    private static boolean changedBoolean(JsonSample latest, boolean value) {
        return latest == null || ResourceWebAlarms.isTrue(latest) != value;
    }

    private static void write(WebEdit.Store store, JsonObject object, String attribute, int fallback, String value,
                              DateTime now, String action) throws Exception {
        WebEdit.writeSample(store, object.getId(), attribute, WebEdit.primitiveType(store, object, attribute, fallback),
                WebEdit.sample(now, value, null), action);
    }

    private static void writeBoolean(WebEdit.Store store, JsonObject object, String attribute, Boolean value,
                                     JsonSample latest, DateTime now, String action) throws Exception {
        if (value != null && changedBoolean(latest, value)) {
            write(store, object, attribute, JEVisConstants.PrimitiveType.BOOLEAN, String.valueOf(value), now, action);
        }
    }

    private static void writeText(WebEdit.Store store, JsonObject object, String attribute, String value,
                                  JsonSample latest, DateTime now, String action) throws Exception {
        if (value != null && changed(latest, value)) {
            write(store, object, attribute, JEVisConstants.PrimitiveType.STRING, value, now, action);
        }
    }

    /**
     * Writes the changed attributes of the configuration (all given ones on create).
     */
    static void writeConfiguration(WebEdit.Store store, JsonObject object, Change change, DateTime now, String action)
            throws Exception {
        long id = object.getId();
        writeBoolean(store, object, ATTRIBUTE_ENABLED, change.enabled, store.latest(id, ATTRIBUTE_ENABLED), now, action);
        writeText(store, object, ATTRIBUTE_SCOPE, change.scope, store.latest(id, ATTRIBUTE_SCOPE), now, action);
        if (change.objects != null) {
            JsonSample latest = store.latest(id, ATTRIBUTE_OBJECTS);
            if (!targets(value(latest)).equals(change.objects) || (latest == null && !change.objects.isEmpty())) {
                write(store, object, ATTRIBUTE_OBJECTS, JEVisConstants.PrimitiveType.STRING, targetsText(change.objects), now, action);
            }
        }
        writeText(store, object, ATTRIBUTE_PERIOD, change.period, store.latest(id, ATTRIBUTE_PERIOD), now, action);
        writeBoolean(store, object, ATTRIBUTE_BIND_TO_DATA, change.bindToData, store.latest(id, ATTRIBUTE_BIND_TO_DATA), now, action);
        writeBoolean(store, object, ATTRIBUTE_INCLUDE_LIMITS, change.includeLimits, store.latest(id, ATTRIBUTE_INCLUDE_LIMITS), now, action);
        writeBoolean(store, object, ATTRIBUTE_DISABLE_LINK, change.disableLink, store.latest(id, ATTRIBUTE_DISABLE_LINK), now, action);
    }

    /**
     * Writes the changed attributes of the notification (all given ones for a new one).
     */
    static void writeNotification(WebEdit.Store store, JsonObject object, MailChange change, DateTime now, String action)
            throws Exception {
        Notification stored = notification(store, object);
        long id = object.getId();
        writeBoolean(store, object, MAIL_ENABLED, change.enabled, stored.enabled, now, action);
        if (change.users != null && !recipientIds(stored.users).equals(change.users)) {
            write(store, object, MAIL_USERS, JEVisConstants.PrimitiveType.STRING, recipientIdsText(change.users), now, action);
        }
        if (change.groups != null && !recipientIds(stored.groups).equals(change.groups)) {
            write(store, object, MAIL_GROUPS, JEVisConstants.PrimitiveType.STRING, recipientIdsText(change.groups), now, action);
        }
        writeAddresses(store, object, MAIL_RECIPIENTS, change.recipients, stored.recipients, now, action);
        writeAddresses(store, object, MAIL_CC, change.cc, stored.cc, now, action);
        writeAddresses(store, object, MAIL_BCC, change.bcc, stored.bcc, now, action);
        if (change.subject != null && !change.subject.equals(stored.subject != null ? stored.subject : "")) {
            write(store, object, MAIL_SUBJECT, JEVisConstants.PrimitiveType.STRING, change.subject, now, action);
        }
        if (change.message != null && !change.message.equals(stored.message != null ? stored.message : "")) {
            write(store, object, MAIL_MESSAGE, JEVisConstants.PrimitiveType.STRING, change.message, now, action);
        }
        writeBoolean(store, object, MAIL_HTML, change.html, stored.html, now, action);
        logger.debug("Notification {} saved", id);
    }

    private static void writeAddresses(WebEdit.Store store, JsonObject object, String attribute, List<String> sent,
                                       String stored, DateTime now, String action) throws Exception {
        if (sent != null && !addresses(stored).equals(sent)) {
            write(store, object, attribute, JEVisConstants.PrimitiveType.STRING, String.join(";", sent), now, action);
        }
    }

    /**
     * @return the name of a new notification ({@code "<configuration> Benachrichtigung"})
     */
    static String notificationName(String configurationName, String language) {
        String suffix = "de".equals(language) ? "Benachrichtigung" : "Notification";
        String name = configurationName + " " + suffix;
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /**
     * @return now rounded down to the start of the period in the time zone (the first period JEAlarm checks)
     */
    static DateTime periodStart(DateTime now, String period, DateTimeZone zone) {
        DateTime local = now.withZone(zone);
        switch (period) {
            case "QUARTER_HOURLY":
                return local.hourOfDay().roundFloorCopy().plusMinutes((local.getMinuteOfHour() / 15) * 15);
            case "HOURLY":
                return local.hourOfDay().roundFloorCopy();
            case "DAILY":
                return local.withTimeAtStartOfDay();
            case "WEEKLY":
                return local.withDayOfWeek(1).withTimeAtStartOfDay();
            case "MONTHLY":
                return local.withDayOfMonth(1).withTimeAtStartOfDay();
            case "QUARTERLY":
                return local.withDayOfMonth(1).withMonthOfYear(((local.getMonthOfYear() - 1) / 3) * 3 + 1).withTimeAtStartOfDay();
            case "YEARLY":
                return local.withDayOfYear(1).withTimeAtStartOfDay();
            case "MINUTELY":
            default:
                return local.minuteOfDay().roundFloorCopy();
        }
    }

    private static JsonNode readJson(String body) throws WebEdit.Stop {
        return WebEdit.body(body);
    }

    private static void checkName(String name, List<String> messages) {
        if (name != null && name.length() > MAX_NAME_LENGTH) messages.add("Name longer than " + MAX_NAME_LENGTH + " characters");
    }

    /**
     * {@code POST /alarms/settings} with {@code {parentId, name, enabled?, scope?, objects?, period?, bindToData?,
     * includeLimits?, disableLink?, notification?}}
     */
    static WebEdit.Reply create(WebEdit.Store store, String body, String language, DateTime now) throws Exception {
        JsonNode json = readJson(body);
        ResourceWebModules.Lookup lookup = store.lookup();
        List<String> messages = new ArrayList<>();
        JsonNode parentNode = json.get("parentId");
        JsonObject parent = null;
        if (parentNode == null || parentNode.isNull()) {
            messages.add("Missing parentId");
        } else if (!parentNode.isIntegralNumber() || !parentNode.canConvertToLong()) {
            messages.add("Invalid parentId");
        } else {
            parent = WebEdit.readable(store, parentNode.asLong());
            if (parent == null) return WebEdit.Reply.notFound("Directory not found");
            if (!directoryClasses(store.classes()).contains(parent.getJevisClass())
                    || !belowSite(lookup, parent, siteClasses(store.classes()))) {
                messages.add("parentId: an " + CLASS_DIRECTORY + " below a building or organisation expected");
            }
        }
        String name = ResourceWebEditAnalysis.name(json, messages, true);
        checkName(name, messages);
        if (parent != null && name != null && messages.isEmpty()) {
            ResourceWebEditContracts.checkUnique(lookup, parent.getId(), name, null, messages);
        }
        Change change = change(store, json, null, messages);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!store.rights().canCreate(parent, CLASS_CONFIGURATION)) {
            return WebEdit.Reply.forbidden("Not allowed to create an alarm configuration here");
        }
        if (change.enabled == null) change.enabled = true;
        if (change.scope == null) change.scope = DEFAULT_SCOPE;
        if (change.period == null) change.period = DEFAULT_PERIOD;
        if (change.bindToData == null) change.bindToData = false;
        if (change.includeLimits == null) change.includeLimits = false;
        if (change.disableLink == null) change.disableLink = false;
        DateTimeZone zone = store.workDay(parent).zone;

        JsonObject configuration;
        synchronized (CREATE_LOCK) {
            ResourceWebEditContracts.checkUnique(lookup, parent.getId(), name, null, messages);
            if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
            configuration = store.createObject(name, CLASS_CONFIGURATION, parent.getId());
            store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(configuration.getId(), configuration.getName(), ACTION_CREATE));
            lookup.addReadable(configuration, parent.getId());
            JsonObject notification = null;
            try {
                writeConfiguration(store, configuration, change, now, ACTION_CREATE);
                write(store, configuration, ATTRIBUTE_TIME_STAMP, JEVisConstants.PrimitiveType.STRING,
                        periodStart(now, change.period, zone).toString(), now, ACTION_CREATE);
                if (change.notification != null) {
                    if (!store.rights().canCreate(configuration, CLASS_EMAIL)) {
                        throw new WebEdit.Stop(WebEdit.Reply.forbidden("Not allowed to create the e-mail notification"));
                    }
                    notification = createNotification(store, lookup, configuration, change.notification, language, now, ACTION_CREATE);
                }
            } catch (Exception e) {
                if (notification != null) rollback(store, notification);
                rollback(store, configuration);
                throw e;
            }
        }
        return WebEdit.Reply.created(configurationJson(store, configuration));
    }

    private static JsonObject createNotification(WebEdit.Store store, ResourceWebModules.Lookup lookup,
                                                 JsonObject configuration, MailChange change, String language,
                                                 DateTime now, String action) throws Exception {
        JsonObject notification = store.createObject(notificationName(configuration.getName(), language), CLASS_EMAIL,
                configuration.getId());
        store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(notification.getId(), notification.getName(), action));
        lookup.addReadable(notification, configuration.getId());
        if (change.enabled == null) change.enabled = true;
        if (change.html == null) change.html = false;
        try {
            writeNotification(store, notification, change, now, action);
        } catch (Exception e) {
            rollback(store, notification);
            throw e;
        }
        return notification;
    }

    private static void rollback(WebEdit.Store store, JsonObject object) {
        try {
            store.deleteObject(object);
            store.log(SQLDataSource.LOG_EVENT.DELETE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), ACTION_CREATE + "-rollback"));
        } catch (Exception e) {
            logger.error("Could not remove {} after a failed create", object.getId(), e);
        }
    }

    /**
     * {@code PUT /alarms/settings/{id}} with {@code {version, name?, enabled?, scope?, objects?, period?, bindToData?,
     * includeLimits?, disableLink?, notification?}}
     */
    static WebEdit.Reply update(WebEdit.Store store, long id, String body, String language, DateTime now) throws Exception {
        JsonNode json = readJson(body);
        JsonObject object = configurationObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Alarm configuration not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this alarm configuration");
        String version = WebEdit.text(json, "version");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");

        List<String> messages = new ArrayList<>();
        String name = json.has("name") ? ResourceWebEditAnalysis.name(json, messages, true) : null;
        checkName(name, messages);
        if (name != null && messages.isEmpty() && !name.equals(object.getName())) {
            Long parent = store.lookup().parent(id);
            if (parent != null) ResourceWebEditContracts.checkUnique(store.lookup(), parent, name, id, messages);
        }
        Config stored = config(store, object);
        Change change = change(store, json, stored, messages);
        if (change.notification != null && stored.notification == null && !stored.notifications.isEmpty()) {
            messages.add(stored.notifications.size() > 1
                    ? "The configuration has several notifications; they can only be changed in the Control Center"
                    : "The notification of this configuration can only be changed in the Control Center");
        }
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!ResourceWebEditAnalysis.sameVersion(version, versionSample(store, object))) {
            return WebEdit.Reply.conflict(configurationJson(store, object));
        }
        if (change.notification != null) {
            if (stored.notification != null && !store.rights().canWrite(stored.notification.object)) {
                return WebEdit.Reply.forbidden("Not allowed to change the e-mail notification");
            }
            if (stored.notification == null && !store.rights().canCreate(object, CLASS_EMAIL)) {
                return WebEdit.Reply.forbidden("Not allowed to create the e-mail notification");
            }
        }
        if (name != null && !name.equals(object.getName())) {
            store.rename(object, name);
            store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(id, name, ACTION_SAVE));
            object.setName(name);
        }
        writeConfiguration(store, object, change, now, ACTION_SAVE);
        if (change.notification != null) {
            if (stored.notification != null) {
                writeNotification(store, stored.notification.object, change.notification, now, ACTION_SAVE);
            } else {
                createNotification(store, store.lookup(), object, change.notification, language, now, ACTION_SAVE);
            }
        }
        return WebEdit.Reply.ok(configurationJson(store, object));
    }

    /**
     * {@code DELETE /alarms/settings/{id}?version=…}: the configuration with its notification to the recycle bin
     */
    static WebEdit.Reply delete(WebEdit.Store store, long id, String version, String language) throws Exception {
        JsonObject object = configurationObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Alarm configuration not found");
        if (!store.rights().canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to delete this alarm configuration");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        if (!ResourceWebEditAnalysis.sameVersion(version, versionSample(store, object))) {
            return WebEdit.Reply.conflict(configurationJson(store, object));
        }
        store.recycle(object);
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(id, object.getName(), ACTION_DELETE));
        return WebEdit.Reply.noContent();
    }
}
