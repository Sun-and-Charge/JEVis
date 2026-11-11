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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.appointment.Appointment;
import org.jevis.commons.appointment.AppointmentLogic;
import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.jevis.commons.maintenance.MaintenanceEntry;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import javax.security.sasl.AuthenticationException;
import java.util.*;

/**
 * Appointments (upstream issue #1855) and maintenance messages (#2663) for the web app, view only.
 * <p>
 * Each entry is an object whose attribute "Data" holds the entry as JSON and whose attribute "Mail State" holds the
 * mail bookkeeping of the JENotifier (see {@link EntryFormat}). Only entries the user may read are returned; names of
 * sites, directories, linked objects and recipients are only given if the user may read those objects as well.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebModules {

    private static final Logger logger = LogManager.getLogger(ResourceWebModules.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GET
    @Path("appointments")
    @Produces(MediaType.APPLICATION_JSON)
    public Response appointments(@Context HttpHeaders httpHeaders) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            Lookup lookup = lookup(ds);
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> entries = new ArrayList<>();
            for (JsonObject object : lookup.readableOfClass(Appointment.CLASS_NAME)) {
                entries.add(appointmentJson(object, latest(ds, object.getId(), EntryFormat.ATTRIBUTE_DATA),
                        latest(ds, object.getId(), EntryFormat.ATTRIBUTE_MAIL_STATE), lookup, today));
            }
            sortByTime(entries, "due", true);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("today", today.toString());
            result.put("appointments", entries);
            return json(result);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the appointments", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("maintenance")
    @Produces(MediaType.APPLICATION_JSON)
    public Response maintenance(@Context HttpHeaders httpHeaders) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            Lookup lookup = lookup(ds);
            List<Map<String, Object>> entries = new ArrayList<>();
            for (JsonObject object : lookup.readableOfClass(MaintenanceEntry.CLASS_NAME)) {
                entries.add(maintenanceJson(object, latest(ds, object.getId(), EntryFormat.ATTRIBUTE_DATA),
                        latest(ds, object.getId(), EntryFormat.ATTRIBUTE_MAIL_STATE), lookup));
            }
            sortByTime(entries, "start", false);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("now", EntryFormat.format(LocalDateTime.now()));
            result.put("entries", entries);
            return json(result);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the maintenance messages", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    /**
     * @param readable the objects the user may read
     * @return {@code appointments} and {@code maintenance}: number of readable entries, for the navigation
     */
    static Map<String, Object> moduleCounts(Collection<JsonObject> readable) {
        int appointments = 0;
        int maintenance = 0;
        for (JsonObject object : readable) {
            if (Appointment.CLASS_NAME.equals(object.getJevisClass())) {
                appointments++;
            } else if (MaintenanceEntry.CLASS_NAME.equals(object.getJevisClass())) {
                maintenance++;
            }
        }
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put("appointments", appointments);
        modules.put("maintenance", maintenance);
        modules.putAll(ResourceWebIso.moduleCounts(readable));
        ResourceWebRegisters.Classes classes = ResourceWebRegisters.Classes.fromCache();
        modules.putAll(ResourceWebRegisters.moduleCounts(readable, classes));
        modules.putAll(ResourceWebNotes.moduleCounts(readable));
        modules.putAll(ResourceWebReports.moduleCounts(readable, classes));
        return modules;
    }

    /**
     * @param lookup objects and rights of the request
     * @param store  samples and files (for the alarms)
     * @param now    end of the time frame of {@code alarmsOpen}
     * @return {@link #moduleCounts(Collection)} plus {@code alarms} and {@code alarmsOpen}
     * ({@link ResourceWebAlarms#moduleCounts}; {@code 0} if they cannot be read)
     */
    static Map<String, Object> moduleCounts(Lookup lookup, ResourceWebAlarms.Store store, DateTime now) {
        Map<String, Object> modules = moduleCounts(lookup.readable());
        try {
            modules.putAll(ResourceWebAlarms.moduleCounts(lookup, store, now));
        } catch (Exception e) {
            logger.warn("Could not count the alarms", e);
            modules.put("alarms", 0);
            modules.put("alarmsOpen", 0);
        }
        return modules;
    }

    static Lookup lookup(SQLDataSource ds) throws Exception {
        List<JsonObject> all = ds.getObjects();
        List<JsonObject> readable = ds.getUserManager().filterList(all);
        Map<Long, Long> parents = new HashMap<>();
        for (JsonRelationship rel : ds.getRelationships(JEVisConstants.ObjectRelationship.PARENT)) {
            parents.put(rel.getFrom(), rel.getTo());
        }
        return new Lookup(all, readable, parents);
    }

    private static String latest(SQLDataSource ds, long id, String attribute) {
        try {
            JsonSample sample = ds.getLastSample(id, attribute);
            return sample != null ? sample.getValue() : null;
        } catch (Exception e) {
            logger.warn("Could not read {} of {}", attribute, id, e);
            return null;
        }
    }

    /**
     * @param dataJson      latest value of "Data"
     * @param mailStateJson latest value of "Mail State"
     * @param today         the server's date for {@code overdue}/{@code dueSoon}
     */
    static Map<String, Object> appointmentJson(JsonObject object, String dataJson, String mailStateJson,
                                               Lookup lookup, LocalDate today) {
        Appointment appointment = EntryFormat.fromJson(dataJson, Appointment.class);
        Map<String, Object> json = header(object, lookup);
        if (appointment == null) {
            json.put("title", object.getName());
            json.put("description", null);
            json.put("due", null);
            json.put("nextDue", null);
            json.put("recurrence", null);
            json.put("anchorDay", null);
            json.put("reminderDays", null);
            json.put("reminderDay", null);
            json.put("status", null);
            json.put("overdue", false);
            json.put("dueSoon", false);
            json.put("lastDone", null);
            json.put("linkedObject", null);
            json.put("recipients", lookup.recipients(Collections.emptyList()));
            json.put("lastMail", lastMail(mailStateJson));
            json.put("created", null);
            json.put("createdBy", null);
            return json;
        }
        LocalDateTime due = appointment.getDueDate();
        LocalDate reminderDay = AppointmentLogic.reminderDay(appointment);
        json.put("title", appointment.getTitle());
        json.put("description", appointment.getDescription());
        json.put("due", EntryFormat.format(due));
        json.put("nextDue", EntryFormat.format(AppointmentLogic.nextDue(due, appointment.getRecurrence(), appointment.getAnchorDay())));
        json.put("recurrence", appointment.getRecurrence().name());
        json.put("anchorDay", appointment.getAnchorDay());
        json.put("reminderDays", appointment.getReminderDays());
        json.put("reminderDay", reminderDay != null ? reminderDay.toString() : null);
        json.put("status", appointment.getStatus().name());
        json.put("overdue", AppointmentLogic.isOverdue(appointment, today));
        json.put("dueSoon", AppointmentLogic.isDueSoon(appointment, today));
        json.put("lastDone", appointment.getLastDone());
        json.put("linkedObject", lookup.linkedObject(appointment.getLinkedObject()));
        json.put("recipients", lookup.recipients(appointment.getRecipients()));
        json.put("lastMail", lastMail(mailStateJson));
        json.put("created", appointment.getCreated());
        json.put("createdBy", appointment.getCreatedBy());
        return json;
    }

    /**
     * @param dataJson      latest value of "Data"
     * @param mailStateJson latest value of "Mail State"
     */
    static Map<String, Object> maintenanceJson(JsonObject object, String dataJson, String mailStateJson, Lookup lookup) {
        MaintenanceEntry entry = EntryFormat.fromJson(dataJson, MaintenanceEntry.class);
        Map<String, Object> json = header(object, lookup);
        if (entry == null) {
            json.put("type", null);
            json.put("title", object.getName());
            json.put("description", null);
            json.put("impact", null);
            json.put("start", null);
            json.put("end", null);
            json.put("durationMinutes", null);
            json.put("status", null);
            json.put("active", false);
            json.put("recipients", lookup.recipients(Collections.emptyList()));
            json.put("informRequested", null);
            json.put("doneAt", null);
            json.put("lastMail", lastMail(mailStateJson));
            json.put("created", null);
            json.put("createdBy", null);
            return json;
        }
        LocalDateTime start = entry.getStartDate();
        Integer duration = entry.getDurationMinutes();
        json.put("type", entry.getType().name());
        json.put("title", entry.getTitle());
        json.put("description", entry.getDescription());
        json.put("impact", entry.getImpact());
        json.put("start", EntryFormat.format(start));
        json.put("end", start != null && duration != null && duration > 0 ? EntryFormat.format(start.plusMinutes(duration)) : null);
        json.put("durationMinutes", duration);
        json.put("status", entry.getStatus().name());
        json.put("active", entry.getStatus() == MaintenanceEntry.Status.PLANNED || entry.getStatus() == MaintenanceEntry.Status.IN_PROGRESS);
        json.put("recipients", lookup.recipients(entry.getRecipients()));
        json.put("informRequested", entry.getInformRequested());
        json.put("doneAt", entry.getDoneAt());
        json.put("lastMail", lastMail(mailStateJson));
        json.put("created", entry.getCreated());
        json.put("createdBy", entry.getCreatedBy());
        return json;
    }

    private static Map<String, Object> header(JsonObject object, Lookup lookup) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("site", lookup.site(object));
        json.put("directory", lookup.directory(object));
        return json;
    }

    private static String lastMail(String mailStateJson) {
        MailState state = EntryFormat.fromJson(mailStateJson, MailState.class);
        return state != null ? state.getLastMail() : null;
    }

    /**
     * Sorts by the time field (entries without time last), then by title.
     */
    static void sortByTime(List<Map<String, Object>> entries, String field, boolean ascending) {
        Comparator<String> times = ascending ? Comparator.<String>naturalOrder() : Comparator.<String>reverseOrder();
        entries.sort(Comparator.comparing((Map<String, Object> e) -> (String) e.get(field), Comparator.nullsLast(times))
                .thenComparing(e -> String.valueOf(e.get("title")).toLowerCase(Locale.ROOT)));
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }

    /**
     * Objects, rights and parents of one request.
     */
    static class Lookup {
        private final Map<Long, JsonObject> byId = new HashMap<>();
        private final Set<Long> readableIds = new HashSet<>();
        private final List<JsonObject> readable;
        private final Map<Long, Long> parents;
        private Map<Long, List<JsonObject>> readableChildren;

        /**
         * @param all      all objects (to walk up the parents)
         * @param readable the objects the user may read
         * @param parents  parent id by object id
         */
        Lookup(Collection<JsonObject> all, Collection<JsonObject> readable, Map<Long, Long> parents) {
            for (JsonObject object : all) byId.put(object.getId(), object);
            for (JsonObject object : readable) readableIds.add(object.getId());
            this.readable = new ArrayList<>(readable);
            this.parents = parents;
        }

        /**
         * @return the object if it exists and the user may read it, else null
         */
        JsonObject readable(Long id) {
            return id != null && readableIds.contains(id) ? byId.get(id) : null;
        }

        /**
         * @return the object (readable or not, for walking up the parents) or null
         */
        JsonObject object(Long id) {
            return id != null ? byId.get(id) : null;
        }

        /**
         * @return the parent id of the object or null
         */
        Long parent(long id) {
            return parents.get(id);
        }

        /**
         * @return the readable children of the object whose class is one of the given classes
         */
        List<JsonObject> readableChildren(long id, Collection<String> classNames) {
            List<JsonObject> result = new ArrayList<>();
            for (JsonObject object : readableChildren(id)) {
                if (classNames.contains(object.getJevisClass())) result.add(object);
            }
            return result;
        }

        /**
         * @return all readable children of the object
         */
        List<JsonObject> readableChildren(long id) {
            if (readableChildren == null) {
                readableChildren = new HashMap<>();
                for (JsonObject object : readable) {
                    Long parent = parents.get(object.getId());
                    if (parent != null) readableChildren.computeIfAbsent(parent, k -> new ArrayList<>()).add(object);
                }
            }
            return readableChildren.getOrDefault(id, Collections.emptyList());
        }

        /**
         * @return the objects the user may read
         */
        List<JsonObject> readable() {
            return readable;
        }

        List<JsonObject> readableOfClass(String className) {
            List<JsonObject> result = new ArrayList<>();
            for (JsonObject object : readable) {
                if (className.equals(object.getJevisClass())) result.add(object);
            }
            return result;
        }

        /**
         * @return nearest readable organisation or building above the object ({@code id, name, names}), or null
         */
        Map<String, Object> site(JsonObject object) {
            Long siteId = ResourceWebNavigation.nearestSite(object, byId, parents, readableIds);
            return siteId != null ? ResourceWebNavigation.entry(byId.get(siteId)) : null;
        }

        /**
         * @return the readable parent folder of the entry ({@code id, name}), or null
         */
        Map<String, Object> directory(JsonObject object) {
            Long parentId = parents.get(object.getId());
            JsonObject parent = parentId != null && readableIds.contains(parentId) ? byId.get(parentId) : null;
            if (parent == null) return null;
            Map<String, Object> directory = new LinkedHashMap<>();
            directory.put("id", parent.getId());
            directory.put("name", parent.getName());
            return directory;
        }

        /**
         * @return null without link; {@code id, name, readable: true} if readable, else only {@code id, readable: false}
         */
        Map<String, Object> linkedObject(Long id) {
            if (id == null) return null;
            Map<String, Object> linked = new LinkedHashMap<>();
            linked.put("id", id);
            JsonObject object = readableIds.contains(id) ? byId.get(id) : null;
            if (object != null) {
                linked.put("name", object.getName());
                linked.put("readable", true);
            } else {
                linked.put("readable", false);
            }
            return linked;
        }

        /**
         * @return {@code count} of all recipients and the {@code names} of those the user may read
         */
        Map<String, Object> recipients(List<Long> ids) {
            List<String> names = new ArrayList<>();
            int count = 0;
            for (Long id : ids) {
                if (id == null) continue;
                count++;
                JsonObject object = readableIds.contains(id) ? byId.get(id) : null;
                if (object != null) names.add(object.getName());
            }
            Map<String, Object> recipients = new LinkedHashMap<>();
            recipients.put("count", count);
            recipients.put("names", names);
            return recipients;
        }
    }
}
