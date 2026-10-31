/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.maintenance;

import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.jevis.commons.mail.MailMessage;
import org.joda.time.LocalDateTime;
import org.junit.Test;

import static org.junit.Assert.*;

public class MaintenanceLogicTest {

    private static MaintenanceEntry entry() {
        MaintenanceEntry entry = new MaintenanceEntry();
        entry.setType(MaintenanceEntry.Type.MAINTENANCE);
        entry.setTitle("Server update");
        entry.setStartDate(LocalDateTime.parse("2026-11-10T18:00"));
        entry.setDurationMinutes(150);
        entry.setImpact("No access to the Control Center.");
        return entry;
    }

    @Test
    public void nothingPendingWithoutRequest() {
        assertNull(MaintenanceLogic.pendingMail(entry(), new MailState()));
    }

    @Test
    public void informOncePerRequest() {
        MaintenanceEntry entry = entry();
        MailState state = new MailState();
        MaintenanceLogic.requestInform(entry, LocalDateTime.parse("2026-11-01T10:00:00"));
        assertEquals(MaintenanceLogic.MailKind.INFORM, MaintenanceLogic.pendingMail(entry, state));
        MaintenanceLogic.markSent(MaintenanceLogic.MailKind.INFORM, entry, state, LocalDateTime.parse("2026-11-01T10:05:00"));
        assertNull(MaintenanceLogic.pendingMail(entry, state));
        assertFalse(MaintenanceLogic.isInformPending(entry, state));

        MaintenanceLogic.requestInform(entry, LocalDateTime.parse("2026-11-02T10:00:00"));
        assertEquals("a new request sends again", MaintenanceLogic.MailKind.INFORM, MaintenanceLogic.pendingMail(entry, state));
    }

    @Test
    public void doneMailAfterInform() {
        MaintenanceEntry entry = entry();
        MailState state = new MailState();
        MaintenanceLogic.requestInform(entry, LocalDateTime.parse("2026-11-01T10:00:00"));
        MaintenanceLogic.markSent(MaintenanceLogic.MailKind.INFORM, entry, state, LocalDateTime.parse("2026-11-01T10:05:00"));

        MaintenanceLogic.changeStatus(entry, MaintenanceEntry.Status.IN_PROGRESS, LocalDateTime.parse("2026-11-10T18:00:00"));
        assertNull(MaintenanceLogic.pendingMail(entry, state));

        MaintenanceLogic.changeStatus(entry, MaintenanceEntry.Status.DONE, LocalDateTime.parse("2026-11-10T20:00:00"));
        assertEquals("2026-11-10T20:00:00", entry.getDoneAt());
        assertEquals(MaintenanceLogic.MailKind.DONE, MaintenanceLogic.pendingMail(entry, state));
        MaintenanceLogic.markSent(MaintenanceLogic.MailKind.DONE, entry, state, LocalDateTime.parse("2026-11-10T20:05:00"));
        assertNull(MaintenanceLogic.pendingMail(entry, state));

        MaintenanceLogic.changeStatus(entry, MaintenanceEntry.Status.DONE, LocalDateTime.parse("2026-11-10T21:00:00"));
        assertEquals("saving again as done keeps the timestamp", "2026-11-10T20:00:00", entry.getDoneAt());

        MaintenanceLogic.changeStatus(entry, MaintenanceEntry.Status.IN_PROGRESS, LocalDateTime.parse("2026-11-11T08:00:00"));
        assertNull(entry.getDoneAt());
        MaintenanceLogic.changeStatus(entry, MaintenanceEntry.Status.DONE, LocalDateTime.parse("2026-11-11T09:00:00"));
        assertEquals("reopened and done again", MaintenanceLogic.MailKind.DONE, MaintenanceLogic.pendingMail(entry, state));
    }

    @Test
    public void noDoneMailIfNeverInformed() {
        MaintenanceEntry entry = entry();
        MaintenanceLogic.changeStatus(entry, MaintenanceEntry.Status.DONE, LocalDateTime.parse("2026-11-10T20:00:00"));
        assertNull(MaintenanceLogic.pendingMail(entry, new MailState()));
    }

    @Test
    public void doneBeforeInformWasSentOnlySendsDoneMail() {
        MaintenanceEntry entry = entry();
        MailState state = new MailState();
        MaintenanceLogic.requestInform(entry, LocalDateTime.parse("2026-11-01T10:00:00"));
        MaintenanceLogic.changeStatus(entry, MaintenanceEntry.Status.DONE, LocalDateTime.parse("2026-11-01T10:01:00"));
        assertEquals(MaintenanceLogic.MailKind.DONE, MaintenanceLogic.pendingMail(entry, state));
        MaintenanceLogic.markSent(MaintenanceLogic.MailKind.DONE, entry, state, LocalDateTime.parse("2026-11-01T10:05:00"));
        assertNull(MaintenanceLogic.pendingMail(entry, state));
    }

    @Test
    public void mailTexts() {
        MaintenanceEntry entry = entry();
        MailMessage inform = MaintenanceMails.build(MaintenanceLogic.MailKind.INFORM, entry);
        assertEquals("Geplante Wartung / Planned maintenance: Server update (10.11.2026 18:00)", inform.getSubject());
        assertTrue(inform.getText().contains("Voraussichtliche Dauer: 2 h 30 min"));
        assertTrue(inform.getText().contains("Expected duration: 2 h 30 min"));
        assertTrue(inform.getText().contains("Auswirkungen:\nNo access to the Control Center."));
        assertTrue(inform.getText().contains("Status: Geplant"));

        entry.setType(MaintenanceEntry.Type.KNOWN_ISSUE);
        MaintenanceLogic.changeStatus(entry, MaintenanceEntry.Status.DONE, LocalDateTime.parse("2026-11-10T20:00:00"));
        MailMessage done = MaintenanceMails.build(MaintenanceLogic.MailKind.DONE, entry);
        assertEquals("Abgeschlossen / Finished: Server update", done.getSubject());
        assertTrue(done.getText().contains("das folgende bekannte Problem ist behoben."));
        assertTrue(done.getText().contains("Status: Finished"));
    }

    @Test
    public void durationFormat() {
        assertEquals("", EntryFormat.displayDuration(null));
        assertEquals("45 min", EntryFormat.displayDuration(45));
        assertEquals("3 h", EntryFormat.displayDuration(180));
    }
}
