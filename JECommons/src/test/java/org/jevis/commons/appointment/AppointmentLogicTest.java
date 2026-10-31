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
package org.jevis.commons.appointment;

import org.jevis.commons.mail.MailMessage;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class AppointmentLogicTest {

    private static Appointment appointment(String due, Appointment.Recurrence recurrence, int reminderDays) {
        Appointment appointment = new Appointment();
        appointment.setTitle("Meter verification");
        appointment.setDue(due);
        appointment.setRecurrence(recurrence);
        appointment.setReminderDays(reminderDays);
        return appointment;
    }

    @Test
    public void nextDueByRecurrence() {
        LocalDateTime due = LocalDateTime.parse("2026-01-15T09:00");
        assertNull(AppointmentLogic.nextDue(due, Appointment.Recurrence.NONE, null));
        assertEquals(LocalDateTime.parse("2026-02-15T09:00"), AppointmentLogic.nextDue(due, Appointment.Recurrence.MONTHLY, null));
        assertEquals(LocalDateTime.parse("2026-04-15T09:00"), AppointmentLogic.nextDue(due, Appointment.Recurrence.QUARTERLY, null));
        assertEquals(LocalDateTime.parse("2027-01-15T09:00"), AppointmentLogic.nextDue(due, Appointment.Recurrence.YEARLY, null));
    }

    @Test
    public void nextDueKeepsAnchorDayAfterShortMonth() {
        LocalDateTime feb = AppointmentLogic.nextDue(LocalDateTime.parse("2026-01-31T08:00"), Appointment.Recurrence.MONTHLY, 31);
        assertEquals(LocalDateTime.parse("2026-02-28T08:00"), feb);
        assertEquals(LocalDateTime.parse("2026-03-31T08:00"), AppointmentLogic.nextDue(feb, Appointment.Recurrence.MONTHLY, 31));
    }

    @Test
    public void completeOnceSetsDone() {
        Appointment appointment = appointment("2026-11-30T09:00", Appointment.Recurrence.NONE, 7);
        assertNull(AppointmentLogic.complete(appointment, LocalDateTime.parse("2026-11-29T10:00")));
        assertEquals(Appointment.Status.DONE, appointment.getStatus());
        assertEquals("2026-11-29T10:00:00", appointment.getLastDone());
    }

    @Test
    public void completeRecurringAdvancesDueDate() {
        Appointment appointment = appointment("2026-11-30T09:00", Appointment.Recurrence.YEARLY, 7);
        LocalDateTime next = AppointmentLogic.complete(appointment, LocalDateTime.parse("2026-11-29T10:00"));
        assertEquals(LocalDateTime.parse("2027-11-30T09:00"), next);
        assertEquals("2027-11-30T09:00", appointment.getDue());
        assertEquals(Appointment.Status.OPEN, appointment.getStatus());
        assertEquals(Integer.valueOf(30), appointment.getAnchorDay());
    }

    @Test
    public void completeOverdueRecurringSkipsPastPeriods() {
        Appointment appointment = appointment("2026-01-10T09:00", Appointment.Recurrence.MONTHLY, 0);
        LocalDateTime next = AppointmentLogic.complete(appointment, LocalDateTime.parse("2026-04-20T10:00"));
        assertEquals(LocalDateTime.parse("2026-05-10T09:00"), next);
    }

    @Test
    public void reminderWindowAndDueMail() {
        Appointment appointment = appointment("2026-11-30T09:00", Appointment.Recurrence.NONE, 7);
        MailState state = new MailState();
        assertNull(AppointmentLogic.pendingMail(appointment, state, LocalDateTime.parse("2026-11-22T23:59")));
        assertEquals(AppointmentLogic.MailKind.REMINDER, AppointmentLogic.pendingMail(appointment, state, LocalDateTime.parse("2026-11-23T00:05")));

        AppointmentLogic.markSent(AppointmentLogic.MailKind.REMINDER, appointment, state, LocalDateTime.parse("2026-11-23T00:05"));
        assertNull("reminder is sent once", AppointmentLogic.pendingMail(appointment, state, LocalDateTime.parse("2026-11-25T12:00")));

        assertEquals(AppointmentLogic.MailKind.DUE, AppointmentLogic.pendingMail(appointment, state, LocalDateTime.parse("2026-11-30T00:05")));
        AppointmentLogic.markSent(AppointmentLogic.MailKind.DUE, appointment, state, LocalDateTime.parse("2026-11-30T00:05"));
        assertNull("due mail is sent once", AppointmentLogic.pendingMail(appointment, state, LocalDateTime.parse("2026-12-05T12:00")));
    }

    @Test
    public void missedReminderOnlySendsDueMail() {
        Appointment appointment = appointment("2026-11-30T09:00", Appointment.Recurrence.NONE, 7);
        MailState state = new MailState();
        assertEquals(AppointmentLogic.MailKind.DUE, AppointmentLogic.pendingMail(appointment, state, LocalDateTime.parse("2026-12-02T08:00")));
        AppointmentLogic.markSent(AppointmentLogic.MailKind.DUE, appointment, state, LocalDateTime.parse("2026-12-02T08:00"));
        assertNull(AppointmentLogic.pendingMail(appointment, state, LocalDateTime.parse("2026-12-02T08:15")));
    }

    @Test
    public void noReminderWithoutReminderDaysAndNoMailWhenDone() {
        Appointment appointment = appointment("2026-11-30T09:00", Appointment.Recurrence.NONE, 0);
        assertNull(AppointmentLogic.pendingMail(appointment, new MailState(), LocalDateTime.parse("2026-11-29T08:00")));
        appointment.setStatus(Appointment.Status.DONE);
        assertNull(AppointmentLogic.pendingMail(appointment, new MailState(), LocalDateTime.parse("2026-12-01T08:00")));
    }

    @Test
    public void advancedRecurringAppointmentGetsNewReminder() {
        Appointment appointment = appointment("2026-11-30T09:00", Appointment.Recurrence.MONTHLY, 3);
        MailState state = new MailState();
        AppointmentLogic.markSent(AppointmentLogic.MailKind.DUE, appointment, state, LocalDateTime.parse("2026-11-30T00:05"));
        AppointmentLogic.complete(appointment, LocalDateTime.parse("2026-11-30T10:00"));
        assertEquals("2026-12-30T09:00", appointment.getDue());
        assertNull(AppointmentLogic.pendingMail(appointment, state, LocalDateTime.parse("2026-12-26T08:00")));
        assertEquals(AppointmentLogic.MailKind.REMINDER, AppointmentLogic.pendingMail(appointment, state, LocalDateTime.parse("2026-12-27T08:00")));
    }

    @Test
    public void overdueAndDueSoon() {
        Appointment appointment = appointment("2026-11-30T09:00", Appointment.Recurrence.NONE, 7);
        assertFalse(AppointmentLogic.isDueSoon(appointment, LocalDate.parse("2026-11-22")));
        assertTrue(AppointmentLogic.isDueSoon(appointment, LocalDate.parse("2026-11-23")));
        assertTrue(AppointmentLogic.isDueSoon(appointment, LocalDate.parse("2026-11-30")));
        assertFalse(AppointmentLogic.isOverdue(appointment, LocalDate.parse("2026-11-30")));
        assertTrue(AppointmentLogic.isOverdue(appointment, LocalDate.parse("2026-12-01")));
    }

    @Test
    public void jsonRoundTrip() {
        Appointment appointment = appointment("2026-11-30T09:00", Appointment.Recurrence.QUARTERLY, 14);
        appointment.setRecipients(Arrays.asList(12L, 15L));
        appointment.setLinkedObject(99L);
        Appointment copy = EntryFormat.fromJson(EntryFormat.toJson(appointment), Appointment.class);
        assertEquals("2026-11-30T09:00", copy.getDue());
        assertEquals(Appointment.Recurrence.QUARTERLY, copy.getRecurrence());
        assertEquals(14, copy.getReminderDays());
        assertEquals(Arrays.asList(12L, 15L), copy.getRecipients());
        assertEquals(Long.valueOf(99L), copy.getLinkedObject());
        assertEquals(Appointment.Status.OPEN, copy.getStatus());
        assertNull(EntryFormat.fromJson("not json", Appointment.class));
        assertNull(EntryFormat.fromJson("", Appointment.class));
    }

    @Test
    public void mailTexts() {
        Appointment appointment = appointment("2026-11-30T09:00", Appointment.Recurrence.YEARLY, 7);
        appointment.setDescription("Check the seal.");

        MailMessage reminder = AppointmentMails.build(AppointmentLogic.MailKind.REMINDER, appointment, "Meter 1", LocalDate.parse("2026-11-23"));
        assertEquals("Erinnerung / Reminder: Meter verification - fällig am / due on 30.11.2026", reminder.getSubject());
        assertTrue(reminder.getText().contains("Fällig: 30.11.2026 09:00"));
        assertTrue(reminder.getText().contains("Wiederholung: jährlich"));
        assertTrue(reminder.getText().contains("Bezug: Meter 1"));
        assertTrue(reminder.getText().contains("Related to: Meter 1"));
        assertTrue(reminder.getText().indexOf("Hallo") < reminder.getText().indexOf("Hello"));
        assertFalse(reminder.isHtml());

        MailMessage dueToday = AppointmentMails.build(AppointmentLogic.MailKind.DUE, appointment, null, LocalDate.parse("2026-11-30"));
        assertEquals("Heute fällig / Due today: Meter verification", dueToday.getSubject());
        assertFalse(dueToday.getText().contains("Bezug:"));

        MailMessage overdue = AppointmentMails.build(AppointmentLogic.MailKind.DUE, appointment, null, LocalDate.parse("2026-12-02"));
        assertTrue(overdue.getSubject().startsWith("Überfällig / Overdue"));
    }
}
