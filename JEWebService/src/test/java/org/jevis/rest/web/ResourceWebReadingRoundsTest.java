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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.junit.Test;

import java.util.*;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

/**
 * Reading rounds and route sheet (stage E8.2).
 */
public class ResourceWebReadingRoundsTest {

    private static final DateTime NOW = new DateTime(2026, 11, 13, 10, 30, 15, ZONE);
    private static final DateTime T0 = new DateTime(2026, 11, 10, 9, 0, 0, ZONE);
    private static final DateTime OLD = new DateTime(2026, 1, 1, 0, 0, ZONE);
    private static final String EMI = "Electricity Measurement Instrument";
    private static final String MI = "Measurement Instrument";
    private static final String ROUND = ResourceWebReadingRounds.CLASS_ROUND;
    private static final String ROUND_NAME = "Monatsablesung Musterwerk";

    /* organisation 1 > building 2 "Musterwerk" > measurement directory 3 > meters 101 "Zähler A" (row 201, read on
       2026-11-02), 102 "Zähler B" (row 202, last read 2026-10-01), 103 "Zähler C" (row 203, 15-minute values:
       automatic), 104 "Zähler D" (hidden, row 204 read 2026-10-30); round directory 7000 > round 7001 with the meters
       101, 103, 102, 104; building 5 "Zweitwerk" > directory 6 > meter 111; users 41 (Max Muster, e-mail), 42 (no
       name, no e-mail), 43 (hidden), group 44 */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        store.add(obj(1, "Musterfirma", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(2, "Musterwerk", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(3, "Messstellen", ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY, 2),
                obj(101, "Zähler A", EMI, 3),
                obj(102, "Zähler B", EMI, 3),
                obj(103, "Zähler C", EMI, 3),
                obj(104, "Zähler D", EMI, 3),
                obj(10, "Daten", "Data Directory", 2),
                obj(201, "Strom A", "Data", 10),
                obj(202, "Strom B", "Data", 10),
                obj(203, "Strom C", "Data", 10),
                obj(204, "Strom D", "Data", 10),
                obj(5, "Zweitwerk", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(6, "Messstellen", ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY, 5),
                obj(111, "Wärmezähler", EMI, 6),
                obj(211, "Wärme", "Data", 10),
                obj(30, "Benutzer", "User Directory", 1),
                obj(41, "max.muster", "User", 30),
                obj(42, "erika", "User", 30),
                obj(43, "verborgen", "User", 30),
                obj(44, "Energieteam", "Group", 30),
                obj(7000, ResourceWebReadingRounds.CLASS_DIRECTORY, ResourceWebReadingRounds.CLASS_DIRECTORY, 2),
                obj(7001, ROUND_NAME, ROUND, 7000));
        store.hidden.add(104L);
        store.hidden.add(43L);
        Object[] meterTypes = {"Online ID", JEVisConstants.PrimitiveType.STRING, "Location", JEVisConstants.PrimitiveType.STRING,
                "Serial Number", JEVisConstants.PrimitiveType.STRING, "Meter Point", JEVisConstants.PrimitiveType.STRING,
                "Decimal Places", JEVisConstants.PrimitiveType.LONG};
        store.type(MI, null, meterTypes);
        store.type(EMI, MI, meterTypes);
        store.type("Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE, "Period", JEVisConstants.PrimitiveType.STRING);
        store.type("User", null, "First Name", JEVisConstants.PrimitiveType.STRING, "Last Name", JEVisConstants.PrimitiveType.STRING,
                "E-Mail", JEVisConstants.PrimitiveType.STRING);
        store.type("Group", null);
        store.type(ResourceWebReadingRounds.CLASS_DIRECTORY, null);
        store.type(ROUND, null, ResourceWebReadingRounds.ATTRIBUTE_ENABLED, JEVisConstants.PrimitiveType.BOOLEAN,
                ResourceWebReadingRounds.ATTRIBUTE_METERS, JEVisConstants.PrimitiveType.STRING,
                ResourceWebReadingRounds.ATTRIBUTE_READERS, JEVisConstants.PrimitiveType.STRING,
                ResourceWebReadingRounds.ATTRIBUTE_SUPERVISOR, JEVisConstants.PrimitiveType.STRING,
                ResourceWebReadingRounds.ATTRIBUTE_START_DATE, JEVisConstants.PrimitiveType.STRING,
                ResourceWebReadingRounds.ATTRIBUTE_PERIOD, JEVisConstants.PrimitiveType.STRING,
                ResourceWebReadingRounds.ATTRIBUTE_LEAD_DAYS, JEVisConstants.PrimitiveType.LONG,
                ResourceWebReadingRounds.ATTRIBUTE_REMINDER_DAYS, JEVisConstants.PrimitiveType.LONG,
                ResourceWebReadingRounds.ATTRIBUTE_ESCALATION_DAYS, JEVisConstants.PrimitiveType.LONG,
                ResourceWebReadingRounds.ATTRIBUTE_REMARKS, JEVisConstants.PrimitiveType.STRING,
                ResourceWebReadingRounds.ATTRIBUTE_MAIL_STATE, JEVisConstants.PrimitiveType.STRING);

        for (long id : new long[]{101, 102, 103, 104}) {
            store.sample(id, "Online ID", OLD, (id + 100) + ":Value", null);
            store.sample(id, "Location", OLD, "Keller " + (id - 100), null);
        }
        store.sample(101, "Serial Number", OLD, "A-123", null);
        store.sample(101, "Meter Point", OLD, "DE0001", null);
        store.sample(111, "Online ID", OLD, "211", null);
        store.sample(201, "Period", OLD, "P1M", null);
        store.sample(201, "Value", new DateTime(2026, 10, 1, 0, 0, ZONE), "100", null);
        store.sample(201, "Value", new DateTime(2026, 11, 2, 0, 0, ZONE), "120", null);
        store.sample(202, "Period", OLD, "P1M", null);
        store.sample(202, "Value", new DateTime(2026, 9, 1, 0, 0, ZONE), "40", null);
        store.sample(202, "Value", new DateTime(2026, 10, 1, 0, 0, ZONE), "50", null);
        store.sample(203, "Period", OLD, "PT15M", null);
        store.sample(203, "Value", new DateTime(2026, 9, 1, 0, 0, ZONE), "7", null);
        store.sample(204, "Period", OLD, "P1M", null);
        store.sample(204, "Value", new DateTime(2026, 10, 30, 0, 0, ZONE), "5", null);
        store.sample(41, "First Name", OLD, "Max", null);
        store.sample(41, "Last Name", OLD, "Muster", null);
        store.sample(41, "E-Mail", OLD, "max@example.org", null);

        round(store, 7001, T0, "[101,103,102,104]", "[44]", "[42,43]", "2026-01-01", "P1M", 3, 3, 7);
        store.sample(7001, ResourceWebReadingRounds.ATTRIBUTE_REMARKS, T0, "Schlüssel beim Pförtner", null);
        store.write.add(7001L);
        store.delete.add(7001L);
        return store;
    }

    private static void round(FakeEditStore store, long id, DateTime time, String meters, String readers,
                              String supervisor, String start, String period, int lead, int reminder, int escalation) {
        store.sample(id, ResourceWebReadingRounds.ATTRIBUTE_ENABLED, time, "true", null);
        store.sample(id, ResourceWebReadingRounds.ATTRIBUTE_METERS, time, meters, null);
        store.sample(id, ResourceWebReadingRounds.ATTRIBUTE_READERS, time, readers, null);
        store.sample(id, ResourceWebReadingRounds.ATTRIBUTE_SUPERVISOR, time, supervisor, null);
        store.sample(id, ResourceWebReadingRounds.ATTRIBUTE_START_DATE, time, start, null);
        store.sample(id, ResourceWebReadingRounds.ATTRIBUTE_PERIOD, time, period, null);
        store.sample(id, ResourceWebReadingRounds.ATTRIBUTE_LEAD_DAYS, time, String.valueOf(lead), null);
        store.sample(id, ResourceWebReadingRounds.ATTRIBUTE_REMINDER_DAYS, time, String.valueOf(reminder), null);
        store.sample(id, ResourceWebReadingRounds.ATTRIBUTE_ESCALATION_DAYS, time, String.valueOf(escalation), null);
    }

    private static JsonNode list(FakeEditStore store, Long site) throws Exception {
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebReadingRounds.list(s, site, NOW));
        assertEquals(200, reply.status);
        return FakeEditStore.json(reply);
    }

    private static JsonNode round(JsonNode list, long id) {
        for (JsonNode round : list.get("rounds")) {
            if (round.get("id").asLong() == id) return round;
        }
        return null;
    }

    private static List<String> messages(WebEdit.Reply reply) throws Exception {
        assertEquals(String.valueOf(reply.body), 400, reply.status);
        List<String> messages = new ArrayList<>();
        for (JsonNode message : FakeEditStore.json(reply).get("messages")) messages.add(message.asText());
        return messages;
    }

    private static boolean any(List<String> messages, String part) {
        for (String message : messages) {
            if (message.contains(part)) return true;
        }
        return false;
    }

    private static String body(String name, Long site, String meters, String readers, String extra) {
        return "{" + (name != null ? "\"name\":\"" + name + "\"," : "") + (site != null ? "\"site\":" + site + "," : "")
                + "\"enabled\":true,\"meters\":" + meters + ",\"readers\":" + readers + ",\"supervisor\":[],"
                + "\"startDate\":\"2026-11-01\",\"period\":\"P1M\",\"leadDays\":3,\"reminderDays\":3,"
                + "\"escalationDays\":7,\"remarks\":\"Hof\"" + (extra != null ? "," + extra : "") + "}";
    }

    // ---------------------------------------------------------------- order of a period

    private static ResourceWebReadingRounds.Schedule schedule(String start, String period, String today) {
        return ResourceWebReadingRounds.schedule(LocalDate.parse(start), Period.parse(period), LocalDate.parse(today));
    }

    private static void assertSchedule(String due, String next, ResourceWebReadingRounds.Schedule schedule) {
        assertEquals(due, schedule.due != null ? schedule.due.toString() : null);
        assertEquals(next, schedule.nextDue != null ? schedule.nextDue.toString() : null);
    }

    @Test
    public void scheduleOfAPeriod() {
        assertSchedule("2026-11-01", "2026-12-01", schedule("2026-01-01", "P1M", "2026-11-13"));
        assertSchedule("2026-11-01", "2026-12-01", schedule("2026-01-01", "P1M", "2026-11-01"));
        assertSchedule("2026-10-01", "2026-11-01", schedule("2026-01-01", "P1M", "2026-10-31"));
        assertSchedule("2026-01-01", "2026-02-01", schedule("2026-01-01", "P1M", "2026-01-01"));
        /* before the start */
        assertSchedule(null, "2026-12-01", schedule("2026-12-01", "P1M", "2026-11-13"));
        /* month ends: always counted from the start date */
        assertSchedule("2026-02-28", "2026-03-31", schedule("2026-01-31", "P1M", "2026-02-28"));
        assertSchedule("2026-02-28", "2026-03-31", schedule("2026-01-31", "P1M", "2026-03-30"));
        assertSchedule("2026-03-31", "2026-04-30", schedule("2026-01-31", "P1M", "2026-03-31"));
        assertSchedule("2025-02-28", "2026-02-28", schedule("2024-02-29", "P1Y", "2025-03-01"));
        assertSchedule("2028-02-29", "2029-02-28", schedule("2024-02-29", "P1Y", "2028-03-01"));
        assertSchedule("2026-08-31", "2026-11-30", schedule("2026-05-31", "P3M", "2026-11-13"));
        /* weeks over years */
        ResourceWebReadingRounds.Schedule weeks = schedule("2020-01-06", "P2W", "2026-11-13");
        assertFalse(weeks.due.isAfter(LocalDate.parse("2026-11-13")));
        assertTrue(weeks.nextDue.isAfter(LocalDate.parse("2026-11-13")));
        assertEquals(14, org.joda.time.Days.daysBetween(weeks.due, weeks.nextDue).getDays());
        assertEquals(0, org.joda.time.Days.daysBetween(LocalDate.parse("2020-01-06"), weeks.due).getDays() % 14);
        assertSchedule("2026-11-09", "2026-11-16", schedule("2026-01-05", "P1W", "2026-11-13"));
        /* no start or period */
        assertSchedule(null, null, ResourceWebReadingRounds.schedule(null, Period.parse("P1M"), LocalDate.parse("2026-11-13")));
        assertSchedule(null, null, ResourceWebReadingRounds.schedule(LocalDate.parse("2026-01-01"), null, LocalDate.parse("2026-11-13")));
        assertNull(ResourceWebReadingRounds.period("P0D"));
        assertNull(ResourceWebReadingRounds.period("nonsense"));
    }

    @Test
    public void stateOfARound() {
        LocalDate due = LocalDate.parse("2026-11-01");
        assertEquals(ResourceWebReadingRounds.State.INACTIVE, ResourceWebReadingRounds.state(false, due, 0, 3, due, 3, 7));
        assertEquals(ResourceWebReadingRounds.State.NOT_STARTED, ResourceWebReadingRounds.state(true, null, 0, 3, due, 3, 7));
        assertEquals(ResourceWebReadingRounds.State.DONE, ResourceWebReadingRounds.state(true, due, 3, 3, due.plusDays(20), 3, 7));
        assertEquals(ResourceWebReadingRounds.State.OPEN, ResourceWebReadingRounds.state(true, due, 2, 3, due, 3, 7));
        assertEquals(ResourceWebReadingRounds.State.OPEN, ResourceWebReadingRounds.state(true, due, 2, 3, due.plusDays(2), 3, 7));
        assertEquals(ResourceWebReadingRounds.State.LATE, ResourceWebReadingRounds.state(true, due, 2, 3, due.plusDays(3), 3, 7));
        assertEquals(ResourceWebReadingRounds.State.LATE, ResourceWebReadingRounds.state(true, due, 2, 3, due.plusDays(6), 3, 7));
        assertEquals(ResourceWebReadingRounds.State.ESCALATED, ResourceWebReadingRounds.state(true, due, 2, 3, due.plusDays(7), 3, 7));
        assertEquals(ResourceWebReadingRounds.State.LATE, ResourceWebReadingRounds.state(true, due, 0, 1, due, 0, 7));
    }

    // ---------------------------------------------------------------- GET list

    @Test
    public void listRound() throws Exception {
        FakeEditStore store = store();
        store.userAndGroups.addAll(Arrays.asList(41L, 44L));
        JsonNode json = list(store, null);
        assertEquals("2026-11-13", json.get("today").asText());
        assertEquals(1, json.get("rounds").size());
        JsonNode round = round(json, 7001);
        assertEquals(ROUND_NAME, round.get("name").asText());
        assertEquals(2, round.get("site").get("id").asLong());
        assertEquals("Musterwerk", round.get("site").get("name").asText());
        assertEquals(7000, round.get("directoryId").asLong());
        assertTrue(round.get("enabled").asBoolean());
        assertEquals("P1M", round.get("period").asText());
        assertEquals("2026-01-01", round.get("startDate").asText());
        assertEquals(3, round.get("leadDays").asInt());
        assertEquals(3, round.get("reminderDays").asInt());
        assertEquals(7, round.get("escalationDays").asInt());
        assertEquals("Schlüssel beim Pförtner", round.get("remarks").asText());
        assertEquals("2026-11-01", round.get("due").asText());
        assertEquals("2026-12-01", round.get("nextDue").asText());
        /* A read, C automatic, D (hidden) read, B not read; 12 days after the due date */
        assertEquals(3, round.get("done").asInt());
        assertEquals(4, round.get("total").asInt());
        assertEquals("ESCALATED", round.get("state").asText());
        assertTrue(round.get("mine").asBoolean());
        assertTrue(round.get("canWrite").asBoolean());
        assertTrue(round.get("canDelete").asBoolean());
        assertEquals(WebEdit.iso(T0.getMillis(), org.joda.time.DateTimeZone.getDefault()), round.get("version").asText());

        JsonNode meters = round.get("meters");
        assertEquals(4, meters.size());
        assertEquals(Arrays.asList(101L, 103L, 102L, 104L), Arrays.asList(meters.get(0).get("id").asLong(),
                meters.get(1).get("id").asLong(), meters.get(2).get("id").asLong(), meters.get(3).get("id").asLong()));
        JsonNode a = meters.get(0);
        assertEquals("Zähler A", a.get("name").asText());
        assertEquals("Keller 1", a.get("location").asText());
        assertTrue(a.get("done").asBoolean());
        assertFalse(a.get("automatic").asBoolean());
        assertEquals("2026-11-02T00:00:00.000+01:00", a.get("last").get("timestamp").asText());
        assertEquals(120.0, a.get("last").get("value").asDouble(), 0);
        assertEquals("kWh", a.get("last").get("unit").asText());
        assertTrue(a.get("readable").asBoolean());
        assertTrue(meters.get(1).get("automatic").asBoolean());
        assertTrue(meters.get(1).get("done").asBoolean());
        assertFalse(meters.get(2).get("done").asBoolean());
        /* the hidden meter: id only */
        JsonNode hidden = meters.get(3);
        assertFalse(hidden.get("readable").asBoolean());
        assertNull(hidden.get("name"));
        assertNull(hidden.get("last"));
        assertEquals(2, hidden.size());

        JsonNode readers = round.get("readers");
        assertEquals(1, readers.size());
        assertEquals(44, readers.get(0).get("id").asLong());
        assertEquals("Energieteam", readers.get(0).get("name").asText());
        assertEquals("GROUP", readers.get(0).get("kind").asText());
        JsonNode supervisor = round.get("supervisor");
        assertEquals(2, supervisor.size());
        assertEquals("erika", supervisor.get(0).get("name").asText());
        assertEquals("USER", supervisor.get(0).get("kind").asText());
        assertTrue("not readable: no name", supervisor.get(1).get("name").isNull());
        assertEquals("USER", supervisor.get(1).get("kind").asText());
    }

    @Test
    public void listStatesAndRights() throws Exception {
        FakeEditStore store = store();
        store.sample(7001, ResourceWebReadingRounds.ATTRIBUTE_ESCALATION_DAYS, T0, "14", null);
        assertEquals("LATE", round(list(store, null), 7001).get("state").asText());
        store.sample(7001, ResourceWebReadingRounds.ATTRIBUTE_REMINDER_DAYS, T0, "20", null);
        store.sample(7001, ResourceWebReadingRounds.ATTRIBUTE_ESCALATION_DAYS, T0, "30", null);
        assertEquals("OPEN", round(list(store, null), 7001).get("state").asText());
        store.sample(202, "Value", new DateTime(2026, 11, 3, 8, 0, ZONE), "60", null);
        JsonNode round = round(list(store, null), 7001);
        assertEquals("DONE", round.get("state").asText());
        assertEquals(4, round.get("done").asInt());
        store.sample(7001, ResourceWebReadingRounds.ATTRIBUTE_ENABLED, T0, "false", null);
        assertEquals("INACTIVE", round(list(store, null), 7001).get("state").asText());
        store.sample(7001, ResourceWebReadingRounds.ATTRIBUTE_ENABLED, T0, "true", null);
        store.sample(7001, ResourceWebReadingRounds.ATTRIBUTE_START_DATE, T0, "2026-12-01", null);
        round = round(list(store, null), 7001);
        assertEquals("NOT_STARTED", round.get("state").asText());
        assertTrue(round.get("due").isNull());
        assertEquals("2026-12-01", round.get("nextDue").asText());

        /* rights, mine, site filter, deleted meters, unreadable rounds */
        store = store();
        store.write.clear();
        store.delete.clear();
        store.userAndGroups.add(41L);
        store.objects.removeIf(o -> o.getId() == 102);
        round = round(list(store, null), 7001);
        assertFalse(round.get("canWrite").asBoolean());
        assertFalse(round.get("canDelete").asBoolean());
        assertFalse(round.get("mine").asBoolean());
        assertEquals("deleted meters are left out", 3, round.get("total").asInt());
        assertEquals("DONE", round.get("state").asText());
        assertEquals(1, list(store, 2L).get("rounds").size());
        assertEquals(0, list(store, 5L).get("rounds").size());
        store.hidden.add(7001L);
        store.add();
        assertEquals(0, list(store, null).get("rounds").size());
    }

    @Test
    public void mineByGroupMembershipAndSorting() throws Exception {
        FakeEditStore store = store();
        store.add(obj(7002, "Wochenrunde", ROUND, 7000), obj(7003, "Alte Runde", ROUND, 7000),
                obj(7004, "Bad", ROUND, 7000));
        round(store, 7002, T0, "[101]", "[41]", "[]", "2026-11-09", "P1W", 1, 3, 7);
        round(store, 7003, T0, "[102]", "[42]", "[]", "2026-01-01", "P1M", 3, 3, 7);
        store.sample(7003, ResourceWebReadingRounds.ATTRIBUTE_ENABLED, T0, "false", null);
        round(store, 7004, T0, "[102]", "[42]", "[]", "2026-01-01", "P1M", 3, 3, 7);
        /* the user is member of group 44 (reader of 7001), not reader of 7002 */
        store.userAndGroups.addAll(Arrays.asList(42L, 44L));
        JsonNode rounds = list(store, null).get("rounds");
        assertEquals(4, rounds.size());
        /* mine first (ESCALATED by name, then INACTIVE), then the others */
        assertEquals(7004, rounds.get(0).get("id").asLong());
        assertEquals("ESCALATED", rounds.get(0).get("state").asText());
        assertEquals(7001, rounds.get(1).get("id").asLong());
        assertTrue("by membership of group 44", rounds.get(1).get("mine").asBoolean());
        assertEquals(7003, rounds.get(2).get("id").asLong());
        assertEquals("INACTIVE", rounds.get(2).get("state").asText());
        assertEquals(7002, rounds.get(3).get("id").asLong());
        assertFalse(rounds.get(3).get("mine").asBoolean());
        /* weekly from 2026-11-09, lead 1: A was read on 2026-11-02, too early */
        assertEquals("2026-11-09", rounds.get(3).get("due").asText());
        assertEquals("2026-11-16", rounds.get(3).get("nextDue").asText());
        assertEquals("LATE", rounds.get(3).get("state").asText());
    }

    @Test
    public void doneWindowWithLeadDays() throws Exception {
        FakeEditStore store = store();
        store.samples.remove("202/Value");
        /* due 2026-11-01, lead 3: [2026-10-29 00:00, 2026-12-01 00:00) */
        store.sample(202, "Value", new DateTime(2026, 10, 28, 23, 59, ZONE), "1", null);
        assertFalse(round(list(store, null), 7001).get("meters").get(2).get("done").asBoolean());
        store.sample(202, "Value", new DateTime(2026, 10, 29, 0, 0, ZONE), "2", null);
        assertTrue(round(list(store, null), 7001).get("meters").get(2).get("done").asBoolean());
        store.samples.remove("202/Value");
        store.sample(202, "Value", new DateTime(2026, 12, 1, 0, 0, ZONE), "3", null);
        assertFalse("next due is not included", round(list(store, null), 7001).get("meters").get(2).get("done").asBoolean());
        store.sample(202, "Value", new DateTime(2026, 11, 30, 23, 59, ZONE), "4", null);
        assertTrue(round(list(store, null), 7001).get("meters").get(2).get("done").asBoolean());
        store.sample(7001, ResourceWebReadingRounds.ATTRIBUTE_LEAD_DAYS, T0, "0", null);
        store.samples.remove("202/Value");
        store.sample(202, "Value", new DateTime(2026, 10, 31, 23, 0, ZONE), "5", null);
        assertFalse(round(list(store, null), 7001).get("meters").get(2).get("done").asBoolean());
    }

    // ---------------------------------------------------------------- options

    @Test
    public void options() throws Exception {
        FakeEditStore store = store();
        store.create.add(7000L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebReadingRounds.options(s, 2L));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertTrue(json.get("canCreate").asBoolean());
        assertEquals(7000, json.get("directoryId").asLong());
        JsonNode meters = json.get("meters");
        assertEquals(3, meters.size());
        assertEquals("Zähler A", meters.get(0).get("name").asText());
        assertEquals("Keller 1", meters.get(0).get("location").asText());
        assertEquals("ELECTRICITY", meters.get(0).get("medium").asText());
        assertFalse(meters.get(0).get("automatic").asBoolean());
        assertEquals(103, meters.get(2).get("id").asLong());
        assertTrue(meters.get(2).get("automatic").asBoolean());
        JsonNode people = json.get("people");
        assertEquals(3, people.size());
        assertEquals("Energieteam", people.get(0).get("name").asText());
        assertEquals("GROUP", people.get(0).get("kind").asText());
        assertEquals("erika", people.get(1).get("name").asText());
        assertFalse(people.get(1).get("email").asBoolean());
        assertEquals("Max Muster", people.get(2).get("name").asText());
        assertEquals("USER", people.get(2).get("kind").asText());
        assertTrue(people.get(2).get("email").asBoolean());

        store.create.clear();
        assertFalse(FakeEditStore.json(WebEdit.call(store, s -> ResourceWebReadingRounds.options(s, 2L))).get("canCreate").asBoolean());
        /* no directory yet: create right for the directory on the building */
        json = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebReadingRounds.options(s, 5L)));
        assertTrue(json.get("directoryId").isNull());
        assertFalse(json.get("canCreate").asBoolean());
        assertEquals(1, json.get("meters").size());
        store.create.add(5L);
        assertTrue(FakeEditStore.json(WebEdit.call(store, s -> ResourceWebReadingRounds.options(s, 5L))).get("canCreate").asBoolean());

        assertEquals(400, WebEdit.call(store, s -> ResourceWebReadingRounds.options(s, null)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadingRounds.options(s, 3L)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadingRounds.options(s, 999L)).status);
    }

