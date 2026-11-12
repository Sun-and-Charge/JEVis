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
import org.jevis.commons.appointment.Appointment;
import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.maintenance.MaintenanceEntry;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.junit.Test;

import java.util.Map;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

/**
 * Write endpoints for alarms, appointments and maintenance messages (stage E1).
 */
public class ResourceWebEditTest {

    private static final String CONFIG = ResourceWebAlarms.CLASS_ALARM_CONFIGURATION;
    private static final DateTime NOW = new DateTime(2026, 11, 12, 10, 30, 15, 0, ZONE);
    private static final DateTime EARLIER = new DateTime(2026, 11, 1, 8, 0, ZONE);

    /* organisation 1 > alarm folder 5 > configurations 100 (enabled), 101 (disabled), 102 (hidden), 103 (recycle
       bin); folder 6 > appointment 200, maintenance message 300; folder 7 > other object 400 */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        JsonObject bin = obj(103, "Bin alarm", CONFIG, 5);
        bin.setDeleteTS("2026-11-01T00:00:00.000+01:00");
        store.add(obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(5, "Alarms", "Alarm Directory", 1),
                obj(100, "Alarm A", CONFIG, 5),
                obj(101, "Alarm B", CONFIG, 5),
                obj(102, "Alarm C", CONFIG, 5),
                bin,
                obj(6, "Entries", "Appointment Directory", 1),
                obj(200, "Old title", Appointment.CLASS_NAME, 6),
                obj(300, "Server update", MaintenanceEntry.CLASS_NAME, 6),
                obj(400, "Something", "Data Directory", 1));
        store.hidden.add(102L);
        store.type(CONFIG, null, ResourceWebAlarms.ATTRIBUTE_CHECKED, JEVisConstants.PrimitiveType.BOOLEAN,
                ResourceWebAlarms.ATTRIBUTE_ENABLED, JEVisConstants.PrimitiveType.BOOLEAN);
        store.type(Appointment.CLASS_NAME, null, EntryFormat.ATTRIBUTE_DATA, JEVisConstants.PrimitiveType.STRING);
        for (long id : new long[]{100, 102, 103}) store.sample(id, ResourceWebAlarms.ATTRIBUTE_ENABLED, EARLIER, "true", null);
        store.sample(101, ResourceWebAlarms.ATTRIBUTE_ENABLED, EARLIER, "false", null);
        return store;
    }

    // ---------------------------------------------------------------- alarms

    @Test
    public void confirmWritesCheckedLikeTheControlCenter() throws Exception {
        FakeEditStore store = store();
        store.write.add(100L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.confirm(s, 100, "{\"checked\":true}", ZONE, NOW));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertTrue(json.get("checked").asBoolean());
        assertEquals(FakeEditStore.ACCOUNT, json.get("checkedBy").asText());
        assertEquals("2026-11-12T10:30:15.000+01:00", json.get("checkedAt").asText());

        JsonSample sample = store.at(100, ResourceWebAlarms.ATTRIBUTE_CHECKED, NOW);
        assertEquals("true", sample.getValue());
        assertEquals("Checked by " + FakeEditStore.ACCOUNT, sample.getNote());
        assertTrue(store.writes.get(0).startsWith("100/Alarm Checked " + JEVisConstants.PrimitiveType.BOOLEAN + " true"));
        assertEquals("CREATE_SAMPLE 100:Alarm Checked|1|web:alarm-confirm", store.logs.get(0));
    }

    @Test
    public void unconfirmWithExecuteRight() throws Exception {
        FakeEditStore store = store();
        store.execute.add(100L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.confirm(s, 100, "{\"checked\":false}", ZONE, NOW));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertFalse(json.get("checked").asBoolean());
        assertTrue(json.get("checkedBy").isNull());
        JsonSample sample = store.at(100, ResourceWebAlarms.ATTRIBUTE_CHECKED, NOW);
        assertEquals("false", sample.getValue());
        assertEquals("Unchecked by " + FakeEditStore.ACCOUNT, sample.getNote());
    }

    @Test
    public void confirmRefusedWithoutRight() throws Exception {
        FakeEditStore store = store();
        store.delete.add(100L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.confirm(s, 100, "{\"checked\":true}", ZONE, NOW));
        assertEquals(403, reply.status);
        assertTrue(store.writes.isEmpty());
        assertTrue(store.logs.isEmpty());
    }

    @Test
    public void confirmOnlyEnabledReadableConfigurations() throws Exception {
        FakeEditStore store = store();
        for (long id : new long[]{100, 101, 102, 103, 400}) store.write.add(id);
        for (long id : new long[]{101, 102, 103, 400, 999}) {
            WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.confirm(s, id, "{\"checked\":true}", ZONE, NOW));
            assertEquals("id " + id, 404, reply.status);
        }
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void confirmValidatesTheBody() throws Exception {
        FakeEditStore store = store();
        store.write.add(100L);
        for (String body : new String[]{null, "", "[]", "{}", "{\"checked\":\"yes\"}", "{nope"}) {
            WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.confirm(s, 100, body, ZONE, NOW));
            assertEquals(String.valueOf(body), 400, reply.status);
        }
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void confirmAllListsRefusedIds() throws Exception {
        FakeEditStore store = store();
        store.write.add(100L);
        store.write.add(101L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.confirmAll(s, "{\"ids\":[100,101,102,999,100]}", NOW));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("[100]", json.get("confirmed").toString());
        assertEquals("[101,102,999]", json.get("refused").toString());
        assertEquals(1, store.writes.size());
        assertEquals("true", store.at(100, ResourceWebAlarms.ATTRIBUTE_CHECKED, NOW).getValue());

        assertEquals(400, WebEdit.call(store, s -> ResourceWebEdit.confirmAll(s, "{\"ids\":[\"a\"]}", NOW)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEdit.confirmAll(s, "{}", NOW)).status);
    }

    @Test
    public void alarmsAnswerHasCanConfirm() {
        JsonObject configuration = obj(100, "Alarm A", CONFIG, 5);
        Map<String, Object> json = ResourceWebAlarms.configurationJson(configuration,
                new ResourceWebAlarms.Checked(false, null, null), ZONE, true);
        assertEquals(Boolean.TRUE, json.get("canConfirm"));
        FakeEditStore store = store();
        assertFalse(WebEdit.canConfirmAlarm(store.rights(), configuration));
        store.execute.add(100L);
        assertTrue(WebEdit.canConfirmAlarm(store.rights(), configuration));
    }

    // ---------------------------------------------------------------- appointments

    private static String appointment(Appointment.Recurrence recurrence, Appointment.Status status) {
        Appointment appointment = new Appointment();
        appointment.setTitle("Check meter");
        appointment.setDueDate(new LocalDateTime(2026, 11, 10, 0, 0));
        appointment.setRecurrence(recurrence);
        appointment.setStatus(status);
        return EntryFormat.toJson(appointment);
    }

    private static String version(DateTime time) {
        return WebEdit.iso(time.getMillis(), org.joda.time.DateTimeZone.getDefault());
    }

    @Test
    public void appointmentDoneNotRecurring() throws Exception {
        FakeEditStore store = store();
        store.write.add(200L);
        store.sample(200, EntryFormat.ATTRIBUTE_DATA, EARLIER, appointment(Appointment.Recurrence.NONE, Appointment.Status.OPEN), null);
        String body = "{\"version\":\"" + version(EARLIER) + "\"}";
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 200, body, NOW));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("DONE", json.get("status").asText());
        assertEquals("Check meter", json.get("name").asText());
        assertTrue(json.get("canWrite").asBoolean());
        assertEquals(version(NOW), json.get("version").asText());
        assertNotNull(json.get("lastDone").asText());

        Appointment written = EntryFormat.fromJson(store.at(200, EntryFormat.ATTRIBUTE_DATA, NOW).getValue(), Appointment.class);
        assertEquals(Appointment.Status.DONE, written.getStatus());
        assertEquals("[200 Check meter]", store.renames.toString());
        assertEquals("CREATE_SAMPLE 200:Data|1|web:appointment-done", store.logs.get(0));
        assertEquals("UPDATE_OBJECT 200:Check meter|web:appointment-done", store.logs.get(1));
    }

    @Test
    public void appointmentDoneRecurringMovesTheDueDate() throws Exception {
        FakeEditStore store = store();
        store.write.add(200L);
        store.add(obj(201, "Check meter", Appointment.CLASS_NAME, 6));
        store.sample(201, EntryFormat.ATTRIBUTE_DATA, EARLIER, appointment(Appointment.Recurrence.MONTHLY, Appointment.Status.OPEN), null);
        store.write.add(201L);
        String body = "{\"version\":\"" + version(EARLIER) + "\"}";
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 201, body, NOW));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("OPEN", json.get("status").asText());
        assertTrue(json.get("due").asText().startsWith("2026-12-10"));
        assertFalse(json.get("lastDone").isNull());
        assertTrue("same name: not renamed", store.renames.isEmpty());
        assertEquals(1, store.logs.size());
    }

    @Test
    public void appointmentVersionConflict() throws Exception {
        FakeEditStore store = store();
        store.write.add(200L);
        store.sample(200, EntryFormat.ATTRIBUTE_DATA, EARLIER, appointment(Appointment.Recurrence.NONE, Appointment.Status.OPEN), null);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 200,
                "{\"version\":\"" + version(EARLIER.minusMinutes(1)) + "\"}", NOW));
        assertEquals(409, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("OPEN", json.get("status").asText());
        assertEquals(version(EARLIER), json.get("version").asText());
        assertTrue(store.writes.isEmpty());

        WebEdit.Reply missing = WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 200, "{}", NOW));
        assertEquals(400, missing.status);
        assertEquals("version missing", missing.body);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 200, "{\"version\":null}", NOW)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 200, "{\"version\":\"x\"}", NOW)).status);
    }

    @Test
    public void appointmentRightsAndNotFound() throws Exception {
        FakeEditStore store = store();
        store.execute.add(200L);
        store.sample(200, EntryFormat.ATTRIBUTE_DATA, EARLIER, appointment(Appointment.Recurrence.NONE, Appointment.Status.OPEN), null);
        String body = "{\"version\":\"" + version(EARLIER) + "\"}";
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 200, body, NOW)).status);
        store.write.add(300L);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 300, body, NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 999, body, NOW)).status);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void appointmentAlreadyDone() throws Exception {
        FakeEditStore store = store();
        store.write.add(200L);
        store.sample(200, EntryFormat.ATTRIBUTE_DATA, EARLIER, appointment(Appointment.Recurrence.NONE, Appointment.Status.DONE), null);
        String body = "{\"version\":\"" + version(EARLIER) + "\"}";
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEdit.appointmentDone(s, 200, body, NOW)).status);
        assertTrue(store.writes.isEmpty());
    }

    // ---------------------------------------------------------------- maintenance

    private static String maintenance(MaintenanceEntry.Status status) {
        MaintenanceEntry entry = new MaintenanceEntry();
        entry.setType(MaintenanceEntry.Type.MAINTENANCE);
        entry.setTitle("Server update");
        entry.setStartDate(new LocalDateTime(2026, 11, 12, 8, 0));
        entry.setStatus(status);
        return EntryFormat.toJson(entry);
    }

    @Test
    public void maintenanceStatusSetsAndClearsDoneAt() throws Exception {
        FakeEditStore store = store();
        store.write.add(300L);
        store.sample(300, EntryFormat.ATTRIBUTE_DATA, EARLIER, maintenance(MaintenanceEntry.Status.IN_PROGRESS), null);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEdit.maintenanceStatus(s, 300,
                "{\"status\":\"DONE\",\"version\":\"" + version(EARLIER) + "\"}", NOW));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("DONE", json.get("status").asText());
        assertFalse(json.get("doneAt").isNull());
        assertFalse(json.get("active").asBoolean());
        assertEquals("CREATE_SAMPLE 300:Data|1|web:maintenance-status", store.logs.get(0));

        DateTime later = NOW.plusMinutes(5);
        reply = WebEdit.call(store, s -> ResourceWebEdit.maintenanceStatus(s, 300,
                "{\"status\":\"PLANNED\",\"version\":\"" + version(NOW) + "\"}", later));
        assertEquals(200, reply.status);
        json = FakeEditStore.json(reply);
        assertEquals("PLANNED", json.get("status").asText());
        assertTrue(json.get("doneAt").isNull());
        assertEquals(version(later), json.get("version").asText());
    }

    @Test
    public void maintenanceStatusValidation() throws Exception {
        FakeEditStore store = store();
        store.sample(300, EntryFormat.ATTRIBUTE_DATA, EARLIER, maintenance(MaintenanceEntry.Status.PLANNED), null);
        String ok = "{\"status\":\"DONE\",\"version\":\"" + version(EARLIER) + "\"}";
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEdit.maintenanceStatus(s, 300, ok, NOW)).status);
        store.write.add(300L);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEdit.maintenanceStatus(s, 300,
                "{\"status\":\"FINISHED\",\"version\":\"" + version(EARLIER) + "\"}", NOW)).status);
        assertEquals(409, WebEdit.call(store, s -> ResourceWebEdit.maintenanceStatus(s, 300,
                "{\"status\":\"DONE\",\"version\":\"" + version(NOW) + "\"}", NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEdit.maintenanceStatus(s, 200, ok, NOW)).status);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void entryEditInfo() {
        FakeEditStore store = store();
        store.write.add(200L);
        JsonSample data = new JsonSample();
        data.setTs(FakeEditStore.ts(EARLIER));
        Map<String, Object> json = ResourceWebModules.editInfo(new java.util.LinkedHashMap<>(), obj(200, "x", Appointment.CLASS_NAME, 6),
                data, store.rights());
        assertEquals(Boolean.TRUE, json.get("canWrite"));
        assertEquals(version(EARLIER), json.get("version"));
        json = ResourceWebModules.editInfo(new java.util.LinkedHashMap<>(), obj(300, "x", MaintenanceEntry.CLASS_NAME, 6),
                null, store.rights());
        assertEquals(Boolean.FALSE, json.get("canWrite"));
        assertNull(json.get("version"));
    }

    @Test
    public void analysisRowsGetEditInfo() throws Exception {
        org.jevis.commons.chart.AnalysisResult result = new org.jevis.commons.chart.AnalysisResult();
        org.jevis.commons.chart.ChartResult chart = new org.jevis.commons.chart.ChartResult(0);
        chart.getRows().add(new org.jevis.commons.chart.RowResult("0-20-Value", org.jevis.commons.chart.RowStatus.OK, null));
        chart.getRows().add(new org.jevis.commons.chart.RowResult("0-21-Value", org.jevis.commons.chart.RowStatus.OK, null));
        result.getCharts().add(chart);
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("rowClass", "Data");
        info.put("canAddNote", true);
        info.put("canEnter", true);
        Map<String, Map<String, Object>> editInfo = new java.util.HashMap<>();
        editInfo.put("0-20-Value", info);
        JsonNode json = FakeEditStore.MAPPER.readTree(FakeEditStore.MAPPER.writeValueAsString(
                ResourceWebAnalysis.dataJson(result, java.util.Collections.emptyMap(), editInfo)));
        JsonNode row = json.get("charts").get(0).get("rows").get(0);
        assertEquals("Data", row.get("rowClass").asText());
        assertTrue(row.get("canAddNote").asBoolean());
        assertTrue(row.get("canEnter").asBoolean());
        assertFalse(json.get("charts").get(0).get("rows").get(1).has("canEnter"));
    }

    @Test
    public void objectNameLikeEntryStore() {
        assertEquals("-", ResourceWebEdit.objectName("  "));
        assertEquals("a b", ResourceWebEdit.objectName("a\r\n\tb"));
        StringBuilder longTitle = new StringBuilder();
        for (int i = 0; i < 130; i++) longTitle.append('x');
        assertEquals(ResourceWebEdit.MAX_NAME_LENGTH, ResourceWebEdit.objectName(longTitle.toString()).length());
    }
}
