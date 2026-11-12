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
import org.jevis.commons.alarm.AlarmConfiguration;
import org.jevis.commons.appointment.Appointment;
import org.jevis.commons.appointment.AppointmentLogic;
import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.maintenance.MaintenanceEntry;
import org.jevis.commons.maintenance.MaintenanceLogic;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.*;

/**
 * Write endpoints of the web app (stage E1, see docs/WEB-API.md) for alarms (confirm), appointments (done) and
 * maintenance messages (status). The data is written like the Control Center writes it: "Alarm Checked" like
 * {@link AlarmConfiguration#setChecked(Boolean)}, the JSON of an entry as new "Data" sample at now plus the rename of
 * the object to the title like the Control Center's {@code EntryStore}.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEdit {

    static final String ACTION_ALARM_CONFIRM = "alarm-confirm";
    static final String ACTION_APPOINTMENT_DONE = "appointment-done";
    static final String ACTION_MAINTENANCE_STATUS = "maintenance-status";
    /**
     * At most this many configurations per {@code confirm-all}
     */
    static final int MAX_CONFIRM_IDS = 1000;
    /**
     * Longest object name of an entry ({@code EntryStore.objectName})
     */
    static final int MAX_NAME_LENGTH = 120;

    // ---------------------------------------------------------------- endpoints

    @POST
    @Path("alarms/configurations/{id}/confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirm(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                            @QueryParam("timeZone") String timeZone, String body) {
        return WebEdit.run(httpHeaders, "confirm the alarm configuration " + id,
                store -> confirm(store, id, body, ResourceWebNotes.zone(timeZone), DateTime.now()));
    }

    @POST
    @Path("alarms/confirm-all")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirmAll(@Context HttpHeaders httpHeaders, String body) {
        return WebEdit.run(httpHeaders, "confirm the alarms", store -> confirmAll(store, body, DateTime.now()));
    }

    @POST
    @Path("appointments/{id}/done")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response appointmentDone(@Context HttpHeaders httpHeaders, @PathParam("id") long id, String body) {
        return WebEdit.run(httpHeaders, "complete the appointment " + id,
                store -> appointmentDone(store, id, body, DateTime.now()));
    }

    @POST
    @Path("maintenance/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response maintenanceStatus(@Context HttpHeaders httpHeaders, @PathParam("id") long id, String body) {
        return WebEdit.run(httpHeaders, "change the status of the maintenance message " + id,
                store -> maintenanceStatus(store, id, body, DateTime.now()));
    }

    // ---------------------------------------------------------------- alarms

    /**
     * @return the readable, enabled "Alarm Configuration" outside the recycle bin, else null
     */
    static JsonObject enabledConfiguration(WebEdit.Store store, long id) throws Exception {
        JsonObject configuration = WebEdit.readable(store, id);
        if (configuration == null || !ResourceWebAlarms.CLASS_ALARM_CONFIGURATION.equals(configuration.getJevisClass())) {
            return null;
        }
        return ResourceWebAlarms.isTrue(store.latest(id, ResourceWebAlarms.ATTRIBUTE_ENABLED)) ? configuration : null;
    }

    /**
     * {@code POST /alarms/configurations/{id}/confirm} with {@code {checked}}
     */
    static WebEdit.Reply confirm(WebEdit.Store store, long id, String body, DateTimeZone zone, DateTime now) throws Exception {
        boolean checked = WebEdit.bool(WebEdit.body(body), "checked");
        JsonObject configuration = enabledConfiguration(store, id);
        if (configuration == null) return WebEdit.Reply.notFound("Alarm configuration not found");
        if (!WebEdit.canConfirmAlarm(store.rights(), configuration)) {
            return WebEdit.Reply.forbidden("Not allowed to confirm the alarms of this configuration");
        }
        writeChecked(store, configuration, checked, now);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("checked", checked);
        json.put("checkedBy", checked ? store.account() : null);
        json.put("checkedAt", checked ? WebEdit.iso(now.getMillis(), zone) : null);
        return WebEdit.Reply.ok(json);
    }

    /**
     * {@code POST /alarms/confirm-all} with {@code {ids}}: confirms each allowed configuration, lists the others
     */
    static WebEdit.Reply confirmAll(WebEdit.Store store, String body, DateTime now) throws Exception {
        JsonNode ids = WebEdit.body(body).get("ids");
        if (ids == null || !ids.isArray()) return WebEdit.Reply.badRequest("Missing ids");
        if (ids.size() > MAX_CONFIRM_IDS) return WebEdit.Reply.badRequest("At most " + MAX_CONFIRM_IDS + " ids");
        Set<Long> unique = new LinkedHashSet<>();
        for (JsonNode id : ids) {
            if (!id.canConvertToLong() || !id.isIntegralNumber()) return WebEdit.Reply.badRequest("Invalid ids");
            unique.add(id.asLong());
        }
        List<Long> confirmed = new ArrayList<>();
        List<Long> refused = new ArrayList<>();
        for (Long id : unique) {
            JsonObject configuration = enabledConfiguration(store, id);
            if (configuration == null || !WebEdit.canConfirmAlarm(store.rights(), configuration)) {
                refused.add(id);
                continue;
            }
            writeChecked(store, configuration, true, now);
            confirmed.add(id);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("confirmed", confirmed);
        json.put("refused", refused);
        return WebEdit.Reply.ok(json);
    }

    /**
     * Like {@link AlarmConfiguration#setChecked(Boolean)}: Boolean sample of "Alarm Checked" at now with the note
     * {@code Checked by <account>} / {@code Unchecked by <account>}.
     */
    static void writeChecked(WebEdit.Store store, JsonObject configuration, boolean checked, DateTime now) throws Exception {
        String note = (checked ? "Checked by " : "Unchecked by ") + store.account();
        WebEdit.writeSample(store, configuration.getId(), ResourceWebAlarms.ATTRIBUTE_CHECKED,
                WebEdit.primitiveType(store, configuration, ResourceWebAlarms.ATTRIBUTE_CHECKED, JEVisConstants.PrimitiveType.BOOLEAN),
                WebEdit.sample(now, String.valueOf(checked), note), ACTION_ALARM_CONFIRM);
    }

    // ---------------------------------------------------------------- appointments and maintenance

    /**
     * @return the readable entry of the class outside the recycle bin, else null
     */
    static JsonObject entry(WebEdit.Store store, long id, String className) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        return object != null && className.equals(object.getJevisClass()) ? object : null;
    }

    /**
     * {@code POST /appointments/{id}/done} with {@code {version}}: {@link AppointmentLogic#complete} (not recurring:
     * done; recurring: next due date, stays open, {@code lastDone} set).
     */
    static WebEdit.Reply appointmentDone(WebEdit.Store store, long id, String body, DateTime now) throws Exception {
        String version = WebEdit.text(WebEdit.body(body), "version");
        JsonObject object = entry(store, id, Appointment.CLASS_NAME);
        if (object == null) return WebEdit.Reply.notFound("Appointment not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this appointment");
        JsonSample data = store.latest(id, EntryFormat.ATTRIBUTE_DATA);
        if (!WebEdit.sameVersion(version, data)) {
            return WebEdit.Reply.conflict(appointmentJson(store, object, data, now));
        }
        Appointment appointment = EntryFormat.fromJson(data.getValue(), Appointment.class);
        if (appointment == null) return WebEdit.Reply.badRequest("The appointment has no valid data");
        if (appointment.getStatus() == Appointment.Status.DONE) return WebEdit.Reply.badRequest("The appointment is already done");
        AppointmentLogic.complete(appointment, now.toLocalDateTime());
        writeEntry(store, object, appointment.getTitle(), EntryFormat.toJson(appointment), now, ACTION_APPOINTMENT_DONE);
        return WebEdit.Reply.ok(appointmentJson(store, object, store.latest(id, EntryFormat.ATTRIBUTE_DATA), now));
    }

    /**
     * {@code POST /maintenance/{id}/status} with {@code {status, version}}: {@link MaintenanceLogic#changeStatus}
     * ({@code doneAt} set or cleared).
     */
    static WebEdit.Reply maintenanceStatus(WebEdit.Store store, long id, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        String version = WebEdit.text(json, "version");
        String statusText = WebEdit.text(json, "status");
        JsonObject object = entry(store, id, MaintenanceEntry.CLASS_NAME);
        if (object == null) return WebEdit.Reply.notFound("Maintenance message not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this maintenance message");
        MaintenanceEntry.Status status;
        try {
            status = MaintenanceEntry.Status.valueOf(statusText != null ? statusText.trim() : "");
        } catch (IllegalArgumentException e) {
            return WebEdit.Reply.badRequest("Invalid status");
        }
        JsonSample data = store.latest(id, EntryFormat.ATTRIBUTE_DATA);
        if (!WebEdit.sameVersion(version, data)) {
            return WebEdit.Reply.conflict(maintenanceJson(store, object, data));
        }
        MaintenanceEntry entry = EntryFormat.fromJson(data.getValue(), MaintenanceEntry.class);
        if (entry == null) return WebEdit.Reply.badRequest("The maintenance message has no valid data");
        MaintenanceLogic.changeStatus(entry, status, now.toLocalDateTime());
        writeEntry(store, object, entry.getTitle(), EntryFormat.toJson(entry), now, ACTION_MAINTENANCE_STATUS);
        return WebEdit.Reply.ok(maintenanceJson(store, object, store.latest(id, EntryFormat.ATTRIBUTE_DATA)));
    }

    /**
     * Like the Control Center's {@code EntryStore.write} and {@code rename}: the JSON as new "Data" sample at now, then
     * the object renamed to the title if it differs.
     */
    static void writeEntry(WebEdit.Store store, JsonObject object, String title, String json, DateTime now, String action)
            throws Exception {
        WebEdit.writeSample(store, object.getId(), EntryFormat.ATTRIBUTE_DATA,
                WebEdit.primitiveType(store, object, EntryFormat.ATTRIBUTE_DATA, JEVisConstants.PrimitiveType.STRING),
                WebEdit.sample(now, json, null), action);
        String name = objectName(title);
        if (!name.equals(object.getName())) {
            store.rename(object, name);
            store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(object.getId(), name, action));
            object.setName(name);
        }
    }

    /**
     * {@code EntryStore.objectName}: the title on one line, {@code -} if empty, at most {@link #MAX_NAME_LENGTH}
     * characters
     */
    static String objectName(String title) {
        String name = title == null ? "" : title.trim().replaceAll("[\\r\\n\\t]+", " ");
        if (name.isEmpty()) name = "-";
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    private static Map<String, Object> appointmentJson(WebEdit.Store store, JsonObject object, JsonSample data,
                                                       DateTime now) throws Exception {
        return ResourceWebModules.editInfo(ResourceWebModules.appointmentJson(object, data != null ? data.getValue() : null,
                        mailState(store, object), store.lookup(), now.toLocalDate()),
                object, data, store.rights());
    }

    private static Map<String, Object> maintenanceJson(WebEdit.Store store, JsonObject object, JsonSample data)
            throws Exception {
        return ResourceWebModules.editInfo(ResourceWebModules.maintenanceJson(object, data != null ? data.getValue() : null,
                mailState(store, object), store.lookup()), object, data, store.rights());
    }

    private static String mailState(WebEdit.Store store, JsonObject object) throws Exception {
        JsonSample sample = store.latest(object.getId(), EntryFormat.ATTRIBUTE_MAIL_STATE);
        return sample != null ? sample.getValue() : null;
    }
}
