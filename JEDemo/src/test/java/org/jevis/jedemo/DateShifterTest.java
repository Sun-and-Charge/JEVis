/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.commons.appointment.Appointment;
import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.iso.ActionItem;
import org.jevis.commons.iso.IsoJson;
import org.jevis.commons.iso.Obligation;
import org.jevis.commons.maintenance.MaintenanceEntry;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

public class DateShifterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void weeksToShiftIsFloorOfWholeWeeks() {
        LocalDate until = LocalDate.of(2026, 11, 6);
        assertEquals(0, DateShifter.weeksToShift(until, LocalDate.of(2026, 11, 6)));
        assertEquals(0, DateShifter.weeksToShift(until, LocalDate.of(2026, 11, 12)));
        assertEquals(1, DateShifter.weeksToShift(until, LocalDate.of(2026, 11, 13)));
        assertEquals(2, DateShifter.weeksToShift(until, LocalDate.of(2026, 11, 26)));
        assertEquals(0, DateShifter.weeksToShift(until, LocalDate.of(2026, 10, 1)));
    }

    @Test
    public void appointmentFieldsMoveAndUnknownKeysStay() throws Exception {
        String json = "{\"title\":\"Prüfung Feuerlöscher\",\"due\":\"2026-11-20T09:00\",\"recurrence\":\"YEARLY\","
                + "\"lastDone\":\"2025-11-20T10:30\",\"created\":\"2025-01-02T08:15:30\",\"status\":\"OPEN\","
                + "\"recipients\":[],\"futureKey\":{\"a\":1}}";
        String shifted = DateShifter.shiftLocalFields(json, DateShifter.APPOINTMENT_FIELDS, 2);
        JsonNode node = MAPPER.readTree(shifted);
        assertEquals("2026-12-04T09:00", node.get("due").asText());
        assertEquals("2025-12-04T10:30", node.get("lastDone").asText());
        assertEquals("2025-01-16T08:15:30", node.get("created").asText());
        assertEquals("Prüfung Feuerlöscher", node.get("title").asText());
        assertEquals(1, node.get("futureKey").get("a").asInt());
        assertEquals("YEARLY", node.get("recurrence").asText());

        // the commons reader still understands it, in the storage format
        Appointment appointment = EntryFormat.fromJson(shifted, Appointment.class);
        assertNotNull(appointment);
        assertEquals(EntryFormat.format(new LocalDateTime(2026, 12, 4, 9, 0)), node.get("due").asText());
    }

    @Test
    public void appointmentWithoutDatesIsUnchanged() throws Exception {
        assertNull(DateShifter.shiftLocalFields("{\"title\":\"x\",\"due\":null}", DateShifter.APPOINTMENT_FIELDS, 1));
    }

    @Test
    public void recipientsAreDetected() throws Exception {
        assertFalse(DateShifter.hasRecipients("{\"recipients\":[]}"));
        assertFalse(DateShifter.hasRecipients("{\"title\":\"x\"}"));
        assertFalse(DateShifter.hasRecipients("{\"recipients\":null}"));
        assertTrue(DateShifter.hasRecipients("{\"recipients\":[4711]}"));
    }

    @Test
    public void maintenanceFieldsMoveWithTheirFormat() throws Exception {
        String json = "{\"type\":\"PLANNED\",\"start\":\"2026-11-10T06:00\",\"durationMinutes\":120,"
                + "\"doneAt\":\"2026-11-10T08:05:00\",\"created\":\"2026-10-01T12:00:00\","
                + "\"informRequested\":\"2026-11-01T07:00:00\",\"recipients\":[],\"extra\":\"keep\"}";
        String shifted = DateShifter.shiftLocalFields(json, DateShifter.MAINTENANCE_FIELDS, 3);
        JsonNode node = MAPPER.readTree(shifted);
        assertEquals("2026-12-01T06:00", node.get("start").asText());
        assertEquals("2026-12-01T08:05:00", node.get("doneAt").asText());
        assertEquals("2026-10-22T12:00:00", node.get("created").asText());
        assertEquals("2026-11-22T07:00:00", node.get("informRequested").asText());
        assertEquals(120, node.get("durationMinutes").asInt());
        assertEquals("keep", node.get("extra").asText());
        assertNotNull(EntryFormat.fromJson(shifted, MaintenanceEntry.class));
    }

    @Test
    public void actionDatesMoveAcrossDstAndUnknownKeysStay() throws Exception {
        // Control Center format (Joda ISODateTimeFormat.dateTime()), with NaN like Gson writes it
        String json = "{\n  \"Nr\": 3,\n  \"Title\": \"LED\",\n  \"Create Date\": \"2026-10-20T10:00:00.000+02:00\",\n"
                + "  \"Planned Date\": \"2026-12-01T00:00:00.000+01:00\",\n  \"Done Date\": null,\n"
                + "  \"Savings\": NaN,\n  \"Unknown\": [1, 2]\n}";
        String shifted = DateShifter.shiftIsoFields(json, DateShifter.ACTION_FIELDS, 2);
        assertTrue(shifted.contains("NaN"));
        assertFalse(shifted.contains("\"NaN\""));

        ActionItem item = IsoJson.read(shifted, ActionItem.class);
        assertNotNull(item);
        JsonNode node = new ObjectMapper().configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true)
                .readTree(shifted);
        // 20 Oct + 2 weeks = 3 Nov, DST ended in between: same local time, new offset
        assertEquals("2026-11-03T10:00:00.000+01:00", node.get("Create Date").asText());
        assertEquals("2026-12-15T00:00:00.000+01:00", node.get("Planned Date").asText());
        assertTrue(node.get("Done Date").isNull());
        assertEquals(2, node.get("Unknown").size());
        assertEquals(3, node.get("Nr").asInt());

        // Joda (as the Control Center) parses it
        DateTime created = ISODateTimeFormat.dateTime().parseDateTime(node.get("Create Date").asText());
        assertEquals(10, created.withZone(org.joda.time.DateTimeZone.forID("Europe/Berlin")).getHourOfDay());
    }

    @Test
    public void utcOffsetStaysFixed() {
        assertEquals("2026-11-17T09:00:00.000Z", DateShifter.shiftIso("2026-10-27T09:00:00.000Z", 3));
    }

    @Test
    public void nonconformityUsesTheActionFields() throws Exception {
        String json = "{\"Create Date\":\"2026-01-05T08:00:00.000+01:00\",\"Done Date\":\"2026-02-01T08:00:00.000+01:00\",\"x\":true}";
        JsonNode node = MAPPER.readTree(DateShifter.shiftIsoFields(json, DateShifter.ACTION_FIELDS, 1));
        assertEquals("2026-01-12T08:00:00.000+01:00", node.get("Create Date").asText());
        assertEquals("2026-02-08T08:00:00.000+01:00", node.get("Done Date").asText());
        assertTrue(node.get("x").asBoolean());
    }

    @Test
    public void obligationDatesMove() throws Exception {
        String json = "{\"title\":\"GEG\",\"issue date\":\"2023-08-16T00:00:00.000+02:00\","
                + "\"current version date\":\"2024-01-01T00:00:00.000+01:00\","
                + "\"Date of Examination\":\"2026-11-02T00:00:00.000+01:00\",\"relevant\":true,\"other\":\"x\"}";
        String shifted = DateShifter.shiftIsoFields(json, DateShifter.OBLIGATION_FIELDS, 1);
        JsonNode node = MAPPER.readTree(shifted);
        assertEquals("2023-08-23T00:00:00.000+02:00", node.get("issue date").asText());
        assertEquals("2024-01-08T00:00:00.000+01:00", node.get("current version date").asText());
        assertEquals("2026-11-09T00:00:00.000+01:00", node.get("Date of Examination").asText());
        assertEquals("x", node.get("other").asText());
        assertNotNull(IsoJson.read(shifted, Obligation.class));
    }

    @Test
    public void invalidJsonIsRejected() {
        try {
            DateShifter.shiftIsoFields("not json", DateShifter.ACTION_FIELDS, 1);
            fail();
        } catch (Exception expected) {
            // ok
        }
    }
}
