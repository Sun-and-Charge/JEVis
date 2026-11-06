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
import org.jevis.commons.appointment.Appointment;
import org.jevis.commons.maintenance.MaintenanceEntry;
import org.jevis.commons.ws.json.JsonObject;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebModulesTest {

    private static final LocalDate TODAY = new LocalDate(2026, 11, 6);

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    private static Map<Long, Long> parents(Collection<JsonObject> objects) {
        Map<Long, Long> parents = new HashMap<>();
        for (JsonObject o : objects) parents.put(o.getId(), o.getParent());
        return parents;
    }

    /* Site 1 > Directory 2 > Appointment 3; Meter 4, User 5, Group 6 below the site; maintenance 8 in folder 7 */
    private static final JsonObject SITE = obj(1, "Site A", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
    private static final JsonObject DIR = obj(2, "Appointments", Appointment.DIRECTORY_CLASS_NAME, 1);
    private static final JsonObject APPOINTMENT = obj(3, "Calibration", Appointment.CLASS_NAME, 2);
    private static final JsonObject METER = obj(4, "Meter 1", "Data", 1);
    private static final JsonObject USER = obj(5, "User A", "User", 1);
    private static final JsonObject GROUP = obj(6, "Group A", "Group", 1);
    private static final JsonObject MDIR = obj(7, "Maintenance", MaintenanceEntry.DIRECTORY_CLASS_NAME, 0);
    private static final JsonObject MAINTENANCE = obj(8, "Update", MaintenanceEntry.CLASS_NAME, 7);
    private static final List<JsonObject> ALL = Arrays.asList(SITE, DIR, APPOINTMENT, METER, USER, GROUP, MDIR, MAINTENANCE);

    private static ResourceWebModules.Lookup lookup(JsonObject... unreadable) {
        List<JsonObject> readable = new ArrayList<>(ALL);
        readable.removeAll(Arrays.asList(unreadable));
        return new ResourceWebModules.Lookup(ALL, readable, parents(ALL));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> json, String key) {
        return (Map<String, Object>) json.get(key);
    }

    private static final String APPOINTMENT_JSON = "{\"title\":\"Calibration main meter\",\"description\":\"Text\","
            + "\"due\":\"2026-11-02T09:00\",\"recurrence\":\"YEARLY\",\"anchorDay\":2,\"reminderDays\":3,"
            + "\"recipients\":[5,6],\"linkedObject\":4,\"status\":\"OPEN\",\"created\":\"2026-10-31T00:31:49\",\"createdBy\":\"Admin\"}";

    @Test
    public void appointmentIsMapped() {
        Map<String, Object> json = ResourceWebModules.appointmentJson(APPOINTMENT, APPOINTMENT_JSON,
                "{\"lastMail\":\"2026-10-31T00:33:51\",\"lastMailKind\":\"DUE\"}", lookup(), TODAY);

        assertEquals(3L, json.get("id"));
        assertEquals("Calibration", json.get("name"));
        assertEquals(1L, map(json, "site").get("id"));
        assertEquals("Site A", map(json, "site").get("name"));
        assertEquals(2L, map(json, "directory").get("id"));
        assertEquals("Calibration main meter", json.get("title"));
        assertEquals("2026-11-02T09:00", json.get("due"));
        assertEquals("2027-11-02T09:00", json.get("nextDue"));
        assertEquals("YEARLY", json.get("recurrence"));
        assertEquals(2, json.get("anchorDay"));
        assertEquals(3, json.get("reminderDays"));
        assertEquals("2026-10-30", json.get("reminderDay"));
        assertEquals("OPEN", json.get("status"));
        assertEquals(true, json.get("overdue"));
        assertEquals(false, json.get("dueSoon"));
        assertNull(json.get("lastDone"));
        assertEquals("Meter 1", map(json, "linkedObject").get("name"));
        assertEquals(true, map(json, "linkedObject").get("readable"));
        assertEquals(2, map(json, "recipients").get("count"));
        assertEquals(Arrays.asList("User A", "Group A"), map(json, "recipients").get("names"));
        assertEquals("2026-10-31T00:33:51", json.get("lastMail"));
        assertEquals("2026-10-31T00:31:49", json.get("created"));
        assertEquals("Admin", json.get("createdBy"));
    }

    @Test
    public void unreadableObjectsAreNotNamed() {
        Map<String, Object> json = ResourceWebModules.appointmentJson(APPOINTMENT, APPOINTMENT_JSON, null,
                lookup(SITE, DIR, METER, USER), TODAY);

        assertNull(json.get("site"));
        assertNull(json.get("directory"));
        Map<String, Object> linked = map(json, "linkedObject");
        assertEquals(4L, linked.get("id"));
        assertEquals(false, linked.get("readable"));
        assertFalse(linked.containsKey("name"));
        assertEquals(2, map(json, "recipients").get("count"));
        assertEquals(Collections.singletonList("Group A"), map(json, "recipients").get("names"));
        assertNull(json.get("lastMail"));
    }

    @Test
    public void dueSoonAndNonRecurring() {
        String data = "{\"title\":\"Check\",\"due\":\"2026-11-08T10:00\",\"reminderDays\":5}";
        Map<String, Object> json = ResourceWebModules.appointmentJson(APPOINTMENT, data, null, lookup(), TODAY);

        assertEquals("NONE", json.get("recurrence"));
        assertNull(json.get("nextDue"));
        assertEquals(false, json.get("overdue"));
        assertEquals(true, json.get("dueSoon"));
        assertNull(json.get("linkedObject"));
        assertEquals(0, map(json, "recipients").get("count"));
    }

    @Test
    public void doneAppointmentIsNeitherOverdueNorDueSoon() {
        String data = "{\"title\":\"Old\",\"due\":\"2026-01-01T10:00\",\"status\":\"DONE\",\"lastDone\":\"2026-01-02T08:00:00\"}";
        Map<String, Object> json = ResourceWebModules.appointmentJson(APPOINTMENT, data, null, lookup(), TODAY);

        assertEquals("DONE", json.get("status"));
        assertEquals(false, json.get("overdue"));
        assertEquals(false, json.get("dueSoon"));
        assertEquals("2026-01-02T08:00:00", json.get("lastDone"));
    }

    @Test
    public void unreadableJsonGivesObjectNameAndNoStatus() {
        Map<String, Object> appointment = ResourceWebModules.appointmentJson(APPOINTMENT, "{broken", null, lookup(), TODAY);
        assertEquals("Calibration", appointment.get("title"));
        assertNull(appointment.get("status"));
        assertEquals(false, appointment.get("overdue"));
        assertTrue(appointment.containsKey("due"));

        Map<String, Object> maintenance = ResourceWebModules.maintenanceJson(MAINTENANCE, null, null, lookup());
        assertEquals("Update", maintenance.get("title"));
        assertNull(maintenance.get("status"));
        assertEquals(false, maintenance.get("active"));
    }

    @Test
    public void maintenanceIsMapped() {
        String data = "{\"type\":\"MAINTENANCE\",\"title\":\"Server update\",\"description\":\"Text\",\"impact\":\"None\","
                + "\"start\":\"2026-11-06T18:00\",\"durationMinutes\":30,\"status\":\"PLANNED\",\"recipients\":[5],"
                + "\"informRequested\":\"2026-10-31T00:32:52\",\"createdBy\":\"Admin\"}";
        Map<String, Object> json = ResourceWebModules.maintenanceJson(MAINTENANCE, data, "{\"lastMail\":\"2026-10-31T00:35:00\"}", lookup(USER));

        assertNull(json.get("site"));
        assertEquals(7L, map(json, "directory").get("id"));
        assertEquals("MAINTENANCE", json.get("type"));
        assertEquals("Server update", json.get("title"));
        assertEquals("None", json.get("impact"));
        assertEquals("2026-11-06T18:00", json.get("start"));
        assertEquals("2026-11-06T18:30", json.get("end"));
        assertEquals(30, json.get("durationMinutes"));
        assertEquals("PLANNED", json.get("status"));
        assertEquals(true, json.get("active"));
        assertEquals(1, map(json, "recipients").get("count"));
        assertEquals(Collections.emptyList(), map(json, "recipients").get("names"));
        assertEquals("2026-10-31T00:32:52", json.get("informRequested"));
        assertNull(json.get("doneAt"));
        assertEquals("2026-10-31T00:35:00", json.get("lastMail"));
    }

    @Test
    public void maintenanceWithoutDurationHasNoEndAndDoneIsInactive() {
        String data = "{\"type\":\"KNOWN_ISSUE\",\"title\":\"Issue\",\"start\":\"2026-11-01T08:00\",\"status\":\"DONE\","
                + "\"doneAt\":\"2026-11-02T09:00:00\"}";
        Map<String, Object> json = ResourceWebModules.maintenanceJson(MAINTENANCE, data, null, lookup());

        assertEquals("KNOWN_ISSUE", json.get("type"));
        assertNull(json.get("end"));
        assertNull(json.get("durationMinutes"));
        assertEquals(false, json.get("active"));
        assertEquals("2026-11-02T09:00:00", json.get("doneAt"));
    }

    @Test
    public void readableOfClassAndModuleCounts() {
        assertEquals(Collections.singletonList(APPOINTMENT), lookup().readableOfClass(Appointment.CLASS_NAME));
        assertTrue(lookup(APPOINTMENT).readableOfClass(Appointment.CLASS_NAME).isEmpty());

        Map<String, Object> counts = ResourceWebModules.moduleCounts(Arrays.asList(SITE, APPOINTMENT, MAINTENANCE));
        assertEquals(1, counts.get("appointments"));
        assertEquals(1, counts.get("maintenance"));
        assertEquals(0, ResourceWebModules.moduleCounts(Collections.singletonList(SITE)).get("appointments"));
    }

    @Test
    public void sortedByTimeWithMissingTimesLast() {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String[] e : new String[][]{{"b", "2026-12-01T00:00"}, {"a", null}, {"c", "2026-01-01T00:00"}}) {
            Map<String, Object> m = new HashMap<>();
            m.put("title", e[0]);
            m.put("due", e[1]);
            entries.add(m);
        }
        ResourceWebModules.sortByTime(entries, "due", true);
        assertEquals("c", entries.get(0).get("title"));
        assertEquals("a", entries.get(2).get("title"));
        ResourceWebModules.sortByTime(entries, "due", false);
        assertEquals("b", entries.get(0).get("title"));
        assertEquals("a", entries.get(2).get("title"));
    }

    @Test
    public void answerIsSerializable() throws Exception {
        Map<String, Object> json = ResourceWebModules.appointmentJson(APPOINTMENT, APPOINTMENT_JSON, null, lookup(), TODAY);
        String text = new ObjectMapper().writeValueAsString(json);
        assertTrue(text.contains("\"nextDue\":\"2027-11-02T09:00\""));
        assertTrue(text.contains("\"lastMail\":null"));
    }
}