    // ---------------------------------------------------------------- POST

    private static WebEdit.Reply create(FakeEditStore store, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebReadingRounds.create(s, body, NOW));
    }

    @Test
    public void createInTheExistingDirectory() throws Exception {
        FakeEditStore store = store();
        store.create.add(7000L);
        WebEdit.Reply reply = create(store, body("Nachablesung Süd", 2L, "[102,101]", "[41,44]", null));
        assertEquals(String.valueOf(reply.body), 201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        long id = json.get("id").asLong();
        assertEquals("Nachablesung Süd", json.get("name").asText());
        assertEquals(7000, json.get("directoryId").asLong());
        assertEquals(7000L, (long) store.object(id).getParent());
        assertEquals(ROUND, store.object(id).getJevisClass());
        assertEquals("[102,101]", store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_METERS).getValue());
        assertEquals("[41,44]", store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_READERS).getValue());
        assertEquals("[]", store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_SUPERVISOR).getValue());
        assertEquals("true", store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_ENABLED).getValue());
        assertEquals("2026-11-01", store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_START_DATE).getValue());
        assertEquals("P1M", store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_PERIOD).getValue());
        assertEquals("3", store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_LEAD_DAYS).getValue());
        assertEquals("Hof", store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_REMARKS).getValue());
        assertNull("Mail State is never written", store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_MAIL_STATE));
        assertEquals(new DateTime(2026, 11, 13, 10, 30, 15, ZONE).getMillis(),
                (long) ResourceWebNotes.millis(store.latest(id, ResourceWebReadingRounds.ATTRIBUTE_PERIOD).getTs()));
        assertTrue(store.writes.toString(), store.writes.contains(id + "/Enabled 4 true | null"));
        assertTrue(store.writes.contains(id + "/Lead Days 2 3 | null"));
        assertEquals("the round and its 10 attributes", 11, store.logs.size());
        assertTrue(store.logs.get(0), store.logs.get(0).startsWith("CREATE_OBJECT " + id + ":Nachablesung Süd|web:round-create"));
        assertTrue(store.logs.get(1).endsWith("|web:round-create"));
        assertEquals(2, json.get("meters").size());
        assertEquals("ESCALATED", json.get("state").asText());
        assertEquals(1, json.get("done").asInt());
        assertEquals("2026-11-01", json.get("due").asText());

        /* without create right */
        store = store();
        assertEquals(403, create(store, body("Nachablesung Süd", 2L, "[101]", "[41]", null)).status);
        assertEquals(1, store.objects.stream().filter(o -> ROUND.equals(o.getJevisClass())).count());
    }

    @Test
    public void createWithANewDirectory() throws Exception {
        FakeEditStore store = store();
        store.create.add(5L);
        WebEdit.Reply reply = create(store, body("Zweitwerk monatlich", 5L, "[111]", "[41]", null));
        assertEquals(String.valueOf(reply.body), 201, reply.status);
        long id = FakeEditStore.json(reply).get("id").asLong();
        long directory = store.object(id).getParent();
        assertEquals(ResourceWebReadingRounds.CLASS_DIRECTORY, store.object(directory).getJevisClass());
        assertEquals(5L, (long) store.object(directory).getParent());
        assertTrue(store.logs.get(0).startsWith("CREATE_OBJECT " + directory + ":"));
        assertTrue(store.logs.get(1).startsWith("CREATE_OBJECT " + id + ":"));

        /* the round fails: the new directory is removed again */
        FakeEditStore failing = new FakeEditStore() {
            @Override
            public int write(long objectId, String attribute, int primitiveType, List<JsonSample> written) {
                if (ResourceWebReadingRounds.ATTRIBUTE_METERS.equals(attribute)) throw new IllegalStateException("database gone");
                return super.write(objectId, attribute, primitiveType, written);
            }
        };
        FakeEditStore template = store();
        failing.add(template.objects.toArray(new org.jevis.commons.ws.json.JsonObject[0]));
        failing.hidden.addAll(template.hidden);
        failing.classMap.putAll(template.classMap);
        failing.samples.putAll(template.samples);
        failing.create.add(5L);
        try {
            create(failing, body("Zweitwerk monatlich", 5L, "[111]", "[41]", null));
            fail("the write error is passed on");
        } catch (IllegalStateException e) {
            assertEquals("database gone", e.getMessage());
        }
        assertEquals(2, failing.deletedObjects.size());
        assertEquals(0, failing.objects.stream().filter(o -> ResourceWebReadingRounds.CLASS_DIRECTORY.equals(o.getJevisClass())
                && o.getParent() == 5L).count());
        assertTrue(failing.logs.get(failing.logs.size() - 1).endsWith("|web:round-create-rollback"));
    }

    @Test
    public void validationReportsAllProblems() throws Exception {
        FakeEditStore store = store();
        store.create.add(7000L);
        StringBuilder longRemarks = new StringBuilder();
        for (int i = 0; i < 1001; i++) longRemarks.append('x');
        String body = "{\"site\":2,\"name\":\"  \",\"meters\":[101,101,111,999,104],\"readers\":[],\"supervisor\":[43,101],"
                + "\"startDate\":\"2026-13-01\",\"period\":\"P2M\",\"leadDays\":61,\"reminderDays\":-1,"
                + "\"escalationDays\":\"7\",\"remarks\":\"" + longRemarks + "\"}";
        List<String> messages = messages(create(store, body));
        assertTrue(messages.toString(), any(messages, "Missing name"));
        assertTrue(messages.toString(), any(messages, "Meter 101 is listed twice"));
        assertTrue(messages.toString(), any(messages, "Meter 111 is no readable meter of the site"));
        assertTrue(messages.toString(), any(messages, "Meter 999 is no readable meter of the site"));
        assertTrue(messages.toString(), any(messages, "Meter 104 is no readable meter of the site"));
        assertTrue(messages.toString(), any(messages, "At least one reader"));
        assertTrue(messages.toString(), any(messages, "supervisor: 43 is no readable user or group"));
        assertTrue(messages.toString(), any(messages, "supervisor: 101 is no readable user or group"));
        assertTrue(messages.toString(), any(messages, "Invalid startDate"));
        assertTrue(messages.toString(), any(messages, "Invalid period"));
        assertTrue(messages.toString(), any(messages, "leadDays must be between 0 and 60"));
        assertTrue(messages.toString(), any(messages, "reminderDays must be between 0 and 60"));
        assertTrue(messages.toString(), any(messages, "Invalid escalationDays"));
        assertTrue(messages.toString(), any(messages, "Remarks longer than 1000"));
        assertTrue(store.writes.isEmpty());
        assertEquals(1, store.objects.stream().filter(o -> ROUND.equals(o.getJevisClass())).count());

        messages = messages(create(store, body(ROUND_NAME.toLowerCase(Locale.ROOT), 2L, "[]", "[41]",
                "\"leadDays\":28")));
        assertTrue(messages.toString(), any(messages, "already exists"));
        assertTrue(messages.toString(), any(messages, "At least one meter"));
        assertTrue(messages.toString(), any(messages, "leadDays must be shorter than the period"));

        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 501; i++) many.append(i > 0 ? "," : "").append(101);
        messages = messages(create(store, body("Viele", 2L, many + "]", "[41]", null)));
        assertTrue(any(messages, "At most 500 meters"));
        messages = messages(create(store, "{\"site\":2,\"name\":\"a\\u0007b\",\"meters\":\"101\",\"readers\":[41]}"));
        assertTrue(messages.toString(), any(messages, "Invalid characters in name"));
        assertTrue(any(messages, "Invalid meters"));
        assertTrue(any(messages, "Missing startDate"));
        assertTrue(any(messages, "Missing period"));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 101; i++) longName.append('n');
        assertTrue(any(messages(create(store, body(longName.toString(), 2L, "[101]", "[41]", null))), "longer than 100"));

        assertTrue(any(messages(create(store, body("Ohne Standort", null, "[101]", "[41]", null))), "Missing site"));
        assertEquals(404, create(store, body("Falscher Standort", 3L, "[101]", "[41]", null)).status);
        assertEquals(400, create(store, "not json").status);
    }

    // ---------------------------------------------------------------- PUT

    private static WebEdit.Reply update(FakeEditStore store, long id, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebReadingRounds.update(s, id, body, NOW));
    }

    private static String version() {
        return WebEdit.iso(T0.getMillis(), org.joda.time.DateTimeZone.getDefault());
    }

    @Test
    public void updateWritesTheChangedAttributes() throws Exception {
        FakeEditStore store = store();
        /* the hidden meter 104 and the hidden supervisor 43 stay */
        String body = "{\"name\":\"Monatsablesung Nord\",\"enabled\":true,\"meters\":[101,103,102,104],\"readers\":[44],"
                + "\"supervisor\":[42,43],\"startDate\":\"2026-01-01\",\"period\":\"P1M\",\"leadDays\":3,"
                + "\"reminderDays\":3,\"escalationDays\":7,\"remarks\":\"Schlüssel beim Empfang\",\"version\":\"" + version() + "\"}";
        WebEdit.Reply reply = update(store, 7001, body);
        assertEquals(String.valueOf(reply.body), 200, reply.status);
        assertEquals(store.writes.toString(), 1, store.writes.size());
        assertEquals("7001/Remarks 0 Schlüssel beim Empfang | null", store.writes.get(0));
        assertEquals(Collections.singletonList("7001 Monatsablesung Nord"), store.renames);
        assertTrue(store.logs.get(store.logs.size() - 1).endsWith("|web:round-save"));
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("Monatsablesung Nord", json.get("name").asText());
        assertEquals("Schlüssel beim Empfang", json.get("remarks").asText());
        assertNotEquals(version(), json.get("version").asText());

        /* the old version: 409 with the current round */
        reply = update(store, 7001, body);
        assertEquals(409, reply.status);
        assertEquals(7001, FakeEditStore.json(reply).get("id").asLong());
        assertEquals(400, update(store, 7001, body.replace(",\"version\":\"" + version() + "\"", "")).status);

        /* optional fields missing: kept; adding a hidden meter that was not in the round: refused */
        store = store();
        reply = update(store, 7001, "{\"name\":\"" + ROUND_NAME + "\",\"meters\":[101],\"readers\":[44],"
                + "\"startDate\":\"2026-01-01\",\"period\":\"P1M\",\"version\":\"" + version() + "\"}");
        assertEquals(200, reply.status);
        assertEquals(store.writes.toString(), 1, store.writes.size());
        assertEquals("[101]", store.latest(7001, ResourceWebReadingRounds.ATTRIBUTE_METERS).getValue());
        assertEquals("Schlüssel beim Pförtner", FakeEditStore.json(reply).get("remarks").asText());
        assertTrue(store.renames.isEmpty());

        store = store();
        store.add(obj(7002, "Andere Runde", ROUND, 7000));
        List<String> messages = messages(update(store, 7001, "{\"name\":\"andere runde\",\"meters\":[101,104,103],"
                + "\"readers\":[44],\"startDate\":\"2026-01-01\",\"period\":\"P1M\",\"version\":\"" + version() + "\"}"));
        assertTrue(messages.toString(), any(messages, "already exists"));
        store.samples.remove("7001/" + ResourceWebReadingRounds.ATTRIBUTE_METERS);
        assertTrue(any(messages(update(store, 7001, "{\"name\":\"" + ROUND_NAME + "\",\"meters\":[104],\"readers\":[44],"
                + "\"startDate\":\"2026-01-01\",\"period\":\"P1M\",\"version\":\"" + version() + "\"}")), "Meter 104"));

        store = store();
        store.write.clear();
        assertEquals(403, update(store, 7001, body).status);
        assertEquals(404, update(store, 101, body).status);
        assertEquals(404, update(store, 999, body).status);
        assertTrue(store.writes.isEmpty());
    }

    // ---------------------------------------------------------------- DELETE

    @Test
    public void deleteToTheRecycleBin() throws Exception {
        FakeEditStore store = store();
        assertEquals(409, WebEdit.call(store, s -> ResourceWebReadingRounds.delete(s, 7001, "2026-11-01T00:00:00.000+01:00", NOW)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebReadingRounds.delete(s, 7001, null, NOW)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebReadingRounds.delete(s, 7001, "yesterday", NOW)).status);
        store.delete.clear();
        assertEquals(403, WebEdit.call(store, s -> ResourceWebReadingRounds.delete(s, 7001, version(), NOW)).status);
        store.delete.add(7001L);
        assertTrue(store.recycled.isEmpty());
        assertEquals(204, WebEdit.call(store, s -> ResourceWebReadingRounds.delete(s, 7001, version(), NOW)).status);
        assertEquals(Collections.singletonList(7001L), store.recycled);
        assertTrue(store.logs.get(0), store.logs.get(0).equals("MARK_AS_DELETE_OBJECT 7001:" + ROUND_NAME + "|web:round-delete"));
        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadingRounds.delete(s, 7001, version(), NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadingRounds.delete(s, 7000, version(), NOW)).status);
    }

    // ---------------------------------------------------------------- readings of a round

    @Test
    public void readingsOfARound() throws Exception {
        FakeEditStore store = store();
        ResourceWebRegisters.Translations translations = new ResourceWebRegisters.Translations(Collections.emptyList(), "de", store.classes());
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebReadingRounds.readings(s, translations, 7001, NOW));
        assertEquals(200, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("2026-11-13", json.get("today").asText());
        JsonNode round = json.get("round");
        assertEquals(7001, round.get("id").asLong());
        assertEquals(ROUND_NAME, round.get("name").asText());
        assertEquals("2026-11-01", round.get("due").asText());
        assertEquals("2026-12-01", round.get("nextDue").asText());
        assertEquals("ESCALATED", round.get("state").asText());
        assertEquals(1, json.get("groups").size());
        JsonNode group = json.get("groups").get(0);
        assertEquals(ROUND_NAME, group.get("name").asText());
        assertEquals("7001", group.get("key").asText());
        assertEquals(2, group.get("site").get("id").asLong());
        JsonNode meters = group.get("meters");
        assertEquals("round order, the hidden meter left out", 3, meters.size());
        assertEquals(101, meters.get(0).get("id").asLong());
        assertEquals(103, meters.get(1).get("id").asLong());
        assertEquals(102, meters.get(2).get("id").asLong());
        assertTrue(meters.get(0).get("doneInRound").asBoolean());
        assertTrue(meters.get(1).get("doneInRound").asBoolean());
        assertEquals("AUTOMATIC", meters.get(1).get("problem").asText());
        assertFalse(meters.get(2).get("doneInRound").asBoolean());
        /* the E8.1 fields are there */
        assertEquals(201, meters.get(0).get("row").get("id").asLong());
        assertNotNull(meters.get(0).get("readings"));

        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadingRounds.readings(s, translations, 999, NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadingRounds.readings(s, translations, 101, NOW)).status);
        /* without round: the E8.1 answer has no round fields */
        JsonNode plain = FakeEditStore.json(WebEdit.call(store, s -> ResourceWebReadings.list(s, translations, null, NOW)));
        assertNull(plain.get("round"));
        assertNull(plain.get("groups").get(0).get("meters").get(0).get("doneInRound"));
    }

    // ---------------------------------------------------------------- route sheet

    private static String pdfText(byte[] bytes, int[] pages) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (pages != null) pages[0] = document.getNumberOfPages();
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    public void routeSheetPdf() throws Exception {
        RouteSheetPdf.Sheet sheet = new RouteSheetPdf.Sheet();
        sheet.roundName = "Ableserunde Größe Süd – Prüfstand ß";
        sheet.site = "Musterwerk";
        sheet.due = "01.11.2026";
        sheet.period = "monatlich";
        sheet.readers = Arrays.asList("Max Muster", "Energieteam");
        sheet.remarks = "Schlüssel beim Pförtner\nZweite Zeile 测试";
        sheet.printed = "13.11.2026";
        sheet.hidden = 2;
        for (int i = 1; i <= 40; i++) {
            RouteSheetPdf.Row row = new RouteSheetPdf.Row();
            row.position = i;
            row.name = "Zähler " + i;
            row.location = "Keller, Raum " + i;
            row.serialNumber = "SN-" + i;
            row.meterPoint = "DE000" + i;
            row.link = "https://example.org/app/readings?meter=" + (5000 + i);
            row.last = "12.345,6 kWh (02.10.2026)";
            row.expected = "12.100 … 12.600 kWh";
            sheet.rows.add(row);
        }
        int[] pages = new int[1];
        String text = pdfText(RouteSheetPdf.render(sheet), pages);
        assertTrue(text, text.contains("Laufzettel"));
        assertTrue(text, text.contains("Ableserunde Größe Süd – Prüfstand ß"));
        assertTrue(text.contains("Zählerstand"));
        assertTrue(text.contains("Kürzel"));
        assertTrue(text.contains("Bemerkung"));
        assertTrue(text.contains("Schlüssel beim Pförtner"));
        assertTrue("unsupported characters as ?", text.contains("Zweite Zeile ??"));
        assertTrue(text.contains("2 Zähler ohne Leserecht nicht aufgeführt"));
        assertTrue(text.contains("12.100 … 12.600 kWh"));
        assertTrue(text.contains("Max Muster, Energieteam"));
        assertTrue("40 blocks of 96 pt need more than 5 pages", pages[0] >= 6);
        assertTrue(text, text.contains("Seite 1 von " + pages[0]));
        assertTrue(text.contains("Seite " + pages[0] + " von " + pages[0]));
        for (int i = 1; i <= 40; i++) {
            assertTrue("meter " + i, text.contains(i + ". Zähler " + i + "\n") || text.contains(i + ". Zähler " + i + "\r"));
        }

        /* one meter: one page; English */
        sheet.rows = sheet.rows.subList(0, 1);
        sheet.language = "en";
        sheet.hidden = 0;
        text = pdfText(RouteSheetPdf.render(sheet), pages);
        assertEquals(1, pages[0]);
        assertTrue(text.contains("Route sheet"));
        assertTrue(text.contains("Page 1 of 1"));
        assertTrue(text.contains("Initials"));
        assertFalse(text.contains("read right"));

        /* no meters */
        sheet.rows = new ArrayList<>();
        text = pdfText(RouteSheetPdf.render(sheet), pages);
        assertEquals(1, pages[0]);
        assertTrue(text.contains("No meters to read"));
    }

    @Test
    public void routeSheetOfARound() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebReadingRounds.sheet(s, 7001, "de", null, "https://example.org", NOW));
        assertEquals(200, reply.status);
        ResourceWebReadingRounds.Pdf pdf = (ResourceWebReadingRounds.Pdf) reply.body;
        assertEquals("Laufzettel_" + ROUND_NAME + "_2026-11-01.pdf", pdf.name);
        int[] pages = new int[1];
        String text = pdfText(pdf.bytes, pages);
        assertEquals(1, pages[0]);
        assertTrue(text, text.contains(ROUND_NAME));
        assertTrue(text.contains("Musterwerk"));
        assertTrue(text.contains("01.11.2026"));
        assertTrue(text.contains("monatlich"));
        assertTrue(text.contains("Energieteam"));
        assertTrue(text.contains("Schlüssel beim Pförtner"));
        assertTrue(text.contains("13.11.2026"));
        assertTrue(text.contains("1. Zähler A"));
        assertTrue(text.contains("2. Zähler B"));
        assertFalse("automatic meters are left out", text.contains("Zähler C"));
        assertFalse(text.contains("Zähler D"));
        assertTrue(text.contains("1 Zähler ohne Leserecht nicht aufgeführt"));
        assertTrue(text, text.contains("A-123"));
        assertTrue(text.contains("DE0001"));
        assertTrue(text.contains("Keller 1"));
        assertTrue(text.contains("120 kWh (02.11.2026)"));
        /* monthly values of B (40, 50): min − margin … max + margin, margin max(20 % of 10, 20 % of 45) = 9 */
        assertTrue(text, text.contains("31 … 59 kWh"));

        reply = WebEdit.call(store, s -> ResourceWebReadingRounds.sheet(s, 7001, "en", "2026-12-01", "", NOW));
        assertEquals("Laufzettel_" + ROUND_NAME + "_2026-12-01.pdf", ((ResourceWebReadingRounds.Pdf) reply.body).name);
        text = pdfText(((ResourceWebReadingRounds.Pdf) reply.body).bytes, null);
        assertTrue(text.contains("Route sheet"));
        assertTrue(text.contains("2026-12-01"));
        assertTrue(text.contains("monthly"));

        /* before the start: the next due date */
        store.sample(7001, ResourceWebReadingRounds.ATTRIBUTE_START_DATE, T0, "2027-01-15", null);
        reply = WebEdit.call(store, s -> ResourceWebReadingRounds.sheet(s, 7001, "de", null, "", NOW));
        assertEquals("Laufzettel_" + ROUND_NAME + "_2027-01-15.pdf", ((ResourceWebReadingRounds.Pdf) reply.body).name);

        assertEquals(400, WebEdit.call(store, s -> ResourceWebReadingRounds.sheet(s, 7001, "de", "01.11.2026", "", NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadingRounds.sheet(s, 999, "de", null, "", NOW)).status);
        store.hidden.add(7001L);
        store.add();
        assertEquals(404, WebEdit.call(store, s -> ResourceWebReadingRounds.sheet(s, 7001, "de", null, "", NOW)).status);
    }

    @Test
    public void links() {
        assertEquals("https://example.org", ResourceWebReadingRounds.appBase("https://example.org:443"));
        assertEquals("http://example.org", ResourceWebReadingRounds.appBase("http://example.org:80"));
        assertEquals("https://example.org:8443", ResourceWebReadingRounds.appBase("https://example.org:8443"));
        assertEquals("", ResourceWebReadingRounds.appBase(null));
        assertEquals("https://example.org/app/readings?meter=5101", ResourceWebReadingRounds.meterLink("https://example.org", 5101));
        assertEquals(Arrays.asList(1L, 2L, 3L), ResourceWebReadingRounds.ids("[1, \"2\", 3, \"x\", 1.5]"));
        assertTrue(ResourceWebReadingRounds.ids("{}").isEmpty());
        assertTrue(ResourceWebReadingRounds.ids("nonsense").isEmpty());
        assertEquals("[4,5]", ResourceWebReadingRounds.idsJson(Arrays.asList(4L, 5L)));
        assertEquals("vierteljährlich", ResourceWebReadingRounds.periodLabel("P3M", "de"));
        assertEquals("every 2 weeks", ResourceWebReadingRounds.periodLabel("P2W", "en"));
    }
}
